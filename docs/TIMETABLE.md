# Timetable (课表)

The `timetable` capability group reads the user's timetable from the school's 教务系统
(济宁医学院, `http://210.44.16.13`, 乘方科技) and answers everyday questions from a local cache, so
"今天有什么课" / 本周 / 下一节 never touch the network.

## Where the data comes from

The system's own timetable page is backed by a list endpoint that returns JSON (not an HTML table):

```
GET /new/xsgrkbcx!getDataList.action?xnxqdm=<term>&zc=&page=1&rows=1000&sort=kxh&order=asc
-> { "total": 266, "rows": [ { "kcmc", "teaxms", "jxcdmc", "xq", "jcdm", "zc", "pkrq", … } ] }
```

| field | meaning |
|---|---|
| `kcmc` | course name |
| `teaxms` | teacher |
| `jxcdmc` | room (`\` separates two room numbers, shown as `/`) |
| `xq` | weekday, 1 = Monday |
| `jcdm` | period code: `0102` `0304` `0607` `0809` `1112` |
| `zc` | week of the term |
| `pkrq` | the real date, `YYYY-MM-DD` (authoritative: weekday and week follow from it) |
| `jxhjmc` | 理论教学 / 实验教学 / 体育教学 |
| `sknrjj` | what happens in that class |
| `jxbmc` | the merged class groups |

`term` is `YYYYNN`: `202601` = 2026-2027 学年 第 1 学期 (September to January), `202602` = 第 2 学期.

## How it is fetched

`timetable_sync` does **not** implement the login. It opens the endpoint in the built-in agent browser
([BrowserTools](../app/src/main/java/io/github/lazy/dsh/core/capability/tools/BrowserTools.kt),
`pageText`), which sends that WebView's cookies.

`timetable_login` establishes that session without the password ever entering the conversation:

1. The user writes the account and password to a file on the phone (default
   `Download/jnmc-account.json`):
   `{"account": "<学号>", "password": "…"}`
2. `timetable_login` (no captcha) opens the login page, draws the captcha `<img>` into a canvas and
   returns it as an image — it shows up inline in the conversation, so the user (or a vision model)
   can read the 4 characters.
3. `timetable_login(captcha = "…")` fills the form from the file and presses 登录. The page's own
   handler encrypts the password (`AES-128-ECB(utf8(password), key = utf8(captcha × 4), PKCS7)`,
   lowercase hex), so nothing in the app re-implements it.
4. On failure the layui popup says which half was wrong — the tool reports 验证码 vs 账号/密码
   accordingly, instead of blaming the captcha every time.

The password only exists in that file and in the browser's request; it is never a tool argument and
never stored by the app. Delete the file once logged in — the session lives in the WebView's cookie
jar, and it expires after a while (the next `timetable_sync` then answers `not_logged_in`).

Why not do it in Kotlin: the captcha is a distorted 4-character image that generic OCR cannot read
(Windows OCR and ML Kit both return nothing), so a human or a vision model has to read it, and
微信扫码登录 needs a QR encoder plus WeChat. Driving the existing WebView keeps the login human and the
code honest, and reuses the site's own encryption.

## Files on the phone

`files/dsh-home/data/timetable/` (app-private):

| file | content |
|---|---|
| `courses.json` | the parsed classes (`Course`: course, type, teacher, room, weekday, periods, week, date, klass, note) |
| `state.json` | `term`, `firstMonday`, `season`, `fetchedAt` |
| `timetable.ics` | written by `timetable_export` into `Download/课表.ics` |

## 作息 (clock times)

The school has five 90-minute blocks — they match the only `jcdm` values, and periods 5 and 10 do not
exist:

| block | 冬季 | 夏季 |
|---|---|---|
| 1-2 | 08:00-09:30 | 08:00-09:30 |
| 3-4 | 09:50-11:20 | 09:50-11:20 |
| 6-7 | 14:00-15:30 | 14:30-16:00 |
| 8-9 | 15:50-17:20 | 16:20-17:50 |
| 11-12 | 19:00-20:30 | 19:00-20:30 |

The school switches on its own schedule, so `season` is a stored setting, not a date rule:
`timetable_season` flips it. Both tables live in
[Schedule](../app/src/main/java/io/github/lazy/dsh/core/timetable/Timetable.kt).

## Reminders (上课提醒)

Class reminders are **local alarms + system notifications**, not agent runs: no model, no engine, no
tokens per class, and they fire with the phone asleep.

- `timetable_remind(lead = 15)` turns them on; `lead = 0` rings when the class starts;
  `enabled = false` turns them off; `test = true` posts one immediately so the user can check that
  notifications actually arrive (title prefixed `Sample ·`).
- One exact alarm at a time, for the next class (`TimetableReminders.next` → `nextClassAlert`), and
  each firing chains the next one. One alarm means nothing to keep in sync: a sync, a 作息 change or a
  lead-time change is a single cancel-and-schedule, and a class already under way is skipped instead
  of being announced late.
- Re-armed on sync / import / season change, on app start (force-stop clears alarms), and after a
  reboot (`BootReceiver`).
- Channel `timetable` (high importance, its own channel so it can be muted alone); the reminder is
  posted by `TimetableAlarmReceiver`, a non-exported broadcast receiver.
- Needs POST_NOTIFICATIONS on Android 13+ (the tool raises `PermissionMissingException("Notifications")`
  so the normal permission card appears) and "Alarms & reminders" for exact timing — without it the
  alarm still fires, but the result text says it may be a few minutes late.

The notification says what a person needs at a glance: `15 分钟后上课 · UI设计` /
`第3-4节 · 09:50-11:20 · B312/316教室 · 徐琦`.

## Tools

| tool | risk | what it does |
|---|---|---|
| `timetable_sync` | read | fetch the term into the cache through the agent browser |
| `timetable_login` | write | log in with the account/password file; returns the captcha image |
| `timetable_import` | write | import a JSON saved elsewhere (list endpoint or `courses.json`) |
| `timetable_today` | read | one day with clock times (`date` optional) |
| `timetable_week` | read | Monday to Sunday (`week` optional) |
| `timetable_next` | read | upcoming classes and the wait until the first |
| `timetable_season` | write | winter / summer 作息 |
| `timetable_remind` | write | class reminders: on/off, lead time, a test post |
| `timetable_export` | write | `.ics` into the phone calendar |

A daily briefing is a normal rule: trigger `Time(at = 07:00, days = 1..5)`, prompt "用 timetable_today
报今天的课", and allow `timetable_today` in the automation's tool list.

## Tests

`app/src/test/java/io/github/lazy/dsh/core/timetable/TimetableTest.kt` covers parsing (including the
login page), period codes, week maths, the term code, both 作息 tables, the `.ics` output (clock times,
stable UIDs, 75-octet folding) and the day text.
