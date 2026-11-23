package app.flightcompanion.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.flightcompanion.R

// Identità: segnaletica aeroportuale + carta d'imbarco. Carta calda, inchiostro blu notte,
// arancio segnale e giallo "taxiway".
val Ink = Color(0xFF0F1B2D)
val Paper = Color(0xFFF3EEE3)
val PaperDeep = Color(0xFFE8E1D2)
val Signal = Color(0xFFE8501A)
val Amber = Color(0xFFF2B200)
val Night = Color(0xFF0B1320)
val Blueprint = Color(0xFF0F3B6B)

val BarlowCondensed = FontFamily(
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_condensed_bold, FontWeight.Bold)
)
val Barlow = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_semibold, FontWeight.SemiBold)
)
val PlexMono = FontFamily(
    Font(R.font.plex_mono_regular, FontWeight.Normal),
    Font(R.font.plex_mono_medium, FontWeight.Medium),
    Font(R.font.plex_mono_semibold, FontWeight.SemiBold)
)

private val LightColors = lightColorScheme(
    primary = Signal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCCB),
    onPrimaryContainer = Color(0xFF3A1100),
    secondary = Color(0xFF17304D),
    onSecondary = Paper,
    secondaryContainer = Color(0xFFF7E3A6),
    onSecondaryContainer = Ink,
    tertiary = Color(0xFF1F7A6B),
    background = Paper,
    onBackground = Ink,
    surface = Color(0xFFFBF8F1),
    onSurface = Ink,
    surfaceVariant = PaperDeep,
    onSurfaceVariant = Color(0xFF4A5568),
    outline = Color(0xFFB9AF9A),
    error = Color(0xFFB3261E)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF7A3D),
    onPrimary = Color(0xFF2A0D00),
    primaryContainer = Color(0xFF5A2410),
    onPrimaryContainer = Color(0xFFFFDCCB),
    secondary = Amber,
    onSecondary = Ink,
    secondaryContainer = Color(0xFF4A3A00),
    onSecondaryContainer = Color(0xFFFFE9A8),
    tertiary = Color(0xFF53D1BE),
    background = Night,
    onBackground = Color(0xFFEDE7D9),
    surface = Color(0xFF121C2C),
    onSurface = Color(0xFFEDE7D9),
    surfaceVariant = Color(0xFF1B2A41),
    onSurfaceVariant = Color(0xFFB5BDC9),
    outline = Color(0xFF3B4C68),
    error = Color(0xFFFF6B5E)
)

private fun appTypography(): Typography {
    val b = Typography()
    return b.copy(
        displayLarge = b.displayLarge.copy(fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold),
        displayMedium = b.displayMedium.copy(fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold),
        displaySmall = b.displaySmall.copy(fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold),
        headlineLarge = b.headlineLarge.copy(fontFamily = BarlowCondensed, fontWeight = FontWeight.SemiBold),
        headlineMedium = b.headlineMedium.copy(fontFamily = BarlowCondensed, fontWeight = FontWeight.SemiBold),
        headlineSmall = b.headlineSmall.copy(fontFamily = BarlowCondensed, fontWeight = FontWeight.SemiBold),
        titleLarge = b.titleLarge.copy(fontFamily = BarlowCondensed, fontWeight = FontWeight.SemiBold),
        titleMedium = b.titleMedium.copy(fontFamily = Barlow, fontWeight = FontWeight.SemiBold),
        titleSmall = b.titleSmall.copy(fontFamily = Barlow, fontWeight = FontWeight.SemiBold),
        bodyLarge = b.bodyLarge.copy(fontFamily = Barlow),
        bodyMedium = b.bodyMedium.copy(fontFamily = Barlow),
        bodySmall = b.bodySmall.copy(fontFamily = Barlow),
        labelLarge = b.labelLarge.copy(fontFamily = Barlow, fontWeight = FontWeight.Medium),
        labelMedium = b.labelMedium.copy(fontFamily = Barlow, fontWeight = FontWeight.Medium),
        labelSmall = b.labelSmall.copy(fontFamily = Barlow, fontWeight = FontWeight.Medium)
    )
}

@Composable
fun FlightTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = appTypography(),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(2.dp),
            small = RoundedCornerShape(4.dp),
            medium = RoundedCornerShape(6.dp),
            large = RoundedCornerShape(8.dp),
            extraLarge = RoundedCornerShape(12.dp)
        ),
        content = content
    )
}

/** Metà di una carta d'imbarco: angoli superiori (o inferiori) arrotondati e tacche semicircolari sul bordo di giunzione. */
class TicketHalf(
    private val isTop: Boolean,
    private val notch: Dp = 8.dp,
    private val corner: Dp = 10.dp
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { notch.toPx() }
        val c = with(density) { corner.toPx() }
        val rr = if (isTop) {
            RoundRect(0f, 0f, size.width, size.height, CornerRadius(c), CornerRadius(c), CornerRadius.Zero, CornerRadius.Zero)
        } else {
            RoundRect(0f, 0f, size.width, size.height, CornerRadius.Zero, CornerRadius.Zero, CornerRadius(c), CornerRadius(c))
        }
        val base = Path().apply { addRoundRect(rr) }
        val cy = if (isTop) size.height else 0f
        val left = Path().apply { addOval(Rect(Offset(-r, cy - r), Size(2 * r, 2 * r))) }
        val right = Path().apply { addOval(Rect(Offset(size.width - r, cy - r), Size(2 * r, 2 * r))) }
        val a = Path.combine(PathOperation.Difference, base, left)
        return Outline.Generic(Path.combine(PathOperation.Difference, a, right))
    }
}
