package com.plcoding.cardigan.tooling.konsist

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.jupiter.api.Test

class DomainLayerTest {
    @Test
    fun `domain packages import nothing from Spring or jakarta persistence`() {
        Konsist
            .scopeFromModules(checkedModules)
            .files
            .filter { it.sourceSetName in checkedSourceSets }
            .filter { file ->
                file.packagee
                    ?.name
                    ?.split('.')
                    ?.contains("domain") == true
            }.assertFalse { file ->
                file.imports.any { import -> forbiddenImportPrefixes.any { import.name.startsWith(it) } }
            }
    }

    private companion object {
        val checkedModules = listOf("common", "user", "board")
        val checkedSourceSets = setOf("main", "testFixtures")
        val forbiddenImportPrefixes = listOf("org.springframework.", "jakarta.persistence.")
    }
}
