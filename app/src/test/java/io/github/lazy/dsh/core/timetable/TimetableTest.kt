package io.github.lazy.dsh.core.timetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** The timetable's pure parts: parsing the list endpoint, week maths, 作息 and the .ics output. */
class TimetableTest {
    private val body = """
        {"total":2,"rows":[
          {"kcmc":"Java程序设计","teaxms":"葛敬军","jxcdmc":"C301\\303教室","xq":"1","jcdm":"0304",
           "zc":"1","pkrq":"2026-09-07","jxhjmc":"理论教学","sknrjj":"JDBC1","jxbmc":"25计算机本-1"},
          {"kcmc":"大学英语3","teaxms":"段梅青","jxcdmc":"C112\\116教室","xq":"4","jcdm":"0102",
           "zc":"1","pkrq":"2026-09-10","jxhjmc":"理论教学","sknrjj":"Unit 1","jxbmc":"25计算机本-1"}
        ]}
    """.trimIndent()

    @Test fun `parses the list endpoint`() {
        val courses = TimetableParser.parse(body)
        assertEquals(2, courses.size)
        val java = courses.first()
        assertEquals("Java程序设计", java.course)
        assertEquals("葛敬军", java.teacher)
        // The system separates the two room numbers with a backslash; it reads as a slash.
        assertEquals("C301/303教室", java.room)
        assertEquals(listOf(3, 4), java.periods)
        assertEquals("3-4", java.slot)
        assertEquals(LocalDate.of(2026, 9, 7), LocalDate.parse(java.date))
        assertEquals(1, java.weekday)
        assertEquals("JDBC1", java.note)
    }

