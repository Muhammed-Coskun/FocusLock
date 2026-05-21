package com.personal.focuslock.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkScheme = darkColorScheme(
    primary = AccentBlue,
    onPrimary = Bg,
    primaryContainer = AccentBlueTint,
    onPrimaryContainer = TextPrimary,
    secondary = AccentMint,
    onSecondary = Bg,
    secondaryContainer = AccentMintTint,
    onSecondaryContainer = TextPrimary,
    tertiary = AccentAmber,
    onTertiary = Bg,
    tertiaryContainer = AccentAmberTint,
    onTertiaryContainer = TextPrimary,
    background = Bg,
    onBackground = TextPrimary,
    surface = Surface1,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondary,
    error = AccentCoral,
    onError = Bg,
    errorContainer = AccentCoralTint,
    onErrorContainer = TextPrimary,
    outline = Stroke,
    outlineVariant = StrokeSoft
)

@Composable
fun FocusLockTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        typography = focusTypography(),
        content = content
    )
}
