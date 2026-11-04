// Engineered by uncoalesced
package com.uncoalesced.impart.core.util

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle

// The only clipboard write in Impart. The clip is flagged sensitive, so Android 13+ hides
// it from the clipboard preview, and it is cleared after a minute unless something else
// has replaced it. Keys, PINs and fingerprints are never copied at all.
object SensitiveClipboard {
    private const val CLEAR_AFTER_MS = 60_000L
    private const val LABEL = "Impart"

    fun copy(context: Context, text: String) {
        val manager = context.getSystemService(ClipboardManager::class.java)
        val clip = ClipData.newPlainText(LABEL, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        manager.setPrimaryClip(clip)
        Handler(Looper.getMainLooper()).postDelayed({ clearIfOurs(manager) }, CLEAR_AFTER_MS)
    }

    private fun clearIfOurs(manager: ClipboardManager) {
        val label = manager.primaryClipDescription?.label ?: return
        if (label != LABEL) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.clearPrimaryClip()
        } else {
            manager.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }
}
