package com.justpass.app.data.repository

import android.content.Context
import com.justpass.app.data.local.SecurePreferences
import com.justpass.app.data.model.OpenMeteoResponse
import com.justpass.app.data.network.NetworkModule
import com.justpass.app.ui.components.WeatherScene
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WeatherRepository {

    // Neelambur, Coimbatore, Tamil Nadu — geocoded centroid.
    private const val LATITUDE = 11.0498
    private const val LONGITUDE = 77.0625

    // Cache for 1 hour (milliseconds)
    private const val CACHE_DURATION_MS = 60 * 60 * 1000L

    suspend fun fetchAndStoreWeather(context: Context): WeatherScene? = withContext(Dispatchers.IO) {
        try {
            val response = NetworkModule.weatherApi.getCurrentWeather(LATITUDE, LONGITUDE)
            val scene = mapToWeatherScene(response)
            val prefs = SecurePreferences.getInstance(context)
            prefs.lastWeatherScene = scene.name
            prefs.lastWeatherFetchTime = System.currentTimeMillis()
            scene
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getCachedWeatherScene(context: Context): WeatherScene? {
        val prefs = SecurePreferences.getInstance(context)
        val lastFetch = prefs.lastWeatherFetchTime
        val cachedName = prefs.lastWeatherScene
        return if (cachedName.isNotEmpty() && (System.currentTimeMillis() - lastFetch) < CACHE_DURATION_MS) {
            WeatherScene.fromString(cachedName)
        } else null
    }

    fun shouldFetch(context: Context): Boolean {
        val prefs = SecurePreferences.getInstance(context)
        if (!prefs.autoWeatherEnabled) return false
        val lastFetch = prefs.lastWeatherFetchTime
        return (System.currentTimeMillis() - lastFetch) > CACHE_DURATION_MS
    }

    private fun mapToWeatherScene(response: OpenMeteoResponse): WeatherScene {
        val code = response.current.weatherCode
        val precipMm = response.current.precipitation ?: 0.0
        val cloudPct = response.current.cloudCover ?: 0

        // Re-derive is_day locally from sunrise/sunset rather than trusting
        // the API's coarse 15-min `is_day` flag (it lags real civil sunset).
        val isDay = resolveIsDay(response)

        // Start with the WMO-code mapping.
        var scene: WeatherScene = when (code) {
            0 -> if (isDay) WeatherScene.CLEAR_DAY else WeatherScene.CLEAR_NIGHT
            1 -> if (isDay) WeatherScene.PARTLY_DAY else WeatherScene.PARTLY_NIGHT
            2 -> WeatherScene.CLOUDY
            3 -> if (isDay) WeatherScene.OVERCAST else WeatherScene.OVERCAST_NIGHT
            45, 48 -> WeatherScene.FOG
            51, 53, 55, 56, 57, 61, 80 -> WeatherScene.RAIN
            63, 65, 66, 67, 81, 82 -> WeatherScene.HEAVY_RAIN
            71, 73, 75, 77, 85, 86 -> WeatherScene.SNOW
            95, 96, 99 -> WeatherScene.THUNDERSTORM
            else -> if (isDay) WeatherScene.CLEAR_DAY else WeatherScene.CLEAR_NIGHT
        }

        // ── Precip / cloud sanity overrides ──
        // The WMO code is computed on a coarse interval, so a clear/partly/
        // cloudy/overcast bucket can lag during a rain ramp-up. If we see
        // real precipitation in the last interval, promote the scene.
        if (scene in setOf(
                WeatherScene.CLEAR_DAY, WeatherScene.CLEAR_NIGHT,
                WeatherScene.PARTLY_DAY, WeatherScene.PARTLY_NIGHT,
                WeatherScene.CLOUDY,
                WeatherScene.OVERCAST, WeatherScene.OVERCAST_NIGHT,
            )
        ) {
            scene = when {
                precipMm >= 1.0 -> WeatherScene.HEAVY_RAIN
                precipMm >= 0.1 -> WeatherScene.RAIN
                else -> scene
            }
        }

        // Cloud-cover sanity: code 1 (mainly clear) with 80%+ cloud reads
        // wrong on a phone screen — bump to CLOUDY / OVERCAST as observed.
        // Respect is_day so the overcast variant matches the time of day.
        if (code == 1 && cloudPct >= 95) {
            scene = if (isDay) WeatherScene.OVERCAST else WeatherScene.OVERCAST_NIGHT
        } else if (code == 1 && cloudPct >= 80) {
            scene = WeatherScene.CLOUDY
        }

        return scene
    }

    /**
     * Decide whether it's day right now. Prefer the API's sunrise/sunset
     * pair when present (more accurate than the 15-min `is_day` flag),
     * fall back to `is_day` otherwise.
     */
    private fun resolveIsDay(response: OpenMeteoResponse): Boolean {
        val daily = response.daily
        val sunriseStr = daily?.sunrise?.firstOrNull()
        val sunsetStr = daily?.sunset?.firstOrNull()
        if (sunriseStr.isNullOrBlank() || sunsetStr.isNullOrBlank()) {
            return response.current.isDay == 1
        }
        // Open-Meteo returns local times in ISO-8601 without zone (timezone=auto
        // ensures they're in the location's local time). The current.time field
        // is in the same zone, so a lexicographic compare works.
        val now = response.current.time
        return now >= sunriseStr && now < sunsetStr
    }
}
