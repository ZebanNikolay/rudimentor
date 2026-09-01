# Course map migrations

Course data has two independent versions:

- `schemaVersion` changes when the JSON contract changes.
- `map.version` changes when lesson parameters, prerequisites, or map order change.

Every map version increment must have an explicit one-version migration step in
`CourseMapMigrations`, including a no-op step when existing progress remains valid.
The runtime applies every intermediate step, so an old installation can upgrade
directly across several released map versions.

## Safety rules

- Lesson IDs are persistent storage identifiers. Do not rename or reuse them without
  an explicit progress mapping.
- A missing, duplicate, or non-contiguous migration step must not advance the stored
  map version. The application preserves progress and can retry after an update.
- Completion resets invalidate only `completed`, stars, and crown for named ranks.
  Historical best accuracy is retained.
- A downgrade never lowers the stored map version or modifies progress.
- Optional map nodes must have exactly one prerequisite. This is validated by the
  production asset loader, not only by the course generator.

## Required workflow

1. Edit canonical YAML and increment `map.version` for every changed family.
2. Add a migration note with the reason, exact data changes, progress impact, and
   Android action.
3. Add one contiguous `MapMigrationStep` per version increment. Use an explicit no-op
   when no reset is needed.
4. Regenerate family JSON and copy the generated packages into app assets.
5. Run `./gradlew compileDebugKotlin testDebugUnitTest lintDebug`.
6. Build and install the APK only in the dedicated build workflow.

`CourseReleaseSafetyTest` loads the real bundled curriculum and all family packages
through `AssetCourseLoader`, the same path used during application startup. It also
checks migration coverage and reset targets. `MapMigrationApplicationTest` checks
that resets preserve accuracy and unrelated progress.

## Incident covered by these gates

The dev.53 startup crash was caused by generated optional nodes with two
prerequisites. Generator checks passed, but `LevelCatalog.build` rejected the real
bundled asset during `MainActivity.onCreate`. The production-loader asset test now
fails before integration for that exact class of defect.
