package app.flightcompanion.data

import android.content.Context
import android.content.SharedPreferences
import java.time.YearMonth

object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    }

    var apiKey: String
        get() = sp.getString("api_key", "") ?: ""
        set(v) {
            sp.edit().putString("api_key", v.trim()).apply()
        }

    var useMiles: Boolean
        get() = sp.getBoolean("use_miles", false)
        set(v) {
            sp.edit().putBoolean("use_miles", v).apply()
        }

    var openskyId: String
        get() = sp.getString("os_id", "") ?: ""
        set(v) {
            sp.edit().putString("os_id", v.trim()).apply()
        }

    var openskySecret: String
        get() = sp.getString("os_secret", "") ?: ""
        set(v) {
            sp.edit().putString("os_secret", v.trim()).apply()
        }

    /** "sat" (satellite) oppure "map" (topografica). */
    var mapStyle: String
        get() = sp.getString("map_style", "sat") ?: "sat"
        set(v) {
            sp.edit().putString("map_style", v).apply()
        }

    var airportsSeeded: Boolean
        get() = sp.getBoolean("airports_seeded", false)
        set(v) {
            sp.edit().putBoolean("airports_seeded", v).apply()
        }

    private fun monthKey() = "calls_" + YearMonth.now().toString()

    fun callsThisMonth(): Int = sp.getInt(monthKey(), 0)

    @Synchronized
    fun countCall() {
        sp.edit().putInt(monthKey(), callsThisMonth() + 1).apply()
    }
}
