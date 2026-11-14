package io.github.lazy.dsh.core.greeting

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** The festivals the home page knows how to greet. */
enum class HolidayId {
    NEW_YEAR_EVE, NEW_YEAR, SPRING_FESTIVAL, QINGMING, LABOR_DAY,
    DRAGON_BOAT, MID_AUTUMN, NATIONAL_DAY,
}

/** Which pool the headline comes from. */
sealed interface Headline {
    data class Holiday(val id: HolidayId) : Headline
    data object FridayEvening : Headline

    /** Saturday and Sunday share the weekend copy but keep their own pool, so Saturday never says "周日". */
    data class Weekend(val day: DayOfWeek) : Headline
    data class Weekday(val day: DayOfWeek) : Headline
}

/** Which pool the line under the headline comes from. */
enum class Subline { EARLY_BIRD, NIGHT_OWL, CARE }

/**
 * What the home page should say right now: the date decides the headline (festival > Friday
 * evening > weekend > weekday) and the clock decides the line under it (early bird / night owl /
 * ordinary care), so "周五晚上" and "夜猫子" can coexist without one hiding the other.
 *
 * Deliberately pure: no Android, no `now()` inside, so every boundary is testable. The caller
 * refreshes it as the clock moves.
 */
data class HeroMoment(val headline: Headline, val subline: Subline) {
    companion object {
        private val EARLY_BIRD_START = LocalTime.of(5, 0)
        private val EARLY_BIRD_END = LocalTime.of(7, 30)
        private val NIGHT_OWL_START = LocalTime.of(23, 0)
        private val NIGHT_OWL_END = LocalTime.of(5, 0)
        private val FRIDAY_EVENING_START = LocalTime.of(18, 0)
        private val FRIDAY_EVENING_END = LocalTime.of(23, 0)

        fun of(now: LocalDateTime): HeroMoment = HeroMoment(headline(now), subline(now.toLocalTime()))

        fun headline(now: LocalDateTime): Headline {
            holidayOn(now.toLocalDate())?.let { return Headline.Holiday(it) }
            val time = now.toLocalTime()
            val fridayEvening = now.dayOfWeek == DayOfWeek.FRIDAY &&
                time >= FRIDAY_EVENING_START && time < FRIDAY_EVENING_END
            if (fridayEvening) return Headline.FridayEvening
            return when (now.dayOfWeek) {
                DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> Headline.Weekend(now.dayOfWeek)
                else -> Headline.Weekday(now.dayOfWeek)
            }
        }

        fun subline(time: LocalTime): Subline = when {
            time >= EARLY_BIRD_START && time < EARLY_BIRD_END -> Subline.EARLY_BIRD
            time >= NIGHT_OWL_START || time < NIGHT_OWL_END -> Subline.NIGHT_OWL
            else -> Subline.CARE
        }

        fun subline(hour: Int, minute: Int): Subline = subline(LocalTime.of(hour, minute))

        /** The festival on [date], or null. China's statutory days; make-up workdays are ignored. */
        fun holidayOn(date: LocalDate): HolidayId? =
            HOLIDAYS.firstOrNull { date >= it.start && date <= it.end }?.id

        private data class Range(val start: LocalDate, val end: LocalDate, val id: HolidayId)

        /**
         * 2026–2028, including the lunar dates already converted. New Year's Eve is its own pool;
         * Christmas is deliberately absent. Extend the list when the next year is published —
         * a missing year simply falls back to weekend/weekday greetings.
         *
         * Order is priority: on 2028-10-03 Mid-Autumn and National Day overlap, and the moon wins.
         */
        private val HOLIDAYS = listOf(
            Range(LocalDate.of(2026, 12, 31), LocalDate.of(2026, 12, 31), HolidayId.NEW_YEAR_EVE),
            Range(LocalDate.of(2027, 12, 31), LocalDate.of(2027, 12, 31), HolidayId.NEW_YEAR_EVE),
            Range(LocalDate.of(2028, 12, 31), LocalDate.of(2028, 12, 31), HolidayId.NEW_YEAR_EVE),

            Range(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), HolidayId.NEW_YEAR),
            Range(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 1), HolidayId.NEW_YEAR),
            Range(LocalDate.of(2028, 1, 1), LocalDate.of(2028, 1, 1), HolidayId.NEW_YEAR),

            // 除夕 → 正月初六
            Range(LocalDate.of(2026, 2, 16), LocalDate.of(2026, 2, 22), HolidayId.SPRING_FESTIVAL),
            Range(LocalDate.of(2027, 2, 5), LocalDate.of(2027, 2, 11), HolidayId.SPRING_FESTIVAL),
            Range(LocalDate.of(2028, 1, 25), LocalDate.of(2028, 1, 31), HolidayId.SPRING_FESTIVAL),

            Range(LocalDate.of(2026, 4, 5), LocalDate.of(2026, 4, 5), HolidayId.QINGMING),
            Range(LocalDate.of(2027, 4, 5), LocalDate.of(2027, 4, 5), HolidayId.QINGMING),
            Range(LocalDate.of(2028, 4, 4), LocalDate.of(2028, 4, 4), HolidayId.QINGMING),

            Range(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5), HolidayId.LABOR_DAY),
            Range(LocalDate.of(2027, 5, 1), LocalDate.of(2027, 5, 5), HolidayId.LABOR_DAY),
            Range(LocalDate.of(2028, 5, 1), LocalDate.of(2028, 5, 5), HolidayId.LABOR_DAY),

            Range(LocalDate.of(2026, 6, 19), LocalDate.of(2026, 6, 19), HolidayId.DRAGON_BOAT),
            Range(LocalDate.of(2027, 6, 9), LocalDate.of(2027, 6, 9), HolidayId.DRAGON_BOAT),
            Range(LocalDate.of(2028, 5, 28), LocalDate.of(2028, 5, 28), HolidayId.DRAGON_BOAT),

            Range(LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 25), HolidayId.MID_AUTUMN),
            Range(LocalDate.of(2027, 9, 15), LocalDate.of(2027, 9, 15), HolidayId.MID_AUTUMN),
            Range(LocalDate.of(2028, 10, 3), LocalDate.of(2028, 10, 3), HolidayId.MID_AUTUMN),

            Range(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7), HolidayId.NATIONAL_DAY),
            Range(LocalDate.of(2027, 10, 1), LocalDate.of(2027, 10, 7), HolidayId.NATIONAL_DAY),
            Range(LocalDate.of(2028, 10, 1), LocalDate.of(2028, 10, 7), HolidayId.NATIONAL_DAY),
        )
    }
}
