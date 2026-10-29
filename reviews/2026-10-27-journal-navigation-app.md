# Review: Three-screen journal app with Navigation Compose
**Date**: 2026-10-27 · **Status**: Decisions made 2026-10-29, all 9 recommended options approved · Emulator-tested 2026-10-29

## What was built
A journal app with three screens: **List** (entries grouped under Today / This Week / Older), **Detail** (full entry, Edit and Delete actions, delete asks for confirmation) and **Edit** (one form used for both new and existing entries). Everything is wired together in one place, `JournalNavHost`, which owns the single `NavController` and `NavHost`. The three screens are *stateless*: they receive plain data and lambdas such as `onEntryClick: (Int) -> Unit`, and never see the `NavController` or the ViewModel.

The data flow is: screen -> lambda -> `JournalNavHost` -> `JournalViewModel` (one instance shared by all screens, scoped to the Activity) -> an in-memory `List<JournalEntry>` held in a `StateFlow`. There is no repository or database, because the assignment asks for an in-memory list in a ViewModel. The seed data (10 entries) is built relative to "now", so all three sections are populated on every launch. Saving leaves a one-shot `UserMessage` in the ViewModel state so the screen you land on can show the "saved" snackbar.

## Code walkthrough

### `ui/navigation/Routes.kt` (34 lines)
All route strings in one object. Edit uses **one route with an optional query argument**:
```kotlin
const val DETAIL = "detail/{$ARG_ENTRY_ID}"                 // required path argument
const val EDIT = "edit?$ARG_ENTRY_ID={$ARG_ENTRY_ID}"        // optional, default -1
fun edit(id: Int = NEW_ENTRY_ID): String =
    if (id == NEW_ENTRY_ID) "edit" else "edit?$ARG_ENTRY_ID=$id"
```
Idiom: `{name}` in a route is a placeholder that Navigation fills in. A `/name` path segment is required; a `?name=` query parameter can have a default.

### `ui/navigation/JournalNavHost.kt` (138 lines)
The only file that knows about navigation. The ViewModel is created *outside* the `NavHost`, so it is scoped to the Activity and shared:
```kotlin
fun JournalNavHost(
    journalViewModel: JournalViewModel = viewModel(factory = JournalViewModel.Factory),
) {
    val navController = rememberNavController()
    val uiState by journalViewModel.uiState.collectAsStateWithLifecycle()
```
Save and Delete **pop** the back stack instead of navigating forward, so Edit never stays on it:
```kotlin
onSave = { title, body ->
    backStackEntry.ifResumed {
        if (isNew) {
            journalViewModel.addEntry(title, body)
            navController.popBackStack(Routes.LIST, inclusive = false)
        } else if (journalViewModel.updateEntry(entryId, title, body)) {
            navController.popBackStack()          // lands on Detail
        } else {
            navController.popBackStack(Routes.LIST, inclusive = false)   // entry vanished
        }
    }
},
```
`ifResumed` is a small guard that runs a navigation action only if this screen's `NavBackStackEntry` is in the `RESUMED` lifecycle state. During a transition the old screen is still tappable, so without it a quick double-tap could push Detail twice.

### `ui/JournalViewModel.kt` (133 lines)
Holds `JournalUiState(entries, message)` and exposes `addEntry`, `updateEntry`, `deleteEntry`, `entryById` and `messageShown`. Updates use `MutableStateFlow.update { ... }`, which is synchronous, so the class can be tested with plain JUnit (no coroutine test libraries). Idiom: the "backing property" pattern, a private `_uiState` that only the class can change, exposed as a read-only `uiState`.
```kotlin
fun updateEntry(id: Int, title: String, body: String): Boolean {
    val cleanTitle = title.trim()
    if (cleanTitle.isEmpty() || entryById(id) == null) return false
    ...
}
```
`Factory` (a `viewModelFactory { initializer { ... } }`) is needed because the constructor takes arguments.

### `data/EntryGrouping.kt`, `data/EntryIds.kt`, `data/SeedData.kt`
Pure functions that take `now` and a `TimeZone` as parameters, so tests can pass a fixed moment.
- `groupEntries` sorts newest first (ties broken by descending id), then groups into Today / This Week / Older and drops empty sections.
- "This Week" is the six calendar days before today (`WEEK_WINDOW_DAYS = 7` including today).
- `startOfDay` moves by calendar days with `Calendar.add(DAY_OF_MONTH, ...)`, not by subtracting 24 hours, so daylight-saving days of 23 or 25 hours still work.
- `nextEntryId` is `max id + 1` (not `size + 1`), so deleting never produces a duplicate id. `NEW_ENTRY_ID = -1` means "create a new entry".

