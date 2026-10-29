# CIS 600 Journal

Assignment 5 for CIS 600 (Android Development): a multi-screen journal app written in Kotlin with
Jetpack Compose and Material 3. Its main focus is using Navigation Compose correctly: routes,
arguments, and sensible back-stack behaviour.

## What the app does

- **List** (start screen): every entry as a Material `Card` (title, one-line body preview, date),
  grouped under **Today**, **This Week** and **Older** headers in a `LazyColumn`. A floating action
  button creates a new entry. Tapping a card opens Detail.
- **Detail**: the full title, date and body. The top bar has a back arrow, an **Edit** action, and a
  **Delete** action that asks for confirmation in an `AlertDialog`.
- **Edit**: one screen for both new and existing entries, with `OutlinedTextField`s for the title and
  body and a **Save** button. Save is disabled while the title is blank, and a "Title is required"
  error appears once the user has typed and cleared the title.
- After a save, a `Snackbar` confirms it on the screen the user lands on: List for a new entry,
  Detail for an edit. After creating an entry, List also scrolls back to the top so the new card
  (the newest entry in Today) is visible even if the list was scrolled down before tapping +.

The data is an in-memory list held in a `ViewModel`, seeded with 10 entries whose timestamps are
computed relative to the current time, so all three sections are always populated. Data survives
rotation but is reset when the app process ends. (The assignment text uses both "JournalItem" and
"JournalEntry"; this project uses `JournalEntry(id, title, body, timestamp)` throughout.)

## How to run

1. Open the project folder in Android Studio (a recent version that supports AGP 9.4).
2. Let Gradle sync, then pick an emulator or device running Android 7.0 (API 24) or newer.
3. Press **Run**, or from a terminal:

   ```sh
   ./gradlew :app:assembleDebug      # build the APK
   ./gradlew :app:installDebug       # install it on a connected device or emulator
   ./gradlew :app:testDebugUnitTest  # run the unit tests
   ```

## Project layout

```
app/src/main/java/com/example/journal/
├── MainActivity.kt              single Activity, hosts JournalNavHost
├── model/                       JournalEntry, EntryGroup, EntrySection
├── data/                        pure functions: grouping, id generation, seed data
└── ui/
    ├── JournalViewModel.kt      shared in-memory state, save()/SaveOutcome, one-shot snackbar message
    ├── navigation/              Routes and the single NavHost
    ├── list/ detail/ edit/      one stateless screen composable each
    ├── common/                  date formatter, snackbar helper, back button
    └── theme/                   Material 3 theme, theme seeds, theme picker dialog
```

## Route design

| Screen | Route pattern | Example |
| --- | --- | --- |
| List | `list` | `list` |
| Detail | `detail/{entryId}` (required `Int`) | `detail/4` |
| Edit | `edit?entryId={entryId}` (optional `Int`, default `-1`) | `edit` (new), `edit?entryId=4` (existing) |

I chose **one Edit route with an optional query argument** (`NavType.IntType`, `defaultValue = -1`)
rather than two routes (`entry` and `entry/{entryId}`). With one route there is exactly one
`composable(...)` block for Edit and no risk of the two routes drifting apart. The "new vs.
existing" decision happens in a single place, `JournalViewModel.save()`, which also treats an id
that no longer exists as a new entry. Real ids start
at 1, so `-1` can never be mistaken for an entry. Detail uses a required path argument because it
makes no sense without an id. All route strings live in `ui/navigation/Routes.kt`, and screens never
see the `NavController`: they receive lambdas such as `onEntryClick: (Int) -> Unit`, and
`JournalNavHost` translates those into navigation calls. Detail and Edit look their entry up by id
in the shared ViewModel's state. Edit doesn't decide "add or update" itself: its Save lambda calls
`JournalViewModel.save(entryId, title, body)`, which returns a `SaveOutcome` (`Created`, `Updated`
or `Invalid`), and the NavHost only maps that outcome to a navigation action.

## Back-stack behaviour

