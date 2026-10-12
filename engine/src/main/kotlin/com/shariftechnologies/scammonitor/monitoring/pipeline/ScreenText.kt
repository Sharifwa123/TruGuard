package com.shariftechnologies.scammonitor.monitoring.pipeline

import com.shariftechnologies.scammonitor.core.Hashing
import com.shariftechnologies.scammonitor.core.TextSanitizer
import com.shariftechnologies.scammonitor.domain.model.ScanInput
import java.util.Locale

/**
 * Apps whose screens can never be monitored, whatever the user chooses: system chrome, keyboards, password
 * managers and finance apps. Screen monitoring is opt-in per app on top of this.
 */
object ScreenTextPolicy {
    private val EXACT = setOf("android", "com.android.systemui", "com.android.settings", "com.android.permissioncontroller", "com.google.android.permissioncontroller", "com.android.packageinstaller")

    private val FRAGMENTS = listOf(
        "launcher", "inputmethod", "keyboard", "swiftkey", "touchtype", "gboard", "fleksy", "systemui", "bitwarden", "lastpass", "1password", "keepass", "dashlane",
        "authenticator", "bank", "momo", "mobilemoney", "paypal", "wallet", "permissioncontroller", "packageinstaller",
    )

    /** [extraExcluded] lets the Android layer add the phone's actually enabled keyboards, whatever their package names. */
    fun isMonitorable(packageName: String?, ownPackage: String, extraExcluded: Set<String> = emptySet()): Boolean {
        if (packageName.isNullOrEmpty() || packageName == ownPackage || packageName in extraExcluded) return false
        val p = packageName.lowercase(Locale.ROOT)
        return p !in EXACT && FRAGMENTS.none { p.contains(it) }
    }
}

/**
 * Remembers which on-screen text chunks were already analysed per app, so that only newly appeared text
 * (a new chat bubble, a new post) is scanned and nothing is re-warned when the screen redraws or scrolls back.
 * Only short hashes are kept, never the text.
 */
class ScreenChunkTracker(private val maxPerPackage: Int = 600, private val maxPackages: Int = 40) {
    private val lock = Any()
    private val whitespace = Regex("\\s+")
    private val packages = object : LinkedHashMap<String, LinkedHashSet<String>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LinkedHashSet<String>>?) = size > maxPackages
    }

    /** Returns the chunks not seen before for [packageName], in order, and remembers them. */
    fun newChunks(packageName: String, chunks: List<String>): List<String> = synchronized(lock) {
        val seen = packages.getOrPut(packageName) { LinkedHashSet() }
        val fresh = ArrayList<String>()
        for (chunk in chunks) {
            val key = Hashing.sha256Hex(chunk.lowercase(Locale.ROOT).replace(whitespace, " ").trim(), length = 16)
            if (seen.add(key)) fresh += chunk
        }
        while (seen.size > maxPerPackage) seen.remove(seen.first())
        fresh
    }

    /** Forgets an app, e.g. when monitoring for it is switched off. */
    fun forget(packageName: String) { synchronized(lock) { packages.remove(packageName) } }
}

object ScreenEventFactory {
    const val MIN_CHUNK = 6
    const val MAX_CHUNK = 1000
    const val MAX_CHUNKS = 80
    private val whitespace = Regex("\\s+")

    /**
     * Builds one event from newly visible text. The engine judges runs of neighbouring chunks together, so a scam
     * spread over a few chat bubbles ("we sent you a code" / "send it to me") is still one message to the detector,
     * while unrelated bubbles, labels and buttons far apart on the screen cannot combine.
     */
    fun create(packageName: String, chunks: List<String>, nowMillis: Long): NotificationEvent? {
        val cleaned = chunks
            .map { TextSanitizer.truncate(TextSanitizer.clean(it).replace(whitespace, " "), MAX_CHUNK) }
            .filter { it.length >= MIN_CHUNK }
            .take(MAX_CHUNKS)
        if (cleaned.isEmpty()) return null
        val input = ScanInput("", TextSanitizer.truncate(cleaned.joinToString("\n"), NotificationContentAssembler.MAX_BODY), segments = cleaned)
        val key = "screen:$packageName"
        val fingerprint = NotificationContentAssembler.fingerprint(packageName, input)
        return NotificationEvent(
            eventId = NotificationContentAssembler.eventId(key, nowMillis, fingerprint),
            key = key,
            packageName = packageName,
            postTimeMillis = nowMillis,
            input = input,
            fingerprint = fingerprint,
            origin = EventOrigin.SCREEN,
        )
    }
}
