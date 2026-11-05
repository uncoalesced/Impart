// Engineered by uncoalesced
package com.uncoalesced.impart.presentation.theme

import androidx.compose.ui.graphics.Color

// The uncoalesced design language for Impart (specs/09_DESIGN_LANGUAGE.md). Screens are true
// black so AMOLED pixels stay off; Graphite is for surfaces. Watermelon means "this is Impart"
// or "this is an emergency", never "error". Warnings are Sandy Brown, healthy states Palm Leaf.
// The ARGB values live in ImpartPalette so ContrastTest can compute WCAG ratios on the JVM.
object ImpartPalette {
    const val GROUND = 0xFF000000
    const val SURFACE = 0xFF262626
    const val SURFACE_RAISED = 0xFF333333
    const val BORDER = 0xFF4B4B4B
    const val TEXT_PRIMARY = 0xFFFDFFFC
    const val TEXT_MUTED = 0xFF9A9B99
    const val ACCENT_SHADE = 0xFFAD2F4A
    const val ACCENT = 0xFFEF2D56
    const val ACCENT_TINT = 0xFFF47690
    const val WARNING = 0xFFF19143
    const val VERIFIED = 0xFF748E54
    const val VERIFIED_TINT = 0xFFA3B48F
}

val ImpartGround = Color(ImpartPalette.GROUND)               // Every screen background
val ImpartSurface = Color(ImpartPalette.SURFACE)             // Cards, list rows
val ImpartSurfaceRaised = Color(ImpartPalette.SURFACE_RAISED) // Dialogs, nav bar, sheets
val ImpartBorder = Color(ImpartPalette.BORDER)               // Dividers, outlines
val ImpartTextPrimary = Color(ImpartPalette.TEXT_PRIMARY)    // Porcelain: body and headings
val ImpartTextMuted = Color(ImpartPalette.TEXT_MUTED)        // Labels, unselected, timestamps
val ImpartAccentShade = Color(ImpartPalette.ACCENT_SHADE)    // Pressed states, broadcast panic
val ImpartAccent = Color(ImpartPalette.ACCENT)               // Watermelon: panic, intrusion, the mark
val ImpartAccentTint = Color(ImpartPalette.ACCENT_TINT)      // Accent text and icons on surfaces
val ImpartWarning = Color(ImpartPalette.WARNING)             // Sandy Brown: stale, expiring, needs attention
val ImpartVerified = Color(ImpartPalette.VERIFIED)           // Palm Leaf: dots and fills
val ImpartVerifiedTint = Color(ImpartPalette.VERIFIED_TINT)  // Palm Leaf text on surfaces
