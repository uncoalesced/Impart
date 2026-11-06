// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

// Tapjacking defence. A touch that reaches a sensitive control while another window is
// drawn over it, fully or partly, is refused. The flag values are MotionEvent's, mirrored
// here so the rule is pure Kotlin and unit-tested.
object ObscuredTouchPolicy {
    const val FLAG_WINDOW_IS_OBSCURED = 0x1
    const val FLAG_WINDOW_IS_PARTIALLY_OBSCURED = 0x2

    fun shouldReject(motionEventFlags: Int): Boolean =
        motionEventFlags and (FLAG_WINDOW_IS_OBSCURED or FLAG_WINDOW_IS_PARTIALLY_OBSCURED) != 0
}
