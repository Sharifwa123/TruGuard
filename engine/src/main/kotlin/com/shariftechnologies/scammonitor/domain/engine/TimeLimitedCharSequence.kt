package com.shariftechnologies.scammonitor.domain.engine

class RegexTimeoutException : RuntimeException("Regex evaluation exceeded its time budget")

/**
 * Wraps text so that every character read checks a deadline. A backtracking regex that runs away
 * therefore aborts with [RegexTimeoutException] instead of hanging the processing worker.
 */
class TimeLimitedCharSequence(
    private val inner: CharSequence,
    private val deadlineNanos: Long,
    private val nanoTime: () -> Long = System::nanoTime,
) : CharSequence {
    private var reads = 0

    override val length: Int get() = inner.length

    override fun get(index: Int): Char {
        // Checking the clock on every read is costly; every 1024th read is precise enough.
        if ((++reads and 0x3FF) == 0 && nanoTime() > deadlineNanos) throw RegexTimeoutException()
        return inner[index]
    }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
        TimeLimitedCharSequence(inner.subSequence(startIndex, endIndex), deadlineNanos, nanoTime)

    override fun toString(): String = inner.toString()
}