### `model/JournalEntry.kt`, `model/EntryGroup.kt`
`JournalEntry(id, title, body, timestamp)` is an immutable `data class`; editing creates a `copy(...)`. `EntryGroup` is the three-value enum and `EntrySection` is one header plus its entries.

### `ui/list/ListScreen.kt` (195 lines)
A `Scaffold` with a FAB and a `LazyColumn` of `Card`s under section headers. Headers are marked `semantics { heading() }` so TalkBack can jump between them. Grouping is computed once per change of the entries list:
```kotlin
val sections = remember(entries) {
    groupEntries(entries, System.currentTimeMillis(), TimeZone.getDefault())
}
```
Idiom: `remember(key)` caches a value and recomputes it only when `key` changes.

### `ui/detail/DetailScreen.kt` (165 lines)
Shows the entry and the Edit and Delete icons. Delete opens an `AlertDialog`; the dialog's open state is `rememberSaveable` so it survives rotation. The screen keeps the last non-null entry it saw so that, after a delete, it does not flash "entry not found" during the exit animation.

### `ui/edit/EditScreen.kt` (136 lines)
One form for both modes. The draft text is screen-local state saved with `rememberSaveable` (survives rotation and process death). The mode is chosen from `entry == null`:
```kotlin
Text(stringResource(if (entry == null) R.string.title_new_entry else R.string.title_edit_entry))
```
Save is disabled while the title is blank. Keyboard handling uses `.consumeWindowInsets(innerPadding).imePadding()` so the bottom inset is not counted twice.

### `ui/common/CommonUi.kt` (80 lines)
`rememberDateFormatter` (`java.text.DateFormat`, medium date plus short time, per locale), `SnackbarMessageEffect` (shows a `UserMessage` once, then clears it) and `BackButton`. Idiom: the snackbar is launched in a separate `rememberCoroutineScope` because clearing the message changes the `LaunchedEffect` key, which would otherwise cancel (dismiss) the snackbar immediately.

### Build and resource files
- `gradle/libs.versions.toml` and `app/build.gradle.kts`: four new dependencies (see decision 5).
- `app/src/main/res/values/strings.xml`: all user-facing strings. `dimens.xml`: `spacing_xsmall/small/medium` (4/8/16 dp).
- `MainActivity.kt`: `enableEdgeToEdge()` and `JournalNavHost()` inside `JournalTheme`.
- `README.md`: route design, back-stack behaviour, decisions and open questions.

## Tests
**84 plain JUnit4 tests, all passing** (counts verified in the files): `EntryGroupingTest` 32, `JournalViewModelTest` 30, `SeedDataTest` 10, `EntryIdsTest` 6, `RoutesTest` 6. They cover day boundaries, daylight-saving changes, tie-breaks, id generation after deletes, seed distribution across the three sections, and every ViewModel method. `assembleDebug` passes. Lint: 0 errors, 11 warnings, all from the project template.

**Gaps**: `JournalViewModel.Factory`, the Compose screens, the save-routing logic inside `JournalNavHost`, and `UserMessage.textRes()` are not covered. Compose UI tests would need the instrumented-test setup (emulator), which is outside the plain-JUnit-only convention in CLAUDE.md. Decision 3 would make the save routing testable.

## Decisions needed

### 1. Root Gradle files and wrapper are untracked, template test is deleted — Architecture
> Owner: include `.claude/`, `CLAUDE.md` and `reviews/` in the commit.
- **Where**: repository root (`settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradlew`, `gradlew.bat`, `gradle/wrapper/`, `gradle/gradle-daemon-jvm.properties`); `app/src/test/java/com/example/journal/ExampleUnitTest.kt`
- **Context**: `git status` shows these as `??` (untracked), while `app/` files are already staged. A fresh clone of the repo would not build. `ExampleUnitTest.kt` is staged as added (`AD`) but the file has been deleted from disk, replaced by the real tests.
- **Options**:
  - **A (recommended)**: Stage all of the root Gradle files and the wrapper, and stage the deletion of `ExampleUnitTest.kt`. The submission then builds from a clean clone.
  - **Sub-decision**: should `.claude/`, `CLAUDE.md` and `reviews/` go in the commit or zip? Including them shows the agent workflow, but makes the submission less minimal for a grader. Excluding them keeps the repo to just the app, README and build files (they stay on disk, untracked).
