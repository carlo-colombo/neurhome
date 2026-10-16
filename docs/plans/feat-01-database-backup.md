# FEAT-01 / BUG-11 — Automatic database backup via the Android backup framework

Read-only design. Repo: `/home/carlo/projects/neurhome`. All current-behavior claims cite `file:line`. Items I could not verify from source are tagged **[UNVERIFIED]**.

> **OPERATOR DECISION — Option A, full backup.** The product intent is to back up everything,
> **including the `ApplicationLogEntry` launch log** (SSID, latitude/longitude, geohash, query text).
> This supersedes the Option B recommendation below. Implement Option A: explicit rules that
> include the `database` domain in **both** cloud backup and device transfer. **BUG-11 is therefore
> closed as working-as-intended**, not fixed by exclusion. The Option B/C analysis below is retained
> for context.
>
> **OPERATOR DECISION UPDATE — client-side encryption is now required.** This reverses the earlier
> "CSE deliberately NOT enabled" note. Every cloud-backup `<include>` now carries
> `requireFlags="clientSideEncryption"` (in both `data_extraction_rules.xml` and `backup_rules.xml`),
> so cloud backup runs only on devices with a secure lock screen and is skipped entirely on devices
> without one. Skipping backup there is an accepted tradeoff: not backing up the launch log is
> preferred over uploading it unencrypted. The `<device-transfer>` includes deliberately do NOT
> carry the flag, because CSE is a cloud-backup concept and local device-to-device transfer must
> keep working without a lock screen.

---

## 0. Executive summary

- Today the app ships the untouched AOSP template backup rules; both files are empty, which means **include-everything** for cloud backup and for device transfer. The whole Room database — including launch history with Wi-Fi SSID, latitude/longitude, geohash, and search queries — is uploaded to Google cloud backup on API 31+ devices. This is BUG-11.
- `app/build.gradle.kts:25` sets `minSdk = 34`. **`android:fullBackupContent` / `backup_rules.xml` are dead code on every device this app supports** (they apply only to API ≤ 30). Only `dataExtractionRules` / `data_extraction_rules.xml` is operative. The design must still keep `backup_rules.xml` consistent for correctness/documentation, but it cannot affect any shipped install.
- **Recommendation: Option B (split the sensitive launch history into its own Room database, exclude it from cloud backup) as the target, preceded by an immediate Option C stopgap rule change (exclude the whole database from cloud backup) to close BUG-11 before the split lands.** Option A alone (encrypt + toggle) leaves sensitive data on Google's servers and does not satisfy the finding. Option C alone throws away the feature.
- The main cost of B is a cross-database one-time migration (Room cannot auto-migrate data between two `RoomDatabase` instances) and a second schema export. The main benefit is that tags/settings/aliases keep automatic backup while SSID + geo + query history does not leave the device (except local device transfer, which is a deliberate policy choice).
- Constraint surfaced by the design: **backup XML is static; a real runtime user toggle is not achievable with the XML alone.** It requires a custom `BackupAgent`. See §D.

---

## A. Exactly what is backed up today, in file terms

### A.1 Manifest wiring

`app/src/main/AndroidManifest.xml`:

```xml
android:allowBackup="true"                                    <!-- line 27 -->
android:dataExtractionRules="@xml/data_extraction_rules"      <!-- line 28 -->
android:fullBackupContent="@xml/backup_rules"                 <!-- line 29 -->
```

No `android:backupAgent` is declared anywhere (verified by grep over `app/`; only hits are the three lines above). So the framework's default `FullBackupAgent` is used.

### A.2 Rule files (both are empty templates)

`app/src/main/res/xml/data_extraction_rules.xml` (1–16): a `<data-extraction-rules>` element with `<cloud-backup>` containing only commented-out sample includes/excludes, and the entire `<device-transfer>` block commented out.

`app/src/main/res/xml/backup_rules.xml` (1–13): an empty `<full-backup-content>` with only commented-out sample entries.

Effective semantics: when no `<include>` is present, **all** eligible data is included, and there is no `<exclude>`. Therefore cloud backup and device transfer currently capture everything the default agent collects. Even if a single `<include>` existed, absence of `<exclude>` still leaves that include broad; here there is neither.

### A.3 What "everything" resolves to on disk

Default full backup domains for this package:

| Domain | Path on device | Content found in this app | Backed up today |
|---|---|---|---|
| `database` | `data/data/<pkg>/databases/` | `neurhome_database` + Room WAL sidecars (`-wal`, `-shm`) | Yes |
| `sharedpref` | `data/data/<pkg>/shared_prefs/` | none — no `getSharedPreferences` call anywhere (grep over `app/src/main`, only hit is `getExternalFilesDir`) | n/a |
| `file` | `data/data/<pkg>/files/` | none found | n/a |
| `external` | `getExternalFilesDir(null)` | `app_logcat.txt`, **debug builds only** (`NeurhomeApplication.kt:216`) | Yes on debug builds |
| `root` | `data/data/<pkg>/` | nothing app-specific found | n/a |

