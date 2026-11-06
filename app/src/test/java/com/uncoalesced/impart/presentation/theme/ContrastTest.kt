// Engineered by uncoalesced
package com.uncoalesced.impart.presentation.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.pow

// Every foreground and background pairing the screens use, with the WCAG 2.x ratio it must
// reach. Body text needs 4.5:1; text drawn at 24sp bold or larger, and non-text marks such
// as icons, switch tracks and dots, need 3:1 (spec 09 section 3). A new pairing in the UI
// gets a row here and in the spec 09 table.
class ContrastTest {

    private enum class Use(val minimum: Double) { BODY(4.5), LARGE_OR_GRAPHIC(3.0) }

    private data class Pair(val name: String, val foreground: Long, val background: Long, val use: Use)

    private val pairs = listOf(
        Pair("Porcelain on black", ImpartPalette.TEXT_PRIMARY, ImpartPalette.GROUND, Use.BODY),
        Pair("Porcelain on surface", ImpartPalette.TEXT_PRIMARY, ImpartPalette.SURFACE, Use.BODY),
        Pair("Porcelain on raised surface", ImpartPalette.TEXT_PRIMARY, ImpartPalette.SURFACE_RAISED, Use.BODY),
        Pair("Muted on black", ImpartPalette.TEXT_MUTED, ImpartPalette.GROUND, Use.BODY),
        Pair("Muted on surface", ImpartPalette.TEXT_MUTED, ImpartPalette.SURFACE, Use.BODY),
        Pair("Muted on raised surface", ImpartPalette.TEXT_MUTED, ImpartPalette.SURFACE_RAISED, Use.BODY),
        Pair("Watermelon on black", ImpartPalette.ACCENT, ImpartPalette.GROUND, Use.BODY),
        Pair("Accent tint on surface", ImpartPalette.ACCENT_TINT, ImpartPalette.SURFACE, Use.BODY),
        Pair("Accent tint on raised surface (selected nav)", ImpartPalette.ACCENT_TINT, ImpartPalette.SURFACE_RAISED, Use.BODY),
        Pair("Accent tint on black", ImpartPalette.ACCENT_TINT, ImpartPalette.GROUND, Use.BODY),
        Pair("Black on Watermelon (intrusion, panic button, chips)", ImpartPalette.GROUND, ImpartPalette.ACCENT, Use.BODY),
        Pair("Porcelain on accent shade (broadcast panic)", ImpartPalette.TEXT_PRIMARY, ImpartPalette.ACCENT_SHADE, Use.BODY),
        Pair("Sandy Brown on black", ImpartPalette.WARNING, ImpartPalette.GROUND, Use.BODY),
        Pair("Sandy Brown on surface", ImpartPalette.WARNING, ImpartPalette.SURFACE, Use.BODY),
        Pair("Sandy Brown on raised surface", ImpartPalette.WARNING, ImpartPalette.SURFACE_RAISED, Use.BODY),
        Pair("Black on Sandy Brown (revoke)", ImpartPalette.GROUND, ImpartPalette.WARNING, Use.BODY),
        Pair("Palm Leaf tint on black", ImpartPalette.VERIFIED_TINT, ImpartPalette.GROUND, Use.BODY),
        Pair("Palm Leaf tint on surface", ImpartPalette.VERIFIED_TINT, ImpartPalette.SURFACE, Use.BODY),
        Pair("Palm Leaf tint on raised surface", ImpartPalette.VERIFIED_TINT, ImpartPalette.SURFACE_RAISED, Use.BODY),
        Pair("Black on Porcelain (QR code, maps button)", ImpartPalette.GROUND, ImpartPalette.TEXT_PRIMARY, Use.BODY),
        Pair("Watermelon on surface (marks only)", ImpartPalette.ACCENT, ImpartPalette.SURFACE, Use.LARGE_OR_GRAPHIC),
        Pair("Porcelain on Watermelon (24sp bold only)", ImpartPalette.TEXT_PRIMARY, ImpartPalette.ACCENT, Use.LARGE_OR_GRAPHIC),
        Pair("Palm Leaf dot on surface", ImpartPalette.VERIFIED, ImpartPalette.SURFACE, Use.LARGE_OR_GRAPHIC),
        Pair("Watermelon switch track on raised surface", ImpartPalette.ACCENT, ImpartPalette.SURFACE_RAISED, Use.LARGE_OR_GRAPHIC)
    )

    @Test
    fun `every pairing meets its WCAG minimum`() {
        val failures = pairs.mapNotNull { pair ->
            val ratio = contrast(pair.foreground, pair.background)
            if (ratio < pair.use.minimum) "${pair.name}: %.2f < ${pair.use.minimum}".format(ratio) else null
        }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun `ratios match the spec 09 table`() {
        assertEquals(20.89, contrast(ImpartPalette.TEXT_PRIMARY, ImpartPalette.GROUND), 0.01)
        assertEquals(5.18, contrast(ImpartPalette.GROUND, ImpartPalette.ACCENT), 0.01)
        assertEquals(4.03, contrast(ImpartPalette.TEXT_PRIMARY, ImpartPalette.ACCENT), 0.01)
        assertEquals(3.73, contrast(ImpartPalette.ACCENT, ImpartPalette.SURFACE), 0.01)
        assertEquals(4.53, contrast(ImpartPalette.TEXT_MUTED, ImpartPalette.SURFACE_RAISED), 0.01)
    }

    @Test
    fun `Porcelain on Watermelon really does fail body text`() {
        assertTrue(contrast(ImpartPalette.TEXT_PRIMARY, ImpartPalette.ACCENT) < Use.BODY.minimum)
    }

    @Test
    fun `colors xml mirrors the palette`() {
        val xml = File("src/main/res/values/colors.xml").readText()
        assertTrue(xml.contains("<color name=\"impart_ground\">#000000</color>"))
        assertEquals(0xFF000000, ImpartPalette.GROUND)
        listOf("2B2D42", "EF233C", "D90429", "FFB000").forEach { old ->
            assertTrue("old palette value $old in colors.xml", !xml.contains(old, ignoreCase = true))
        }
    }

    @Test
    fun `no old palette value is left in the app sources or resources`() {
        val old = listOf("2B2D42", "EF233C", "D90429", "FFB000")
        val offenders = File("src").walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }
            .filterNot { it.name == "ContrastTest.kt" }
            .filter { file -> old.any { file.readText().contains(it, ignoreCase = true) } }
            .map { it.path }
            .toList()
        assertEquals(emptyList<String>(), offenders)
    }

    private fun luminance(argb: Long): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF).toDouble() / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun contrast(foreground: Long, background: Long): Double {
        val a = luminance(foreground)
        val b = luminance(background)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }
}
