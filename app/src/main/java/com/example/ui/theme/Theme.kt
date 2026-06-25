package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF00D1FF),
    secondary = Color(0xFF00E5FF),
    background = Color(0xFF0B0E14),
    surface = Color(0xFF151922)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF00D1FF),
    secondary = Color(0xFF00E5FF),
    background = Color(0xFFF0F4F8),
    surface = Color(0xFFFFFFFF)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
