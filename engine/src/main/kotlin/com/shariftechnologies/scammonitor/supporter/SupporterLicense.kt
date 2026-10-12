package com.shariftechnologies.scammonitor.supporter

import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Offline "ad-free supporter" codes. After someone pays, the developer issues a code (see `tools/license`) that is a
 * payload signed with the developer's private key; the app only holds the matching *public* key, so it can check a
 * code without any server and nobody can forge one without the private key. Format: `TG1.<payload>.<signature>`
 * (both base64url). A blank or missing public key means no code is accepted.
 */
object SupporterLicense {
    private const val PREFIX = "TG1"

    class Result(val valid: Boolean, val holder: String?)

    private val invalid = Result(false, null)

    /** Never throws: anything malformed is simply not valid. */
    fun verify(code: String?, publicKeyBase64: String?): Result {
        if (code.isNullOrBlank() || publicKeyBase64.isNullOrBlank()) return invalid
        return try {
            val parts = code.trim().split('.')
            if (parts.size != 3 || parts[0] != PREFIX) return invalid
            val url = Base64.getUrlDecoder()
            val payload = url.decode(parts[1])
            val signature = url.decode(parts[2])
            val key: PublicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getMimeDecoder().decode(publicKeyBase64.trim())))
            val ok = Signature.getInstance("SHA256withECDSA").run { initVerify(key); update(payload); verify(signature) }
            if (!ok) return invalid
            // payload is "TG1|<holder>|<date>"
            val fields = String(payload, Charsets.UTF_8).split('|')
            if (fields.size < 3 || fields[0] != PREFIX) return invalid
            Result(true, fields[1].takeIf { it.isNotBlank() })
        } catch (e: Exception) {
            invalid
        }
    }
}
