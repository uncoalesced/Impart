// Engineered by uncoalesced
package com.uncoalesced.impart.core.services

import com.uncoalesced.impart.core.security.AlertVerdict
import com.uncoalesced.impart.domain.model.Contact
import com.uncoalesced.impart.domain.model.PanicPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Every branch of the intrusion pipeline ends in something visible (spec 02, brief rule 3),
// and only an accepted panic raises the alarm.
class AlertNoticesTest {

    private val bob = Contact(uuid = "b0b0b0b0-0000-0000-0000-000000000000", publicKeyBase64 = "pk", signingKeyBase64 = "sk", nickname = "Bob")
    private val panic = PanicPayload.Panic(from = bob.uuid, to = "me", id = "p1", sentAt = 1_000L, lat = null, lng = null)
    private val ack = PanicPayload.Ack(from = bob.uuid, to = "me", id = "a1", sentAt = 2_000L, ackFor = "p0")
    private val format: (Long) -> String = { "T$it" }

    // One instance of every AlertVerdict. The when below has no else, so adding a verdict
    // without listing it here fails to compile.
    private val everyVerdict: List<AlertVerdict> = listOf(
        AlertVerdict.Unreadable,
        AlertVerdict.Malformed,
        AlertVerdict.Outdated,
        AlertVerdict.UnknownSender,
        AlertVerdict.BadSignature(bob),
        AlertVerdict.NotForThisNode(bob),
        AlertVerdict.TagMismatch(bob),
        AlertVerdict.FromFuture(bob, 9_000L),
        AlertVerdict.Stale(bob, panic),
        AlertVerdict.Replayed(bob),
        AlertVerdict.Accepted(bob, panic, 3),
        AlertVerdict.Accepted(bob, ack, 0)
    ).onEach { verdict ->
        @Suppress("UNUSED_VARIABLE")
        val covered: Unit = when (verdict) {
            AlertVerdict.Unreadable, AlertVerdict.Malformed, AlertVerdict.Outdated, AlertVerdict.UnknownSender,
            is AlertVerdict.BadSignature, is AlertVerdict.NotForThisNode, is AlertVerdict.TagMismatch,
            is AlertVerdict.FromFuture, is AlertVerdict.Stale, is AlertVerdict.Replayed, is AlertVerdict.Accepted -> Unit
        }
    }

    @Test
    fun `every verdict produces a notice with a title and text`() {
        everyVerdict.forEach { verdict ->
            when (val notice = AlertNotices.forVerdict(verdict, format)) {
                is AlertNotice.Status -> {
                    assertTrue("$verdict has no title", notice.title.isNotBlank())
                    assertTrue("$verdict has no text", notice.text.isNotBlank())
                }
                is AlertNotice.Acknowledged -> assertTrue(notice.text.isNotBlank())
                is AlertNotice.Alarm -> Unit
            }
        }
    }

    @Test
    fun `only an accepted panic raises the alarm`() {
        val alarms = everyVerdict.filter { AlertNotices.forVerdict(it, format) is AlertNotice.Alarm }
        assertEquals(listOf<AlertVerdict>(AlertVerdict.Accepted(bob, panic, 3)), alarms)
        val alarm = AlertNotices.forVerdict(AlertVerdict.Accepted(bob, panic, 3), format) as AlertNotice.Alarm
        assertEquals(bob, alarm.sender)
        assertEquals(panic, alarm.payload)
        assertEquals(3L, alarm.delayMinutes)
    }

    @Test
    fun `an accepted ack is an acknowledgement naming the sender`() {
        val notice = AlertNotices.forVerdict(AlertVerdict.Accepted(bob, ack, 0), format)
        assertTrue(notice is AlertNotice.Acknowledged)
        assertTrue((notice as AlertNotice.Acknowledged).text.contains("Bob"))
    }

    @Test
    fun `attacks are reported on the security id and say no alarm was raised`() {
        listOf(
            AlertVerdict.BadSignature(bob),
            AlertVerdict.TagMismatch(bob),
            AlertVerdict.FromFuture(bob, 9_000L),
            AlertVerdict.Replayed(bob)
        ).forEach { verdict ->
            val notice = AlertNotices.forVerdict(verdict, format) as AlertNotice.Status
            assertEquals("$verdict", ImpartNotifications.ID_SECURITY, notice.id)
            assertTrue("$verdict", notice.text.contains("No alarm was raised"))
        }
        val misdirected = AlertNotices.forVerdict(AlertVerdict.NotForThisNode(bob), format) as AlertNotice.Status
        assertEquals(ImpartNotifications.ID_SECURITY, misdirected.id)
    }

    @Test
    fun `undecryptable, malformed and outdated alerts share the unreadable id`() {
        listOf(AlertVerdict.Unreadable, AlertVerdict.Malformed, AlertVerdict.Outdated).forEach {
            assertEquals(ImpartNotifications.ID_UNREADABLE, (AlertNotices.forVerdict(it, format) as AlertNotice.Status).id)
        }
        assertEquals(
            ImpartNotifications.ID_UNKNOWN_SENDER,
            (AlertNotices.forVerdict(AlertVerdict.UnknownSender, format) as AlertNotice.Status).id
        )
    }

    @Test
    fun `stale and future alerts show the claimed time`() {
        val stale = AlertNotices.forVerdict(AlertVerdict.Stale(bob, panic), format) as AlertNotice.Status
        assertEquals(ImpartNotifications.ID_STALE, stale.id)
        assertTrue(stale.text.contains("T1000"))
        assertTrue(stale.text.startsWith("A panic"))
        val staleAck = AlertNotices.forVerdict(AlertVerdict.Stale(bob, ack), format) as AlertNotice.Status
        assertTrue(staleAck.text.startsWith("An acknowledgement"))
        val future = AlertNotices.forVerdict(AlertVerdict.FromFuture(bob, 9_000L), format) as AlertNotice.Status
        assertTrue(future.text.contains("T9000"))
    }

    @Test
    fun `a pipeline failure is visible and names the error`() {
        val notice = AlertNotices.pipelineFailure(IllegalStateException("boom"))
        assertEquals(ImpartNotifications.ID_UNREADABLE, notice.id)
        assertTrue(notice.text.endsWith("boom"))
        val unnamed = AlertNotices.pipelineFailure(IllegalStateException())
        assertTrue(unnamed.text.endsWith("IllegalStateException"))
        assertFalse(unnamed.title.isBlank())
    }
}
