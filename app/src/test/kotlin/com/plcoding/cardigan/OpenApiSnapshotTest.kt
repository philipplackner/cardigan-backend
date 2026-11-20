package com.plcoding.cardigan

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.doesNotContain
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.matches
import com.plcoding.cardigan.common.PostgresTestConfig
import org.junit.jupiter.api.Test
import org.opentest4j.AssertionFailedError
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.client.RestTestClient
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.json.JsonMapper
import java.nio.file.Files
import java.nio.file.Path

/**
 * Compares the generated OpenAPI document with the committed snapshot that clients copy. Run
 * `./gradlew :app:updateOpenApiSnapshot` to rewrite the snapshot after a deliberate API change.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["springdoc.api-docs.enabled=true"],
)
@ActiveProfiles("test")
@Import(PostgresTestConfig::class)
class OpenApiSnapshotTest @Autowired constructor(
    @LocalServerPort private val port: Int,
) {

    private val snapshot: Path = Path.of("src/test/resources/openapi/openapi.json")

    private val generated: String by lazy {
        val raw = RestTestClient.bindToServer().baseUrl("http://localhost:$port").build()
            .get().uri("/v3/api-docs")
            .exchange()
            .expectStatus().isOk
            .returnResult(String::class.java)
            .responseBody!!
        val tree = jsonMapper.readValue(raw, Any::class.java)
        prettyMapper.writeValueAsString(tree).replace("\r\n", "\n") + "\n"
    }

    private val document: Map<String, Any?> by lazy { generated.asMap() }

    @Test
    fun `generated document matches the committed snapshot`() {
        if (System.getProperty(UPDATE_PROPERTY) == "true") {
            Files.createDirectories(snapshot.parent)
            Files.writeString(snapshot, generated)
            return
        }
        val committed = if (Files.exists(snapshot)) Files.readString(snapshot) else ""
        if (committed != generated) {
            throw AssertionFailedError(
                "The generated OpenAPI document differs from app/$snapshot. If the API change is " +
                    "intended, run ./gradlew :app:updateOpenApiSnapshot and commit the file.",
                committed,
                generated,
            )
        }
    }

    @Test
    fun `document is OpenAPI 3_0_1 without list-valued types`() {
        assertThat(document["openapi"]).isEqualTo("3.0.1")
        assertThat(listValuedTypes(document)).isEmpty()
    }

    @Test
    fun `document has exactly one relative server`() {
        assertThat(document["servers"]).isEqualTo(listOf(mapOf("url" to "/")))
    }

    @Test
    fun `no test endpoint is documented`() {
        assertThat(document.map("paths").keys.filter { it.startsWith("/api/v1/test") }).isEmpty()
    }

    @Test
    fun `every operation has a unique camelCase operationId and one plural PascalCase tag`() {
        val operations = operations()
        val ids = operations.map { it["operationId"] as String? }

        ids.forEach { assertThat(it).isNotNull().matches(Regex("^[a-z][a-zA-Z0-9]*$")) }
        assertThat(ids.toSet().size).isEqualTo(ids.size)
        operations.forEach { operation ->
            @Suppress("UNCHECKED_CAST")
            val tags = operation["tags"] as List<String>
            assertThat(tags.size, "tags of ${operation["operationId"]}").isEqualTo(1)
            // `Auth` is the agreed exception: the auth routes are not a plural resource.
            assertThat(tags.single()).matches(Regex("^([A-Z][a-zA-Z]*s|Auth)$"))
        }
    }

    @Test
    fun `current user operation`() {
        val operation = document.map("paths").map("/api/v1/users/me").map("get")

        assertThat(operation["operationId"]).isEqualTo("getCurrentUser")
        assertThat(operation["tags"]).isEqualTo(listOf("Users"))
        assertThat(operation["security"]).isEqualTo(listOf(mapOf("bearerAuth" to emptyList<String>())))
        assertThat(operation.map("responses").keys.sorted()).containsExactly("200", "401", "500")
        assertThat(operation.map("responses").map("401")["description"] as String).doesNotContain("INTERNAL_ERROR")
        assertThat(operation.map("responses").map("401")["description"]).isEqualTo("Error codes: UNAUTHENTICATED")
        assertThat(operation.map("responses").map("500")["description"]).isEqualTo("Error codes: INTERNAL_ERROR")
        assertThat(operation.map("responses").map("401").map("content").map("application/problem+json").map("schema"))
            .isEqualTo(mapOf("\$ref" to "#/components/schemas/Problem"))
        assertThat(operation["requestBody"]).isNull()
        assertThat(operation.map("responses").map("200").map("content").keys).isEqualTo(setOf("application/json"))
    }

    @Test
    fun `current user schema is required, non-null and formatted`() {
        val ref = document.map("paths").map("/api/v1/users/me").map("get").map("responses").map("200")
            .map("content").values.single().let { (it as Map<*, *>)["schema"] as Map<*, *> }["\$ref"] as String
        val schema = document.map("components").map("schemas").map(ref.substringAfterLast('/'))

        assertThat((schema["required"] as List<*>).toSet()).isEqualTo(setOf("id", "email", "createdAt"))
        val properties = schema.map("properties")
        assertThat(properties.keys.sorted()).containsExactly("createdAt", "email", "id")
        properties.values.forEach { assertThat((it as Map<*, *>)["nullable"]).isNull() }
        assertThat(properties.map("id")["format"]).isEqualTo("uuid")
        assertThat(properties.map("createdAt")["format"]).isEqualTo("date-time")
    }

    @Test
    fun `problem schemas name the error fields`() {
        val schemas = document.map("components").map("schemas")

        assertThat((schemas.map("Problem")["required"] as List<*>).toSet())
            .isEqualTo(setOf("type", "title", "status", "detail", "code", "fieldErrors"))
        assertThat((schemas.map("FieldError")["required"] as List<*>).toSet()).isEqualTo(setOf("field", "code"))
        assertThat(schemas.map("FieldError").map("properties").keys.sorted()).containsExactly("code", "field", "max", "min")
    }

    @Test
    fun `bearer security scheme is declared`() {
        assertThat(document.map("components").map("securitySchemes").map("bearerAuth"))
            .isEqualTo(mapOf("type" to "http", "scheme" to "bearer", "bearerFormat" to "JWT"))
    }

    private fun operations(): List<Map<String, Any?>> =
        document.map("paths").values.flatMap { item -> (item as Map<*, *>).values.map { it.asStringMap() } }

    private fun listValuedTypes(node: Any?): List<Any?> = when (node) {
        is Map<*, *> -> node.flatMap { (key, value) ->
            (if (key == "type" && value is List<*>) listOf(value) else emptyList()) + listValuedTypes(value)
        }
        is List<*> -> node.flatMap { listValuedTypes(it) }
        else -> emptyList()
    }

    private fun String.asMap(): Map<String, Any?> = jsonMapper.readValue(this, Map::class.java).asStringMap()

    private fun Map<String, Any?>.map(key: String): Map<String, Any?> = this[key].asStringMap()

    @Suppress("UNCHECKED_CAST")
    private fun Any?.asStringMap(): Map<String, Any?> = this as Map<String, Any?>

    companion object {
        const val UPDATE_PROPERTY = "openapi.update"

        private val jsonMapper: JsonMapper = JsonMapper.builder().build()
        private val prettyMapper: JsonMapper = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build()
    }
}
