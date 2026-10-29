package com.example.journal.ui.navigation

import com.example.journal.data.NEW_ENTRY_ID
import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for the route strings produced by [Routes]. */
class RoutesTest {

    @Test
    fun detail_givenId_buildsPathRoute() {
        assertEquals("detail/7", Routes.detail(7))
    }

    @Test
    fun edit_noArgument_buildsNewEntryRoute() {
        assertEquals("edit", Routes.edit())
    }

    @Test
    fun edit_newEntrySentinel_buildsNewEntryRoute() {
        assertEquals("edit", Routes.edit(NEW_ENTRY_ID))
    }

    @Test
    fun edit_givenId_buildsQueryRoute() {
        assertEquals("edit?entryId=3", Routes.edit(3))
    }

    @Test
    fun patterns_matchTheArgumentName() {
        // The builders and the NavHost patterns must agree on the argument name.
        assertEquals("entryId", Routes.ARG_ENTRY_ID)
        assertEquals("detail/{entryId}", Routes.DETAIL)
        assertEquals("edit?entryId={entryId}", Routes.EDIT)
    }

}
