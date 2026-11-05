// Engineered by uncoalesced
package com.uncoalesced.impart.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.uncoalesced.impart.R

// Both families are bundled in res/font under the SIL Open Font License (design/fonts/).
// Nothing is downloaded: downloadable fonts need Play Services and the network.

// Headings, the wordmark, numbers, PINs, fingerprints, timestamps, phone numbers: anything a
// person compares character by character.
val ImpartMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
    // ExtraBold in older screens maps to the heaviest bundled weight.
    Font(R.font.jetbrains_mono_bold, FontWeight.ExtraBold)
)

// Body text, buttons, dialogs, legal documents.
val ImpartSans = FontFamily(
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_semibold, FontWeight.Bold),
    Font(R.font.ibm_plex_sans_semibold, FontWeight.ExtraBold)
)

private val base = Typography()

private fun TextStyle.mono() = copy(fontFamily = ImpartMono)
private fun TextStyle.sans() = copy(fontFamily = ImpartSans)

// Spec 09 section 4: display, headline and title use Mono; body and label use Plex Sans.
val Typography = Typography(
    displayLarge = base.displayLarge.mono(),
    displayMedium = base.displayMedium.mono(),
    displaySmall = base.displaySmall.mono().copy(fontWeight = FontWeight.Bold),
    headlineLarge = base.headlineLarge.mono(),
    headlineMedium = base.headlineMedium.mono(),
    headlineSmall = base.headlineSmall.mono(),
    titleLarge = base.titleLarge.mono().copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.mono().copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.mono(),
    bodyLarge = base.bodyLarge.sans(),
    bodyMedium = base.bodyMedium.sans(),
    bodySmall = base.bodySmall.sans(),
    labelLarge = base.labelLarge.sans().copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.sans(),
    labelSmall = base.labelSmall.sans().copy(fontSize = 11.sp)
)
