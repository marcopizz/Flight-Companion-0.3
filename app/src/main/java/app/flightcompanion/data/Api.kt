package app.flightcompanion.data

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.google.gson.JsonArray
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// ---------- AeroDataBox (via RapidAPI) ----------

data class TimeDto(val utc: String?, val local: String?)
data class LocationDto(val lat: Double?, val lon: Double?)
data class AirportDto(
    val iata: String?,
    val icao: String?,
    val name: String?,
    val shortName: String?,
    val municipalityName: String?,
    val location: LocationDto?,
    val countryCode: String?,
    val timeZone: String?
)

data class MovementDto(
    val airport: AirportDto?,
    val scheduledTime: TimeDto?,
    val revisedTime: TimeDto?,
    val predictedTime: TimeDto?,
    val runwayTime: TimeDto?,
    val terminal: String?,
    val gate: String?,
    val baggageBelt: String?
)

data class DistanceDto(val km: Double?, val mile: Double?)
data class AircraftDto(val reg: String?, val modeS: String?, val model: String?)
data class AirlineDto(val name: String?, val iata: String?, val icao: String?)

data class FlightDto(
    val number: String?,
    val status: String?,
    val codeshareStatus: String?,
    val isCargo: Boolean?,
    val departure: MovementDto?,
    val arrival: MovementDto?,
    val greatCircleDistance: DistanceDto?,
    val aircraft: AircraftDto?,
    val airline: AirlineDto?
)

data class AircraftDetailDto(
    val reg: String?,
    val model: String?,
    val ageYears: Double?
)

interface AeroApi {
    @GET("flights/number/{number}/{date}")
    suspend fun flight(
        @Path("number") number: String,
        @Path("date") date: String,
        @Query("dateLocalRole") role: String,
        @Query("withLocation") withLocation: Boolean,
        @Query("withAircraftImage") withImage: Boolean
    ): Response<List<FlightDto>>

    @GET("aircrafts/reg/{reg}")
    suspend fun aircraft(@Path("reg") reg: String): Response<AircraftDetailDto>
}

// ---------- Open-Meteo ----------

data class HourlyDto(
    val time: List<String>?,
    val temperature_2m: List<Double?>?,
    val precipitation: List<Double?>?,
    val weather_code: List<Int?>?,
    val wind_speed_10m: List<Double?>?
)

data class WeatherDto(val hourly: HourlyDto?)

interface WeatherApi {
    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("hourly") hourly: String,
        @Query("start_date") start: String,
        @Query("end_date") end: String,
        @Query("timezone") tz: String
    ): Response<WeatherDto>

    @GET("v1/archive")
    suspend fun archive(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("hourly") hourly: String,
        @Query("start_date") start: String,
        @Query("end_date") end: String,
        @Query("timezone") tz: String
    ): Response<WeatherDto>
}

// ---------- Planespotters (foto) ----------

data class PsThumb(val src: String?)
data class PsPhoto(
    val thumbnail_large: PsThumb?,
    val thumbnail: PsThumb?,
    val link: String?,
    val photographer: String?
)

data class PsResponse(val photos: List<PsPhoto>?)

interface PlanespottersApi {
    @GET("pub/photos/reg/{reg}")
    suspend fun byReg(@Path("reg") reg: String): Response<PsResponse>
}

// ---------- OpenSky (rotte reali) ----------

data class OsTrack(
    val icao24: String?,
    val startTime: Long?,
    val endTime: Long?,
    val path: JsonArray?
)

data class OsToken(val access_token: String?, val expires_in: Int?)

interface OpenSkyApi {
    @GET("api/tracks/all")
    suspend fun trackAll(
        @Query("icao24") icao24: String,
        @Query("time") time: Long,
        @Header("Authorization") auth: String?
    ): Response<OsTrack>

    @GET("api/tracks")
    suspend fun track(
        @Query("icao24") icao24: String,
        @Query("time") time: Long,
        @Header("Authorization") auth: String?
    ): Response<OsTrack>
}

interface OpenSkyAuthApi {
    @FormUrlEncoded
    @POST("auth/realms/opensky-network/protocol/openid-connect/token")
    suspend fun token(
        @Field("grant_type") grantType: String,
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String
    ): Response<OsToken>
}

object Net {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(Interceptor { chain ->
            val req = chain.request()
            if (req.url.host.contains("rapidapi.com")) {
                val withKey = req.newBuilder()
                    .header("X-RapidAPI-Key", Prefs.apiKey)
                    .header("X-RapidAPI-Host", "aerodatabox.p.rapidapi.com")
                    .build()
                Prefs.countCall()
                chain.proceed(withKey)
            } else if (req.url.host.contains("planespotters")) {
                // Planespotters richiede una User-Agent che identifichi l'app
                chain.proceed(req.newBuilder().header("User-Agent", "FlightCompanion/0.2 (personal use)").build())
            } else {
                chain.proceed(req)
            }
        })
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun retrofit(base: String): Retrofit =
        Retrofit.Builder()
            .baseUrl(base)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    val aero: AeroApi by lazy { retrofit("https://aerodatabox.p.rapidapi.com/").create(AeroApi::class.java) }
    val weatherForecast: WeatherApi by lazy { retrofit("https://api.open-meteo.com/").create(WeatherApi::class.java) }
    val weatherArchive: WeatherApi by lazy { retrofit("https://archive-api.open-meteo.com/").create(WeatherApi::class.java) }
    val planespotters: PlanespottersApi by lazy { retrofit("https://api.planespotters.net/").create(PlanespottersApi::class.java) }
    val opensky: OpenSkyApi by lazy { retrofit("https://opensky-network.org/").create(OpenSkyApi::class.java) }
    val openskyAuth: OpenSkyAuthApi by lazy { retrofit("https://auth.opensky-network.org/").create(OpenSkyAuthApi::class.java) }
}
