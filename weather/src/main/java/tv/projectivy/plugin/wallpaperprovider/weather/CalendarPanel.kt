package tv.projectivy.plugin.wallpaperprovider.weather

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale

/**
 * Month grid (Mon-first, Russian) with Orthodox feasts, drawn on the right
 * side of the frame under the clock. Fits itself into the space between
 * [top] and [bottom], shrinking if the safe area is short.
 */
object CalendarPanel {

    private val RU = Locale("ru")
    private val ACCENT = Color.rgb(255, 196, 92)
    private val FEAST = Color.rgb(255, 138, 128)
    private val WEEKDAYS = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

    fun draw(canvas: Canvas, right: Float, top: Float, bottom: Float, light: Typeface, medium: Typeface) {
        val now = Calendar.getInstance()
        val year = now.get(Calendar.YEAR)
        val month = now.get(Calendar.MONTH) + 1
        val today = now.get(Calendar.DAY_OF_MONTH)

        val first = GregorianCalendar(year, month - 1, 1)
        val daysInMonth = first.getActualMaximum(Calendar.DAY_OF_MONTH)
        val lead = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7  // Monday = 0
        val weeks = (lead + daysInMonth + 6) / 7

        // Natural size, then shrink to fit.
        val cellW = 76f
        val cellH = 58f
        val headerH = 70f
        val weekdayH = 50f
        val feastH = 110f
        val pad = 34f
        val naturalW = cellW * 7 + pad * 2
        val naturalH = pad + headerH + weekdayH + cellH * weeks + feastH + pad
        val k = minOf(1f, (bottom - top) / naturalH)
        if (k < 0.55f) return  // no sensible room, skip rather than draw unreadable

        val w = naturalW * k
        val left = right - w
        val h = naturalH * k

        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 0, 0, 0) }
        canvas.drawRoundRect(RectF(left, top, right, top + h), 28f * k, 28f * k, bg)

        fun text(size: Float, face: Typeface, color: Int = Color.WHITE, alpha: Int = 255) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size * k
                typeface = face
                this.color = color
                this.alpha = alpha
                setShadowLayer(8f, 0f, 2f, Color.argb(120, 0, 0, 0))
            }

        val x0 = left + pad * k
        var y = top + pad * k

        // Header: "Ноябрь 2026"
        val monthName = SimpleDateFormat("LLLL", RU).format(now.time)
            .replaceFirstChar { it.titlecase(RU) }
        canvas.drawText("$monthName $year", x0, y + 46f * k, text(46f, medium))
        y += headerH * k

        // Weekday row
        val wd = text(28f, medium, alpha = 170)
        WEEKDAYS.forEachIndexed { i, s ->
            val p = if (i >= 5) text(28f, medium, FEAST, 200) else wd
            val cx = x0 + (i + 0.5f) * cellW * k
            canvas.drawText(s, cx - p.measureText(s) / 2f, y + 30f * k, p)
        }
        y += weekdayH * k

        val feastDays = OrthodoxCalendar.feastsFor(year).filter { it.month == month }.associateBy { it.day }

        for (d in 1..daysInMonth) {
            val idx = lead + d - 1
            val col = idx % 7
            val row = idx / 7
            val cx = x0 + (col + 0.5f) * cellW * k
            val cy = y + (row + 0.5f) * cellH * k
            val label = d.toString()
            val feast = feastDays[d]

            if (d == today) {
                canvas.drawCircle(cx, cy, 25f * k, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT })
            } else if (feast?.major == true) {
                canvas.drawCircle(cx, cy, 25f * k, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = 2.5f * k; color = FEAST
                })
            }

            val p = when {
                d == today -> text(32f, medium, Color.rgb(30, 30, 30)).apply { clearShadowLayer() }
                feast != null -> text(32f, medium, FEAST)
                col >= 5 -> text(32f, light, FEAST, 210)
                else -> text(32f, light)
            }
            canvas.drawText(label, cx - p.measureText(label) / 2f, cy + 11f * k, p)
        }
        y += cellH * weeks * k + 18f * k

        // Feast line(s)
        val todays = OrthodoxCalendar.feastsOn(year, month, today)
        val small = text(26f, light, alpha = 190)
        val big = text(32f, medium, FEAST)
        if (todays.isNotEmpty()) {
            canvas.drawText("Сегодня", x0, y + 26f * k, small)
            canvas.drawText(fit(todays.joinToString(" · ") { it.name }, big, w - pad * 2 * k), x0, y + 70f * k, big)
        } else {
            OrthodoxCalendar.next(now)?.let { (f, inDays) ->
                val date = SimpleDateFormat("d MMMM", RU).format(
                    GregorianCalendar(if (f.month < month) year + 1 else year, f.month - 1, f.day).time
                )
                canvas.drawText(
                    "Через $inDays ${OrthodoxCalendar.daysWord(inDays)} · $date", x0, y + 26f * k, small
                )
                canvas.drawText(fit(f.name, big, w - pad * 2 * k), x0, y + 70f * k, big)
            }
        }
    }

    private fun fit(s: String, p: Paint, max: Float): String {
        if (p.measureText(s) <= max) return s
        var t = s
        while (t.length > 1 && p.measureText("$t…") > max) t = t.dropLast(1)
        return "$t…"
    }
}
