package io.github.lazy.dsh.core.capability.tools

import android.content.Context
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.lazy.dsh.core.capability.CallContext
import io.github.lazy.dsh.core.capability.InvalidArgsException
import io.github.lazy.dsh.core.capability.PermissionMissingException
import io.github.lazy.dsh.core.capability.Tool
import io.github.lazy.dsh.core.capability.ToolResult
import io.github.lazy.dsh.core.capability.ToolSet
import io.github.lazy.dsh.core.capability.bool
import io.github.lazy.dsh.core.capability.int
import io.github.lazy.dsh.core.capability.reqStr
import io.github.lazy.dsh.core.capability.str
import io.github.lazy.dsh.core.model.Risk
import io.github.lazy.dsh.core.model.ToolGroup
import io.github.lazy.dsh.core.timetable.Course
import io.github.lazy.dsh.core.timetable.Ics
import io.github.lazy.dsh.core.timetable.Schedule
import io.github.lazy.dsh.core.timetable.Season
import io.github.lazy.dsh.core.timetable.TimetableParser
import io.github.lazy.dsh.core.timetable.TimetableReminders
import io.github.lazy.dsh.core.timetable.TimetableStore
import io.github.lazy.dsh.core.timetable.TimetableSync
import io.github.lazy.dsh.core.timetable.TimetableText
import io.github.lazy.dsh.core.timetable.termIdFor
import io.github.lazy.dsh.core.timetable.weekdayCn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The user's school timetable (济宁医学院 教务系统): one sync through the built-in browser, then
 * everyday questions answered from the cache — no session needed for 今天 / 本周 / 下一节.
 */
