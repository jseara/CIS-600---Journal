# Review: Theme color picker and light/dark toggle
**Date**: 2026-10-29 · **Status**: Decisions made 2026-10-29, all 6 recommended options approved

## What was built
Two new buttons on the **List** top bar. The first toggles light/dark mode. The second (a palette icon) opens a dialog with a swipeable row of ten overlapping color circles; the centered circle is the choice, and the whole app recolors live behind a lightly dimmed dialog. **Apply** keeps the color; Cancel, Back or tapping outside restores the previous one. Detail and Edit pick up the theme too, but only List shows the buttons. This is a port of the theme picker and light/dark toggle from your "CIS 600 - Material Design" project (read-only reference). Fonts and the D&D styling were left out on purpose.

The theme state lives in `MainActivity`, because the theme has to wrap the whole NavHost: `MainActivity` (`rememberSaveable` for light/dark and the seed color) -> `JournalTheme` (MaterialKolor generates a full Material 3 scheme from one "seed" color) and, as plain values and lambdas, `JournalNavHost` -> `ListScreen` -> `ThemePickerDialog`. There is no ViewModel involvement and nothing is persisted: choices survive rotation but reset when the app is closed.

## Code walkthrough

### `ui/theme/ThemeSeeds.kt` (new, 55 lines)
The ten seed colors (the "500" shades of the classic Material palette) as `ThemeSeed(nameRes, color)`, and a pure function that decides which circle the picker opens on. It works on ARGB `Int`s so a JUnit test can call it with no Compose setup.
```kotlin
fun initialSeedPage(seedArgbs: List<Int>, selectedArgb: Int?): Int {
    if (selectedArgb == null) return DEFAULT_SEED_PAGE
    val index = seedArgbs.indexOf(selectedArgb)
    return if (index >= 0) index else DEFAULT_SEED_PAGE
}
```
`DEFAULT_SEED_PAGE` is 0, which is **Red**. That detail matters for decision 1.

### `ui/theme/ThemePickerDialog.kt` (new, 171 lines)
An `AlertDialog` whose text slot holds a `HorizontalPager` of circles.
- **Live preview**: `LaunchedEffect(pagerState.currentPage) { onPreview(ThemeSeeds[pagerState.currentPage].color) }` reports the centered color whenever the page changes, including once when the dialog first opens. `DisposableEffect(Unit) { onDispose { onPreview(null) } }` clears the preview however the dialog closes.
- **Centering**: `PageSize.Fixed(itemSize)`, a negative `pageSpacing` (so circles overlap) and `contentPadding = PaddingValues(horizontal = (maxWidth - itemSize) / 2)` so the first and last circle can reach the middle. `BoxWithConstraints` supplies `maxWidth`.
- **Look**: `graphicsLayer` scales circles by distance from the center (draw-time only, so scrolling does not re-run layout); `zIndex(-distance)` draws the centered one on top.
- **Lighter dim**: `(LocalView.current.parent as? DialogWindowProvider)?.window` reaches the dialog's own window, and `setDimAmount(0.3f)` lightens the default (about 0.6) so the preview is visible.
- **Accessibility**: the circle row has `selectableGroup()` and each circle has `selectable(..., role = Role.RadioButton)` plus the color name as its `contentDescription`, so TalkBack says "Teal, radio button, selected".

### `ui/theme/Theme.kt` (modified)
`JournalTheme(darkTheme, dynamicColor, seedColor, content)` now picks a scheme in this order: a seed (`rememberDynamicColorScheme(seedColor, isDark)`), else wallpaper colors on Android 12+, else the template's purple schemes. The result is wrapped so changes fade:
```kotlin
MaterialTheme(
    colorScheme = animateColorScheme(colorScheme),
```

