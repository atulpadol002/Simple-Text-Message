package com.atul.messageapp.utils

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.pow

object AvatarColorResolver {
    private val palette = listOf(
        Color(0xFFE8B4B8), Color(0xFFD8C4E8), Color(0xFFB9DDF2), Color(0xFFAEDFD3),
        Color(0xFFB9DDB8), Color(0xFFF2D28A), Color(0xFFF2B78B), Color(0xFFC9B7EA)
    )
    fun background(seed: String, scheme: ColorScheme): Color {
        val index = seed.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.code ?: 0
        return palette[index.mod(palette.size)]
    }
    fun foreground(background: Color, scheme: ColorScheme): Color =
        if (background.luminance() > 0.55f) Color(0xFF27313A) else scheme.onPrimary
    private fun Color.luminance(): Float {
        fun c(v: Float) = if (v <= 0.03928f) v / 12.92f else ((v + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
        return 0.2126f*c(red) + 0.7152f*c(green) + 0.0722f*c(blue)
    }
}
