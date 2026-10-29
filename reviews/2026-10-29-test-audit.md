# Unit-test audit
**Date**: 2026-10-29 · **Status**: Owner approved all fixes and deletions; mutation re-run below

An independent agent (not the test-writer) reviewed all 129 unit tests against the production code. It looked for tests that cannot fail, duplicate tests, and tests that don't test correctly. It backed its findings with mutation testing: it planted small bugs in a scratch copy of the project and checked which tests caught them. The project directory was not modified during the audit.

## Summary (before fixes)
129 tests reviewed · 9 cannot-fail · 7 duplicate groups · 3 incorrect · 22 mutations run, 4 survived (1 equivalent)

## Findings and what was done

### Bug: date helper depended on the machine's locale
`TestTime.at()` used `Calendar.getInstance(zone)`. In a Thai (`th_TH`) locale that returns a Buddhist calendar, so `2025` meant 1482 CE. Three date tests failed under that locale. **Fix:** use `GregorianCalendar(zone)`. Production `startOfDay` is unaffected, because it only sets time-of-day fields and adds days.

### Cannot-fail
| Test | Problem | Action |
|---|---|---|
| `EntryGroupingTest.groupEntries_doesNotMutateOrDropEntries` | The input was already sorted, so an in-place sort went undetected (mutation M08 survived); the "drop" check only counted entries | Rewritten with unsorted input and an exact id set |
| `JournalViewModelTest.updateEntry_keepsOriginalTimestampAndId` | `entryById(1)!!.id == 1` is always true | Asserts the whole entry and the id list |
| `JournalViewModelTest.messageShown_withNoMessage_isHarmless` | Passed against a no-op | Asserts the whole state is unchanged |
| `ThemeSeedsTest.initialSeedPage_matchAtFirstIndex_returnsZero`, `…nullAtIndexZero…` | "Found at 0" and the fallback are both 0 | Deleted |
| `weekWindowDays_isSeven`, `newEntryId_isMinusOne`, `defaultSeedPage_isZero` | Constant compared with its own literal | Deleted (behavioural tests cover each value) |
| `EntryIdsTest.nextEntryId_resultNeverEqualsSentinel` | Checked one input; the "never" claim is false for negative ids | Deleted |

### Gaps (mutations no test caught)
| Mutation | Action |
|---|---|
| M08: sort the caller's list in place | Covered by the rewritten no-mutation test |
| M18: two seeds share a TalkBack name | New test: name resources are distinct |
| M19: typo in a seed color | New test: pins all 10 ARGB values |

### Weak assertions
- The SeedData launch-time and DST tests checked only the `[3, 4, 3]` section sizes. They now check which ids are in which group.
- `groupEntries_boundaryEntries_splitAtExpectedPlaces` now also checks the group labels.

### Duplicates deleted
- **ThemeSeeds:** three of the four round-trip tests, one of two "unknown color" tests (the two empty-list tests were merged into one), `isNotEmpty`, and `firstEntryIsNullDefaultPage`.
- **Routes:** `builtRoutes_startWithPatternPrefix`.
- **ViewModel:** `init_emptyList_isEmptyState`, `deleteEntry_lastEntry_leavesEmptyList`, `addEntry_twice_producesDistinctIds`.
- **Grouping:** `justAfterMidnightToday`, `sixDaysAgoLateEvening`, `sevenDaysAgo`, `veryOldTimestamp`. The boundary tests stay.
- **Save:** `save_newEntryId_trimsTitle`, `save_existingId_trimsTitle` (already covered through `addEntry` and `updateEntry`).

### Noted, no change
- `save_idDeletedBeforeSave_createsNewEntryInstead` documents that `max + 1` reuses an id after the highest entry is deleted. That is correct for the current design, but a stale back-stack route could then point at a different entry.
- M13, removing `save()`'s blank-title guard, is an equivalent mutant: `addEntry`'s own blank check gives the same result.

## Mutation re-run after fixes
The auditor re-ran all 22 mutations against the revised suite: **21 caught and 1 equivalent survivor (M13)**.
- M08, M18 and M19, which survived before, are now caught.
- No mutation lost its last catching test because of the deletions.
- **Locale control:** with the old `Calendar.getInstance` helper restored, the DST tests fail again under `th_TH`. With the fix they pass, so the fix is confirmed.

The re-run found three more items, all cleaned up:
- `messageShown_withNoMessage_isHarmless` still passed against a no-op, so it was deleted.
- Three per-id seed tests were made redundant by the new full-layout test, so they were deleted.
- An unused import was removed.

The pinned seed-color test was kept alongside the three readable property tests (count, distinct, opaque), which serve as documentation.

**Result:** 104 tests (was 129), all passing, no warnings. Every real mutation is caught.
