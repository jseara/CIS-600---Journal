package com.example.journal.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.journal.R
import com.example.journal.data.groupEntries
import com.example.journal.data.seedEntries
import com.example.journal.data.startOfDay
import com.example.journal.model.EntryGroup
import com.example.journal.model.EntrySection
import com.example.journal.model.JournalEntry
import com.example.journal.ui.UserMessage
import com.example.journal.ui.common.SnackbarMessageEffect
import com.example.journal.ui.common.rememberDateFormatter
import com.example.journal.ui.theme.JournalTheme
import java.util.TimeZone

/**
 * The start destination: every entry, grouped under Today / This Week / Older headers.
 *
 * Like all screens in this app it is stateless: it receives data and reports clicks through
 * lambdas, and never sees the NavController or the ViewModel. That keeps it easy to preview and
 * means navigation decisions all live in one place (`JournalNavHost`).
 *
 * @param entries All entries; this screen groups and sorts them.
 * @param message A pending snackbar message to show once (e.g. after creating an entry).
 * @param onMessageShown Called when [message] has been handed to the snackbar.
 * @param onEntryClick Called with the tapped entry's id.
 * @param onAddClick Called when the floating action button is tapped.
 */
@OptIn(ExperimentalMaterial3Api::class) // TopAppBar is still marked experimental in Material 3.
@Composable
fun ListScreen(
    entries: List<JournalEntry>,
    message: UserMessage?,
    onMessageShown: () -> Unit,
    onEntryClick: (Int) -> Unit,
    onAddClick: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    // The scroll position lives here (not inside EntryList) so the message effect below can
    // scroll it. rememberLazyListState is saveable, so the position also survives rotation.
    val listState = rememberLazyListState()

    SnackbarMessageEffect(
        message = message,
        snackbarHostState = snackbarHostState,
        onMessageShown = onMessageShown,
        onMessageReceived = { received ->
            // A new entry is always the newest, so it sits at the top of Today. If the list was
            // scrolled down before tapping +, scroll back up so the user can see what they saved.
            if (received == UserMessage.ENTRY_CREATED) listState.animateScrollToItem(0)
        },
    )

    // "Now" for grouping. It is refreshed every time this screen resumes (returning from another
    // screen, or bringing the app back to the foreground), so leaving the app open overnight
    // doesn't keep yesterday's entries under "Today".
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { now = System.currentTimeMillis() }

    // Grouping only depends on which *day* "now" is in, so we key on the start of that day:
    // resuming later the same day reuses the cached sections, and a new day regroups them.
    // Entries saved after `now` was read count as Today, because future timestamps do.
    val timeZone = TimeZone.getDefault()
    val today = startOfDay(now, timeZone)
    val sections = remember(entries, today, timeZone) { groupEntries(entries, now, timeZone) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.title_list)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_new_entry))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (sections.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.list_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            EntryList(sections, listState, onEntryClick, Modifier.padding(innerPadding))
        }
    }
}

/** The scrolling list: a header item followed by one card per entry, for each section. */
@Composable
private fun EntryList(
    sections: List<EntrySection>,
    listState: LazyListState,
    onEntryClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val formatDate = rememberDateFormatter()
    val spacing = dimensionResource(R.dimen.spacing_medium)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        // Extra bottom padding lets the last card scroll above the floating + button.
        contentPadding = PaddingValues(
            start = spacing,
            top = spacing,
            end = spacing,
            bottom = dimensionResource(R.dimen.fab_clearance),
        ),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_small)),
    ) {
        sections.forEach { section ->
            // Keys let LazyColumn keep track of items when the list changes, so it can animate
            // and preserve scroll position correctly. Header keys are strings so they can never
            // collide with the Int keys of entries.
            item(key = section.group.name) {
                SectionHeader(section.group)
            }
            items(section.entries, key = { it.id }) { entry ->
                EntryCard(entry, formatDate(entry.timestamp), onClick = { onEntryClick(entry.id) })
            }
        }
    }
}

/** A section title such as "Today", styled from the theme. */
@Composable
private fun SectionHeader(group: EntryGroup) {
    val text = when (group) {
        EntryGroup.TODAY -> R.string.section_today
        EntryGroup.THIS_WEEK -> R.string.section_this_week
        EntryGroup.OLDER -> R.string.section_older
    }
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        // Marking the header as a heading lets TalkBack users jump between sections.
        modifier = Modifier
            .padding(top = dimensionResource(R.dimen.spacing_small))
            .semantics { heading() },
    )
}

/** One tappable entry: title, a one-line body preview cut off with "…", and the date. */
@Composable
private fun EntryCard(entry: JournalEntry, formattedDate: String, onClick: () -> Unit) {
    // This Card overload takes onClick, which also gives it the ripple and accessibility role.
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(dimensionResource(R.dimen.spacing_medium)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_xsmall)),
        ) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.body.isNotBlank()) {
                Text(
                    text = entry.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = formattedDate,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ListScreenPreview() {
    JournalTheme {
        ListScreen(
            entries = seedEntries(System.currentTimeMillis(), TimeZone.getDefault()),
            message = null,
            onMessageShown = {},
            onEntryClick = {},
            onAddClick = {},
        )
    }
}
