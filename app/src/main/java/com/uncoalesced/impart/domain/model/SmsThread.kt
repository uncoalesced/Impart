// Engineered by uncoalesced
package com.uncoalesced.impart.domain.model

// Ordinary texts, shown only when Impart is the default SMS app (T14). Alerts are never here.
data class SmsConversation(
    val address: String,
    val lastBody: String,
    val lastAt: Long,
    val messageCount: Int,
    val unread: Boolean
)

data class SmsMessageItem(
    val id: Long,
    val address: String,
    val body: String,
    val at: Long,
    val outgoing: Boolean
)
