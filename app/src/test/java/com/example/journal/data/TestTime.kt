package com.example.journal.data

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

/** The fixed zone every data test uses, so results never depend on the machine's time zone. */
val NEW_YORK: TimeZone = TimeZone.getTimeZone("America/New_York")

/**
 * Test helper: epoch millis of a local wall-clock time in [zone].
 * [month] is 1-based (1 = January), unlike [Calendar.MONTH], to keep test dates readable.
 *
 * Uses [GregorianCalendar] directly instead of `Calendar.getInstance(zone)`: `getInstance`
 * picks the calendar type from the default *locale*, so under e.g. th_TH it returns a Buddhist
 * calendar whose years are offset by 543 and the same arguments give a different instant.
 * A GregorianCalendar makes the helper independent of both the machine's zone and its locale.
 */
fun at(
    year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0,
    second: Int = 0, zone: TimeZone = NEW_YORK,
): Long = GregorianCalendar(zone).apply {
    clear()
    set(year, month - 1, day, hour, minute, second)
}.timeInMillis