### `MainActivity.kt` (modified)
Owns the state and moves edge-to-edge setup into a `LaunchedEffect`:
```kotlin
val systemDark = isSystemInDarkTheme()
var darkTheme by rememberSaveable { mutableStateOf(systemDark) }
var seedArgb by rememberSaveable { mutableStateOf<Int?>(null) }
...
LaunchedEffect(darkTheme) {
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.auto(TRANSPARENT, TRANSPARENT) { darkTheme },
        navigationBarStyle = SystemBarStyle.auto(TRANSPARENT, argb(0x80, 0x1b, 0x1b, 0x1b)) { darkTheme },
    )
}
```
Idioms: `Color` cannot be stored in a saved-state `Bundle`, so the seed is saved as its ARGB `Int`. `SystemBarStyle.auto(light, dark) { isDark }` lets the app (not the system) decide whether the status-bar icons should be light or dark. The semi-transparent dark navigation scrim exists because API 24-25 cannot draw dark navigation-bar icons, so a transparent bar left white buttons on a light app. The original plain `enableEdgeToEdge()` call in `onCreate` was removed (see decision 3).

### `ui/navigation/JournalNavHost.kt` and `ui/list/ListScreen.kt` (modified)
Five new parameters (`isDark`, `onToggleDark`, `seedColor`, `onSeedSelected`, `onPreviewSeed`) are passed through the NavHost to `ListScreen`, which adds the two `IconButton`s in the top bar `actions` and shows the dialog when `showThemeDialog` (a `rememberSaveable` flag) is true. The toggle's icon shows what tapping will switch *to* (sun in dark mode, moon in light mode).

### Resources and build
- `ic_dark_mode.xml`, `ic_light_mode.xml`, `ic_palette.xml`: vector drawables copied from Google's material-design-icons (Apache 2.0), used instead of the very large `material-icons-extended`. Loaded with `painterResource`.
- `strings.xml` (Apply, switch-to-light/dark, change theme, dialog title, ten color names) and `dimens.xml` (circle size 56dp, overlap 20dp, border widths 3dp/1dp).
- `gradle/libs.versions.toml` and `app/build.gradle.kts`: `com.materialkolor:material-kolor` 4.0.5.
- `README.md`: new "Theme picker" section, dependency notes and two entries under unsure decisions.

## Tests
**118 plain JUnit4 tests, all passing** (102 from the earlier work plus 16 new in `ThemeSeedsTest`, verified by counting `@Test` per file). `ThemeSeedsTest` covers `initialSeedPage` (null, first, middle, last, unknown color, alpha mismatch, empty list, duplicates) and the seed list (ten seeds, all distinct, all fully opaque, every seed found at its own index, and the save-as-ARGB / restore-as-`Color` round trip). Lint: 0 errors. Build: passes.

**Gaps**: the dialog, the theme composition and the `MainActivity` state wiring are Compose/Activity code, so plain JUnit cannot test them; they were checked on the emulator instead.

**Emulator checks (Medium_Phone, API 37)**

| Check | Result |
| --- | --- |
| Light/dark toggle, with status-bar icons following the app | Pass |
| Dialog opens centered on the applied seed | Pass |
| Swiping, and tap-to-centre | Pass |
| Live preview tints the whole app | Pass |
| Cancel reverts the preview; Apply keeps it | Pass |
| Rotating with the dialog open keeps the dialog and preview | Pass |
| Detail and Edit pick up the theme | Pass |
| Light mode with a seed | Pass |
| Red preview as soon as the picker opens with no seed | Issue (decision 1) |
| Light flash while navigating in dark mode | Issue (decision 4) |

Screenshots (in `reviews/screenshots/`): `theme-red-preview-on-open.png`, `theme-dark-transition-flash.png` (taken with animations slowed 10x, mid-transition), `theme-brown-applied-dark.png`, `theme-brown-applied-light.png`. I opened the first two and they show what is described.

## Decisions needed

### 1. No way back to the default colors, and Red previews as soon as the picker opens — UX
- **Where**: `ui/theme/ThemeSeeds.kt:42,51-55`, `ui/theme/ThemePickerDialog.kt:78-86`
- **Context**: With no seed set, `initialSeedPage` returns page 0, which is Red, and the preview effect immediately calls `onPreview(ThemeSeeds[pagerState.currentPage].color)`. So opening the picker recolors the app red before you have touched anything (`reviews/screenshots/theme-red-preview-on-open.png`). Cancel restores the old colors, but Apply always stores a seed, and once one is applied nothing in the picker returns the app to the wallpaper colors (Android 12+) or the template scheme; only restarting the app does. The source project behaves the same way. The README already admits the second part: "No way back to wallpaper colors."
```kotlin
LaunchedEffect(pagerState.currentPage) {
    onPreview(ThemeSeeds[pagerState.currentPage].color)
}
```
- **Options**:
  - **A (recommended)**: Add a "Default" page 0 whose color is `null` (previews the wallpaper or template colors) and where Apply stores `null`. Fixes both problems. Needs one string and a nullable color in `ThemeSeed`, and the pager/preview/apply types change from `Color` to `Color?`.
  - **B**: Do not preview until the user first swipes or taps. Smaller change, but there is still no way back to the default.
  - **C**: Keep it and document it.
