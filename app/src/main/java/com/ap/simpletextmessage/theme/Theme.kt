package com.ap.simpletextmessage.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme =
    lightColorScheme(
        primary = MessageGreen,
        onPrimary = LightBackground,
        primaryContainer = Color(0xFFD7F2DD),
        onPrimaryContainer = Color(0xFF073D16),
        secondary = Color(0xFF42664B),
        secondaryContainer = Color(0xFFDCEFE0),
        onSecondaryContainer = Color(0xFF102D18),
        background = LightBackground,
        onBackground = LightText,
        surface = LightSurface,
        onSurface = LightText,
        surfaceVariant = LightSurfaceVariant,
        onSurfaceVariant = LightMutedText,
        surfaceContainer = LightSurface,
        surfaceContainerHigh = LightSurfaceVariant,
        surfaceContainerHighest = Color(0xFFDCECE0),
        outline = Color(0xFF68786C),
        outlineVariant = LightDivider
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = MessageGreenDark,
        onPrimary = Color(0xFF003912),
        primaryContainer = Color(0xFF075B20),
        onPrimaryContainer = Color(0xFFB7F5C4),
        secondary = Color(0xFFA6D4B0),
        secondaryContainer = Color(0xFF294832),
        onSecondaryContainer = Color(0xFFD5F2DB),
        background = DarkBackground,
        onBackground = DarkText,
        surface = DarkSurface,
        onSurface = DarkText,
        surfaceVariant = DarkSurfaceVariant,
        onSurfaceVariant = DarkMutedText,
        surfaceContainer = DarkSurface,
        surfaceContainerHigh = Color(0xFF1B2A20),
        surfaceContainerHighest = DarkSurfaceVariant,
        outline = Color(0xFF87998B),
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
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = if (useDarkTheme) {
                            listOf(Color(0xFF102619), DarkBackground, Color(0xFF09110C))
                        } else {
                            listOf(Color(0xFFE4F7E8), LightBackground, Color(0xFFF9FCF9))
                        }
                    )
                )
        ) {
            content()
        }
    }
}
