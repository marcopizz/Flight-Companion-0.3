package app.flightcompanion.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
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

    @Insert
    suspend fun insert(f: Flight): Long

    @Update
    suspend fun update(f: Flight)

    @Delete
    suspend fun delete(f: Flight)
}

@Database(entities = [Flight::class], version = 3, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): FlightDao
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE flights ADD COLUMN seatType TEXT")
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
            .addMigrations(MIGRATION_2_3)
            .fallbackToDestructiveMigration()
            .build()
        repo = Repository(db.dao())
    }
}
