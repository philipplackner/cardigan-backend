import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.DataInputStream
import java.io.File

/**
 * Class annotations that mark a class as wiring, configuration or JPA persistence, not logic worth
 * mutating. `@Repository` marks the JPA adapters, which only persistence and E2E tests reach.
 */
internal val NON_LOGIC_CLASS_ANNOTATIONS =
    setOf(
        "org.springframework.context.annotation.Configuration",
        "org.springframework.boot.context.properties.ConfigurationProperties",
        "org.springframework.boot.autoconfigure.SpringBootApplication",
        "org.springframework.stereotype.Repository",
        "jakarta.persistence.Entity",
    )

/**
 * Writes one PIT class pattern per line for every compiled class directly annotated with one of
 * [NON_LOGIC_CLASS_ANNOTATIONS], plus a pattern for its nested classes.
 */
@CacheableTask
abstract class NonLogicClassScanTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val classDirs: ConfigurableFileCollection

    @get:OutputFile
    abstract val patterns: RegularFileProperty

    @TaskAction
    fun scan() {
        val names =
            classDirs.files
                .filter { it.isDirectory }
                .flatMap { dir -> dir.walk().filter { it.isFile && it.name.endsWith(".class") }.toList() }
                .mapNotNull { file -> readAnnotatedClassName(file) }
                .sorted()
        patterns.get().asFile.writeText(names.flatMap { listOf(it, "$it$*") }.joinToString("\n"))
    }

    private fun readAnnotatedClassName(file: File): String? =
        DataInputStream(file.inputStream().buffered()).use { input ->
            val classFile = ClassFileHeader.read(input)
            val annotated = classFile.annotationTypes.any { it in NON_LOGIC_CLASS_ANNOTATIONS }
            if (annotated) classFile.name else null
        }
}

/**
 * Reads only the class name and the runtime-visible class annotations of a class file
 * (JVMS chapter 4). Avoids a bytecode library on the build-logic classpath.
 */
private class ClassFileHeader(
    val name: String,
    val annotationTypes: List<String>,
) {
    companion object {
        fun read(input: DataInputStream): ClassFileHeader {
            require(input.readInt() == 0xCAFEBABE.toInt()) { "Not a class file" }
            input.skipBytes(4)
            val pool = readConstantPool(input)
            val utf8 = pool.utf8
            input.skipBytes(2)
            val name = utf8.getValue(pool.classNameIndex.getValue(input.readUnsignedShort())).replace('/', '.')
            input.skipBytes(2)
            input.skipBytes(2 * input.readUnsignedShort())
            repeat(2) { skipMembers(input) }
            var annotationTypes = emptyList<String>()
            repeat(input.readUnsignedShort()) {
                val attributeName = utf8[input.readUnsignedShort()]
                val length = input.readInt()
                if (attributeName == "RuntimeVisibleAnnotations") {
                    annotationTypes = readAnnotationTypes(input, utf8)
                } else {
                    input.skipBytes(length)
                }
            }
            return ClassFileHeader(name, annotationTypes)
        }

        private class ConstantPool(
            val utf8: Map<Int, String>,
            val classNameIndex: Map<Int, Int>,
        )

        private fun readConstantPool(input: DataInputStream): ConstantPool {
            val utf8 = mutableMapOf<Int, String>()
            val classes = mutableMapOf<Int, Int>()
            val count = input.readUnsignedShort()
            var index = 1
            while (index < count) {
                when (val tag = input.readUnsignedByte()) {
                    1 -> {
                        utf8[index] = input.readUTF()
                    }

                    7 -> {
                        classes[index] = input.readUnsignedShort()
                    }

                    8, 16, 19, 20 -> {
                        input.skipBytes(2)
                    }

                    15 -> {
                        input.skipBytes(3)
                    }

                    3, 4, 9, 10, 11, 12, 17, 18 -> {
                        input.skipBytes(4)
                    }

                    5, 6 -> {
                        input.skipBytes(8)
                        index++
                    }

                    else -> {
                        error("Unknown constant pool tag $tag")
                    }
                }
                index++
            }
            return ConstantPool(utf8, classes)
        }

        private fun skipMembers(input: DataInputStream) {
            repeat(input.readUnsignedShort()) {
                input.skipBytes(6)
                repeat(input.readUnsignedShort()) {
                    input.skipBytes(2)
                    input.skipBytes(input.readInt())
                }
            }
        }

        private fun readAnnotationTypes(
            input: DataInputStream,
            utf8: Map<Int, String>,
        ): List<String> = List(input.readUnsignedShort()) { readAnnotation(input, utf8) }

        private fun readAnnotation(
            input: DataInputStream,
            utf8: Map<Int, String>,
        ): String {
            val descriptor = utf8.getValue(input.readUnsignedShort())
            repeat(input.readUnsignedShort()) {
                input.skipBytes(2)
                skipElementValue(input, utf8)
            }
            return descriptor.removePrefix("L").removeSuffix(";").replace('/', '.')
        }

        private fun skipElementValue(
            input: DataInputStream,
            utf8: Map<Int, String>,
        ) {
            when (input.readUnsignedByte().toChar()) {
                'e' -> input.skipBytes(4)
                '@' -> readAnnotation(input, utf8)
                '[' -> repeat(input.readUnsignedShort()) { skipElementValue(input, utf8) }
                else -> input.skipBytes(2)
            }
        }
    }
}
