package com.shariftechnologies.scammonitor.domain.intent

/** Turns a classifier verdict into a rule-engine score and a plain-language reason. */
object IntentScoring {
    const val RULE_ID = "intent_model"

    /** Score for a probability. Thresholds come from the held-out evaluation in docs/INTENT_MODEL.md. */
    fun baseScore(probability: Double): Int = when {
        probability >= 0.95 -> 45
        probability >= 0.90 -> 38
        probability >= 0.80 -> 26
        probability >= 0.75 -> 15
        else -> 0
    }

    /**
     * Rules that already fired say much the same thing, so the model adds less on top of them (but still adds: two
     * independent methods agreeing is stronger evidence than one).
     */
    fun contribution(probability: Double, ruleScoreSoFar: Int): Int {
        val base = baseScore(probability)
        if (base == 0) return 0
        val overlap = minOf(ruleScoreSoFar, 60) / 60.0
        return maxOf(1, (base * (1.0 - 0.6 * overlap)).toInt())
    }

    fun explanation(group: String): String = when (group) {
        "secret_request" -> "This message pushes you to hand over a code, PIN, password or login. Real companies and real friends don't ask for these in a chat."
        "payment_request" -> "This message steers you toward sending money or paying a fee. Check with the person or company through a number you already trust."
        "threat_pressure" -> "This message uses threats or pressure to rush you. Scammers create panic so you act before you think."
        "reward_bait" -> "This message offers a reward, job or easy profit in exchange for money or details. Genuine offers don't ask you to pay first."
        "impersonation_trust" -> "This message looks like someone building trust, or pretending to be someone you know in trouble. Confirm by calling them directly."
        "access_install" -> "This message asks you to install something or give access to your phone. Never do this for someone who contacted you."
        else -> "This message is worded like known scams, even though it uses no familiar phrases. Be careful."
    }
}
