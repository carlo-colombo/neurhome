# Task 14: Replace Glide with `rememberDrawablePainter`

**Status: Not started.**

Source: Code review — Performance 6. Priority: P2.

## Problem

`GlideImage(model = app.icon, ...)` is used in `ApplicationItem`, `BottomBar`,
and `AppStatisticsScreen`, but `app.icon` is already a `Drawable` from
`LauncherIconCache`. Glide adds a dependency, resizing, and thread overhead for
no benefit. `accompanist-drawablePainter` is already a dependency but unused.

## Scope

- Switch the three call sites to `rememberDrawablePainter(app.icon)`.
- Remove `glide`, `glide-compose`, `glide-ksp`, and the Glide `GalleryModule`
  once no usages remain.

## Acceptance criteria

- No Glide imports/usages remain; Glide dependencies removed.
- Icons render on Home, bottom bar, stats, and application lists.
- `./gradlew :app:assembleDevDebug` succeeds and manual icon check.

## Dependencies

None.