The behaviour I wanted is that the back stack only ever holds screens that are worth going back
to: List at the bottom, optionally Detail above it, and Edit only while the form is actually open.
Pressing back from Detail should always go to List, never to a form the user has already saved,
because returning to a stale form would be confusing and could lead to saving the same thing twice.
To get that, every "finish" action **pops** instead of navigating forward. When Save is tapped, the
ViewModel's `save()` returns a `SaveOutcome` and the NavHost acts on it. `Updated` calls
`popBackStack()`, which removes Edit and reveals the Detail screen beneath it (List → Detail → Edit
becomes List → Detail), so the next back goes to List. `Created` calls
`popBackStack(Routes.LIST, inclusive = false)`, which lands on List, and List scrolls to the top so
the new card is visible. `Invalid` (blank title) stays on the form.
Deleting from Detail uses the same pop to List, so the user is never left on a screen for an entry
that no longer exists. Because nothing ever navigates *forward* to List or Detail after a save, there
are no duplicate copies of a screen on the stack. Finally, every navigation lambda is wrapped in an
`ifResumed` check on the current `NavBackStackEntry`, so a fast double-tap during a transition can't
push Detail twice or pop past List.

The snackbar has to survive that navigation: each screen has its own `Scaffold` and
`SnackbarHostState`, so Edit can't show a snackbar on another screen. Instead, saving leaves a
one-shot `UserMessage` in the shared ViewModel's state; the destination screen (List or Detail)
shows it and immediately clears it, so it appears once and doesn't reappear on rotation. On List,
the same message also triggers the scroll to the top. The scroll and the snackbar run in separate
coroutines, so neither one cancels the other, and the message is still cleared exactly once.

## Theme picker

The List screen's top bar has two extra buttons that restyle the whole app (Detail and Edit pick up
the change too, though their top bars don't show the buttons):

- **Light/dark toggle.** The app follows the system's light/dark mode (including when the system
  setting changes) until you tap the button; from then on your choice wins. The icon shows what
  tapping will switch *to* (a sun in dark mode, a moon in light mode).
