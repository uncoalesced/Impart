// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObscuredTouchPolicyTest {

    @Test
    fun `mirrored flags match MotionEvent`() {
        assertEquals(MotionEvent.FLAG_WINDOW_IS_OBSCURED, ObscuredTouchPolicy.FLAG_WINDOW_IS_OBSCURED)
        assertEquals(MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED, ObscuredTouchPolicy.FLAG_WINDOW_IS_PARTIALLY_OBSCURED)
    }

    @Test
    fun `a touch under a full overlay is rejected`() {
        assertTrue(ObscuredTouchPolicy.shouldReject(ObscuredTouchPolicy.FLAG_WINDOW_IS_OBSCURED))
    }

    @Test
    fun `a touch under a partial overlay is rejected`() {
        assertTrue(ObscuredTouchPolicy.shouldReject(ObscuredTouchPolicy.FLAG_WINDOW_IS_PARTIALLY_OBSCURED))
        assertTrue(ObscuredTouchPolicy.shouldReject(0x3))
    }

    @Test
    fun `an unobscured touch with other flags set is accepted`() {
        assertFalse(ObscuredTouchPolicy.shouldReject(0))
        assertFalse(ObscuredTouchPolicy.shouldReject(0x4))
        assertFalse(ObscuredTouchPolicy.shouldReject(0x80))
    }
}
