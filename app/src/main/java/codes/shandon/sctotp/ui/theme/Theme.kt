package codes.shandon.sctotp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = HotPink,
    onPrimary = Ink,
    primaryContainer = HotPink,
    onPrimaryContainer = Ink,
    secondary = Lavender,
    onSecondary = Ink,
    secondaryContainer = InkHighest,
    onSecondaryContainer = Lavender,
    tertiary = Teal,
    onTertiary = Ink,
    error = Coral,
    onError = Ink,
    background = Ink,
    onBackground = Cream,
    surface = Ink,
    onSurface = Cream,
    onSurfaceVariant = Mist,
    surfaceContainerLowest = Ink,
    surfaceContainerLow = InkRaised,
    surfaceContainer = InkRaised,
    surfaceContainerHigh = InkHigh,
    surfaceContainerHighest = InkHighest,
    outline = InkLine,
    outlineVariant = InkHigh,
    inverseSurface = Lavender,
    inverseOnSurface = Ink,
    inversePrimary = HotPinkDeep
)

private val LightColorScheme = lightColorScheme(
    primary = HotPinkDeep,
    onPrimary = PaperRaised,
    primaryContainer = HotPink,
    onPrimaryContainer = Ink,
    secondary = LavenderDeep,
    onSecondary = PaperRaised,
    secondaryContainer = PaperHighest,
    onSecondaryContainer = LavenderDeep,
    tertiary = TealDeep,
    onTertiary = PaperRaised,
    error = CoralDeep,
    onError = PaperRaised,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    onSurfaceVariant = Graphite,
    surfaceContainerLowest = PaperRaised,
    surfaceContainerLow = PaperRaised,
    surfaceContainer = PaperRaised,
    surfaceContainerHigh = PaperHigh,
    surfaceContainerHighest = PaperHighest,
    outline = Ink,
    outlineVariant = PaperLine,
    inverseSurface = Ink,
    inverseOnSurface = Cream,
    inversePrimary = HotPink
)

// Crisper corners than default Material, closer to rounded TUI boxes
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(18.dp)
)

// Extra colors the Material scheme has no slot for
@Immutable
data class WhimsyColors(
    val shadow: Color,
    val gradientStart: Color,
    val gradientEnd: Color,
    val ink: Color
)

val LocalWhimsyColors = staticCompositionLocalOf {
    WhimsyColors(shadow = Cyan, gradientStart = Lavender, gradientEnd = Teal, ink = Ink)
}

@Composable
fun SCTOTPTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val whimsy = if (darkTheme) {
        WhimsyColors(shadow = Cyan, gradientStart = Lavender, gradientEnd = Teal, ink = Ink)
    } else {
        WhimsyColors(shadow = CyanDeep, gradientStart = LavenderDeep, gradientEnd = TealDeep, ink = Ink)
    }

    CompositionLocalProvider(LocalWhimsyColors provides whimsy) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = Typography,
            shapes = AppShapes,
            content = content
        )
    }
}
