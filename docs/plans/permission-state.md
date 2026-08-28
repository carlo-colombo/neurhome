# Permission State Plan

## Objective

Avoid creating a separate `CALL_PHONE` permission state for every visible `ApplicationItem`.

## Scope

- Create the phone permission state once per application-list parent.
- Pass the shared state into each `ApplicationItem`.
- Remove per-row `rememberPermissionState()` creation.
- Preserve the current request-before-launch behavior.

## Design Constraints

- Keep permission handling in the Compose layer.
- Keep the shared state outside lazy-item identity and row keys.
- Preserve behavior for normal launcher applications and callable/contact applications.
- Do not alter long-press editing, tag editing, or application launch behavior.

## Verification

- Run the relevant JVM tests and `:app:assembleDevDebug`.
- Test with permission already granted.
- Test with permission denied and request flow visible.
- Test revoking permission while the list is open.
- Test multiple callable/contact rows.
- Confirm lazy-list state and application row interactions remain unchanged.
- Use Layout Inspector or Compose recomposition counters to compare row creation.

## Acceptance Criteria

- One permission state is created per active list parent, not per row.
- Permission requests and launches behave exactly as before.
- No row state is lost when lazy items are recycled or reordered.
- No new permission-related crashes or lifecycle issues occur.
