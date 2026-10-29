package com.example.journal.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.example.journal.R
import com.example.journal.model.JournalEntry
import com.example.journal.ui.UserMessage
import com.example.journal.ui.common.BackButton
import com.example.journal.ui.common.SnackbarMessageEffect
import com.example.journal.ui.common.rememberDateFormatter
import com.example.journal.ui.theme.JournalTheme

/**
 * Shows one entry in full, with Edit and Delete actions in the top bar.
 *
 * @param entry The entry looked up from the navigation argument, or null if no entry has that id.
 * @param message A pending snackbar message to show once (e.g. after editing this entry).
 * @param onMessageShown Called when [message] has been handed to the snackbar.
 * @param onBack Called for the top-bar back arrow.
 * @param onEditClick Called with the entry's id when the Edit action is tapped.
 * @param onDeleteConfirmed Called with the entry's id after the user confirms the delete dialog.
 */
@OptIn(ExperimentalMaterial3Api::class) // TopAppBar is still marked experimental in Material 3.
@Composable
fun DetailScreen(
    entry: JournalEntry?,
    message: UserMessage?,
    onMessageShown: () -> Unit,
    onBack: () -> Unit,
    onEditClick: (Int) -> Unit,
    onDeleteConfirmed: (Int) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    SnackbarMessageEffect(message, snackbarHostState, onMessageShown)

    // Keep showing the last entry we had. After a delete this screen stays on screen for a moment
    // while the exit animation plays; without this it would flash "entry not found" first.
    // (Assigning to a `remember`ed holder during composition is safe here because it only ever
    // stores the same value the caller just passed in.)
    val lastEntry = remember { arrayOfNulls<JournalEntry>(1) }
    if (entry != null) lastEntry[0] = entry
    val shown = entry ?: lastEntry[0]

    // rememberSaveable keeps the dialog open across rotation.
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_detail)) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    // Only offer actions when there is an entry to act on.
                    if (shown != null) {
                        IconButton(onClick = { onEditClick(shown.id) }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit))
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (shown == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.entry_not_found),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            EntryContent(shown, Modifier.padding(innerPadding))
        }
    }

    if (showDeleteDialog && shown != null) {
        AlertDialog(
            // Tapping outside the dialog or pressing back just closes it, like Cancel.
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_dialog_title)) },
            text = { Text(stringResource(R.string.delete_dialog_text, shown.title)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDeleteConfirmed(shown.id)
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/** Title, date and body of an entry; the column scrolls so long entries are fully readable. */
@Composable
private fun EntryContent(entry: JournalEntry, modifier: Modifier = Modifier) {
    val formatDate = rememberDateFormatter()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(dimensionResource(R.dimen.spacing_medium)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_small)),
    ) {
        Text(text = entry.title, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = formatDate(entry.timestamp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = entry.body, style = MaterialTheme.typography.bodyLarge)
    }
}

@Preview(showBackground = true)
@Composable
private fun DetailScreenPreview() {
    JournalTheme {
        DetailScreen(
            entry = JournalEntry(1, "Morning run", "Did 5k around the lake before class.", System.currentTimeMillis()),
            message = null,
            onMessageShown = {},
            onBack = {},
            onEditClick = {},
            onDeleteConfirmed = {},
        )
    }
}
