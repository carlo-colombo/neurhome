# Neurhome

This is a personal home launcher project inspired by Nokia's discontinued 
[Z Launcher](https://en.wikipedia.org/wiki/Z_Launcher). Frustrated by the layout breaking on my new
phone, I decided to build my own.

Neurhome is built using modern Android development practices, leveraging Jetpack Compose and Material 3 for a fluid, responsive UI.

## Features

Neurhome aims to replicate and expand upon the features of Z Launcher:

* **Smart App List:** Dynamically generated list of frequently used apps based on time of day, day of the week, and on-device context (Location, Wi-Fi, etc.).
* **Quick Search:** A simplified, always-on keyboard for launching apps or calling starred contacts. Supports word boundary filtering and custom aliases.
* **Information Dashboards:**
    * Calendar display for upcoming events.
    * Next alarm status.
    * Real-time Weather integration (via Open-Meteo).
* **Usage Statistics:** Visualization of app usage patterns and interaction logs.
* **Privacy-First Tracking:** On-device tracking of location (using Geohashes), Wi-Fi connection, and device position to power the smart ranking engine.
* **Data Management:** Full database export and import functionality to keep your data under your control.

## Technical Stack

* **UI:** [Jetpack Compose](https://developer.android.com/jetpack/compose) with [Material 3](https://m3.material.io/).
* **Persistence:** [Room Database](https://developer.android.com/training/data-storage/room) with comprehensive schema tracking and auto-migrations.
* **Networking:** [Ktor](https://ktor.io/) for API interactions (e.g., weather data).
* **Images:** [Glide](https://github.com/bumptech/glide) for efficient image loading.
* **Architecture:** MVVM (Model-View-ViewModel) with [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) and [Flow](https://kotlinlang.org/docs/flow.html).
* **Location:** [Geohash](https://en.wikipedia.org/wiki/Geohash) for privacy-preserving location tagging.

## Development

The project uses Gradle flavors to manage different environments:

* `dev`: Development flavor with a unique package name suffix for side-by-side installation.
* `prod`: Production flavor.

To build the project, use the standard Gradle wrapper:

```bash
./gradlew assembleProdRelease
```

### Database Migrations

Room schemas are exported to `app/schemas`. When modifying the database, ensure migrations are handled correctly. The project utilizes `AutoMigration` for most schema updates.

![neurhome demo](https://raw.githubusercontent.com/carlo-colombo/neurhome/docs/neurhome.gif)
