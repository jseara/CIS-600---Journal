package com.example.journal.ui

import com.example.journal.model.JournalEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [JournalViewModel]. The ViewModel updates its state synchronously, so tests
 * read `uiState.value` directly (no coroutine test library needed), and a fake clock
 * (a lambda returning a fixed number) controls the timestamps of new entries.
 */
class JournalViewModelTest {

    private val original = JournalEntry(1, "First", "body one", 1_000L)
    private val second = JournalEntry(2, "Second", "body two", 2_000L)

    /** Builds a ViewModel with a controllable clock; [time] is read each time the clock ticks. */
    private fun viewModel(
        entries: List<JournalEntry> = listOf(original, second),
        time: () -> Long = { 9_999L },
    ) = JournalViewModel(entries, time)

    // ---- initial state ----

    @Test
    fun init_exposesInitialEntriesAndNoMessage() {
        val vm = viewModel()
        assertEquals(listOf(original, second), vm.uiState.value.entries)
        assertNull(vm.uiState.value.message)
    }

    // ---- entryById ----

    @Test
    fun entryById_existingId_returnsEntry() {
        assertEquals(second, viewModel().entryById(2))
    }

    @Test
    fun entryById_unknownId_returnsNull() {
        assertNull(viewModel().entryById(99))
    }

    // ---- addEntry ----

    @Test
    fun addEntry_validTitle_returnsNewIdAndStoresEntry() {
        val vm = viewModel()
        val id = vm.addEntry("Hello", "World")
        assertEquals(3, id)
        assertEquals(JournalEntry(3, "Hello", "World", 9_999L), vm.entryById(3))
        assertEquals(3, vm.uiState.value.entries.size)
    }

    @Test
    fun addEntry_validTitle_setsCreatedMessage() {
        val vm = viewModel()
        vm.addEntry("Hello", "")
        assertEquals(UserMessage.ENTRY_CREATED, vm.uiState.value.message)
    }

    @Test
    fun addEntry_usesClockForTimestamp() {
        var now = 100L
        val vm = viewModel(time = { now })
        val first = vm.addEntry("A", "")!!
        now = 200L
        val next = vm.addEntry("B", "")!!
        assertEquals(100L, vm.entryById(first)!!.timestamp)
        assertEquals(200L, vm.entryById(next)!!.timestamp)
    }

    @Test
    fun addEntry_trimsTitleButKeepsBodyUntouched() {
        val vm = viewModel()
        val id = vm.addEntry("  Padded  ", "  body with spaces \n")!!
        assertEquals("Padded", vm.entryById(id)!!.title)
        assertEquals("  body with spaces \n", vm.entryById(id)!!.body)
    }

    @Test
    fun addEntry_emptyBody_isAllowed() {
        val vm = viewModel()
        val id = vm.addEntry("Title only", "")
        assertNotNull(id)
        assertEquals("", vm.entryById(id!!)!!.body)
    }

    @Test
    fun addEntry_blankTitle_returnsNullAndChangesNothing() {
        val vm = viewModel()
        val before = vm.uiState.value
        assertNull(vm.addEntry("   ", "body"))
        assertNull(vm.addEntry("", "body"))
        assertEquals(before, vm.uiState.value)
    }

    @Test
    fun addEntry_onEmptyList_firstIdIsOne() {
        assertEquals(1, viewModel(emptyList()).addEntry("First", ""))
    }

    @Test
    fun addEntry_afterDeletingMiddleEntry_doesNotReuseExistingId() {
        val vm = viewModel(listOf(original, second, JournalEntry(3, "Third", "", 3_000L)))
        vm.deleteEntry(2)
        val id = vm.addEntry("New", "")
        assertEquals(4, id)
        assertEquals(listOf(1, 3, 4), vm.uiState.value.entries.map { it.id })
    }

    // ---- updateEntry ----

