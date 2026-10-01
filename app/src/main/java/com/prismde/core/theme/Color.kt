package com.prismde.core.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val PrismPrimaryLight = Color(0xFF6750A4)
val PrismOnPrimaryLight = Color(0xFFFFFFFF)
val PrismPrimaryContainerLight = Color(0xFFEADDFF)
val PrismOnPrimaryContainerLight = Color(0xFF21005D)

val PrismSecondaryLight = Color(0xFF625B71)
val PrismOnSecondaryLight = Color(0xFFFFFFFF)
val PrismSecondaryContainerLight = Color(0xFFE8DEF8)
val PrismOnSecondaryContainerLight = Color(0xFF1D192B)

val PrismTertiaryLight = Color(0xFF7D5260)
val PrismOnTertiaryLight = Color(0xFFFFFFFF)
val PrismTertiaryContainerLight = Color(0xFFFFD8E4)
val PrismOnTertiaryContainerLight = Color(0xFF31111D)

val PrismBackgroundLight = Color(0xFFFEF7FF)
val PrismOnBackgroundLight = Color(0xFF1D1B20)
val PrismSurfaceLight = Color(0xFFFEF7FF)
val PrismOnSurfaceLight = Color(0xFF1D1B20)
val PrismSurfaceVariantLight = Color(0xFFE7E0EC)
val PrismOnSurfaceVariantLight = Color(0xFF49454F)

// Dark Theme Colors
val PrismPrimaryDark = Color(0xFFD0BCFF)
val PrismOnPrimaryDark = Color(0xFF381E72)
val PrismPrimaryContainerDark = Color(0xFF4F378B)
val PrismOnPrimaryContainerDark = Color(0xFFEADDFF)

val PrismSecondaryDark = Color(0xFFCCC2DC)
val PrismOnSecondaryDark = Color(0xFF332D41)
val PrismSecondaryContainerDark = Color(0xFF4A4458)
val PrismOnSecondaryContainerDark = Color(0xFFE8DEF8)

val PrismTertiaryDark = Color(0xFFEFB8C8)
val PrismOnTertiaryDark = Color(0xFF492532)
val PrismTertiaryContainerDark = Color(0xFF633B48)
val PrismOnTertiaryContainerDark = Color(0xFFFFD8E4)

val PrismBackgroundDark = Color(0xFF141218)
val PrismOnBackgroundDark = Color(0xFFE6E0E9)
val PrismSurfaceDark = Color(0xFF141218)
val PrismOnSurfaceDark = Color(0xFFE6E0E9)
val PrismSurfaceVariantDark = Color(0xFF49454F)
val PrismOnSurfaceVariantDark = Color(0xFFCAC4D0)

// Expressive Diagnostics Colors
val DiagnosticError = Color(0xFFE53935)
val DiagnosticErrorContainer = Color(0xFFFFDAD6)
val DiagnosticOnErrorContainer = Color(0xFF410002)

val DiagnosticWarning = Color(0xFFFFA000)
val DiagnosticWarningContainer = Color(0xFFFFE082)
val DiagnosticOnWarningContainer = Color(0xFF261900)

val DiagnosticNote = Color(0xFF0288D1)
val DiagnosticNoteContainer = Color(0xFFB3E5FC)
val DiagnosticOnNoteContainer = Color(0xFF001F2A)

val DiagnosticSuccess = Color(0xFF2E7D32)
val DiagnosticSuccessContainer = Color(0xFFC8E6C9)
val DiagnosticOnSuccessContainer = Color(0xFF002204)

val PrismLightColorScheme = lightColorScheme(
    primary = PrismPrimaryLight,
    onPrimary = PrismOnPrimaryLight,
    primaryContainer = PrismPrimaryContainerLight,
    onPrimaryContainer = PrismOnPrimaryContainerLight,
    secondary = PrismSecondaryLight,
    onSecondary = PrismOnSecondaryLight,
    secondaryContainer = PrismSecondaryContainerLight,
    onSecondaryContainer = PrismOnSecondaryContainerLight,
    tertiary = PrismTertiaryLight,
    onTertiary = PrismOnTertiaryLight,
    tertiaryContainer = PrismTertiaryContainerLight,
    onTertiaryContainer = PrismOnTertiaryContainerLight,
    background = PrismBackgroundLight,
    onBackground = PrismOnBackgroundLight,
    surface = PrismSurfaceLight,
    onSurface = PrismOnSurfaceLight,
    surfaceVariant = PrismSurfaceVariantLight,
    onSurfaceVariant = PrismOnSurfaceVariantLight,
)

val PrismDarkColorScheme = darkColorScheme(
    primary = PrismPrimaryDark,
    onPrimary = PrismOnPrimaryDark,
    primaryContainer = PrismPrimaryContainerDark,
    onPrimaryContainer = PrismOnPrimaryContainerDark,
    secondary = PrismSecondaryDark,
    onSecondary = PrismOnSecondaryDark,
    secondaryContainer = PrismSecondaryContainerDark,
    onSecondaryContainer = PrismOnSecondaryContainerDark,
    tertiary = PrismTertiaryDark,
    onTertiary = PrismOnTertiaryDark,
    tertiaryContainer = PrismTertiaryContainerDark,
    onTertiaryContainer = PrismOnTertiaryContainerDark,
    background = PrismBackgroundDark,
    onBackground = PrismOnBackgroundDark,
    surface = PrismSurfaceDark,
    onSurface = PrismOnSurfaceDark,
    surfaceVariant = PrismSurfaceVariantDark,
    onSurfaceVariant = PrismOnSurfaceVariantDark,
)
