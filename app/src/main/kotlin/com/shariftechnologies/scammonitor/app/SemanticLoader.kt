package com.shariftechnologies.scammonitor.app

import android.content.Context
import android.util.Log
import com.shariftechnologies.scammonitor.domain.semantic.OrtSemanticScorer
import com.shariftechnologies.scammonitor.domain.semantic.SemanticModel
import com.shariftechnologies.scammonitor.domain.semantic.SemanticTokenizer
import java.io.File

/**
 * Loads the on-device language model from the app's assets. The ONNX file is copied once to private storage (ONNX Runtime
 * reads it from a path), and again only if a new app version ships a different model. Any failure - missing file, no
 * native library for this CPU, not enough memory - just means the faster model works alone.
 */
internal object SemanticLoader {
    private const val MODEL = "semantic_model.onnx"
    private const val VOCAB = "semantic_vocab.txt"
    private const val META = "semantic_meta.json"

    fun load(context: Context): SemanticModel? {
        return try {
            val assets = context.assets
            val meta = SemanticModel.parseMeta(assets.open(META).bufferedReader(Charsets.UTF_8).use { it.readText() }) ?: return null
            val target = File(context.filesDir, "semantic_${meta.version}.onnx")
            if (!target.exists() || target.length() == 0L) {
                val tmp = File(context.filesDir, "semantic_${meta.version}.tmp")
                assets.open(MODEL).use { input -> tmp.outputStream().use { input.copyTo(it) } }
                if (!tmp.renameTo(target)) return null
                context.filesDir.listFiles { f -> f.name.startsWith("semantic_") && f.name != target.name }?.forEach { it.delete() }
            }
            val tokenizer = assets.open(VOCAB).bufferedReader(Charsets.UTF_8).use { SemanticTokenizer.parse(it.lineSequence()) }
            SemanticModel(OrtSemanticScorer(target.absolutePath, tokenizer), meta)
        } catch (t: Throwable) {
            Log.w("ScamMonitor", "Language model unavailable: ${t.javaClass.simpleName}")
            null
        }
    }
}
