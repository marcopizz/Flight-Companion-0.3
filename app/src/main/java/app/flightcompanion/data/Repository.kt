package app.flightcompanion.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
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

class Repository(private val dao: FlightDao, private val airportDao: AirportDao) {

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
        ensureAirport(f.depIata)
        ensureAirport(f.arrIata)
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
        ensureAirport(f.depIata)
        ensureAirport(f.arrIata)
        ensurePhoto(id)
        return id
    }

    // ---------- Modifiche manuali ----------

    suspend fun updateManual(id: Long, seat: String?, seatType: String?, cls: String?, reason: String?) =
        dao.updateManual(id, seat?.ifBlank { null }, seatType, cls, reason)

    suspend fun delete(f: Flight) = dao.delete(f)

    // ---------- Aeroporti ----------

    fun airports(): Flow<List<SavedAirport>> = airportDao.observeAll()
    fun airport(iata: String): Flow<SavedAirport?> = airportDao.observe(iata)

    suspend fun ensureAirport(iata: String?) {
        val code = iata?.uppercase() ?: return
        if (AirportDb.get(code) == null) return
        airportDao.insertIgnore(SavedAirport(iata = code, addedAt = System.currentTimeMillis()))
    }

    suspend fun addAirport(iata: String) = ensureAirport(iata)

    suspend fun setFavorite(iata: String, fav: Boolean) = airportDao.setFavorite(iata, fav)

    suspend fun removeAirport(iata: String) = airportDao.delete(iata)

    /** Una tantum: aggiunge all'elenco gli aeroporti dei voli in programma già presenti. */
    suspend fun seedAirportsFromFlights() {
        if (Prefs.airportsSeeded) return
        for (f in dao.pending()) {
            ensureAirport(f.depIata)
            ensureAirport(f.arrIata)
        }
        Prefs.airportsSeeded = true
    }

    /** Meteo aeronautico (METAR + TAF) su richiesta. Ritorna un messaggio d'errore oppure null se ok. */
    suspend fun refreshWeather(iata: String): String? {
        val icao = AirportDb.get(iata)?.icao?.takeIf { it.isNotBlank() } ?: return "Codice ICAO non disponibile per questo aeroporto."
        return try {
            val m = Net.wx.metar(icao, "json")
            val t = Net.wx.taf(icao, "json")
            if (m.code() == 429 || t.code() == 429) return "Troppe richieste al servizio meteo: riprova tra poco."
            val metarObj = m.body()?.let { if (it.size() > 0) it[0].asJsonObject.toString() else null }
            val tafRaw = t.body()?.let {
                if (it.size() > 0) it[0].asJsonObject.get("rawTAF")?.takeIf { e -> !e.isJsonNull }?.asString else null
            }
            if (metarObj == null && tafRaw == null) {
                "Nessuna osservazione METAR/TAF disponibile per $icao."
            } else {
                airportDao.setWeather(iata, metarObj, tafRaw, System.currentTimeMillis())
                null
            }
        } catch (e: Exception) {
            "Errore di rete: ${e.message}"
        }
    }

    /** Gate di un aeroporto da OpenStreetMap (nodi aeroway=gate), salvati in cache dopo il primo scaricamento. */
    class Gate(val ref: String, val lat: Double, val lon: Double)

    private fun gatesFile(iata: String) = File(File(Graph.appContext.filesDir, "gates").apply { mkdirs() }, "$iata.txt")

    fun cachedGates(iata: String): List<Gate> {
        val f = gatesFile(iata)
        if (!f.exists()) return emptyList()
        return try {
            f.readLines().mapNotNull { line ->
                val p = line.split('|')
                if (p.size < 3) null else {
                    val la = p[1].toDoubleOrNull()
                    val lo = p[2].toDoubleOrNull()
                    if (la == null || lo == null) null else Gate(p[0], la, lo)
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun downloadGates(iata: String): String? {
        val a = AirportDb.get(iata) ?: return "Aeroporto sconosciuto."
        val q = "[out:json][timeout:30];node[\"aeroway\"=\"gate\"](around:6000,${a.lat},${a.lon});out;"
        return try {
            val r = Net.overpass.query(q)
            if (!r.isSuccessful) return "Il server OpenStreetMap ha risposto con errore ${r.code()}."
            val els = r.body()?.getAsJsonArray("elements") ?: return "Risposta vuota da OpenStreetMap."
            val lines = ArrayList<String>()
            for (e in els) {
                val o = e.asJsonObject
                val ref = o.getAsJsonObject("tags")?.get("ref")?.takeIf { x -> !x.isJsonNull }?.asString ?: continue
                lines.add("${ref.replace('|', ' ')}|${o.get("lat").asDouble}|${o.get("lon").asDouble}")
            }
            withContext(Dispatchers.IO) { gatesFile(iata).writeText(lines.joinToString("\n")) }
            if (lines.isEmpty()) "Nessun gate mappato in OpenStreetMap per questo aeroporto." else null
        } catch (e: Exception) {
            "Errore di rete: ${e.message}"
        }
    }

    /** Copia il PDF scelto dall'utente nello spazio dell'app, così resta disponibile offline. */
    suspend fun importPdf(iata: String, uri: android.net.Uri): String? =
        withContext(Dispatchers.IO) {
            try {
                val dir = File(Graph.appContext.filesDir, "airport_pdf").apply { mkdirs() }
                val dest = File(dir, "$iata.pdf")
                Graph.appContext.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { out -> input.copyTo(out) }
                } ?: return@withContext "Impossibile leggere il file."
                airportDao.setPdf(iata, dest.absolutePath)
                null
            } catch (e: Exception) {
                "Errore durante l'importazione: ${e.message}"
            }
        }

    suspend fun removePdf(iata: String) {
        val a = airportDao.get(iata)
        a?.pdfPath?.let { File(it).delete() }
        airportDao.setPdf(iata, null)
    }

    // ---------- Aereo in arrivo (volo precedente della stessa matricola) ----------

    /** Cerca il volo che porta l'aereo all'aeroporto di partenza. Ritorna un messaggio d'errore o null. */
    suspend fun refreshInbound(id: Long, force: Boolean): String? {
        val f = dao.get(id) ?: return "Volo non trovato."
        val reg = f.aircraftReg ?: return "La matricola dell'aereo non è ancora stata assegnata."
        val depIata = f.depIata ?: return "Aeroporto di partenza sconosciuto."
        val depSched = f.depScheduledUtc ?: return "Orario di partenza sconosciuto."
        val now = System.currentTimeMillis()
        if (!force && now - f.inboundAt < 10 * M) return null
        if (Prefs.apiKey.isBlank()) return "Inserisci la chiave API nelle Impostazioni."
        return try {
            val prevDay = try { LocalDate.parse(f.dateLocal).minusDays(1).toString() } catch (e: Exception) { f.dateLocal }
            var best: FlightDto? = null
            for (d in listOf(f.dateLocal, prevDay)) {
                val r = Net.aero.flightsByReg(reg, d, "Both", false)
                if (r.code() == 401 || r.code() == 403) return "Chiave API non valida o non abilitata."
                if (r.code() == 429) return "Quota API esaurita."
                if (!r.isSuccessful) continue
                val cand = r.body().orEmpty().filter {
                    it.arrival?.airport?.iata == depIata && it.number?.replace(" ", "") != f.flightNumber
                }.filter { dto ->
                    val t = parseUtc(dto.arrival?.scheduledTime?.utc)
                    t != null && t <= depSched + 30 * M
                }
                best = cand.maxByOrNull { parseUtc(it.arrival?.scheduledTime?.utc) ?: 0L }
                if (best != null) break
            }
            val json = JSONObject()
            if (best == null) {
                json.put("none", true)
            } else {
                val a = best.arrival
                val dpt = best.departure
                json.put("number", best.number ?: "")
                json.put("airline", best.airline?.name ?: "")
                json.put("status", best.status ?: "")
                json.put("fromIata", dpt?.airport?.iata ?: "")
                json.put("fromCity", dpt?.airport?.municipalityName ?: dpt?.airport?.name ?: "")
                json.put("schedArr", parseUtc(a?.scheduledTime?.utc) ?: JSONObject.NULL)
                json.put("revArr", parseUtc(a?.revisedTime?.utc) ?: JSONObject.NULL)
                json.put("actArr", parseUtc(a?.runwayTime?.utc) ?: JSONObject.NULL)
                json.put("schedDep", parseUtc(dpt?.scheduledTime?.utc) ?: JSONObject.NULL)
            }
            dao.updateInbound(id, json.toString(), now)
            null
        } catch (e: Exception) {
            "Errore di rete: ${e.message}"
        }
    }
}
