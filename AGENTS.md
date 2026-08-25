# Neurhome Agent Notes

## Project Shape

- This is a single-module Android app: `app`; the package/namespace is `ovh.litapp.neurhome3`.
- `MainActivity` owns the Compose `NavHost`; UI screens and view models are under `app/src/main/java/ovh/litapp/neurhome3/ui`.
- `NeurhomeApplication` is the application-level composition root: it owns the Room database and constructs repositories/services. Data access is under `data`.
- The project uses Jetpack Compose, Material 3, MVVM, Kotlin coroutines/Flow, Room, KSP, and Java/Kotlin 17.
- Category UI is under `ui/categories`. The home destination uses `CategoryPager` with Home first, then tags, and Uncategorized last. The dedicated Categories destination uses the same tag/Uncategorized pages but starts directly with tags, without a Home dashboard page.
- Tag screens keep a centered title header fixed above independently vertically scrollable content. The full applications screen has a Categories action that opens the dedicated Categories destination; future tag screens must stay consistent between both category entry paths.
- Home already contains a horizontal calendar/weather pager. Do not assume an outer horizontal pager will receive gestures from its child; validate category gestures on a real device and preserve dashboard swiping.

## Build And Test

- Use the checked-in Gradle wrapper (`./gradlew`), with JDK 17; Android SDK 37 is required to compile and the app minimum SDK is 34.
- PR-equivalent build: `./gradlew :app:assembleDevDebug`.
- Production build: `./gradlew assembleProdRelease`; release enables R8/resource shrinking and CI signs the resulting APK.
- JVM unit tests: `./gradlew :app:testDevDebugUnitTest`.
- Compose/instrumentation tests require a connected or running Android device/emulator: `./gradlew :app:connectedDevDebugAndroidTest`.
- Compose tests use the debug-only `ComposeTestActivity` when they need to call `setContent`; do not replace `MainActivity` content in tests because it initializes its own Compose hierarchy.
- CI currently builds the dev debug APK for pull requests and the prod release APK on pushes to `main`; it does not run tests or lint.

## Flavors And Generated Files

- `dev` adds `.dev.<hostname>` to the application ID and `-dev-<hostname>` to the version name, so dev APK identity varies by machine; `prod` has no suffix.
- For manual dev deployment, install `app/build/outputs/apk/dev/debug/app-dev-debug.apk`, then resolve the package from the current build output or `adb shell pm list packages`; launch `ovh.litapp.neurhome3.MainActivity` using that package. Do not hard-code another machine's hostname suffix.
- Room exports schemas to `app/schemas`. When changing entities or database version, update the migration declarations and commit the generated schema output; do not delete historical schema versions.
- Room and Glide use KSP during the Gradle build; generated output belongs in build directories, not source control.
- Keep application initialization blocks after the properties they access. In particular, `NeurhomeApplication` must initialize repository lazy properties before starting collectors that reference them.

## Development Learnings

- Key application `LazyColumn` rows with package, profile, and launcher component. Package/profile alone is not unique when an application exposes multiple launcher activities.
- After a successful dev build, deploy `app/build/outputs/apk/dev/debug/app-dev-debug.apk` to the attached device and launch the package resolved from `output-metadata.json`.
