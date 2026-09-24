// Engineered by uncoalesced
package com.uncoalesced.impart.core.util

import java.util.Locale

// HH:MM:SS for the dead man's switch countdown. Negative input renders as zero.
fun formatCountdown(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0L)
    return String.format(
        Locale.US,
        "%02d:%02d:%02d",
        totalSeconds / 3600,
        (totalSeconds % 3600) / 60,
        totalSeconds % 60
    )
}
