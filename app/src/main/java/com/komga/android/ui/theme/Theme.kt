package com.komga.android.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.komga.android.data.local.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = KomgaBlueLight,
    onPrimary = SurfaceDark,
    primaryContainer = KomgaBlueDark,
    onPrimaryContainer = KomgaBlueLight,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = SurfaceDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onBackground = OnSurfaceDark,
    onSurface = OnSurfaceDark,
)

private val LightColorScheme = lightColorScheme(
    primary = KomgaBlue,
    onPrimary = CardLight,
    primaryContainer = KomgaBlueLight,
    onPrimaryContainer = KomgaBlueDark,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = SurfaceLight,
    surface = CardLight,
    onBackground = OnSurfaceLight,
    onSurface = OnSurfaceLight,
)

/** Pure black backgrounds for AMOLED screens — saves battery on OLED panels. */
private val AmoledColorScheme = darkColorScheme(
    primary = KomgaBlueLight,
    onPrimary = Color.Black,
    primaryContainer = KomgaBlueDark,
    onPrimaryContainer = KomgaBlueLight,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = Color.Black,
    surface = Color(0xFF0A0A0A),
    surfaceVariant = Color(0xFF111111),
    onBackground = OnSurfaceDark,
    onSurface = OnSurfaceDark,
)

@Composable
fun KomgaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemInDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM  -> systemInDark
        ThemeMode.LIGHT   -> false
        ThemeMode.DARK    -> true
        ThemeMode.AMOLED  -> true
    }

    val colorScheme = when {
        themeMode == ThemeMode.AMOLED -> AmoledColorScheme
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else      -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
