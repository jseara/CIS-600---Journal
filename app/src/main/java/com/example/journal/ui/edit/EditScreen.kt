package com.example.journal.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import com.example.journal.R
import com.example.journal.model.JournalEntry
import com.example.journal.ui.common.BackButton
import com.example.journal.ui.theme.JournalTheme

/** Minimum visible lines for the body field, so it looks like a writing area rather than one line. */
private const val BODY_MIN_LINES = 6

/**
 * One screen for both creating and editing an entry.
 *
 * Which mode we are in is decided by [entry]: null means "new entry", otherwise the fields start
 * with that entry's text. The screen only reports the typed text through [onSave]; the caller
 * decides whether that means "add" or "update" and where to navigate afterwards.
 *
 * @param entry The entry being edited, or null when creating a new one.
 * @param onBack Called for the top-bar back arrow (discards changes).
 * @param onSave Called with the title and body when Save is tapped. Only possible when the
 *   title is not blank.
 */
@OptIn(ExperimentalMaterial3Api::class) // TopAppBar is still marked experimental in Material 3.
@Composable
fun EditScreen(
    entry: JournalEntry?,
    onBack: () -> Unit,
    onSave: (title: String, body: String) -> Unit,
) {
    // The text being typed is screen-local UI state, not app data, so it lives here rather than
    // in the ViewModel. rememberSaveable keeps it across rotation. Keying on the id resets the
    // fields if this composable is ever reused for a different entry.
    var title by rememberSaveable(entry?.id) { mutableStateOf(entry?.title.orEmpty()) }
    var body by rememberSaveable(entry?.id) { mutableStateOf(entry?.body.orEmpty()) }
    // Don't shout "Title is required" at a brand-new empty form; only after the user has typed.
    var titleTouched by rememberSaveable(entry?.id) { mutableStateOf(false) }

    val titleBlank = title.isBlank()
    val showTitleError = titleBlank && titleTouched

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (entry == null) R.string.title_new_entry else R.string.title_edit_entry))
                },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // padding() does not tell children that these insets are already handled, and the
                // keyboard (IME) inset already includes the navigation bar. Consuming innerPadding
                // stops imePadding from adding that bottom space a second time.
                .consumeWindowInsets(innerPadding)
                // imePadding moves content above the on-screen keyboard (works with edge-to-edge).
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(dimensionResource(R.dimen.spacing_medium)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_medium)),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    titleTouched = true
                },
                label = { Text(stringResource(R.string.field_title)) },
                singleLine = true,
                isError = showTitleError,
                // supportingText shows a hint line under the field; we use it for the error.
                supportingText = if (showTitleError) {
                    { Text(stringResource(R.string.error_title_required)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text(stringResource(R.string.field_body)) },
                minLines = BODY_MIN_LINES,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onSave(title, body) },
                // Save is disabled while the title is blank, so a blank entry can never be saved.
                enabled = !titleBlank,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EditScreenPreview() {
    JournalTheme {
        EditScreen(entry = null, onBack = {}, onSave = { _, _ -> })
    }
}
