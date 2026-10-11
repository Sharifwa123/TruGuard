package com.shariftechnologies.scammonitor.domain.reputation

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Schema of `domain_blocklist.json`. */
@Serializable
data class BlocklistDto(
    val schemaVersion: Int,
    val updatedAt: String? = null,
    val description: String = "",
    val entries: List<BlocklistEntryDto> = emptyList(),
)

@Serializable
data class BlocklistEntryDto(
    val domain: String,
    /** Only when true are subdomains of [domain] matched too. Otherwise exact match only. */
    val matchSubdomains: Boolean = false,
    val type: String = "PHISHING",
    val source: String,
    val reason: String,
    val addedAt: String? = null,
)

/** Schema of `domain_heuristics.json`. */
@Serializable
data class HeuristicsDto(
    val schemaVersion: Int,
    val shorteners: List<String> = emptyList(),
    /** Multi-label public suffixes (e.g. com.gh) so the registrable domain can be approximated. */
    val multiPartSuffixes: List<String> = emptyList(),
    val brands: List<BrandDto> = emptyList(),
)

@Serializable
data class BrandDto(
    /** Lower-case token that identifies the brand inside a hostname, e.g. "mtn". */
    val token: String,
    /** Domains the brand really uses; a host equal to or under one of these is not flagged. */
    val officialDomains: List<String>,
)

data class BlocklistEntry(
    val domain: String,
    val matchSubdomains: Boolean,
    val type: String,
    val source: String,
    val reason: String,
)

class DomainHeuristics(
    val shorteners: Set<String>,
    val multiPartSuffixes: Set<String>,
    val brands: List<BrandDto>,
) {
    companion object {
        val EMPTY = DomainHeuristics(emptySet(), emptySet(), emptyList())
    }
}

object ReputationDataParser {
    private val json = Json { ignoreUnknownKeys = true }

    /** Returns valid entries only; an unparsable file yields an empty list (engine still works). */
    fun parseBlocklist(text: String?): List<BlocklistEntry> {
        if (text.isNullOrBlank()) return emptyList()
        val dto = runCatching { json.decodeFromString(BlocklistDto.serializer(), text) }.getOrNull() ?: return emptyList()
        if (dto.schemaVersion != 1) return emptyList()
        return dto.entries.mapNotNull { e ->
            val domain = e.domain.trim().trimEnd('.').lowercase()
            if (domain.isEmpty() || domain.contains('/') || domain.contains(' ') || domain.contains('*')) null
            else BlocklistEntry(domain, e.matchSubdomains, e.type, e.source, e.reason)
        }
    }

    fun parseHeuristics(text: String?): DomainHeuristics {
        if (text.isNullOrBlank()) return DomainHeuristics.EMPTY
        val dto = runCatching { json.decodeFromString(HeuristicsDto.serializer(), text) }.getOrNull() ?: return DomainHeuristics.EMPTY
        if (dto.schemaVersion != 1) return DomainHeuristics.EMPTY
        return DomainHeuristics(
            shorteners = dto.shorteners.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet(),
            multiPartSuffixes = dto.multiPartSuffixes.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet(),
            brands = dto.brands.filter { it.token.length >= 3 }.map { BrandDto(it.token.lowercase(), it.officialDomains.map { d -> d.lowercase() }) },
        )
    }
}
