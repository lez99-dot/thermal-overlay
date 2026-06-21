package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = SophisticatedDarkPrimary,
    onPrimary = SophisticatedDarkOnPrimary,
    secondary = SophisticatedDarkSecondary,
    onSecondary = SophisticatedDarkOnSurface,
    background = SophisticatedDarkBg,
    onBackground = SophisticatedDarkOnSurface,
    surface = SophisticatedDarkSurface,
    onSurface = SophisticatedDarkOnSurface,
    surfaceVariant = SophisticatedDarkSurface,
    onSurfaceVariant = SophisticatedDarkOnSurfaceVariant
  )

private val LightColorScheme = DarkColorScheme

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force dark theme
  dynamicColor: Boolean = false, // Disable dynamic colors to enforce branding
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
