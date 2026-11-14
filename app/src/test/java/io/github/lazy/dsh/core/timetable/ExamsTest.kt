package io.github.lazy.dsh.core.timetable

import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class ExamsTest {
    @Test fun `school response keeps exam times independent of class periods`() {
        val exam = ExamParser.parse("""{"total":1,"rows":[{"ksrcdm":"exam-1","kcmc":"Probability","ksrq":"2026-11-26","kssj":"19:00:00--21:00:00","kscdmc":"0408\\0410","xqmc":"Campus","zwh":null,"kslbmc":"Final"}]}""").single()
        assertEquals(LocalTime.of(19, 0), exam.start)
        assertEquals(LocalTime.of(21, 0), exam.end)
        assertEquals("19:00–21:00", exam.clockLabel)
        assertEquals("0408/0410", exam.room)
        assertEquals("", exam.seat)
        assertTrue(examsText(listOf(exam)).contains("21:00"))
    }

    @Test fun `empty list is valid but login pages malformed rows and truncated lists are not`() {
        assertTrue(ExamParser.parse("""{"total":0,"rows":[]}""").isEmpty())
        listOf(
            "<html>Login</html>", "{}", """{"rows":[{}]}""",
            """{"total":2,"rows":[{"kcmc":"A"}]}""",
            """{"rows":[]}""", """{"total":-1,"rows":[]}""",
            """{"total":"error","rows":[]}""", """{"total":0,"rows":[{"kcmc":"A"}]}""",
        ).forEach {
            assertTrue(runCatching { ExamParser.parse(it) }.exceptionOrNull() is NotExamJson)
        }
    }

    @Test fun `unpublished dates and times are retained without fabricating a schedule`() {
        val exam = ExamParser.parse("""{"total":1,"rows":[{"kcmc":"A","ksrq":"","kssj":"Pending"}]}""").single()
        assertNull(exam.day)
        assertNull(exam.start)
        assertEquals("Pending", exam.clockLabel)
    }

    @Test fun `sorts by actual date and time with undated exams last`() {
        val exams = ExamParser.parse("""{"total":3,"rows":[{"kcmc":"C"},{"kcmc":"B","ksrq":"2026-11-26","kssj":"19:00--21:00"},{"kcmc":"A","ksrq":"2026-11-26","kssj":"9:05-11:05"}]}""")
        assertEquals(listOf("A", "B", "C"), exams.map { it.course })
        assertEquals(LocalTime.of(9, 5), exams.first().start)
    }
}
