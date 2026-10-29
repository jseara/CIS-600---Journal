package com.example.journal.data

import com.example.journal.model.JournalEntry
import java.util.TimeZone

/** One hour in milliseconds, used to place seed entries at a sensible time of day. */
private const val HOUR_MS = 60L * 60L * 1000L

/**
 * Builds the ten sample entries the app starts with.
 *
 * Timestamps are computed relative to [now] so that all three sections are populated no matter
 * when the app is launched: three entries today, four during this week, three older.
 *
 * Today's entries are spread between local midnight and [now] (not "now minus N hours"), so they
 * stay on today's date even when the app is opened just after midnight.
 */
fun seedEntries(now: Long, timeZone: TimeZone): List<JournalEntry> {
    val todayStart = startOfDay(now, timeZone)
    val elapsedToday = now - todayStart

    /** Noon, [daysAgo] calendar days before today. */
    fun noonDaysAgo(daysAgo: Int): Long = startOfDay(now, timeZone, -daysAgo) + 12 * HOUR_MS

    return listOf(
        // Today
        JournalEntry(1, "Morning run", "Did 5k around the lake before class. Legs are sore but it felt great to get out early.", now),
        JournalEntry(2, "Android lecture notes", "Navigation Compose: routes, arguments, and how the back stack works. Remember popBackStack after saving.", todayStart + elapsedToday * 2 / 3),
        JournalEntry(3, "Grocery list", "Eggs, spinach, oat milk, coffee beans, and something for dinner on Friday.", todayStart + elapsedToday / 3),
        // This Week (1 to 6 days ago)
        JournalEntry(4, "Study group", "Worked through the ViewModel examples together. StateFlow finally clicked for me.", noonDaysAgo(1)),
        JournalEntry(5, "Rainy day", "Stayed in and read most of the afternoon. Made soup.", noonDaysAgo(2)),
        JournalEntry(6, "Call with Mom", "Caught up on family news. She wants photos from the weekend.", noonDaysAgo(4)),
        JournalEntry(7, "Project idea", "A small app that reminds me to drink water, with a home screen widget.", noonDaysAgo(6)),
        // Older
        JournalEntry(8, "Weekend hike", "Climbed to the fire tower. The view of the valley was worth every step.", noonDaysAgo(9)),
        JournalEntry(9, "First day of the semester", "New schedule, new professors. CIS 600 looks like it will be a lot of hands-on work.", noonDaysAgo(30)),
        JournalEntry(10, "Book recommendations", "Friends suggested three novels for the break. Starting with the shortest one.", noonDaysAgo(60)),
    )
}
