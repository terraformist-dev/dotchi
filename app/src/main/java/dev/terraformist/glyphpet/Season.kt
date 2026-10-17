package dev.terraformist.glyphpet

import android.icu.util.ChineseCalendar
import android.icu.util.TimeZone
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Special days, when the pet dresses up. */
enum class Season(val greeting: String) {
    BIRTHDAY("HAPPY BIRTHDAY!"),           // 10 June: its birthday
    CHINESE_NEW_YEAR("HAPPY LUNAR NEW YEAR!"),
    HALLOWEEN("HAPPY HALLOWEEN!"),         // 31 October
    CHRISTMAS("MERRY CHRISTMAS!"),         // 24-26 December
    NEW_YEAR("HAPPY NEW YEAR!");           // 31 December - 1 January

    companion object {
        /** How many days the Lunar New Year celebration lasts here (the day itself and the next two). */
        private const val LUNAR_DAYS = 3L

        private val lunarCache = HashMap<Int, LocalDate>()

        fun on(time: Long, zone: ZoneId = ZoneId.systemDefault()): Season? {
            val date = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
            val m = date.monthValue
            val d = date.dayOfMonth
            return when {
                m == 6 && d == 10 -> BIRTHDAY
                isLunarNewYear(date) -> CHINESE_NEW_YEAR
                m == 10 && d == 31 -> HALLOWEEN
                m == 12 && d in 24..26 -> CHRISTMAS
                (m == 12 && d == 31) || (m == 1 && d == 1) -> NEW_YEAR
                else -> null
            }
        }

        private fun isLunarNewYear(date: LocalDate): Boolean {
            val start = lunarNewYear(date.year) ?: return false
            return !date.isBefore(start) && date.isBefore(start.plusDays(LUNAR_DAYS))
        }

        /**
         * The first day of the first month in the Chinese calendar, worked out by the phone's own
         * calendar maths for whichever year it is (it always falls between 21 Jan and 20 Feb).
         */
        fun lunarNewYear(year: Int): LocalDate? = synchronized(lunarCache) {
            lunarCache[year]?.let { return it }
            val china = TimeZone.getTimeZone("Asia/Shanghai")
            val calendar = ChineseCalendar(china)
            var day = LocalDate.of(year, 1, 20)
            val last = LocalDate.of(year, 2, 21)
            while (!day.isAfter(last)) {
                val noon = day.atTime(12, 0).atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()
                calendar.timeInMillis = noon
                val firstMonth = calendar.get(ChineseCalendar.MONTH) == 0 &&
                    calendar.get(ChineseCalendar.IS_LEAP_MONTH) == 0
                if (firstMonth && calendar.get(ChineseCalendar.DAY_OF_MONTH) == 1) {
                    lunarCache[year] = day
                    return day
                }
                day = day.plusDays(1)
            }
            null
        }
    }
}
