// Engineered by uncoalesced
package com.uncoalesced.impart

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(cl, ImpartTestApplication_Application::class.java.name, context)
}
