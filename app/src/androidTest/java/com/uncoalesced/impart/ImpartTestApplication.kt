// Engineered by uncoalesced
package com.uncoalesced.impart

import android.app.Application
import androidx.work.Configuration
import com.uncoalesced.impart.core.services.ImpartNotifications
import dagger.hilt.android.testing.CustomTestApplication

// The production application wires WorkManager to Hilt's worker factory. The test
// application only needs WorkManager initialised (the dashboard asks for it) and the
// notification channels; no worker runs in these tests.
open class ImpartTestApplicationBase : Application(), Configuration.Provider {
    override fun onCreate() {
        super.onCreate()
        ImpartNotifications.ensureChannels(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()
}

@CustomTestApplication(ImpartTestApplicationBase::class)
interface ImpartTestApplication
