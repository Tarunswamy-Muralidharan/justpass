package com.justpass.app.data.network

import com.justpass.app.data.model.OpenMeteoResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApiService {
    /**
     * Open-Meteo forecast endpoint, scoped to a single point.
     *
     * - `current` fields cover what the theme picker needs to disambiguate
     *   WMO code edge cases (e.g. overcast with active drizzle still flagged
     *   as code 3 → we promote to RAIN via precipitation).
     * - `daily=sunrise,sunset` lets us re-check is_day locally — the API's
     *   `is_day` flag is coarse (15 min interval) and frequently disagrees
     *   with civil sunset at dusk.
     * - `models=ecmwf_ifs025` pins the request to ECMWF IFS 0.25° — the
     *   globally best-scoring model for India/Coimbatore conditions
     *   (Open-Meteo defaults to a blend that includes GFS).
     */
    @GET("v1/forecast")
    suspend fun getCurrentWeather(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "weather_code,is_day,precipitation,cloud_cover",
        @Query("daily") daily: String = "sunrise,sunset",
        @Query("models") models: String = "ecmwf_ifs025",
        @Query("timezone") timezone: String = "auto"
    ): OpenMeteoResponse
}
