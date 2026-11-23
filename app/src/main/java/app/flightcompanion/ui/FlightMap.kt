package app.flightcompanion.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.flightcompanion.data.Flight
import app.flightcompanion.data.Track
import app.flightcompanion.data.WorldData
import app.flightcompanion.data.greatCircle
import app.flightcompanion.data.haversineKm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MapPaths(val landCoarse: Path, val landFine: Path, val borderCoarse: Path, val borderFine: Path)

/** Costruisce una sola volta i tracciati del mondo (in unità "gradi": x = lon+180, y = 90-lat). */
object MapCache {
    @Volatile
    private var paths: MapPaths? = null

    suspend fun get(ctx: Context): MapPaths {
        paths?.let { return it }
        val built = withContext(Dispatchers.Default) {
            val l = WorldData.loadAll(ctx.applicationContext)
            MapPaths(
                polys(l.landCoarse, true),
                polys(l.landFine, true),
                polys(l.bordersCoarse, false),
                polys(l.bordersFine, false)
            )
        }
        paths = built
        return built
    }

    private fun polys(list: List<FloatArray>, close: Boolean): Path {
        val path = Path()
        for (a in list) {
            var i = 0
            while (i + 1 < a.size) {
                val x = a[i] + 180f
                val y = 90f - a[i + 1]
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                i += 2
            }
            if (close) path.close()
        }
        return path
    }
}

class MapStyle(
    val sea: Color,
    val land: Color,
    val border: Color,
    val route: Color,
    val track: Color,
    val dot: Color,
    val label: Color
)

@Composable
fun defaultMapStyle(): MapStyle {
    val c = MaterialTheme.colorScheme
    return MapStyle(
        sea = c.surface,
        land = c.surfaceVariant,
        border = c.outline.copy(alpha = 0.55f),
        route = c.primary,
        track = c.tertiary,
        dot = c.tertiary,
        label = c.onSurface
    )
}

val PassportMapStyle = MapStyle(
    sea = Color.Transparent,
    land = Color(0xFF244A78),
    border = Color(0x00000000),
    route = Color(0xE6F3EEE3),
    track = Color(0xFFF3EEE3),
    dot = Color(0xFFFF7A3D),
    label = Color.White
)

class MapRoute(val points: List<Pair<Double, Double>>, val isTrack: Boolean)
class MapPoint(val code: String, val lat: Double, val lon: Double)

private fun densify(pts: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
    if (pts.size < 2) return pts
    val out = ArrayList<Pair<Double, Double>>()
    out.add(pts[0])
    for (i in 1 until pts.size) {
        val a = pts[i - 1]
        val b = pts[i]
        val d = haversineKm(a.first, a.second, b.first, b.second)
        if (d > 150.0) {
            val n = (d / 150.0).toInt() + 1
            val gc = greatCircle(a.first, a.second, b.first, b.second, n)
            for (j in 1 until gc.size) out.add(gc[j])
        } else {
            out.add(b)
        }
    }
    return out
}

fun buildRoutes(flights: List<Flight>): List<MapRoute> {
    val res = ArrayList<MapRoute>()
    for (f in flights) {
        val la1 = f.depLat
        val lo1 = f.depLon
        val la2 = f.arrLat
        val lo2 = f.arrLon
        if (la1 == null || lo1 == null || la2 == null || lo2 == null) continue
        val tr = Track.decode(f.trackPoints)
        if (f.trackState == 1 && tr.size >= 3) {
            val all = ArrayList<Pair<Double, Double>>()
            all.add(la1 to lo1)
            all.addAll(tr)
            all.add(la2 to lo2)
            res.add(MapRoute(densify(all), true))
        } else {
            res.add(MapRoute(greatCircle(la1, lo1, la2, lo2, 64), false))
        }
    }
    return res
}

fun buildPoints(flights: List<Flight>): List<MapPoint> {
    val m = LinkedHashMap<String, MapPoint>()
    for (f in flights) {
        val a = f.depIata
        val la1 = f.depLat
        val lo1 = f.depLon
        if (a != null && la1 != null && lo1 != null) m.getOrPut(a) { MapPoint(a, la1, lo1) }
        val b = f.arrIata
        val la2 = f.arrLat
        val lo2 = f.arrLon
        if (b != null && la2 != null && lo2 != null) m.getOrPut(b) { MapPoint(b, la2, lo2) }
    }
    return m.values.toList()
}

