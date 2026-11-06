// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

// Digits typed on the in-app PIN pad. Held in a CharArray so they can be zeroed; no String
// is ever built from them. The pad reads only the count, never the digits.
class PinEntry(private val capacity: Int = DuressManager.MAX_PIN_LENGTH) {
    private val digits = CharArray(capacity)

    var length: Int = 0
        private set

    val isFull: Boolean get() = length >= capacity

    fun push(digit: Char): Boolean {
        if (digit !in '0'..'9' || isFull) return false
        digits[length] = digit
        length++
        return true
    }

    fun pop() {
        if (length == 0) return
        length--
        digits[length] = '\u0000'
    }

    // The caller owns the copy and zeroes it when done.
    fun snapshot(): CharArray = digits.copyOf(length)

    fun clear() {
        digits.fill('\u0000')
        length = 0
    }

    fun contentEquals(other: PinEntry): Boolean {
        if (length != other.length) return false
        var same = true
        for (i in 0 until length) same = same and (digits[i] == other.digits[i])
        return same
    }
}
