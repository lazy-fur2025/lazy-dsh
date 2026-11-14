package io.github.lazy.dsh.core.timetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The 课表 grid grouping: five 90-minute rows, seven days, one class in exactly one cell. */
class TimetableGridTest {
    private fun course(name: String, date: String, periods: List<Int>, room: String = "") = Course(
        course = name, date = date, weekday = LocalDate.parse(date).dayOfWeek.value,
        periods = periods, week = 1, room = room,
    )

    @Test
    fun blocksAreTheFiveSchoolRows() {
        assertEquals(listOf(listOf(1, 2), listOf(3, 4), listOf(6, 7), listOf(8, 9), listOf(11, 12)), TimetableGrid.blocks)
    }

    @Test
    fun dayPutsEachClassInItsRowAndSkipsOtherDates() {
        val monday = LocalDate.parse("2026-11-09")
        val courses = listOf(
            course("数学", "2026-11-09", listOf(3, 4), "C301"),
            course("英语", "2026-11-09", listOf(1, 2), "B102"),
            course("物理", "2026-11-10", listOf(1, 2)),
        )
        val day = TimetableGrid.day(courses, monday)
        assertEquals(2, day.classes.size)
        assertEquals(listOf("英语"), day.byBlock[0].map { it.course })
        assertEquals(listOf("数学"), day.byBlock[1].map { it.course })
        assertTrue(day.byBlock[2].isEmpty())
        assertEquals(5, day.byBlock.size)
    }

    @Test
    fun weekStartsOnMondayAndHasSevenDays() {
        val monday = LocalDate.parse("2026-09-07")
        val courses = listOf(course("数学", "2026-09-09", listOf(6, 7)))
        val week = TimetableGrid.week(courses, monday)
        assertEquals(7, week.size)
        assertEquals(monday, week.first().date)
        assertEquals(LocalDate.parse("2026-09-13"), week.last().date)
        assertEquals(2, week.indexOfFirst { !it.empty })
        assertEquals("数学", week[2].classes.single().course)
    }

    @Test
    fun emptyWeekHasNoClassesInAnyCell() {
        val week = TimetableGrid.week(emptyList(), LocalDate.parse("2026-09-07"))
        assertTrue(week.all { it.empty })
        assertTrue(week.all { it.byBlock.size == 5 })
    }
}
