package com.plcoding.cardigan.tooling.detekt

import dev.detekt.api.Config
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtImportDirective

/** Reports every import from Mockito or MockK, because tests use hand-written fakes. */
class NoMockingLibraries(config: Config) : Rule(
    config,
    description = "Tests use hand-written fakes. Imports from mocking libraries are not allowed.",
) {

    override fun visitImportDirective(importDirective: KtImportDirective) {
        TODO()
    }
}
