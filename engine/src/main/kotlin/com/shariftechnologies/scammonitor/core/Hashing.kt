package com.shariftechnologies.scammonitor.core

import java.security.MessageDigest

object Hashing {
    /** Hex SHA-256 of the parts joined with a separator that cannot appear in normal text. */
    fun sha256Hex(vararg parts: String, length: Int = 32): String {
        val digest = MessageDigest.getInstance("SHA-256")
        parts.forEachIndexed { index, part ->
            if (index > 0) digest.update(0x1F)
            digest.update(part.toByteArray(Charsets.UTF_8))
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.take(length)
    }
}
