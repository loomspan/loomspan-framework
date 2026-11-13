# Provider and Schema Migration Ticket Overview

Agreed November 13, 2026. The Framework migration is divided into 17 tickets across
four phases, intended for implementation by Sol 6.1. This overview records their
scope, sequence, dependencies, expected build state and verification boundaries.
The 17 individual tickets were created November 13, 2026. See the
[ticket index and requirement coverage](migration-ticket-coverage.md). This overview
is not an execution ticket; no pipeline has been started.

Use **PR 30.phase.sequence**, as shown below. These identifiers override the usual
integer-number allocation in [write_ticket.md](../../commands/write_ticket.md).
They are proposed ticket identifiers, not claims that GitHub PRs already exist.

## Governing material

- [Repository guidance](../../../AGENTS.md) governs content fidelity and the closed public API.
- [Migration roadmap](spring-ai-responses-migration.md) owns the accepted technical decisions,
  numbered TBD requirements, preservation constraints and acceptance details.
- [Schema research](standard-json-schema-research.md) retains technical inventory,
  versioned adapter evidence, probes and superseded proposals.
- [Test-suite roadmap](../../../../loomspan-sidecar-test-suite/MIGRATION-ACCEPTANCE-ROADMAP.md)
  owns later deployed and live acceptance.
- [Ticket-writing command](../../commands/write_ticket.md) governs individual ticket authoring,
  subject to the explicit numbering and migration instructions here.

The bounded readiness review concluded that the known areas have sufficient handoffs
to proceed to implementation planning and ticket preparation. Implementation,
integrated verification and deployed/live acceptance remain pending. This overview
organizes the agreed requirements; it does not replace their technical detail.

## Ticket sizing and execution rules

Group work by cohesive implementation boundary rather than TBD number. Related
corrections touching the same code should be implemented together, while each ticket
has one clear outcome and explicit exclusions. Size by interacting concerns and
decisions rather than file count. Provider-specific work stays separate; shared
schema machinery is established once and reused by its consumers.

Destructive updates are encouraged. Loomspan is still in development with no
deployments to support. Remove superseded implementations instead of introducing
temporary compatibility layers, aliases, dual readers or fallback paths. Supported
public API changes still require deliberate treatment under AGENTS.md; internal
Java types do not require compatibility shims merely because they are public.

The developer will commit between tickets without syncing. An intermediate ticket
may intentionally leave the build broken. Each such ticket must name the expected
breakage, its downstream restoration owner and the verification that remains blocked.
Blocked tests must never be reported as passed. Put this authorization in applicable
Pipeline notes so later stages do not stop over an intentional intermediate state.
It does not waive correctness or the final integrated checks.

Tests and fixtures necessary to verify a ticket belong with that ticket. The final
tickets must not become a dumping ground for unfinished implementation. When a shared
build failure prevents focused verification, record the exact blocked checks and
carry them into the restoration and final integration gates.

Preserve existing worktree changes and retained probes, including the separately
completed stream-closure repair. Keep deferred Java declaration edge cases outside
implementation and acceptance scope; do not restore their removed details or probes.

## Phase 1 Establish the provider foundation

### PR 30.1.1 Select and integrate the dependency baseline

**Scope:** Select the exact Boot/Spring AI/SDK/NetworkNT combination. Resolve
dependency alignment and mechanical Framework API changes. Record which known adapter
fixes the selected artifacts contain or still require. Preserve the existing
stream-closure repair.

**Dependencies:** None.

**Expected build state:** Aim for compilation and startup. Explicitly identified
adapter-transition failures may remain for the following tickets.

**Verification:** Effective dependency tree, compilation, startup, existing regressions
and public-surface architecture test. Distinguish platform failures from known pending
migration behavior.

### PR 30.1.2 Replace OpenAI and Ollama paths with shared Responses

**Scope:** Replace model construction and request options; remove native Ollama and
legacy Chat implementation/configuration. Preserve credentials, headers, timeouts and
retry ownership. Preserve ordered message parts during canonicalization. Integrate
verified Responses schema-precision fixes. Cover effort, caps and OpenRouter routing
requirements within the shared path.

**Dependencies:** PR 30.1.1.

**Expected build state:** Shared Responses path should compile and operate. Legacy
fixtures outside its migrated coverage may remain broken.

