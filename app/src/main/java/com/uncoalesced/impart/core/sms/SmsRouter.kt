// Engineered by uncoalesced
package com.uncoalesced.impart.core.sms

import com.uncoalesced.impart.core.security.AlertWire
import com.uncoalesced.impart.core.security.LEGACY_ALERT_PREFIX

sealed interface SmsRoute {
    // Has the shape of an Impart alert (or the old prefix): goes to the verifier, never to
    // the Telephony provider.
    data object Alert : SmsRoute

    // Anything else: stored and shown like a normal SMS app would.
    data object Ordinary : SmsRoute
}

// The one decision the default-SMS receiver makes before anything is written anywhere.
object SmsRouter {
    fun route(body: String): SmsRoute =
        if (body.startsWith(LEGACY_ALERT_PREFIX) || AlertWire.couldBeAlert(body)) SmsRoute.Alert else SmsRoute.Ordinary
}
