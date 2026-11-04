// Engineered by uncoalesced
package com.uncoalesced.impart.core.util

import android.app.Activity
import android.os.Build
import android.view.Window
import android.view.WindowManager

// Every window Impart owns goes through here: both activities and every Compose dialog
// window. WindowHardeningSourceTest fails the build if a window is created without it.
object WindowHardening {
    // No screenshots, no screen recording, no recents preview, and no content for an
    // accessibility screenshot API. Does not stop an accessibility service reading the tree.
    fun harden(window: Window) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    fun harden(activity: Activity) {
        harden(activity.window)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.setRecentsScreenshotEnabled(false)
        }
    }
}
