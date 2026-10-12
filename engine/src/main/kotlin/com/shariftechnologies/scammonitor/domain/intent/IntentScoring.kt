package com.shariftechnologies.scammonitor.domain.intent

/** Plain-language reasons for the kinds of manipulation the intent model recognises. */
object IntentScoring {
    const val RULE_ID = "intent_model"

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
