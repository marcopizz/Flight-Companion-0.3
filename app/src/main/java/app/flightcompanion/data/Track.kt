package app.flightcompanion.data

import java.util.Locale

object Track {
    fun encode(points: List<Pair<Double, Double>>): String =
        points.joinToString(";") { String.format(Locale.US, "%.3f,%.3f", it.first, it.second) }

    fun decode(s: String?): List<Pair<Double, Double>> {
        if (s.isNullOrBlank()) return emptyList()
        val out = ArrayList<Pair<Double, Double>>()
        for (item in s.split(';')) {
            val p = item.split(',')
            if (p.size != 2) continue
            val a = p[0].toDoubleOrNull()
            val b = p[1].toDoubleOrNull()
            if (a != null && b != null) out.add(a to b)
        }
        return out
    }
}

sealed class TrackResult {
    class Ok(val points: List<Pair<Double, Double>>) : TrackResult()
    object NoData : TrackResult()
    object Failed : TrackResult()
}

/** Recupero della traccia ADS-B di un volo da OpenSky Network (endpoint sperimentale). */
object OpenSkyClient {
    private var token: String? = null
    private var expiresAt = 0L

    private suspend fun authHeader(): String? {
        val id = Prefs.openskyId
        val secret = Prefs.openskySecret
        if (id.isBlank() || secret.isBlank()) return null // accesso anonimo
        val now = System.currentTimeMillis()
        val t = token
        if (t != null && now < expiresAt) return "Bearer $t"
        return try {
            val r = Net.openskyAuth.token("client_credentials", id, secret)
            val b = r.body()
            if (r.isSuccessful && b?.access_token != null) {
                token = b.access_token
                expiresAt = now + ((b.expires_in ?: 1800) - 60) * 1000L
                "Bearer ${b.access_token}"
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /** timeSec: un istante qualsiasi compreso tra decollo e atterraggio (secondi Unix). */
    suspend fun fetch(icao24: String, timeSec: Long): TrackResult {
        return try {
            val auth = authHeader()
            var r = Net.opensky.trackAll(icao24, timeSec, auth)
            if (r.code() == 404 || r.code() == 405) {
                val r2 = Net.opensky.track(icao24, timeSec, auth)
                if (r2.code() != 404 && r2.code() != 405) r = r2
            }
            when {
                r.code() == 404 -> TrackResult.NoData
                !r.isSuccessful -> TrackResult.Failed
                else -> {
                    val path = r.body()?.path ?: return TrackResult.NoData
                    val pts = ArrayList<Pair<Double, Double>>()
                    for (e in path) {
                        if (!e.isJsonArray) continue
                        val a = e.asJsonArray
                        if (a.size() < 3) continue
                        val la = a[1]
                        val lo = a[2]
                        if (la.isJsonNull || lo.isJsonNull) continue
                        pts.add(la.asDouble to lo.asDouble)
                    }
                    if (pts.size < 3) {
                        TrackResult.NoData
                    } else {
                        val step = (pts.size / 200) + 1
                        val slim = pts.filterIndexed { i, _ -> i % step == 0 || i == pts.size - 1 }
                        TrackResult.Ok(slim)
                    }
                }
            }
        } catch (e: Exception) {
            TrackResult.Failed
        }
    }
}
