package tv.projectivy.plugin.wallpaperprovider.weather

import java.util.Calendar
import java.util.GregorianCalendar

/**
 * Russian Orthodox feasts in civil (Gregorian) dates.
 *
 * Fixed feasts are already shifted from the Julian calendar (+13 days, valid
 * for 1900-2099). Moving feasts are counted from Pascha, which is computed
 * with the Julian computus (Meeus) and then converted to Gregorian.
 */
object OrthodoxCalendar {

    data class Feast(val month: Int, val day: Int, val name: String, val major: Boolean)

    private val FIXED = listOf(
        Triple(1, 7, "Рождество Христово") to true,
        Triple(1, 14, "Обрезание Господне") to false,
        Triple(1, 19, "Крещение Господне") to true,
        Triple(2, 15, "Сретение Господне") to true,
        Triple(4, 7, "Благовещение") to true,
        Triple(7, 7, "Рождество Иоанна Предтечи") to false,
        Triple(7, 12, "Петра и Павла") to false,
        Triple(8, 14, "Медовый Спас") to false,
        Triple(8, 19, "Преображение (Яблочный Спас)") to true,
        Triple(8, 28, "Успение Богородицы") to true,
        Triple(8, 29, "Ореховый Спас") to false,
        Triple(9, 11, "Усекновение главы Иоанна") to false,
        Triple(9, 21, "Рождество Богородицы") to true,
        Triple(9, 27, "Воздвижение Креста") to true,
        Triple(10, 14, "Покров Богородицы") to false,
        Triple(12, 4, "Введение во храм") to true
    )

    /** Offset in days from Pascha. */
    private val MOVING = listOf(
        -55 to ("Масленица" to false),
        -49 to ("Прощёное воскресенье" to false),
        -48 to ("Начало Великого поста" to false),
        -7 to ("Вербное воскресенье" to true),
        0 to ("Пасха" to true),
        9 to ("Радоница" to false),
        39 to ("Вознесение Господне" to true),
        49 to ("Троица" to true),
        50 to ("Духов день" to false)
    )

    /** Pascha for a given year, Gregorian (month 1-12, day). */
    fun pascha(year: Int): Pair<Int, Int> {
        val a = year % 4
        val b = year % 7
        val c = year % 19
        val d = (19 * c + 15) % 30
        val e = (2 * a + 4 * b - d + 34) % 7
        val month = (d + e + 114) / 31
        val day = (d + e + 114) % 31 + 1
        val cal = GregorianCalendar(year, month - 1, day)
        cal.add(Calendar.DAY_OF_MONTH, 13)
        return (cal.get(Calendar.MONTH) + 1) to cal.get(Calendar.DAY_OF_MONTH)
    }

    fun feastsFor(year: Int): List<Feast> {
        val list = FIXED.map { (t, major) -> Feast(t.first, t.second, t.third, major) }.toMutableList()
        val (pm, pd) = pascha(year)
        for ((offset, info) in MOVING) {
            val cal = GregorianCalendar(year, pm - 1, pd)
            cal.add(Calendar.DAY_OF_MONTH, offset)
            if (cal.get(Calendar.YEAR) == year) {
                list += Feast(cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH), info.first, info.second)
            }
        }
        return list.sortedWith(compareBy({ it.month }, { it.day }))
    }

    fun feastsOn(year: Int, month: Int, day: Int): List<Feast> =
        feastsFor(year).filter { it.month == month && it.day == day }

    /** Next feast strictly after today (today's is returned separately). */
    fun next(today: Calendar): Pair<Feast, Int>? {
        val start = GregorianCalendar(
            today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH)
        )
        val probe = start.clone() as GregorianCalendar
        for (i in 1..400) {
            probe.add(Calendar.DAY_OF_MONTH, 1)
            val hit = feastsOn(
                probe.get(Calendar.YEAR), probe.get(Calendar.MONTH) + 1, probe.get(Calendar.DAY_OF_MONTH)
            ).maxByOrNull { if (it.major) 1 else 0 }
            if (hit != null) return hit to i
        }
        return null
    }

    fun daysWord(n: Int): String {
        val m10 = n % 10
        val m100 = n % 100
        return when {
            m100 in 11..14 -> "дней"
            m10 == 1 -> "день"
            m10 in 2..4 -> "дня"
            else -> "дней"
        }
    }
}
