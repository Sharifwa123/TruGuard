package com.shariftechnologies.scammonitor.domain.model

/** Risk levels, ordered from weakest to strongest. */
enum class Severity {
    INFO,
    LOW,
    MEDIUM,
    HIGH;

    fun atLeast(other: Severity): Boolean = this >= other
}
