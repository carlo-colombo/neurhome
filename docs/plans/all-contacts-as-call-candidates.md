# Refined Spec — All Contacts as Call Candidates

Status: accepted and implemented. The four open decisions were resolved as: tri-state
selector, new enum key, number-level dedupe in every mode, lazy thumbnail loading.

## Goal

Let any contact with a phone number be a call candidate, not only starred ones, behind an
opt-in in Settings that is off by default and gated on `READ_CONTACTS`.

"Call candidate" means the contact row can be searched from Home (T9) and launched by its
`tel:` intent, appears in the merged application/contact list, and — once launched — is
ranked and resolved exactly like an application row (it is logged under its call URI, which
is its row identity). See REF-10 / REF-16 in `docs/backlog.md`.

## Non-goals

- No change to Home top-app ranking rules. Ranking is score-driven and unlaunched contacts
  have no log rows, so enabling all contacts does not push zero-history rows into the top
  list.
- No new persistence of contact data: rows stay in-memory; only launched `tel:` keys reach
  `ApplicationLogEntry`, exactly as today. No Room schema change (Settings is a key/value
  table, so `NEURHOME_DATABASE_VERSION` is untouched and no schema export changes).
- No new permission, no per-contact selection UI, no "favourites from all contacts" work
  beyond what key resolution already does.

## Current behaviour (verified before the change; line numbers refer to that state)

- `ContactsDAO.getStarredContacts()` (the read this feature replaces) (`data/dao/ContactsDAO.kt:35`) queries
  `Phone.CONTENT_URI` with `selection = "STARRED='1'"` (`:49`), builds one `Application`
  per row with `packageName = tel:` URI (`:69`), and collapses the result with
  `groupBy { label }`.
- `NeurhomeRepository.contacts` (`:124`) is the shared `StateFlow`: gated on
  `settingsRepository.showStarredContacts` + `READ_CONTACTS` (`:125-129`), re-read every
  5 minutes by `ticker` (`:110-114`), `distinctUntilChanged`, `stateIn` with
  `replayExpirationMillis = Long.MAX_VALUE`.
- Consumers: `HomeRankingCoordinator` (row resolution for logged `tel:` keys),
  `applicationAndContacts` (`:220`, feeds Home search and All Applications),
  `getSimulatedTopApps` (`:292`), `FavouritesRepository` slots.
- Settings row `ShowStarredContacts` ("Show Starred Contacts") is a
  `SettingWithPermission` toggle persisted as `show.contacts.starred`
  (`SettingsRepository.kt:39,59-60`).

## Design

### Flag shape

Replace the single boolean with a three-way, mutually exclusive choice, because "all" and
"starred" are not independent (all ⊃ starred) and two checkboxes admit a contradictory
state:

| Option | Rows loaded |
| --- | --- |
| `NONE` (default) | none — today's "off" |
| `STARRED` | `STARRED='1'` — today's "on" |
| `ALL` | every contact with a phone number |

- New setting key `contacts.call.candidates` holding `NONE | STARRED | ALL`; unknown or
  absent value → `NONE`. A pre-existing `show.contacts.starred=true` maps to `STARRED`, so
  an upgrading user keeps today's behaviour; the old key is left in place (read-only
  fallback) so a downgrade still works.
- Stored as an enum + `fromStoredValue` exactly like `HomeAppSelection`
  (`SettingsRepository.kt:13-24`), with a grant-gated setter mirroring `setGrantGated`
  (`:85-88`): turning the flag off is always persisted; turning it on requires the grant.
- Settings UI: one stateless composable (`ContactCandidateSelector`) plus a
  permission-aware wrapper that reuses `SettingWithPermission`'s grant-observe pattern
  — permission request, `pendingGrantWrite`, and revocation-turns-off behaviour must stay
  identical. Stable test tags as with `HomeAppSelectionSelector*`.
- `SettingsUiState.starredContacts: Boolean` → `contactCandidates: ContactCandidates`, and
  `ISettingsViewModel.setShowStarredContacts` → `setContactCandidates`. Update the four
  call sites and both settings tests.

### Contact read

- `ContactsDAO.getStarredContacts()` → `getContacts(includeUnstarred: Boolean)`:
  `selection = if (includeUnstarred) null else "STARRED='1'"`. Keep the projection, the
  blank-`displayName` guard and the `tel:` identity. A row whose provider left
  `NORMALIZED_NUMBER` null falls back to the stored `NUMBER` (normalized locally), so an
  address book entry another tool inserted does not silently disappear.
- `NeurhomeRepository.contacts` keys off the enum; `NONE`/no grant short-circuits to
  `emptyList()` without touching the provider.
- Deduplication: collapse by call URI identity (`tel:` + normalized number) instead of
  `groupBy { label }`. Label dedupe silently drops every second number of a contact and
  every contact sharing a display name — tolerable for a short starred list, a real
  regression for a full address book. This also keeps row identity 1:1 with the logged
  key, which the ranking/favourites resolvers assume.

### Scale and refresh

The full address book changes the cost profile; the following are part of the work, not
optional polish:

- **Photos are loaded lazily, for every mode.** The DAO no longer decodes anything: a row
  carries the thumbnail URI in the new `Application.photoUri` and `icon` stays null. The UI
  resolves the photo through `rememberContactPhoto` (`ui/components/ContactPhoto.kt`) only
  for the rows it composes, off the main thread, through the process-wide `ContactPhotos`
  LRU owned by `NeurhomeApplication`. `ApplicationIcon` and the favourites bar both go
  through it. A list read therefore performs zero thumbnail reads, and a starred row no
  longer pays an eager decode either.
- **Refresh.** The provider has no invalidation signal, so the list keeps the existing
  5-minute tick for every scope and a scope change re-reads immediately
  (`combine(ticker, contactCandidates)` in `NeurhomeRepository.contacts`). A slower `ALL`
  tick was considered and rejected: with photos lazy, a full address-book read is only a
  cursor scan, and a slower tick would leave a newly added contact invisible for longer than
  the starred behaviour it replaces.
- **Search cost.** `filterApplicationsForQuery` regex-matches and sorts the whole merged
  list per keystroke (`domain/home/HomeApplicationFilter.kt:16-28`, capped at
  `MAX_HOME_APPLICATIONS`). With an address book the list grows by 2–3 orders of
  magnitude. Verify on a real device with a large address book before calling this done;
  if keystrokes lag, precompute a lowercase label match string per row rather than
  changing the matching semantics.

### Privacy

Enabling `ALL` raises what is on screen (names and numbers are searchable and visible in
screenshots) but not what is stored: rows are unpersisted and only a launched number's
`tel:` key is logged, as today. `docs/plans/copyright-and-licensing.md` and the README
"Privacy" wording should mention that the selected scope also determines whether unstarred
contact names are shown, so the description stays accurate.

## Acceptance criteria

1. Default install reads `NONE`; an upgrading user with `show.contacts.starred=true` reads
   `STARRED`, and revoking the contact grant drops the scope to `NONE`.
2. With `ALL`: an unstarred contact is found by T9 search on its name, launching it dials
   its number, the launch is logged under the `tel:` key, and it then resolves its label,
   photo and call intent in the statistics simulation and the Home ranking.
3. With `NONE`/`STARRED` no unstarred contact appears anywhere and the provider is not
   queried beyond the starred selection.
4. Two numbers on one contact and two contacts sharing a display name each produce one row
   per number, each launchable independently; a number the provider returns twice is one row.
5. Rows with a null/blank name or number are skipped, not crashed on.
6. A contact-list read performs no thumbnail read in any mode; photos are decoded once per
   URI, only for rows that are composed, and at most 64 are retained.
7. `:app:assembleDevDebug`, `:app:testDevDebugUnitTest`, `:app:testProdDebugUnitTest`,
   `:app:lintDevDebug` pass; Room schema export is unchanged.

## Tests to add/update

- `data/dao/ContactsDaoTest`: `includeUnstarred=false` keeps the starred selection
  assertion (projection + selection through the fake provider); `true` returns unstarred
  rows and passes a null selection; number-level dedupe; duplicate number; blank
  name/number guard; `photoUri` carried with no icon and no thumbnail read.
- `data/ContactPhotosTest`: lazy decode, cache reuse, cache bound, unreadable URI.
- `ui/ContactLaunchTrackingTest`: unstarred contact found by a T9 query and launched.
- `data/repositories/SettingsRepositoryTest`: enum round-trip, unknown value → `NONE`,
  `show.contacts.starred=true` fallback → `STARRED`, grant-gating both directions.
- `ui/settings/SettingsViewModelTest`: state mapping and setter.
- `ContactCandidatesSelectorTest` (instrumented): the three-way selector's state and clicks.
- `data/repositories/ContactRowRankingTest`: logged `tel:` key of an unstarred contact
  resolves to its row under `ALL`.
- Optional Compose test of `ContactCandidateSelector` (stateless, like
  `HomeAppSelectionSelector`).

## Task breakdown

1. `ContactCandidates` enum + settings key, fallback, grant-gated setter, repository tests.
2. `ContactsDAO.getContacts(includeUnstarred)`, URI-identity dedupe, `photoUri` instead of a
   decoded icon, DAO tests.
3. Repository wiring: enum-driven `contacts` flow, `ALL` refresh cadence, live tests.
4. Settings UI: permission-aware selector, view model, state, test tags, Compose test, plus
   the lazy `ContactPhotos` loader and `rememberContactPhoto` in the icon call sites.
5. Real-device check with a large address book (search latency, 5-minute refresh, dial
   launch), then docs (README feature bullet, privacy wording, `AGENTS.md` learnings).

## Decisions (confirmed)

1. **UX**: one tri-state selector — `Off`, `Starred contacts`, `All contacts` — rendered by
   the stateless `ContactCandidatesSelector` and gated by `ContactsSection`.
2. **Storage**: new `contacts.call.candidates` key holding the enum name, with the legacy
   `show.contacts.starred` boolean read as a fallback and never written again.
3. **Dedupe**: one row per phone number in both modes (`distinctBy { packageName }`, the call
   URI being the identity), replacing the label-based collapse that dropped a contact's
   second number and merged people sharing a display name.
4. **Photos**: lazy per-row loading — rows carry `photoUri`, the UI decodes on first
   composition through the process-wide `ContactPhotos` LRU.