- **Theme color (palette icon).** Opens a dialog with a swipeable row of overlapping color
  circles; the centered one is the choice. The first circle is **Default** (no seed: wallpaper
  colors on Android 12+, the template's purple scheme below that), drawn in the primary color the
  app would use without a seed; it is followed by ten seed colors. The dialog opens on the applied
  seed, or on Default if none is applied. While you swipe, the whole app recolors live behind a
  lightly dimmed dialog. **Apply** keeps the choice (picking Default removes the seed);
  **Cancel**, back or tapping outside restores the previous one. TalkBack reads each circle as its
  name, as a radio button in one group.

How it works: `MainActivity` holds a dark-mode override (`null` = follow the system) and the seed
(saved as an ARGB `Int`, because `Color` can't go in a saved-state `Bundle`) in `rememberSaveable`,
plus a temporary preview used only while the dialog is open. The preview needs its own "previewing"
flag, because a `null` preview color means "preview Default". These are passed down to `JournalTheme`, and as plain values and
lambdas through `JournalNavHost` to `ListScreen`. `JournalTheme` uses
[MaterialKolor](https://github.com/jordond/MaterialKolor) to generate a complete Material 3 scheme
from the seed, and `animateColorScheme` fades between schemes instead of jumping. The system bar
icons follow the app's own light/dark setting (via `enableEdgeToEdge` with `SystemBarStyle.auto`),
so they stay readable when the app and the system disagree. A plain `enableEdgeToEdge()` in
`onCreate` makes the very first frame edge-to-edge, and the styled call is re-applied whenever dark
mode changes. A `Surface` in the theme's background color sits behind the NavHost, so screen
crossfades in dark mode don't flash the light window background. The picker was ported from my CIS 600
Material Design project, without that project's fonts and custom palette.

## Decisions made

- **Dependencies** (all in `gradle/libs.versions.toml`):
  - `androidx.navigation:navigation-compose` **2.10.2** (latest stable).
  - `androidx.lifecycle:lifecycle-viewmodel-compose` and `lifecycle-runtime-compose` **2.11.0**,
    sharing one `lifecycle` version key with the existing `lifecycle-runtime-ktx`.
  - `androidx.compose.material:material-icons-core` (version from the Compose BOM, 1.7.8). Material 3
    1.4 no longer pulls in the icons, so the Add, Edit, Delete and back arrow icons need it explicitly.
  - `com.materialkolor:material-kolor` **4.0.5**, for the theme picker. That's the version my other
    project uses with the same Compose BOM and Kotlin version. (5.0.1 is newer, but I didn't move to
    a new major version without checking it against these.)
- **Theme icons as vector drawables.** The dark mode, light mode and palette icons aren't in
  `material-icons-core`. Instead of adding `material-icons-extended` (a very large library, and release
  builds here don't shrink unused code), the three icons are vector drawables in `res/drawable`,
  copied from Google's Material icons.
- **No seed = today's colors.** Until a color is applied (or after Default is applied), the app uses
  the same scheme as before: wallpaper ("Material You") colors on Android 12+, and the template's
  purple scheme on older versions. `defaultColorScheme()` in `Theme.kt` returns that scheme, and the
  picker uses it to draw the Default circle.
- **Default is a picker page, not a seed.** `ThemeSeeds` stays the list of ten real colors; the
  picker's pages are `ThemePickerPages` = `null` (Default) followed by those ten. `initialSeedPage`
  works on `List<Int?>`, so a `null` selection finds the Default page.
- **Theme choices aren't saved when the app closes.** Like the source project, the light/dark
  override and the seed live in `rememberSaveable`, so they survive rotation but reset when the app
  restarts. The owner chose not to add persistence (DataStore or SharedPreferences) for now.
- **Dates without `java.time`:** minSdk is 24 and `java.time` needs API 26 unless core library
  desugaring is enabled. I used `java.util.Calendar` for day boundaries and `java.text.DateFormat`
  (medium date + short time, in the user's locale) for display, so no extra build setup is needed.
- **"This Week"** means the six calendar days before today, i.e. Today + This Week together are a
  rolling 7-day window. I chose this over the calendar week (Mon–Sun) because the calendar week makes
  the section nearly empty early in the week, and its first day differs by locale. Future timestamps
  count as Today. Each section is sorted newest first, and empty sections are not shown.
- **Grouping stays current across midnight:** List keeps its own "now", refreshed on every
  `ON_RESUME` with `LifecycleEventEffect`, and only regroups when `startOfDay(now)` changes (or the
  entries change). Coming back to the app the next morning moves yesterday's entries out of Today.
- **Save logic lives in the ViewModel:** `save()` returns a sealed `SaveOutcome`, so the add-or-update
  rule is unit-testable and the NavHost only handles navigation. It delegates to `addEntry` and
  `updateEntry`, which stay public.
- **FAB clearance:** the List has 88dp of bottom content padding (`fab_clearance` in `dimens.xml`:
  16dp margin + 56dp FAB + 16dp gap), so the last card can scroll above the + button.
- **Pure, testable logic:** grouping (`groupEntries`), id generation (`nextEntryId`, max id + 1) and
  seed data (`seedEntries`) are plain functions that take `now` and a `TimeZone` as parameters.
- **One shared ViewModel**, created with `viewModel(factory = ...)` at the top of `JournalNavHost`,
  outside the `NavHost`, so it is scoped to the Activity and all three screens share it. Its updates
  are synchronous (`MutableStateFlow.update`), so it can be tested with plain JUnit.
- **Editing keeps the original timestamp**, so editing an old entry doesn't move it into Today.
  Titles are trimmed on save; the body is saved as typed.
- **The typed text in Edit is local UI state** (`rememberSaveable`), not ViewModel state, because it
  is a draft until the user taps Save.

## Decisions I was unsure about

- **One Edit route vs. two routes.** Both are allowed. One route felt simpler, but two routes would
  make the "new" and "edit" URLs read more naturally.
- **Where "now" comes from for grouping.** List refreshes it when the screen resumes. If the app
  stays open on List across midnight without ever leaving the screen, the groups only update the
  next time it resumes or the entries change.
- **Back from Edit discards changes silently.** There is no "discard changes?" dialog; that seemed
  beyond the assignment but could be added.
- **Missing entries.** If Detail or Edit is opened with an id that no longer exists (for example,
  after the process is killed and the seed data is rebuilt), Detail shows "This entry no longer
  exists". Edit shows a "New entry" form, and `save()` adds the text as a new entry
  (`SaveOutcome.Created`), so the draft isn't lost: the user lands on List with "Entry saved".
- **No snackbar after delete.** The assignment only asks for one after saving, so I left it out,
  though an "Entry deleted" message (maybe with Undo) would be a natural addition.