**Verification:** Real factory/tool-manager multi-round captures: stateless replay,
IDs, ordering, reasoning, canonical names, numeric schema precision, strict=false,
option combinations and model overrides. No duplicate tool effects.

### PR 30.1.3 Preserve Anthropic schemas and native options

**Scope:** Repair tool root-schema loss through the selected dependency strategy.
Use native output configuration while preserving effort and other options. Support
the selected acceptance model's native effort without rejected thinking budgets or
cap inflation; preserve legacy-model behavior.

**Dependencies:** PR 30.1.1.

**Expected build state:** Anthropic adapter boundary should be testable; shared schema
consumers may still use legacy contracts.

**Verification:** Actual tool/output request captures, root and nested constraints,
changing per-call schemas, effort/format combination, unchanged generation caps and
legacy controls.

### PR 30.1.4 Preserve Gemini raw schemas and native options

**Scope:** Integrate raw input/output schemas, bypassing both narrowing tool
conversions. Preserve per-request options and canonical contracts. Map native thinking
level and caps for the selected acceptance model.

**Dependencies:** PR 30.1.1.

**Expected build state:** Gemini adapter boundary should be testable independently
of the schema migration.

**Verification:** Actual requests containing nullable types, ordinary-input typed maps,
unconstrained leaves and generated reserved properties. Test changing schemas, option
combination, native MEDIUM and unchanged caps.

### PR 30.1.5 Reject explicit refusal and incomplete responses with faithful accounting

**Scope:** Implement TBD-12 across all three adapters: required evidence retention,
explicit-status classification, rejection before correction/planning/dispatch, zero
partial-batch execution, failed outcomes and usage counted once. Preserve evidenced
transport retry behavior.

**Dependencies:** PR 30.1.2, PR 30.1.3 and PR 30.1.4.

**Expected build state:** Provider boundary should compile and pass focused tests.
Later schema migrations may temporarily disturb the wider build.

**Verification:** Controlled HTTP responses through real pipelines: valid-looking
incomplete JSON, refusals, incomplete multi-tool responses, complete tool turns, prose
controls, failed lifecycle evidence, missing/zero usage and quota/cancellation collisions.

### Adapter completion boundary

Adapter tickets cannot declare completion merely because an upstream fix is proposed
or merged. Identify the exact tested artifact containing it. If no acceptable artifact
is available, the affected ticket remains blocked while independent work continues.
Copied research classes, another transport and silent schema weakening remain excluded.

## Phase 2 Replace the schema and input foundations

### PR 30.2.1 Introduce canonical standard schemas and lossless ingestion

**Scope:** Replace legacy schema declaration/parsing structures with the agreed internal
Draft 2020-12 representation and NetworkNT foundation. Implement profile checks, formats,
standard semantics, annotation placement/extraction and lossless YAML/JSON handling.
Reject non-JSON YAML keys/numbers and obsolete syntax.

**Dependencies:** PR 30.1.1.

**Expected build state:** May intentionally break downstream consumers until their
owning tickets migrate them. No dual-format reader.

**Verification:** Focused compiler/validator tests: exact names, omission/null/openness,
integer and enum semantics, precise bounds, supported formats, unsupported declarations,
canonical immutability and diagnostic fidelity.

### PR 30.2.2 Align generated Java contracts and binding

**Scope:** Migrate Java generation directly to canonical schemas. Group the retained
Java corrections: resolved generics, naming, enum names, interface remapping, UUID,
map-key restrictions, JsonUnwrapped rejection, recursion/runtime-leaf ordering,
authored descriptions and demonstrated Resource container binding. Preserve
SkillExecutor's YAML-owned contract and existing cleanup.

**Dependencies:** PR 30.2.1.

**Expected build state:** Java declaration subsystem should compile; other schema
consumers may remain broken.

**Verification:** Registration-to-validation-to-invocation cases, no publication for
rejected declarations, finite generic controls, runtime leaf identity, descriptions,
conversion failures and stream closure. Deferred Java edge cases remain excluded.

These corrections share the generation/introspection/binding path and one end-to-end
harness. Keeping them together reduces repeated rewrites. Explicit exclusions must
prevent renewed annotation-by-annotation exploration.

### PR 30.2.3 Migrate input validation and ordinary tool ingress

**Scope:** Replace custom structural validation. Implement ordinary JSON compatibility,
open-property checks, runtime map-key rejection and marked Java runtime-value adaptation.
Preserve validation/resolution/binding ordering and generic-reference behavior. Fix
callback decimal precision, whole-document parsing and object-root enforcement.

