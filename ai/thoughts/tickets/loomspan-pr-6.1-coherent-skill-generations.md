# PR 6.1 — Execute against coherent skill generations

## Outcome

Build the internal foundation for preparing and publishing complete skill
updates without mixing generations inside an invocation tree. Keep generation
assembly and execution propagation together so the next PR can expose the
operation without redesigning loading, registration, or invocation state.

## Requirements

### Complete, immutable generations

- Construct an initial generation from all configured model-backed YAML and REST
  manifests plus fixed Java declarations. Include related definitions, capability
  metadata, input contracts, and immutable discovery data in one coherent view.
- Separate whole-candidate preparation and validation from atomic activation.
  Preserve existing declaration validation, including duplicates, schemas, REST
  handler requirements, and exact child references to candidate YAML or Java
  skills. Invalid startup fails; invalid replacement leaves active state intact.
- Every successfully prepared candidate gets a fresh opaque process-local string
  generation ID, even if its YAML is identical. The ID is not a content hash.
  Assign the initial startup generation an ID too. Publication retains the ID
  assigned during preparation; do not generate another ID at activation.
- Candidate data is immutable and independent of subsequent source-file edits.
  Internal publication uses prepared data without rereading or revalidating files.
  Support complete additions, edits, removals, and an empty YAML set. Java
  declarations remain; a remaining reference to a removed child is invalid.
- Java implementations, Spring beans, model connections, and REST handler beans
  remain fixed. Do not rediscover Java beans per candidate or create replacement
  dependencies. Do not add YAML syntax or application properties for this work.
- Build immutable catalog snapshots from generations. Existing snapshots,
  including the initially injected `SkillCatalog`, remain unchanged. Include an
  internal association with generation identity for PR 6.2's public accessor.

### Capture and execution

- Capture a generation before invocation input validation. Retain it through
  input preparation, delayed admitted handoffs, root execution, routing,
  planning, nested calls, parallel work, and completion or release.
- Children inherit the invocation tree's generation. New roots capture the
  active generation. Standalone validation uses one coherent generation but
  does not reserve it for a later unrelated invocation.
- Invocation-dependent definitions, schemas, child visibility, capability access
  policies, and execution configuration use that generation. Old work may use a
  child removed from the current generation. Existing authentication and
  authorization checks still run; manifest policy changes affect new invocation
  trees, not already prepared or admitted work.
- Carry identity through the existing execution lifecycle rather than a parallel
  global context or model-controlled input. Exposing the ID on the REST invocation
  API and execution diagnostics belongs to PR 6.2.

### Framework memory ownership

- Retain active generations and generations needed by input preparation, admitted
  handoffs, running work, or prepared candidate owners. Completion, failure,
  cancellation, and handoff release must release unnecessary framework-owned
  references without depending on another prepare call.
- Superseding a generation stops new roots acquiring it; existing work retains
  it. A retained catalog is discovery data and does not authorize invoking a
  historical generation. Avoid retaining entire execution state merely for a
  catalog snapshot.
- Use ordinary strong ownership while data is needed, then let Java garbage
  collection reclaim unreferenced data. Do not use WeakHashMap, finalizers, a
  prepare-time sweep, or GC notifications as the correctness mechanism.
- No global generation archive, linked chain of previous generations, fixed
  two-generation limit, or drain-before-publication policy. More than two live
  generations are valid when older work or callers still need them. Application
  references can retain objects; Loomspan cannot force their collection.
- Everything outside the framework is application-owned. No external artifact
  cleanup, retirement notification API, cleanup callback SPI, or configuration
  registry is part of this ticket.

## Acceptance criteria

- [ ] Valid startup preserves skill behavior; invalid initial declarations fail
  with useful diagnostics. Invalid candidates leave active state usable.
- [ ] Identical valid preparations get different IDs without activation; controlled
  internal publication keeps the candidate's ID and complete contents, including
  when files changed or disappeared after preparation.
- [ ] Whole-set additions/removals and empty YAML sets work against unchanged Java
  declarations and dependencies; unresolved child references reject the candidate.
- [ ] Input preparation, delayed handoffs, nested calls, and parallel work retain
  the correct generation across schema, visibility, and manifest policy changes,
  with existing authorization enforced.
- [ ] New roots use the active generation while older trees can still use their
  removed children. Retained catalog snapshots remain immutable and coherent.
- [ ] Completion, failure, cancellation, and handoff release relinquish unnecessary
  framework references independently of subsequent preparation. Three or more
  legitimately retained generations can coexist without forced eviction.
- [ ] Relevant startup, execution, authorization, lifecycle, and architecture tests
  pass. Verification uses controlled concurrency and ownership evidence, not
  arbitrary sleeps or GC timing. No new public reload API or SPI is introduced.

## Context and sequencing

An application publishes a stable set of skill files. Loomspan prepares a valid
candidate and returns an ID; the application stages related artifacts under that
ID and explicitly publishes the candidate. The ID must be fresh even for
identical YAML because application-owned endpoint configuration may change.
Loomspan manages only its own generation data and invocation lifetime. The
application owns external configuration, its startup readiness, and its retention.

This ticket provides the internal foundation; it does not expose the public
prepare/publish API yet. PR 6.2 adds `SkillReloader.prepare()`,
`publish(candidate)`, `snapshot()`, generation metadata on public catalog and REST
invocation types, publication eligibility checks, and diagnostics. No separate
PR 6.3 is needed; tests accompany both implementation PRs.

There is no preceding Phase 6 dependency. GPT-5.6 Sol is the intended coding
model. Exact internal classes and storage mechanisms are implementation choices;
reuse existing owners and remove obsolete paths instead of parallel mechanisms.
See the [roadmap](../phases/loomspan-phase-6-reloadable-skills.md).

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Broad execution, authorization consistency, lifecycle, and
  concurrency changes require research, planning, tests, and independent review.
- **Reassessment triggers:** Scope beyond generation preparation and execution
  retention, or evidence the required invariant cannot be met with the agreed
  boundaries. Routine internal refactoring choices do not require approval.

## Pipeline notes

Loomspan is in development. Breaking changes needed for this generation refactor
are authorized; no compatibility shims, overloads preserving obsolete contracts,
deprecated paths, or dual behavior. Update affected callers, samples, fixtures,
tests, and documentation together. Public preparation/publication and REST API
changes are scoped to PR 6.2 so this PR leaves a coherent working intermediate state.

Apply the [framework feature design lens](../framework-feature-design-lens.md):
classify affected surfaces, record impact, reuse existing authority, and remove
in-scope obsolete code. No migration requirement overrides the explicit no-shim
instruction. Preserve security, catalog immutability, and useful current diagnostics.
Run `LoomspanPublicSurfaceArchitectureTest` after production type changes.
