package com.shariftechnologies.scammonitor

import com.shariftechnologies.scammonitor.domain.model.ScanInput
import com.shariftechnologies.scammonitor.domain.model.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Detection must not depend on exact phrasing: misspellings, shorthand, leetspeak, reordering. */
class FuzzyDetectionTest {
    private val engine = TestSupport.engine()
    private fun scan(text: String) = engine.analyze(ScanInput("", text))
    private fun assertFlags(rule: String, vararg texts: String) {
        for (t in texts) {
            val r = scan(t)
            assertTrue("[$t] should match $rule but matched ${r.matchedRuleIds}", rule in r.matchedRuleIds)
            assertTrue("[$t] should be flagged", r.flagged)
        }
    }

    @Test fun `code forwarding survives misspelling shorthand and reordering`() = assertFlags(
        "code_forwarding",
        "Please we have sent you a code,send it to me so we can add you to the group",
        "Pleas we hav sent u a cod, snd it 2 me so we can add u to d group",
        "plz forwrd the c0de dat was sent to ur phone, to me",
        "Sh@re the O.T.P you got with me",
        "we sent u a c o d e, pls s e n d it back to me",
        "Pleeeease send me back the code you just recieved",
        "the code you received on whatsapp, give it back to me quick",
    )

    @Test fun `pin and otp requests survive obfuscation`() = assertFlags(
        "pin_otp_request",
        "Send your MoMo PIN to verify",
        "pls shre ur m0mo p1n with me",
        "Plss gv me ur O T P",
        "S.E.N.D your p.i.n now",
        "Share ur passwrd with us",
        "tell me the verifcation code on your phone",
    )

    @Test fun `reversal advance fee reward and block wording variants`() {
        assertFlags("fake_reversal", "pls reversse d mony i sent by mistke", "I sent u money by mistake kindly refund me the cash")
        assertFlags("advance_fee", "pay the clearence fee 2 release d prize", "Kindly paay a small procesing fee to unlock your loan")
        assertFlags("fake_reward", "Congrts u hav won a prise, clam it now", "You are the lucky winner, collect your prize today")
        assertFlags("account_block_threat", "Ur acct wil be blokd unless u verfy", "Your MoMo wallet is suspended, click the link to reactivate")
    }

    @Test fun `spaced and stretched words are understood`() {
        assertTrue("pin_otp_request" in scan("send ur p i n").matchedRuleIds)
        assertTrue("code_forwarding" in scan("we sent a cooooode, forward iiiit to me").matchedRuleIds)
    }

    @Test fun `negated and ordinary uses are not flagged`() {
        for (t in listOf(
            "Your OTP is 482913. Never share your PIN or OTP with anyone.",
            "Do not share ur pin with anybody, even bank staff",
            "dont forward the otp to anyone please",
            "I will send you the gate code tomorrow",
            "Can you send me the wifi code?",
            "Send me the report you got from Kofi",
            "Please confirm your bank transfer of lunch money",
            "Transaction reversed due to an error. Your refund of GHS 20 is complete.",
            "Mode of payment is cash, see you at the node",
            "Read the verification instructions in the app",
            "Your reward points: you will receive a discount",
        )) {
            val r = scan(t)
            assertFalse("[$t] must not reach MEDIUM but matched ${r.matchedRuleIds}", r.severity.atLeast(Severity.MEDIUM))
        }
    }

    @Test fun `everyday messages stay below medium`() {
        val corpus = listOf(
            "Your account has been closed as requested. Thank you for banking with us.",
            "Congratulations on your new job! Come and collect your cake at 5",
            "We sent your password reset link. Click it to choose a new password.",
            "MTN MoMo: You have received GHS 50.00 from KOFI 0241234567. Balance GHS 120.",
            "Cash deposit of GHS 100.00 confirmed at branch.",
            "Your OTP is 123456, valid for 5 minutes. Do not share it.",
            "Send me the pics when you get home",
            "Pay the school fees before Friday please",
            "Hey, I got your message, call me back when free",
            "Please send the minutes of the meeting to me",
            "The line is locked, I will unlock it after lunch",
            "Your parcel was delivered. Show the code on your app to the rider.",
            "Reminder: your card ending 4242 was used for GHS 30 at Melcom.",
            "Mum says come back home, dinner is ready",
            "Verify your email address to finish signing up for the newsletter",
            "You have won the group chat quiz, well done everyone!",
            "Update: the bank will be closed on Monday for the holiday",
            "Reply YES to confirm your appointment tomorrow",
            "Please give me a call, I received your money",
            "Transfer completed. Reference 123456789. Thank you.",
        )
        for (t in corpus) {
            val r = scan(t)
            assertFalse("[$t] matched ${r.matchedRuleIds} score ${r.riskScore}", r.severity.atLeast(Severity.MEDIUM))
        }
    }

    @Test fun `homoglyph and accented lookalikes are folded for regex rules`() {
        // Cyrillic 'о' and 'е' standing in for Latin letters.
        val r = scan("Pleаse sеnd your PIN to complete verification")
        assertTrue(r.matchedRuleIds.toString(), "pin_otp_request" in r.matchedRuleIds)
    }

    @Test fun `plain money talk with fuzzy words stays low`() {
        val r = scan("Mum sent me cash for the wrong number, I will return it later")
        assertEquals(false, r.severity.atLeast(Severity.MEDIUM))
    }

    @Test fun `long noisy text finishes within budget`() {
        val start = System.nanoTime()
        scan((1..800).joinToString(" ") { "wrd$it c0d3 s3nd sh4re" })
        assertTrue((System.nanoTime() - start) / 1_000_000 < 3000)
    }
}
