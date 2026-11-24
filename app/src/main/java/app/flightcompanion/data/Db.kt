package app.flightcompanion.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface FlightDao {
    @Query("SELECT * FROM flights WHERE completed = 0 ORDER BY COALESCE(depScheduledUtc, 0) ASC")
    fun upcoming(): Flow<List<Flight>>

    @Query("SELECT * FROM flights WHERE completed = 1 ORDER BY COALESCE(depScheduledUtc, 0) DESC")
    fun past(): Flow<List<Flight>>

    @Query("SELECT * FROM flights WHERE id = :id")
    fun observe(id: Long): Flow<Flight?>

    @Query("SELECT * FROM flights WHERE id = :id")
    suspend fun get(id: Long): Flight?

    @Query("SELECT * FROM flights WHERE completed = 0")
    suspend fun pending(): List<Flight>

    @Query("UPDATE flights SET seat = :seat, seatType = :seatType, travelClass = :cls, travelReason = :reason WHERE id = :id")
    suspend fun updateManual(id: Long, seat: String?, seatType: String?, cls: String?, reason: String?)

    @Query("SELECT * FROM flights WHERE completed = 1 AND trackState = 0 AND trackTries < 6 AND depScheduledUtc > :minUtc")
    suspend fun needingTrack(minUtc: Long): List<Flight>

    @Query("UPDATE flights SET trackPoints = :pts, trackState = :state, trackTries = :tries, trackTriedAt = :at WHERE id = :id")
    suspend fun updateTrack(id: Long, pts: String?, state: Int, tries: Int, at: Long)

    @Query("UPDATE flights SET photoUrl = :url, photoLink = :link, photographer = :ph, photoTried = 1 WHERE id = :id")
    suspend fun updatePhoto(id: Long, url: String?, link: String?, ph: String?)

    @Query("UPDATE flights SET inboundJson = :json, inboundAt = :at WHERE id = :id")
    suspend fun updateInbound(id: Long, json: String?, at: Long)

    @Insert
    suspend fun insert(f: Flight): Long

    @Update
    suspend fun update(f: Flight)

    @Delete
    suspend fun delete(f: Flight)
}

@Dao
interface AirportDao {
    @Query("SELECT * FROM airports ORDER BY favorite DESC, addedAt DESC")
    fun observeAll(): Flow<List<SavedAirport>>

    @Query("SELECT * FROM airports WHERE iata = :iata")
    fun observe(iata: String): Flow<SavedAirport?>

    @Query("SELECT * FROM airports WHERE iata = :iata")
    suspend fun get(iata: String): SavedAirport?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(a: SavedAirport): Long

    @Query("UPDATE airports SET favorite = :fav WHERE iata = :iata")
    suspend fun setFavorite(iata: String, fav: Boolean)

    @Query("UPDATE airports SET pdfPath = :path WHERE iata = :iata")
    suspend fun setPdf(iata: String, path: String?)

    @Query("UPDATE airports SET metar = :metar, taf = :taf, metarAt = :at WHERE iata = :iata")
    suspend fun setWeather(iata: String, metar: String?, taf: String?, at: Long)

    @Query("DELETE FROM airports WHERE iata = :iata")
    suspend fun delete(iata: String)
}

@Database(entities = [Flight::class, SavedAirport::class], version = 4, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): FlightDao
    abstract fun airportDao(): AirportDao
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE flights ADD COLUMN seatType TEXT")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE flights ADD COLUMN inboundJson TEXT")
        db.execSQL("ALTER TABLE flights ADD COLUMN inboundAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `airports` (`iata` TEXT NOT NULL, `favorite` INTEGER NOT NULL, " +
                "`addedAt` INTEGER NOT NULL, `pdfPath` TEXT, `metar` TEXT, `taf` TEXT, `metarAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`iata`))"
        )
    }
}

object Graph {
    lateinit var appContext: Context
    lateinit var db: AppDb
    lateinit var repo: Repository

    fun init(ctx: Context) {
        appContext = ctx.applicationContext
        Prefs.init(appContext)
        db = Room.databaseBuilder(appContext, AppDb::class.java, "flights.db")
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
            .fallbackToDestructiveMigration()
            .build()
        repo = Repository(db.dao(), db.airportDao())
    }
}