- **Your decision**: A (recommended), approved 2026-10-29
- **Notes**: _

### 2. Edit opened for an id that no longer exists — Architecture
- **Where**: `ui/edit/EditScreen.kt:72`, `ui/navigation/JournalNavHost.kt:100-116`
- **Context**: `EditScreen` decides its title from `entry == null`, but the NavHost decides add vs update from `entryId == NEW_ENTRY_ID`. The two can disagree. Scenario: you add an entry (id 11), open it in Edit, and the process is killed. On restore the ViewModel is rebuilt from seed data (ids 1-10), the back stack and the `rememberSaveable` draft are restored, so the form says "New entry" with your draft text. Save calls `updateEntry(11, ...)`, which returns false, and the app pops to List. The draft is silently lost.
```kotlin
entry = if (isNew) null else uiState.entries.find { it.id == entryId },
...
} else if (journalViewModel.updateEntry(entryId, title, body)) {
    navController.popBackStack()
} else {
    // The entry vanished (e.g. state lost after process death): go home.
```
- **Options**:
  - **A (recommended)**: If the id is not -1 and the entry is missing, treat Save as `addEntry` so the draft is kept. Trade-off: the entry gets a new id and timestamp, and the title still says "New entry", which is arguably correct.
  - **B**: Show "This entry no longer exists" and hide Save. Honest, but the user loses the draft anyway, and it needs a new screen state and string.
  - **C**: Keep the current behaviour but title the form "Edit entry". Smallest change, but the text is still lost.
  - Whichever you choose, the README's "Missing entries" paragraph must be updated (it currently says Save "just returns to List").
- **Your decision**: A (recommended), approved 2026-10-29
- **Notes**: _

### 3. Save routing is inline in the NavHost and untested — Uncertain (reviewer confidence: medium)
- **Where**: `ui/navigation/JournalNavHost.kt:102-116`
- **Context**: The if/else-if/else that picks between add, update and "entry vanished" is the most branch-heavy logic in the app, and it sits inside a composable, so plain JUnit cannot reach it.
- **Options**:
  - **A**: Add `save(entryId, title, body): SaveOutcome` to the ViewModel, returning `Created / Updated / Missing / Invalid`, and have the NavHost only map the outcome to a pop. This makes the routing testable in JUnit and pairs naturally with decision 2. Trade-off: one new type and some refactoring.
  - **B (also reasonable)**: Leave it untested. The logic is small and the README documents it; the cost is that decision 2's behaviour has no automated check.
- **Your decision**: A (recommended), approved 2026-10-29
- **Notes**: _

### 4. List grouping goes stale across midnight — UX
- **Where**: `ui/list/ListScreen.kt:71-75`
- **Context**: `now` is read only when the entries list changes. If the app is left on List overnight, "Today" entries stay under "Today" until something changes the list or the screen is recreated.
```kotlin
val sections = remember(entries) {
    groupEntries(entries, System.currentTimeMillis(), TimeZone.getDefault())
}
```
- **Options**:
  - **A (recommended)**: Refresh `now` when the screen resumes (`LifecycleResumeEffect`) and key the `remember` on `(entries, startOfDay(now))`. Small change, fixes the real-world case (open app, lock phone, come back next day).
  - **B**: Pass `now` in from the NavHost or ViewModel. Cleaner for testing, but needs a clock tick or resume hook there as well.
  - **C**: Accept it as a documented limitation (it is already listed in the README).
- **Your decision**: A (recommended), approved 2026-10-29
- **Notes**: _

### 5. New dependencies — Architecture
- **Where**: `gradle/libs.versions.toml`, `app/build.gradle.kts:42-45`
- **Context**: CLAUDE.md says any addition is an architecture decision. Added: `navigation-compose` 2.10.2, `lifecycle-viewmodel-compose` 2.11.0, `lifecycle-runtime-compose` 2.11.0, and `material-icons-core` (version from the Compose BOM). Navigation Compose was already confirmed by you. The icons library is needed because Material 3 no longer includes icons; `viewmodel-compose` provides `viewModel()`; `runtime-compose` provides `collectAsStateWithLifecycle`.
- **Options**:
  - **A (recommended)**: Approve all four.
  - **Optional nit**: all three lifecycle libraries share the version key `lifecycleRuntimeKtx`; renaming it to `lifecycle` would read better. Cosmetic only.