    @Test fun `a login page is not the timetable`() {
        val error = runCatching { TimetableParser.parse("<html><input name=\"verifycode\"></html>") }.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error is NotTimetableJson)
        assertTrue((error as NotTimetableJson).snippet.contains("verifycode"))
    }

    @Test fun `rows without a course name are dropped`() {
        val courses = TimetableParser.parse("""{"total":1,"rows":[{"kcmc":"","jcdm":"0102"}]}""")
        assertTrue(courses.isEmpty())
    }

    @Test fun `period codes`() {
        assertEquals(listOf(1, 2), TimetableParser.periodsOf("0102"))
        assertEquals(listOf(11, 12), TimetableParser.periodsOf("1112"))
        assertTrue(TimetableParser.periodsOf("").isEmpty())
        assertTrue(TimetableParser.periodsOf("ab").isEmpty())
    }

    @Test fun `week numbers count from week one's Monday`() {
        val term = Term("202601", "2026-09-07")
        assertEquals(1, term.weekOf(LocalDate.of(2026, 9, 7)))
        assertEquals(1, term.weekOf(LocalDate.of(2026, 9, 13)))
        assertEquals(2, term.weekOf(LocalDate.of(2026, 9, 14)))
        assertEquals(9, term.weekOf(LocalDate.of(2026, 11, 8)))
        assertEquals(LocalDate.of(2026, 11, 2), term.mondayOf(9))
        assertEquals(0, term.weekOf(LocalDate.of(2026, 8, 1)))
    }

    @Test fun `the term code follows the academic calendar`() {
        assertEquals("202601", termIdFor(LocalDate.of(2026, 9, 7)))
        assertEquals("202601", termIdFor(LocalDate.of(2027, 1, 5)))
        assertEquals("202602", termIdFor(LocalDate.of(2027, 3, 1)))
        assertEquals("202602", termIdFor(LocalDate.of(2027, 7, 20)))
    }

    @Test fun `winter and summer differ only in the afternoon`() {
        assertEquals("08:00", Schedule.start(Season.Winter, 1))
        assertEquals("08:00", Schedule.start(Season.Summer, 1))
        assertEquals("14:00", Schedule.start(Season.Winter, 6))
        assertEquals("14:30", Schedule.start(Season.Summer, 6))
        assertEquals("20:30", Schedule.end(Season.Summer, 12))
        // 5 and 10 do not exist: the school has five 90-minute blocks.
        assertNull(Schedule.start(Season.Winter, 5))
        assertNull(Schedule.start(Season.Winter, 10))
    }

    @Test fun `ics has one event per class with real clock times`() {
        val courses = TimetableParser.parse(body)
        val (ics, skipped) = Ics.build(courses, Season.Winter, "课表 202601")
        assertEquals(0, skipped)
        assertEquals(2, Regex("BEGIN:VEVENT").findAll(ics).count())
        assertTrue(ics.contains("DTSTART;TZID=Asia/Shanghai:20260907T095000"))
        assertTrue(ics.contains("DTEND;TZID=Asia/Shanghai:20260907T112000"))
        assertTrue(ics.contains("SUMMARY:Java程序设计"))
        assertTrue(ics.contains("LOCATION:C301/303教室"))
        // Summer moves the same class 30 minutes later.
        val (summer, _) = Ics.build(courses, Season.Summer, "x")
        assertTrue(summer.contains("DTSTART;TZID=Asia/Shanghai:20260910T080000"))
    }

    @Test fun `ics ids are stable between runs`() {
        val courses = TimetableParser.parse(body)
        val a = Ics.build(courses, Season.Winter, "x").first
        val b = Ics.build(courses.reversed(), Season.Winter, "x").first
        assertEquals(a.lines().filter { it.startsWith("UID:") }, b.lines().filter { it.startsWith("UID:") })
    }

    @Test fun `lines fold to 75 octets without splitting a character`() {
        val long = "DESCRIPTION:" + "很长的一段课程内容".repeat(12)
        val folded = Ics.fold(long)
        folded.lines().forEach { assertTrue("${it.length}: $it", it.toByteArray(Charsets.UTF_8).size <= 75) }
        // Unfolding (CRLF + space) gives the original line back.
        assertEquals(long, folded.replace("\r\n ", ""))
        assertEquals("short", Ics.fold("short"))
    }

    @Test fun `reminder picks the next class and never refires a started one`() {
        val courses = TimetableParser.parse(body)
        val zone = java.time.ZoneId.of("Asia/Shanghai")
        fun ms(date: String, time: String) = LocalDateTime.parse("${date}T$time").atZone(zone).toInstant().toEpochMilli()

        val first = nextClassAlert(courses, Season.Winter, 15, ms("2026-09-07", "09:00"), zone)!!
        assertEquals("Java程序设计", first.course.course)
        assertEquals(ms("2026-09-07", "09:50"), first.startsAt)
        assertEquals(ms("2026-09-07", "09:35"), first.alertAt)
        assertEquals(15, first.leadMinutes)

        // The alert time has passed (the class is under way): no reminder for it, and the next
        // class is the one three days later.
        val second = nextClassAlert(courses, Season.Winter, 15, ms("2026-09-07", "09:40"), zone)!!
        assertEquals("大学英语3", second.course.course)

        // lead 0 rings when the class starts.
        val atStart = nextClassAlert(courses, Season.Winter, 0, ms("2026-09-07", "09:00"), zone)!!
        assertEquals(atStart.startsAt, atStart.alertAt)

        // Nothing left after the last class.
        assertNull(nextClassAlert(courses, Season.Winter, 15, ms("2026-09-10", "12:00"), zone))
    }

    @Test fun `day text lists clock times and the note`() {        val courses = TimetableParser.parse(body)
        val text = TimetableText.day(courses, LocalDate.of(2026, 9, 7), Season.Winter, Term("202601", "2026-09-07"))
        assertTrue(text, text.contains("周一"))
        assertTrue(text, text.contains("第 1 周"))
        assertTrue(text, text.contains("09:50-11:20"))
        assertTrue(text, text.contains("JDBC1"))
    }
}
