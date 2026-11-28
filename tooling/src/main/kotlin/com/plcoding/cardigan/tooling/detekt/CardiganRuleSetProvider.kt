package com.plcoding.cardigan.tooling.detekt

import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider

/** Provides the `cardigan` rule set with the project's custom detekt rules. */
class CardiganRuleSetProvider : RuleSetProvider {

    override val ruleSetId: RuleSetId = RuleSetId("cardigan")

    override fun instance(): RuleSet = RuleSet(ruleSetId, listOf(::NoMockingLibraries))
}
