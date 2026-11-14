package io.github.lazy.dsh.core.timetable

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class ExamStoreTest {
    @Test fun `exam snapshot survives recreation and successful empty response clears it`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = TimetableStore(context)
        val exam = Exam(course = "A", date = "2026-11-26", time = "19:00--21:00")
        store.saveExams(listOf(exam), "202601")
        val saved = TimetableStore(context).exams()
        assertEquals("202601", saved.term)
        assertTrue(saved.fetchedAt.isNotBlank())
        assertEquals(listOf(exam), saved.items)
        assertTrue(runCatching { ExamParser.parse("<html>Login</html>") }.isFailure)
        assertEquals(saved, store.exams())
        store.saveExams(emptyList(), "202602")
        assertTrue(store.exams().toString(), store.exams().items.isEmpty())
        assertEquals("202602", store.exams().term)
    }
}
