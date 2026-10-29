package com.example.journal.data

import com.example.journal.model.JournalEntry
import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for [nextEntryId]. */
class EntryIdsTest {

    private fun entry(id: Int) = JournalEntry(id, "t", "", 0L)

    @Test
    fun nextEntryId_emptyList_returnsOne() {
        assertEquals(1, nextEntryId(emptyList()))
    }

    @Test
    fun nextEntryId_contiguousIds_returnsMaxPlusOne() {
        assertEquals(4, nextEntryId(listOf(entry(1), entry(2), entry(3))))
    }

    @Test
    fun nextEntryId_gapInIds_usesMaxNotSize() {
        // Entries 1..5 with 3 deleted: size is 4, but the next id must be 6, never a duplicate 5.
        assertEquals(6, nextEntryId(listOf(entry(1), entry(2), entry(4), entry(5))))
    }

    @Test
    fun nextEntryId_unorderedList_stillFindsMax() {
        assertEquals(10, nextEntryId(listOf(entry(7), entry(9), entry(2))))
    }

}
