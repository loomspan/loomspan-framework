---
audience: loomspan-application-developer
status: development
applies_to: bundled-loomspan-revision
coverage: source-verified
---

# Two-stage skill updates

Inject the framework-provided `SkillReloader`. At startup, read `snapshot().generationId()` and stage matching application-owned REST configuration before opening your traffic gate. The initially injected `SkillCatalog` is a permanent startup snapshot; call `reloader.snapshot()` for current discovery. Catalog lookup is unfiltered and does not authorize an invocation.

After the application publishes stable YAML files and prevents writes for the duration of preparation, call `PreparedSkillUpdate update = reloader.prepare()`. This synchronously validates a complete new model-backed and REST YAML set against fixed Java declarations, model connections, and the one fixed REST handler bean. It does not activate the candidate. Stage external artifacts under `update.generationId()`, then call `reloader.publish(update)`. `update.snapshot()` shows the candidate before publication. Publication uses the frozen candidate without file reads or revalidation. Identical YAML still gets a fresh process-local ID. Empty YAML removes all YAML skills, not Java declarations. No file transaction, Java hot swap, model reconnection, or handler replacement is supplied.

`publish` succeeds once only for an update from the same framework instance whose preparation base is still active. Foreign, fabricated, stale, repeated, and shutdown-time updates fail with unchecked `SkillReloadException`; operational preparation failures are wrapped with a cause. A later update requires another `prepare`. `snapshot()` never reads resources. Shutdown begun during preparation prevents a successful return. Old admitted and running trees keep their captured definitions and ID; new roots capture the new active generation.

The application owns readiness, external artifact retention, and cleanup. Publishing B does not show that all work using A has finished; Loomspan provides no external safe-deletion signal, retirement callback, or historical invocation API. Keep A's configuration while any admitted or running A work may use it. See [REST handler SPI](rest-skills.md) for generation-keyed handler selection.

## Evidence

- `PublicSkillReloadIntegrationTest` exercises startup ID staging, frozen publication, new REST routing, and unchanged startup catalog.
- `SkillReloaderTest` protects owner, base, one-shot, overlapping preparation/publication, and shutdown checks.
- `SkillGenerationManagerTest` protects complete validation, same-content fresh IDs, additions/deletions/empty set, and fixed handler reuse.
- `LoomspanPublicSurfaceArchitectureTest` protects the closed API and sole supported SPI.
