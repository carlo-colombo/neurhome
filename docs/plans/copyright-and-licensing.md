# Copyright and Open-Source Licensing Plan

Read-only design. Repo: `/home/carlo/projects/neurhome`. Current-state claims cite `file:line`.
Items I could not verify from source are tagged **[UNVERIFIED]**.

## 0. Objective

Give Neurhome an explicit, correct, and enforceable copyright + open-source license story in three
places, because all three are legally distinct obligations:

1. **The repository** — the license and copyright notices contributors and redistributors read.
2. **The artifact** — the APK that ships to a device must carry the app's own license and the
   third-party attribution for the dependencies it links (currently it carries neither).
3. **The release** — the GitHub release must state the license, point at the Corresponding Source
   for that binary, and attach the notices.

## 1. Current state (verified)

| Item | State | Evidence |
| --- | --- | --- |
| App license | GNU GPL v3 full text already committed | `LICENSE` (674 lines, added 2023-12-23 in `980100d`) |
| Copyright notice | **Missing.** The text is the pristine GitHub template; the appendix still contains the literal placeholders `Copyright (C) <year> <name of author>` | `LICENSE` tail |
| SPDX identifier | **Ambiguous.** Nothing states `GPL-3.0-only` vs `GPL-3.0-or-later` | no `SPDX-License-Identifier` anywhere in `app/src` |
| App copyright holder | Carlo Colombo `<carlo.colombo@gmx.com>` is the sole author | `git shortlog -sne` |
| README license section | **Missing.** README documents features/stack/build only | `README.md` (no "License" heading) |
| In-APK license | **The app's own license was missing** (no `assets/`, no `res/raw/`, no root `META-INF/LICENSE`). The APK did carry per-artifact `META-INF/<group>/<artifact>/LICENSE.txt` files for some AndroidX libraries, but nothing stated the app's license. | `app/src/main/res/` had no `raw/`; `app/src/main` had no `assets/`; pre-change `unzip -l` showed only `META-INF/**/LICENSE.txt` |
| Dependency attribution | **Incomplete.** AGP drops the generic `AL2.0`/`LGPL2.1` notice files (`app/build.gradle.kts:126-128`), and the surviving `META-INF/**/LICENSE.txt` files cover only some artifacts — Ktor, kotlinx, `ch.hsr:geohash` and `org.slf4j:slf4j-api` ship no license file at all. | pre-change `unzip -l` of the dev APK; `gradle/libs.versions.toml` |
| In-app OSS screen | **Missing.** Nav graph has Home / ApplicationList / Categories / AppStatistics / Settings only | `MainActivity.kt:64-82`; `SettingsScreen.kt:58` |
| Release assets | APK + `.sha256` only; no license, no notices | `.github/workflows/build.yml` "Release" step |
| CI license check | **None.** `LICENSE` is only listed in the PR `paths-ignore` filter | `.github/workflows/pr.yml:52` |
| Third-party licenses | All declared dependencies are permissive (Apache-2.0 / MIT-class) and GPLv3-compatible **[UNVERIFIED — see Phase 2 audit]** | `gradle/libs.versions.toml` |

The important gap is not the license file — that exists. It is that **the license is inert**: no
copyright line, no stated SPDX version, no README pointer, and no copy in the artifact or the
release.

## 2. Decisions required from the operator (blocking)

These change the text that gets committed, so they must be settled before Phase 1.

- **D1 — License version identifier.** `GPL-3.0-only` (exactly v3) or `GPL-3.0-or-later` (v3 or any
  later version published by the FSF)? The committed file alone does not say. Recommendation:
  `GPL-3.0-or-later` — it is the common choice for a personal copyleft project and preserves the
  option to move to a future GPL without re-consenting to yourself. Pick `-only` if you want to
  pin the exact terms.
- **D2 — Copyright holder string.** Exact legal form to display everywhere, e.g.
  `Copyright (C) 2023-2026 Carlo Colombo`. Confirm the year range (first commit is 2023-12-23;
  current year should be the current year at commit time) and whether a contact address is
  included. The `LICENSE` file itself should stay verbatim, with the copyright recorded in the
  README + `NOTICE` + per-file SPDX headers (see Phase 1) rather than by editing the appendix.
