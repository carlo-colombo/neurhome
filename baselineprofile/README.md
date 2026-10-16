# Baseline profile generator (PERF-15)

Scaffolding only. This module can record a Baseline Profile for the app, but no profile is
committed to the repository yet, so **cold start and the first frame of the Compose Home are
still unoptimised**.

## Why nothing is committed

A Baseline Profile is the list of methods the app actually executes during a journey, in order.
ART pre-compiles them at install time so the first frames of that journey do not pay for
JIT and verification. That list is only useful for the code and device class it was recorded on:

- a profile recorded on an **emulator** describes the emulator's ART behaviour, not a phone's,
  and a badly matched profile can make real devices slower than shipping no profile at all;
- a profile recorded for a different build than the one that ships (different flavour, different
  obfuscation) does not match the code that runs.

Recording it therefore needs a **real device**: an unlocked, screen-on, plugged-in device with
USB debugging, running Android 13 (API 33) or newer. Older devices can only be used with root.
Nothing here runs on CI for that reason.

`androidx.profileinstaller` is already a dependency of `:app`, so once a profile exists at
`app/src/main/baseline-prof.txt` the build embeds it in the APK and the library installs it on
release builds. Until then the dependency is a no-op.

## Generate a profile

Connect the real device, confirm it is visible to `adb devices`, and run:

```bash
./gradlew :baselineprofile:connectedProdDebugAndroidTest
```

- The task builds and installs the app under test (`:app:packageProdDebug` is part of its task
  graph) together with the generator APK, then runs
  `BaselineProfileGenerator.coldStart`, which presses Home, starts the app and waits for the Home
  dashboard to settle.
- It targets the **prod** flavour, because prod's application id (`ovh.litapp.neurhome3`) is
  stable while dev appends `.dev.<hostname>` (see `app/build.gradle.kts`) and cannot be named by a
  constant in the generator. It targets the **debug** build type because the app's `prodRelease`
  variant is minified and unsigned locally, so a connected test cannot install it.
- The profile is written into the instrumentation's additional test output. Find it with:

  ```bash
  find baselineprofile/build -name '*-baseline-prof.txt'
  ```

- To ship it, commit that file as `app/src/main/baseline-prof.txt` and rebuild
  (`./gradlew :app:assembleProdRelease`). AGP passes the profile to R8, which rewrites its
  references to the obfuscated release names.

## Current limitations

- The generator covers only cold start. Extend `BaselineProfileGenerator` with the app's other
  critical journeys — keypad search and paging the categories — before a profile is committed,
  and keep the journeys in the same class so they are recorded together.
- Recording against a debuggable build is not ideal: a debug build runs extra code that the
  release build does not have. A `benchmark`/non-minified-release build type in `:app` (signed
  with the debug key) would be a better target; that is a follow-up, not part of this scaffolding.
- The profile is unverified. Nothing measures cold start yet, so treat any committed profile as an
  unproven change until it is benchmarked on the same device with `MacrobenchmarkRule`.
