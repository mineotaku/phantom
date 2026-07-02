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
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
  primary = PhantomPrimary,
  onPrimary = PhantomSecondary,
  secondary = PhantomSecondary,
  onSecondary = Color.White,
  tertiary = PhantomTertiary,
  onTertiary = Color.White,
  background = PhantomBg,
  onBackground = PhantomTextPrimary,
  surface = PhantomSurface,
  onSurface = PhantomTextPrimary,
  surfaceVariant = PhantomSurfaceVariant,
  onSurfaceVariant = PhantomTextSecondary,
  error = PhantomError,
  onError = Color.White
)

private val DarkColorScheme = LightColorScheme // Fallback to same light palette since we are forcing the bright, professional, sage-cream Material You layout

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false, // Default to false to showcase the premium warm cream light theme
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
