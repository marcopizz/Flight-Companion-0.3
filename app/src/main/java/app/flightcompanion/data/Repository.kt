package app.flightcompanion.data

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class ManualFlight(
    val flightNumber: String,
    val date: LocalDate,
    val depIata: String,
    val arrIata: String,
    val depTime: LocalTime?,
    val arrTime: LocalTime?,
    val arrDayOffset: Int,
    val delayDep: Int?,
    val delayArr: Int?,
    val airlineName: String?,
    val airlineIata: String?,
    val airlineIcao: String?,
    val aircraftModel: String?,
    val aircraftReg: String?
)

sealed class Lookup {
    data class Found(val list: List<FlightDto>) : Lookup()
    object NotFound : Lookup()
    data class Error(val msg: String) : Lookup()
}

class Repository(private val dao: FlightDao) {

    private val H = 3_600_000L
    private val M = 60_000L

    fun upcoming(): Flow<List<Flight>> = dao.upcoming()
    fun past(): Flow<List<Flight>> = dao.past()
    fun observe(id: Long): Flow<Flight?> = dao.observe(id)

    // ---------- Ricerca e inserimento ----------

    private fun normalize(raw: String) = raw.replace(" ", "").uppercase()

    private fun apiMessage(r: retrofit2.Response<*>): String =
        try {
            val raw = r.errorBody()?.string().orEmpty()
            Regex("\"message\"\\s*:\\s*\"([^\"]*)\"").find(raw)?.groupValues?.get(1) ?: raw.take(120)
        } catch (e: Exception) {
            ""
        }

    suspend fun lookup(numberRaw: String, date: String): Lookup {
        if (Prefs.apiKey.isBlank()) {
            return Lookup.Error("Inserisci prima la chiave API nelle Impostazioni.")
        }
        return try {
            val r = Net.aero.flight(normalize(numberRaw), date, "Departure", true, false)
            when {
                r.code() == 401 || r.code() == 403 ->
                    Lookup.Error("Chiave API non valida o non abilitata (HTTP ${r.code()}).")
                r.code() == 429 -> Lookup.Error("Quota API esaurita (HTTP 429).")
                r.code() == 404 || r.code() == 204 -> Lookup.NotFound
                r.code() == 400 -> Lookup.Error(
                    "Richiesta rifiutata dall'API (HTTP 400): ${apiMessage(r)}. " +
                        "Di solito la data è fuori dall'intervallo coperto dal tuo piano."
                )
                !r.isSuccessful -> Lookup.Error("Errore API: HTTP ${r.code()} ${apiMessage(r)}")
                else -> {
                    val all = r.body().orEmpty().filter { it.isCargo != true }
                    val operators = all.filter { it.codeshareStatus == "IsOperator" }
                    val list = if (operators.isNotEmpty()) operators else all
                    if (list.isEmpty()) Lookup.NotFound else Lookup.Found(list)
                }
            }
        } catch (e: Exception) {
            Lookup.Error("Errore di rete: ${e.message}")
        }
    }

    suspend fun add(dto: FlightDto, numberRaw: String, date: String): Long {
        val base = Flight(flightNumber = normalize(numberRaw), dateLocal = date)
        val f = applyDto(base, dto).copy(lastFetchedAt = System.currentTimeMillis())
        val id = dao.insert(f)
        maybeFinalize(id)
        return id
    }

    // ---------- Aggiornamento dati ----------

    private val utcFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm[:ss]XXX")

    private fun parseUtc(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        val t = s.trim()
        return try {
            OffsetDateTime.parse(t, utcFmt).toInstant().toEpochMilli()
        } catch (e: Exception) {
            try {
                OffsetDateTime.parse(t).toInstant().toEpochMilli()
            } catch (e2: Exception) {
                null
            }
        }
    }

