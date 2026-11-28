package com.plcoding.cardigan.tooling.detekt

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.single
import dev.detekt.api.Config
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider
import org.junit.jupiter.api.Test
import java.util.ServiceLoader

class CardiganRuleSetProviderTest {
    // tests.md: be-detekt-konsist C11
    @Test
    fun `the provider exposes rule set cardigan with exactly NoMockingLibraries`() {
        val provider = CardiganRuleSetProvider()

        val ruleSet = provider.instance()

        assertThat(provider.ruleSetId).isEqualTo(RuleSetId("cardigan"))
        assertThat(ruleSet.id).isEqualTo(RuleSetId("cardigan"))
        assertThat(ruleSet.rules.values.map { it(Config.empty) }).single().isInstanceOf<NoMockingLibraries>()
    }

    // tests.md: be-detekt-konsist C13
    @Test
    fun `the provider is registered for ServiceLoader discovery`() {
        val providers = ServiceLoader.load(RuleSetProvider::class.java).toList()

        assertThat(providers.filter { it.ruleSetId == RuleSetId("cardigan") })
            .single()
            .isInstanceOf<CardiganRuleSetProvider>()
    }
}
