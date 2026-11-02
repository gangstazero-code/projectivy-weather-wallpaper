package tv.projectivy.plugin.wallpaperprovider.weather

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Fallback weather source: MET Norway Locationforecast (no key, reachable
 * from networks where Open-Meteo is blocked or overloaded).
 *
 * Maps the response onto OpenMeteoClient.Conditions so the renderer does not
 * care where the data came from. Fields MET compact does not provide (UV,
 * visibility, sunrise/sunset, nowcast) are left empty, which the renderer
 * already tolerates.
 */
object MetNoClient {

    private const val TAG = "MetNoClient"
    private const val ENDPOINT = "https://api.met.no/weatherapi/locationforecast/2.0/compact"
    private const val UA = "tv-weather/1.0 github.com/gangstazero-code/projectivy-weather-wallpaper"
    private val RU = Locale("ru")
    private val DAYS = listOf("Вс", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб")

    fun fetch(lat: Double, lon: Double, metric: Boolean): OpenMeteoClient.Conditions? {
        val url = "$ENDPOINT?lat=%.4f&lon=%.4f".format(Locale.US, lat, lon)
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode !in 200..299) {
                Log.w(TAG, "HTTP ${conn.responseCode}")
                return null
            }
            parse(JSONObject(conn.inputStream.bufferedReader().use { it.readText() }), metric)
        } catch (e: Exception) {
            Log.w(TAG, "Fetch failed: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    private class Point(
        val utcMillis: Long, val temp: Double, val wind: Double, val windDir: Int,
        val humidity: Int, val pressure: Double, val symbol: String?
    )

    private fun parse(root: JSONObject, metric: Boolean): OpenMeteoClient.Conditions? {
        val series = root.getJSONObject("properties").getJSONArray("timeseries")
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val points = (0 until series.length()).mapNotNull { i ->
            val o = series.getJSONObject(i)
            val data = o.getJSONObject("data")
            val d = data.getJSONObject("instant").getJSONObject("details")
            val symbol = data.optJSONObject("next_1_hours")?.optJSONObject("summary")?.optString("symbol_code")
                ?: data.optJSONObject("next_6_hours")?.optJSONObject("summary")?.optString("symbol_code")
            val t = iso.parse(o.getString("time"))?.time ?: return@mapNotNull null
            Point(
                t, d.optDouble("air_temperature", Double.NaN), d.optDouble("wind_speed", 0.0),
                d.optDouble("wind_from_direction", -1.0).toInt(), d.optDouble("relative_humidity", -1.0).toInt(),
                d.optDouble("air_pressure_at_sea_level", Double.NaN), symbol
            )
        }
        if (points.isEmpty()) return null

        val tz = TimeZone.getDefault()
        val now = System.currentTimeMillis()
        val offset = tz.getOffset(now)
        val cur = points.lastOrNull { it.utcMillis <= now } ?: points.first()

        fun localDay(ms: Long) = (ms + tz.getOffset(ms)) / 86_400_000L
        fun localHour(ms: Long) = (((ms + tz.getOffset(ms)) % 86_400_000L) / 3_600_000L).toInt()
        fun temp(c: Double) = if (metric) c else c * 9 / 5 + 32
        fun wind(ms: Double) = if (metric) ms * 3.6 else ms * 2.23694

        val byDay = points.groupBy { localDay(it.utcMillis) }.toSortedMap()
        val today = byDay[localDay(now)] ?: byDay.values.first()

        val hourly = points.filter { it.utcMillis > now }.take(6).map {
            OpenMeteoClient.HourEntry(
                label = "%d:00".format(localHour(it.utcMillis)),
                temperature = temp(it.temp),
                weatherCode = code(it.symbol),
                precipChance = 0,
                isDay = isDay(it.symbol)
            )
        }
        val daily = byDay.entries.drop(1).take(5).map { (day, pts) ->
            val noon = pts.minByOrNull { kotlin.math.abs(localHour(it.utcMillis) - 13) } ?: pts.first()
            val cal = java.util.Calendar.getInstance(tz).apply { timeInMillis = day * 86_400_000L + 12 * 3_600_000L - offset }
            OpenMeteoClient.DayEntry(
                label = DAYS[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1],
                high = temp(pts.maxOf { it.temp }),
                low = temp(pts.minOf { it.temp }),
                weatherCode = code(noon.symbol)
            )
        }

        return OpenMeteoClient.Conditions(
            temperature = temp(cur.temp),
            apparentTemperature = temp(cur.temp),
            weatherCode = code(cur.symbol),
            isDay = isDay(cur.symbol),
            windSpeed = wind(cur.wind),
            windDirection = cur.windDir,
            high = temp(today.maxOf { it.temp }),
            low = temp(today.minOf { it.temp }),
            unitSuffix = if (metric) "°C" else "°F",
            humidity = cur.humidity,
            pressure = cur.pressure,
            visibility = Double.NaN,
            dewPoint = Double.NaN,
            uvIndexMax = Double.NaN,
            sunrise = "",
            sunset = "",
            metric = metric,
            hourly = hourly,
            daily = daily,
            nowcast = null,
            yesterdayHigh = null,
            utcOffsetSeconds = offset / 1000
        )
    }

    private fun isDay(symbol: String?) = symbol?.endsWith("_night") != true &&
        symbol?.endsWith("_polartwilight") != true

    /** MET symbol_code -> WMO weather code used by the renderer. */
    private fun code(symbol: String?): Int {
        val s = symbol?.substringBefore('_') ?: return 3
        return when {
            s == "clearsky" -> 0
            s == "fair" -> 1
            s == "partlycloudy" -> 2
            s == "cloudy" -> 3
            s == "fog" -> 45
            "thunder" in s -> 95
            "sleet" in s -> 66
            "snowshowers" in s -> 85
            s.startsWith("heavysnow") -> 75
            s.startsWith("lightsnow") -> 71
            "snow" in s -> 73
            "rainshowers" in s -> 80
            s.startsWith("heavyrain") -> 65
            s.startsWith("lightrain") -> 61
            "rain" in s -> 63
            else -> 3
        }
    }
}
