# Task 14: Replace Glide with `rememberDrawablePainter`

**Status: Done.** The three icon call sites (`ApplicationItem`, `BottomBar`,
`AppStatisticsScreen`) render with `rememberDrawablePainter`; the `glide`,
`glide-compose`, and `glide-ksp` dependencies and the `GalleryModule`
`AppGlideModule` were removed. Each site casts `app.icon` to `Drawable` and
falls back to the `Person` placeholder when it is not a drawable, because
starred contacts carry a `content://` photo URI `String` in `Application.icon`;
Glide used to resolve those URIs, so a starred contact with a photo now shows
the placeholder instead of the photo. Loading those URIs again is out of scope
for this task.

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
