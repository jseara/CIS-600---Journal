package com.example.journal.model

/**
 * The three sections the List screen groups entries into, in the order they are displayed.
 *
 * The exact boundaries are defined by [com.example.journal.data.groupEntries].
 */
enum class EntryGroup {
    /** Same calendar day as "now" (also used for timestamps in the future, e.g. after a clock change). */
    TODAY,

    /** One of the six calendar days before today, so Today + This Week cover the last 7 days. */
    THIS_WEEK,

    /** Anything before This Week. */
    OLDER,
}

/**
 * One header plus its entries, ready for the List screen to render.
 *
 * @property group Which section this is; the UI maps it to a header string.
 * @property entries The entries in this section, newest first. Never empty.
 */
data class EntrySection(
    val group: EntryGroup,
    val entries: List<JournalEntry>,
)
