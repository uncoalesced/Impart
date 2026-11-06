// Engineered by uncoalesced
package com.uncoalesced.impart.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Every window Impart draws must carry FLAG_SECURE, and every PIN must be typed on the
// in-app pad. A JVM test cannot open a window, so this one reads the sources: a bare
// Compose Dialog or AlertDialog anywhere but SecureDialog.kt, an Activity that does not call
// WindowHardening.harden(this), or a text field in the security package, fails the build.
class WindowHardeningSourceTest {

    private val root = File("src/main/java/com/uncoalesced/impart")
    private val sources = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private val bareDialog = Regex("""(^|[^A-Za-z0-9_])(AlertDialog|Dialog)\(""")

    @Test
    fun `sources are found`() {
        assertTrue("no Kotlin sources under ${root.absolutePath}", sources.size > 20)
    }

    @Test
    fun `every dialog is opened through SecureDialog or SecureAlertDialog`() {
        val offenders = sources
            .filterNot { it.path.endsWith("presentation/components/SecureDialog.kt") }
            .filter { file -> file.readLines().any { line -> bareDialog.containsMatchIn(line) && !line.trimStart().startsWith("//") } }
            .map { it.relativeTo(root).path }
        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun `every activity hardens its window`() {
        val activities = sources.filter { it.readText().contains(": ComponentActivity()") }
        assertEquals(
            listOf("MainActivity.kt", "presentation/intrusion/IntrusionActivity.kt", "presentation/messages/ComposeSmsActivity.kt").sorted(),
            activities.map { it.relativeTo(root).path }.sorted()
        )
        activities.forEach { file ->
            assertTrue("${file.name} must call WindowHardening.harden(this)", file.readText().contains("WindowHardening.harden(this)"))
        }
    }

    @Test
    fun `no raw FLAG_SECURE outside WindowHardening`() {
        val offenders = sources
            .filterNot { it.path.endsWith("core/util/WindowHardening.kt") }
            .filter { it.readText().contains("FLAG_SECURE") && it.readLines().any { l -> l.contains("FLAG_SECURE") && !l.trimStart().startsWith("//") } }
            .map { it.relativeTo(root).path }
        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun `no PIN entry uses a text field`() {
        val security = sources.filter { it.path.contains("presentation/security/") }
        assertTrue(security.isNotEmpty())
        val offenders = security.filter { it.readText().contains("TextField") }.map { it.name }
        assertEquals(emptyList<String>(), offenders)
        val padUsers = security.filter { it.readText().contains("PinPad(") }.map { it.name }.sorted()
        assertEquals(listOf("AppLockScreen.kt", "PinSetupDialog.kt", "SecurityAuthDialog.kt"), padUsers)
    }
}
