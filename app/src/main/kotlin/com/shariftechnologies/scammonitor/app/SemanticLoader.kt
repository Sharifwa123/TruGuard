package com.shariftechnologies.scammonitor.app

import android.content.Context
import android.util.Log
import com.shariftechnologies.scammonitor.domain.semantic.OrtSemanticScorer
import com.shariftechnologies.scammonitor.domain.semantic.SemanticModel
import com.shariftechnologies.scammonitor.domain.semantic.SemanticScorer
import com.shariftechnologies.scammonitor.domain.semantic.SemanticTokenizer
import com.shariftechnologies.scammonitor.domain.semantic.SemanticVerdict
import java.io.File

/**
 * Loads the on-device language model from the app's assets. The ONNX file is copied once to private storage (ONNX Runtime
 * reads it from a path), and again only if a new app version ships a different model. Any failure - missing file, no
 * native library for this CPU, not enough memory - just means the faster model works alone.
 *
 * A crash in native code or the system killing the app for memory leaves no exception to catch, so loading and every
 * scoring call is bracketed by a small marker file naming the step in progress. If the app finds a step still marked on
 * the next start, that step took the app down: the language model is switched off (never retried on its own) and the
 * step is shown in Settings so the problem can be diagnosed.
 */
internal object SemanticLoader {
    private const val MODEL = "semantic_model.onnx"
    private const val VOCAB = "semantic_vocab.txt"
    private const val META = "semantic_meta.json"
    private const val OK = "ok"
    private const val CRASHED = "crashed:"
    private const val FAILED = "failed:"

    private fun marker(context: Context) = File(context.filesDir, "semantic_guard.txt")

    private fun read(context: Context): String? = try { marker(context).takeIf { it.exists() }?.readText()?.trim() } catch (e: Exception) { null }

    private fun write(context: Context, value: String) {
        try { marker(context).writeText(value) } catch (e: Exception) { /* diagnostics only */ }
    }

    /** A step still marked from an earlier run means that run ended there. */
    private fun inProgressStep(stored: String?): String? =
        stored?.takeIf { it.isNotEmpty() && it != OK && !it.startsWith(CRASHED) && !it.startsWith(FAILED) }

    /** True if the language model took the app down earlier and so must stay off until the user retries. */
    fun tripped(context: Context): Boolean {
        val stored = read(context)
        inProgressStep(stored)?.let { write(context, CRASHED + it); return true }
        return stored?.startsWith(CRASHED) == true
    }

    /** What went wrong last time, in a few words for Settings, or null if nothing did. */
    fun problem(context: Context): String? {
        tripped(context)
        val stored = read(context) ?: return null
        return when {
            stored.startsWith(CRASHED) -> "it stopped the app while ${stored.removePrefix(CRASHED)}"
            stored.startsWith(FAILED) -> "it could not start (${stored.removePrefix(FAILED)})"
            else -> null
        }
    }

    /** Forget an earlier problem so the next load is attempted again. */
    fun clear(context: Context) { marker(context).delete() }

    fun load(context: Context): SemanticModel? {
        if (tripped(context)) return null
        var step = "starting"
        fun at(name: String) { step = name; write(context, name) }
        return try {
            val assets = context.assets
            at("reading settings")
            val meta = SemanticModel.parseMeta(assets.open(META).bufferedReader(Charsets.UTF_8).use { it.readText() }) ?: run {
                write(context, FAILED + "bad settings file"); return null
            }
            val target = File(context.filesDir, "semantic_${meta.version}.onnx")
            if (!target.exists() || target.length() == 0L) {
                at("copying the model")
                val tmp = File(context.filesDir, "semantic_${meta.version}.tmp")
                assets.open(MODEL).use { input -> tmp.outputStream().use { input.copyTo(it) } }
                if (!tmp.renameTo(target)) { write(context, FAILED + "could not save the model"); return null }
                context.filesDir.listFiles { f -> f.name.startsWith("semantic_") && f.name.endsWith(".onnx") && f.name != target.name }?.forEach { it.delete() }
            }
            at("reading the vocabulary")
            val tokenizer = assets.open(VOCAB).bufferedReader(Charsets.UTF_8).use { SemanticTokenizer.parse(it.lineSequence()) }
            at("starting the model")
            val scorer = OrtSemanticScorer(target.absolutePath, tokenizer)
            at("first test run")
            if (scorer.score("hello") == null) { write(context, FAILED + "test run returned nothing"); return null }
            write(context, OK)
            SemanticModel(Guarded(context, scorer), meta)
        } catch (t: Throwable) {
            Log.w("ScamMonitor", "Language model unavailable at '$step': ${t.javaClass.simpleName}")
            write(context, FAILED + "$step: ${t.javaClass.simpleName}")
            null
        }
    }

    /** Marks each scoring call, so a crash inside one is noticed on the next start. */
    private class Guarded(private val context: Context, private val inner: SemanticScorer) : SemanticScorer {
        override fun score(text: String): SemanticVerdict? {
            write(context, "reading a message")
            val result = inner.score(text)
            write(context, OK)
            return result
        }
    }
}