Database file name: `NEURHOME_DATABASE = "neurhome_database"` (`AppDatabase.kt:26`), opened via `Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NEURHOME_DATABASE)` (`AppDatabase.kt:76-79`). No `setJournalMode` call, so Room's default `AUTOMATIC` journal mode applies (WAL on modern Android) — **the `-wal`/`-shm` sidecar files exist [UNVERIFIED behaviorally]**; this matters for rule path matching and for restore consistency (§C.3, §F.5).

### A.4 What is inside the database (the sensitive payload)

Single Room DB, `version = 20` (`AppDatabase.kt:28-30`), entities registered at `AppDatabase.kt:29`:

- `Setting(key, value)` (`Setting.kt:8-10`). Keys actually persisted (`SettingsRepository.kt:24-59`): `log.wifi`, `log.position`, `show.calendar`, `show.contacts.starred`, `show.alternative.time`, `alternative.time.zone`, `home.app.selection`.
- `ApplicationLogEntry` (`ApplicationLogEntry.kt:19-29`): `packageName`, `timestamp`, **`wifi` (SSID)**, `wifiState`, **`latitude`**, **`longitude`**, **`geohash`**, `user`, **`query` (search text)**. This is the launch history and the payload the privacy finding is about.
- `AdditionalPackageMetadata(packageName, user, hideFrom, alias)` (`AdditionalPackageMetadata.kt:17-21`).
- `Tag(name, position)` (`Tag.kt:7-9`).
- `ApplicationTag(packageName, profile, tagName)` (`ApplicationTag.kt:5-8`).

### A.5 The one existing export path

`Settings` → "Export database" (share sheet) calls `NeurhomeRepository.exportDatabase` (`SettingsScreen.kt:98`, `SettingsViewModel.kt:65-66`, `NeurhomeRepository.kt:485-500`). It runs `pragma wal_checkpoint(full)` (`:490`), resolves a `content://` URI for `getDatabasePath(NEURHOME_DATABASE)` via `NeurhomeFileProvider.getDatabaseURI` (`NeurhomeFileProvider.kt:11-13`, backed by `provider_paths.xml` files-path `../databases/`), and shares the raw, unencrypted database file. This is a **local, user-initiated, manual** export — not automatic, and it also contains the sensitive history. It is the natural anchor for Option C, but note it is not encrypted and not scheduled.

### A.6 The restore path that already exists

"Replace database" (`SettingsScreen.kt:107-120`) feeds a picked file to `NeurhomeApplication.replaceDatabase(u)` (`NeurhomeApplication.kt:172-178`), which closes the DB, copies the picked stream over `getDatabasePath(NEURHOME_DATABASE)`, then `rebindDataLayer()` (`:189-200`) drops the Room singleton and rebuilds repositories. Any restore design must keep this path working, and it is also the mechanism a system restore lands behind.

---

## B. Option space with tradeoffs

### Option A — Full database backup, user control + `clientSideEncryption`

Keep everything in the backup, add a user toggle and `requireFlags="clientSideEncryption"` on the includes.

- Pros: smallest code change; only XML + a settings row. CSE means Google's servers only ever hold ciphertext and the key is derived from the user's lock screen.
- Cons: **does not resolve BUG-11 as stated.** Sensitive history (SSID, exact coordinates, queries) still leaves the device. CSE's key is lock-screen-derived, so a device with no lock screen gets no CSE (with `requireFlags`, the data is then simply skipped — which is accidental privacy, not designed). Consent is via the toggle, but the data is still uploaded, and users rarely understand what a launcher log contains. Requires a `BackupAgent` if the toggle is to be authoritative (§D).
- Effort: low. Residual risk: sensitive data on Google infra; no fix for already-uploaded backups.

### Option B — Back up the DB but exclude sensitive history (separate Room database)

Move `ApplicationLogEntry` into a second Room database (`neurhome_history_database`) and exclude that file from cloud backup; keep `neurhome_database` (settings, tags, aliases) backed up.

