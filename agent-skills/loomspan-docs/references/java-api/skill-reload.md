---
audience: loomspan-application-developer
status: development
applies_to: bundled-loomspan-revision
coverage: source-verified
---

# Two-stage skill updates

Inject the framework-provided `SkillReloader`. At startup, read `snapshot().generationId()` and stage matching application-owned REST configuration before opening your traffic gate. The initially injected `SkillCatalog` is a permanent startup snapshot; call `reloader.snapshot()` for current discovery. Catalog lookup is unfiltered and does not authorize an invocation.

After the application publishes stable YAML files and prevents writes for the duration of preparation, call `PreparedSkillUpdate update = reloader.prepare()`. This synchronously validates a complete new model-backed and REST YAML set against fixed Java declarations, model connections, and the one fixed REST handler bean. It does not activate the candidate. Stage external artifacts under `update.generationId()`, then call `reloader.publish(update)`. `update.snapshot()` shows the candidate before publication. Publication uses the frozen candidate without file reads or revalidation. Identical YAML still gets a fresh process-local ID. Empty YAML removes all YAML skills, not Java declarations. No file transaction, Java hot swap, model reconnection, or handler replacement is supplied.

For YAML already held by the application, call `reloader.prepare(Collection<SkillDocument>)` with the complete replacement set. Each record contains `sourceName` and `yaml`; the collection and elements must be non-null, source names must be nonblank and exactly unique, and YAML must be non-null. A source name is diagnostic text, not a resource path or the callable YAML `name`; case-different labels are distinct. Loomspan copies the collection and freezes the YAML as UTF-8 during preparation. The same parser, model/REST rules, child references, input contracts, roles, fixed Java declarations, and handler binding apply. An empty collection removes all YAML and REST declarations while leaving Java skills. A later no-argument `prepare()` still discovers the configured locations.

An application restoring its own saved documents after an ordinary restart can follow this supported-only sequence:

```java
List<SkillDocument> saved = snapshotStore.loadDocuments(); // application-owned durable state
PreparedSkillUpdate update = reloader.prepare(saved);
configurationStore.stage(update.generationId(), snapshotStore.loadRestConfiguration());
reloader.publish(update);
trafficGate.open(); // application-owned readiness gate
```

The ordinary configured generation starts first. On every process start, REST configuration must be staged for whichever generation the application will serve; old process IDs cannot be reused. Loomspan intentionally has no startup switch, deferred activation mode, persistence store, or framework traffic gate. The application owns its durable snapshot, staging, and traffic readiness.

`publish` succeeds once only for an update from the same framework instance whose preparation base is still active. Foreign, fabricated, stale, repeated, and shutdown-time updates fail with unchecked `SkillReloadException`; operational preparation failures are wrapped with a cause. A later update requires another `prepare`. `snapshot()` never reads resources. Shutdown begun during preparation prevents a successful return. Old admitted and running trees keep their captured definitions and ID; new roots capture the new active generation.

The application owns readiness, external artifact retention, and cleanup. Publishing B does not show that all work using A has finished; Loomspan provides no external safe-deletion signal, retirement callback, or historical invocation API. Keep A's configuration while any admitted or running A work may use it. See [REST handler SPI](rest-skills.md) for generation-keyed handler selection.

## Evidence

- `PublicSkillReloadIntegrationTest` exercises startup ID staging, frozen publication, new REST routing, and unchanged startup catalog.
- `PublicSkillReloadIntegrationTest` also exercises supplied REST YAML without files, old admitted work, configured alternation, and restart resubmission.
- `SkillReloaderTest` protects owner, base, one-shot, overlapping preparation/publication, and shutdown checks.
- `SkillGenerationManagerTest` protects complete validation, same-content fresh IDs, additions/deletions/empty set, and fixed handler reuse.
- `LoomspanPublicSurfaceArchitectureTest` protects the closed API and sole supported SPI.
