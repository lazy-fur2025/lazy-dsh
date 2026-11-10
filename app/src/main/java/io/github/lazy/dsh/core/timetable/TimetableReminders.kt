package io.github.lazy.dsh.core.timetable

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.lazy.dsh.R
import io.github.lazy.dsh.core.platform.BallNotice
import io.github.lazy.dsh.core.platform.Notifications
import io.github.lazy.dsh.core.trigger.canExact
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Class reminders without the agent: one exact alarm for the next class, posted as a system
 * notification by [TimetableAlarmReceiver], and after each one the next is chained. No model call, no
 * engine, no tokens — only the cached timetable and the 作息 in force.
 *
 * One alarm at a time on purpose: there is nothing to keep in sync, a missed alarm cannot leave a
 * stale queue behind, and changing the lead time or the timetable is a single cancel-and-schedule.
 */
@Singleton
class TimetableReminders @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: TimetableStore,
) {
    /** The next alert: which class, when to post it, and when the class itself starts. */
    data class Next(val course: Course, val alertAt: Long, val startsAt: Long) {
        val leadMinutes: Int get() = ((startsAt - alertAt) / 60_000L).toInt()
    }

    /**
     * Next class whose alert is still ahead of [after]; classes already under way are skipped, so a
     * reminder never fires for a class that started (the phone was off through it).
     */
    fun next(
        courses: List<Course> = store.courses(),
        season: Season = store.season(),
        lead: Int = store.state().leadMinutes,
        after: Long = System.currentTimeMillis(),
    ): Next? = nextClassAlert(courses, season, lead, after)
        ?.let { Next(it.course, it.alertAt, it.startsAt) }

    /** Cancels whatever was pending and schedules the next one if reminders are on. */
    fun resync(): Next? {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(alarmPendingIntent())
        val st = store.state()
        if (!st.remind) return null
        val n = next(lead = st.leadMinutes) ?: return null
        val pi = alarmPendingIntent(n)
        if (am.canExact()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, n.alertAt, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, n.alertAt, pi)
        return n
    }

    /** Posts the reminder carried by [intent]'s extras, then arms the following one. */
    fun onAlarm(intent: Intent) {
        val course = intent.getStringExtra(EXTRA_COURSE).orEmpty()
        val room = intent.getStringExtra(EXTRA_ROOM).orEmpty()
        val teacher = intent.getStringExtra(EXTRA_TEACHER).orEmpty()
        val span = intent.getStringExtra(EXTRA_SPAN).orEmpty()
        val slot = intent.getStringExtra(EXTRA_SLOT).orEmpty()
        val lead = intent.getIntExtra(EXTRA_LEAD, 0)
        if (course.isNotEmpty()) post(course, room, teacher, span, slot, lead)
        resync()
    }

    private fun post(course: String, room: String, teacher: String, span: String, slot: String, lead: Int, sample: Boolean = false) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val title = if (lead > 0) context.getString(R.string.notif_class_title, lead, course)
        else context.getString(R.string.notif_class_now, course)
        // A test post says so, so "in 15 min" is not read as a real class that is 15 minutes away.
        val shown = if (sample) context.getString(R.string.notif_class_sample, title) else title
        val detail = listOf("第${slot}节", span, room, teacher).filter { it.isNotBlank() }.joinToString(" · ")
        // The same reminder as a bubble beside the floating ball, when it is running. Short on purpose:
        // the capsule is one line and ~18 characters wide (the notification has the full detail).
        val leadText = if (lead > 0) context.getString(R.string.ball_class_lead, lead, course)
        else context.getString(R.string.ball_class_now, course)
        val bubble = if (sample) context.getString(R.string.notif_class_sample, leadText) else leadText
        BallNotice.show(listOf(bubble, room).filter { it.isNotBlank() }.joinToString(" · "))
        val b = NotificationCompat.Builder(context, Notifications.CHANNEL_TIMETABLE)
            .setSmallIcon(R.drawable.ic_stat_dsh)
            .setContentTitle(shown)
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(Notifications.openRunning(context))
        runCatching { NotificationManagerCompat.from(context).notify(Notifications.ID_CLASS, b.build()) }
    }

    /** Posts one sample reminder now, so the user can check that notifications actually arrive. */
    fun postNow(): Boolean {
        val n = next() ?: return false
        val c = n.course
        post(c.course, c.room, c.teacher, Schedule.span(store.season(), c).orEmpty(), c.slot, n.leadMinutes, sample = true)
        return true
    }

    /** The alarm currently armed (or a no-op pending intent to cancel it). */
    private fun alarmPendingIntent(n: Next? = null): PendingIntent {
        val intent = Intent(context, TimetableAlarmReceiver::class.java).setAction(ACTION_CLASS)
        if (n != null) {
            val c = n.course
            intent.putExtra(EXTRA_COURSE, c.course)
                .putExtra(EXTRA_ROOM, c.room)
                .putExtra(EXTRA_TEACHER, c.teacher)
                .putExtra(EXTRA_SLOT, c.slot)
                .putExtra(EXTRA_SPAN, Schedule.span(store.season(), c).orEmpty())
                .putExtra(EXTRA_LEAD, n.leadMinutes)
        }
        // One request code: there is only ever one class alarm.
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val ACTION_CLASS = "io.github.lazy.dsh.TIMETABLE_CLASS"
        private const val EXTRA_COURSE = "course"
        private const val EXTRA_ROOM = "room"
        private const val EXTRA_TEACHER = "teacher"
        private const val EXTRA_SLOT = "slot"
        private const val EXTRA_SPAN = "span"
        private const val EXTRA_LEAD = "lead"

        /** 33+ needs the runtime permission; before that the app-wide switch is the only gate. */
        fun notificationsAllowed(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) return false
            return NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

        /** Exact alarms can fire several minutes late without this (Alarms & reminders). */
        fun exactAllowed(context: Context): Boolean = context.getSystemService(AlarmManager::class.java).canExact()
    }
}

/** The class alarm itself: post the reminder, then arm the next one (internal, not exported). */
@dagger.hilt.android.AndroidEntryPoint
class TimetableAlarmReceiver : android.content.BroadcastReceiver() {
    @javax.inject.Inject lateinit var reminders: TimetableReminders

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TimetableReminders.ACTION_CLASS) return
        // A cold-started process must not die before the notification is posted and the next alarm armed.
        val pending = goAsync()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            try {
                reminders.onAlarm(intent)
            } catch (e: Exception) {
                android.util.Log.w("TimetableAlarm", "reminder failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}
