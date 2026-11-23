package app.flightcompanion.data

import android.content.Context
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val p1 = Math.toRadians(lat1)
    val p2 = Math.toRadians(lat2)
    val dp = p2 - p1
    val dl = Math.toRadians(lon2 - lon1)
    val a = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
    return 2 * 6371.0 * asin(sqrt(a))
}

/** Punti (lat, lon) lungo il cerchio massimo tra due coordinate. */
fun greatCircle(lat1: Double, lon1: Double, lat2: Double, lon2: Double, n: Int): List<Pair<Double, Double>> {
    val p1 = Math.toRadians(lat1)
    val l1 = Math.toRadians(lon1)
    val p2 = Math.toRadians(lat2)
    val l2 = Math.toRadians(lon2)
    val d = 2 * asin(sqrt(sin((p2 - p1) / 2).pow(2) + cos(p1) * cos(p2) * sin((l2 - l1) / 2).pow(2)))
    if (d < 1e-9 || sin(d) == 0.0) return listOf(lat1 to lon1, lat2 to lon2)
    val res = ArrayList<Pair<Double, Double>>()
    for (i in 0..n) {
        val f = i.toDouble() / n
        val a = sin((1 - f) * d) / sin(d)
        val b = sin(f * d) / sin(d)
        val x = a * cos(p1) * cos(l1) + b * cos(p2) * cos(l2)
        val y = a * cos(p1) * sin(l1) + b * cos(p2) * sin(l2)
        val z = a * sin(p1) + b * sin(p2)
        res.add(Math.toDegrees(atan2(z, sqrt(x * x + y * y))) to Math.toDegrees(atan2(y, x)))
    }
    return res
}

/** Contorni (Natural Earth): una polilinea per riga, "lon,lat lon,lat ...". */
object WorldData {
    class Layers(
        val landCoarse: List<FloatArray>,
        val landFine: List<FloatArray>,
        val bordersCoarse: List<FloatArray>,
        val bordersFine: List<FloatArray>
    )

    private fun parse(ctx: Context, name: String): List<FloatArray> {
        val out = ArrayList<FloatArray>()
        ctx.assets.open(name).bufferedReader().useLines { seq ->
            for (line in seq) {
                if (line.isBlank()) continue
                val tok = line.trim().split(' ')
                val arr = FloatArray(tok.size * 2)
                for ((i, t) in tok.withIndex()) {
                    val c = t.indexOf(',')
                    arr[2 * i] = t.substring(0, c).toFloat()
                    arr[2 * i + 1] = t.substring(c + 1).toFloat()
                }
                out.add(arr)
            }
        }
        return out
    }

    fun loadAll(ctx: Context): Layers = Layers(
        parse(ctx, "land_coarse.txt"),
        parse(ctx, "land_fine.txt"),
        parse(ctx, "borders_coarse.txt"),
        parse(ctx, "borders_fine.txt")
    )
}

class AirportInfo(
    val iata: String,
    val lat: Double,
    val lon: Double,
    val name: String,
    val city: String,
    val country: String,
    val tz: String?
)

/** Aeroporti per codice IATA (OurAirports + OpenFlights): coordinate, nome, città, paese, fuso orario. */
object AirportDb {
    private val map: Map<String, AirportInfo> by lazy {
        val m = HashMap<String, AirportInfo>()
        try {
            Graph.appContext.assets.open("airports.csv").bufferedReader().useLines { seq ->
                for (line in seq) {
                    val p = line.split('|')
                    if (p.size < 7) continue
                    val la = p[1].toDoubleOrNull()
                    val lo = p[2].toDoubleOrNull()
                    if (la == null || lo == null) continue
                    m[p[0]] = AirportInfo(p[0], la, lo, p[3], p[4], p[5], p[6].ifBlank { null })
                }
            }
        } catch (e: Exception) {
            // asset mancante: nessun dato
        }
        m
    }

    fun get(iata: String?): AirportInfo? = if (iata == null) null else map[iata.uppercase()]

    fun coords(iata: String?): Pair<Double, Double>? = get(iata)?.let { it.lat to it.lon }
}

class AirlineInfo(val iata: String, val icao: String, val name: String)

/** Compagnie per codice IATA/ICAO (OpenFlights, solo attive). */
object AirlineDb {
    private val byIataMap = HashMap<String, AirlineInfo>()
    private val byIcaoMap = HashMap<String, AirlineInfo>()

    private val loaded: Boolean by lazy {
        try {
            Graph.appContext.assets.open("airlines.csv").bufferedReader().useLines { seq ->
                for (line in seq) {
                    val p = line.split('|')
                    if (p.size < 3) continue
                    val a = AirlineInfo(p[0], p[1], p[2])
                    byIataMap[a.iata.uppercase()] = a
                    byIcaoMap[a.icao.uppercase()] = a
                }
            }
        } catch (e: Exception) {
            // asset mancante
        }
        true
    }

    fun byIata(code: String): AirlineInfo? = if (loaded) byIataMap[code.uppercase()] else null
    fun byIcao(code: String): AirlineInfo? = if (loaded) byIcaoMap[code.uppercase()] else null

    /** Deduce la compagnia dal prefisso di un numero volo (IATA a 2 caratteri o ICAO a 3 lettere). */
    fun fromFlightNumber(number: String): AirlineInfo? {
        val n = number.replace(" ", "").uppercase()
        if (n.length >= 3) {
            byIata(n.take(2))?.let { return it }
            if (n.take(3).all { it.isLetter() }) byIcao(n.take(3))?.let { return it }
        }
        return null
    }
}
