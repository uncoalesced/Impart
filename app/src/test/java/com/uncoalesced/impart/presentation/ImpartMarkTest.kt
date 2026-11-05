// Engineered by uncoalesced
package com.uncoalesced.impart.presentation

import com.uncoalesced.impart.presentation.components.ImpartMarkCells
import com.uncoalesced.impart.presentation.components.MarkShape
import com.uncoalesced.impart.presentation.components.MarkTone
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// The mark in the app must be exactly the committed design (spec 09 section 5).
class ImpartMarkTest {

    private val json = JSONObject(File("../design/impart-mark.cells.json").readText())

    @Test
    fun `kotlin table matches the committed cell list`() {
        assertEquals(json.getInt("grid"), ImpartMarkCells.GRID)
        val cells = json.getJSONArray("cells")
        assertEquals(cells.length(), ImpartMarkCells.cells.size)
        for (i in 0 until cells.length()) {
            val c = cells.getJSONObject(i)
            val k = ImpartMarkCells.cells[i]
            assertEquals("cell $i row", c.getInt("row"), k.row)
            assertEquals("cell $i col", c.getInt("col"), k.col)
            assertEquals("cell $i shape", MarkShape.valueOf(c.getString("shape").uppercase()), k.shape)
            assertEquals("cell $i tone", MarkTone.valueOf(c.getString("tone").uppercase()), k.tone)
        }
    }

    @Test
    fun `the mark has 21 cells and no duplicates`() {
        assertEquals(21, ImpartMarkCells.cells.size)
        assertEquals(21, ImpartMarkCells.cells.map { it.row to it.col }.toSet().size)
    }

    @Test
    fun `the center cell is never drawn`() {
        val center = json.getJSONArray("center_empty")
        assertEquals(ImpartMarkCells.CENTER, center.getInt(0))
        assertEquals(ImpartMarkCells.CENTER, center.getInt(1))
        assertFalse(ImpartMarkCells.drawable().any { it.row == 4 && it.col == 4 })
        assertFalse(ImpartMarkCells.cells.any { it.row == 4 && it.col == 4 })
    }

    @Test
    fun `ramp colors in the json are the spec 09 accent tokens`() {
        val ramp = json.getJSONObject("ramp")
        assertEquals("#AD2F4A", ramp.getString("shade"))
        assertEquals("#EF2D56", ramp.getString("base"))
        assertEquals("#F47690", ramp.getString("tint"))
        assertEquals(0xFFAD2F4A, com.uncoalesced.impart.presentation.theme.ImpartPalette.ACCENT_SHADE)
        assertEquals(0xFFEF2D56, com.uncoalesced.impart.presentation.theme.ImpartPalette.ACCENT)
        assertEquals(0xFFF47690, com.uncoalesced.impart.presentation.theme.ImpartPalette.ACCENT_TINT)
    }

    @Test
    fun `geometry follows spec 09`() {
        assertEquals(0.62f, ImpartMarkCells.CIRCLE_DIAMETER, 0f)
        assertEquals(0.66f, ImpartMarkCells.SQUARE_SIDE, 0f)
        assertEquals(0.12f, ImpartMarkCells.SQUARE_CORNER, 0f)
    }

    @Test
    fun `the decoy never shows the mark, the wordmark or Watermelon`() {
        val decoy = File("src/main/java/com/uncoalesced/impart/presentation/decoy/NodeCorruptedScreen.kt").readText()
        listOf("ImpartMark", "ImpartLockup", "ImpartWordmark", "ImpartAccent").forEach {
            assertFalse("decoy uses $it", decoy.contains(it))
        }
    }

    @Test
    fun `the mark is on the lock, legal and about screens, and hidden under the cloak`() {
        val root = "src/main/java/com/uncoalesced/impart/presentation"
        val lock = File("$root/security/AppLockScreen.kt").readText()
        val legal = File("$root/legal/LegalAcceptanceScreen.kt").readText()
        val settings = File("$root/settings/SettingsScreen.kt").readText()
        listOf(lock, legal, settings).forEach { assertTrue(it.contains("ImpartLockup(")) }
        assertTrue(lock.contains("if (showMark)"))
        assertTrue(legal.contains("if (showMark)"))
        assertTrue(settings.contains("if (!cloaked)"))
    }
}
