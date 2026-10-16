# TEST-26: mutation audit of the unit suite

## Why

An independent audit claimed several tests were weaker than their names implied. This is the
empirical answer: apply one small production mutation at a time, run only the test that claims to
guard the behaviour, and see whether it actually fails.

Method, per the task:

1. Make the smallest production edit that breaks the behaviour a test claims to protect.
2. Run only the claiming tests with `--tests`.
3. Record FAILED (the behaviour is protected) or PASSED (unprotected, or the test is inert).
4. Revert the edit completely (`git checkout -- app/src/main`) before the next mutation.

No mutation is committed; `app/src/main` is byte-identical to HEAD at the end of the audit.

## Mechanism

`tools/mutation_check.sh` — the documented, repeatable mutation list plus the runner.

```
tools/mutation_check.sh --list            # mutation id -> expected catcher test classes
MUTATION=<id> tools/mutation_check.sh     # apply one mutation, run its tests, revert
```

It refuses to start on a dirty `app/src/main`, verifies each replacement string matches exactly
once, parses the JUnit XML for the failing test names, and reverts with `git checkout -- app/src/main`
(asserting the tree is clean again). A Gradle compile error is reported as
`DID-NOT-COMPILE` and is *not* counted as coverage.

Limits, stated plainly:

* The list only contains behaviours some test already claims to guard. A behaviour with no test at
  all never appears here, so "everything caught" would not mean the suite is complete.
* Replacements are exact strings; a refactor that changes whitespace breaks the mutation, not the
  behaviour.
* Mutants are single-edit. A test that catches one edit may still miss another way of breaking the
  same behaviour (for example the two `LogRetention` vacuum edits below are both caught, which is
  luck of the assertion set, not proof of exhaustiveness).
* Entries whose only catcher is `app/src/androidTest/**` cannot be executed in this environment
  (no device or emulator is allowed), so they are reported `UNVERIFIED-IN-THIS-ENVIRONMENT` with a
  compile check only.

## Results

Line numbers are from HEAD.

