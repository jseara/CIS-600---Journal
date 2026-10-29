package com.example.journal.data

import java.util.Calendar
import java.util.TimeZone

/** The fixed zone every data test uses, so results never depend on the machine running them. */
val NEW_YORK: TimeZone = TimeZone.getTimeZone("America/New_York")

/**
 * Test helper: epoch millis of a local wall-clock time in [zone].
 * [month] is 1-based (1 = January), unlike [Calendar.MONTH], to keep test dates readable.
 */
fun at(
    year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0,
    second: Int = 0, zone: TimeZone = NEW_YORK,
): Long = Calendar.getInstance(zone).apply {
    clear()
    set(year, month - 1, day, hour, minute, second)
}.timeInMillis
