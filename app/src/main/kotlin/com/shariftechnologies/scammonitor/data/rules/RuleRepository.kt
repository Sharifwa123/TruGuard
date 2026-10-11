package com.shariftechnologies.scammonitor.data.rules

import com.shariftechnologies.scammonitor.data.prefs.SettingsRepository
import com.shariftechnologies.scammonitor.domain.rules.CompiledRuleSet
import com.shariftechnologies.scammonitor.domain.rules.RuleParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

data class RuleInfo(
    val id: String,
    val description: String,
    val category: String,
    val score: Int,
    /** Enabled in the rule file itself; a file-disabled rule cannot be switched on from the UI. */
    val enabledInFile: Boolean,
    val userEnabled: Boolean,
)

data class RuleSetInfo(val version: String, val isFallback: Boolean, val warningCount: Int, val rules: List<RuleInfo>)

/**
 * Serves the validated rule set: the bundled `scam_rules.json` with the user's disabled-rule
 * overrides applied. A future validated local update file would be parsed by the same [RuleParser].
 */
class RuleRepository(
    private val assets: AssetReader,
    private val settings: SettingsRepository,
) {
    /** Parsed once on first use (always on a background thread); falls back safely if invalid. */
    private val bundled: CompiledRuleSet by lazy { RuleParser.parse(assets.readOrNull(RULES_ASSET)) }

    /** Snapshot for the detection engine. */
    fun current(): CompiledRuleSet = bundled.withDisabled(settings.disabledRuleIds.value)

    /** Rule list with user overrides, for the settings screen. */
    fun observeRuleSet(): Flow<RuleSetInfo> =
        settings.disabledRuleIds.map { disabled ->
            RuleSetInfo(
                version = bundled.rulesVersion,
                isFallback = bundled.isFallback,
                warningCount = bundled.warnings.size,
                rules = bundled.rules.map { RuleInfo(it.id, it.description, it.category, it.score, it.enabled, it.id !in disabled) },
            )
        }.flowOn(Dispatchers.IO)

    private companion object {
        const val RULES_ASSET = "scam_rules.json"
    }
}
