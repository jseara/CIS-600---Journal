package com.example.journal.data

import com.example.journal.model.EntryGroup
import com.example.journal.model.EntrySection
import com.example.journal.model.JournalEntry
import java.util.Calendar
import java.util.TimeZone

/*
 * Pure date helpers for grouping and seeding.
 *
 * Why java.util.Calendar instead of java.time? minSdk is 24 and java.time only exists from API 26
 * unless we turn on core library desugaring. Calendar is available on every API level and is
 * enough for "start of day" and "go back N days", so we avoid an extra build feature.
 *
 * Every function takes `now` and `timeZone` as parameters instead of reading the system clock.
 * That keeps them deterministic, so unit tests can pass in a fixed moment and time zone.
 */

/** How many calendar days (including today) the Today + This Week sections cover together. */
const val WEEK_WINDOW_DAYS = 7

/**
 * Returns the epoch millis of local midnight at the start of the day that contains [now],
 * shifted by [dayOffset] calendar days (e.g. -1 for the start of yesterday).
 *
 * We move by calendar days with `Calendar.add(DAY_OF_MONTH, ...)` rather than subtracting
 * 24 hours, because a day can be 23 or 25 hours long across a daylight-saving change.
 */
fun startOfDay(now: Long, timeZone: TimeZone, dayOffset: Int = 0): Long {
    val calendar = Calendar.getInstance(timeZone).apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_MONTH, dayOffset)
    }
    return calendar.timeInMillis
}

/**
 * Decides which section a single [timestamp] belongs to, relative to [now] in [timeZone].
 *
 * - [EntryGroup.TODAY]: on or after local midnight today (future timestamps count as today too).
 * - [EntryGroup.THIS_WEEK]: during the six calendar days before today.
 * - [EntryGroup.OLDER]: everything earlier.
 *
 * "This Week" is a rolling 7-day window rather than the calendar week (Mon–Sun), because a
 * calendar week makes the section empty or tiny early in the week, and the first day of the
 * week differs by locale.
 */
fun groupFor(timestamp: Long, now: Long, timeZone: TimeZone): EntryGroup {
    val todayStart = startOfDay(now, timeZone)
    val weekStart = startOfDay(now, timeZone, dayOffset = -(WEEK_WINDOW_DAYS - 1))
    return when {
        timestamp >= todayStart -> EntryGroup.TODAY
        timestamp >= weekStart -> EntryGroup.THIS_WEEK
        else -> EntryGroup.OLDER
    }
}

/**
 * Splits [entries] into the sections shown on the List screen.
 *
 * Sections come back in [EntryGroup] order (Today, This Week, Older), each sorted newest first.
 * Empty sections are left out so the UI never shows a header with nothing under it.
 * Entries with the same timestamp are ordered by descending id, which keeps the order stable.
 */
fun groupEntries(entries: List<JournalEntry>, now: Long, timeZone: TimeZone): List<EntrySection> {
    val sorted = entries.sortedWith(
        compareByDescending<JournalEntry> { it.timestamp }.thenByDescending { it.id }
    )
    // groupBy keeps the (already sorted) order of entries inside each group.
    val byGroup = sorted.groupBy { groupFor(it.timestamp, now, timeZone) }
    // Walk the enum in declaration order so the sections always appear Today, This Week, Older.
    return EntryGroup.entries.mapNotNull { group ->
        byGroup[group]?.let { EntrySection(group, it) }
    }
}