- **D3 — Attribution mechanism.** Two acceptable options for the in-app dependency list:
  - **(a) Automated (recommended):** the AboutLibraries Gradle plugin + Compose screen
    (`com.mikepenz.aboutlibraries.plugin` + `aboutlibraries-compose-m3`; confirm current
    coordinates at implementation time). It generates a machine-accurate license JSON from the
    resolved dependency graph and gives a Material 3 "Open source licenses" screen.
  - **(b) Static:** a hand-maintained `THIRD_PARTY_LICENSES.md`/`NOTICE` shipped as an asset and
    rendered as plain text. Lighter, no new dependency, but it drifts as the dependency graph
    changes.
  Either is compliant for permissive deps; (a) is the one that stays correct over time.
- **D4 — Whether to add per-file SPDX headers.** Adding two header lines to ~all `.kt` files is
  large churn; the lower-noise alternative is a `REUSE.toml` (REUSE spec 3.x) that assigns
  `GPL-3.0-or-later` + the copyright to `**` by default and lets `reuse lint` verify it in CI.
  Recommendation: `REUSE.toml` now, headers only for files that leave the repo's default.

## 3. Phase 1 — Repository metadata

1. Add a `NOTICE` (or `COPYRIGHT`/`AUTHORS`) file at the repo root containing the D2 holder line,
   the D1 identifier, and a one-line pointer to `LICENSE`.
2. Add a `## License` section to `README.md` (after "Technical Stack"): "Neurhome is licensed under
   the GNU General Public License v3.0 or later — see `LICENSE`. `Copyright (C) <D2>`." Include the
   GPL §15/§16 no-warranty sentence.
3. Add `REUSE.toml` (D4) and, if chosen, the AboutLibraries plugin wiring.
4. Keep `LICENSE` **byte-identical** to the canonical GPLv3 text. Do not hand-edit the appendix —
   an unmodified file is trivially verifiable and is what GitHub's license detector keys on.
5. Update `.github/workflows/pr.yml:52` and `.github/workflows/pr.yml:22-23` `paths-ignore` so the
   new non-build metadata (`NOTICE`, `REUSE.toml`, `AUTHORS`) is grouped with `LICENSE` and does
   not trigger the heavy job. **Failing to do this makes a docs-only licensing PR run the full
   Android build.**

## 4. Phase 2 — Artifact (what the APK carries)

Goal: `unzip -p app-dev-debug.apk` shows the app's license and a complete third-party notice.

1. **Dependency license audit (one-off, must precede the notice).** Produce the resolved runtime
   graph (`:app:dependencies --configuration prodReleaseRuntimeClasspath`) and record each
   dependency's license. Expected dominantly Apache-2.0 (AndroidX/Compose/Room/Ktor/Accompanist/
   Material Icons) — confirm `ch.hsr:geohash` and `com.mikepenz:*` if D3(a) lands. Record the
   result in the notice; this is the artifact that answers a redistribution question.
2. **Ship notices in assets via a generated source dir.** Register a Gradle `Copy` task that copies
   root `LICENSE` + `NOTICE` + the generated third-party notice into a build-directory path, and
   add it as an assets source dir:
   `android.sourceSets.getByName("main").assets.srcDir(licenseAssetsTask)`.
   Passing the **task** (not a bare path) is what makes AGP wire the task dependency automatically.
   Assets are the reliable location; `res/raw/` works too. Rationale for generating rather than
   committing `app/src/main/assets/LICENSE`: one source of truth, so `LICENSE` can never drift from
   its copy.
   - Note: a `src/main/resources/META-INF/LICENSE` Java-resource route is the historically popular
     one but relies on AGP packaging behavior that is easier to regress; prefer `assets/`.
   - `packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }`
     (`app/build.gradle.kts:126-128`) should stay: those excludes exist to stop duplicate-notice
     merge conflicts. They remove *dependency* notice files, which is only compliant because (2)
     re-supplies them authoritatively.
