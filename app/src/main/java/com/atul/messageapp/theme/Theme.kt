package com.atul.messageapp.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme =
    lightColorScheme()

private val DarkColorScheme =
    darkColorScheme()

@Composable
fun MessageAppTheme(
    themeMode: ThemeMode,
    dynamicColor: Boolean = true,
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