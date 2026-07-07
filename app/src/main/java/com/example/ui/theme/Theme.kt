package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = PurplePrimary,
    onPrimary = PurpleOnPrimary,
    primaryContainer = PurpleContainer,
    onPrimaryContainer = PurpleOnContainer,
    
    secondary = SageSecondary,
    onSecondary = SageOnSecondary,
    secondaryContainer = SageContainer,
    onSecondaryContainer = SageOnContainer,
    
    tertiary = AmberTertiary,
    onTertiary = AmberOnTertiary,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = AmberOnTertiaryContainer,
    
    background = NaturalBackground,
    onBackground = NaturalOnBackground,
    surface = NaturalSurface,
    onSurface = NaturalOnSurface,
    surfaceVariant = NaturalSurfaceVariant,
    onSurfaceVariant = NaturalOnSurfaceVariant,
    outline = NaturalOutline,
    
    errorContainer = SoftPinkContainer,
    onErrorContainer = SoftPinkOnContainer
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPurplePrimary,
    onPrimary = DarkPurpleOnPrimary,
    primaryContainer = DarkPurpleContainer,
    onPrimaryContainer = DarkPurpleOnContainer,
    
    secondary = DarkSageSecondary,
    onSecondary = DarkSageOnSecondary,
    secondaryContainer = DarkSageContainer,
    onSecondaryContainer = DarkSageOnContainer,
    
    tertiary = DarkAmberTertiary,
    onTertiary = DarkAmberOnTertiary,
    tertiaryContainer = DarkAmberContainer,
    onTertiaryContainer = DarkAmberOnContainer,
    
    background = NaturalOnBackground, // Inverted for dark mode
    onBackground = NaturalBackground,
    surface = NaturalOnBackground,
    onSurface = NaturalBackground,
    surfaceVariant = Color(0xFF323035) // Dark slate-lavender
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Set dynamicColor to false by default to showcase the Natural Tones palette exactly
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
