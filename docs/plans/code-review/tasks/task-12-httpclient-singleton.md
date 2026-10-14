# Task 12: Make the Weather `HttpClient` an App Singleton

**Status: Done.** `WeatherServiceImpl`/`WeatherRepository` are created once as
`NeurhomeApplication.weatherRepository`; the `HomeViewModel` initializer uses the
app singleton, so one CIO `HttpClient` exists per process.

Source: Code review — Performance 4. Priority: P1.

## Problem

`AppViewModelProvider` builds `WeatherRepository(WeatherServiceImpl())` inside
the `HomeViewModel` initializer (`AppViewModelProvider.kt:24`), so every
navigation back to Home creates a new CIO `HttpClient` (thread pools) that is
never closed.

## Scope

- Make the weather service/`HttpClient` an application-scoped singleton, or
  close it in `ViewModel.onCleared`.
- Prefer the singleton to avoid reconnect/thread churn.

## Acceptance criteria

- Only one `HttpClient` exists per process.
- Weather still loads on Home.
- `./gradlew :app:assembleDevDebug` succeeds.

## Dependencies

Related to Task 21 (ViewModel wiring consolidation).
