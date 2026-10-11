package com.shariftechnologies.scammonitor

import com.shariftechnologies.scammonitor.domain.engine.ScamDetectionEngine
import com.shariftechnologies.scammonitor.domain.reputation.DomainReputationChecker
import com.shariftechnologies.scammonitor.domain.reputation.LocalBlocklistProvider
import com.shariftechnologies.scammonitor.domain.reputation.ReputationDataParser
import com.shariftechnologies.scammonitor.domain.rules.CompiledRuleSet
import com.shariftechnologies.scammonitor.domain.rules.RuleParser
import java.io.File

/** Loads the very same asset files the Android app ships, so tests cover the real rule data. */
object TestSupport {
    private val assets = File("../app/src/main/assets")

    fun assetText(name: String): String = File(assets, name).readText()

    fun rules(): CompiledRuleSet = RuleParser.parse(assetText("scam_rules.json"))

    fun checker(): DomainReputationChecker = DomainReputationChecker(
        listOf(LocalBlocklistProvider(ReputationDataParser.parseBlocklist(assetText("domain_blocklist.json")))),
        ReputationDataParser.parseHeuristics(assetText("domain_heuristics.json")),
    )

    fun engine(rules: CompiledRuleSet = rules()) = ScamDetectionEngine({ rules }, { checker() })
}
