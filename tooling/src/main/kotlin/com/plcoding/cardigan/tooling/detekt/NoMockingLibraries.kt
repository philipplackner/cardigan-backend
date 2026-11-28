package com.plcoding.cardigan.tooling.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtImportDirective

/** Reports every import from Mockito or MockK, because tests use hand-written fakes. */
class NoMockingLibraries(
    config: Config,
) : Rule(
        config,
        description = "Tests use hand-written fakes. Imports from mocking libraries are not allowed.",
    ) {
    override fun visitImportDirective(importDirective: KtImportDirective) {
        super.visitImportDirective(importDirective)
        val fqName = importDirective.importedFqName?.asString() ?: return
        if (MOCKING_PACKAGES.none { fqName == it || fqName.startsWith("$it.") }) return
        val path = if (importDirective.isAllUnder) "$fqName.*" else fqName
        report(
            Finding(
                Entity.from(importDirective),
                "Import of '$path' is not allowed. Use fakes instead of mocking libraries.",
            ),
        )
    }

    private companion object {
        val MOCKING_PACKAGES = listOf("org.mockito", "io.mockk")
    }
}
