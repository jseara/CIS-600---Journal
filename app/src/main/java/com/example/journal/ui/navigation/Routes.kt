package com.example.journal.ui.navigation

import com.example.journal.data.NEW_ENTRY_ID

/**
 * Every route in the app, in one place, so no screen has to build route strings by hand.
 *
 * A route is like a URL for a screen. Parts in `{braces}` are placeholders that Navigation fills
 * with arguments: a path segment (`detail/{entryId}`) is required, while a query parameter
 * (`edit?entryId={entryId}`) is optional and can have a default value.
 */
object Routes {
    /** Name of the argument that carries an entry id to Detail and Edit. */
    const val ARG_ENTRY_ID = "entryId"

    /** The start destination: the grouped list of entries. */
    const val LIST = "list"

    /** Detail always needs an id, so it is a required path argument. */
    const val DETAIL = "detail/{$ARG_ENTRY_ID}"

    /**
     * Edit uses ONE route for both modes. The id is an optional query argument whose default
     * is [NEW_ENTRY_ID] (-1): `edit` means "new entry", `edit?entryId=3` means "edit entry 3".
     */
    const val EDIT = "edit?$ARG_ENTRY_ID={$ARG_ENTRY_ID}"

    /** Builds the concrete route for the Detail screen of entry [id]. */
    fun detail(id: Int): String = "detail/$id"

    /** Builds the Edit route; leave [id] out (or pass [NEW_ENTRY_ID]) to create a new entry. */
    fun edit(id: Int = NEW_ENTRY_ID): String =
        if (id == NEW_ENTRY_ID) "edit" else "edit?$ARG_ENTRY_ID=$id"
}
