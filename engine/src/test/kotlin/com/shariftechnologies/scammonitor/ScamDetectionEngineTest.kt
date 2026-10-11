package com.shariftechnologies.scammonitor

import com.shariftechnologies.scammonitor.domain.model.ScanInput
import com.shariftechnologies.scammonitor.domain.model.Severity
import com.shariftechnologies.scammonitor.domain.rules.RuleParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScamDetectionEngineTest {
    private val engine = TestSupport.engine()
    private fun scan(body: String, title: String = "") = engine.analyze(ScanInput(title, body))

    @Test fun `bundled rules load cleanly with eleven starter rules`() {
        val rules = TestSupport.rules()
        assertFalse("bundled rules must not fall back", rules.isFallback)
        assertEquals(emptyList<String>(), rules.warnings)
        assertEquals(11, rules.rules.size)
    }

    @Test fun `pin request is high severity`() {
        val r = scan("Please send your MoMo PIN to complete the verification", "MTN MoMo")
        assertTrue(r.flagged)
        assertEquals(Severity.HIGH, r.severity)
        assertTrue("pin_otp_request" in r.matchedRuleIds)
        assertTrue(r.explanations.any { it.contains("PIN") })
    }

    @Test fun `fake reversal with payment instruction is high`() {
        val r = scan("I sent you GHS 200 by mistake. Please reverse the money. Send GHS 200 to 0240000000 now.")
        assertEquals(Severity.HIGH, r.severity)
        assertTrue(r.matchedRuleIds.containsAll(listOf("fake_reversal", "payment_instruction", "combo.reversal_payment")))
    }

    @Test fun `account block threat with lookalike link is high`() {
        val r = scan("Your MoMo account will be blocked today. Verify your details at https://mtn-momo-verify.example/login")
        assertEquals(Severity.HIGH, r.severity)
        assertTrue("account_block_threat" in r.matchedRuleIds)
        assertTrue("signal.suspicious_link" in r.matchedRuleIds)
        assertTrue("combo.block_threat_link" in r.matchedRuleIds)
        assertEquals(listOf("mtn-momo-verify.example"), r.suspiciousDomains)
    }

    @Test fun `reward needing an advance fee is high`() {
        val r = scan("Congratulations! You have won GHS 5000. Pay a GHS 50 processing fee to claim your cash prize.")
        assertEquals(Severity.HIGH, r.severity)
        assertTrue(r.matchedRuleIds.containsAll(listOf("fake_reward", "advance_fee", "combo.reward_fee")))
    }

    @Test fun `urgent message with synthetic blocked domain is high`() {
        val r = scan("URGENT: confirm now at http://blocked.example/x")
        assertEquals(Severity.HIGH, r.severity)
        assertTrue("signal.blocked_domain" in r.matchedRuleIds)
        assertTrue("urgency" in r.matchedRuleIds)
        assertTrue(r.explanations.any { it.contains("blocklist") })
    }

    @Test fun `subdomain of subdomain-matching blocklist entry is blocked but unrelated suffix is not`() {
        assertTrue("signal.blocked_domain" in scan("see https://a.scam-test.example/").matchedRuleIds)
        assertFalse("signal.blocked_domain" in scan("see https://notscam-test.example/").matchedRuleIds)
        assertFalse("signal.blocked_domain" in scan("see https://a.blocked.example/").matchedRuleIds) // exact-only entry
    }

    @Test fun `whatsapp code forwarding request is flagged`() {
        for (text in listOf(
            "Please we have sent you a code,send it to me so we can add you to the group",
            "I sent you a code by mistake, forward it back to me please",
            "Send me the 6 digit code you just received",
        )) {
            val r = scan(text, "@SOMEONE")
            assertTrue("code_forwarding for [$text]", "code_forwarding" in r.matchedRuleIds)
            assertTrue(text, r.flagged && r.severity.atLeast(Severity.MEDIUM))
        }
    }

    @Test fun `ordinary talk about codes is not flagged`() {
        for (text in listOf(
            "Your verification code is 123456. Do not share it with anyone.",
            "I will send you the gate code tomorrow",
            "The promo code is SAVE10, use it at checkout",
            "Can you send me the report when ready?",
        )) assertFalse("[$text]", scan(text).matchedRuleIds.contains("code_forwarding"))
    }

    @Test fun `normal transaction notification is not high risk`() {
        val r = scan("You have sent GHS 50.00 to KOFI MENSAH 0241234567. Transaction ID 123456789. New balance GHS 120.50.", "MoMo")
        assertFalse(r.severity.atLeast(Severity.MEDIUM))
        assertFalse(r.flagged)
    }

    @Test fun `legitimate otp notification with a do-not-share warning is not flagged`() {
        val r = scan("Your OTP is 482913. It expires in 5 minutes. Never share your PIN or OTP with anyone.", "MyBank")
        assertFalse(r.flagged)
        assertFalse("pin_otp_request" in r.matchedRuleIds)
    }

    @Test fun `plain account notice without action request has minimal risk`() {
        val r = scan("Your monthly account statement for September is now available in the app.", "MyBank")
        assertEquals(Severity.INFO, r.severity)
        assertEquals(0, r.riskScore)
        assertFalse(r.flagged)
    }

    @Test fun `urgency alone is weak`() {
        val r = scan("Please reply urgently about the meeting")
        assertEquals(Severity.INFO, r.severity)
        assertFalse(r.flagged)
    }

    @Test fun `single common words never reach high`() {
        for (w in listOf("account", "payment", "bank", "money", "momo", "transfer")) {
            assertFalse(w, scan("Reminder about the $w").severity.atLeast(Severity.MEDIUM))
        }
    }

    @Test fun `malformed urls do not crash and are not flagged as hosts`() {
        val r = scan("see http:// and https://.. and www. and http://[::1 and h t t p://x")
        assertTrue(r.domains.isEmpty())
        assertFalse(r.flagged)
    }

    @Test fun `deceptive lookalike hostnames are flagged`() {
        for (url in listOf("https://mtn.com.gh.secure-login.example/", "https://rnomo-mtn.example", "https://mtn.com.gh@evil.example/", "http://192.168.4.4/pay", "https://bit.ly/abc")) {
            val r = scan("Open $url")
            assertTrue("$url should be suspicious", "signal.suspicious_link" in r.matchedRuleIds)
        }
    }

    @Test fun `official domain and its subdomain are not flagged for brand abuse`() {
        val r = scan("Details at https://www.mtn.com.gh/momo")
        assertTrue(r.domains.single().flags.isEmpty())
    }

    @Test fun `urgency plus suspicious link scores higher than either alone`() {
        val linkOnly = scan("See https://bit.ly/xyz").riskScore
        val urgencyOnly = scan("Act now, within 10 minutes").riskScore
        val both = scan("Act now, within 10 minutes: https://bit.ly/xyz")
        assertTrue(both.riskScore > linkOnly + urgencyOnly)
        assertTrue("combo.urgency_suspicious_link" in both.matchedRuleIds)
    }

    @Test fun `evidence masks digit runs and contains no control characters`() {
        val r = scan("Send your pin 123456 now\u0007\u202E")
        val evidence = r.evidence.joinToString()
        assertFalse(evidence.contains("123456"))
        assertFalse(evidence.any { it.isISOControl() || it == '\u202E' })
    }

    @Test fun `empty input yields info result`() {
        val r = scan("   ")
        assertEquals(Severity.INFO, r.severity)
        assertFalse(r.flagged)
    }

    @Test fun `disabled rule does not contribute`() {
        val disabled = TestSupport.rules().withDisabled(setOf("pin_otp_request"))
        val r = TestSupport.engine(disabled).analyze(ScanInput("", "Send your PIN"))
        assertFalse("pin_otp_request" in r.matchedRuleIds)
    }

    @Test fun `pathological input finishes quickly`() {
        val start = System.nanoTime()
        scan("a".repeat(5000) + " pin " + "send ".repeat(2000))
        assertTrue((System.nanoTime() - start) / 1_000_000 < 2000)
    }

    @Test fun `missing or invalid rule data falls back safely`() {
        for (bad in listOf(null, "", "not json", "{}", """{"schemaVersion":99,"rules":[]}""")) {
            val rules = RuleParser.parse(bad)
            assertTrue("fallback for [$bad]", rules.isFallback)
            val r = TestSupport.engine(rules).analyze(ScanInput("", "Please share your PIN"))
            assertEquals(Severity.HIGH, r.severity)
        }
    }

    @Test fun `invalid individual rules are skipped with warnings`() {
        val json = """{"schemaVersion":1,"rules":[
          {"id":"good","description":"d","category":"c","score":40,"patterns":["hello"],"explanation":"e"},
          {"id":"bad_regex","description":"d","category":"c","score":40,"patterns":["(unclosed"],"explanation":"e"},
          {"id":"nested","description":"d","category":"c","score":40,"patterns":["(a+)+${'$'}"],"explanation":"e"},
          {"id":"good","description":"d","category":"c","score":40,"patterns":["dup"],"explanation":"e"},
          {"id":"zero","description":"d","category":"c","score":0,"patterns":["x"],"explanation":"e"}]}"""
        val rules = RuleParser.parse(json)
        assertFalse(rules.isFallback)
        assertEquals(listOf("good"), rules.rules.map { it.id })
        assertEquals(4, rules.warnings.size)
    }
}
