package com.shariftechnologies.scammonitor.domain.semantic

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.Closeable
import java.nio.LongBuffer

/**
 * Runs the quantised ONNX language model with ONNX Runtime. The same Java API ships for desktop (used by the engine
 * tests) and Android (`onnxruntime-android`, used by the app), so this one class serves both.
 *
 * Results for text it has just seen are remembered, because the same screen text arrives again and again.
 */
class OrtSemanticScorer(modelPath: String, private val tokenizer: SemanticTokenizer, threads: Int = 2) : SemanticScorer, Closeable {
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession = env.createSession(
        modelPath,
        OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(threads)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        },
    )
    private val cache = object : LinkedHashMap<String, SemanticVerdict>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SemanticVerdict>?) = size > CACHE
    }

    @Synchronized
    override fun score(text: String): SemanticVerdict? {
        cache[text]?.let { return it }
        return try {
            val ids = tokenizer.encode(text)
            val shape = longArrayOf(1, ids.size.toLong())
            val idTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(ids.size) { ids[it].toLong() }), shape)
            val maskTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(ids.size) { 1L }), shape)
            idTensor.use {
                maskTensor.use {
                    session.run(mapOf("input_ids" to idTensor, "attention_mask" to maskTensor)).use { out ->
                        val scam = (out[0].value as FloatArray)[0].toDouble()
                        @Suppress("UNCHECKED_CAST")
                        val group = (out[1].value as Array<FloatArray>)[0].map { it.toDouble() }.toDoubleArray()
                        SemanticVerdict(scam, group).also { cache[text] = it }
                    }
                }
            }
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    override fun close() {
        session.close()
    }

    private companion object { const val CACHE = 128 }
}
