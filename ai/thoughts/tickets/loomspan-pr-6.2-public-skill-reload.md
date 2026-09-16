# PR 6.2 — Prepare and publish externally managed skills

## Outcome

Expose two-stage skill updates: fully validate and freeze a candidate, return its
generation ID so the application can stage related artifacts, then explicitly
activate that candidate. Existing invocation trees retain their original
skills and supply that same generation ID to the application REST handler.

## Requirements

### Minimal public contract

Supply one injectable Loomspan-provided `ai.loomspan.api.SkillReloader` with:

```java
PreparedSkillUpdate prepare();
void publish(PreparedSkillUpdate update);
SkillCatalog snapshot();
```

- `PreparedSkillUpdate` is an opaque, framework-created handle with immutable
  candidate contents. Expose `String generationId()` and `SkillCatalog snapshot()`
  for its candidate. Do not expose internal types, mutable candidate definitions,
  a public constructor accepting fabricated catalogs, or an application SPI.
- Add `String generationId()` to immutable `SkillCatalog`. It identifies that
  catalog's generation, including the initial startup generation. Service
  `snapshot()` returns the current catalog without filesystem reads. Old catalogs,
  including the initially injected bean, remain unchanged. Preserve exact-name
  ordering, lookup, and unfiltered discovery; discovery does not authorize work.
- Add `generationId` explicitly to `RestSkillInvocation`, alongside its skill
  name and input. Loomspan supplies the actual invocation tree's captured ID.
  It is trusted runtime metadata, never model-selected or taken from business
  input and never replaced with whichever ID happens to be current at call time.
- Use documented unchecked `SkillReloadException` extending `SkillException` for
  operational preparation/publication failures, including rejected candidates,
  stale/foreign/repeated publication and shutdown rejection. Preserve useful
  diagnostics and causes. Null API arguments follow ordinary argument validation.
  Do not catch JVM errors as ordinary update failures.
- Deliberately allowlist and document these API changes. `RestSkillHandler` stays
  the sole supported SPI. The update service and candidate are framework-owned
  operations/data, not new implementation or bean-replacement extension points.
- Remove the superseded one-step `reload()` proposal, `SkillReloadResult`, changed
  flags, content comparisons, and no-op paths. Do not add convenience alternatives.

### Preparation

- Synchronously read all configured YAML locations and fully validate one complete
  model-backed and REST skill set against fixed Java declarations. Validate all
  declarations, schemas, handler requirements, duplicate names, and exact child
  references before returning success. Do not activate anything during preparation.
- Every successful preparation receives a new opaque process-local ID, even with
  identical YAML. IDs must not collide within the running framework. They carry
  no content-equivalence, ordering, restart, or persistence promise. Publication
  retains this ID rather than allocating another.
- Freeze all data needed for execution and discovery. Later file modification,
  removal, or invalid content cannot change the candidate. Publication does not
  reread resources or redo candidate validation; runtime eligibility checks still
  occur at publication.
- Support additions, edits, deletions, renames, and an empty YAML set, which removes
  all YAML skills while retaining Java declarations. Remaining references to
  deleted children invalidate the candidate. Failure preserves active state.
- Keep Java implementations, Spring beans, configured model connections, and REST
  handler beans fixed. Apply existing REST handler cardinality requirements to
  each candidate; first-time REST additions without a suitable existing handler
  fail preparation. Do not add manifest or property syntax for this feature.
- The application finishes file publication before prepare and prevents writes
  until prepare returns. Loomspan does not lock external files, stage directories,
  or guarantee transactional multi-file reads. The application may modify files
  after prepare returns without affecting that candidate.

### Candidate ownership and publication

- The caller owns the prepared handle. It belongs only to the framework instance
  that created it and is not serializable or transferable across processes or
  restarts. Do not maintain a global registry of candidates or introduce an expiry
  service. Abandonment means dropping application references; it cannot activate
  anything and requires no framework discard operation.
