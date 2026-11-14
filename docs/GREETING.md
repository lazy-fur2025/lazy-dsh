# Home greeting (首页问候)

The empty home page has two lines: a **headline** chosen by the date and a **subline** chosen by the
clock. They are independent, so "周五晚上" and "夜猫子" can appear together.

## Rules

Headline priority:

1. Festival (`HolidayId`)
2. Friday 18:00–22:59 → `hero_fri_evening`
3. Saturday / Sunday → `hero_sat` / `hero_sun` (each weekend day has its own pool)
4. Monday–Friday → `hero_mon` … `hero_fri`

Subline:

| band | pool |
|---|---|
| 05:00–07:29 | `hero_earlybird` |
| 23:00–04:59 | `hero_nightowl` |
| otherwise | `hero_care` |

At 23:00 Friday the headline falls back to Friday's weekday pool and the subline becomes the night
owl's; that is intended, not a gap.

## Festivals

`core/greeting/HeroMoment.kt` holds a 2026–2028 table (New Year's Eve, New Year, Spring Festival
除夕–初六, Qingming, Labor Day, Dragon Boat, Mid-Autumn, National Day). Christmas is deliberately not
included. Make-up workdays are ignored: Saturday and Sunday always read as weekend.

When the next year's dates are published, add `Range(...)` rows in priority order. A year with no
rows simply falls back to weekend/weekday copy. On 2028-10-03 Mid-Autumn and National Day overlap;
Mid-Autumn wins because it is listed first.

## Copy

`res/values/hero.xml`, `values-en/hero.xml`, `values-ru/hero.xml` — one array per pool. Keep the three
files in sync. The headline is picked at random once per moment and `rememberSaveable` keeps it
stable across rotation; the page ticks once a minute so crossing midnight or a band boundary re-rolls
without a restart.

## Tests

`app/src/test/java/io/github/lazy/dsh/core/greeting/HeroMomentTest.kt` covers every weekday, the
17:59/18:00 and 22:59/23:00 boundaries, 04:59/05:00/07:29/07:30, festival-over-weekend,
Mid-Autumn-over-National-Day, and a year without a table.
