package com.shariftechnologies.scammonitor

import com.shariftechnologies.scammonitor.domain.model.ScanInput
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * Not a test: a tool for the model trainer. Run with `DUMP_RULEHITS=1 ./gradlew :engine:test --tests '*DumpRuleHits*'`.
 * Writes, for every example in the .jsonl files of tools/intent/data, which rules the engine fires on it (rules alone, no model), so
 * the trainer can learn how far each rule should be trusted.
 */
class DumpRuleHits {
    @Test fun dump() {
        assumeTrue(System.getenv("DUMP_RULEHITS") == "1")
        val engine = TestSupport.engine()
        val dir = File("../tools/intent/data")
        val out = File(dir, "rulehits.jsonl")
        val sha = MessageDigest.getInstance("SHA-1")
        out.bufferedWriter().use { w ->
            dir.listFiles { f -> f.name.endsWith(".jsonl") && f.name != "rulehits.jsonl" }!!.sorted().forEach { f ->
                f.readLines().filter { it.isNotBlank() }.forEach { line ->
                    val text = Json.parseToJsonElement(line).jsonObject.getValue("text").jsonPrimitive.content
                    val ids = engine.analyze(ScanInput("", text)).matches.map { it.ruleId }
                    val digest = sha.digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
                    w.write(buildJsonObject { put("sha", digest); put("rules", JsonArray(ids.map { JsonPrimitive(it) })) }.toString())
                    w.newLine()
                }
            }
        }
    }
}
