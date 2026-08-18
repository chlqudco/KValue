/*
 * 앱 전체에 적용되는 Material 3 색상 체계와 KValueTheme 진입점을 정의한다.
 * 현재는 밝은 색상표를 기본으로 사용하고 content 람다 전체를 MaterialTheme으로 감싼다.
 * 화면은 이 테마를 통해 색상·타이포그래피를 받아 구체 토큰 구현과 분리된다.
 */
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
