// Engineered by uncoalesced
package com.uncoalesced.impart.core.services

import com.uncoalesced.impart.core.security.AlertVerdict
import com.uncoalesced.impart.domain.model.Contact
import com.uncoalesced.impart.domain.model.PanicPayload
import com.uncoalesced.impart.domain.model.displayName

// What the user sees for an incoming alert. Every verdict maps to exactly one notice and
// none maps to nothing: the intrusion pipeline is never silent. Pure, so the mapping is
// tested on the JVM (AlertNoticesTest); AlertIntake only posts what this returns.
sealed interface AlertNotice {
    // A heads-up on the status channel under one of the ImpartNotifications ids.
    data class Status(val id: Int, val title: String, val text: String) : AlertNotice

    // The other node acknowledged our panic.
    data class Acknowledged(val title: String, val text: String) : AlertNotice

    // A verified, fresh, first-seen panic from a trusted contact: raise the alarm.
    data class Alarm(val sender: Contact, val payload: PanicPayload.Panic, val delayMinutes: Long) : AlertNotice
}

object AlertNotices {

    fun forVerdict(verdict: AlertVerdict, formatTime: (Long) -> String): AlertNotice = when (verdict) {
        AlertVerdict.Unreadable -> AlertNotice.Status(
            ImpartNotifications.ID_UNREADABLE, "UNREADABLE ALERT RECEIVED",
            "An Impart alert arrived that this node could not decrypt. It was not encrypted to this node's key, or it was damaged in transit."
        )
        AlertVerdict.Malformed -> AlertNotice.Status(
            ImpartNotifications.ID_UNREADABLE, "MALFORMED ALERT RECEIVED",
            "An Impart alert decrypted but its contents were not understood. No alarm was raised."
        )
        AlertVerdict.Outdated -> AlertNotice.Status(
            ImpartNotifications.ID_UNREADABLE, "ALERT FROM AN OUTDATED VERSION",
            "An Impart alert arrived in an old wire format. Update Impart on both phones and pair again."
        )
        AlertVerdict.UnknownSender -> AlertNotice.Status(
            ImpartNotifications.ID_UNKNOWN_SENDER, "ALERT FROM UNRECOGNIZED NODE",
            "A node that is not in your Contact Matrix sent an alert. Pair it by QR handshake before it can raise the alarm."
        )
        is AlertVerdict.BadSignature -> security(
            "ALERT FAILED VERIFICATION",
            "An alert claiming to be from ${verdict.claimedSender.displayName} failed its signature check. Someone may be impersonating them. No alarm was raised."
        )
        is AlertVerdict.TagMismatch -> security(
            "ALERT WITH A MISMATCHED TAG",
            "An alert signed by ${verdict.sender.displayName} arrived under another pair's tag. It may have been relayed or tampered with. No alarm was raised."
        )
        is AlertVerdict.NotForThisNode -> security(
            "MISDIRECTED ALERT",
            "${verdict.sender.displayName} sent an alert addressed to a different node. It was not raised."
        )
        is AlertVerdict.FromFuture -> security(
            "ALERT WITH AN IMPOSSIBLE TIME",
            "An alert from ${verdict.sender.displayName} is dated ${formatTime(verdict.sentAt)}, which is in the future. Check this phone's clock. No alarm was raised."
        )
        is AlertVerdict.Stale -> AlertNotice.Status(
            ImpartNotifications.ID_STALE, "OLD ALERT NOT RAISED",
            "${kind(verdict.payload)} from ${verdict.sender.displayName} sent on ${formatTime(verdict.payload.sentAt)} arrived more than a day late and was not raised."
        )
        is AlertVerdict.Replayed -> security(
            "REPEATED ALERT IGNORED",
            "An alert from ${verdict.sender.displayName} that was already handled arrived again. It may have been copied and re-sent. No alarm was raised."
        )
        is AlertVerdict.Accepted -> when (val payload = verdict.payload) {
            is PanicPayload.Panic -> AlertNotice.Alarm(verdict.sender, payload, verdict.delayMinutes)
            is PanicPayload.Ack -> AlertNotice.Acknowledged(
                "PANIC ACKNOWLEDGED", "${verdict.sender.displayName} acknowledged your panic signal."
            )
        }
    }

    // Anything thrown inside the pipeline still ends in something the user sees.
    fun pipelineFailure(error: Throwable): AlertNotice.Status = AlertNotice.Status(
        ImpartNotifications.ID_UNREADABLE, "ALERT PIPELINE FAILURE",
        "An Impart alert arrived but could not be processed: ${error.message ?: error.javaClass.simpleName}"
    )

    private fun security(title: String, text: String) = AlertNotice.Status(ImpartNotifications.ID_SECURITY, title, text)

    private fun kind(payload: PanicPayload): String = if (payload is PanicPayload.Ack) "An acknowledgement" else "A panic"
}