**Dependencies:** PR 30.2.1 and PR 30.2.2.

**Expected build state:** Input and callback paths should compile and pass focused
checks; output/planning consumers may still be broken.

**Verification:** Public map invocation and actual callback/tool-manager tests:
unchanged inputs, precise decimals, invalid roots/suffixes, no side effects on rejection,
authorization, marked/unmarked references, runtime leaves and parent/sibling constraints.

### PR 30.2.4 Migrate attachment validation materialization and trace descriptors

**Scope:** Implement attachment annotation consumption and direct-Resource validation.
Group MIME parsing/case handling, typed-map traversal, bounded size/hash reads,
unreadable-resource failures, missing filenames, null-preserving containers and JSON
Pointer paths. Record descriptors correctly in coordinator and both engines; propagate
through journal/observer. Correct generic failure advice.

**Dependencies:** PR 30.2.1 and PR 30.2.3.

**Expected build state:** Attachment paths should pass focused tests; broader
output/planning migration may remain incomplete.

**Verification:** Reference/direct Resource cases, nested/maps/arrays, nulls, filenames,
precise associations, bounded reads, no invocation on failure, descriptor-only planning,
media execution, canonical traces and the existing journal-redaction exception.

### PR 30.2.5 Migrate binding ownership projection and composition

**Scope:** Migrate child-input and output-binding schema consumers together. Preserve
snapshots, producer identity, exact values, whole-object/array transfers, required-work
checks and fully bound synthesis skipping. Implement exact-name matching, standard
openness and numeric enum equality.

**Dependencies:** PR 30.2.1 and PR 30.2.3.

**Expected build state:** Binding components should compile; final generation paths
may remain broken until Phase 3.

**Verification:** Partial/full bindings, nested nullable ancestors, open residual space,
reserved false schemas, equal/null override rejection, exact numeric transfers, invalid
sources, producer changes and unchanged canonical/projected contracts.

## Phase 3 Integrate schemas through execution

### PR 30.3.1 Apply native contracts to ordinary skill output

**Scope:** Carry canonical/projected output contracts through internal requests and all
three provider mappings. Implement evidence-backed omission rules, local lossless
validation, standard prompt openness and full composed-output acceptance. Preserve
ordinary text mode and existing correction budgets.

**Dependencies:** Phase 1, PR 30.2.1 and PR 30.2.5.

**Expected build state:** Ordinary output path should compile and pass. Planning/step
and remaining metadata consumers may still be broken.

**Verification:** Actual initial and correction requests on all three adapters;
projected versus canonical contracts; local enforcement of omitted assertions; schema
isolation; precision; unsupported-contract failures without downgrade or retry-based
weakening.

### PR 30.3.2 Migrate planning and step execution contracts

**Scope:** Apply the standard/native schema machinery to planning and the agreed step
execution boundaries. Remove superseded planning parser fallbacks/aliases. Preserve
semantic plan checks and execution ownership. Include step-action decimal precision
and explicit-null toolArguments correction.

**Dependencies:** PR 30.3.1, PR 30.2.3, PR 30.2.4 and PR 30.2.5.

**Expected build state:** Planning and step execution should compile and pass focused
checks. Remaining TypeSafe/catalog consumers may still prevent a clean full build.

**Verification:** Planner schema versus final-output schema, initial/correction/override
captures, semantic rejection, exact action values, null-argument rejection before
effects, final-phase tool prohibition, bindings, evidence and required-work regressions.

### PR 30.3.3 Preserve completed work during ordinary final-output repair

**Scope:** Implement TBD-11 as one cohesive change across schema/linter/evidence
advisors, the interaction context, provider capture and canonical tool manager. Use
call-local state, raw replies, explicit replay-segment ownership and a terminal repair
flag. Remove executable tools and enforce no dispatch.

**Dependencies:** PR 30.1.5, PR 30.3.1, PR 30.2.4 and PR 30.2.5.

**Expected build state:** Ordinary repair pipeline should pass integrated tests. This
ticket should not introduce a new intentional build break.

**Verification:** Actual captures for each advisor and combined paths on three adapters:
completed exchanges, latest candidate, no duplicate history, zero repair side effects,
default callbacks, concurrent/nested isolation, attachments, snapshots, usage and
exhaustion.

