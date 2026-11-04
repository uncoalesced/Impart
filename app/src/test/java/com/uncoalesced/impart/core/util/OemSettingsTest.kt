// Engineered by uncoalesced
package com.uncoalesced.impart.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OemSettingsTest {

    @Test
    fun `vendor detection by manufacturer and brand, case-insensitive`() {
        assertEquals(OemVendor.XIAOMI, OemSettingsTable.vendorFor("Xiaomi", "Xiaomi"))
        assertEquals(OemVendor.XIAOMI, OemSettingsTable.vendorFor("Xiaomi", "Redmi"))
        assertEquals(OemVendor.XIAOMI, OemSettingsTable.vendorFor("XIAOMI", "POCO"))
        assertEquals(OemVendor.OPPO, OemSettingsTable.vendorFor("OPPO", "OPPO"))
        assertEquals(OemVendor.REALME, OemSettingsTable.vendorFor("realme", "realme"))
        assertEquals(OemVendor.VIVO, OemSettingsTable.vendorFor("vivo", "iQOO"))
        assertEquals(OemVendor.HUAWEI, OemSettingsTable.vendorFor("HUAWEI", "HUAWEI"))
        assertEquals(OemVendor.HONOR, OemSettingsTable.vendorFor("HONOR", "HONOR"))
        assertEquals(OemVendor.ONEPLUS, OemSettingsTable.vendorFor("OnePlus", "OnePlus"))
        assertEquals(OemVendor.SAMSUNG, OemSettingsTable.vendorFor("samsung", "samsung"))
    }

    @Test
    fun `stock and custom roms have no row`() {
        assertNull(OemSettingsTable.vendorFor("Google", "google"))
        assertNull(OemSettingsTable.vendorFor("Fairphone", "Fairphone"))
        assertNull(OemSettingsTable.vendorFor("LineageOS", "samsung-lineage"))
        assertNull(OemSettingsTable.vendorFor(null, null))
        assertNull(OemSettingsTable.vendorFor("", ""))
    }

    @Test
    fun `every vendor has candidates and an instruction`() {
        OemVendor.entries.forEach { vendor ->
            assertTrue(vendor.name, OemSettingsTable.candidates(vendor).isNotEmpty())
            assertTrue(vendor.name, OemSettingsTable.instruction(vendor).isNotBlank())
            OemSettingsTable.candidates(vendor).forEach {
                assertTrue(it.packageName.contains('.'))
                assertTrue(it.className.startsWith(it.packageName.substringBefore('.')))
            }
        }
    }

    @Test
    fun `the first candidate that resolves wins, in table order`() {
        val candidates = OemSettingsTable.candidates(OemVendor.XIAOMI)
        val chosen = OemSettingsPolicy.choose(
            candidates,
            resolves = { it == candidates[1] || it == candidates[2] },
            toTarget = { it.className },
            fallback = "details"
        )
        assertEquals(candidates[1].className, chosen)
    }

    @Test
    fun `the fallback is used only when nothing resolves`() {
        val candidates = OemSettingsTable.candidates(OemVendor.SAMSUNG)
        assertEquals("details", OemSettingsPolicy.choose(candidates, { false }, { it.className }, "details"))
        assertEquals(candidates.first().className, OemSettingsPolicy.choose(candidates, { true }, { it.className }, "details"))
    }
}
