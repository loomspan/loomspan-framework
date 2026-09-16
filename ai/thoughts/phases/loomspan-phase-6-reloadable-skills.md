# Phase 6 — Prepare and publish skill generations

## Status and intent

The two tickets are ready for fresh-context pipeline execution, with implementation
not started. PR 6.1 and PR 6.2 are proposed work identifiers, not existing GitHub
PRs. GPT-5.6 Sol is the intended coding model. Keep related changes together and
prefer the simplest complete design with the least technical debt.

Loomspan owns validated generations, atomic activation, and execution consistency.
The application owns everything outside the framework: files, external artifacts,
configuration, traffic readiness, and external resource retention/cleanup.

## Contract

### Prepare → stage application artifacts → publish

- One Loomspan-provided `SkillReloader` offers `prepare()`, `publish(candidate)`,
  and `snapshot()`. No one-step reload convenience or changed-result flag.
- Preparation validates all configured model-backed YAML and REST declarations
  against fixed Java declarations and returns an immutable `PreparedSkillUpdate`
  exposing its `generationId()` and candidate catalog `snapshot()`.
- Every successful preparation gets a fresh opaque process-local ID, even with
  unchanged YAML. External configuration may change independently of YAML.
  No content comparison, no-op detection, or content-derived identity is needed.
- Preparation does not activate. The application stages external artifacts using
  the candidate ID, then publishes. Publication activates exactly the prepared
  data and ID without rereading files or redoing declaration validation.
- Additions, edits, deletions, renames, and an empty YAML set are supported. Empty
  removes all YAML skills and leaves Java skills. Remaining references to deleted
  children reject the entire candidate. Invalid preparation preserves active state.
- Model connections, Java implementations, Spring beans, and REST handler beans
  stay fixed. Existing handler requirements apply to every candidate. The external
  manager finishes publishing files before prepare and prevents writes until it
  returns; subsequent file edits cannot affect the candidate.

### Identity, publication, and startup

- `SkillCatalog.generationId()` identifies each immutable catalog, including the
  startup generation. Service snapshot access does not read files. Older snapshots,
  including the initially injected catalog, remain unchanged.
- Candidate handles are framework-created, caller-owned, and process/instance-local.
  No candidate registry, expiry service, or discard operation. Dropping references
  abandons an unpublished candidate; the application cleans up its staged artifacts.
- Record the active base ID at preparation start. Reject stale publication if
  active state changed in the interim; reject foreign or repeated publication.
  Successful publication is single-use. Prepare anew to retry stale updates.
- Serialize preparations with each other and publications with each other, without
  holding a lock through application staging. Publication can make an in-progress
  preparation stale. Ordinary execution and snapshot reads continue.
- Coordinate activation with shutdown; no publication may activate after shutdown
  begins. Reject new updates and preparation finishing across that boundary.
- Startup still validates and activates an initial generation. The application
  reads its ID, stages initial matching configuration, then admits its own traffic
  or starts skills. No additional framework startup gate or fallback routing.

### Execution and ownership

- Capture a generation before input validation and retain it through queued
  handoffs, root execution, nested calls, planning, and parallel work. Children
  inherit it. New roots acquire the active generation.
- Include the captured ID explicitly in `RestSkillInvocation` as trusted runtime
  metadata. The model cannot select it, and handlers must not substitute a global
  current ID. Manifest policy changes affect new invocation trees; existing
  authentication and authorization enforcement remains intact.
- Current observability discovery shows active definitions; execution diagnostics
  show the actual captured ID. No historical catalog lookup service.
- Retain needed generations strongly. Release unnecessary framework execution
  references at completion, failure, cancellation, and handoff release; ordinary
  Java GC reclaims unreferenced data. No WeakHashMap, finalizer lifecycle,
  prepare-time cleanup sweep, global history, or strict two-generation limit.
- More than two generations may coexist because old work or caller references
  still need them. Discovery snapshots do not authorize historical execution.
- External resource retirement is application-owned. No framework cleanup,
  retirement query/event, callback SPI, or reference-count API is included.
  Publishing a new generation does not prove old work has finished. Documentation
  must not promise a safe-deletion signal for application configuration.

## PR sequence

### PR 6.1 — Execute against coherent skill generations

[Ticket](../tickets/loomspan-pr-6.1-coherent-skill-generations.md)

Build generation assembly, validation, identity, atomic activation foundations,
immutable snapshot data, and execution retention together. Include ownership
release and controlled concurrency tests. Expose no public update operation yet.

### PR 6.2 — Prepare and publish externally managed skills

[Ticket](../tickets/loomspan-pr-6.2-public-skill-reload.md)

Depends on PR 6.1. Add the public service/handle and catalog/REST metadata changes,
publication eligibility and shutdown coordination, current diagnostics, application
examples, and supported-surface tests. Final integration verification is included;
the formerly proposed PR 6.3 is unnecessary.

## Compatibility, design, and verification

Breaking development-time changes for this feature are authorized. No shims,
compatibility constructors, aliases, legacy readers, or old/new parallel paths.
Update affected callers, samples, fixtures, tests, and documentation together.
Preserve catalog immutability, security, authorization, and diagnostic usefulness.

Apply the [framework feature design lens](../framework-feature-design-lens.md):
classify affected surfaces, record impact and public-surface delta, reuse existing
owners, remove obsolete paths, and assess diagnostic protocol marker changes.
Public API lives in the closed `ai.loomspan.api` allowlist and exposes no internal
or autoconfigure types. `RestSkillHandler` remains the sole supported SPI.

Both tickets recommend the Full 5-Step Pipeline. Tests accompany implementation,
including identical-YAML updates with different REST configuration, stale/foreign/
repeated publication, source edits after preparation, startup readiness examples,
queued/nested/parallel execution, more than two retained generations, and shutdown.
Run `LoomspanPublicSurfaceArchitectureTest` after production changes. Use controlled
coordination and ownership evidence rather than sleeps or GC timing.

## Deferred scope and effort

No file watching, console editing, per-skill reload, Java hot swapping, dependency
replacement, model reconfiguration, distributed transactions, deployment registry,
multi-instance coordination, durable history, rollback API, or external cleanup.

The earlier 70k–130k total agent-token estimate predates two-stage publication.
It is an uncertain planning range, not a measured Sol forecast or updated budget.
PR 6.1 concentrates architectural risk; PR 6.2 concentrates public contract and
lifecycle integration risk.
