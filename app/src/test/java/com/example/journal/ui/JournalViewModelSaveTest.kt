package com.example.journal.ui

import com.example.journal.data.NEW_ENTRY_ID
import com.example.journal.model.JournalEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [JournalViewModel.save], the single entry point the Edit screen uses.
 * It must pick between create, update and "invalid" and report which one happened.
 */
class JournalViewModelSaveTest {

    private val first = JournalEntry(1, "First", "body one", 1_000L)
    private val second = JournalEntry(2, "Second", "body two", 2_000L)

    /** Fake clock returns a fixed time so new entries have a predictable timestamp. */
    private fun viewModel() = JournalViewModel(listOf(first, second)) { 9_999L }

    // ---- new entry (-1) ----

    @Test
    fun save_newEntryId_returnsCreatedWithNextId() {
        val vm = viewModel()
        assertEquals(SaveOutcome.Created(3), vm.save(NEW_ENTRY_ID, "Hello", "World"))
    }

    @Test
    fun save_newEntryId_storesEntryWithClockTimestamp() {
        val vm = viewModel()
        vm.save(NEW_ENTRY_ID, "Hello", "World")
        assertEquals(JournalEntry(3, "Hello", "World", 9_999L), vm.entryById(3))
        assertEquals(3, vm.uiState.value.entries.size)
    }

    @Test
    fun save_newEntryId_setsCreatedMessage() {
        val vm = viewModel()
        vm.save(NEW_ENTRY_ID, "Hello", "World")
        assertEquals(UserMessage.ENTRY_CREATED, vm.uiState.value.message)
    }

    // ---- existing entry ----

    @Test
    fun save_existingId_returnsUpdated() {
        val vm = viewModel()
        assertEquals(SaveOutcome.Updated, vm.save(1, "Changed", "new body"))
    }

    @Test
    fun save_existingId_appliesChangeAndKeepsTimestamp() {
        val vm = viewModel()
        vm.save(1, "Changed", "new body")
        // Same id and original timestamp (1_000), not the clock's 9_999.
        assertEquals(JournalEntry(1, "Changed", "new body", 1_000L), vm.entryById(1))
        assertEquals(2, vm.uiState.value.entries.size)
        assertEquals(second, vm.entryById(2))
    }

    @Test
    fun save_existingId_setsUpdatedMessage() {
        val vm = viewModel()
        vm.save(1, "Changed", "")
        assertEquals(UserMessage.ENTRY_UPDATED, vm.uiState.value.message)
    }

    // ---- missing id (e.g. app data rebuilt after process death) ----

    @Test
    fun save_missingId_returnsCreatedWithFreshId() {
        val vm = viewModel()
        val outcome = vm.save(999, "Draft", "typed text")
        // The new id comes from max + 1 (3), not from the stale 999.
        assertEquals(SaveOutcome.Created(3), outcome)
    }

    @Test
    fun save_missingId_keepsDraftAsNewEntry() {
        val vm = viewModel()
        vm.save(999, "Draft", "typed text")
        assertEquals(JournalEntry(3, "Draft", "typed text", 9_999L), vm.entryById(3))
        assertNull(vm.entryById(999))
        assertEquals(3, vm.uiState.value.entries.size)
    }

    @Test
    fun save_missingId_setsCreatedMessage() {
        val vm = viewModel()
        vm.save(999, "Draft", "typed text")
        assertEquals(UserMessage.ENTRY_CREATED, vm.uiState.value.message)
    }

    @Test
    fun save_missingId_leavesExistingEntriesUntouched() {
        val vm = viewModel()
        vm.save(999, "Draft", "")
        assertEquals(first, vm.entryById(1))
        assertEquals(second, vm.entryById(2))
    }

    @Test
    fun save_idDeletedBeforeSave_createsNewEntryInstead() {
        val vm = viewModel()
        vm.deleteEntry(2)
        // Editing entry 2 after it was deleted: max + 1 is now 2 again, which is a valid fresh id.
        assertEquals(SaveOutcome.Created(2), vm.save(2, "Revived", "text"))
        assertEquals("Revived", vm.entryById(2)!!.title)
    }

    // ---- blank title ----

    @Test
    fun save_blankTitleNewEntry_returnsInvalidAndChangesNothing() {
        val vm = viewModel()
        val before = vm.uiState.value
        assertEquals(SaveOutcome.Invalid, vm.save(NEW_ENTRY_ID, "", "body"))
        assertEquals(before, vm.uiState.value)
    }

    @Test
    fun save_whitespaceTitleNewEntry_returnsInvalidAndChangesNothing() {
        val vm = viewModel()
        val before = vm.uiState.value
        assertEquals(SaveOutcome.Invalid, vm.save(NEW_ENTRY_ID, " \t\n ", "body"))
        assertEquals(before, vm.uiState.value)
    }

    @Test
    fun save_blankTitleExistingId_returnsInvalidAndChangesNothing() {
        val vm = viewModel()
        val before = vm.uiState.value
        assertEquals(SaveOutcome.Invalid, vm.save(1, "   ", "new body"))
        assertEquals(before, vm.uiState.value)
        assertEquals(first, vm.entryById(1))
    }

    @Test
    fun save_blankTitleMissingId_returnsInvalidAndChangesNothing() {
        val vm = viewModel()
        val before = vm.uiState.value
        assertEquals(SaveOutcome.Invalid, vm.save(999, "", "body"))
        assertEquals(before, vm.uiState.value)
    }

    @Test
    fun save_blankTitleWithPendingMessage_leavesMessageUnchanged() {
        val vm = viewModel()
        vm.save(NEW_ENTRY_ID, "A", "")           // queues ENTRY_CREATED
        vm.save(1, "  ", "x")                    // invalid: must not replace or clear it
        assertEquals(UserMessage.ENTRY_CREATED, vm.uiState.value.message)
    }
}
