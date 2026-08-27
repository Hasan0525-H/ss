package com.vibe.app.presentation.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.vibe.app.data.model.DynamicTheme
import com.vibe.app.data.model.ThemeMode

private val CleanLightColorScheme = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE8F0FF),
    onPrimaryContainer = Color(0xFF12366E),
    secondary = Color(0xFF475569),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF1F5F9),
    onSecondaryContainer = Color(0xFF1E293B),
    tertiary = Color(0xFF4F46E5),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEDE9FE),
    onTertiaryContainer = Color(0xFF312E81),
    error = Color(0xFFB42318),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE4E2),
    onErrorContainer = Color(0xFF7A271A),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFF3F6FA),
    onSurfaceVariant = Color(0xFF667085),
    outline = Color(0xFFD7DEE9),
    outlineVariant = Color(0xFFE8ECF2),
    inverseSurface = Color(0xFF1F2937),
    inverseOnSurface = Color(0xFFF9FAFB),
    inversePrimary = Color(0xFFB9CCFF),
    surfaceDim = Color(0xFFE9EDF3),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFCFDFE),
    surfaceContainer = Color(0xFFF6F8FB),
    surfaceContainerHigh = Color(0xFFF0F3F7),
    surfaceContainerHighest = Color(0xFFE9EDF3),
)

private val CleanDarkColorScheme = darkColorScheme(
    primary = Color(0xFF8FB0FF),
    onPrimary = Color(0xFF0B2C6B),
    primaryContainer = Color(0xFF183D86),
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Color(0xFFBCC7D8),
    onSecondary = Color(0xFF263141),
    secondaryContainer = Color(0xFF354152),
    onSecondaryContainer = Color(0xFFDCE5F4),
    tertiary = Color(0xFFB8B5FF),
    onTertiary = Color(0xFF2F2A72),
    tertiaryContainer = Color(0xFF44408A),
    onTertiaryContainer = Color(0xFFE5E3FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFE5E7EB),
    surface = Color(0xFF111827),
    onSurface = Color(0xFFE5E7EB),
    surfaceVariant = Color(0xFF1C2534),
    onSurfaceVariant = Color(0xFFAEB8C8),
    outline = Color(0xFF586579),
    outlineVariant = Color(0xFF2C3748),
    inverseSurface = Color(0xFFE5E7EB),
    inverseOnSurface = Color(0xFF1F2937),
    inversePrimary = Color(0xFF2563EB),
    surfaceDim = Color(0xFF0B1220),
    surfaceBright = Color(0xFF263244),
    surfaceContainerLowest = Color(0xFF080E18),
    surfaceContainerLow = Color(0xFF101827),
    surfaceContainer = Color(0xFF141E2D),
    surfaceContainerHigh = Color(0xFF1A2535),
    surfaceContainerHighest = Color(0xFF223044),
)

private val CleanShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Clean, restrained app theme used across the whole application.
 *
 * The palette intentionally keeps surfaces neutral and uses blue only for
 * emphasis and active controls. This makes settings, setup, chat and project
 * screens share one visual language without forcing each screen to hard-code
 * colors or corner radii.
 */
@Composable
fun CleanVibeTheme(
    dynamicTheme: DynamicTheme = DynamicTheme.OFF,
    themeMode: ThemeMode = ThemeMode.LIGHT,
    content: @Composable () -> Unit,
) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val context = LocalContext.current
    val colorScheme = if (dynamicTheme == DynamicTheme.ON) {
        if (useDarkTheme) {
            dynamicDarkColorScheme(context)
        } else {
            dynamicLightColorScheme(context)
        }
    } else if (useDarkTheme) {
        CleanDarkColorScheme
    } else {
        CleanLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                !useDarkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars =
                !useDarkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = CleanShapes,
        content = content,
    )
}
