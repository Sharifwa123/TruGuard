package com.shariftechnologies.scammonitor.testsender

/**
 * Synthetic notification texts. They contain no real people, numbers, PINs or links: every domain
 * uses a reserved ".example" name and every number is a made-up placeholder.
 */
data class Scenario(val label: String, val title: String, val body: String, val expectation: String)

object Scenarios {
    val all = listOf(
        Scenario("PIN request", "MoMo Support", "Please send your MoMo PIN to complete the verification of your wallet.", "HIGH, warning shown"),
        Scenario("Reversal + payment", "Unknown sender", "I sent you GHS 200 by mistake. Please reverse the money. Send GHS 200 to 0240000000 now.", "HIGH, warning shown"),
        Scenario("Block threat + link", "Account Alert", "Your MoMo account will be blocked today. Verify your details at https://mtn-momo-verify.example/login", "HIGH, warning shown"),
        Scenario("Reward + fee", "Promo", "Congratulations! You have won GHS 5000. Pay a GHS 50 processing fee to claim your cash prize.", "HIGH, warning shown"),
        Scenario("Urgent + blocked domain", "Notice", "URGENT: confirm now at http://blocked.example/x", "HIGH (local blocklist), warning shown"),
        Scenario("Normal transaction", "MoMo", "You have sent GHS 50.00 to TEST PERSON 0200000000. Transaction ID 000000001. New balance GHS 120.50.", "INFO, no flag, no warning"),
        Scenario("Legit OTP notice", "Test Bank", "Your OTP is 000000. It expires in 5 minutes. Never share your PIN or OTP with anyone.", "INFO, no flag, no warning"),
    )
}
