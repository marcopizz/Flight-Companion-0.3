package app.flightcompanion.data

import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

object Weather {
    private fun wmo(code: Int?): String = when (code) {
        0 -> "Sereno"
        1 -> "Prevalentemente sereno"
        2 -> "Parzialmente nuvoloso"
        3 -> "Coperto"
        45, 48 -> "Nebbia"
        51, 53, 55, 56, 57 -> "Pioviggine"
        61, 63, 65 -> "Pioggia"
        66, 67 -> "Pioggia gelata"
        71, 73, 75, 77 -> "Neve"
        80, 81, 82 -> "Rovesci"
        85, 86 -> "Rovesci di neve"
        95 -> "Temporale"
        96, 99 -> "Temporale con grandine"
        null -> "Meteo n.d."
        else -> "Codice meteo $code"
    }

    /** Riassunto testuale del meteo (ora UTC più vicina) in un punto; null se non disponibile. */
    suspend fun summary(lat: Double?, lon: Double?, utcMillis: Long?): String? {
        if (lat == null || lon == null || utcMillis == null) return null
        return try {
            val inst = Instant.ofEpochMilli(utcMillis)
            val z = inst.atZone(ZoneOffset.UTC)
            val day = z.toLocalDate().toString()
            val ageDays = ChronoUnit.DAYS.between(inst, Instant.now())
            val hourly = "temperature_2m,precipitation,weather_code,wind_speed_10m"
            val r = if (ageDays <= 80) {
                Net.weatherForecast.forecast(lat, lon, hourly, day, day, "UTC")
            } else {
                Net.weatherArchive.archive(lat, lon, hourly, day, day, "UTC")
            }
            val h = r.body()?.hourly ?: return null
            val times = h.time ?: return null
            val key = "%sT%02d:00".format(day, z.hour)
            var i = times.indexOf(key)
            if (i < 0) i = z.hour
            if (i >= times.size) return null
            val temp = h.temperature_2m?.getOrNull(i)
            val wind = h.wind_speed_10m?.getOrNull(i)
            val prec = h.precipitation?.getOrNull(i)
            val parts = ArrayList<String>()
            parts.add(wmo(h.weather_code?.getOrNull(i)))
            if (temp != null) parts.add("${temp.roundToInt()}°C")
            if (wind != null) parts.add("vento ${wind.roundToInt()} km/h")
            if (prec != null && prec > 0.0) parts.add("prec. %.1f mm".format(prec))
            parts.joinToString(", ")
        } catch (e: Exception) {
            null
        }
    }
}
