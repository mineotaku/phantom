package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Phantom custom clean sans-serif typography (Material You Style)
val TerminalFontFamily = FontFamily.Default

val Typography = Typography(
  displayLarge = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 32.sp,
    lineHeight = 40.sp,
    letterSpacing = (-0.5).sp
  ),
  titleLarge = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 20.sp,
    lineHeight = 28.sp,
    letterSpacing = (-0.25).sp
  ),
  bodyLarge = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 15.sp,
    lineHeight = 22.sp,
    letterSpacing = 0.15.sp
  ),
  bodyMedium = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.15.sp
  ),
  labelLarge = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.5.sp
  ),
  labelSmall = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 10.sp,
    lineHeight = 14.sp,
    letterSpacing = 0.25.sp
  )
)