- Capture the active generation's ID as the candidate's expected base when
  preparation begins. Retain only the base ID for this check, not a chain of
  previous generation objects.
- `publish` synchronously checks ownership, shutdown, and expected base, then
  atomically activates exactly the prepared generation. Reject a candidate if
  another generation has become active since preparation began. This prevents
  accidental publication of an obsolete update over a newer one.
- A candidate can publish successfully only once. Reject repeated publication,
  including when that candidate is still current. Rejected attempts leave active
  state unchanged. A stale or abandoned update's external artifacts remain the
  application's responsibility. To retry an obsolete update, prepare a new one
  and stage artifacts under its fresh ID.
- Serialize preparation calls with each other and publication calls with each
  other; hold no coordination lock across the application's staging interval.
  Publication may occur while another preparation reads files, making the latter
  candidate stale. Snapshot reads and ordinary execution continue during preparation.
  Atomic activation and eligibility checks share one authoritative boundary.
- Reject new preparation and publication after shutdown begins. If shutdown starts
  during preparation, do not return a publishable success. No candidate may
  activate after the shutdown boundary. A publication that already activated
  before that boundary remains successful. Preserve existing execution shutdown
  behavior; publication never drains or interrupts old invocations.

### Invocation consistency and application ownership

- Preserve PR 6.1's generation capture before input validation through preparation
  of inputs, queued handoffs, root execution, planning, nested calls, parallel work,
  and completion/release. Children inherit the tree's generation. New roots use
  the active generation; old work may still call a child deleted from the new one.
- Definitions, schemas, visibility, execution configuration, and manifest access
  policies cannot mix across generations. Existing authentication and authorization
  checks remain enforced; manifest permission changes apply to new invocation trees.
- The application stages generation-keyed REST configuration and any other external
  artifacts before publishing. Publication asserts that application preparation is
  complete; Loomspan neither validates external readiness nor manages a transaction
  across those systems. Handler code may use the supplied ID to select configuration.
- Initial startup continues to build and activate a validated generation. The
  application obtains its ID with `snapshot().generationId()` and initializes
  matching configuration before admitting its own traffic or initiating skills.
  This readiness ordering is application-owned. No new framework startup callback,
  fallback to current configuration, or extra activation mode is introduced.
- All external artifact storage, retention, cleanup, and readiness remain the
  application's responsibility. Do not add a retirement event, retirement query,
  cleanup callback SPI, reference-count API, or configuration registry. Publication
  is not evidence that old work has finished. Documentation must state that the
  application must retain old configuration for any work that can still use it;
  this API does not supply an external safe-deletion signal.

### Framework memory and diagnostics

- Retain generations while active or needed by admitted/running work or caller-owned
  candidates. Release unnecessary framework execution references on completion,
  failure, cancellation, or handoff release. Cleanup must not wait for another
  prepare call. Let ordinary Java GC reclaim unreferenced objects afterward.
- No fixed two-generation limit, forced eviction, WeakHashMap, prepare-time sweep,
  finalizer-driven lifecycle, or global history. Older work and retained candidates
  or snapshots can legitimately keep more than two generations/data sets alive.
  Retaining a catalog does not enable historical invocation or prevent execution
  retirement. Avoid retaining runtime state solely for catalog discovery data.
- Current observability discovery reads one active generation per operation.
  Execution diagnostics record the captured ID, including old work finishing after
  publication. Keep current writers, readers, projections, fixtures, and affected
  console consumers coherent. No historical catalog service or cross-request
  pagination snapshot guarantee is added.

## Acceptance criteria

- [ ] The service, prepared handle, catalog ID, and REST invocation ID are exposed
  through the deliberately supported API; no new SPI or internal-type leakage exists.
- [ ] Preparation validates and freezes the whole candidate without affecting active
  discovery/execution. Invalid candidates leave prior state usable. Initial startup
  validation and the documented Java/REST dependency rules remain enforced.
