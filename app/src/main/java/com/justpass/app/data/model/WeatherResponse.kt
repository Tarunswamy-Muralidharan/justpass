package com.justpass.app.data.model

import com.google.gson.annotations.SerializedName

/** Current-conditions block returned by Open-Meteo's `current=` query. */
data class OpenMeteoCurrentWeather(
    @SerializedName("time") val time: String,
    @SerializedName("weather_code") val weatherCode: Int,
    @SerializedName("is_day") val isDay: Int,
    /** Liquid + frozen precip in the most-recent 15-min interval, mm. */
    @SerializedName("precipitation") val precipitation: Double? = null,
    /** Total cloud cover percent (0..100). */
    @SerializedName("cloud_cover") val cloudCover: Int? = null
)

/**
 * Daily block — we only ask for sunrise/sunset so we can re-derive `is_day`
 * locally. Each list is one entry per day (`forecast_days=1` by default).
 */
data class OpenMeteoDaily(
    @SerializedName("time") val time: List<String> = emptyList(),
    @SerializedName("sunrise") val sunrise: List<String> = emptyList(),
    @SerializedName("sunset") val sunset: List<String> = emptyList()
)

data class OpenMeteoResponse(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("current") val current: OpenMeteoCurrentWeather,
    @SerializedName("daily") val daily: OpenMeteoDaily? = null
)
