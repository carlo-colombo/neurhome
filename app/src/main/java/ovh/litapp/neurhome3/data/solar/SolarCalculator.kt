package ovh.litapp.neurhome3.data.solar

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

data class SolarTimes(
    val sunrise: LocalTime? = null,
    val sunset: LocalTime? = null,
    val morningBlueHourStart: LocalTime? = null,
    val morningBlueHourEnd: LocalTime? = null,
    val eveningBlueHourStart: LocalTime? = null,
    val eveningBlueHourEnd: LocalTime? = null,
    val morningGoldenHourStart: LocalTime? = null,
    val morningGoldenHourEnd: LocalTime? = null,
    val eveningGoldenHourStart: LocalTime? = null,
    val eveningGoldenHourEnd: LocalTime? = null
)

object SolarCalculator {

    private fun julianDay(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = y / 100
        val b = 2 - a + (a / 4)
        return (365.25 * (y + 4716)).toInt() + (30.6001 * (m + 1)).toInt() + day + b - 1524.5
    }

    private fun julianToInstant(julianDate: Double): Instant {
        val millis = ((julianDate - 2440587.5) * 86400000.0).toLong()
        return Instant.ofEpochMilli(millis)
    }

    private fun hourAngle(latitudeDeg: Double, declinationRad: Double, elevationDeg: Double): Double? {
        val latRad = Math.toRadians(latitudeDeg)
        val elevRad = Math.toRadians(elevationDeg)
        val cosH = (sin(elevRad) - sin(latRad) * sin(declinationRad)) / (cos(latRad) * cos(declinationRad))
        if (cosH < -1.0 || cosH > 1.0) {
            return null
        }
        return Math.toDegrees(acos(cosH))
    }

    fun calculateSolarTimes(
        latitude: Double,
        longitude: Double,
        date: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): SolarTimes {
        val jd = julianDay(date.year, date.monthValue, date.dayOfMonth)
        val n = jd - 2451545.0 + 0.0008
        val jStar = n - (longitude / 360.0)

        val mRad = Math.toRadians((357.5291 + 0.98560028 * jStar) % 360.0)
        val cDeg = 1.9148 * sin(mRad) + 0.0200 * sin(2 * mRad) + 0.0003 * sin(3 * mRad)
        val lambdaRad = Math.toRadians((Math.toDegrees(mRad) + cDeg + 180.0 + 102.9372) % 360.0)

        val jTransit = 2451545.0 + jStar + 0.0053 * sin(mRad) - 0.0069 * sin(2 * lambdaRad)
        val declinationRad = asin(sin(lambdaRad) * sin(Math.toRadians(23.4397)))

        fun getTimeAtElevation(elevationDeg: Double): Pair<LocalTime?, LocalTime?> {
            val hDeg = hourAngle(latitude, declinationRad, elevationDeg) ?: return Pair(null, null)
            val hFraction = hDeg / 360.0
            val jRise = jTransit - hFraction
            val jSet = jTransit + hFraction

            val riseTime = ZonedDateTime.ofInstant(julianToInstant(jRise), zoneId).toLocalTime()
            val setTime = ZonedDateTime.ofInstant(julianToInstant(jSet), zoneId).toLocalTime()
            return Pair(riseTime, setTime)
        }

        val (sunrise, sunset) = getTimeAtElevation(-0.833)
        val (blueHourMorningStart, blueHourEveningEnd) = getTimeAtElevation(-6.0)
        val (blueHourMorningEnd, blueHourEveningStart) = getTimeAtElevation(-4.0)
        val (goldenHourMorningEnd, goldenHourEveningStart) = getTimeAtElevation(6.0)

        return SolarTimes(
            sunrise = sunrise,
            sunset = sunset,
            morningBlueHourStart = blueHourMorningStart,
            morningBlueHourEnd = blueHourMorningEnd,
            eveningBlueHourStart = blueHourEveningStart,
            eveningBlueHourEnd = blueHourEveningEnd,
            morningGoldenHourStart = blueHourMorningEnd,
            morningGoldenHourEnd = goldenHourMorningEnd,
            eveningGoldenHourStart = goldenHourEveningStart,
            eveningGoldenHourEnd = blueHourEveningStart
        )
    }
}