- **Your decision**: A (recommended), approved 2026-10-29
- **Notes**: _

### 6. One shared ViewModel and no repository — Architecture
- **Where**: `ui/JournalViewModel.kt`, `ui/navigation/JournalNavHost.kt:39-40`
- **Context**: CLAUDE.md's proposed defaults are one ViewModel per screen and Room behind a repository interface. The assignment asks for "an in-memory list in a ViewModel", which takes precedence. A single ViewModel is also the simplest way for Edit to hand a snackbar message to List or Detail.
- **Options**:
  - **A (recommended)**: Approve for this assignment. Persistence can be added later behind a repository.
  - **B**: Per-screen ViewModels over a shared in-memory repository class. Closer to CLAUDE.md and the usual "real app" shape, but more files, and the snackbar hand-off becomes harder.
- **Your decision**: A (recommended), approved 2026-10-29
- **Notes**: _

### 7. The coder's design choices (approve together) — UX
- **Where**: `Routes.kt`, `JournalNavHost.kt`, `EntryGrouping.kt`, `JournalViewModel.kt`, `EditScreen.kt`, `README.md`
- **Context**: Choices that are documented in the README but that you have not confirmed:
  - routes: `list`, `detail/{entryId}`, `edit?entryId={entryId}` (one Edit route, default -1)
  - pop strategy: Save and Delete pop, so Edit is never left on the back stack
  - `ifResumed` double-tap guard on every navigation lambda
  - `Calendar` / `DateFormat` for dates (no `java.time` desugaring)
  - "This Week" = the six calendar days before today
  - editing keeps the original timestamp and trims the title (the body is saved as typed)
  - drafts live in `rememberSaveable`, not the ViewModel
  - one-shot snackbar via the ViewModel (rotating the device while it shows dismisses it)
  - Back from Edit discards the draft with no prompt
  - no "Entry deleted" snackbar
- **Options**:
  - **A (recommended)**: Approve as documented.
  - **Optional extras**: a "Discard changes?" dialog on Back from Edit, and/or an "Entry deleted" snackbar with Undo. Both go beyond the assignment and add UI and ViewModel state.
- **Your decision**: A (recommended), approved 2026-10-29
- **Notes**: _

### 8. FAB can cover the bottom card — UX (reviewer confidence: medium)
- **Where**: `ui/list/ListScreen.kt:112-114`
- **Context**: The list has only 16 dp of content padding, so when scrolled to the end the floating button can sit on top of the last card's text or date.
```kotlin
LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(spacing),
```
- **Options**:
  - **A (recommended)**: Add extra bottom padding of about the FAB height plus its margin (56 dp + 16 dp), as a named value in `dimens.xml`.
  - **B**: Leave it. No code change, but the last card's right edge can stay hidden behind the button at the end of the list, which is a small usability and accessibility flaw.
- **Your decision**: A (recommended), approved 2026-10-29
- **Notes**: _

### 9. New entry may not be visible after saving — Requirement gap (found in emulator testing)
- **Where**: `ui/list/ListScreen.kt` (LazyColumn scroll state) and the save-new path in `ui/navigation/JournalNavHost.kt`
- **Context**: The assignment says that after saving a new entry the user should see the List "with the new entry visible". New entries go to the top of **Today**, but List keeps its old scroll position. If you had scrolled down before tapping the FAB, you come back to the same spot and the new entry is off-screen above. The "Entry saved" snackbar still shows. Screenshot: `reviews/screenshots/new-entry-offscreen.png`.
- **Options**:
  - **A (recommended)**: When List shows the `ENTRY_CREATED` message, scroll the list to the top (`listState.animateScrollToItem(0)`), so the new entry is always in view.
  - **B**: Scroll to the new entry's exact position using its id. More precise, but more code, and with newest-first sorting the new entry is always first anyway.
- **Your decision**: A (recommended), approved 2026-10-29
- **Notes**: _