    private fun applyDto(f: Flight, d: FlightDto): Flight {
        val dep = d.departure
        val arr = d.arrival
        val da = dep?.airport
        val aa = arr?.airport
        val depIata = da?.iata ?: f.depIata
        val arrIata = aa?.iata ?: f.arrIata

        val depLat = da?.location?.lat ?: f.depLat ?: AirportDb.coords(depIata)?.first
        val depLon = da?.location?.lon ?: f.depLon ?: AirportDb.coords(depIata)?.second
        val arrLat = aa?.location?.lat ?: f.arrLat ?: AirportDb.coords(arrIata)?.first
        val arrLon = aa?.location?.lon ?: f.arrLon ?: AirportDb.coords(arrIata)?.second

        val dist = d.greatCircleDistance?.km ?: f.distanceKm ?: run {
            if (depLat != null && depLon != null && arrLat != null && arrLon != null)
                haversineKm(depLat, depLon, arrLat, arrLon) else null
        }

        return f.copy(
            status = d.status ?: f.status,
            airlineName = d.airline?.name ?: f.airlineName,
            airlineIata = d.airline?.iata ?: f.airlineIata,
            airlineIcao = d.airline?.icao ?: f.airlineIcao,

            depIata = depIata,
            depName = da?.shortName ?: da?.name ?: f.depName,
            depCity = da?.municipalityName ?: f.depCity,
            depCountry = da?.countryCode ?: f.depCountry,
            depLat = depLat,
            depLon = depLon,
            depTz = da?.timeZone ?: f.depTz,

            arrIata = arrIata,
            arrName = aa?.shortName ?: aa?.name ?: f.arrName,
            arrCity = aa?.municipalityName ?: f.arrCity,
            arrCountry = aa?.countryCode ?: f.arrCountry,
            arrLat = arrLat,
            arrLon = arrLon,
            arrTz = aa?.timeZone ?: f.arrTz,

            depScheduledUtc = parseUtc(dep?.scheduledTime?.utc) ?: f.depScheduledUtc,
            depRevisedUtc = parseUtc(dep?.revisedTime?.utc) ?: f.depRevisedUtc,
            depActualUtc = parseUtc(dep?.runwayTime?.utc) ?: f.depActualUtc,
            arrScheduledUtc = parseUtc(arr?.scheduledTime?.utc) ?: f.arrScheduledUtc,
            arrRevisedUtc = parseUtc(arr?.revisedTime?.utc) ?: f.arrRevisedUtc,
            arrActualUtc = parseUtc(arr?.runwayTime?.utc) ?: f.arrActualUtc,

            depTerminal = dep?.terminal ?: f.depTerminal,
            depGate = dep?.gate ?: f.depGate,
            arrTerminal = arr?.terminal ?: f.arrTerminal,
            arrGate = arr?.gate ?: f.arrGate,
            baggageBelt = arr?.baggageBelt ?: f.baggageBelt,

            aircraftReg = d.aircraft?.reg ?: f.aircraftReg,
            aircraftModeS = d.aircraft?.modeS ?: f.aircraftModeS,
            aircraftModel = d.aircraft?.model ?: f.aircraftModel,
            distanceKm = dist
        )
    }

    /** Aggiorna un volo dall'API. Ritorna 200 se ok, il codice HTTP in caso di errore, -1 per errori di rete. */
    suspend fun refresh(id: Long, notify: Boolean): Int {
        val old = dao.get(id) ?: return -1
        val now = System.currentTimeMillis()
        // Segna subito il tentativo, così un errore non causa tentativi continui
        dao.update(old.copy(lastFetchedAt = now))
        return try {
            val r = Net.aero.flight(old.flightNumber, old.dateLocal, "Departure", true, false)
            if (!r.isSuccessful) return r.code()
            val list = r.body().orEmpty()
            val dto = list.firstOrNull {
                it.departure?.airport?.iata == old.depIata && it.codeshareStatus == "IsOperator"
            } ?: list.firstOrNull { it.departure?.airport?.iata == old.depIata }
                ?: return 200
            val updated = applyDto(old, dto).copy(lastFetchedAt = now)
            dao.update(updated)
            if (notify) Notifier.diff(old, updated)
            200
        } catch (e: Exception) {
            -1
        }
    }

    suspend fun refreshNow(id: Long) {
        refresh(id, true)
        maybeFinalize(id)
    }

    // ---------- Pianificazione delle chiamate (per risparmiare quota) ----------