### PR 30.3.4 Migrate TypeSafe evidence metadata and effective catalogs

**Scope:** Remove remaining legacy schema dependencies from native TypeSafe and
catalog/display metadata. Preserve descriptive-only TypeSafe outputs and native
acceptance. Include request/response decimal fidelity, exact evidence claim names and
faithful structural metadata without inherited child-local evidence.

**Dependencies:** PR 30.2.1, PR 30.2.3, PR 30.2.5 and PR 30.3.1.

**Expected build state:** Framework build should return to green at the end of Phase 3.
Remaining failures must be identified, not deferred generically.

**Verification:** Native mock protocol/request reuse and answer passthrough; precise
decimals; evidence extraction/placement; forwarding/catalog snapshots; canonical versus
effective schema separation; public-surface checks.

Ordinary output and planning/step tickets share an established schema transport
mechanism; the latter extends it rather than inventing another path. History repair
stays separate because its state, ordering and side-effect requirements warrant a
focused ticket.

## Phase 4 Complete consumers and establish Framework acceptance

### PR 30.4.1 Update Console schema and attachment views

**Scope:** Update Console contracts/rendering for standard effective schemas and
attachment descriptor records. Use existing content endpoints and chunk reconstruction.
Preserve original YAML, inert rendering and diagnostic fidelity. No new browser validator
or file dereferencing.

**Dependencies:** PR 30.2.4 and PR 30.3.4.

**Expected build state:** Framework and Console builds should be green.

**Verification:** Contract/parity tests, effective-schema display, TypeSafe metadata,
attachment details, missing optional fields and chunked payloads.

### PR 30.4.2 Finish authoring migration and removal audit

**Scope:** Complete README, examples and authoring guidance; reconcile remaining active
declarations and fixtures. Audit removal of legacy schema structures, Chat/native Ollama
paths, obsolete configuration and redundant formatting machinery. Preserve historical
evidence and separate Console contracts.

**Dependencies:** Phases 1 through 3 and PR 30.4.1.

**Expected build state:** Green; no intentional new breakage.

**Verification:** Repository reference audits, affected examples/fixtures and
documentation checks. Verify exactly three chat implementations, separate native
TypeSafe and no aliases, fallback or dual-format support.

### PR 30.4.3 Verify integrated Framework migration readiness

**Scope:** Run the complete agreed deterministic acceptance set against the final
artifacts. Close cross-ticket integration failures, record dependency/fix identities
and map requirements to evidence. Produce the handoff for later test-suite implementation.

**Dependencies:** All preceding tickets.

**Expected build state:** Green is mandatory.

**Verification:** Full applicable Framework/Console checks, public-surface architecture
tests, real adapter/pipeline captures, isolation, retries, status/usage, schema fidelity,
attachments and removal evidence. Clearly identify remaining deployed/live gates.

## Individual ticket completeness

Each ticket must be self-contained for a fresh Sol 6.1 context. Include:

- Applicable settled requirements, rationale, technical findings, reproduction anchors
  and evidence limitations. Links to probes supply artifacts, not a substitute for
  explaining their results and what remains unproved.
- Required behavior versus nonbinding implementation suggestions, with exact semantics,
  integration seams, preservation constraints and observable acceptance criteria.
- Destructive-change authorization and the absence of deployment compatibility
  obligations, while respecting the closed public API boundary.
- Precise exclusions, especially the deferred Java declaration cases.
- Dependencies and any permitted intermediate breakage, including restoration ownership
  and blocked verification.
- An execution-profile recommendation following the ticket command and its shared
  protocol; the recommendation does not waive ticket requirements or verification.

During ticket drafting, maintain a coverage map from every agreed requirement and
technical finding to an owning ticket. Check the complete set for omissions,
contradictory instructions and duplicated ownership. Accepted decisions override older
research proposals. Preserve historical qualifications rather than presenting probe
success as integrated acceptance. Do not create tickets for the already completed
stream-closure repair as though it were missing.

## Later test-suite work

Do not create test-suite implementation tickets in this Framework batch. The agreed
sequence places those after Framework implementation. The neighboring suite roadmap
retains the live matrix, budgets, timeouts and the unresolved additional OpenRouter
comparison-scope question. That question does not expand the initial matrix or budget.

Framework completion does not establish deployed/live acceptance. No live provider
calls are authorized by this overview. Individual ticket creation and pipeline
execution remain separate actions from saving this agreed breakdown.