## Emulator test results (2026-10-29, Medium_Phone, API 37)
| Check | Result |
|---|---|
| List opens with Today / This Week / Older headers, Cards show title, one-line preview and date | Pass |
| Tap card → Detail; Edit and Delete in the top bar | Pass |
| Edit existing → Save → Detail shows the edit + "Changes saved" snackbar | Pass |
| Back (system and top-bar arrow) on Detail after an edit → List, not Edit | Pass |
| FAB → "New entry"; Save disabled while title blank; "Title is required" after clearing | Pass |
| Save new (list at top) → List shows new entry + "Entry saved" snackbar | Pass |
| Save new (list scrolled down) → new entry visible | **Fail**, see #9 |
| Delete → dialog; Cancel keeps it; Delete → List, entry gone | Pass |
| Back from Edit discards the draft and returns to Detail | Pass |
| Rotation on Edit keeps the draft | Pass |
| Back on List exits the app | Pass |
| Dark mode: theme colors applied throughout | Pass |
| FAB covers the last card at the end of the list | Confirmed, see #8 (`screenshots/fab-covers-last-card.png`) |
| Process death while editing a user-created entry | Confirmed, see #2: title reads "New entry", Save returns to List with no snackbar, and the draft is lost (`screenshots/restored-edit-says-new.png`) |

Expected with in-memory data: closing the app (Back on List) or process death resets the list to the seed entries.


## Retest after decisions (2026-10-29)
After the approved changes, a second re-review found one stale README line and one misleading comment. Both are now fixed. 102 JUnit4 tests pass (the new `JournalViewModelSaveTest` adds 18).

| Check | Result |
|---|---|
| #9 Save new entry while scrolled down → list scrolls to top, new entry visible, "Entry saved" snackbar shown | Pass |
| #8 End of list → last card sits fully above the FAB | Pass |
| #2 Process death while editing a user-created entry → Save keeps the draft as a new entry on List | Pass |
| Edit existing → Save → Detail with "Changes saved" (regression check) | Pass |

## Already handled
- Edit screen: the bottom inset was counted twice above the keyboard; fixed with `consumeWindowInsets(innerPadding)`.
- List section headers are marked `semantics { heading() }` for TalkBack.
- A misleading comment about delete ordering in `JournalNavHost` was corrected.

The reviewer also noted as OK: rotation and process death are otherwise handled correctly, entry content is never logged, and the seed text hard-coded in the data layer is acceptable.

## Proposed commit
- **Files** (build outputs, `.gradle/`, `.idea/`, `.kotlin/` and `local.properties` are gitignored and excluded):
  - Root build files (pending decision #1, recommended yes): `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`, `gradle/gradle-daemon-jvm.properties`, `gradle/libs.versions.toml`
  - Docs: `README.md`
  - App build and manifest: `app/.gitignore`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/keepRules/rules.keep`, `app/src/androidTest/java/com/example/journal/ExampleInstrumentedTest.kt` (template)
  - Resources: `app/src/main/res/values/{colors,dimens,strings,themes}.xml`, `app/src/main/res/xml/{backup_rules,data_extraction_rules}.xml`, `app/src/main/res/drawable/ic_launcher_{background,foreground}.xml`, `app/src/main/res/mipmap-*/ic_launcher*.webp` and `mipmap-anydpi-v26/ic_launcher*.xml`
  - Main code: `MainActivity.kt`, `model/JournalEntry.kt`, `model/EntryGroup.kt`, `data/EntryGrouping.kt`, `data/EntryIds.kt`, `data/SeedData.kt`, `ui/JournalViewModel.kt`, `ui/navigation/Routes.kt`, `ui/navigation/JournalNavHost.kt`, `ui/list/ListScreen.kt`, `ui/detail/DetailScreen.kt`, `ui/edit/EditScreen.kt`, `ui/common/CommonUi.kt`, `ui/theme/{Color,Theme,Type}.kt`
  - Tests: `data/EntryGroupingTest.kt`, `data/EntryIdsTest.kt`, `data/SeedDataTest.kt`, `data/TestTime.kt`, `ui/JournalViewModelTest.kt`, `ui/navigation/RoutesTest.kt`, plus the deletion of `ExampleUnitTest.kt`
  - **Pending decision #1**: `.claude/` (4 agent definitions), `CLAUDE.md`, `reviews/` (`.gitkeep` and this report)
  - Note: decisions 2, 3, 4 and 8 would change `JournalNavHost.kt`, `EditScreen.kt`, `ListScreen.kt`, `JournalViewModel.kt`, `dimens.xml`, the tests and the README, so staging should wait until they are carried out.
- **Message**:
  ```
  Build three-screen journal app with Navigation Compose and Material 3

  Add List, Detail and Edit screens wired through a single NavHost, with
  one shared in-memory ViewModel, grouped entries (Today / This Week /
  Older), save snackbars, and 84 JUnit tests. Include the Gradle root
  files and wrapper so the project builds from a clean clone.
  ```
- Nothing is committed until you approve.
