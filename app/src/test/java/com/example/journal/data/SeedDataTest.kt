package com.example.journal.data

import com.example.journal.model.EntryGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [seedEntries]: the sample data must fill all three List sections no matter
 * when the app is launched. Uses fixed instants in America/New_York.
 */
class SeedDataTest {

    private val now = at(2025, 6, 18, 15, 30)

    private fun groupOfId(id: Int, at: Long = now): EntryGroup {
        val entry = seedEntries(at, NEW_YORK).first { it.id == id }
        return groupFor(entry.timestamp, at, NEW_YORK)
    }

    @Test
    fun seedEntries_returnsTenEntriesWithUniqueIds() {
        val entries = seedEntries(now, NEW_YORK)
        assertEquals(10, entries.size)
        assertEquals((1..10).toList(), entries.map { it.id }.sorted())
    }

    @Test
    fun seedEntries_titlesAreNotBlank() {
        assertTrue(seedEntries(now, NEW_YORK).all { it.title.isNotBlank() })
    }

    @Test
    fun seedEntries_idsOneToThree_areToday() {
        (1..3).forEach { assertEquals("id $it", EntryGroup.TODAY, groupOfId(it)) }
    }

    @Test
    fun seedEntries_idsFourToSeven_areThisWeek() {
        (4..7).forEach { assertEquals("id $it", EntryGroup.THIS_WEEK, groupOfId(it)) }
    }

    @Test
    fun seedEntries_idsEightToTen_areOlder() {
        (8..10).forEach { assertEquals("id $it", EntryGroup.OLDER, groupOfId(it)) }
    }

    @Test
    fun seedEntries_groupedByGroupEntries_hasThreeSectionsOfThreeFourThree() {
        val sections = groupEntries(seedEntries(now, NEW_YORK), now, NEW_YORK)
        assertEquals(
            listOf(EntryGroup.TODAY, EntryGroup.THIS_WEEK, EntryGroup.OLDER),
            sections.map { it.group },
        )
        assertEquals(listOf(3, 4, 3), sections.map { it.entries.size })
    }

    @Test
    fun seedEntries_launchedJustAfterMidnight_todayEntriesStayToday() {
        // 00:05 is the hard case: "now minus a few hours" would fall on yesterday.
        val justAfterMidnight = at(2025, 6, 18, 0, 5)
        (1..3).forEach {
            assertEquals("id $it", EntryGroup.TODAY, groupOfId(it, justAfterMidnight))
        }
    }

    @Test
    fun seedEntries_launchedAtExactMidnight_stillThreeSections() {
        val midnight = at(2025, 6, 18)
        val sections = groupEntries(seedEntries(midnight, NEW_YORK), midnight, NEW_YORK)
        assertEquals(listOf(3, 4, 3), sections.map { it.entries.size })
    }

    @Test
    fun seedEntries_launchedJustBeforeMidnight_stillThreeSections() {
        val lateNight = at(2025, 6, 18, 23, 59)
        val sections = groupEntries(seedEntries(lateNight, NEW_YORK), lateNight, NEW_YORK)
        assertEquals(listOf(3, 4, 3), sections.map { it.entries.size })
    }

    @Test
    fun seedEntries_launchedAroundDaylightSavingChange_stillThreeSections() {
        // Both the spring-forward and fall-back weeks must keep the 3/4/3 split.
        listOf(at(2025, 3, 11, 9), at(2025, 11, 4, 9)).forEach { launch ->
            val sections = groupEntries(seedEntries(launch, NEW_YORK), launch, NEW_YORK)
            assertEquals(listOf(3, 4, 3), sections.map { it.entries.size })
        }
    }
}
