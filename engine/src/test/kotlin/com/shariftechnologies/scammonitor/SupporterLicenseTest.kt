package com.shariftechnologies.scammonitor

import com.shariftechnologies.scammonitor.supporter.SupporterLicense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class SupporterLicenseTest {
    private fun keys(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
    private fun url(b: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(b)
    private fun publicKey(k: KeyPair) = Base64.getEncoder().encodeToString(k.public.encoded)

    private fun issue(k: KeyPair, holder: String): String {
        val payload = "TG1|$holder|2026-10-12".toByteArray()
        val sig = Signature.getInstance("SHA256withECDSA").run { initSign(k.private); update(payload); sign() }
        return "TG1.${url(payload)}.${url(sig)}"
    }

    @Test fun `a code signed with the private key is valid and names its holder`() {
        val k = keys()
        val r = SupporterLicense.verify(issue(k, "Ama Mensah"), publicKey(k))
        assertTrue(r.valid)
        assertEquals("Ama Mensah", r.holder)
    }

    @Test fun `surrounding whitespace from copy and paste is tolerated`() {
        val k = keys()
        assertTrue(SupporterLicense.verify("  " + issue(k, "A") + "\n", publicKey(k)).valid)
    }

    @Test fun `a code signed with a different key is rejected`() {
        assertFalse(SupporterLicense.verify(issue(keys(), "A"), publicKey(keys())).valid)
    }

    @Test fun `a tampered payload or signature is rejected`() {
        val k = keys()
        val parts = issue(k, "Ama").split('.')
        val forgedPayload = url("TG1|Someone Else|2026-10-12".toByteArray())
        assertFalse(SupporterLicense.verify("TG1.$forgedPayload.${parts[2]}", publicKey(k)).valid)
        assertFalse(SupporterLicense.verify("TG1.${parts[1]}.${parts[2].reversed()}", publicKey(k)).valid)
    }

    @Test fun `garbage blank or keyless input is never valid and never throws`() {
        val k = keys()
        val code = issue(k, "A")
        listOf(null, "", "   ", "hello", "TG1.a.b", "TG1..", "TG2.x.y", code.replace("TG1", "TG2"), "a.b.c.d").forEach {
            assertFalse("should be invalid: $it", SupporterLicense.verify(it, publicKey(k)).valid)
        }
        assertFalse("no public key configured", SupporterLicense.verify(code, null).valid)
        assertFalse("blank public key", SupporterLicense.verify(code, "  ").valid)
        assertFalse("garbage public key", SupporterLicense.verify(code, "not a key").valid)
    }
}
