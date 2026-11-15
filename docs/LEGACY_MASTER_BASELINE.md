# Legacy master baseline

The new repository's development baseline includes the complete file snapshot from the legacy repository's `master` branch.

- Source repository: `redhatlxg/xiaoxiao-ktv-1`
- Source branch: `master`
- Source commit: `36140418286659a4ff968d293f5c5090c9f46c69`
- Destination repository: `redhatlxg/xiaoxiao-ktv`
- Destination branch audited: `ui/redesign-leishi-style`
- Destination commit audited: `85476c076ff8daef73e00e4afd464b36e0d86b25`

## Snapshot audit

At the time of the audit:

- Legacy master contained 1,341 tracked files.
- The new development branch contained all 1,341 of those paths.
- No legacy-master file paths were missing from the new development branch.
- The new branch additionally contained four paths: three new drawable selectors/tiles and this UI redesign checklist.
- 38 shared paths had content differences. These include intentional UI/theme work, accessibility/focus styling, project/build configuration, documentation, and mobile UI updates.

Important preserved behavior modules such as the mobile API bridge, download manager, and local remote server matched the legacy master snapshot at the audited revisions.

## Development rule

Continue all changes in the new repository. Treat the legacy repository as read-only backup/reference. Do not replace the new branch wholesale with the old snapshot: doing so would discard subsequent UI work and build/security improvements. Preserve original app functionality while evolving the product through its own branding, UI, and feature development.

This is a source-file baseline migration record. It does not claim that the two repositories share Git commit ancestry.
