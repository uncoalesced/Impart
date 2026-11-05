// Engineered by uncoalesced
package com.uncoalesced.impart.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Material 3 roles mapped onto the spec 09 tokens. Error is Sandy Brown on purpose:
// Watermelon never means "error" in Impart.
private val DarkColorScheme = darkColorScheme(
    primary = ImpartAccent,
    onPrimary = ImpartGround,
    primaryContainer = ImpartAccentShade,
    onPrimaryContainer = ImpartTextPrimary,
    secondary = ImpartTextMuted,
    onSecondary = ImpartGround,
    tertiary = ImpartVerified,
    onTertiary = ImpartGround,
    background = ImpartGround,
    onBackground = ImpartTextPrimary,
    surface = ImpartSurface,
    onSurface = ImpartTextPrimary,
    surfaceVariant = ImpartSurfaceRaised,
    onSurfaceVariant = ImpartTextMuted,
    surfaceContainer = ImpartSurface,
    surfaceContainerHigh = ImpartSurfaceRaised,
    surfaceContainerHighest = ImpartSurfaceRaised,
    outline = ImpartBorder,
    outlineVariant = ImpartBorder,
    error = ImpartWarning,
    onError = ImpartGround
)

@Composable
fun ImpartTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