- [ ] Two identical successful preparations receive different IDs, each matching its
  candidate catalog. Publishing retains that ID and uses prepared contents even if
  source files are subsequently modified, removed, or made invalid.
- [ ] Additions, edits, deletions, renames, empty sets, collisions, and references to
  removed children have the specified outcomes. Java declarations remain fixed.
- [ ] Current and retained catalogs are immutable, coherent, exact-name sorted, and
  identified correctly. Snapshot access does not perform file reads.
- [ ] Foreign, stale, and repeated publications fail without altering active state;
  abandonment needs no framework cleanup call. Controlled concurrency verifies
  the expected-base rule, serialized operations, and shutdown/activation boundary.
- [ ] Delayed handoffs, nested calls, and parallel work retain matching schemas,
  child visibility, policies, and REST generation IDs after activation. A handler
  using configurations A and B sees A for old work and B for new work, including
  when both generations were prepared from identical YAML. Model input cannot
  override the runtime ID. Existing authorization remains enforced.
- [ ] Three or more needed generations can coexist. Completion, failure,
  cancellation, and release remove unnecessary framework-owned references without
  another preparation or a GC-timed test. No archive or external cleanup API exists.
- [ ] Diagnostics and affected current consumers identify the actual execution
  generation, including work completing after publication. Architecture and relevant
  integration checks pass with coherent fixtures and any protocol-marker changes.
- [ ] README and application examples cover initial ID/configuration readiness,
  prepare → stage → publish, fixed REST handler with generation-keyed configuration,
  stale publication, fresh IDs for unchanged YAML, errors, snapshots, shutdown,
  file stability during prepare, empty-set deletion, and application-owned retention.
  The example does not delete configuration just because a new generation published.

## Context and sequencing

Depends on [PR 6.1](loomspan-pr-6.1-coherent-skill-generations.md), which owns
complete generation assembly and invocation retention. Extend those owners rather
than creating another registry lifecycle. The feature exists because application
artifacts such as REST endpoints must be staged using the future generation ID
before activation. A fresh ID is necessary even when only external artifacts change.

GPT-5.6 Sol is the intended coding model. Internal decomposition, synchronization
primitives, and exception constructors are implementation choices; observable
rules above are requirements. Reuse framework concepts and remove obsolete paths.
Tests and final cross-boundary review are included here; no separate PR 6.3 is
needed. Do not start implementation merely by creating this ticket.

Excluded: file watching, console editing, per-skill reload, Java hot swapping,
bean replacement, model reconfiguration, distributed transactions, deployment
registries, multi-instance activation, content equivalence checks, durable history,
rollback APIs, or application resource lifecycle management. See the
[roadmap](../phases/loomspan-phase-6-reloadable-skills.md).

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Supported API/SPI metadata, publication concurrency, execution
  lifecycle, authorization consistency, and diagnostics require full assurance.
- **Reassessment triggers:** Missing PR 6.1 foundations or scope beyond the stated
  ownership/activation contract; routine internal decisions do not require approval.

## Pipeline notes

Development-time breaking changes required by this feature are explicitly
approved. Change `SkillCatalog` and `RestSkillInvocation` directly; do not preserve
old constructors/signatures through compatibility shims, aliases, fallback readers,
or dual behavior. Update affected callers, samples, fixtures, tests, and guidance
atomically. Existing handlers gain trusted metadata without becoming replaceable
framework beans; skill YAML authors need no new syntax.

Apply the [framework feature design lens](../framework-feature-design-lens.md):
classify API, SPI, configuration/manifest, diagnostics, and internal surfaces;
record the public delta, developer impact, and explicit no-shim decision. Trace
and console integration may change coherently for this feature without historical
readers. Assess any affected compatibility marker under the documented current
version/development policy and record the decision; preserve security, redaction,
and diagnostic usefulness. Run `LoomspanPublicSurfaceArchitectureTest` after
production type changes. No waiver of correctness or independent review is implied.