3. **In-app screen.** Add a Settings entry ("About") and a destination rendering: app version
   (`BuildConfig.VERSION_NAME`, already stamped with the release tag), the D2 copyright line, the
   full `LICENSE` text (from assets), and the dependency list (D3(a) screen or the static notice).
   Wire it into the `NavHost` at `MainActivity.kt:62-82` next to `Settings.route`.
   - Keep the "About" entry out of `SettingsScreen.kt`'s permission/logging sections
     (`SettingsScreen.kt:58-105`) so it does not disturb the existing toggle semantics.
   - Give the new destination an explicit test tag; `MainActivity`'s root sets
     `testTagsAsResourceId = true`, so instrumentation can address it.
4. **Expose the source URL to the UI.** Add a `buildConfigField` (or asset) holding
   `https://github.com/carlo-colombo/neurhome` and the released tag, so the About screen can link
   the Corresponding Source for the exact build. This is the in-app half of the GPL §6 obligation.

## 5. Phase 3 — Release

In `.github/workflows/build.yml`:

1. Add `LICENSE`, `NOTICE`, and the generated third-party notice to the `softprops/action-gh-release`
   `files:` list, and to the uploaded build artifact, so every release carries them alongside
   `neurhome-<tag>.apk` and its `.sha256`.
2. Extend the release body (or add a step writing release notes) with the D2 copyright line,
   the D1 SPDX identifier, and the sentence: "Corresponding Source for this binary is the source
   tree at tag `<tag>` (`https://github.com/carlo-colombo/neurhome/tree/<tag>`)." GitHub's
   automatically generated tag source archives are what satisfies GPLv3 §6(d) (offer from a
   network server) — no separate tarball job is required, but the release must say where the
   source is.
3. **Keep the signed-APK version and the tag identical** — the workflow already pins the tag with
   `CUSTOM_TAG` and dies if validation fails before tagging. Do not add a license step before
   validation in a way that reorders that guarantee.

## 6. Phase 4 — CI enforcement (so this does not rot)

1. Add a cheap JVM unit test (runs in both `:app:testDevDebugUnitTest` and
   `:app:testProdDebugUnitTest`) that asserts the expected license/NOTICE assets exist in the
   processed assets of the variant, mirroring how `BackupRulesTest.kt` pins the backup-rule shape.
2. Optional: `reuse lint` as a non-blocking step (matching the repo's existing non-blocking
   instrumented-lane convention) until the tree is clean, then make it blocking.
3. Add the release-side presence check: fail the `Sign Android release`/`Upload release artifact`
   steps if `LICENSE`/`NOTICE` are not in the `files:` set. A silent license-omission regression is
   exactly the failure class the current workflow already guards against elsewhere.

## 7. GPLv3-specific obligations to keep in mind

- **Corresponding Source.** Distributing the signed APK obliges you to convey the complete source
  of the exact version. The per-tag source tree + build instructions (`README.md` "Development")
  satisfy this; make the link explicit in the release (Phase 3.2).
- **Installation Information (§6).** GPLv3 requires that a modified version can actually be
  installed on the device. Plain APK distribution from GitHub Releases is fine (no bootloader /
  keystore lock-in). **Shipping on the Play Store with Play App Signing would put §6 in tension
  with store signing** — if that is ever planned, re-review this section first.
- **No warranty.** The app reads location, Wi-Fi SSID, contacts, and calendar (see the README
  "Privacy-First Tracking" and the backup policy in `AGENTS.md`). The "AS IS / no warranty"
  disclaimer in the license plus a short README disclaimer is the standard posture; a
  privacy/claims statement is out of scope for this plan but should be reviewed separately.
- **Single author.** GPLv3's copyleft applies to derivatives; since contributions are essentially
  dependabot-only today, no CLA/DCO is required now. If external code contributions begin, the
  default GPL inbound=outbound rule applies — add a `CONTRIBUTING.md` and a DCO sign-off then, not
  preemptively.

## 8. Acceptance criteria

- `LICENSE` present, unmodified GPLv3, with the D2 copyright recorded in `README.md`/`NOTICE`.
- The D1 SPDX identifier is stated in `README.md`, `NOTICE`, and (via `REUSE.toml`) the tree.
- `unzip -l app-dev-debug.apk` contains the license and third-party notice under `assets/`.
- The app exposes an About screen showing version, copyright, license text, and dependency licenses.
- Every GitHub release lists `LICENSE`, `NOTICE`, and the notices as assets, and its body names the
  SPDX identifier and the source tag.
