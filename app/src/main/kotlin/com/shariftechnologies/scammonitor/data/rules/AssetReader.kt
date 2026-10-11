package com.shariftechnologies.scammonitor.data.rules

import android.content.Context

/** Reads bundled asset files as text. Returns null instead of throwing when a file is absent. */
class AssetReader(private val context: Context) {
    fun readOrNull(name: String): String? =
        try {
            context.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: java.io.IOException) {
            null
        }
}