    @Test
    fun updateEntry_existingId_changesTitleAndBodyAndReturnsTrue() {
        val vm = viewModel()
        assertTrue(vm.updateEntry(1, "New title", "New body"))
        assertEquals("New title", vm.entryById(1)!!.title)
        assertEquals("New body", vm.entryById(1)!!.body)
    }

    @Test
    fun updateEntry_keepsOriginalTimestampAndId() {
        val vm = viewModel(time = { 123_456L })
        vm.updateEntry(1, "New title", "New body")
        // Whole entry: same id and original timestamp (1_000), not the clock's 123_456.
        assertEquals(JournalEntry(1, "New title", "New body", 1_000L), vm.entryById(1))
        assertEquals(listOf(1, 2), vm.uiState.value.entries.map { it.id })
    }

    @Test
    fun updateEntry_setsUpdatedMessage() {
        val vm = viewModel()
        vm.updateEntry(1, "New title", "")
        assertEquals(UserMessage.ENTRY_UPDATED, vm.uiState.value.message)
    }

    @Test
    fun updateEntry_trimsTitle() {
        val vm = viewModel()
        vm.updateEntry(1, "  Trimmed  ", "b")
        assertEquals("Trimmed", vm.entryById(1)!!.title)
    }

    @Test
    fun updateEntry_onlyAffectsTargetEntry() {
        val vm = viewModel()
        vm.updateEntry(1, "Changed", "x")
        assertEquals(second, vm.entryById(2))
    }

    @Test
    fun updateEntry_blankTitle_returnsFalseAndChangesNothing() {
        val vm = viewModel()
        val before = vm.uiState.value
        assertFalse(vm.updateEntry(1, "   ", "new body"))
        assertEquals(before, vm.uiState.value)
    }

    @Test
    fun updateEntry_unknownId_returnsFalseAndChangesNothing() {
        val vm = viewModel()
        val before = vm.uiState.value
        assertFalse(vm.updateEntry(42, "Title", "body"))
        assertEquals(before, vm.uiState.value)
    }

    @Test
    fun updateEntry_emptyBody_isAllowed() {
        val vm = viewModel()
        assertTrue(vm.updateEntry(1, "Title", ""))
        assertEquals("", vm.entryById(1)!!.body)
    }

    // ---- deleteEntry ----

    @Test
    fun deleteEntry_existingId_removesOnlyThatEntry() {
        val vm = viewModel()
        vm.deleteEntry(1)
        assertNull(vm.entryById(1))
        assertEquals(listOf(second), vm.uiState.value.entries)
    }

    @Test
    fun deleteEntry_unknownId_leavesEntriesUnchanged() {
        val vm = viewModel()
        vm.deleteEntry(99)
        assertEquals(listOf(original, second), vm.uiState.value.entries)
    }

    @Test
    fun deleteEntry_doesNotChangePendingMessage() {
        val vm = viewModel()
        vm.addEntry("A", "")
        vm.deleteEntry(1)
        assertEquals(UserMessage.ENTRY_CREATED, vm.uiState.value.message)
    }

    @Test
    fun deleteEntry_whenNoMessagePending_stillNoMessage() {
        val vm = viewModel()
        vm.deleteEntry(1)
        assertNull(vm.uiState.value.message)
    }

    // ---- messageShown ----

    @Test
    fun messageShown_clearsPendingMessage() {
        val vm = viewModel()
        vm.addEntry("A", "")
        vm.messageShown()
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun messageShown_doesNotTouchEntries() {
        val vm = viewModel()
        vm.addEntry("A", "")
        val entries = vm.uiState.value.entries
        vm.messageShown()
        assertEquals(entries, vm.uiState.value.entries)
    }

    @Test
    fun message_newerEventReplacesOlderMessage() {
        val vm = viewModel()
        vm.addEntry("A", "")
        vm.updateEntry(1, "B", "")
        assertEquals(UserMessage.ENTRY_UPDATED, vm.uiState.value.message)
    }
}
