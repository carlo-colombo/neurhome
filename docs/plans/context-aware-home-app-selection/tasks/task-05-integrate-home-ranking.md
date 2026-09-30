# Task 5: Integrate Home Predictions

## Objective

Switch the Home top-app list between the existing ranking and the learned model
without changing application launch or filtering behavior.

## Scope

- Select the ranking strategy from the persisted Settings mode.
- Supply current local time, a usable current location, and current Wi-Fi state
  to learned inference. A disconnected Wi-Fi state is valid; unknown SSID state
  remains unknown.
- Fall back to the existing ranking when prerequisites/location/model/history
  are unavailable; never leave Home empty solely because the learner is cold.
- Preserve the six-app limit, hidden-from-Top exclusion, quiet-profile
  filtering, application resolution, and existing refresh/update behavior.
- The Learned list may omit labels with fewer than 10 prior launches; this does
  not remove apps from the full application list or prevent launching them.
- Ensure new launch records eventually affect predictions without requiring a
  reinstall or network access.

## Acceptance criteria

- Classic mode produces the existing results and remains the default.
- Learned mode produces model-ranked apps when ready and falls back safely when
  it is not.
- Existing Home search, favorites, launch logging, and app/profile identity
  behavior remain intact.

## Dependencies and verification

- Depends on Task 2, Task 3's verified Kotlin implementation, and Task 4.
  Rare-label coverage is diagnostic, not a dependency.
- Add repository/ViewModel tests for strategy switching, fallback, filtering,
  and model refresh after a new launch.
