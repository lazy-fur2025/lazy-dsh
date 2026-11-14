package io.github.lazy.dsh.core.greeting

import java.time.DayOfWeek
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class HeroMomentTest {

    private fun moment(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): HeroMoment =
        HeroMoment.of(LocalDateTime.of(year, month, day, hour, minute))

    @Test fun `every weekday picks its own pool and ordinary care`() {
        val monday = moment(2026, 11, 16, 9)
        assertEquals(Headline.Weekday(DayOfWeek.MONDAY), monday.headline)
        assertEquals(Subline.CARE, monday.subline)
        assertEquals(Headline.Weekday(DayOfWeek.TUESDAY), moment(2026, 11, 17, 12).headline)
        assertEquals(Headline.Weekday(DayOfWeek.WEDNESDAY), moment(2026, 11, 18, 15).headline)
        assertEquals(Headline.Weekday(DayOfWeek.THURSDAY), moment(2026, 11, 19, 10).headline)
        assertEquals(Headline.Weekday(DayOfWeek.FRIDAY), moment(2026, 11, 20, 9).headline)
    }

    @Test fun `Friday evening starts at 18_00 and yields to the night owl at 23_00`() {
        assertEquals(Headline.Weekday(DayOfWeek.FRIDAY), moment(2026, 11, 20, 17, 59).headline)
        assertEquals(Headline.FridayEvening, moment(2026, 11, 20, 18).headline)
        assertEquals(Headline.FridayEvening, moment(2026, 11, 20, 22, 59).headline)
        // 23:00 is Friday's weekday headline again, but the subline is the night owl's.
        val late = moment(2026, 11, 20, 23)
        assertEquals(Headline.Weekday(DayOfWeek.FRIDAY), late.headline)
        assertEquals(Subline.NIGHT_OWL, late.subline)
    }

    @Test fun `Saturday and Sunday each read as their own weekend day, including a would-be make-up workday`() {
        assertEquals(Headline.Weekend(DayOfWeek.SATURDAY), moment(2026, 11, 14, 10).headline)
        assertEquals(Headline.Weekend(DayOfWeek.SUNDAY), moment(2026, 11, 15, 10).headline)
        // No make-up workday table: a Saturday outside a festival is always a weekend.
        assertEquals(Headline.Weekend(DayOfWeek.SATURDAY), moment(2026, 9, 26, 9).headline)
    }

    @Test fun `early bird is 05_00 to 07_29 and night owl is 23_00 to 04_59`() {
        assertEquals(Subline.NIGHT_OWL, HeroMoment.subline(4, 59))
        assertEquals(Subline.EARLY_BIRD, HeroMoment.subline(5, 0))
        assertEquals(Subline.EARLY_BIRD, HeroMoment.subline(7, 29))
        assertEquals(Subline.CARE, HeroMoment.subline(7, 30))
        assertEquals(Subline.CARE, HeroMoment.subline(22, 59))
        assertEquals(Subline.NIGHT_OWL, HeroMoment.subline(23, 0))
        assertEquals(Subline.NIGHT_OWL, HeroMoment.subline(0, 0))
    }

    @Test fun `weekend and early bird combine instead of overwriting each other`() {
        val sundayDawn = moment(2026, 11, 15, 6)
        assertEquals(Headline.Weekend(DayOfWeek.SUNDAY), sundayDawn.headline)
        assertEquals(Subline.EARLY_BIRD, sundayDawn.subline)
    }

    @Test fun `each festival is recognised on its day`() {
        assertEquals(HolidayId.NEW_YEAR, moment(2026, 1, 1, 9).headline.holiday())
        assertEquals(HolidayId.SPRING_FESTIVAL, moment(2026, 2, 17, 9).headline.holiday())
        assertEquals(HolidayId.QINGMING, moment(2026, 4, 5, 9).headline.holiday())
        assertEquals(HolidayId.LABOR_DAY, moment(2026, 5, 1, 9).headline.holiday())
        assertEquals(HolidayId.DRAGON_BOAT, moment(2026, 6, 19, 9).headline.holiday())
        assertEquals(HolidayId.MID_AUTUMN, moment(2026, 9, 25, 9).headline.holiday())
        assertEquals(HolidayId.NATIONAL_DAY, moment(2026, 10, 3, 9).headline.holiday())
        assertEquals(HolidayId.NEW_YEAR_EVE, moment(2026, 12, 31, 9).headline.holiday())
    }

    @Test fun `the Spring Festival range covers New Year's Eve through the sixth day`() {
        assertEquals(HolidayId.SPRING_FESTIVAL, moment(2026, 2, 16, 9).headline.holiday())
        assertEquals(HolidayId.SPRING_FESTIVAL, moment(2026, 2, 22, 9).headline.holiday())
        assertEquals(null, HeroMoment.holidayOn(java.time.LocalDate.of(2026, 2, 23)))
    }

    @Test fun `a festival beats the weekend`() {
        // 2026-10-03 is a Saturday inside National Day; 2027-02-06 is a Saturday inside Spring Festival.
        assertEquals(HolidayId.NATIONAL_DAY, moment(2026, 10, 3, 12).headline.holiday())
        assertEquals(HolidayId.SPRING_FESTIVAL, moment(2027, 2, 6, 12).headline.holiday())
    }

    @Test fun `Mid-Autumn wins when it falls inside National Day`() {
        // 2028-10-03: both Mid-Autumn and National Day.
        assertEquals(HolidayId.MID_AUTUMN, moment(2028, 10, 3, 12).headline.holiday())
        // ...but the rest of the National Day week still reads as National Day.
        assertEquals(HolidayId.NATIONAL_DAY, moment(2028, 10, 4, 12).headline.holiday())
    }

    @Test fun `a year without a table falls back to weekend and weekday`() {
        assertEquals(null, HeroMoment.holidayOn(java.time.LocalDate.of(2030, 10, 1)))
        assertEquals(Headline.Weekday(DayOfWeek.TUESDAY), moment(2030, 10, 1, 10).headline)
    }

    private fun Headline.holiday(): HolidayId? = (this as? Headline.Holiday)?.id
}
