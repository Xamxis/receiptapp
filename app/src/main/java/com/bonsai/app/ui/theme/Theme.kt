package com.bonsai.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val BonsaiLight = lightColorScheme(
    primary = ForestMid,
    onPrimary = SandCard,
    primaryContainer = LeafPale,
    onPrimaryContainer = ForestDeep,
    secondary = ForestDeep,
    onSecondary = SandCard,
    tertiary = Terracotta,
    onTertiary = SandCard,
    tertiaryContainer = TerracottaPale,
    onTertiaryContainer = Terracotta,
    background = SandLight,
    onBackground = InkDark,
    surface = SandCard,
    onSurface = InkDark,
    surfaceVariant = LeafPale,
    onSurfaceVariant = InkMuted,
    outlineVariant = Color_OutlineLight
)

private val BonsaiDark = darkColorScheme(
    primary = LeafOnDark,
    onPrimary = ForestNight,
    primaryContainer = ForestMid,
    onPrimaryContainer = LeafPale,
    secondary = LeafLight,
    onSecondary = ForestNight,
    tertiary = Terracotta,
    onTertiary = SandCard,
    background = ForestNight,
    onBackground = SandOnDark,
    surface = ForestNightCard,
    onSurface = SandOnDark,
    surfaceVariant = ForestNightCard,
    onSurfaceVariant = LeafPale,
    outlineVariant = Color_OutlineDark
)

/**
 * App-Theme.
 *
 * [dynamicColor] = Material You: übernimmt ab Android 12 die Farben aus dem
 * Hintergrundbild des Nutzers. Standardmäßig AUS, damit die Bonsai-Markenfarben
 * greifen – im Einstellungs-Screen kann der Nutzer es einschalten.
 */
@Composable
fun BonsaiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> BonsaiDark
        else -> BonsaiLight
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = BonsaiTypography,
        content = content
    )
}
