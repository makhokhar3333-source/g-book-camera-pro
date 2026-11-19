package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val GBookDarkColorScheme = darkColorScheme(
    primary = SapphirePrimary,
    onPrimary = PureWhite,
    secondary = SapphireBright,
    onSecondary = OceanDeepTop,
    tertiary = GoldReflection,
    background = StudioDarkBg,
    onBackground = TextPrimary,
    surface = StudioSurface,
    onSurface = TextPrimary,
    surfaceVariant = StudioSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = StudioBorder,
    error = OfflineRed,
    onError = PureWhite
)

@Composable
fun GBookCameraTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = GBookDarkColorScheme,
        typography = GBookTypography,
        content = content
    )
}
