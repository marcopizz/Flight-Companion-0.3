package app.flightcompanion.data

data class RankItem(
    val label: String,
    val count: Int,
    val icao: String? = null,
    val iata: String? = null,
    val flag: String? = null
)

data class AirlineDelay(val name: String, val icao: String?, val iata: String?, val minutes: Long)

data class Stats(
    val flights: Int,
    val km: Double,
    val minutes: Long,
    val avgMinutes: Int,
    val avgArrDelay: Double?,
    val shortest: Flight?,
    val longest: Flight?,
    val airportCount: Int,
    val airlineCount: Int,
    val routeCount: Int,
    val countryCount: Int,
    val airports: List<RankItem>,
    val airlines: List<RankItem>,
    val routes: List<RankItem>,
    val aircraftRegs: List<RankItem>,
    val aircraftModels: List<RankItem>,
    val countries: List<RankItem>,
    val countryCodes: List<String>,
    val airportCodes: List<String>,
    // aerei
    val aircraftCount: Int,
    val newest: Flight?,
    val oldest: Flight?,
    val topManufacturer: String?,
    val topManufacturerPct: Int,
    val topModel: RankItem?,
    val topModelMinutes: Long,
    val topReg: RankItem?,
    val topRegFlight: Flight?,
    val classCounts: Map<String, Int>,
    val favSeat: RankItem?,
    val seatTypeCounts: Map<String, Int>,
    val reasonCounts: Map<String, Int>,
    // ritardi
    val delayFlights: Int,
    val delayedCount: Int,
    val delayedPct: Int,
    val lostMinutes: Long,
    val avgDelayedMin: Int,
    val worstDelay: Flight?,
    val airlineDelays: List<AirlineDelay>
)

fun yearOf(f: Flight): Int = f.dateLocal.take(4).toIntOrNull() ?: 0

