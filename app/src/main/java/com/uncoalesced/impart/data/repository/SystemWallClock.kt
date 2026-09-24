// Engineered by uncoalesced
package com.uncoalesced.impart.data.repository

import com.uncoalesced.impart.domain.repository.WallClock
import javax.inject.Inject

class SystemWallClock @Inject constructor() : WallClock {
    override fun now(): Long = System.currentTimeMillis()
}