private fun routePath(r: MapRoute): Path {
    val path = Path()
    var prev: Double? = null
    for ((lat, lon) in r.points) {
        val x = (lon + 180.0).toFloat()
        val y = (90.0 - lat).toFloat()
        val p = prev
        if (p == null || abs(lon - p) > 180.0) path.moveTo(x, y) else path.lineTo(x, y)
        prev = lon
    }
    return path
}

private fun clampOff(off: Offset, k: Float, w: Float, h: Float): Offset {
    val mw = 360f * k
    val mh = 180f * k
    val x = if (mw <= w) (w - mw) / 2f else off.x.coerceIn(w - mw, 0f)
    val y = if (mh <= h) (h - mh) / 2f else off.y.coerceIn(h - mh, 0f)
    return Offset(x, y)
}

/**
 * Mappa piatta statica (passaporto e anteprima del volo). Nessun gesto: l'esplorazione interattiva
 * è affidata al globo 3D.
 */
@Composable
fun FlightMap(
    flights: List<Flight>,
    modifier: Modifier = Modifier,
    style: MapStyle = defaultMapStyle(),
    fitToFlights: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val ctx = LocalContext.current
    val paths by produceState<MapPaths?>(null) { value = MapCache.get(ctx) }
    val routes = remember(flights) { buildRoutes(flights) }
    val points = remember(flights) { buildPoints(flights) }
    val routePaths = remember(routes) { routes.map { routePath(it) } }

    var box by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var off by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(box, routes, fitToFlights) {
        if (box.width == 0 || box.height == 0) return@LaunchedEffect
        val w = box.width.toFloat()
        val h = box.height.toFloat()
        val s0 = w / 360f
        var z = 1f
        var o = Offset.Zero
        if (fitToFlights && routes.isNotEmpty()) {
            var minX = Double.MAX_VALUE
            var maxX = -Double.MAX_VALUE
            var minY = Double.MAX_VALUE
            var maxY = -Double.MAX_VALUE
            for (r in routes) {
                for ((lat, lon) in r.points) {
                    minX = min(minX, lon + 180.0)
                    maxX = max(maxX, lon + 180.0)
                    minY = min(minY, 90.0 - lat)
                    maxY = max(maxY, 90.0 - lat)
                }
            }
            val bw = max(maxX - minX, 12.0)
            val bh = max(maxY - minY, 6.0)
            z = min(w / (s0 * bw * 1.4), h / (s0 * bh * 1.4)).coerceIn(1.0, 4.0).toFloat()
            val k = s0 * z
            val cx = ((minX + maxX) / 2.0).toFloat()
            val cy = ((minY + maxY) / 2.0).toFloat()
            o = Offset(w / 2f - cx * k, h / 2f - cy * k)
        }
        zoom = z
        off = clampOff(o, s0 * z, w, h)
    }

    val clickMod: Modifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier

    Canvas(
        modifier
            .clipToBounds()
            .background(style.sea)
            .onSizeChanged { box = it }
            .then(clickMod)
    ) {
        val s0 = this.size.width / 360f
        val k = s0 * zoom
        val p = paths
        withTransform({
            translate(off.x, off.y)
            scale(k, k, Offset.Zero)
        }) {
            if (p != null) {
                drawPath(p.landCoarse, style.land)
                if (style.border.alpha > 0f) {
                    drawPath(p.borderCoarse, style.border, style = Stroke(width = 0.6f.dp.toPx() / k))
                }
            }
            for ((i, rp) in routePaths.withIndex()) {
                val isTrack = routes[i].isTrack
                drawPath(
                    rp,
                    if (isTrack) style.track else style.route,
                    style = Stroke(width = (if (isTrack) 2.4f else 1.8f).dp.toPx() / k)
                )
            }
            for (pt in points) {
                drawCircle(
                    style.dot,
                    radius = 3.2f.dp.toPx() / k,
                    center = Offset((pt.lon + 180.0).toFloat(), (90.0 - pt.lat).toFloat())
                )
            }
        }
    }
}
