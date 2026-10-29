package com.example.journal.data

import com.example.journal.model.EntryGroup
import com.example.journal.model.JournalEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [startOfDay], [groupFor] and [groupEntries].
 *
 * All tests use a fixed "now" (Wednesday 18 June 2025, 15:30) in America/New_York, so the
 * results never depend on the real clock or the machine's time zone.
 */
class EntryGroupingTest {

    private val now = at(2025, 6, 18, 15, 30)

    private fun entry(id: Int, timestamp: Long) = JournalEntry(id, "t$id", "", timestamp)

    // ---- startOfDay ----

    @Test
    fun startOfDay_midAfternoon_returnsLocalMidnight() {
        assertEquals(at(2025, 6, 18), startOfDay(now, NEW_YORK))
    }

    @Test
    fun startOfDay_exactlyMidnight_returnsSameInstant() {
        val midnight = at(2025, 6, 18)
        assertEquals(midnight, startOfDay(midnight, NEW_YORK))
    }

    @Test
    fun startOfDay_oneSecondBeforeMidnight_returnsThatDaysMidnight() {
        assertEquals(at(2025, 6, 17), startOfDay(at(2025, 6, 17, 23, 59, 59), NEW_YORK))
    }

    @Test
    fun startOfDay_negativeOffset_movesBackWholeCalendarDays() {
        assertEquals(at(2025, 6, 17), startOfDay(now, NEW_YORK, -1))
        assertEquals(at(2025, 6, 12), startOfDay(now, NEW_YORK, -6))
    }

    @Test
    fun startOfDay_positiveOffset_movesForward() {
        assertEquals(at(2025, 6, 19), startOfDay(now, NEW_YORK, 1))
    }

    @Test
    fun startOfDay_offsetCrossesMonthBoundary_rollsOverCorrectly() {
        assertEquals(at(2025, 5, 31), startOfDay(at(2025, 6, 2, 9), NEW_YORK, -2))
    }

    @Test
    fun startOfDay_sameInstantDifferentZones_givesDifferentMidnights() {
        // 03:00 UTC on 18 June is still the evening of 17 June in New York (23:00 EDT).
        val instant = at(2025, 6, 18, 3, zone = java.util.TimeZone.getTimeZone("UTC"))
        assertEquals(at(2025, 6, 17), startOfDay(instant, NEW_YORK))
        assertEquals(
            at(2025, 6, 18, zone = java.util.TimeZone.getTimeZone("UTC")),
            startOfDay(instant, java.util.TimeZone.getTimeZone("UTC")),
        )
    }

    @Test
    fun startOfDay_springForwardDay_previousDayIsOnly23HoursLong() {
        // US clocks jumped forward on 9 March 2025, so that calendar day has 23 hours.
        val afterChange = at(2025, 3, 10, 10)
        val length = startOfDay(afterChange, NEW_YORK) - startOfDay(afterChange, NEW_YORK, -1)
        assertEquals(23L * 60 * 60 * 1000, length)
    }

    @Test
    fun startOfDay_fallBackDay_previousDayIs25HoursLong() {
        // Clocks went back on 2 November 2025, so that calendar day has 25 hours.
        val afterChange = at(2025, 11, 3, 10)
        val length = startOfDay(afterChange, NEW_YORK) - startOfDay(afterChange, NEW_YORK, -1)
        assertEquals(25L * 60 * 60 * 1000, length)
    }

    // ---- groupFor ----

    @Test
    fun groupFor_exactlyMidnightToday_isToday() {
        assertEquals(EntryGroup.TODAY, groupFor(at(2025, 6, 18), now, NEW_YORK))
    }

    @Test
    fun groupFor_oneSecondBeforeMidnight_isThisWeek() {
        // 23:59:59 yesterday is NOT today, even though it is less than 24 hours ago.
        assertEquals(EntryGroup.THIS_WEEK, groupFor(at(2025, 6, 17, 23, 59, 59), now, NEW_YORK))
    }

    @Test
    fun groupFor_futureTimestamp_isToday() {
        assertEquals(EntryGroup.TODAY, groupFor(at(2025, 6, 30, 8), now, NEW_YORK))
    }

    @Test
    fun groupFor_nowItself_isToday() {
        assertEquals(EntryGroup.TODAY, groupFor(now, now, NEW_YORK))
    }

    @Test
    fun groupFor_sixDaysAgoAtMidnight_isThisWeek() {
        // The first instant of the window: start of 12 June when now is 18 June.
        assertEquals(EntryGroup.THIS_WEEK, groupFor(at(2025, 6, 12), now, NEW_YORK))
    }

    @Test
    fun groupFor_oneSecondBeforeWeekWindow_isOlder() {
        assertEquals(EntryGroup.OLDER, groupFor(at(2025, 6, 11, 23, 59, 59), now, NEW_YORK))
    }

    @Test
    fun groupFor_nowJustAfterMidnight_yesterdayEveningIsThisWeek() {
        val justAfterMidnight = at(2025, 6, 18, 0, 5)
        assertEquals(
            EntryGroup.THIS_WEEK,
            groupFor(at(2025, 6, 17, 23, 55), justAfterMidnight, NEW_YORK),
        )
        assertEquals(EntryGroup.TODAY, groupFor(justAfterMidnight, justAfterMidnight, NEW_YORK))
    }

