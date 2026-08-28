# Icon Cache Plan

## Objective

Avoid repeated `LauncherActivityInfo.getBadgedIcon(0)` calls when the repository rebuilds the application list.

## Scope

- Add a bounded repository-level cache for launcher icons.
- Key entries by package name, user/profile ID, and launcher component name.
- Reuse cached icons while mapping launcher activities to `Application` objects.
- Invalidate affected entries when launcher packages are changed or removed.
- Keep icon work off the main thread.
- Preserve the existing application row identity and behavior.

## Design Constraints

- Do not change application ordering, filtering, tagging, or visibility behavior.
- Do not retain an unbounded number of `Drawable` instances.
- Handle applications exposing multiple launcher activities independently.
- Avoid stale icons after package updates.

## Verification

- Run the relevant JVM tests and `:app:assembleDevDebug`.
- Add focused tests if the cache logic is extracted into a testable unit.
- Compare repeated application-list refreshes before and after the change.
- Confirm icon loading remains off the UI thread.
- Verify package add, package change, and package removal behavior on a device.

## Acceptance Criteria

- Repeated list rebuilds reuse cached icons.
- Cache memory is bounded or otherwise explicitly controlled.
- Package/component/profile changes invalidate stale entries.
- No visible regression in launcher icons or list behavior.
