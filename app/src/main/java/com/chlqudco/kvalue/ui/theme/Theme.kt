package com.chlqudco.kvalue.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = KValueDarkBlue,
    primaryContainer = KValueDarkBlueContainer,
    tertiary = KValueDarkPositive,
    background = KValueDarkBackground,
    surface = KValueDarkBackground
)

private val LightColorScheme = lightColorScheme(
    primary = KValueBlue,
    primaryContainer = KValueBlueContainer,
    tertiary = KValuePositive,
    background = KValueLightBackground,
    surface = KValueLightBackground
)

@Composable
fun KValueTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
