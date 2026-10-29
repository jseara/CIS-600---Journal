package com.example.journal.model

/**
 * One journal entry.
 *
 * This is an immutable `data class`: to "change" an entry we create a copy with `copy(...)`.
 * Immutability matters in Compose because the UI only redraws when it sees a *new* value,
 * so mutating an object in place would leave the screen showing stale data.
 *
 * @property id Unique, positive identifier. It is the value passed as the navigation argument.
 * @property title Short title shown on the card and in the top of the Detail screen. Never blank.
 * @property body Free-form text of the entry. May be empty.
 * @property timestamp Creation time in epoch milliseconds (the same unit as
 *   [System.currentTimeMillis]). Editing an entry keeps its original timestamp.
 */
data class JournalEntry(
    val id: Int,
    val title: String,
    val body: String,
    val timestamp: Long,
)
