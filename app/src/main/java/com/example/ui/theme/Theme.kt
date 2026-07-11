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

private val DarkColorScheme = darkColorScheme(
  primary = Color(0xFFA2F0B9),
  onPrimary = Color(0xFF00391A),
  secondary = Color(0xFFB4CCB9),
  onSecondary = Color(0xFF203527),
  tertiary = Color(0xFFA2CED9),
  onTertiary = Color(0xFF00363F),
  background = Color(0xFF141A16),
  onBackground = Color(0xFFE1E3DF),
  surface = Color(0xFF1C221D),
  onSurface = Color(0xFFE1E3DF),
  surfaceVariant = Color(0xFF2D352F),
  onSurfaceVariant = Color(0xFFC1C9BE),
  error = Color(0xFFFFB4AB),
  onError = Color(0xFF690005)
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
