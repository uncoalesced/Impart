// Engineered by uncoalesced
package com.uncoalesced.impart

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.uncoalesced.impart.core.security.AppLockManager
import com.uncoalesced.impart.core.security.DuressManager
import com.uncoalesced.impart.data.local.prefs.SecurePrefsManager
import com.uncoalesced.impart.domain.model.LegalDocument
import com.uncoalesced.impart.domain.repository.ContactRepository
import com.uncoalesced.impart.domain.repository.RelayManager
import dagger.hilt.android.testing.HiltAndroidRule
import org.junit.After
import org.junit.Before
import org.junit.Rule
import java.io.File
import java.security.KeyStore
import javax.inject.Inject

// Shared set-up for the instrumented suite. Every test starts from an empty data directory,
// gets a fresh Hilt component, and launches activities itself, after its state is in place.
abstract class ImpartUiTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    @Inject lateinit var prefs: SecurePrefsManager
    @Inject lateinit var duress: DuressManager
    @Inject lateinit var lock: AppLockManager
    @Inject lateinit var contacts: ContactRepository
    @Inject lateinit var relayManager: RelayManager

    protected val relay: FakeRelayManager get() = relayManager as FakeRelayManager
    protected val context: Context get() = ApplicationProvider.getApplicationContext()
    private val scenarios = mutableListOf<ActivityScenario<*>>()

    @Before
    fun resetAndInject() {
        val data = context.dataDir
        // Android caches every SharedPreferences in memory for the life of the process, and
        // the whole suite runs in one process: clear each cached copy before deleting its
        // file, or the next test reads the previous test's keysets and PIN records.
        File(data, "shared_prefs").listFiles()?.filter { it.name.endsWith(".xml") }?.forEach {
            context.getSharedPreferences(it.name.removeSuffix(".xml"), Context.MODE_PRIVATE).edit().clear().commit()
        }
        listOf("shared_prefs", "databases", "files", "no_backup", "cache").forEach { File(data, it).deleteRecursively() }
        // Every test also starts without Keystore keys, as a fresh install does.
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        keyStore.aliases().toList().forEach { keyStore.deleteEntry(it) }
        grantRuntimePermissions()
        hilt.inject()
    }

    @After
    fun closeScenarios() {
        scenarios.forEach { runCatching { it.close() } }
    }

    protected fun acceptLegal() = prefs.acceptLegal(LegalDocument.CURRENT_VERSION)

    protected fun choosePins(standard: String = STANDARD_PIN, duressPin: String = DURESS_PIN) {
        duress.updatePins(standard.toCharArray(), duressPin.toCharArray()).getOrThrow()
    }

    protected fun unlockedApp() {
        acceptLegal()
        choosePins()
        lock.unlockAfterSetup()
    }

    protected fun launchMain(): ActivityScenario<MainActivity> =
        ActivityScenario.launch(MainActivity::class.java).also { scenarios += it }

    protected fun <T : android.app.Activity> track(scenario: ActivityScenario<T>): ActivityScenario<T> = scenario.also { scenarios += it }

    // Types on the in-app PIN pad, as a person would.
    protected fun typePin(pin: String) {
        pin.forEach { compose.onNodeWithContentDescription("digit $it").performClick() }
    }

    protected fun submit(label: String) {
        compose.onNodeWithText(label).performClick()
    }

    private fun grantRuntimePermissions() {
        val wanted = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) wanted += Manifest.permission.POST_NOTIFICATIONS
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        wanted.forEach { automation.executeShellCommand("pm grant ${context.packageName} $it").close() }
    }

    companion object {
        const val STANDARD_PIN = "2580"
        const val DURESS_PIN = "7531"
    }
}
