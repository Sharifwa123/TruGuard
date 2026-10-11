package com.shariftechnologies.scammonitor.data.rules

import com.shariftechnologies.scammonitor.domain.reputation.DomainReputationChecker
import com.shariftechnologies.scammonitor.domain.reputation.LocalBlocklistProvider
import com.shariftechnologies.scammonitor.domain.reputation.ReputationDataParser

/**
 * Builds the [DomainReputationChecker] from bundled assets. Only the offline local blocklist is
 * wired in version 1; an online provider would be appended to the list here.
 */
class DomainReputationRepository(private val assets: AssetReader) {
    val checker: DomainReputationChecker by lazy {
        DomainReputationChecker(
            providers = listOf(LocalBlocklistProvider(ReputationDataParser.parseBlocklist(assets.readOrNull("domain_blocklist.json")))),
            heuristics = ReputationDataParser.parseHeuristics(assets.readOrNull("domain_heuristics.json")),
        )
    }
}