object StatsCalc {
    private fun rank(keys: List<String>): List<Pair<String, Int>> =
        keys.groupingBy { it }.eachCount().toList()
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })

    private fun items(keys: List<String>): List<RankItem> = rank(keys).map { RankItem(it.first, it.second) }

    fun compute(fl: List<Flight>): Stats {
        val km = fl.sumOf { it.distanceKm ?: 0.0 }
        val minutes = fl.sumOf { (it.durationMin ?: 0).toLong() }
        val withDur = fl.filter { it.durationMin != null }
        val avg = if (withDur.isEmpty()) 0 else (withDur.sumOf { it.durationMin!!.toLong() } / withDur.size).toInt()
        val delays = fl.mapNotNull { it.delayArrMin }
        val avgDelay = if (delays.isEmpty()) null else delays.average()
        val withDist = fl.filter { it.distanceKm != null }

        // Aeroporti e paesi
        val airportKeys = ArrayList<String>()
        val countryCodesAll = ArrayList<String>()
        for (f in fl) {
            f.depIata?.let { airportKeys.add("$it – ${f.depCity ?: f.depName ?: ""}") }
            f.arrIata?.let { airportKeys.add("$it – ${f.arrCity ?: f.arrName ?: ""}") }
            f.depCountry?.let { countryCodesAll.add(it.uppercase()) }
            f.arrCountry?.let { countryCodesAll.add(it.uppercase()) }
        }
        val airports = items(airportKeys)
        val countries = rank(countryCodesAll).map {
            RankItem(countryName(it.first), it.second, flag = flagEmoji(it.first))
        }
        val countryCodes = rank(countryCodesAll).map { it.first }
        val airportCodes = rank(fl.flatMap { listOfNotNull(it.depIata, it.arrIata) }).map { it.first }

        // Rotte
        val routes = items(
            fl.mapNotNull { f ->
                val a = f.depIata
                val b = f.arrIata
                if (a == null || b == null) null else listOf(a, b).sorted().joinToString(" ↔ ")
            }
        )

        // Compagnie (con icao per i loghi)
        val airlineRank = rank(fl.mapNotNull { it.airlineName ?: it.airlineIata })
        val airlines = airlineRank.map { (name, n) ->
            val ref = fl.firstOrNull { (it.airlineName ?: it.airlineIata) == name }
            RankItem(name, n, icao = ref?.airlineIcao, iata = ref?.airlineIata)
        }

        // Aerei
        val regRank = rank(fl.mapNotNull { it.aircraftReg })
        val aircraftRegs = regRank.map { RankItem(it.first, it.second) }
        val modelRank = rank(fl.mapNotNull { it.aircraftModel })
        val aircraftModels = modelRank.map { RankItem(it.first, it.second) }
        val withAge = fl.filter { it.aircraftAgeYears != null }
        val manufRank = rank(fl.mapNotNull { manufacturerOf(it.aircraftModel) })
        val manufTotal = manufRank.sumOf { it.second }
        val topModel = aircraftModels.firstOrNull()
        val topModelMinutes = if (topModel == null) 0L else
            fl.filter { it.aircraftModel == topModel.label }.sumOf { (it.durationMin ?: 0).toLong() }
        val topReg = aircraftRegs.firstOrNull()
        val classCounts = rank(fl.mapNotNull { it.travelClass }).toMap()
        val favSeat = rank(fl.mapNotNull { it.seat }).firstOrNull()?.let { RankItem(it.first, it.second) }

        // Ritardi (solo voli con dato di ritardo all'arrivo)
        val withDelay = fl.filter { it.delayArrMin != null }
        val late = withDelay.filter { (it.delayArrMin ?: 0) > 0 }
        val lost = late.sumOf { (it.delayArrMin ?: 0).toLong() }
        val airlineDelays = late
            .groupBy { it.airlineName ?: it.airlineIata ?: "?" }
            .map { (name, list) ->
                AirlineDelay(
                    name,
                    list.firstOrNull()?.airlineIcao,
                    list.firstOrNull()?.airlineIata,
                    list.sumOf { (it.delayArrMin ?: 0).toLong() }
                )
            }
            .sortedByDescending { it.minutes }

        return Stats(
            flights = fl.size,
            km = km,
            minutes = minutes,
            avgMinutes = avg,
            avgArrDelay = avgDelay,
            shortest = withDist.minByOrNull { it.distanceKm!! },
            longest = withDist.maxByOrNull { it.distanceKm!! },
            airportCount = airports.size,
            airlineCount = airlines.size,
            routeCount = routes.size,
            countryCount = countries.size,
            airports = airports,
            airlines = airlines,
            routes = routes,
            aircraftRegs = aircraftRegs,
            aircraftModels = aircraftModels,
            countries = countries,
            countryCodes = countryCodes,
            airportCodes = airportCodes,
            aircraftCount = aircraftRegs.size,
            newest = withAge.minByOrNull { it.aircraftAgeYears!! },
            oldest = withAge.maxByOrNull { it.aircraftAgeYears!! },
            topManufacturer = manufRank.firstOrNull()?.first,
            topManufacturerPct = if (manufTotal == 0) 0 else (100.0 * (manufRank.firstOrNull()?.second ?: 0) / manufTotal).toInt(),
            topModel = topModel,
            topModelMinutes = topModelMinutes,
            topReg = topReg,
            topRegFlight = if (topReg == null) null else fl.firstOrNull { it.aircraftReg == topReg.label },
            classCounts = classCounts,
            favSeat = favSeat,
            seatTypeCounts = rank(fl.mapNotNull { it.seatType }).toMap(),
            reasonCounts = rank(fl.mapNotNull { it.travelReason }).toMap(),
            delayFlights = withDelay.size,
            delayedCount = late.size,
            delayedPct = if (withDelay.isEmpty()) 0 else (100.0 * late.size / withDelay.size).toInt(),
            lostMinutes = lost,
            avgDelayedMin = if (late.isEmpty()) 0 else (lost / late.size).toInt(),
            worstDelay = late.maxByOrNull { it.delayArrMin ?: 0 },
            airlineDelays = airlineDelays
        )
    }
}
