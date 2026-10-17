package dev.terraformist.glyphpet

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt

enum class Sky { CLEAR, CLOUDY, FOG, RAIN, SNOW, STORM }

/** The weather where the phone is, as last fetched. */
data class WeatherNow(val sky: Sky, val tempC: Int, val isDay: Boolean, val fetchedAt: Long) {
    val label: String
        get() = when (sky) {
            Sky.CLEAR -> if (isDay) "SUNNY" else "CLEAR"
            Sky.CLOUDY -> "CLOUDY"
            Sky.FOG -> "FOGGY"
            Sky.RAIN -> "RAIN"
            Sky.SNOW -> "SNOW"
            Sky.STORM -> "STORM"
        }
}

/**
 * Local weather from Open-Meteo (free, no account). Uses the phone's approximate location,
 * rounded to about a kilometre, and only if the location permission was granted.
 * Fetched at most every [REFRESH_MS]; shown for up to [SHOW_FOR_MS] after that.
 */
object Weather {
    private const val TAG = "GlyphPet"
    private const val PREFS = "weather"
    private const val REFRESH_MS = 45 * 60_000L
    private const val SHOW_FOR_MS = 3 * 60 * 60_000L

    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var fetching = false

    fun canUse(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** The last weather fetched, if it's recent enough to show. */
    fun current(context: Context, now: Long = System.currentTimeMillis()): WeatherNow? {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val at = p.getLong("at", 0L)
        if (at == 0L || now - at > SHOW_FOR_MS) return null
        val sky = Sky.entries.firstOrNull { it.name == p.getString("sky", null) } ?: return null
        return WeatherNow(sky, p.getInt("temp", 0), p.getBoolean("day", true), at)
    }

    /**
     * Fetch new weather if what we have is old. [done] is always called on the main thread,
     * within about 8 seconds, whether or not anything was fetched.
     */
    fun refreshIfStale(context: Context, done: () -> Unit = {}) {
        val app = context.applicationContext
        val main = Handler(Looper.getMainLooper())
        val now = System.currentTimeMillis()
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!canUse(app) || fetching || now - p.getLong("at", 0L) < REFRESH_MS) {
            main.post { done() }
            return
        }
        fetching = true
        var called = false
        val finish = {
            if (!called) {
                called = true
                done()
            }
        }
        main.postDelayed({ finish() }, 8_000L)

        findLocation(app) { location ->
            if (location == null) {
                fetching = false
                main.post { finish() }
                return@findLocation
            }
            worker.execute {
                try {
                    fetch(app, location)
                } catch (e: Exception) {
                    Log.w(TAG, "weather fetch failed", e)
                } finally {
                    fetching = false
                    main.post {
                        finish()
                        PetWidgetProvider.refreshAll(app)
                    }
                }
            }
        }
    }

    private fun findLocation(context: Context, done: (Location?) -> Unit) {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (manager == null || !canUse(context)) {
            done(null)
            return
        }
        try {
            val providers = manager.getProviders(true)
            val known = providers.mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull { it.time }
            if (known != null && System.currentTimeMillis() - known.time < 6 * 60 * 60_000L) {
                done(known)
                return
            }
            val provider = when {
                LocationManager.FUSED_PROVIDER in providers -> LocationManager.FUSED_PROVIDER
                LocationManager.NETWORK_PROVIDER in providers -> LocationManager.NETWORK_PROVIDER
                else -> null
            }
            if (provider == null) {
                done(known)
                return
            }
            manager.getCurrentLocation(provider, null, worker) { fresh -> done(fresh ?: known) }
        } catch (e: SecurityException) {
            done(null)
        }
    }

    private fun fetch(context: Context, location: Location) {
        val url = URL(
            String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f&current=temperature_2m,weather_code,is_day",
                location.latitude,
                location.longitude,
            )
        )
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        try {
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val current = JSONObject(body).getJSONObject("current")
            val code = current.getInt("weather_code")
            val temp = current.getDouble("temperature_2m").roundToInt()
            val day = current.optInt("is_day", 1) == 1
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("sky", skyFor(code).name)
                .putInt("temp", temp)
                .putBoolean("day", day)
                .putLong("at", System.currentTimeMillis())
                .apply()
        } finally {
            connection.disconnect()
        }
    }

    /** WMO weather codes, as used by Open-Meteo. */
    private fun skyFor(code: Int): Sky = when (code) {
        0, 1 -> Sky.CLEAR
        2, 3 -> Sky.CLOUDY
        45, 48 -> Sky.FOG
        in 51..67, in 80..82 -> Sky.RAIN
        in 71..77, 85, 86 -> Sky.SNOW
        in 95..99 -> Sky.STORM
        else -> Sky.CLOUDY
    }
}
