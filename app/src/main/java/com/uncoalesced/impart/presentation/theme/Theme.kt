// Engineered by uncoalesced
package com.uncoalesced.impart.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = ImpartPanicRed,
    secondary = ImpartSecondary,
    background = ImpartGround,
    surface = ImpartBackground,
    surfaceVariant = ImpartBackground,
    onPrimary = ImpartTextPrimary,
    onSecondary = ImpartTextPrimary,
    onBackground = ImpartTextPrimary,
    onSurface = ImpartTextPrimary,
    onSurfaceVariant = ImpartSecondary,
    error = ImpartPanicDark,
    onError = ImpartTextPrimary
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
