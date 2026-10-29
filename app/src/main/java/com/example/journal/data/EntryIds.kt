package com.example.journal.data

import com.example.journal.model.JournalEntry

/**
 * The navigation argument value that means "no entry yet, create a new one".
 *
 * Real ids start at 1 (see [nextEntryId]), so -1 can never clash with an actual entry.
 */
const val NEW_ENTRY_ID = -1

/**
 * Returns an id that is not used by any of [entries]: one more than the current highest id,
 * or 1 for an empty list.
 *
 * Using "max + 1" (rather than "size + 1") stays correct after deletions: deleting entry 3 of 5
 * and then adding one gives id 6, never a duplicate 5.
 */
fun nextEntryId(entries: List<JournalEntry>): Int =
    (entries.maxOfOrNull { it.id } ?: 0) + 1
