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

    /** The expected layout: which entry ids belong in which section, per the seed-data spec. */
    private val expectedLayout = listOf(
        EntryGroup.TODAY to listOf(1, 2, 3),
        EntryGroup.THIS_WEEK to listOf(4, 5, 6, 7),
        EntryGroup.OLDER to listOf(8, 9, 10),
    )

    /** Seeds at [launch], groups the result, and returns each section as (group, sorted ids). */
    private fun layoutAt(launch: Long) =
        groupEntries(seedEntries(launch, NEW_YORK), launch, NEW_YORK)
            .map { it.group to it.entries.map { e -> e.id }.sorted() }

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
    fun seedEntries_groupedByGroupEntries_hasExpectedIdsPerSection() {
        assertEquals(expectedLayout, layoutAt(now))
    }

    @Test
    fun seedEntries_launchedJustAfterMidnight_keepsExpectedLayout() {
        // 00:05 is the hard case: "now minus a few hours" would put today's entries on yesterday.
        assertEquals(expectedLayout, layoutAt(at(2025, 6, 18, 0, 5)))
    }

    @Test
    fun seedEntries_launchedAtExactMidnight_keepsExpectedLayout() {
        assertEquals(expectedLayout, layoutAt(at(2025, 6, 18)))
    }

    @Test
    fun seedEntries_launchedJustBeforeMidnight_keepsExpectedLayout() {
        assertEquals(expectedLayout, layoutAt(at(2025, 6, 18, 23, 59)))
    }

    @Test
    fun seedEntries_launchedAroundDaylightSavingChange_keepsExpectedLayout() {
        // Both the spring-forward and fall-back weeks must keep the same layout.
        listOf(at(2025, 3, 11, 9), at(2025, 11, 4, 9)).forEach { launch ->
            assertEquals(expectedLayout, layoutAt(launch))
        }
    }
}