    private fun isDue(f: Flight, now: Long): Boolean {
        val dep = f.depRevisedUtc ?: f.depScheduledUtc ?: return false
        val arr = f.arrRevisedUtc ?: f.arrScheduledUtc ?: (dep + 2 * H)
        val interval = when {
            now < dep - 72 * H -> return false
            now < dep - 24 * H -> 12 * H
            now < dep - 6 * H -> 6 * H
            now < dep - 3 * H -> 90 * M
            now < dep -> 30 * M
            now < arr -> 60 * M
            else -> 30 * M
        }
        return now - f.lastFetchedAt >= interval - 2 * M
    }

    /** Chiamata dal worker in background e all'apertura dell'app. */
    suspend fun syncDue() {
        val now = System.currentTimeMillis()
        for (f in dao.pending()) {
            if (isDue(f, now)) {
                val code = refresh(f.id, true)
                if (code == 401 || code == 403 || code == 429) break
            }
            maybeFinalize(f.id)
        }
        for (f in dao.needingTrack(now - 29 * 24 * H)) {
            fetchTrackFor(f.id, false)
        }
    }

    // ---------- Chiusura del volo e archiviazione ----------

    suspend fun maybeFinalize(id: Long) {
        val f = dao.get(id) ?: return
        if (f.completed) return
        val now = System.currentTimeMillis()
        val arrRef = f.arrRevisedUtc ?: f.arrScheduledUtc
        val done = f.status == "Arrived" || f.isCanceled ||
            (arrRef != null && now > arrRef + 6 * H)
        if (done) finalizeFlight(f)
    }

    private fun minutesBetween(a: Long?, b: Long?): Int? =
        if (a == null || b == null) null else ((a - b) / M).toInt()

    private suspend fun finalizeFlight(f0: Flight) {
        var f = f0
        if (!f.isCanceled) {
            val reg = f.aircraftReg
            if (reg != null && f.aircraftAgeYears == null) {
                try {
                    val r = Net.aero.aircraft(reg)
                    if (r.isSuccessful) {
                        val b = r.body()
                        f = f.copy(
                            aircraftAgeYears = b?.ageYears,
                            aircraftModel = f.aircraftModel ?: b?.model
                        )
                    }
                } catch (e: Exception) {
                    // l'età dell'aereo resta non disponibile
                }
            }
            val depActual = f.depActualUtc ?: f.depRevisedUtc
            val arrActual = f.arrActualUtc ?: f.arrRevisedUtc
            val duration = if (f.depActualUtc != null && f.arrActualUtc != null) {
                minutesBetween(f.arrActualUtc, f.depActualUtc)
            } else {
                minutesBetween(f.arrScheduledUtc, f.depScheduledUtc)
            }
            val wDep = Weather.summary(f.depLat, f.depLon, f.depActualUtc ?: f.depScheduledUtc)
            val wArr = Weather.summary(f.arrLat, f.arrLon, f.arrActualUtc ?: f.arrScheduledUtc)
            f = f.copy(
                delayDepMin = minutesBetween(depActual, f.depScheduledUtc),
                delayArrMin = minutesBetween(arrActual, f.arrScheduledUtc),
                durationMin = duration,
                weatherDep = wDep,
                weatherArr = wArr
            )
        }
        dao.update(f.copy(completed = true))
        ensurePhoto(f.id)
        fetchTrackFor(f.id, false)
    }

    // ---------- Foto dell'aereo (Planespotters) ----------

    suspend fun ensurePhoto(id: Long) {
        val f = dao.get(id) ?: return
        val reg = f.aircraftReg ?: return
        if (f.photoTried) return
        try {
            val r = Net.planespotters.byReg(reg)
            if (!r.isSuccessful) return
            val p = r.body()?.photos?.firstOrNull()
            val url = p?.thumbnail_large?.src ?: p?.thumbnail?.src
            dao.updatePhoto(id, url, p?.link, p?.photographer)
        } catch (e: Exception) {
            // riproverà alla prossima apertura della scheda
        }
    }

    // ---------- Rotta reale (OpenSky, solo ultimi 30 giorni) ----------

