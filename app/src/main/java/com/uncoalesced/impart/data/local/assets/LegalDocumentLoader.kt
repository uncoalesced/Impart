// Engineered by uncoalesced
package com.uncoalesced.impart.data.local.assets

import android.content.Context
import android.util.Log
import com.uncoalesced.impart.core.util.LegalDocumentParser
import com.uncoalesced.impart.domain.model.LegalBlock
import com.uncoalesced.impart.domain.model.LegalDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LegalDocumentLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // A missing or empty asset is an error the screen shows, never a blank page.
    suspend fun load(document: LegalDocument): Result<List<LegalBlock>> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.assets.open(document.assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
            LegalDocumentParser.parse(text, document.format).also {
                require(it.isNotEmpty()) { "${document.title} is empty in this build." }
            }
        }.onFailure { Log.e(TAG, "Could not load ${document.assetPath}", it) }
    }

    private companion object {
        const val TAG = "LegalDocumentLoader"
    }
}
