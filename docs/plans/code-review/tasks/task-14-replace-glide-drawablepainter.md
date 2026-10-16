# Task 14: Replace Glide with `rememberDrawablePainter`

**Status: Done.** The three icon call sites (`ApplicationItem`, `BottomBar`,
`AppStatisticsScreen`) render with `rememberDrawablePainter`; the `glide`,
`glide-compose`, and `glide-ksp` dependencies and the `GalleryModule`
`AppGlideModule` were removed. Each site casts `app.icon` to `Drawable` and
falls back to the `Person` placeholder when it is not a drawable.

A follow-up fixed the contact-photo regression this introduced: starred
contacts carry a `content://` photo URI `String` in `Application.icon`, which
Glide used to resolve, so contacts with a photo fell back to the `Person`
placeholder. `ContactsDAO` now resolves `PHOTO_THUMBNAIL_URI` to a cached
`BitmapDrawable` (via `ContentResolver.loadThumbnail`) and stores it in
`Application.icon`, so no Glide-like image loader is needed and both launcher
icons and contact photos render through `rememberDrawablePainter`.

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
- Resolve starred-contact photo URIs to drawables so contact photos stay
  visible without Glide.

## Acceptance criteria

- No Glide imports/usages remain; Glide dependencies removed.
- Icons render on Home, bottom bar, stats, and application lists.
- Starred contacts with a photo show the photo; contacts without one keep the
  `Person` placeholder.
- `./gradlew :app:assembleDevDebug` succeeds and manual icon check.

## Dependencies

None.
