package com.eltnegcellist.emma.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val EmmaColorScheme = lightColorScheme(
    primary = Color(0xFF6F4DB0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE2FF),
    onPrimaryContainer = Color(0xFF2C1754),
    secondary = Color(0xFFB44E72),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9E5),
    onSecondaryContainer = Color(0xFF4D1027),
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF211A24),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF211A24),
    surfaceVariant = Color(0xFFF2ECF4),
    onSurfaceVariant = Color(0xFF4B454E),
    outline = Color(0xFF7C747F),
    error = Color(0xFFBA1A1A),
)

private val EmmaShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

@Composable
internal fun EmmaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EmmaColorScheme,
        shapes = EmmaShapes,
        content = content,
    )
}
