package `fun`.dogon.note

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ReminderTests {
    private val zone=ZoneId.of("Europe/Istanbul")
    private fun epoch(s:String)=LocalDateTime.parse(s).atZone(zone).toInstant().toEpochMilli()
    @Test fun oncePastIsDisabled() { assertNull(Reminder("2026-01-01T12:00").next(epoch("2026-01-01T12:00"),zone)) }
    @Test fun explicitFutureDate() { assertEquals(epoch("2027-02-14T09:30"),Reminder("2027-02-14T09:30").next(epoch("2026-09-15T00:00"),zone)) }
    @Test fun monthEndDoesNotDrift() { val r=Reminder("2027-01-31T09:00","monthly");assertEquals(epoch("2027-02-28T09:00"),r.next(epoch("2027-02-01T00:00"),zone));assertEquals(epoch("2027-03-31T09:00"),r.next(epoch("2027-02-28T09:01"),zone)) }
    @Test fun leapDayRecoversInLeapYear() { val r=Reminder("2024-02-29T09:00","yearly");assertEquals(epoch("2025-02-28T09:00"),r.next(epoch("2025-01-01T00:00"),zone));assertEquals(epoch("2028-02-29T09:00"),r.next(epoch("2027-03-01T00:00"),zone)) }
    @Test fun hourlyIntervals() { assertEquals(epoch("2026-09-15T18:15"),Reminder("2026-09-15T10:15","hourly",4).next(epoch("2026-09-15T15:00"),zone)) }
    @Test fun daysSkipWeekend() { val r=Reminder("2026-09-14T08:00","weekdays",weekdays=listOf(1,2,3,4,5));assertEquals(epoch("2026-09-21T08:00"),r.next(epoch("2026-09-18T09:00"),zone)) }
    @Test fun cannotTriggerBeforeStart() { val r=Reminder("2027-01-04T08:00","weekdays",weekdays=listOf(1));assertEquals(epoch("2027-01-04T08:00"),r.next(epoch("2026-01-01T00:00"),zone)) }
    @Test fun disabledIsNeverScheduled() { assertNull(Reminder("2027-02-14T09:30",enabled=false).next(epoch("2026-01-01T00:00"),zone)) }
    @Test fun jsonRoundTrip() { val r=Reminder("2027-02-14T09:30","weekly",2);assertEquals(r,Reminder.parse(r.json())) }
    @Test fun rejectBadInterval() { assertTrue(runCatching { Reminder.parse("""{"anchor":"2027-01-01T09:00","repeat":"daily","interval":0}""") }.isFailure) }
    @Test fun rejectEmptyWeekdays() { assertTrue(runCatching { Reminder.parse("""{"anchor":"2027-01-01T09:00","repeat":"weekdays","weekdays":[]}""") }.isFailure) }
    @Test fun rejectBadBackupHeader() { assertTrue(runCatching { Backup.parse("{}") }.isFailure) }
    @Test fun futureTimestampsRemainReadable() { assertEquals("Az önce",relativeTime(999999,0)) }
    @Test fun daylightSavingKeepsLocalHour() { val berlin=ZoneId.of("Europe/Berlin");val after=ZonedDateTime.of(2027,3,27,10,0,0,0,berlin).toInstant().toEpochMilli();val next=Reminder("2027-03-27T09:00","daily").next(after,berlin)!!;assertEquals(9,Instant.ofEpochMilli(next).atZone(berlin).hour);assertEquals(28,Instant.ofEpochMilli(next).atZone(berlin).dayOfMonth) }
}
