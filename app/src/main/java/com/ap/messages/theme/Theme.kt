package com.ap.messages.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme =
    lightColorScheme(
        primary = MessageBlue,
        onPrimary = LightBackground,
        primaryContainer = Color(0xFFDCE8FF),
        onPrimaryContainer = Color(0xFF002E69),
        secondary = Color(0xFF4F6387),
        secondaryContainer = Color(0xFFDCE6FF),
        onSecondaryContainer = Color(0xFF0A1B38),
        background = LightBackground,
        onBackground = LightText,
        surface = LightSurface,
        onSurface = LightText,
        surfaceVariant = LightSurfaceVariant,
        onSurfaceVariant = LightMutedText,
        surfaceContainer = LightSurface,
        surfaceContainerHigh = LightSurfaceVariant,
        surfaceContainerHighest = Color(0xFFEAECF0),
        outline = Color(0xFF747981),
        outlineVariant = LightDivider
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = MessageBlueDark,
        onPrimary = Color(0xFF002F6D),
        primaryContainer = Color(0xFF08458F),
        onPrimaryContainer = Color(0xFFD9E6FF),
        secondary = Color(0xFFB5C8ED),
        secondaryContainer = Color(0xFF354667),
        onSecondaryContainer = Color(0xFFDCE6FF),
        background = DarkBackground,
        onBackground = DarkText,
        surface = DarkSurface,
        onSurface = DarkText,
        surfaceVariant = DarkSurfaceVariant,
        onSurfaceVariant = DarkMutedText,
        surfaceContainer = DarkSurface,
        surfaceContainerHigh = Color(0xFF20242B),
        surfaceContainerHighest = DarkSurfaceVariant,
        outline = Color(0xFF8B919C),
        outlineVariant = DarkDivider
    )

@Composable
fun MessageAppTheme(
    themeMode: ThemeMode,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemDarkTheme =
        isSystemInDarkTheme()

    val useDarkTheme =
        when (themeMode) {
            ThemeMode.SYSTEM ->
                systemDarkTheme

            ThemeMode.LIGHT ->
                false

            ThemeMode.DARK ->
                true
        }

    val context = LocalContext.current

    val colorScheme =
        when {
            dynamicColor &&
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.S -> {

                if (useDarkTheme) {
                    dynamicDarkColorScheme(
                        context
                    )
                } else {
                    dynamicLightColorScheme(
                        context
                    )
                }
            }

            useDarkTheme ->
                DarkColorScheme

            else ->
                LightColorScheme
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
