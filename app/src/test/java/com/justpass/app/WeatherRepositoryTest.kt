package com.justpass.app

import com.justpass.app.data.model.OpenMeteoCurrentWeather
import com.justpass.app.data.model.OpenMeteoDaily
import com.justpass.app.data.model.OpenMeteoResponse
import com.justpass.app.data.repository.WeatherRepository
import com.justpass.app.ui.components.WeatherScene
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Method

class WeatherRepositoryTest {

    private fun invokeMapToWeatherScene(response: OpenMeteoResponse): WeatherScene {
        val method: Method = WeatherRepository::class.java.getDeclaredMethod("mapToWeatherScene", OpenMeteoResponse::class.java)
        method.isAccessible = true
        return method.invoke(WeatherRepository, response) as WeatherScene
    }

    private fun invokeResolveIsDay(response: OpenMeteoResponse): Boolean {
        val method: Method = WeatherRepository::class.java.getDeclaredMethod("resolveIsDay", OpenMeteoResponse::class.java)
        method.isAccessible = true
        return method.invoke(WeatherRepository, response) as Boolean
    }

    private fun makeResponse(
        code: Int,
        precip: Double? = 0.0,
        cloud: Int? = 0,
        isDay: Int = 1,
        sunrise: String? = null,
        sunset: String? = null,
        time: String = "2026-06-01T12:00"
    ): OpenMeteoResponse {
        return OpenMeteoResponse(
            latitude = 11.0498,
            longitude = 77.0625,
            current = OpenMeteoCurrentWeather(
                time = time,
                weatherCode = code,
                isDay = isDay,
                precipitation = precip,
                cloudCover = cloud
            ),
            daily = if (sunrise != null && sunset != null) {
                OpenMeteoDaily(
                    time = listOf("2026-06-01"),
                    sunrise = listOf(sunrise),
                    sunset = listOf(sunset)
                )
            } else null
        )
    }

    @Test
    fun `mapToWeatherScene returns CLEAR_DAY for code 0 during day`() {
        val scene = invokeMapToWeatherScene(makeResponse(0, isDay = 1))
        assertEquals(WeatherScene.CLEAR_DAY, scene)
    }

    @Test
    fun `mapToWeatherScene returns CLEAR_NIGHT for code 0 at night`() {
        val scene = invokeMapToWeatherScene(makeResponse(0, isDay = 0))
        assertEquals(WeatherScene.CLEAR_NIGHT, scene)
    }

    @Test
    fun `mapToWeatherScene returns RAIN for precipitation codes`() {
        assertEquals(WeatherScene.RAIN, invokeMapToWeatherScene(makeResponse(61)))
        assertEquals(WeatherScene.RAIN, invokeMapToWeatherScene(makeResponse(80)))
    }

    @Test
    fun `mapToWeatherScene returns HEAVY_RAIN for heavy precipitation codes`() {
        assertEquals(WeatherScene.HEAVY_RAIN, invokeMapToWeatherScene(makeResponse(63)))
        assertEquals(WeatherScene.HEAVY_RAIN, invokeMapToWeatherScene(makeResponse(65)))
        assertEquals(WeatherScene.HEAVY_RAIN, invokeMapToWeatherScene(makeResponse(82)))
    }

    @Test
    fun `mapToWeatherScene returns SNOW for snow codes`() {
        assertEquals(WeatherScene.SNOW, invokeMapToWeatherScene(makeResponse(71)))
        assertEquals(WeatherScene.SNOW, invokeMapToWeatherScene(makeResponse(85)))
    }

    @Test
    fun `mapToWeatherScene returns THUNDERSTORM for storm codes`() {
        assertEquals(WeatherScene.THUNDERSTORM, invokeMapToWeatherScene(makeResponse(95)))
        assertEquals(WeatherScene.THUNDERSTORM, invokeMapToWeatherScene(makeResponse(99)))
    }

    @Test
    fun `mapToWeatherScene returns FOG for fog codes`() {
        assertEquals(WeatherScene.FOG, invokeMapToWeatherScene(makeResponse(45)))
        assertEquals(WeatherScene.FOG, invokeMapToWeatherScene(makeResponse(48)))
    }

    @Test
    fun `mapToWeatherScene promotes to RAIN when precip is low but present`() {
        val scene = invokeMapToWeatherScene(makeResponse(1, precip = 0.5, isDay = 1))
        assertEquals(WeatherScene.RAIN, scene)
    }

    @Test
    fun `mapToWeatherScene promotes to HEAVY_RAIN when precip is high`() {
        val scene = invokeMapToWeatherScene(makeResponse(2, precip = 2.0))
        assertEquals(WeatherScene.HEAVY_RAIN, scene)
    }

    @Test
    fun `mapToWeatherScene bumps partly cloudy to overcast at 95 percent cloud`() {
        val scene = invokeMapToWeatherScene(makeResponse(1, cloud = 95, isDay = 1))
        assertEquals(WeatherScene.OVERCAST, scene)
    }

    @Test
    fun `mapToWeatherScene bumps partly cloudy to cloudy at 80 percent cloud`() {
        val scene = invokeMapToWeatherScene(makeResponse(1, cloud = 85, isDay = 1))
        assertEquals(WeatherScene.CLOUDY, scene)
    }

    @Test
    fun `mapToWeatherScene does not override rain with cloud check`() {
        val scene = invokeMapToWeatherScene(makeResponse(63, cloud = 95))
        assertEquals(WeatherScene.HEAVY_RAIN, scene)
    }

    @Test
    fun `resolveIsDay falls back to current isDay when sunrise sunset missing`() {
        val res = makeResponse(0, isDay = 1, sunrise = null, sunset = null)
        assertTrue(invokeResolveIsDay(res))

        val resNight = makeResponse(0, isDay = 0, sunrise = null, sunset = null)
        assertFalse(invokeResolveIsDay(resNight))
    }

    @Test
    fun `resolveIsDay uses sunrise sunset when available`() {
        val resDay = makeResponse(0, time = "2026-06-01T12:00", sunrise = "2026-06-01T06:00", sunset = "2026-06-01T18:00")
        assertTrue(invokeResolveIsDay(resDay))

        val resNight = makeResponse(0, time = "2026-06-01T20:00", sunrise = "2026-06-01T06:00", sunset = "2026-06-01T18:00")
        assertFalse(invokeResolveIsDay(resNight))

        val resDawn = makeResponse(0, time = "2026-06-01T05:00", sunrise = "2026-06-01T06:00", sunset = "2026-06-01T18:00")
        assertFalse(invokeResolveIsDay(resDawn))
    }
}
