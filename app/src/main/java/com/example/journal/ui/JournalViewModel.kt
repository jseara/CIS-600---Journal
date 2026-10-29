package com.example.journal.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.journal.data.NEW_ENTRY_ID
import com.example.journal.data.nextEntryId
import com.example.journal.data.seedEntries
import com.example.journal.model.JournalEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.TimeZone

/**
 * A one-shot message that one screen asks another screen to show in its Snackbar.
 *
 * Each screen has its own Scaffold and SnackbarHost, so the Edit screen cannot show a snackbar on
 * the List or Detail screen directly. Instead it leaves one of these in [JournalUiState.message];
 * the screen the user lands on shows it and then calls [JournalViewModel.messageShown] to clear it.
 *
 * It is an enum (not a string) so the ViewModel stays free of Android resources; the UI maps each
 * value to a string in `strings.xml`.
 */
enum class UserMessage {
    /** A new entry was created. Shown on the List screen. */
    ENTRY_CREATED,

    /** An existing entry was edited. Shown on the Detail screen. */
    ENTRY_UPDATED,
}

/**
 * What happened when the Edit screen's Save button was pressed; returned by [JournalViewModel.save].
 *
 * A `sealed interface` means these are the only possible outcomes, so a `when` over a
 * [SaveOutcome] must handle all of them, and the compiler tells us if a new one is ever added.
 */
sealed interface SaveOutcome {
    /** A new entry was added with this [id]. The UI should return to the List screen. */
    data class Created(val id: Int) : SaveOutcome

    /** An existing entry was changed. The UI should return to its Detail screen. */
    data object Updated : SaveOutcome

    /** The title was blank, so nothing was saved. The UI should stay on the Edit screen. */
    data object Invalid : SaveOutcome
}

/**
 * Everything the three screens need, as one immutable value.
 *
 * @property entries All entries, in no particular order (the List screen sorts and groups them).
 * @property message A pending snackbar message, or null when there is nothing to show.
 */
data class JournalUiState(
    val entries: List<JournalEntry> = emptyList(),
    val message: UserMessage? = null,
)

/**
 * The single source of truth for the journal, shared by the List, Detail and Edit screens.
 *
 * The data is an in-memory list (the assignment asks for that instead of a database), so it lives
 * as long as this ViewModel: it survives rotation but is reset when the process is killed.
 *
 * All updates are synchronous: [MutableStateFlow.update] replaces the state atomically on the
 * calling thread. No coroutines are launched, so the class can be unit-tested with plain JUnit.
 *
 * @param initialEntries The entries to start with (seed data in the app, anything in tests).
 * @param clock Returns the current time in epoch millis. Injected so tests can control the
 *   timestamp that new entries receive.
 */
class JournalViewModel(
    initialEntries: List<JournalEntry>,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    // The "backing property" pattern: a private mutable flow that only this class can change,
    // exposed to everyone else as a read-only StateFlow.
    private val _uiState = MutableStateFlow(JournalUiState(entries = initialEntries))

    /** The current state. Compose screens collect this and redraw whenever it changes. */
    val uiState: StateFlow<JournalUiState> = _uiState.asStateFlow()

    /** Returns the entry with [id], or null if there is none (for example, after it was deleted). */
    fun entryById(id: Int): JournalEntry? = _uiState.value.entries.find { it.id == id }

    /**
     * Creates a new entry stamped with the current time and queues [UserMessage.ENTRY_CREATED].
     *
     * @return The new entry's id, or null if [title] is blank (nothing is saved in that case).
     *   The UI already disables Save for a blank title; this check is a second line of defence.
     */
    fun addEntry(title: String, body: String): Int? {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return null
        var newId = 0
        _uiState.update { state ->
            newId = nextEntryId(state.entries)
            val entry = JournalEntry(newId, cleanTitle, body, clock())
            state.copy(entries = state.entries + entry, message = UserMessage.ENTRY_CREATED)
        }
        return newId
    }

    /**
     * Replaces the title and body of the entry with [id] and queues [UserMessage.ENTRY_UPDATED].
     * The original timestamp is kept, so editing does not move an entry to "Today".
     *
     * @return true if the entry was updated; false if [title] is blank or no entry has that id.
     */
    fun updateEntry(id: Int, title: String, body: String): Boolean {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty() || entryById(id) == null) return false
        _uiState.update { state ->
            state.copy(
                entries = state.entries.map { entry ->
                    if (entry.id == id) entry.copy(title = cleanTitle, body = body) else entry
                },
                message = UserMessage.ENTRY_UPDATED,
            )
        }
        return true
    }

    /**
     * Saves the Edit screen's form, deciding by itself whether that means "add" or "update".
     *
     * Keeping this decision here (instead of in the navigation code) puts the rule in one place
     * and lets it be unit-tested without any UI.
     *
     * - [entryId] is [NEW_ENTRY_ID]: adds a new entry and returns [SaveOutcome.Created].
     * - An entry with [entryId] exists: updates it and returns [SaveOutcome.Updated].
     * - [entryId] is not [NEW_ENTRY_ID] but no such entry exists (for example, the in-memory data
     *   was rebuilt after the system killed the app): adds the text as a new entry and returns
     *   [SaveOutcome.Created], so the user's typing is never thrown away.
     * - [title] is blank: changes nothing and returns [SaveOutcome.Invalid].
     *
     * It delegates to [addEntry] and [updateEntry], so the snackbar message is queued the same way.
     */
    fun save(entryId: Int, title: String, body: String): SaveOutcome {
        if (title.isBlank()) return SaveOutcome.Invalid
        if (entryId != NEW_ENTRY_ID && updateEntry(entryId, title, body)) return SaveOutcome.Updated
        // addEntry only returns null for a blank title, which was ruled out above, so this fallback
        // should never run; it just avoids a !!. (`?:`, the "Elvis operator", uses the right side
        // when the left is null.)
        val newId = addEntry(title, body) ?: return SaveOutcome.Invalid
        return SaveOutcome.Created(newId)
    }

    /** Removes the entry with [id]. Does nothing if there is no such entry. */
    fun deleteEntry(id: Int) {
        _uiState.update { state -> state.copy(entries = state.entries.filterNot { it.id == id }) }
    }

    /** Clears the pending snackbar message once a screen has started showing it. */
    fun messageShown() {
        _uiState.update { it.copy(message = null) }
    }

    companion object {
        /**
         * Tells `viewModel()` how to build this ViewModel, because its constructor takes
         * arguments and the default factory can only call a no-argument constructor.
         * The app starts with seed entries placed relative to the current time.
         */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                JournalViewModel(seedEntries(System.currentTimeMillis(), TimeZone.getDefault()))
            }
        }
    }
}
