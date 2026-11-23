package app.flightcompanion.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "flights")
data class Flight(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val flightNumber: String,
    val dateLocal: String,
    val status: String = "Expected",

    val airlineName: String? = null,
    val airlineIata: String? = null,
    val airlineIcao: String? = null,

    val depIata: String? = null,
    val depName: String? = null,
    val depCity: String? = null,
    val depCountry: String? = null,
    val depLat: Double? = null,
    val depLon: Double? = null,
    val depTz: String? = null,

    val arrIata: String? = null,
    val arrName: String? = null,
    val arrCity: String? = null,
    val arrCountry: String? = null,
    val arrLat: Double? = null,
    val arrLon: Double? = null,
    val arrTz: String? = null,

    val depScheduledUtc: Long? = null,
    val depRevisedUtc: Long? = null,
    val depActualUtc: Long? = null,
    val arrScheduledUtc: Long? = null,
    val arrRevisedUtc: Long? = null,
    val arrActualUtc: Long? = null,

    val depTerminal: String? = null,
    val depGate: String? = null,
    val arrTerminal: String? = null,
    val arrGate: String? = null,
    val baggageBelt: String? = null,

    val aircraftReg: String? = null,
    val aircraftModel: String? = null,
    val aircraftAgeYears: Double? = null,
    val aircraftModeS: String? = null,

    val distanceKm: Double? = null,

    // Dati inseriti a mano
    val seat: String? = null,
    val seatType: String? = null,
    val travelClass: String? = null,
    val travelReason: String? = null,

    // Calcolati a fine volo
    val delayDepMin: Int? = null,
    val delayArrMin: Int? = null,
    val durationMin: Int? = null,
    val weatherDep: String? = null,
    val weatherArr: String? = null,

    // Rotta reale (OpenSky): "lat,lon;lat,lon;..."
    val trackPoints: String? = null,
    val trackState: Int = 0, // 0 = da recuperare, 1 = ok, 2 = non disponibile
    val trackTries: Int = 0,
    val trackTriedAt: Long = 0,

    // Foto dell'aereo (Planespotters)
    val photoUrl: String? = null,
    val photoLink: String? = null,
    val photographer: String? = null,
    val photoTried: Boolean = false,

    val completed: Boolean = false,
    val lastFetchedAt: Long = 0
) {
    val isCanceled: Boolean get() = status.startsWith("Cancel")
}
