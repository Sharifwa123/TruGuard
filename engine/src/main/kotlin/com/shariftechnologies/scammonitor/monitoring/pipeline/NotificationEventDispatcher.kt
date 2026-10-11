package com.shariftechnologies.scammonitor.monitoring.pipeline

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

enum class SubmitResult { ACCEPTED, EXCLUDED_PACKAGE, EMPTY, DUPLICATE, STOPPED }

data class DispatcherStats(
    val processed: Long = 0,
    val failed: Long = 0,
    /** Events discarded because the bounded queue was full (oldest first). */
    val dropped: Long = 0,
)

/**
 * Hand-off point between Android's notification callbacks and background processing.
 *
 * - [submit] never blocks and never throws, so listener callbacks return immediately.
 * - The queue is bounded. When it is full the OLDEST waiting event is dropped and counted in
 *   [DispatcherStats.dropped]; a burst therefore costs bounded memory and the newest content wins.
 * - A fixed number of worker coroutines drain the queue; no coroutine is created per event.
 * - Identical updates (same key and content) seen within [dedupeWindowMillis] are ignored; changed
 *   content for the same key is processed. [forget] clears the memory for a removed notification.
 */
class NotificationEventDispatcher(
    private val scope: CoroutineScope,
    private val excludedPackages: Set<String>,
    private val processor: suspend (NotificationEvent) -> Unit,
    private val capacity: Int = 64,
    private val workerCount: Int = 2,
    private val dedupeWindowMillis: Long = 30 * 60_000L,
    private val maxRemembered: Int = 256,
    private val perEventTimeoutMillis: Long = 10_000,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onError: (Throwable) -> Unit = {},
) {
    private val stats = MutableStateFlow(DispatcherStats())
    val statistics: StateFlow<DispatcherStats> = stats.asStateFlow()

    private val channel = Channel<NotificationEvent>(
        capacity = capacity,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
        onUndeliveredElement = { event ->
            forgetFingerprint(event)
            stats.update { it.copy(dropped = it.dropped + 1) }
        },
    )

    private val lock = Any()
    private val recent = object : LinkedHashMap<String, Pair<String, Long>>(16, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<String, Long>>?) = size > maxRemembered
    }
    private var workers: List<Job> = emptyList()

    /** Starts the worker coroutines. Idempotent. */
    fun start() {
        synchronized(lock) {
            if (workers.any { it.isActive }) return
            workers = List(workerCount) { scope.launch { runWorker() } }
        }
    }

    fun stop() {
        synchronized(lock) { workers.forEach { it.cancel() }; workers = emptyList() }
        channel.close()
    }

    fun submit(event: NotificationEvent): SubmitResult {
        if (event.packageName in excludedPackages) return SubmitResult.EXCLUDED_PACKAGE
        if (event.input.isEmpty) return SubmitResult.EMPTY
        synchronized(lock) {
            val now = clock()
            val previous = recent[event.key]
            if (previous != null && previous.first == event.fingerprint && now - previous.second < dedupeWindowMillis) {
                return SubmitResult.DUPLICATE
            }
            recent[event.key] = event.fingerprint to now
        }
        val sent = channel.trySend(event)
        if (sent.isFailure) {
            forgetFingerprint(event)
            return SubmitResult.STOPPED
        }
        return SubmitResult.ACCEPTED
    }

    /** Called when Android reports the notification removed, so a later identical re-post is processed. */
    fun forget(key: String) {
        synchronized(lock) { recent.remove(key) }
    }

    private fun forgetFingerprint(event: NotificationEvent) {
        synchronized(lock) { if (recent[event.key]?.first == event.fingerprint) recent.remove(event.key) }
    }

    private suspend fun runWorker() {
        for (event in channel) {
            try {
                withTimeout(perEventTimeoutMillis) { processor(event) }
                stats.update { it.copy(processed = it.processed + 1) }
            } catch (e: CancellationException) {
                if (!currentCoroutineContext().isActive) throw e // real shutdown; a per-event timeout is just a failure
                stats.update { it.copy(failed = it.failed + 1) }
                onError(e)
            } catch (t: Throwable) {
                stats.update { it.copy(failed = it.failed + 1) }
                onError(t)
            }
        }
    }
}