- `:app:testDevDebugUnitTest` and `:app:testProdDebugUnitTest` include a passing license-asset test.
- `./gradlew :app:assembleDevDebug` is green and the dev APK is deployed and launched on the
  attached device per `AGENTS.md`, exercising the About screen.

## 9. Sizing / sequencing

| Phase | Size | Depends on |
| --- | --- | --- |
| Decisions D1-D4 | S | operator |
| 1 Repository metadata | S | D1, D2, D4 |
| 2 Artifact (audit, assets, About screen) | M | D3, Phase 1 |
| 3 Release workflow | S | Phase 1 |
| 4 CI enforcement | S | Phases 2-3 |

Phases 1 and 3 are independent of the UI work and can land first; Phase 2 is the only one with
real engineering cost (new dependency or new Compose destination + nav wiring).

## 10. Out of scope

- Changing the license itself (GPLv3 is assumed retained; a relicensing decision is separate).
- Contributor licensing agreements / `CONTRIBUTING.md` / `CODE_OF_CONDUCT.md`.
- Trademark, the `neurhome` name, or store-listing legal text.
- A privacy policy (worth doing separately given the launch-log data).

## 11. Execution log (2026-10-17)

Implemented in full. Decisions taken where the plan named a choice:

- **D1** — `GPL-3.0-or-later`.
- **D2** — `Copyright (C) 2023-2026 Carlo Colombo` (sole author; first commit 2023-12-23).
- **D3** — option (b) with an automated step rather than the AboutLibraries plugin: the resolved
  `prodReleaseRuntimeClasspath` was audited against the published POMs and the result committed as
  `THIRD_PARTY_LICENSES.md`. No new Gradle plugin or runtime dependency was added. Every artifact is
  Apache-2.0 except `org.slf4j:slf4j-api` (MIT) and
  `androidx.datastore:datastore-preferences-external-protobuf` (BSD-3-Clause).
- **D4** — `REUSE.toml` was deliberately **not** added. A conformant REUSE setup needs the license
  text duplicated into `LICENSES/GPL-3.0-or-later.txt` on top of the root `LICENSE` that GitHub's
  license detector reads, and the project has no REUSE tooling in CI, so a `REUSE.toml` would imply
  a check that does not exist. SPDX intent is recorded in `NOTICE`, `README.md` and per-file headers
  on the new source files instead.

What landed:

- `NOTICE` (root) — copyright, GPLv3 no-warranty notice, source URL, SPDX identifier.
- `THIRD_PARTY_LICENSES.md` (root) — 118 runtime artifacts grouped by license plus the Apache-2.0,
  BSD-3-Clause and MIT texts.
- `README.md` — new `## License` section.
- `app/build.gradle.kts` — `stageLicenseAssets` copies the three root files into a generated
  `assets/licenses/` directory, wired through the Variant API
  (`variant.sources.assets.addGeneratedSourceDirectory`, because AGP 9 rejects Provider-backed
  outputs in the legacy `sourceSets` API) so every variant's asset merge depends on the copy. Two
  `buildConfigField`s expose the source URL and SPDX identifier.
- `ui/about/AboutScreen.kt` — new Settings → About destination showing version, copyright, the
  packaged license and third-party texts, and a Corresponding Source link. `Navigator`, `MainActivity`
  and `SettingsScreen` are wired accordingly.
- `LicenseAssetsTest.kt` — pins that the license and notices reach the merged assets in both the dev
  and prod JVM suites.
- `.github/workflows/build.yml` — the release verifies the assets are in the signed APK, attaches
  `LICENSE`/`NOTICE`/`THIRD_PARTY_LICENSES.md` to the release, and the release body states the SPDX
  identifier and the Corresponding Source tag.
- `.github/workflows/pr.yml` — the new root files join `LICENSE` in the `paths-ignore` filter.

Verified locally: `:app:assembleDevDebug`, `:app:testDevDebugUnitTest`, `:app:testProdDebugUnitTest`,
`:app:lintDevDebug` and `:app:assembleProdRelease` are all green; the dev and prod APKs contain
`assets/licenses/{LICENSE,NOTICE,THIRD_PARTY_LICENSES.md}`. No device was attached, so the About
screen was not exercised on hardware.
