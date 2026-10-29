# Journal (CIS 600 — Android Development)

A journal app for Android, written in Kotlin with Jetpack Compose.

## Project facts

- Single module: `:app`, package `com.example.journal`
- AGP 9.4 (Kotlin support is built in), Kotlin 2.2, Compose BOM 2026.02, Material 3
- minSdk 24, targetSdk/compileSdk 37, Java 11
- Dependencies are declared in `gradle/libs.versions.toml`. Never hard-code versions in `app/build.gradle.kts`.

## Commands

- Build: `./gradlew :app:assembleDebug`
- Unit tests: `./gradlew :app:testDebugUnitTest`
- Single test class: `./gradlew :app:testDebugUnitTest --tests "com.example.journal.SomeTest"`
- Lint: `./gradlew :app:lintDebug`
- Instrumented tests (need a running emulator): `./gradlew :app:connectedDebugAndroidTest`

## Architecture conventions

> Navigation Compose and plain JUnit4 are confirmed. Everything else here is a proposed default
> that still needs the owner's confirmation. Until then, any change that departs from these
> conventions, or adds to them, is an architecture decision and goes to human review.
> Feature prompts may add further architecture details; when they do, they take precedence.
> Approved for the journal assignment (2026-10-29): one Activity-scoped ViewModel shared by all
> screens, holding an in-memory list (no repository or Room for now).

- Navigation: Navigation Compose, with a single `NavHost` in `MainActivity` and routes defined in `ui/navigation/`.
- MVVM: each screen is a `@Composable` plus a `ViewModel` that exposes UI state through `StateFlow`.
- UI state is an immutable `data class`. User events are methods on the ViewModel.
- Composables are stateless where practical: state goes in as parameters, events come out as lambdas.
- Persistence: Room, behind a repository interface, so ViewModels can be tested with a fake.
- No DI framework yet. Pass dependencies in through constructors and wire them by hand.
- Packages: `ui/<feature>/` for screens and ViewModels, `data/` for repositories and storage, `model/` for domain types.
- User-facing strings go in `res/values/strings.xml`. Never hard-code them in composables.
- Testing: plain JUnit4 only. Don't add MockK, Turbine, Robolectric or similar libraries; use
  hand-written fakes instead. Adding any test library is an architecture decision for the owner.
- Comments are required: this is a course project, so the code should be understandable to a
  student. Use KDoc on all public classes and functions, and add comments explaining *why* the
  code does what it does, as well as any Kotlin, Compose or Android idiom a student might not know yet.

## Taskforce workflow

This project uses four subagents, defined in `.claude/agents/`. The main session acts as orchestrator.

1. **coder**: implements the requested feature or fix in `app/src/main`.
2. **test-writer**: writes unit tests for what the coder changed (`app/src/test`) and runs them.
3. **reviewer**: reviews the code and the tests together. Each finding is tagged:
   - `AUTO-FIX`: a clear defect with an obvious fix. It goes back to the **coder** (or to the test-writer
     for test problems), and the loop repeats until none are left. Stop after 3 rounds and escalate.
   - `NEEDS-HUMAN`: architecture decisions, UX decisions, or anything the reviewer isn't confident about.
   - `OK`: notes only, no action needed.
4. **human-liaison**: takes every `NEEDS-HUMAN` finding, writes a markdown report in `reviews/`
   with a summary of the code, and presents it to the owner in chat.

### Git: stage, then wait for approval
- Subagents never run `git add` or `git commit`.
- When the review loop finishes with no AUTO-FIX items left, the orchestrator stages the change
  (`git add` on exactly the files changed for this task) and drafts a commit message.
- The orchestrator then shows the owner the staged file list and the draft message, and commits
  **only after the owner explicitly approves**. Approving one commit does not approve the next.
- If there are open `NEEDS-HUMAN` items, the orchestrator waits for the owner's decisions and
  carries them out (running them back through the loop) before staging.
- Never push, amend, rebase or switch branches unless the owner asks.
