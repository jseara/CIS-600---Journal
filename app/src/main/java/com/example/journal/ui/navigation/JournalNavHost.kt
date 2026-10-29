package com.example.journal.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.journal.data.NEW_ENTRY_ID
import com.example.journal.ui.JournalViewModel
import com.example.journal.ui.SaveOutcome
import com.example.journal.ui.detail.DetailScreen
import com.example.journal.ui.edit.EditScreen
import com.example.journal.ui.list.ListScreen

/**
 * The root of the app's UI: one NavController, one shared ViewModel, and one NavHost.
 *
 * This is the only place that knows about navigation. Screens get plain lambdas such as
 * `onEntryClick: (Int) -> Unit`, and this function turns those into `navigate`/`popBackStack`
 * calls. That keeps the screens reusable and makes the whole back-stack design readable here.
 *
 * Back stack in practice:
 * - List → Detail → (back) → List.
 * - List → Detail → Edit → Save: Edit is popped, so the user lands on Detail; back → List.
 * - List → (FAB) Edit → Save: we pop back to List, where the new entry appears.
 * - Detail → Delete: we pop back to List.
 * Because Save and Delete *pop* instead of navigating forward, Edit is never left in the back
 * stack, so pressing back on Detail can never return to the Edit form.
 *
 * @param journalViewModel The shared state holder. It is created here, outside the NavHost, so it
 *   is scoped to the Activity and all three destinations see the same instance and the same data.
 */
@Composable
fun JournalNavHost(
    journalViewModel: JournalViewModel = viewModel(factory = JournalViewModel.Factory),
) {
    // Created once at the top level. `remember` inside it keeps the same controller across
    // recompositions, and it saves/restores the back stack across rotation.
    val navController = rememberNavController()
    // collectAsStateWithLifecycle stops collecting while the app is in the background.
    val uiState by journalViewModel.uiState.collectAsStateWithLifecycle()

    NavHost(navController = navController, startDestination = Routes.LIST) {
        composable(Routes.LIST) { backStackEntry ->
            ListScreen(
                entries = uiState.entries,
                message = uiState.message,
                onMessageShown = journalViewModel::messageShown,
                onEntryClick = { id ->
                    backStackEntry.ifResumed { navController.navigate(Routes.detail(id)) }
                },
                onAddClick = {
                    backStackEntry.ifResumed { navController.navigate(Routes.edit()) }
                },
            )
        }

        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument(Routes.ARG_ENTRY_ID) { type = NavType.IntType }),
        ) { backStackEntry ->
            val entryId = backStackEntry.entryIdArg()
            DetailScreen(
                // Look the entry up from the live state, so an edit shows up immediately.
                entry = uiState.entries.find { it.id == entryId },
                message = uiState.message,
                onMessageShown = journalViewModel::messageShown,
                onBack = { backStackEntry.ifResumed { navController.popBackStack() } },
                onEditClick = { id ->
                    backStackEntry.ifResumed { navController.navigate(Routes.edit(id)) }
                },
                onDeleteConfirmed = { id ->
                    backStackEntry.ifResumed {
                        // Pop back to List, then delete. Both happen before the next frame;
                        // DetailScreen keeps showing its last entry during the exit animation,
                        // so there is no "not found" flash.
                        // popBackStack(LIST, inclusive = false) removes everything above List.
                        navController.popBackStack(Routes.LIST, inclusive = false)
                        journalViewModel.deleteEntry(id)
                    }
                },
            )
        }

        composable(
            route = Routes.EDIT,
            arguments = listOf(
                navArgument(Routes.ARG_ENTRY_ID) {
                    type = NavType.IntType
                    defaultValue = NEW_ENTRY_ID
                },
            ),
        ) { backStackEntry ->
            val entryId = backStackEntry.entryIdArg()
            EditScreen(
                // null (a "New entry" form) both for the new-entry route and for an id whose entry
                // no longer exists; in the second case save() also creates a new entry.
                entry = uiState.entries.find { it.id == entryId },
                onBack = { backStackEntry.ifResumed { navController.popBackStack() } },
                onSave = { title, body ->
                    backStackEntry.ifResumed {
                        // The ViewModel decides add vs. update; here we only pick where to go next.
                        when (journalViewModel.save(entryId, title, body)) {
                            // New entries are shown on List, so pop everything above it.
                            is SaveOutcome.Created ->
                                navController.popBackStack(Routes.LIST, inclusive = false)
                            // Edit was opened from Detail: popping lands on Detail with the change.
                            SaveOutcome.Updated -> navController.popBackStack()
                            // Blank title: stay on the form (Save is normally disabled anyway).
                            SaveOutcome.Invalid -> Unit
                        }
                    }
                },
            )
        }
    }
}

/** Reads the `entryId` Int argument; Navigation has already parsed it because of `NavType.IntType`. */
private fun NavBackStackEntry.entryIdArg(): Int =
    arguments?.getInt(Routes.ARG_ENTRY_ID, NEW_ENTRY_ID) ?: NEW_ENTRY_ID

/**
 * Runs [action] only while this destination is the active (RESUMED) one.
 *
 * During a screen transition the old screen is still visible and clickable for a few frames.
 * Without this guard a quick double-tap could navigate twice (two Detail screens on the stack) or
 * pop twice (skipping past List). Once navigation starts, the old entry is no longer RESUMED, so
 * the second tap is ignored.
 */
private inline fun NavBackStackEntry.ifResumed(action: () -> Unit) {
    if (lifecycle.currentState == Lifecycle.State.RESUMED) action()
}
