package com.example.journal.ui.common

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.example.journal.R
import com.example.journal.ui.UserMessage
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Maps a [UserMessage] from the ViewModel to the string resource the snackbar displays. */
@StringRes
fun UserMessage.textRes(): Int = when (this) {
    UserMessage.ENTRY_CREATED -> R.string.message_entry_created
    UserMessage.ENTRY_UPDATED -> R.string.message_entry_updated
}

/**
 * Remembers a function that formats an epoch-millis timestamp as a medium date plus short time
 * in the user's locale, e.g. "Oct 27, 2026, 9:14 AM".
 *
 * `java.text.DateFormat` works on every API level (unlike `java.time`, which needs API 26).
 * Creating a formatter is relatively costly, so we `remember` one per locale instead of building
 * a new one on every recomposition.
 */
@Composable
fun rememberDateFormatter(): (Long) -> String {
    // Reading the configuration makes us pick up a new formatter if the user changes language.
    val locale = LocalConfiguration.current.locales[0]
    val format = remember(locale) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
    }
    return { timestamp -> format.format(Date(timestamp)) }
}

/**
 * Shows [message] in [snackbarHostState] once, then calls [onMessageShown] so it isn't shown again.
 *
 * Why the extra coroutine scope: we clear the message right away, which changes the key of the
 * `LaunchedEffect` and cancels it. If the snackbar ran inside the effect it would be cancelled
 * (dismissed) immediately. Launching it in the screen's own scope lets it keep running.
 *
 * @param onMessageReceived Optional extra work for the message (e.g. scrolling the list). It runs
 *   in its own coroutine next to the snackbar, so neither one cancels or waits for the other.
 */
@Composable
fun SnackbarMessageEffect(
    message: UserMessage?,
    snackbarHostState: SnackbarHostState,
    onMessageShown: () -> Unit,
    onMessageReceived: suspend (UserMessage) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    // stringResource must be called during composition, not inside the effect.
    val text = message?.let { stringResource(it.textRes()) }
    LaunchedEffect(message) {
        if (message != null && text != null) {
            scope.launch { snackbarHostState.showSnackbar(text) }
            scope.launch { onMessageReceived(message) }
            // Cleared exactly once, here; the two coroutines above keep running afterwards.
            onMessageShown()
        }
    }
}

/** The back arrow used in the top bar of the Detail and Edit screens. */
@Composable
fun BackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        // AutoMirrored flips the arrow to point right in right-to-left languages.
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.action_back),
        )
    }
}
