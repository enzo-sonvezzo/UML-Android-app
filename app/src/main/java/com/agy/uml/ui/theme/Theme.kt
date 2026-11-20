package com.agy.uml.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = ExpressivePrimaryContainer,
    onPrimary = ExpressiveOnPrimaryContainer,
    secondary = ExpressiveSecondaryContainer,
    tertiary = ExpressiveTertiaryContainer,
    background = TerminalDarkBg,
    surface = Color(0xFF1E1B22)
)

private val LightColorScheme = lightColorScheme(
    primary = ExpressivePrimary,
    onPrimary = ExpressiveOnPrimary,
    primaryContainer = ExpressivePrimaryContainer,
    onPrimaryContainer = ExpressiveOnPrimaryContainer,
    secondary = ExpressiveSecondary,
    secondaryContainer = ExpressiveSecondaryContainer,
    tertiary = ExpressiveTertiary,
    tertiaryContainer = ExpressiveTertiaryContainer,
    background = ExpressiveBackground,
    surface = ExpressiveSurface
)

@Composable
fun UmlAndroidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