    suspend fun fetchTrackFor(id: Long, force: Boolean) {
        val f = dao.get(id) ?: return
        val hex = f.aircraftModeS?.lowercase() ?: return
        val dep = f.depActualUtc ?: f.depScheduledUtc ?: return
        val arr = f.arrActualUtc ?: f.arrScheduledUtc ?: return
        val now = System.currentTimeMillis()
        if (now - dep > 29 * 24 * H) {
            if (f.trackState == 0) dao.updateTrack(id, null, 2, f.trackTries, now)
            return
        }
        val baseTries = if (force) 0 else f.trackTries
        if (!force && (f.trackState != 0 || baseTries >= 6 || now - f.trackTriedAt < 6 * H)) return
        val mid = (dep + arr) / 2 / 1000
        when (val r = OpenSkyClient.fetch(hex, mid)) {
            is TrackResult.Ok -> dao.updateTrack(id, Track.encode(r.points), 1, baseTries + 1, now)
            is TrackResult.NoData -> {
                val t = baseTries + 1
                dao.updateTrack(id, null, if (t >= 6) 2 else 0, t, now)
            }
            is TrackResult.Failed -> dao.updateTrack(id, f.trackPoints, f.trackState, baseTries + 1, now)
        }
    }

    // ---------- Inserimento manuale di un volo passato ----------

    suspend fun addManual(m: ManualFlight): Long {
        val dep = AirportDb.get(m.depIata)
        val arr = AirportDb.get(m.arrIata)
        val depSched = m.depTime?.let {
            m.date.atTime(it).atZone(zoneOf(dep?.tz)).toInstant().toEpochMilli()
        }
        val arrSched = m.arrTime?.let {
            m.date.plusDays(m.arrDayOffset.toLong()).atTime(it).atZone(zoneOf(arr?.tz)).toInstant().toEpochMilli()
        }
        val depActual = if (depSched != null && m.delayDep != null) depSched + m.delayDep * M else null
        val arrActual = if (arrSched != null && m.delayArr != null) arrSched + m.delayArr * M else null
        val start = depActual ?: depSched
        val end = arrActual ?: arrSched
        val duration = if (start != null && end != null && end > start) ((end - start) / M).toInt() else null
        val dist = if (dep != null && arr != null) haversineKm(dep.lat, dep.lon, arr.lat, arr.lon) else null

        var model = m.aircraftModel
        var age: Double? = null
        val reg = m.aircraftReg
        if (reg != null && Prefs.apiKey.isNotBlank()) {
            try {
                val r = Net.aero.aircraft(reg)
                if (r.isSuccessful) {
                    age = r.body()?.ageYears
                    if (model == null) model = r.body()?.model
                }
            } catch (e: Exception) {
                // età non disponibile
            }
        }

        val f = Flight(
            flightNumber = normalize(m.flightNumber),
            dateLocal = m.date.toString(),
            status = "Arrived",
            airlineName = m.airlineName,
            airlineIata = m.airlineIata,
            airlineIcao = m.airlineIcao,
            depIata = m.depIata.uppercase(),
            depName = dep?.name,
            depCity = dep?.city,
            depCountry = dep?.country,
            depLat = dep?.lat,
            depLon = dep?.lon,
            depTz = dep?.tz,
            arrIata = m.arrIata.uppercase(),
            arrName = arr?.name,
            arrCity = arr?.city,
            arrCountry = arr?.country,
            arrLat = arr?.lat,
            arrLon = arr?.lon,
            arrTz = arr?.tz,
            depScheduledUtc = depSched,
            depActualUtc = depActual,
            arrScheduledUtc = arrSched,
            arrActualUtc = arrActual,
            aircraftReg = reg,
            aircraftModel = model,
            aircraftAgeYears = age,
            distanceKm = dist,
            delayDepMin = m.delayDep,
            delayArrMin = m.delayArr,
            durationMin = duration,
            weatherDep = Weather.summary(dep?.lat, dep?.lon, start),
            weatherArr = Weather.summary(arr?.lat, arr?.lon, end),
            trackState = 2,
            completed = true
        )
        val id = dao.insert(f)
        ensurePhoto(id)
        return id
    }

    // ---------- Modifiche manuali ----------

    suspend fun updateManual(id: Long, seat: String?, seatType: String?, cls: String?, reason: String?) =
        dao.updateManual(id, seat?.ifBlank { null }, seatType, cls, reason)

    suspend fun delete(f: Flight) = dao.delete(f)
}