- **Your decision**: A (recommended), approved 2026-10-29
- **Notes**: _

### 2. The app stops following system dark mode right after launch, not only after a toggle — UX
- **Where**: `MainActivity.kt:33-34`; README ("Decisions I was unsure about")
- **Context**: `darkTheme` is initialised from the system setting inside `rememberSaveable`. `AndroidManifest.xml` has no `configChanges`, so changing the system dark mode recreates the Activity, and `rememberSaveable` then restores the *launch* value instead of reading the new system value. So the app ignores system dark-mode changes from the moment it launches, even if you never touched the toggle. The README says otherwise: "after you toggle light/dark, the app stops following the system setting until it restarts." (This is from reading the code; I did not reproduce it on a device.)
```kotlin
val systemDark = isSystemInDarkTheme()
var darkTheme by rememberSaveable { mutableStateOf(systemDark) }
```
- **Options**:
  - **A (recommended)**: Save a nullable override (`Boolean?`, null until the user taps the toggle) and compute `darkTheme = override ?: isSystemInDarkTheme()`. About 3 lines; makes the README true. The toggle then flips the effective value into an explicit override.
  - **B**: Keep the code and correct the README to say the app uses the system mode at launch only.
- **Your decision**: A (recommended), approved 2026-10-29
- **Notes**: _

### 3. Edge-to-edge is now applied after the first frame — Uncertain (reviewer confidence: medium)
- **Where**: `MainActivity.kt:28-61`
- **Context**: The old code called `enableEdgeToEdge()` in `onCreate`, before `setContent`. The new code only calls it from a `LaunchedEffect`, which runs in a coroutine after the first composition. On API 24-34 the window may therefore be laid out once without edge-to-edge and then jump when it is applied. API 35+ forces edge-to-edge anyway, so the emulator at API 37 would not show it.
- **Options**:
  - **A (recommended)**: Call `enableEdgeToEdge()` in `onCreate` before `setContent` (as before), and keep the styled call in an effect keyed on `darkTheme`; using `DisposableEffect(darkTheme)` (which runs at the end of composition, not in a later coroutine) is a small improvement over `LaunchedEffect`.
  - **B**: Accept the possible one-frame jump.
  - **Correction to the briefing**: the source project does *not* call `enableEdgeToEdge()` in `onCreate`; that line is commented out (`// enableEdgeToEdge() // defnd on ComponentActivity...`). It uses only `DisposableEffect(darkTheme)` in `setContent`, with a plain transparent navigation bar. So option A is the safe pattern, not a copy of the source, and the dark nav-bar scrim is this project's own addition.
- **Your decision**: A (recommended), approved 2026-10-29
- **Notes**: _

### 4. Light flash between screens in dark mode — UX
- **Where**: `app/src/main/res/values/themes.xml`
- **Context**: The window background comes from the XML theme, which is always light, while the Compose theme can be dark through the in-app toggle. During a screen transition the old and new screens cross-fade and the light window shows through; in `reviews/screenshots/theme-dark-transition-flash.png` (animations slowed 10x) the screen turns greyish mid-transition. The toggle makes dark mode much more common, so this will be seen.
```xml
<style name="Theme.Journal" parent="android:Theme.Material.Light.NoActionBar" />
```
- **Options**:
  - **A (recommended)**: Wrap `JournalNavHost` in `Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background)` inside `JournalTheme`. It follows both the toggle and the seed.
  - **B**: Add a `values-night` window theme. Simple, but it follows the system setting, not the in-app toggle, so the flash would remain when the two disagree.
  - **C**: Leave it.
- **Your decision**: A (recommended), approved 2026-10-29
- **Notes**: _

