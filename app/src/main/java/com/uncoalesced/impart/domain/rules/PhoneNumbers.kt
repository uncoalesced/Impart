// Engineered by uncoalesced
package com.uncoalesced.impart.domain.rules

// Every number Impart pages, including a node's own advertised number, must be a full
// international number. That rules out short codes (premium-rate SMS) and local formats
// that would route differently on a roaming phone.
object PhoneNumbers {
    private val E164 = Regex("^\\+[1-9][0-9]{7,14}$")
    private val SEPARATORS = setOf(' ', '-', '.', '(', ')', ' ')

    fun normalize(raw: String): String {
        val compact = raw.trim().filterNot { it in SEPARATORS }
        return if (compact.startsWith("00")) "+" + compact.drop(2) else compact
    }

    fun isValidE164(number: String): Boolean = E164.matches(number)
}
