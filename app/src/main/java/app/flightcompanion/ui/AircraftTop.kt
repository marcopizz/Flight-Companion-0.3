package app.flightcompanion.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import org.json.JSONObject

/**
 * Sagome in pianta degli aerei (set di forme di tar1090, GPL-2.0+, già incluso in assets/shapes.json).
 * Il modello testuale ("Airbus A320-200") viene associato alla forma più vicina.
 */
object AircraftShapes {
    class Shape(val cx: Float, val cy: Float, val w: Float, val h: Float, val path: Path)

    private var cache: Map<String, Shape>? = null

    private fun load(ctx: Context): Map<String, Shape> {
        cache?.let { return it }
        val out = HashMap<String, Shape>()
        try {
            val txt = ctx.assets.open("shapes.json").bufferedReader().use { it.readText() }
            val shapes = JSONObject(txt).getJSONObject("shapes")
            for (k in shapes.keys()) {
                val o = shapes.getJSONObject(k)
                val vb = o.getJSONArray("vb")
                val x = vb.getDouble(0).toFloat()
                val y = vb.getDouble(1).toFloat()
                val w = vb.getDouble(2).toFloat()
                val h = vb.getDouble(3).toFloat()
                val p = PathParser().parsePathString(o.getString("path")).toPath()
                out[k] = Shape(x + w / 2f, y + h / 2f, w, h, p)
            }
        } catch (e: Exception) {
            // nessuna sagoma disponibile
        }
        cache = out
        return out
    }

    fun nameFor(model: String?): String {
        val m = (model ?: "").uppercase()
        fun has(vararg k: String) = k.any { m.contains(it) }
        return when {
            has("A380") -> "a380"
            has("A350") -> "a359"
            has("A330", "A332", "A333", "A338", "A339") -> "a332"
            has("A340") -> "heavy_4e"
            has("A300", "A310") -> "heavy_2e"
            has("A321") -> "a321"
            has("A320", "C919") -> "a320"
            has("A318", "A319") -> "a319"
            has("A220", "BCS") -> "airliner"
            has("747") -> "heavy_4e"
            has("757", "767", "777", "787") -> "heavy_2e"
            has("737") -> when {
                has("800", "MAX 8", "8 MAX", "-8") -> "b738"
                has("900", "MAX 9", "MAX 10", "9 MAX", "-9", "-10") -> "b739"
                else -> "b737"
            }
            has("MD-11", "MD11", "DC-10") -> "md11"
            has("MD-8", "MD-9", "MD8", "MD9", "717", "DC-9", "FOKKER 100", "FOKKER 70", "F100", "ERJ", "E135", "E145", "CRJ", "CANADAIR") -> "jet_swept"
            has("E170", "E175", "E190", "E195", "EMBRAER 17", "EMBRAER 19", "E2", "SUPERJET", "SSJ", "ARJ") -> "airliner"
            has("ATR", "DASH 8", "DHC-8", "Q400", "SAAB", "DORNIER", "FOKKER 50") -> "twin_large"
            has("BAE 146", "AVRO", "146") -> "b707"
            has("IL-62", "TU-134", "TU-154") -> "il_62"
            has("IL-96", "IL-86") -> "heavy_4e"
            else -> "airliner"
        }
    }

    fun get(ctx: Context, model: String?): Shape? {
        val all = load(ctx)
        return all[nameFor(model)] ?: all["airliner"]
    }
}

/** Sagoma in pianta con il muso verso destra, centrata nel riquadro. */
@Composable
fun AircraftTopView(model: String?, color: Color, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val shape = remember(model) { AircraftShapes.get(ctx, model) }
    Canvas(modifier.fillMaxWidth().aspectRatio(2.6f)) {
        val s = shape ?: return@Canvas
        val k = minOf(size.width / s.h, size.height / s.w) * 0.9f
        withTransform({
            translate(size.width / 2f, size.height / 2f)
            rotate(90f, Offset.Zero)
            scale(k, k, Offset.Zero)
            translate(-s.cx, -s.cy)
        }) {
            drawPath(s.path, color)
        }
    }
}

/** Riquadro stile disegno tecnico: griglia, sagoma, quote. */
@Composable
fun BlueprintPlane(model: String?, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val shape = remember(model) { AircraftShapes.get(ctx, model) }
    Canvas(modifier.fillMaxWidth().aspectRatio(2.2f)) {
        val grid = Color(0x26FFFFFF)
        val step = 22.dpPx()
        var x = 0f
        while (x < size.width) {
            drawLine(grid, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            x += step
        }
        var y = 0f
        while (y < size.height) {
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            y += step
        }
        val s = shape ?: return@Canvas
        val k = minOf(size.width / s.h, size.height / s.w) * 0.78f
        withTransform({
            translate(size.width / 2f, size.height / 2f)
            rotate(90f, Offset.Zero)
            scale(k, k, Offset.Zero)
            translate(-s.cx, -s.cy)
        }) {
            drawPath(s.path, Color(0x22FFFFFF))
            drawPath(s.path, Color(0xFFF3EEE3), style = Stroke(width = 1.6f / k))
        }
        // quote: apertura alare e lunghezza
        val half = s.w * k / 2f
        val len = s.h * k / 2f
        val cy = size.height / 2f
        val cx = size.width / 2f
        val dim = Color(0xFFFFC400)
        drawLine(dim, Offset(cx + len * 0.15f, cy - half), Offset(cx + len * 0.15f, cy + half), strokeWidth = 1.5f)
        drawLine(dim, Offset(cx - len, cy + half + 8.dpPx()), Offset(cx + len, cy + half + 8.dpPx()), strokeWidth = 1.5f)
    }
}

private fun Int.dpPx(): Float = this * android.content.res.Resources.getSystem().displayMetrics.density