| # | Mutation | Production line | Expected catcher | Observed | Verdict |
|---|----------|-----------------|------------------|----------|---------|
| 1 | retention cutoff: `minusYears(LOG_RETENTION_YEARS)` -> `+1` | `LogRetention.kt:35` | `LogRetentionTest.cutoff*` | 3 tests failed (exact cutoff, leap-day clamp, lexicographic order) | CAUGHT |
| 2 | leap-day clamp: `minusYears(2)` -> `minusDays(730)` | `LogRetention.kt:35` | `LogRetentionTest.cutoffClampsLeapDayToTheLastValidDay` | that test failed (`2022-03-01` vs `2022-02-28`) | CAUGHT |
| 3 | vacuum threshold: `25L` -> `30L` | `LogRetention.kt:26` | `LogRetentionTest.vacuumRunsOnceTheFreelistReachesTheThreshold` | that test failed | CAUGHT |
| 4 | vacuum boundary: `>=` -> `>` | `LogRetention.kt:39` | `LogRetentionTest.vacuumRunsOnceTheFreelistReachesTheThreshold` | that test failed (the `25/100` exact-boundary case) | CAUGHT |
| 5 | daily gate: `!= today` -> `true` | `LogRetention.kt:75` | `LogRetentionAnalyzeTest.aNewAppVersionAnalyzesEvenWhenThePruningAlreadyRanToday` | that test failed (the expired row was deleted) | CAUGHT |
| 6 | ANALYZE gate: delete the once-per-version early return | `LogRetention.kt:105` | `LogRetentionAnalyzeTest.analyzeRunsAtMostOncePerAppVersion` | that test failed (statistics rebuilt on the second pass of the same version) | CAUGHT |
| 7 | score cache key: drop `limit` from `ScoreKey` | `HomeAppRanking.kt:141` | `CachedHomeAppRankingSourceTest.aDifferentLimitIsNotServedFromTheCachedRead` | that test failed | CAUGHT |
| 8 | most-used cache key: drop `launchLogVersion` | `HomeAppRanking.kt:156` | `CachedHomeAppRankingSourceTest.launchCountsAreCachedForTheWholeDayAndReReadOnALaunch` | that test failed (`2 != 3` reads) | CAUGHT |
| 9 | resolver invalidation: `invalidatePackage` becomes a no-op | `HomeAppRanking.kt:207` | `CachedHomeAppResolverTest.aLauncherPackageChangeDropsTheCachedRow` | 2 tests failed | CAUGHT |
| 10 | over-fetch: `homeCandidateLimit` -> `n` | `HomeAppRanking.kt:19` | `HomeAppRankingTest.classicRankingDropsQuietProfilesAndOverFetchesBeforeCapping` | that test failed | CAUGHT |
| 11 | coordinator in-flight guard: delete the `isActive` early return | `HomeRankingCoordinator.kt:174` | `HomeRankingCoordinatorTest.aRefreshRequestedWhileAPassIsInFlightIsCoalescedIntoThatPass` | that test failed (a second pass started) | CAUGHT |
| 12 | coordinator debounce: `2_000L` -> `30_000L` | `HomeRankingCoordinator.kt:35` | `HomeRankingCoordinatorTest.aBurstOfRefreshesCollapsesIntoASingleTrainingPass` | 2 tests failed (the burst never trained in time; the "settled interval" assertion timed out) | CAUGHT |
| 13 | coordinator minimum-interval gate: `15 min` -> `0` | `HomeRankingCoordinator.kt:38` | `HomeRankingCoordinatorTest.aRefreshInsideTheMinimumTrainingIntervalIsSkippedUntilItHasElapsed` | all tests passed, 4 runs in a row | **SURVIVED** |
| 14 | weather parameter name: `weather_code` -> `weathercode` | `WeatherForecastUrl.kt:15` | `WeatherForecastUrlTest` | 2 tests failed (URL text and requested-fields-decodable) | CAUGHT |
| 15 | validator lower version bound: delete `schemaVersion < MIN_SCHEMA_VERSION` | `DatabaseReplacement.kt:65` | `DatabaseCandidateValidatorTest.rejectsASchemaVersionOlderThanTheMigratableRange` | that test failed | CAUGHT |
| 16 | validator integrity: delete the `quick_check` rejection | `DatabaseReplacement.kt:68` | `DatabaseCandidateValidatorTest.rejectsADatabaseWhosePagesAreDamagedEvenThoughItStillOpens` | that test failed | CAUGHT |
| 17 | validator empty file: delete `candidate.length() == 0L` | `DatabaseReplacement.kt:51` | `DatabaseCandidateValidatorTest.rejectsAnEmptyFile` | that test failed (`UnsupportedVersion(0)` instead of `NotADatabase`) | CAUGHT |
| 18 | backup D2D guard: delete `&& !deviceToDeviceTransfer` | `DatabaseBackupPayload.kt:54` | `DatabaseBackupPayloadTest.skipsThePayloadWhenADeviceTransferAlsoAdvertisesEncryption` | that test failed | CAUGHT |
| 19 | backup CSE flag flip: `!= 0` -> `== 0` | `DatabaseBackupPayload.kt:51` | `DatabaseBackupPayloadTest.uploadsThePayloadForCloudBackupWithClientSideEncryption` | 2 tests failed | CAUGHT |
| 20 | `TagDao` ordering: drop `name COLLATE NOCASE` | `TagDao.kt:15` | `TagDaoTest.tagsAreOrderedByPositionThenCaseInsensitivelyByName` | that test failed | CAUGHT |
| 21 | `TagRepository.moveTag`: drop `coerceIn(-1, 1)` | `TagRepository.kt:68` | `TagRepositoryTest` slot arithmetic | all tests passed | **SURVIVED** |
| 22 | ContactsDAO null guard: drop `displayName.isNullOrBlank()` | `ContactsDAO.kt:63` | `ContactsDaoTest.rowsWithoutADisplayNameOrANumberAreSkippedInsteadOfCrashing` | that test failed | CAUGHT |
| 23 | `SettingsRepository.setGrantGated`: delete the grant guard | `SettingsRepository.kt:86` | `SettingsRepositoryTest.enablingAGrantGatedSettingWithoutTheGrantIsNotPersisted` | that test failed | CAUGHT |
| 24 | `ApplicationItem` tag snapshot: compare against `app.tags` instead of `persistedTags` | `ApplicationItem.kt:190` | `ApplicationItemTagsTest.revertingToTheOriginalTagSetStillPersists` (`androidTest`) | not executable here; the mutation compiles | UNVERIFIED-IN-THIS-ENVIRONMENT |