    @Test
    fun groupFor_windowSpansSpringForward_stillUsesCalendarDays() {
        // now = 12 March 2025; six calendar days back is 6 March, with the DST change in between.
        val march12 = at(2025, 3, 12, 12)
        assertEquals(EntryGroup.THIS_WEEK, groupFor(at(2025, 3, 6), march12, NEW_YORK))
        assertEquals(EntryGroup.OLDER, groupFor(at(2025, 3, 5, 23, 59), march12, NEW_YORK))
    }

    @Test
    fun groupFor_windowSpansFallBack_stillUsesCalendarDays() {
        // now = 5 November 2025; the window starts on 30 October, before the clocks went back.
        val nov5 = at(2025, 11, 5, 12)
        assertEquals(EntryGroup.THIS_WEEK, groupFor(at(2025, 10, 30), nov5, NEW_YORK))
        assertEquals(EntryGroup.OLDER, groupFor(at(2025, 10, 29, 23, 59), nov5, NEW_YORK))
    }

    // ---- groupEntries ----

    @Test
    fun groupEntries_emptyList_returnsNoSections() {
        assertTrue(groupEntries(emptyList(), now, NEW_YORK).isEmpty())
    }

    @Test
    fun groupEntries_oneEntryPerGroup_returnsThreeSectionsInOrder() {
        val sections = groupEntries(
            listOf(
                entry(1, at(2025, 1, 1)),        // older
                entry(2, at(2025, 6, 15)),       // this week
                entry(3, at(2025, 6, 18, 9)),    // today
            ),
            now, NEW_YORK,
        )
        assertEquals(
            listOf(EntryGroup.TODAY, EntryGroup.THIS_WEEK, EntryGroup.OLDER),
            sections.map { it.group },
        )
        assertEquals(listOf(3), sections[0].entries.map { it.id })
        assertEquals(listOf(2), sections[1].entries.map { it.id })
        assertEquals(listOf(1), sections[2].entries.map { it.id })
    }

    @Test
    fun groupEntries_emptyGroups_areOmitted() {
        val sections = groupEntries(
            listOf(entry(1, at(2025, 6, 18, 9)), entry(2, at(2024, 1, 1))),
            now, NEW_YORK,
        )
        assertEquals(listOf(EntryGroup.TODAY, EntryGroup.OLDER), sections.map { it.group })
    }

    @Test
    fun groupEntries_onlyOlderEntries_returnsSingleOlderSection() {
        val sections = groupEntries(listOf(entry(1, at(2024, 1, 1))), now, NEW_YORK)
        assertEquals(listOf(EntryGroup.OLDER), sections.map { it.group })
    }

    @Test
    fun groupEntries_withinSection_sortedNewestFirst() {
        val sections = groupEntries(
            listOf(
                entry(1, at(2025, 6, 18, 8)),
                entry(2, at(2025, 6, 18, 14)),
                entry(3, at(2025, 6, 18, 11)),
            ),
            now, NEW_YORK,
        )
        assertEquals(listOf(2, 3, 1), sections.single().entries.map { it.id })
    }

    @Test
    fun groupEntries_equalTimestamps_tieBrokenByDescendingId() {
        val sameTime = at(2025, 6, 18, 9)
        val sections = groupEntries(
            listOf(entry(2, sameTime), entry(5, sameTime), entry(3, sameTime)),
            now, NEW_YORK,
        )
        assertEquals(listOf(5, 3, 2), sections.single().entries.map { it.id })
    }

    @Test
    fun groupEntries_futureEntry_landsInTodayAndSortsFirst() {
        val sections = groupEntries(
            listOf(entry(1, at(2025, 6, 18, 9)), entry(2, at(2025, 7, 4))),
            now, NEW_YORK,
        )
        assertEquals(EntryGroup.TODAY, sections.single().group)
        assertEquals(listOf(2, 1), sections.single().entries.map { it.id })
    }

    @Test
    fun groupEntries_doesNotMutateOrDropEntries() {
        // Deliberately NOT in sorted order, so an in-place sort of the input would be detected.
        val input = listOf(
            entry(3, at(2020, 1, 1)),
            entry(1, at(2025, 6, 18, 9)),
            entry(2, at(2025, 6, 14)),
        )
        val copy = input.toList()
        val sections = groupEntries(input, now, NEW_YORK)
        assertEquals(copy, input)
        // Every entry appears in exactly one section: right ids, and no duplicates.
        val flatIds = sections.flatMap { it.entries }.map { it.id }
        assertEquals(setOf(1, 2, 3), flatIds.toSet())
        assertEquals(3, flatIds.size)
    }

    @Test
    fun groupEntries_boundaryEntries_splitAtExpectedPlaces() {
        val sections = groupEntries(
            listOf(
                entry(1, at(2025, 6, 18)),               // today, first instant
                entry(2, at(2025, 6, 17, 23, 59)),       // this week, 23:59 yesterday
                entry(3, at(2025, 6, 12)),               // this week, exactly 6 days ago
                entry(4, at(2025, 6, 11, 23, 59)),       // older, just past 7 days
            ),
            now, NEW_YORK,
        )
        assertEquals(
            listOf(EntryGroup.TODAY, EntryGroup.THIS_WEEK, EntryGroup.OLDER),
            sections.map { it.group },
        )
        assertEquals(listOf(1), sections[0].entries.map { it.id })
        assertEquals(listOf(2, 3), sections[1].entries.map { it.id })
        assertEquals(listOf(4), sections[2].entries.map { it.id })
    }
}
