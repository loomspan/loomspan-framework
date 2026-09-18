---
audience: loomspan-application-developer
status: development
applies_to: bundled-loomspan-revision
coverage: source-verified
---

# Two-stage skill updates

For editor feedback, call `reloader.validate()` to reread configured resources or `reloader.validate(completeDocuments)` to check an in-memory complete replacement set. The result's `valid()` is false for any `ERROR`; `issues()` provides severity, source name, optional skill name and field path, and message. Warnings alone permit preparation. Supplied source labels are diagnostics, never paths. An empty collection proposes removal of YAML and REST skills while fixed Java skills remain. Repeated validation does not allocate an ID, create a candidate, activate a generation, instantiate a REST handler, change fixed caches, or notify retirement listeners. Validation does not invoke skills or models or test external connectivity. Correct the draft, then `prepare` current input, stage resources, and `publish`; validation does not guarantee or authorize these later lifecycle steps. See [draft validation workflow](../skill-authoring/validation-workflow.md).

Inject the framework-provided `SkillReloader`. Register retirement notification, read `snapshot().generationId()`, and stage matching application-owned REST configuration before opening your traffic gate. The initially injected `SkillCatalog` is a permanent startup snapshot; call `reloader.snapshot()` for current discovery. Catalog lookup is unfiltered and does not authorize an invocation.

```java
AutoCloseable retirement = reloader.onGenerationRetired(id ->
        cleanupExecutor.execute(() -> configurationStore.remove(id)));
configurationStore.stage(reloader.snapshot().generationId(), initialConfiguration);
trafficGate.open();

PreparedSkillUpdate update = reloader.prepare(); // or prepare(completeDocuments)
configurationStore.stage(update.generationId(), replacementConfiguration);
reloader.publish(update);

// One fixed RestSkillHandler bean selects resources from the trusted invocation ID.
RestSkillHandler handler = invocation -> configurationStore.get(invocation.generationId())
        .call(invocation.skillName(), invocation.input());
```

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

Use `AdmittedSkillInvocation.generationId()` to correlate a pending root with the application-owned durable snapshot staged for that generation before invocation. Do not use `reloader.snapshot().generationId()` for an already admitted handle: publication can change the current catalog before handoff returns. The handle's ID remains readable after release, retirement, or cutoff, but reading it does not retain the generation or guarantee that the application's mapping still exists. Handle a missing mapping and release an admission abandoned during lookup or recording; the application owns mapping retention and durable correlation.

The application owns readiness, staging, cleanup, and retries. Publishing B does not show that all work using A has finished. During normal operation, a registered listener is selected once when a superseded published generation has no captured preparation, pending admission, running root, or physically running descendant. This includes unused generations replaced immediately; a notification can occur before `publish` returns, so stage resources first. Listeners run outside framework locks on publication, invocation, admission-release, or physical-return threads and may run concurrently. They must be thread-safe and return promptly; schedule slow cleanup on an application-owned executor. A throwing listener is logged and isolated from publication, invocation, other listeners, and later retirements. Closing the returned handle prevents future selection without waiting for already selected callbacks. There is no replay, durable delivery, ordering across generation IDs, or retry. Delivery may be omitted after shutdown begins, including for the active generation, and shutdown does not wait for cleanup. Never-published rejected or discarded candidates get no callback and require application cleanup; a rejected repeated publish does not make its previously published generation safe to remove. See [REST handler SPI](rest-skills.md) for generation-keyed handler selection. There is no historical invocation API.

## Evidence

- `PublicSkillReloadIntegrationTest` exercises startup ID staging, frozen publication, new REST routing, pending-admission retirement, and unchanged startup catalog.
- `PublicSkillReloadIntegrationTest` also exercises supplied REST YAML without files, old admitted work, configured alternation, and restart resubmission.
- `SkillReloaderTest` protects owner, base, one-shot, overlapping preparation/publication, unused-generation retirement, listener isolation, and shutdown checks.
- `SkillGenerationManagerTest` protects complete validation, same-content fresh IDs, additions/deletions/empty set, and fixed handler reuse.
- `LoomspanPublicSurfaceArchitectureTest` protects the closed API and sole supported SPI.
