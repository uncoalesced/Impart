// Engineered by uncoalesced
package com.uncoalesced.impart.data.sms

import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import com.uncoalesced.impart.domain.model.SmsConversation
import com.uncoalesced.impart.domain.model.SmsMessageItem
import com.uncoalesced.impart.domain.repository.SmsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

// The phone's SMS provider (content://sms). Reads and writes ordinary texts only; the alert
// pipeline never calls this class. Used only while Impart is the default SMS app, which is
// also when the provider lets it write.
@Singleton
class TelephonyProviderSmsStore @Inject constructor(
    @ApplicationContext private val context: Context
) : SmsStore {
    private val resolver get() = context.contentResolver

    override suspend fun storeIncoming(address: String, body: String, receivedAt: Long, subscriptionId: Int) {
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, receivedAt)
                put(Telephony.Sms.READ, 0)
                put(Telephony.Sms.SEEN, 0)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                if (subscriptionId >= 0) put(Telephony.Sms.SUBSCRIPTION_ID, subscriptionId)
            }
            resolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values) ?: throw IllegalStateException("The SMS provider refused the insert.")
        }
    }

    override suspend fun storeOutgoing(address: String, body: String, sentAt: Long, subscriptionId: Int) {
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, sentAt)
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                if (subscriptionId >= 0) put(Telephony.Sms.SUBSCRIPTION_ID, subscriptionId)
            }
            resolver.insert(Telephony.Sms.Sent.CONTENT_URI, values) ?: throw IllegalStateException("The SMS provider refused the insert.")
        }
    }

    override fun conversations(): Flow<List<SmsConversation>> = observe { readConversations() }

    override fun thread(address: String): Flow<List<SmsMessageItem>> = observe { readThread(address) }

    override suspend fun markRead(address: String) {
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
            }
            resolver.update(Telephony.Sms.CONTENT_URI, values, "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.READ} = 0", arrayOf(address))
        }
    }

    private fun <T> observe(read: () -> T): Flow<T> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(read())
            }
        }
        resolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
        trySend(read())
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.flowOn(Dispatchers.IO)

    private fun readConversations(): List<SmsConversation> {
        val byAddress = LinkedHashMap<String, MutableList<SmsMessageItem>>()
        readAll().forEach { byAddress.getOrPut(it.address) { mutableListOf() }.add(it) }
        val unreadAddresses = unreadAddresses()
        return byAddress.map { (address, items) ->
            val last = items.maxBy { it.at }
            SmsConversation(address, last.body, last.at, items.size, address in unreadAddresses)
        }.sortedByDescending { it.lastAt }
    }

    private fun readThread(address: String): List<SmsMessageItem> =
        readAll(selection = "${Telephony.Sms.ADDRESS} = ?", args = arrayOf(address)).sortedBy { it.at }

    private fun readAll(selection: String? = null, args: Array<String>? = null): List<SmsMessageItem> {
        val projection = arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE)
        val out = mutableListOf<SmsMessageItem>()
        resolver.query(Telephony.Sms.CONTENT_URI, projection, selection, args, "${Telephony.Sms.DATE} DESC")?.use { cursor ->
            while (cursor.moveToNext()) {
                out += SmsMessageItem(
                    id = cursor.getLong(0),
                    address = cursor.getString(1).orEmpty(),
                    body = cursor.getString(2).orEmpty(),
                    at = cursor.getLong(3),
                    outgoing = cursor.getInt(4) != Telephony.Sms.MESSAGE_TYPE_INBOX
                )
            }
        }
        return out
    }

    private fun unreadAddresses(): Set<String> {
        val out = mutableSetOf<String>()
        resolver.query(Telephony.Sms.Inbox.CONTENT_URI, arrayOf(Telephony.Sms.ADDRESS), "${Telephony.Sms.READ} = 0", null, null)?.use { cursor ->
            while (cursor.moveToNext()) out += cursor.getString(0).orEmpty()
        }
        return out
    }
}