### 5. New dependency and icon approach — Architecture
- **Where**: `gradle/libs.versions.toml` (`materialKolor = "4.0.5"`), `app/build.gradle.kts:46`, `app/src/main/res/drawable/ic_{dark_mode,light_mode,palette}.xml`
- **Context**: CLAUDE.md treats any dependency as an architecture decision. MaterialKolor (MIT) generates a full Material 3 scheme from one seed color. It pulls in thin JetBrains Compose wrapper artifacts, one of them beta-tagged, but `material3` still resolves to the stable 1.4.0. A newer major version, 5.0.1, exists; 4.0.5 is the version your other project uses with the same Compose BOM and Kotlin version. The three icons are vector drawables from Google's material-design-icons (Apache 2.0) instead of `material-icons-extended`, to avoid a very large library.
- **Options**:
  - **A (recommended)**: Approve both.
  - **Optional**: the README says `material-icons-extended` is "a ~35 MB library"; I could not verify that figure, so soften it to "very large".
- **Your decision**: A (recommended), approved 2026-10-29
- **Notes**: _

### 6. Remaining design choices (approve together) — UX
- **Where**: `MainActivity.kt`, `JournalNavHost.kt`, `Theme.kt`, `ThemePickerDialog.kt`
- **Context**:
  - Theme state lives in `MainActivity` and is passed down as values and lambdas.
  - With no seed, the app uses wallpaper colors on Android 12+ and the template scheme below that.
  - The confirm button says "Apply" rather than "OK".
  - Nothing is persisted: choices survive rotation but reset when the app is closed. Persisting would need DataStore or SharedPreferences (a new dependency).
- **Options**:
  - **A (recommended)**: Approve as documented.
  - **B**: Add persistence.
- **Your decision**: A (recommended), approved 2026-10-29
- **Notes**: _

## Retest after decisions (2026-10-29)
129 JUnit4 tests pass (ThemeSeedsTest now has 27, covering the Default page).

| Check | Result |
|---|---|
| #1 No seed set: picker opens on "Default" (announced as selected), no red preview | Pass |
| #1 Apply a color, reopen: that color is centered | Pass |
| #1 Swipe back to Default and Apply: default colors return; reopening centers Default | Pass |
| #2 Before any toggle, the app follows system dark mode on and off | Pass |
| #2 After a toggle, the app keeps the chosen mode when the system changes | Pass |
| #4 Dark mode List → Detail transition, animations slowed 10×: no light flash (mid-transition brightness 17, was 88) | Pass |
| Regression: create an entry → List, new entry visible, "Entry saved" | Pass |

## Already handled
- The navigation-bar scrim on API 24-25 was transparent, leaving white buttons on a light app; it is now the library's dark scrim.
- `selectableGroup()` was added to the circle row.
- A test for the saved-ARGB round trip was added.
- An emulator concern turned out to be a false alarm: the centered circle *is* reported to accessibility as selected (`checked=true`). The first check was wrong because the inspection script filtered that attribute out. It was re-checked and is fine.

## Proposed commit
- **Files** (everything in `git status`; nothing else is pending):
  - Modified: `README.md`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `app/src/main/java/com/example/journal/MainActivity.kt`, `app/src/main/java/com/example/journal/ui/list/ListScreen.kt`, `app/src/main/java/com/example/journal/ui/navigation/JournalNavHost.kt`, `app/src/main/java/com/example/journal/ui/theme/Theme.kt`, `app/src/main/res/values/dimens.xml`, `app/src/main/res/values/strings.xml`
  - New: `app/src/main/java/com/example/journal/ui/theme/ThemePickerDialog.kt`, `app/src/main/java/com/example/journal/ui/theme/ThemeSeeds.kt`, `app/src/main/res/drawable/ic_dark_mode.xml`, `ic_light_mode.xml`, `ic_palette.xml`, `app/src/test/java/com/example/journal/ui/theme/ThemeSeedsTest.kt`
  - Review material: `reviews/2026-10-29-theme-picker.md` and the four `reviews/screenshots/theme-*.png` files
- **Message**:
  ```
  Add theme color picker and light/dark toggle

  Add a light/dark toggle and a swipeable seed-color picker with live
  preview to the List top bar. MaterialKolor builds the Material 3
  scheme from the chosen seed; icons are vector drawables. Add
  ThemeSeeds tests (16 new, 118 total).

  Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
  ```
- Decisions 1-4 change code, so staging should wait until they are carried out. Nothing is committed until you approve.
