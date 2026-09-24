// Engineered by uncoalesced
package com.uncoalesced.impart.core.util

// Log-safe forms of identifiers. Logcat is readable over adb, so logs carry enough to tell
// two contacts apart and nothing that identifies a real person.
object Redact {
    fun phone(number: String?): String {
        val digits = number?.filter { it.isDigit() }.orEmpty()
        return if (digits.length < 4) "***" else "***" + digits.takeLast(2)
    }

    fun uuid(uuid: String?): String = uuid?.take(8)?.takeIf { it.isNotEmpty() } ?: "unknown"
}