@Singleton
class TimetableTools @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: TimetableStore,
    private val browser: BrowserTools,
    private val reminders: TimetableReminders,
    private val sync: TimetableSync,
) : ToolSet {

    private fun cached(): List<Course> = store.courses()

    private fun noCache() = ToolResult.error(
        "no_cache",
        "There is no timetable on this phone yet. Run timetable_sync first (it needs a logged-in session in the built-in browser).",
    )

    override val tools = listOf(
        object : Tool(
            "timetable_exams", ToolGroup.Timetable, Risk.read, false,
            "Read cached exam arrangements, including exact exam times, campus, room and seat. Set sync=true to refresh " +
                "from the school system using the existing browser login. An empty successful list means nothing published; " +
                "a failed refresh preserves the last cache.",
            {
                bool("sync", "Refresh from the school system first; default false (offline cache)")
                string("term", "Six-digit term code, e.g. 202601. Default current term")
            },
        ) {
            override suspend fun invoke(args: JsonObject, call: CallContext): ToolResult {
                val term = args.str("term")?.trim()?.takeIf { it.isNotEmpty() } ?: termIdFor(LocalDate.now())
                if (!term.matches(Regex("[0-9]{6}"))) throw InvalidArgsException("term must look like 202601")
                if (args.bool("sync") == true) {
                    when (val fetched = sync.syncExams(term)) {
                        TimetableSync.ExamFetch.NotLoggedIn -> return ToolResult.error("not_logged_in", "Log in with timetable_login, then retry timetable_exams(sync=true). The old cache is unchanged.")
                        is TimetableSync.ExamFetch.Failed -> return ToolResult.error("exam_sync_failed", fetched.detail + ". The old cache is unchanged.")
                        is TimetableSync.ExamFetch.Ok -> Unit
                    }
                }
                val snapshot = withContext(Dispatchers.IO) { store.exams() }
                if (snapshot.fetchedAt.isBlank() || snapshot.term != term) return ToolResult.error("no_exam_cache", "No saved exam list for term $term. Call timetable_exams(sync=true).")
                val text = if (snapshot.items.isEmpty()) "No exams published for this term."
                    else io.github.lazy.dsh.core.timetable.examsText(snapshot.items)
                return ToolResult.text("Exams: ${snapshot.term}; last synced ${snapshot.fetchedAt}.\n$text")
            }
        },

        object : Tool(
            "timetable_sync", ToolGroup.Timetable, Risk.read, false,
            "Sync the user's 济宁医学院 timetable into this phone, replacing the cache. Reads the list endpoint in the built-in browser, " +
                "so not_logged_in means the user must log in there first (timetable_login). After a sync the other tools work offline.",
            {
                string("term", "202601 = autumn of 2026-2027. Default: the term today falls in")
            },
        ) {
            override suspend fun invoke(args: JsonObject, call: CallContext): ToolResult {
                val term = args.str("term")?.trim()?.takeIf { it.isNotEmpty() } ?: termIdFor(LocalDate.now())
                if (!term.matches(Regex("\\d{6}"))) throw InvalidArgsException("term must look like 202601")
                return when (val fetched = sync.fetch(term)) {
                    is TimetableSync.Fetch.Ok -> {
                        val saved = stored(fetched.courses, term, "Synced")
                        val examStatus = when (val exams = sync.syncExams(term)) {
                            is TimetableSync.ExamFetch.Ok -> "Exams synced: ${exams.exams.size}. Use timetable_exams for exact times and rooms."
                            TimetableSync.ExamFetch.NotLoggedIn -> "Exam refresh requires login; previous exam cache kept."
                            is TimetableSync.ExamFetch.Failed -> "Exam refresh failed; previous exam cache kept: ${exams.detail}"
                        }
                        // Keep the existing timetable result shape; the second part reports partial success explicitly.
                        saved.appendText(examStatus)
                    }
                    is TimetableSync.Fetch.NotLoggedIn -> ToolResult.error(
                        "not_logged_in",
                        "The school system answered with its login page, so the built-in browser has no valid session. " +
                            "Log in once there (browser_open ${TimetableSync.BASE}/ , browser_type the student number and password, then the 4-digit " +
                            "captcha read from browser_screenshot), then call timetable_sync again. A session in another browser does not count.",
                    )
                    is TimetableSync.Fetch.NotTimetable -> ToolResult.error(
                        "not_timetable",
                        "The endpoint did not return the timetable JSON. First 300 characters: ${fetched.snippet.replace("\n", " ")}",
                    )
                    is TimetableSync.Fetch.Empty -> ToolResult.error(
                        "empty",
                        "The system returned a timetable with no classes for term $term. Check the term code (202601 is the autumn term).",
                    )
                    is TimetableSync.Fetch.Failed -> ToolResult.error("sync_failed", fetched.detail)
                }
            }
        },

        object : Tool(
            "timetable_login", ToolGroup.Timetable, Risk.write, false,
            "Log the built-in browser in to the 教务系统 with the account and password from a JSON file on this phone, so the password never " +
                "enters this conversation. Call it without captcha first: it returns the captcha image — show it to the user, ask for the 4 " +
                "digits, then call again with captcha.",
            {
                string("captcha", "The 4 digits from the captcha image; omit on the first call to get an image")
                string("path", "Credentials file, relative to /sdcard or absolute. Default Download/jnmc-account.json")
            },
        ) {
            override suspend fun invoke(args: JsonObject, call: CallContext): ToolResult {
                val file = credsFile(args.str("path"))
                if (!withContext(Dispatchers.IO) { file.isFile }) return ToolResult.error(
                    "no_credentials",
                    "${file.path} does not exist. Create it as {\"account\":\"<学号>\",\"password\":\"<密码>\"} and call again — " +
                        "the tool reads the file here, so the password never has to be typed into the conversation.",
                )
                val creds = withContext(Dispatchers.IO) { readCreds(file) }
                    ?: return ToolResult.error(
                        "no_credentials",
                        "${file.path} exists but its account or password is empty. Fill both fields and call again.",
                    )
                val captcha = args.str("captcha")?.trim()?.takeIf { it.isNotEmpty() }

                // Step 1: open the login page and hand the captcha image to the user.
                if (captcha == null) {
                    val text = browser.pageText("${TimetableSync.BASE}/")
                    if (text.contains("注销") || text.contains("信息查询")) {
                        return ToolResult.text("Already logged in — call timetable_sync. (No captcha was needed.)")
                    }
                    val b64 = browser.eval(CAPTCHA_JS)
                    if (b64.length < 100) return ToolResult.error(
                        "no_captcha",
                        "The login page is open but its captcha image could not be read (${b64}). Take a browser_screenshot instead, " +
                            "read the 4 digits with the user, then call timetable_login with captcha.",
                    )
                    return ToolResult.image(
                        "image/png", b64,
                        "验证码图片已取到（正好 4 位）。请让用户看这张图说出 4 位字符，确认后再带 captcha 调用一次 timetable_login。" +
                            "密码从 ${file.path} 读取，不会出现在对话里。",
                    )
                }

                if (!captcha.matches(Regex("[0-9A-Za-z]{4}"))) throw InvalidArgsException("captcha must be exactly 4 characters")
                val quote = { s: String -> Json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(s)) }
                val fill = FILL_JS
                    .replace("%account%", quote(creds.first))
                    .replace("%password%", quote(creds.second))
                    .replace("%captcha%", quote(captcha))

                var r = browser.eval(fill)
                if (r == "no_form") {
                    browser.pageText("${TimetableSync.BASE}/")
                    r = browser.eval(fill)
                }
                if (r == "no_form") return ToolResult.error(
                    "no_login_form",
                    "The login page was not open in the browser, so there was no form to fill. Nothing was submitted.",
                )
                if (r == "no_button") return ToolResult.error(
                    "no_login_button",
                    "Filled the form but found no 登录 button to press. Nothing was submitted.",
                )

                delay(1_000)
                // Success = the page left the login form. Failure = the page stays and layui shows the
                // server's message ("验证码不正确" / "用户名或密码错误"), which says which half was wrong.
                var loggedIn = false
                var serverMsg: String? = null
                for (i in 0 until 8) {
                    val text = browser.currentText()
                    if (text.contains("注销") || text.contains("信息查询") || text.contains("课表查询")) { loggedIn = true; break }
                    val layer = browser.eval(LAYER_JS)
                    if (layer != "none") { serverMsg = layer; break }
                    delay(600)
                }
                if (loggedIn) return ToolResult.text(
                    "Logged in. The browser holds the session now — call timetable_sync to load the timetable. " +
                        "You can delete ${file.path} afterwards if you prefer not to keep the password there.",
                )
                val msg = serverMsg
                return ToolResult.error(
                    "login_failed",
                    when {
                        msg == null -> "Submitted, but the page stayed on the login form without showing why. " +
                            "Try again with a fresh captcha; if it keeps happening the form may have changed."
                        msg.contains("验证码") ->
                            "The system rejected the captcha ($msg). Call timetable_login again without captcha for a fresh image."
                        msg.contains("密码") || msg.contains("账号") || msg.contains("用户名") ->
                            "The system rejected the account or password ($msg). The file ${file.path} holds a wrong " +
                                "account/password — ask the user to fix that file, not the captcha."
                        else -> "Login failed: $msg"
                    },
                )
            }
        },

        object : Tool(
            "timetable_remind", ToolGroup.Timetable, Risk.write, false,
            "Turn class reminders on/off and set how early they are. Local alarms + system notifications: no model, no engine, nothing " +
                "spent per class. Default 15 min before the start; lead 0 rings when the class starts.",
            {
                bool("enabled", "On/off; omit to keep the current state")
                int("lead", "Minutes before the class starts: 0 = at the start, default 15")
                bool("test", "Post one now to test")
            },
        ) {
            override suspend fun invoke(args: JsonObject, call: CallContext): ToolResult {
                val st = store.state()
                val lead = (args.int("lead") ?: st.leadMinutes).coerceIn(0, 120)
                val enabled = args.bool("enabled") ?: (if (args.int("lead") != null) true else st.remind)
                if (enabled && cached().isEmpty()) return noCache()
                if (enabled && !TimetableReminders.notificationsAllowed(context)) {
                    throw PermissionMissingException("Notifications")
                }
                if (args.bool("test") == true) {
                    val posted = reminders.postNow()
                    return if (posted) ToolResult.text("已发一条示例提醒，看看通知栏有没有。没有就是通知被关了或渠道被静音了。")
                    else ToolResult.text("缓存里没有还没开始的课，发不出示例提醒。")
                }
                store.setReminder(enabled, lead)
                val next = reminders.resync()
                if (!enabled) return ToolResult.text("Class reminders are off; nothing will be posted.")
                if (next == null) return ToolResult.text(
                    "Reminders are on (${if (lead > 0) "$lead min before" else "at the start"}), but the cached timetable has no class ahead.",
                )
                val at = Instant.ofEpochMilli(next.alertAt).atZone(ZoneId.systemDefault())
                val start = Instant.ofEpochMilli(next.startsAt).atZone(ZoneId.systemDefault())
                val whenText = "${at.toLocalDate()} ${at.toLocalTime().toString().take(5)}"
                val startText = start.toLocalTime().toString().take(5)
                val room = next.course.room.takeIf { it.isNotEmpty() }?.let { "，$it" }.orEmpty()
                val exact = if (TimetableReminders.exactAllowed(context)) "" else
                    "（系统未允许「闹钟和提醒」，可能会晚几分钟：手机设置 → 应用 → DSH → 闹钟和提醒）"
                return ToolResult.text(
                    "提醒已开启：${if (lead > 0) "课前 $lead 分钟" else "上课时"}通知。下一次 $whenText → ${next.course.course}" +
                        "（$startText 开始$room）。$exact",
                )
            }
        },

        object : Tool(
            "timetable_import", ToolGroup.Timetable, Risk.write, false,
            "Import a timetable from a JSON file saved elsewhere (a PC export): the list endpoint's {total, rows:[…]} or this app's " +
                "courses.json. For when logging in here is inconvenient.",
            {
                string("path", "File path, relative to /sdcard or absolute", required = true)
                string("term", "202601 = autumn of 2026-2027. Default: the term today falls in")
            },
        ) {
            override suspend fun invoke(args: JsonObject, call: CallContext): ToolResult {
                val wanted = args.reqStr("path")
                val term = args.str("term")?.trim()?.takeIf { it.isNotEmpty() } ?: termIdFor(LocalDate.now())
                if (!hasAllFilesAccess(context)) throw PermissionMissingException("All files access")
                val text = withContext(Dispatchers.IO) {
                    val f = if (wanted.startsWith("/")) File(wanted) else File(Environment.getExternalStorageDirectory(), wanted)
                    if (!f.isFile) throw InvalidArgsException("no file at ${f.path}")
                    f.readText()
                }
                val courses = runCatching { TimetableParser.parse(text) }
                    .getOrElse { runCatching { store.decodeCourses(text) }.getOrElse {
                        return ToolResult.error("not_timetable", "That file is neither the list endpoint's JSON nor courses.json.")
                    } }
                if (courses.isEmpty()) return ToolResult.error("empty", "That file holds no classes.")
                return stored(courses, term, "Imported")
            }
        },

        object : Tool(
            "timetable_today", ToolGroup.Timetable, Risk.read, false,
            "Today's classes with clock times. Pass date for another day.",
            {
                string("date", "YYYY-MM-DD; default today")
            },
        ) {
            override suspend fun invoke(args: JsonObject, call: CallContext): ToolResult {
                val courses = cached()
                if (courses.isEmpty()) return noCache()
                val date = args.str("date")?.trim()?.takeIf { it.isNotEmpty() }
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() ?: throw InvalidArgsException("date must be YYYY-MM-DD") }
                    ?: LocalDate.now()
                return ToolResult.text(TimetableText.day(courses, date, store.season(), store.term()))
            }
        },

        object : Tool(
            "timetable_week", ToolGroup.Timetable, Risk.read, false,
            "One week of the cached timetable, Monday to Sunday with clock times. Default: this week.",
            {
                int("week", "Week number, 1-based; default the current week")
            },
        ) {
            override suspend fun invoke(args: JsonObject, call: CallContext): ToolResult {
                val courses = cached()
                if (courses.isEmpty()) return noCache()
                val term = store.term()
                val week = args.int("week")?.takeIf { it > 0 } ?: term.weekOf(LocalDate.now())
                if (week <= 0) return ToolResult.error(
                    "no_week",
                    "Today is outside the cached term (it starts ${term.firstMonday}); pass week=1..20.",
                )
                return ToolResult.text(TimetableText.week(courses, week, store.season(), term))
            }
        },

        object : Tool(
            "timetable_next", ToolGroup.Timetable, Risk.read, false,
            "The upcoming classes from now, with the wait until the first.",
            {
                int("count", "How many classes to list, default 3")
            },
        ) {
            override suspend fun invoke(args: JsonObject, call: CallContext): ToolResult {
                val courses = cached()
                if (courses.isEmpty()) return noCache()
                val season = store.season()
                val now = LocalDateTime.now()
                val upcoming = courses.mapNotNull { c ->
                    runCatching {
                        LocalDateTime.of(LocalDate.parse(c.date), LocalTime.parse(Schedule.start(season, c.startPeriod)!!))
                    }.getOrNull()?.let { it to c }
                }.filter { it.first.isAfter(now) }.sortedBy { it.first }
                    .take((args.int("count") ?: 3).coerceIn(1, 20))

                if (upcoming.isEmpty()) return ToolResult.text("No classes left in the cached timetable after now.")
                val head = durationText(Duration.between(now, upcoming.first().first))
                return ToolResult.text(
                    buildString {
                        append("Next in $head. ${upcoming.size} upcoming:")
                        upcoming.forEach { (at, c) ->
                            append("\n  ").append(at.toLocalDate().toString().substring(5)).append(" ").append(weekdayCn(at.toLocalDate()))
                            val span = Schedule.span(season, c)
                            if (span != null) append(" ").append(span)
                            append("  第").append(c.slot).append("节  ").append(c.course)
                            if (c.teacher.isNotEmpty()) append(" / ").append(c.teacher)
                            if (c.room.isNotEmpty()) append(" / ").append(c.room)
                            if (c.note.isNotEmpty()) append(" · ").append(c.note)
                        }
                    },
                )
            }
        },

        object : Tool(
            "timetable_season", ToolGroup.Timetable, Risk.write, false,
            "Set the 作息 in force: winter 下午 14:00, summer 下午 14:30 (other blocks are the same). The school switches on its own " +
                "schedule; set it when the user says the times changed.",
            {
                string("season", "winter or summer", required = true, enum = listOf("winter", "summer"))
            },
        ) {
            override suspend fun invoke(args: JsonObject, call: CallContext): ToolResult {
                val season = Season.of(args.str("season")) ?: throw InvalidArgsException("season must be winter or summer")
                store.setSeason(season)
                reminders.resync()
                val blocks = Schedule.table(season).entries.sortedBy { it.key }.chunked(2)
                    .joinToString("，") { pair -> "第${pair.first().key}-${pair.last().key}节 ${pair.first().value.first}-${pair.last().value.second}" }
                return ToolResult.text("作息已改为 ${season.id}（$blocks）。要用在日历里就再跑一次 timetable_export。")
            }
        },

        object : Tool(
            "timetable_export", ToolGroup.Timetable, Risk.write, false,
            "Write the cached timetable as an .ics file for the phone calendar (needs All files access).",
            {
                string("path", "Relative to /sdcard or absolute. Default Download/课表.ics")
            },
        ) {
            override suspend fun invoke(args: JsonObject, call: CallContext): ToolResult {
                val courses = cached()
                if (courses.isEmpty()) return noCache()
                val season = store.season()
                val name = "课表 ${store.state().term}".trim()
                val (ics, skipped) = Ics.build(courses, season, name)
                val events = courses.size - skipped

                val wanted = args.str("path")?.trim()?.takeIf { it.isNotEmpty() } ?: "Download/课表.ics"
                val target = withContext(Dispatchers.IO) {
                    if (!hasAllFilesAccess(context)) throw PermissionMissingException("All files access")
                    val f = (if (wanted.startsWith("/")) File(wanted) else File(Environment.getExternalStorageDirectory(), wanted))
                    f.parentFile?.mkdirs()
                    f.writeText(ics)
                    f
                }
                return ToolResult.text(
                    "Wrote $events events (${season.id} 作息${if (skipped > 0) ", $skipped without a clock time skipped" else ""}) to ${target.path}. " +
                        "Importing it in the phone calendar gives a reminder per class; re-import after a sync to update.",
                )
            }
        },
    )

    /** The credentials file: [arg] (absolute or relative to /sdcard) or Download/jnmc-account.json. */
    private fun credsFile(arg: String?): File {
        val path = arg?.trim()?.takeIf { it.isNotEmpty() } ?: "Download/jnmc-account.json"
        return if (path.startsWith("/")) File(path) else File(Environment.getExternalStorageDirectory(), path)
    }

    /** `{ "account": "...", "password": "..." }` — null when missing, unreadable or incomplete. */
    private fun readCreds(file: File): Pair<String, String>? {
        if (!hasAllFilesAccess(context)) throw PermissionMissingException("All files access")
        val text = runCatching { file.readText() }.getOrNull() ?: return null
        val o = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return null
        val account = (o["account"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        val password = (o["password"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        return if (account.isEmpty() || password.isEmpty()) null else account to password
    }

    /** Saves [courses] as the cache and reports what it holds (shared by sync and import). */
    private fun stored(courses: List<Course>, term: String, verb: String): ToolResult {
        val saved = when (val result = sync.save(courses, term)) {
            is TimetableSync.Save.NoDates -> return ToolResult.error(
                "no_dates",
                "The timetable has no usable dates (pkrq), so week 1 cannot be placed.",
            )
            is TimetableSync.Save.Ok -> result
        }
        val dates = courses.map { it.date }.filter { it.isNotEmpty() }.sorted()
        return ToolResult.text(
            "$verb term $term: ${saved.classes} classes, ${saved.courses} courses, ${dates.firstOrNull()} → ${dates.lastOrNull()}. " +
                "Week 1 started ${saved.monday}" + (if (saved.todayWeek > 0) "; today ${LocalDate.now()} is week ${saved.todayWeek}" else "") +
                ". 作息: ${store.season().id}. Ask timetable_today or timetable_week for the details.",
        )
    }

    private fun durationText(d: Duration): String {
        val mins = d.toMinutes().coerceAtLeast(0)
        return when {
            mins < 60 -> "$mins min"
            mins < 60 * 24 -> "${mins / 60} h ${mins % 60} min"
            else -> "${mins / (60 * 24)} d ${(mins % (60 * 24)) / 60} h"
        }
    }

    private companion object {
        /** Draws the captcha <img> into a canvas and returns the PNG as base64 (same origin, so no taint). */
        const val CAPTCHA_JS = """
(()=>{
  const pick=/yzm|captcha|verify|code/i;
  const img=[...document.images].find(i=>pick.test(i.src)||pick.test(i.className||'')||pick.test(i.id||''));
  if(!img) return 'no_image';
  const w=img.naturalWidth||img.width, h=img.naturalHeight||img.height;
  if(!w||!h) return 'no_image';
  const c=document.createElement('canvas'); c.width=w; c.height=h;
  c.getContext('2d').drawImage(img,0,0,w,h);
  try{ return c.toDataURL('image/png').split(',')[1]; }catch(e){ return 'no_image'; }
})()
"""

        /** The login page's own error popup (layui layer), or 'none'. */
        const val LAYER_JS = """
(()=>{
  const l=document.querySelector('.layui-layer-content,.layui-layer-msg');
  const t=l&&l.innerText?l.innerText.trim():'';
  return t? t.slice(0,120) : 'none';
})()
"""

        /**
         * Fills the login form and presses 登录. The page's own handler encrypts the password
         * (AES-128-ECB with the captcha as key) before posting, so nothing here re-implements it.
         * %account% / %password% / %captcha% are replaced with JSON-quoted strings.
         */
        const val FILL_JS = """
(()=>{
  const a=document.querySelector('input[name=account]');
  const p=document.querySelector('input[name=pwd]');
  const v=document.querySelector('input[name=verifycode]');
  if(!a||!p||!v) return 'no_form';
  const set=(el,val)=>{el.value=val;el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));};
  set(a,%account%); set(p,%password%); set(v,%captcha%);
  const btn=[...document.querySelectorAll('[lay-submit],[lay-filter],button,a')]
    .find(e=>/立即登录|登\s*录/.test((e.innerText||'').trim()));
  if(!btn) return 'no_button';
  btn.click();
  return 'ok';
})()
"""
    }
}
