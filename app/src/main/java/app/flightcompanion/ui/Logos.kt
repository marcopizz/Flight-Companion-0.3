package app.flightcompanion.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.BufferedInputStream
import java.util.zip.ZipInputStream
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class Logo(val bitmap: ImageBitmap, val color: Color)

/** Loghi delle compagnie (assets/logos.zip, un file <icao>.webp per compagnia) con colore dominante. */
object LogoCache {
    private val cache = HashMap<String, Logo?>()
    private var files: Map<String, ByteArray>? = null

    private fun entries(ctx: Context): Map<String, ByteArray> {
        files?.let { return it }
        val m = HashMap<String, ByteArray>()
        try {
            ZipInputStream(BufferedInputStream(ctx.assets.open("logos.zip"))).use { zin ->
                var e = zin.nextEntry
                while (e != null) {
                    if (!e.isDirectory) {
                        m[e.name.substringBeforeLast('.').lowercase()] = zin.readBytes()
                    }
                    zin.closeEntry()
                    e = zin.nextEntry
                }
            }
        } catch (ex: Exception) {
            // archivio mancante: nessun logo, restano le sigle
        }
        files = m
        return m
    }

    @Synchronized
    fun get(ctx: Context, icao: String?): Logo? {
        if (icao.isNullOrBlank()) return null
        val key = icao.lowercase()
        if (cache.containsKey(key)) return cache[key]
        val bytes = entries(ctx)[key]
        val logo = if (bytes == null) {
            null
        } else {
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bmp == null) null else Logo(bmp.asImageBitmap(), dominantColor(bmp))
        }
        cache[key] = logo
        return logo
    }

    private fun dominantColor(bmp: Bitmap): Color {
        val small = Bitmap.createScaledBitmap(bmp, 24, 24, true)
        var r = 0.0
        var g = 0.0
        var b = 0.0
        var wSum = 0.0
        val hsv = FloatArray(3)
        for (y in 0 until small.height) {
            for (x in 0 until small.width) {
                val px = small.getPixel(x, y)
                val a = android.graphics.Color.alpha(px)
                if (a < 160) continue
                android.graphics.Color.colorToHSV(px, hsv)
                if (hsv[1] < 0.28f || hsv[2] < 0.2f) continue
                val w = (hsv[1] * hsv[2]).toDouble()
                r += android.graphics.Color.red(px) * w
                g += android.graphics.Color.green(px) * w
                b += android.graphics.Color.blue(px) * w
                wSum += w
            }
        }
        if (wSum <= 0.0) return Color(0xFF1E5FB3)
        return Color((r / wSum).toInt(), (g / wSum).toInt(), (b / wSum).toInt())
    }
}

@Composable
fun airlineColor(icao: String?): Color {
    val ctx = LocalContext.current
    return remember(icao) { LogoCache.get(ctx, icao)?.color ?: Color(0xFF1E5FB3) }
}

@Composable
fun AirlineLogo(icao: String?, iata: String?, modifier: Modifier = Modifier, height: Dp = 24.dp) {
    val ctx = LocalContext.current
    val logo = remember(icao) { LogoCache.get(ctx, icao) }
    if (logo != null) {
        Image(
            bitmap = logo.bitmap,
            contentDescription = null,
            modifier = modifier.height(height).widthIn(max = height * 1.7f),
            contentScale = ContentScale.Fit
        )
    } else {
        Box(
            modifier.size(height).clip(CircleShape).background(Color(0x33888888)),
            contentAlignment = Alignment.Center
        ) {
            Text((iata ?: "?").take(3), fontSize = (height.value * 0.36f).sp, fontWeight = FontWeight.Bold)
        }
    }
}
