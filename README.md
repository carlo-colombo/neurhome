# Neurhome

This is a personal home launcher project inspired by Nokia's discontinued 
[Z Launcher](https://en.wikipedia.org/wiki/Z_Launcher). Frustrated by the layout breaking on my new
phone, I decided to build my own.

Neurhome is built using modern Android development practices, leveraging Jetpack Compose and Material 3 for a fluid, responsive UI.

## Features

Neurhome aims to replicate and expand upon the features of Z Launcher:

* **Smart App List:** Dynamically generated list of frequently used apps based on time of day, day of the week, and on-device context (Location, Wi-Fi, etc.).
* **Home App Ranking Modes:** Choose between the classic launch-score ranking and a learned on-device reranking of the same candidates (Settings → *Home app ranking*).
* **T9 Quick Search:** A simplified, always-on T9 keyboard for launching apps or calling starred contacts. Supports word boundary filtering and custom per-application aliases.
* **Application Categories:** Organize applications with custom tags and swipe between category screens from the home launcher or the dedicated Categories screen. Create, reorder, and remove tags, assign multiple tags to an application, and browse an Uncategorized screen for applications without tags. Tag edits are saved when application detail is closed.
* **Information Dashboards:** A swipeable dashboard on the home screen:
    * Calendar display for upcoming events.
    * Next alarm status.
    * Real-time weather integration and forecasts (via Open-Meteo), including city information.
    * Solar information including sunrise, sunset, blue hour, and golden hour, with upcoming events and automatic progression.
* **Usage Statistics:** Dedicated app-usage simulator with day-of-week, time, and Wi-Fi/SSID filters for exploring usage patterns and interaction logs.
* **Privacy-First Tracking:** On-device tracking of location (using Geohashes), Wi-Fi connection, and device position to power the smart ranking engine.
* **Settings:** Permission-backed opt-ins for calendar, contacts, location and Wi-Fi logging (persisted only while the grant exists), an alternative time zone, and the home app ranking mode.
* **Data Management:** Full database export and import (replace) functionality to keep your data under your control, plus a backup status panel showing what Auto Backup includes. Cloud backup is client-side encrypted, so it uploads only on a device with a secure lock screen; device-to-device transfer always carries the full database.

## Technical Stack

* **UI:** [Jetpack Compose](https://developer.android.com/jetpack/compose) with [Material 3](https://m3.material.io/).
* **Persistence:** [Room Database](https://developer.android.com/training/data-storage/room) with comprehensive schema tracking and auto-migrations.
* **Networking:** [Ktor](https://ktor.io/) for API interactions (e.g., weather data).
* **Images:** Android `Drawable`s (launcher icons, contact photos) rendered with the Accompanist `rememberDrawablePainter`.
* **Architecture:** MVVM (Model-View-ViewModel) with [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) and [Flow](https://kotlinlang.org/docs/flow.html).
* **Location:** [Geohash](https://en.wikipedia.org/wiki/Geohash) for privacy-preserving location tagging.

## Development

The project uses Gradle flavors to manage different environments:

* `dev`: Development flavor with a unique package name suffix for side-by-side installation.
* `prod`: Production flavor.

The build requires the Android SDK for compile SDK 37 and the app minimum is SDK 34. Use the checked-in Gradle wrapper (`./gradlew`).

Build the dev debug APK (the PR-equivalent build):

```bash
./gradlew :app:assembleDevDebug
```

Run the JVM test suite and the lint gate. The JVM suite runs on Robolectric with real in-memory Room, `kotlinx-coroutines-test` and Turbine, so the DAO and migration tests need no device:

```bash
./gradlew :app:testDevDebugUnitTest
./gradlew :app:lintDevDebug
```

The Compose/instrumentation suite needs a connected device or emulator (with the screen awake and `adb shell svc power stayon true`):

```bash
./gradlew :app:connectedDevDebugAndroidTest
```

Build the release APK (R8 minification and resource shrinking):

```bash
./gradlew assembleProdRelease
```

CI builds and signs the release from `main`; pull requests build the dev debug APK and run the JVM tests and lint.

### Database Migrations

Room schemas are exported to `app/schemas`. When modifying the database, ensure migrations are handled correctly. The project utilizes `AutoMigration` for most schema updates, and the exported schema JSON must be committed. The JVM suite sweeps every committed migration version.

![neurhome demo](https://raw.githubusercontent.com/carlo-colombo/neurhome/docs/neurhome.gif)

## License

Neurhome is free software, licensed under the **GNU General Public License, version 3 or later**
(`GPL-3.0-or-later`). Copyright (C) 2023-2026 Carlo Colombo.

The full license text is in [`LICENSE`](LICENSE); the licenses of the third-party libraries the app
links against are in [`THIRD_PARTY_LICENSES.md`](THIRD_PARTY_LICENSES.md). Both files ship inside the
APK (under `assets/licenses/`) and are shown under Settings → About.

This program is distributed in the hope that it will be useful, but **without any warranty**; without
even the implied warranty of merchantability or fitness for a particular purpose. See the GNU General
Public License for more details.
