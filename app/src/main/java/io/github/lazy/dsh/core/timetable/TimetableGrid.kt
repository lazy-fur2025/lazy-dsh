package io.github.lazy.dsh.core.timetable

import java.time.LocalDate

/**
 * How the 课表 screen draws a week: five 90-minute rows, seven day columns. Pure data so the grid's
 * grouping can be tested without Compose (the school has no periods 5 or 10, hence the split).
 */
object TimetableGrid {
    /** The periods covered by each row, in the order they appear in a day. */
    val blocks: List<List<Int>> = listOf(
        listOf(1, 2),
        listOf(3, 4),
        listOf(6, 7),
        listOf(8, 9),
        listOf(11, 12),
    )

    /** One day's classes, already bucketed into [blocks] (a block can hold two overlapping classes). */
    data class Day(val date: LocalDate, val weekday: Int, val byBlock: List<List<Course>>) {
        val classes: List<Course> get() = byBlock.flatten()
        val empty: Boolean get() = classes.isEmpty()
    }

    fun day(courses: List<Course>, date: LocalDate): Day {
        val onDay = courses.filter { it.date == date.toString() }.sortedBy { it.startPeriod }
        return Day(date, date.dayOfWeek.value, blocks.map { row -> onDay.filter { it.startPeriod in row } })
    }

    fun week(courses: List<Course>, monday: LocalDate): List<Day> =
        (0..6).map { day(courses, monday.plusDays(it.toLong())) }
}