- Why a split is required: a Room database is one file; XML backup rules operate on **files and directories**, never on tables. There is no per-table exclusion. So "back everything except history" is impossible without physically separating the history bytes.
- Pros: the only option that both (i) keeps automatic backup of the data a user would be upset to lose (tags, category order, settings, aliases) and (ii) removes SSID/geo/query history from cloud upload. History can still ride **device transfer** (local, user's own devices) if we choose.
- Cons: highest effort and highest migration risk.
  - Needs a second `@Database` class + DAO wiring + a second schema export directory under `app/schemas/` (currently one: `app/schemas/ovh.litapp.neurhome3.data.AppDatabase`, per AGENTS.md the exported schemas must be committed).
  - Room cannot auto-migrate data **between two database instances**. A one-time startup migration must: open the old `ApplicationLogEntry` table in `neurhome_database` v20, stream rows into the new history DB, then drop the table and bump the main DB version with an explicit migration. Losing this step silently discards existing users' history.
  - `NeurhomeApplication.replaceDatabase`/`rebindDataLayer` (`:172-200`) currently assume one DB file; it must close/copy/rebind **two** files.
  - `NeurhomeRepository.exportDatabase` (`:485-500`) and the ranker source (`RoomHomeAppRankingSource`, wired at `NeurhomeApplication.kt:100-103`) must be re-pointed at the history DB.
  - `provider_paths.xml` (`files-path path="../databases/"`) already covers both files, so export keeps working.
- Effort: high. Residual risk: a restore onto a new device yields an empty history DB (Room creates it on first open) — the ranker must tolerate that, which it does since it runs normal queries that return zero rows.

### Option C — No cloud backup; local-only export via the existing Settings flow

Exclude the database (and everything else) from cloud backup; rely on the existing manual "Export database" / "Replace database".

- Pros: fully resolves BUG-11 — nothing sensitive is uploaded. Trivial change, no migration, no schema risk. The user keeps a real backup path.
- Cons: loses automatic off-device backup for tags/settings; relies on the user remembering to export. No scheduled/automatic story. Manual export is unencrypted (a share-sheet file the user must store themselves). Does not fulfil the "automatic" part of FEAT-01.
- Effort: very low. Residual risk: a lost/stolen phone loses all categories unless the user exported manually.

### Recommendation

**Do B, ship C first.**

1. **Immediate stopgap (C-rules, days of work):** replace the empty `data_extraction_rules.xml` with an explicit `<include>`-less cloud-backup block that excludes the `database` and `external` domains. This stops SSID/geo/query history from being uploaded on the next scheduled backup without touching schema or code. It resolves the *ongoing* BUG-11 leak.
2. **Target (B, one milestone):** split history into `neurhome_history_database`, exclude it from cloud backup, keep the main DB in cloud backup under `clientSideEncryption`, and include history in **device transfer only**. Add the Settings toggle (§D) so the user sees and controls what is backed up.

Reasoning: A is insufficient — encrypting an upload does not answer "why is my Wi-Fi SSID and location history on Google's servers at all", which is exactly BUG-11. C is safe but abandons the automatic-backup feature. B is the only option that preserves automatic backup of the *non-sensitive* data while removing the sensitive payload, and the phase-1 rule change is cheap enough to land independently and immediately.

Fallback if the DB split is rejected as too risky: ship the stopgap C rules permanently and invest the saved effort in making the manual export encrypted and scheduled (WorkManager + SAF). That is a coherent product, just not "automatic via the system framework".

---

## C. Concrete file contents for the recommended option

### C.0 The single wiring change (manifest)

No attribute changes are strictly required — keep exactly:

```xml
android:allowBackup="true"
android:dataExtractionRules="@xml/data_extraction_rules"
android:fullBackupContent="@xml/backup_rules"
```

(`AndroidManifest.xml:27-29`). Add `android:backupAgent=".backup.NeurhomeBackupAgent"` **only** if you adopt the runtime toggle in §D. Keep `allowBackup="true"`; `false` would be a sledgehammer that also disables device transfer and is a bigger user-visible regression than a rules change.

### C.1 `app/src/main/res/xml/data_extraction_rules.xml` — target (Option B, API 31+, the operative file)

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
  API 31+. Neurhome. Automated backup policy.

  Sensitivity: neurhome_database holds tags, tag order, hidden/alias metadata
  and app settings only. neurhome_history_database holds launch history with
  Wi-Fi SSID, latitude/longitude/geohash and search queries, and is NEVER
  sent to cloud backup. A Room database is a single file, so the split into
  two databases is what makes this exclusion possible.

  clientSideEncryption: the included database is encrypted with a key derived
  from the user's lock screen. Devices without a secure lock screen do not
  satisfy the flag and the database is skipped rather than uploaded in clear.
-->
<data-extraction-rules>
    <cloud-backup>
        <include domain="database" path="." requireFlags="clientSideEncryption"/>

        <!-- Sensitive launch history: never cloud-backed up. -->
        <exclude domain="database" path="neurhome_history_database"/>
        <exclude domain="database" path="neurhome_history_database-wal"/>
        <exclude domain="database" path="neurhome_history_database-shm"/>
        <exclude domain="database" path="neurhome_history_database-journal"/>
    </cloud-backup>

    <!--
      Device transfer is a direct, local device-to-device copy, not an upload.
      History is included here so migrating to a new phone keeps the feature;
      remove these includes if that is judged too permissive.
    -->
    <device-transfer>
        <include domain="database" path="."/>

        <exclude domain="database" path="neurhome_history_database"/>
        <exclude domain="database" path="neurhome_history_database-wal"/>
        <exclude domain="database" path="neurhome_history_database-shm"/>
        <exclude domain="database" path="neurhome_history_database-journal"/>
    </device-transfer>
</data-extraction-rules>
```

Notes:
- Because `<cloud-backup>` contains at least one `<include>`, only matching files are cloud-backed up; `sharedpref`, `file`, `external` and `root` are excluded implicitly. Explicit excludes for those domains could be added for self-documentation but are redundant.
- The `-wal`/`-shm`/`-journal` excludes are defensive: exclusion is per exact file name, so excluding only the base name would not necessarily drop the sidecars **[UNVERIFIED: exact sidecar naming and whether the framework already filters WAL files]**.
- `requireFlags="clientSideEncryption"` is only meaningful for `<cloud-backup>`; device transfer does not support it.

### C.2 `app/src/main/res/xml/data_extraction_rules.xml` — phase-1 stopgap (Option C, before the split)

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
  Interim policy: no automatic cloud backup of app data. The single undivided
  neurhome_database contains SSID/location/query history, so it is excluded
  wholesale. Local backup remains available via Settings > Export database.
-->
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="database" path="."/>
        <exclude domain="sharedpref" path="."/>
        <exclude domain="file" path="."/>
        <exclude domain="external" path="."/>
    </cloud-backup>
    <device-transfer>
        <exclude domain="database" path="."/>
        <exclude domain="sharedpref" path="."/>
        <exclude domain="file" path="."/>
        <exclude domain="external" path="."/>
    </device-transfer>
</data-extraction-rules>
```

### C.3 `app/src/main/res/xml/backup_rules.xml` — keep consistent (unused at minSdk 34)

`minSdk = 34` (`app/build.gradle.kts:25`) means API ≤ 30 devices never run this app, so this file is vestigial. Still update it so the two files never disagree (a future minSdk drop would otherwise re-open BUG-11):

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- API 30 and below. Neurhome. Mirrors data_extraction_rules.xml.
     UNUSED at minSdk 34; kept in sync defensively. -->
<full-backup-content>
    <include domain="database" path="." requireFlags="clientSideEncryption"/>
    <exclude domain="database" path="neurhome_history_database"/>
    <exclude domain="database" path="neurhome_history_database-wal"/>
    <exclude domain="database" path="neurhome_history_database-shm"/>
    <exclude domain="database" path="neurhome_history_database-journal"/>
    <exclude domain="external" path="."/>
</full-backup-content>
```

`requireFlags="clientSideEncryption"` is accepted from API 28 on this file; on API 28–30 devices without a secure lock screen the database include is skipped.

### C.4 Optional: checkpoint before a system backup

The manual export already checkpoints WAL (`NeurhomeRepository.kt:490`). The system backup path does not run app code, so a database captured while its WAL is hot may restore with a stale main file unless the framework itself checkpoints **[UNVERIFIED]**. Mitigation if testing shows inconsistency: register a `BroadcastReceiver` for `android.intent.action.MY_PACKAGE_REPLACED`/schedule a periodic `pragma wal_checkpoint(TRUNCATE)` on app background, or switch the main DB to `JournalMode.TRUNCATE` if write volume allows.

---

## D. User-facing control design

### D.1 The hard constraint

Backup rules are static XML compiled into the APK. They cannot read a preference at runtime. Therefore:

- With the **default agent + XML only**, there is no real in-app on/off switch; the only control is the system Settings backup toggle.
- A genuine in-app toggle requires `android:backupAgent=".backup.NeurhomeBackupAgent"` and a small `BackupAgent` that reads the preference and either performs or skips the backup.
- **Do not pretend the XML is toggleable.** Either implement the agent, or word the control honestly.

### D.2 Recommended control (with the split in place)

Because the sensitive history is already excluded by rule, the toggle governs only the non-sensitive remainder, so **default ON** matches Android norms and does not re-open BUG-11.

- Location: `SettingsScreen.kt`, directly under `ExportDatabase(…)` (line 98 anyway) and above "Replace database".
- Row shape: reuse the existing `Row { Text(...) ; Checkbox(...) }` pattern used by `ShowCalendar`/`LogWiFi` (`SettingsScreen.kt:232-291`) so it looks native to the screen. No new permission is involved, so it can be a plain `Checkbox`, not `SettingWithPermission`.
- Wording: **"Back up settings and categories to Google"** with a subtitle **"Launch history, Wi-Fi names and locations are never backed up."** The subtitle is the BUG-11 disclosure.
- Preference key: `backup.cloud.enabled`, added to `SettingsRepository` next to the existing keys (`SettingsRepository.kt:30-59`) and surfaced through `SettingsViewModel.uiState`/`Settings` (`SettingsViewModel.kt:29-58, 78-86`). Even with the agent, store this in a `shared_prefs` file that is itself excluded by rule so it can be read before any backup decision.
- Behaviour: toggling calls a new `ISettingsViewModel.setCloudBackupEnabled(Boolean)`, writes the preference, and (agent path) is picked up on the next backup cycle. When set OFF, the agent short-circuits `onFullBackup`/`onBackup` so nothing is sent.

### D.3 testTags

- Add **one** new constant, e.g. `const val SettingsCloudBackupToggleTestTag = "settings-cloud-backup-toggle"`, next to the existing block at `SettingsScreen.kt:42-45`.
- **Existing testTags must not change.** `HomeAppSelectionSelectorTestTag`, `HomeAppSelectionClassicTestTag`, `HomeAppSelectionLearnedTestTag`, `HomeAppSelectionPrerequisiteTestTag` (`SettingsScreen.kt:42-45`) are driven by `HomeAppSelectionSelectorTest` and are exposed as UiAutomator `resource-id`s via `MainActivity`'s `testTagsAsResourceId` (`AGENTS.md`). Renaming or re-nesting them is a regression.
- Keep the new tag stable too: it will become a `resource-id`.

### D.4 Without the agent

If the agent is not adopted, do **not** ship a checkbox that does nothing. Instead show a static explanatory row (no toggle) plus a button that deep-links to the system backup screen, and document that history is excluded by rule.

---

## E. Test strategy

### E.1 What JVM tests can assert

There is no Robolectric dependency (grep over `build.gradle.kts`/version catalog: `testImplementation` is JUnit only), and the Android backup engine is a system service that no JVM/Robolectric shim reproduces faithfully. So:

1. **Static rules regression test (highest value, cheap).** A plain JUnit test under `app/src/test/java/ovh/litapp/neurhome3/backup/BackupRulesTest.kt` that parses the two checked-in XML files with `javax.xml.parsers.DocumentBuilderFactory` (Gradle unit tests run with the module directory as CWD, so `File("src/main/res/xml/data_extraction_rules.xml")` works) and asserts:
   - `data_extraction_rules.xml` cloud-backup contains an `<exclude domain="database" path="neurhome_history_database"/>` (or, in phase 1, `<exclude domain="database" path="."/>`).
   - no `<include ... path="neurhome_history_database">` exists;
   - the same holds for `backup_rules.xml`;
   - `AndroidManifest.xml` still contains `android:dataExtractionRules="@xml/data_extraction_rules"` and `allowBackup="true"` (grep-style assertion, prevents silent attribute removal).
   This catches "someone deletes the exclude" in PR CI using the existing `:app:testDevDebugUnitTest` target.
2. **Policy unit test** for the agent: extract the decision into a pure function, e.g. `object BackupPolicy { fun shouldUpload(databaseName: String, cloudEnabled: Boolean): Boolean }`, and unit-test it with `SettingsRepositoryTest`-style fakes (compare `SettingsRepositoryTest.kt:20-43` for the fake DAO pattern; `CoroutineScope(Dispatchers.Unconfined)` per AGENTS.md).
3. **What JVM cannot assert:** that the framework actually skips the file at runtime. Do not fake confidence here.

### E.2 Robolectric

Not currently configured. Adding it would be a new dependency for little gain: `ShadowBackupManager` stubs the `BackupManager` API and does not execute backup rules, so it cannot validate §C. If the policy function from E.1.2 is pure, Robolectric adds nothing.

### E.3 Instrumentation tests

- Assert manifest wiring: `PackageManager.getApplicationInfo(pkg, 0)` → `flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0`, and that `fullBackupContent` / `dataExtractionRules` resource IDs resolve to the expected `R.xml.*`. A single test in `app/src/androidTest` (run via `:app:connectedDevDebugAndroidTest`).
- Compose test for §D: drive `HomeAppSelectionSelector`-style stateless Composable, i.e. render the backup toggle row with a fake callback and assert the new testTag toggles; mirror `HomeAppSelectionSelectorTest.kt:1-60` (uses `createAndroidComposeRule<ComposeTestActivity>`). Per AGENTS.md, wake the device (`adb shell svc power stayon true`) before connected tests.

### E.4 Manual `bmgr` smoke procedure

Use the **local transport** on an emulator. Caveat up front: the local transport does **not** advertise `clientSideEncryption` **[UNVERIFIED]**, so with `requireFlags="clientSideEncryption"` the database include will be skipped and the smoke test will appear to restore nothing. For the content test, temporarily drop `requireFlags` (or test against a Google transport where the flag is satisfied and the user has a lock screen), then restore the flag and confirm via `adb shell dumpsys backup` / transport flags that the intended behaviour holds.

```
# 0. Build/deploy dev and wake the device
./gradlew :app:assembleDevDebug
adb shell svc power stayon true
adb install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk
# resolve the actual dev package id (hostname suffix varies per machine)
adb shell pm list packages --user 0 | grep neurhome

# 1. Seed data in the app: create a tag, reorder, launch an app (creates history),
#    toggle a setting, then background/close the app.

# 2. Point bmgr at the local transport
adb shell bmgr list transports
adb shell bmgr transport com.android.localtransport/.LocalTransport

# 3. Force a backup
adb shell bmgr enabled
adb shell bmgr backupnow <pkg>          # expect "Success"

# 4. Wipe app data (simulates a fresh install, keeps the backup set)
adb shell pm clear <pkg>

# 5. Restore
adb shell bmgr restore <pkg>
# launch the app once, then inspect:
#   - tag name + position and the settings toggles are back  -> main DB restored
#   - launch history rows are absent                          -> history excluded
adb shell run-as <pkg> sqlite3 databases/neurhome_database \
  "select count(*) from ApplicationLogEntry;"   # [UNVERIFIED: sqlite3 present on emulator]
```

Additional checks:
- Inspect what was captured: `adb shell dumpsys backup` and, where the transport stores plaintext, read the tar under the local transport's backup dir to confirm no `neurhome_history_database*` entry.
- Device transfer cannot be scripted with `bmgr`; it needs two devices or an emulator clone. At minimum verify the `<device-transfer>` block parses via the JVM rules test and rely on the Android docs for semantics.
- Re-run once with `BackupManager.dataChanged()` triggered (or after the next scheduled window) to confirm the exclude survives a second cycle.

---

## F. Migration / rollout risk and BUG-11 resolution per option

### F.1 Existing installs already have the empty rules

- The empty template files (`backup_rules.xml:1-13`, `data_extraction_rules.xml:1-16`) mean current installs have been uploading the full DB (subject to the user having Google backup enabled and being signed in; that precondition is outside app control).
- Changing the rules **does not delete data already uploaded**. For an install that has already been backed up, the old cloud snapshot still contains SSID/geo/query history until Google's 60-day backup expiry or until the user clears backups (Settings → Google → Backup, or `adb shell bmgr wipe <pkg>`). **This must be stated in the BUG-11 closure note**: the code fix stops future uploads; it cannot retract past ones.
- Restoring an **old** backup onto a new device will still restore the full DB (including history) because the snapshot predates the change. So after a restore, history may reappear even with the new rules installed. The rule change governs future backup, not restore of legacy snapshots.
- Because `minSdk = 34`, only the `dataExtractionRules` change has runtime effect. Touching `backup_rules.xml` is bookkeeping, not a fix on shipped devices — do not claim it as the fix.

### F.2 Risk of over-blocking

If phase 1 (Option C rules) accidentally excluded something the user needs, restore becomes empty. The JVM manifest/rules test (§E.1.1) plus the `bmgr` smoke (§E.4) are the guardrails. `allowBackup` stays `true`; only rules change, so device transfer and the manual export are untouched.

### F.3 Risk of the Option B split (the main design risk)

- **Cross-DB data migration is manual and easy to get wrong.** Room auto-migrations run within one `RoomDatabase`; moving rows from `neurhome_database.ApplicationLogEntry` to `neurhome_history_database` needs an explicit startup step: read from the old table, insert into the new DB, `DROP TABLE ApplicationLogEntry`, then bump the main `@Database` version (currently `20`, `AppDatabase.kt:28-30`) with an explicit `Migration`. Committing the new schema JSON under `app/schemas/` is mandatory (AGENTS.md).
- **Restore/rebind path must handle two files.** `replaceDatabase` (`NeurhomeApplication.kt:172-178`) copies exactly one file; `rebindDataLayer` (`:189-200`) forgets one Room instance. Both must be extended, and `AppDatabase.forgetInstance()` (`AppDatabase.kt:90-93`) needs a sibling for the history DB.
- **Ranker tolerance.** On a device where the history DB is newly created and empty, `RoomHomeAppRankingSource` (`NeurhomeApplication.kt:100-103`) must return zero rows rather than fail. Normal queries do; verify before shipping.
- **Two schema exports.** `room { schemaDirectory("$projectDir/schemas") }` (`build.gradle.kts:12-14`) currently produces one schema dir; a second `@Database` adds a second, both must be committed.
- **Downgrade.** If a user installs an older APK after the split, the old code looks for `ApplicationLogEntry` in `neurhome_database` and finds it dropped. This is the usual Room downgrade hazard; decide whether to keep the old table read-only for one release.

### F.4 Rollout order (safe)

1. Land phase-1 `data_extraction_rules.xml` (Option C rules) + the JVM rules test. Ship. BUG-11 upload stops.
2. Land the Option B split behind a normal release, with the one-time migration, the second DB, and the dual-file restore/rebind path. Verify with the `bmgr` smoke.
3. Land the Settings control (§D) and, if adopted, the `BackupAgent`.
4. Flip the rules from "exclude whole DB" to "include main DB, exclude history DB" **only after** step 2 is fully shipped, otherwise the include would cover a still-undivided DB and re-open BUG-11.

### F.5 Restore-consistency note (applies to all options)

WAL mode plus a non-checkpointed backup can restore a stale main DB file. The manual export already checkpoints (`NeurhomeRepository.kt:490`); the system path does not run app code. If the smoke test shows missing recent rows after restore, add a checkpoint on lifecycle background or switch journal mode. **[UNVERIFIED]**

### F.6 BUG-11 resolution per option

| Option | Ongoing cloud leak | Already-uploaded data retracted | Verdict |
|---|---|---|---|
| A (full DB + CSE + toggle) | No, if CSE satisfied and toggle ON; but data still leaves the device | No | **Partial** — mitigates, does not remove, the sensitive upload |
| B (split history, excluded from cloud) | No — history never uploaded | No | **Resolved** (future uploads); history stays on device except local device transfer |
| C (no cloud backup) | No — nothing uploaded | No | **Resolved** in the strongest sense; sacrifices automatic backup |

In every option, the historical cloud snapshot must be called out as out of reach of the code change. BUG-11 closure text should read: "from this version onward, launch history (SSID, location, queries) is not included in cloud backup; previously uploaded backups expire under the user's Google backup retention or can be cleared by the user."

---

## Open questions / unverified items

1. Whether the framework already filters `-wal`/`-shm`/`-journal` for the `database` domain when building a full backup — affects whether the extra excludes in §C are needed and whether a pre-backup checkpoint is required. **[UNVERIFIED]**
2. Whether a custom `BackupAgent`'s default `onFullBackup` still honours `android:fullBackupContent`/`dataExtractionRules`, or whether the agent must reimplement inclusion for the §D toggle to be authoritative. **[UNVERIFIED]** — test with `bmgr` before relying on it.
3. Whether the local transport advertises `clientSideEncryption`; if not, the `bmgr` smoke must temporarily drop the flag. **[UNVERIFIED]**
4. Whether Room's `AUTOMATIC` journal mode yields WAL sidecar files in this app's database directory. **[UNVERIFIED]**
5. No Robolectric/backup shim exists, so runtime rule application can only be validated on a device via `bmgr`.

---

## G. Implemented follow-up: a custom `BackupAgent` compresses the database payload

`android:backupAgent=".backup.NeurhomeBackupAgent"` now owns the Room database, because the raw
`neurhome_database` (measured at 26,079,232 bytes) was over the 25 MB Auto Backup cloud quota and
was very likely being skipped silently. A parallel lane shrinks the raw database (2-year retention
+ index removal, target ~8.5 MB); this lane is insurance on top and brings the payload well under
the quota regardless.

### G.1 What the agent does (`backup/NeurhomeBackupAgent.kt`)

- `onFullBackup` calls `super.onFullBackup(data)` first, so every non-database domain keeps the
  existing `data_extraction_rules.xml` / `backup_rules.xml` scheme and the per-include
  `requireFlags="clientSideEncryption"` behaviour.
- It then appends the database as a gzip payload via `BackupAgent.fullBackupFile`, but only when
  `shouldUploadDatabasePayload(transportFlags)` is true.
- The payload temp file is `databases/neurhome_database.backup.gz`, a sibling of the database.
  It is deliberately **not** in the cache directory: `BackupAgent.fullBackupFile` logs
  "lib, cache, code_cache, and no_backup files are not backed up" and returns without writing
  anything for files under those trees, and only files inside a recognised domain get a backup
  path the restore side can route back. A sibling of the database is recorded in the `database`
  domain, so restore delivers it to `onRestoreFile` as `<databases>/neurhome_database.backup.gz`.
- Before compressing, it runs `PRAGMA wal_checkpoint(TRUNCATE)` on a direct `SQLiteDatabase`
  connection (not through Room, to avoid triggering migrations during a backup). Room opens the
  database in WAL mode, so copying the main file alone can miss the newest committed rows still
  in `-wal`. The checkpoint's `busy` column is logged; a busy checkpoint means the copy may be
  incomplete, but it must not abort the backup.
- The temp payload is deleted in a `finally` block, and a stale payload left by a crashed run is
  deleted before `super.onFullBackup` so it can never be picked up by the device-transfer include.

### G.2 Transport-flag branching (why the database is not uploaded twice)

`shouldUploadDatabasePayload(transportFlags)` returns
`(flags and FLAG_CLIENT_SIDE_ENCRYPTION_ENABLED) != 0 && (flags and FLAG_DEVICE_TO_DEVICE_TRANSFER) == 0`.

- **Cloud backup, CSE available**: the `<cloud-backup>` section omits the database domain, so the
  framework's own pass uploads no raw database; the agent adds the compressed payload. This is the
  only path that carries the database to the cloud.
- **Cloud backup, no CSE** (device without a secure lock screen): every cloud include is
  ineligible because of `requireFlags="clientSideEncryption"`, and the transport has no CSE flag,
  so the framework uploads nothing at all; `shouldUploadDatabasePayload` also returns false, so the
  agent adds nothing. Net effect: cloud backup is skipped entirely, while device transfer still
  carries the full raw database.
- **Device-to-device transfer**: `<device-transfer>` still includes the database, so
  `super.onFullBackup` carries the raw file; the CSE flag is absent **and** the D2D flag is present,
  so `shouldUploadDatabasePayload` returns false and the agent adds nothing. This is what prevents
  a D2D transfer from containing both a raw and a compressed copy of the same database.

### G.3 The database domain is omitted from `<cloud-backup>`, not `<exclude>`d

`data_extraction_rules.xml` (and the legacy `backup_rules.xml`) list every domain **except**
`database` in their cloud/default include list. Because a section with any `<include>` is narrowed
to exactly the listed paths, the missing include already excludes the raw database.

A whole-domain `<exclude domain="database" path="."/>` is deliberately **not** used: Android
Lint's `FullBackupContent` check (severity FATAL) reports "`. is not in an included path`" for any
exclude whose domain has no matching include, which would fail `:app:lintDevDebug`. Omitting the
include is therefore the lint-clean way to express the same thing, and it keeps the explicit-domain
invariant (and the CSE `requireFlags`) for every other domain.

### G.4 Client-side encryption is enforced in code, not by the XML

Declaring a custom agent takes over the backup path. `BackupAgent.onFullBackup` is the code that
consults the rule XML, but this agent overrides it and adds a file with `fullBackupFile`; the
`requireFlags="clientSideEncryption"` attribute therefore does **not** govern the database payload.
`shouldUploadDatabasePayload` re-imposes CSE directly from `FullBackupDataOutput.getTransportFlags()`
(verified constant: `BackupAgent.FLAG_CLIENT_SIDE_ENCRYPTION_ENABLED == 1`,
`FLAG_DEVICE_TO_DEVICE_TRANSFER == 2`). No code path writes the database payload without CSE.

### G.5 Restore

`onRestoreFile` intercepts the payload by name (`neurhome_database.backup.gz`, `TYPE_FILE`),
decompresses it into `databases/neurhome_database.restoring`, then `Files.move(..., REPLACE_EXISTING)`
over the live database and deletes any stale `-wal`/`-shm`/`-journal` sidecars — a leftover log
would otherwise be replayed over the restored file. It reads the `ParcelFileDescriptor` with a
`FileInputStream` wrapped so `close()` is a no-op, mirroring the framework's own
`FullBackup.restoreFile`, which leaves closing the descriptor to the restore engine; closing it in
the callback would double-close the same fd. Every other file (including a raw `neurhome_database`
from a legacy or device-transfer stream) is delegated to `super.onRestoreFile`. If no payload is
present, nothing special happens and `onRestoreFinished` does not touch the database.

`onRestoreFinished` calls `AppDatabase.forgetInstance()` only when a payload was actually restored.
Conclusion on the open Room handle: the backup agent runs as the app component and can run inside a
process that already opened the database, but the restore framework restarts the app process after
a full restore. Dropping the singleton guarantees the next `getDatabase()` reopens the restored
file; closing the existing handle is not attempted because the agent has no supported handle to the
`NeurhomeApplication` singleton and `AppDatabase` only exposes `forgetInstance()` (the same call
`replaceDatabase` uses).

### G.6 Over-quota visibility

`onQuotaExceeded(backupDataBytes, quotaBytes)` (exact signature for API 24+) logs a warning naming
both sizes. It cannot raise the quota; it exists only so an over-quota condition is visible in
logcat instead of silent.

### G.7 Manual verification procedure (adb, sideloaded build)

This uses the **local transport**, which is the only transport scriptable with `bmgr`:
```

# 0. Build and sideload the dev APK; resolve the actual dev package id (suffix varies per host)
./gradlew :app:assembleDevDebug
adb install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk
adb shell pm list packages --user 0 | grep neurhome    # e.g. ovh.litapp.neurhome3.dev.<host>

# 1. Seed data in the app: create and reorder tags, launch an app (creates history), toggle a
#    setting, then close the app.

# 2. Point bmgr at the local transport and force a full backup
adb shell bmgr list transports
adb shell bmgr transport com.android.localtransport/.LocalTransport
adb shell bmgr enabled
adb shell bmgr backupnow <pkg>                          # expect "Success"

# 3. Wipe app data (keeps the transport's backup set)
adb shell pm clear <pkg>

# 4. Restore
adb shell bmgr restore <pkg>
# launch the app once, then check the seeded tags/settings/history are present again

# 5. Inspect transport state and the agent's log lines
adb shell dumpsys backup | grep -i <pkg>
adb logcat -d -s NeurhomeBackupAgent
```

**What this procedure does and does not prove.** The local transport does **not** advertise
client-side encryption, and it is a cloud-style transport, so its transport flags are `0`: every
`<cloud-backup>` include requires CSE and is skipped, and `shouldUploadDatabasePayload` returns
false. `bmgr backupnow` therefore reports "Success" with an **empty** payload and nothing is
restored. So the local transport exercises only the "agent is declared, instantiated, and does not
crash" path; it does **not** prove the compressed-payload backup, the CSE gate, the checkpoint, or
the restore path. It also bypasses the 25 MB cloud quota. Device-to-device transfer cannot be
scripted with `bmgr` at all (it needs two devices or an emulator clone).

### G.8 Remains UNVERIFIED without a real device backup/restore cycle

1. That the compressed `neurhome_database.backup.gz` payload is actually uploaded to the Google
   transport on a locked device and restored correctly — needs the Google transport + secure lock
   screen. **[UNVERIFIED]**
2. That `onRestoreFile` receives the payload as `<databases>/neurhome_database.backup.gz` (domain
   routing of an app-decided `fullBackupFile` path). **[UNVERIFIED on device]**
3. That the `requireFlags="clientSideEncryption"` includes and the in-code CSE gate produce an
   empty cloud backup on an unlocked device. **[UNVERIFIED]**
4. That the `wal_checkpoint(TRUNCATE)` succeeds while the app holds the database open, and that the
   restored file is consistent. **[UNVERIFIED]**
5. That the total payload (compressed database + other domains) now fits the 25 MB quota, and that
   `onQuotaExceeded` fires only when it genuinely does not. **[UNVERIFIED]**
6. That a real device-to-device transfer carries exactly one copy of the database. **[UNVERIFIED]**