19 of 24 mutations were caught. Two survivors were real and are now covered (below). One target is
guarded by an instrumentation test that this environment cannot run.

## Survivors

### 1. `HomeRankingCoordinator` minimum training interval (mutation 13)

`HomeRankingCoordinator.kt:38` (`MODEL_REFRESH_MIN_INTERVAL_MS`) and `:180`
(`if (!force && now - lastModelTrainedAtMs < MODEL_REFRESH_MIN_INTERVAL_MS) return`).

Nothing failed. `HomeRankingCoordinatorTest.aRefreshInsideTheMinimumTrainingIntervalIsSkippedUntilItHasElapsed`
asserts `1 == dao.reads` *immediately* after `advanceTimeBy(2_100)`. The gate is evaluated on the
virtual-time test thread and returns before launching anything, but a mutant that removes the gate
launches the training pass on `Dispatchers.IO` and only then does the assertion read the counter. The
test thread wins that race, so the negative assertion is satisfied whether or not the gate exists.
The second half of the test (advance 20 minutes, expect a pass) then passes for either edit.

That is the dangerous shape: the test can only report "no pass yet", never "no pass at all", and a
user-visible regression (learned mode retraining on every launch, re-reading the whole log) would
slip through.

Fix: `HomeRankingCoordinatorTest.aRefreshInsideTheMinimumIntervalStartsNoPassEvenAfterTheIoDispatcherSettles`
waits real time (`Thread.sleep(IO_SETTLE_MILLIS)`, 1 s) after firing the request inside the
interval before asserting no pass started, then keeps the existing "after the interval it does
train" half. Re-running mutation 13 with that test: **CAUGHT**
(`aRefreshInsideTheMinimumIntervalStartsNoPassEvenAfterTheIoDispatcherSettles` fails).

### 2. `TagRepository.moveTag` single-step clamp (mutation 21)

`TagRepository.kt:68` (`val targetIndex = currentIndex + direction.coerceIn(-1, 1)`).

The doc comment says "the repository only accepts a step of one position", and `moveTag(name,
direction: Int)` is public, but every existing test passes `-1`, `0` or `+1`. With the clamp removed,
`moveTag("Work", 2)` skips a neighbour (persisting the wrong page order) and no test noticed.

Fix: `TagRepositoryTest.aStepLargerThanOneSlotStillMovesExactlyOneSlot` pins that `+2`/`-2` move
exactly one slot and that a clamped step still cannot leave the list. Re-running mutation 21 with
that test: **CAUGHT** (`aStepLargerThanOneSlotStillMovesExactlyOneSlot` fails).

## Unverified target

`ApplicationItem.kt:190` — the BUG-01 persisted-tag snapshot guard. Its only catcher is
`app/src/androidTest/java/ovh/litapp/neurhome3/ui/components/ApplicationItemTagsTest.kt`, which needs
a device; no device or emulator may be used here. The mutation compiles
(`:app:compileDevDebugKotlin :app:compileDevDebugAndroidTestKotlin`), and the production tree was
reverted. The audit therefore cannot say from this environment whether that test bites; the coverage
claim rests on the instrumentation test alone, and there is no JVM test of the guard.

## Honest summary of what is protected

* Genuinely protected by fast, deterministic unit tests: retention cutoff/leap-day/threshold/daily
  gate, the per-version `ANALYZE` gate, both ranking cache keys and the resolver invalidation, both
  backup transport-flag branches, the weather URL parameter set, all three database-candidate
  rejection branches, the tag DAO ordering, the starred-contact null guard, and the grant-gated
  setting write.
* Formerly unprotected and now covered: the coordinator's minimum training interval and the tag
  move step clamp.
* Not verifiable in this environment: the `ApplicationItem` snapshot guard (instrumentation only).
* The coordinator's debounce value and in-flight coalescing *are* protected, but one of the two
  tests that appear to protect the interval only did so by accident, which is why the new test
  matters.
