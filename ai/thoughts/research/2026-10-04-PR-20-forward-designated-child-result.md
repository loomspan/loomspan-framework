---
date: 2026-10-04T14:24:05-07:00
researcher: GPT-6 (exact model variant unknown)
git_commit: 5c46862d04c85ba0d37c40b52feb35094a280b67
branch: main
repository: loomspan-framework
topic: "PR 20 — Return a designated child result without model synthesis"
tags: [research, codebase, planning, output-contracts, skill-generations, console]
status: complete
last_updated: 2026-10-04
last_updated_by: GPT-6
---

# Research: designated child-result forwarding

## Research question and execution context

Map the existing configuration, planning, result, validation, lifecycle, metadata,
diagnostic, and documentation paths relevant to
`ai/thoughts/tickets/loomspan-pr-20-forward-designated-child-result.md`.
This is Step 1 in pipeline mode; the selected profile is **full**.
The user approved the Full 5-Step Pipeline and explicitly accepts Java–Go
boundary changes necessary for this feature. That authorization does not waive
coherent consumers, fixtures, version decisions, verification, or independent
review. The ticket controls the product behavior; this document describes the
current checkout rather than selecting a future implementation.

Initial Git status contained only the untracked ticket. No unrelated changes
were observed. Research edits only this artifact and does not run tests or
change production code. Metadata was collected using
`bash ai/scripts/spec_metadata.sh`; it reported main, the commit above, and
2026-10-04 14:23:13 PDT. `git config user.name` reports matt-loomspan-ai;
no thoughts-system researcher identity was returned.

Research checklist completed: manifest/registration; planning cardinality and
visibility; execution/results/lifecycle; schema validation and metadata;
Console and journal consumers; documentation routing and current tests.

## Summary

There is no current `output_from` declaration or forwarding completion path.
Planning missions execute every accepted task, retain complete direct returns
in a mission-local collection keyed by task ID, and then perform a final
model synthesis step. Existing direct-child task-count constraints already
express exactly one generated task through `required: true` and `max_tasks: 1`.
The root captures an immutable complete skill generation, and children and
parallel workers use that same generation.

The framework result boundary is String text. Java results are Jackson JSON
serialization, including JSON quoting of Java String values; REST handlers
return non-null Strings unchanged; model skills return their normal String
result. Planning task results preserve this boundary. Authored model schemas
are currently also the validator input, with no distinct effective metadata
facility. The supported public catalog and Console source catalog expose input
contracts or original declaration text, not a resolved output-schema field.
Console has a closed trace record vocabulary and executable Java-written/Go-read
fixtures, so diagnostic representation is a coordinated boundary.

## Detailed findings

### Configuration and complete registration

- `src/main/java/ai/loomspan/internal/skill/YamlSkillManifest.java:23` owns a
  field-presence enum; `:74` and `:84` hold planning mode and authored output
  schema. Setters retain declaration presence, including explicit null. No
  `output_from` field exists. YAML decoding rejects unknown properties.
- `YamlSkillCatalog.java:311` reads and validates each document, validates input
  schema, branches for REST, requires a model for ordinary YAML, normalizes
  output schemas and compiles evidence, then constructs `YamlSkillDefinition`.
  `:395` reads raw YAML trees and performs applicability/type checks before
  binding. Errors become structured `SkillValidationIssue`s through checked
  document loading; configured startup and supplied-document validation share
  this machinery.
- `YamlSkillCatalog.java:59` defines structured allowed-child fields, and `:462`
  validates sequence/object shape, strict nonblank exact names, uniqueness,
  unknown fields, nonnegative integer bounds, Boolean required, and bound
  consistency. Any task-count declaration requires explicit planning mode true.
  The effective minimum is max(authored minimum or zero, required ? one : zero).
- `YamlSkillCatalog.java:60` defines the REST forbidden field matrix, checked
  both on the raw tree and loaded manifest. REST cannot declare planning,
  children, output schema, output-schema retries, linter, or model fields,
  even when null. `:646` restricts schema retries to authored schemas, defaulting
  them to two and allowing zero through three. Root output schemas are objects;
  supported vocabulary and recursive normalization are existing model contracts.
- `YamlSkillDefinition.java:14` is an internal record. Its constructor copies
  the manifest, freezes normalized child constraints, retains source bytes,
  and separates REST (no execution config) from model YAML (required config).
  `manifest()`, `inputSchema()`, `outputSchema()`, and `linter()` return defensive
  copies. `outputSchema()` at `:93` currently means the authored schema only.
  Explicit true alone selects planning (`:154`).
- `SkillGenerationManager.java:243` checks an entire candidate set together with
  fixed Java declarations. It rejects duplicate names across kinds, resolves
  input contracts, requires exactly one REST handler when REST definitions
  exist, and checks every direct allowed-child name against the complete set.
  Failed-document names suppress cascading missing-child diagnostics. `:294`
  constructs capabilities and definitions together, including the REST handler
  closure bound to the candidate generation ID. Validate, startup, and reload
  preparation use this complete-set path.
- `SkillGeneration.java:18` freezes capability/definition maps and builds both
  catalogs; `owns()` checks exact metadata identity. Capture/owner leases in
  `SkillGenerationManager` keep old generations alive while owned. Activation
  changes independently captured roots, not an already running tree.

### Plan validation, direct visibility, and task identity

- `DefaultPlanningService.java:270` preflights positive-minimum children against
  the already authorized visible surface before a planning model call. It then
  validates raw structure, converts the plan, validates task counts, and
  validates evidence coverage. One total corrective planning attempt is shared
  across these rules. Exhaustion occurs before plan storage or `PLAN_CREATED`.
- `PlanStructureValidator.java:31` checks task IDs, unique IDs, exact visible
  capability names, dependency arrays and earlier execution units, and strict
  consecutive parallel-group rules. It does not pick result-producing tasks.
- `PlanTaskConstraintValidator.java:20` counts exact non-null
  `PlanTask.capabilityName`s independently of title, intent, status, dependency,
  and tool-call count. `required: true, max_tasks: 1` entails exactly one matching
  accepted plan task; zero and multiple uses participate in existing validation
  failure/retry diagnostics.
- `DefaultCapabilityInvoker.java:59` binds capabilities from the parent's visible
  tools. Bound callbacks accept an explicit task ID. The assigned-step path
  supplies it, so matching is not reconstructed from global skill-name lookup.
  Nonassigned tool calls have separate existing unique-ready-task credit logic.
- `CapabilityExecutionRouter.java:45` applies access and input validation, checks
  the session binding and exact captured-generation ownership, and routes to
  the coordinator. `ExecutionCoordinator.java:96` obtains YAML definitions from
  that binding, creates a fresh child `MissionContext`, and restores the parent
  through binding scope. Definition names are exact registered identities;
  diagnostic file paths are not invocation aliases.

### Execution, completion, and fidelity

- `StepLoopMissionExecutionEngine.java:190` captures the binding for its owning
  executor call, materializes inputs, initializes the plan, and uses the mission
  timeout and `MissionLifecycle` for owning future/cancellation/cleanup.
- `:327` partitions the accepted plan into ordered execution units once. Every
  task receives a fixed task ID, capability, and step number. Groups use atomic
  admission and optional concurrent dispatch, await all ordinary outcomes, fold
  in task-list order, then propagate the first task-list failure. Serial units
  use the same assignment/outcome machinery. All accepted work is traversed.
- `:374` reserves one final-synthesis step while admitting each complete unit.
  Consequently N tasks currently require at least N+1 `max_steps` slots.
  Correction attempts remain within one task step. `:471` checks lifecycle open,
  obtains the completed plan, checks the remaining step budget, invokes
  `executeOneStep(..., assignment=null)`, requires FINAL_RESPONSE, and returns
  that model result. There is no alternative completion branch today.
- `:690` folds a successful task into COMPLETED status, success credit, bounded
  progress summary, and the complete task result. Failed tasks become FAILED
  and mark the plan STALE. Later units do not execute after a propagated failure.
  Cleanup/cutoff folding exists separately for mission-wide termination.
- `MissionContext.java:115` stores complete results in an insertion-ordered map
  keyed by task ID. A result is `(taskId, skillName, String result)`; duplicates
  fail and null results fail, while empty/whitespace values are allowed. Nested
  missions own independent collections. Successes from siblings can be retained
  even if a sibling fails, but that does not turn the parent into a success.
- `StepLoopMissionExecutionEngine.java:1009` invokes a bound child and converts
  its existing return to text (`null` to literal `"null"`, otherwise String.valueOf).
  The existing production routes generally already return String. Only trace
  previews and progress summaries are truncated; the retained task result is
  complete. `StepPromptBuilder` renders complete earlier-unit/final task results
  as escaped JSON strings; dependency edges order work and do not filter these
  results. Group members share the same pre-unit snapshot.
- `SkillMethodBeanPostProcessor.java:485` invokes the final managed Java bean,
  binds arguments, and serializes the returned Java object with Jackson at
  `:505`. A Java String is therefore JSON string text, not its unquoted business
  value. Serialization failures fail execution. There is no Java output schema.
- `SkillGenerationManager#invokeRest` returns `RestSkillHandler.handle` String
  unchanged and rejects null. Transport, envelopes, and remote business fields
  are the application's handler responsibility, not a Framework unwrapping path.
- `ExecutionCoordinator.java:153` executes Java/REST directly using the common
  bounded mission work executor, reference resolution, and scoped authentication.
  Model children choose their own direct/planning executor at `:165`. A child's
  result is returned through the capability boundary, with child internals
  isolated. `DefaultSkillTemplate.java:270` returns String.valueOf of that result;
  public invoke returns text and does not deserialize a business value.

### Schema validation versus metadata

- `DefaultSkillAdvisorResolver.java:100` installs `OutputSchemaCallAdvisor` for
  ordinary model execution when `definition.outputSchema()` is non-null. It
  owns schema prompt guidance, unchanged candidate validation, and semantic
  correction. Evidence and linter advisors are separate existing layers.
- Planning final response uses `StepLoopMissionExecutionEngine.java:1119`:
  serialize the candidate, validate schema, then evidence, then linter. Each has
  diagnostic outcomes and configured retry behavior. Schema validation at
  `:1203` uses the same definition accessor as final prompt guidance at `:806`.
  Thus merely substituting a derived schema into that accessor also reaches
  validation consumers today; there is no existing metadata-only distinction.
- `OutputSchemaPromptAugmentor` emits recursive provider-neutral guidance;
  evidence annotations are orchestration metadata and excluded from model schema
  constraints. Evidence contracts are compiled from the parent's authored schema
  and direct-child names, so a child's evidence expressions have child-local
  meanings rather than the forwarding parent's meanings.
- `api/SkillDescriptor.java:6` is a supported record with name, description,
  kind, and inputSchema. It has no output-schema member. `BoundCapability.java`
  likewise exposes name, description, and inputSchema only. Current planning
  tool surface has no resolved output-schema field. `DefaultRegisteredSkillCatalog`
  shows source kind and exact original YAML/Java source metadata; it does not
  derive an effective output contract. These are distinct metadata boundaries,
  not evidence that new API signatures are required by the ticket.

### Diagnostics and protected consumers

- Assigned/final step execution opens STEP_EXECUTION and MODEL_CALL frames,
  records proposed/validated actions, and emits STEP_COMPLETED. Final synthesis
  currently records `stepAction: FINAL_RESPONSE` at
  `StepLoopMissionExecutionEngine.java:909`; child completion records task ID
  and tool name plus a result preview. A new forwarding response cannot be
  attributed to a final parent model response that never occurred.
- `ExecutionJournalProjector.java:75` selectively projects canonical records.
  It currently ignores ordinary STEP_COMPLETED and records skill lifecycle,
  tool result, validation, thought and failure entries. It uses its existing
  documented field-name transformation for the derived journal. AGENTS.md
  preserves precisely that exception; canonical/result fidelity rules still
  govern all other paths.
- `LiveActivityProjector` exposes a closed visible record set, maps activity
  kinds/phase/summary, and copies scalar metadata/data into bounded facts. Its
  `ExecutionActivityKind` and Go observability enum validators are the REST/SSE
  diagnostic boundary. Scalar facts can carry authoritative task identity, but
  visible event kind/summary requires its own current source mapping.
- `loomspan-console/internal/traceanalysis/enums.go:8` mirrors closed Java
  TraceRecordType and TraceFrameType values; unknown record types are rejected.
  `parser.go`, `model.go`, `frames.go`, `plans.go`, `index_writer.go` and query/
  browser/MCP adapters consume canonical NDJSON, assignments, plans, metadata,
  payloads, usage and failures. The Console browser and MCP tools depend on the
  current diagnostic representation, not supported Java internal constructors.
- Shared contracts are exercised in `loomspan-console-fixtures/traces/` and
  `application-rest/`, `application-sse/`; Java writers include
  `ConsoleTraceFixtureCorpusTest`, `ConsoleRestFixtureCorpusTest`,
  `ConsoleSseFixtureCorpusTest`. Go `traceanalysis/fixture_corpus_test.go` reads
  the repository corpus and verifies expected projections and invalidity cases.
  New canonical record kinds reach Go closed vocabularies and outward schemas.
- `consoleCompatibilityVersion` identifies exact coordinated Framework/Console
  versions; dual development attempts complete validation without a promise.
  The feature-design lens treats current trace/index formats as ephemeral,
  while portable canonical files require same-version transfer. No historical
  reader, dual trace format, or cross-version compatibility promise exists.

## Contract and exposure inventory

| Policy category | Existing relevant surfaces and support evidence |
| --- | --- |
| Application API | `SkillTemplate`, `SkillDescriptor`, `SkillCatalog`, `SkillReloader`, validation types and Java annotations are in the executable closed architecture allowlist and README. Invoke's String return and source/input catalog shape are existing supported behavior. No new public type is currently implied by source. |
| Supported SPI | `RestSkillHandler` is the sole supported SPI; it returns String and receives trusted generation identity. Internal binding/runtime interfaces do not constitute SPIs. |
| Configuration and manifest contracts | Documented planning_mode, max_steps, allowed_skills/counts, output_schema/retries, REST field matrix and validation paths. New output_from is absent today and explicitly requested as opt-in; existing definitions need no migration under the ticket. |
| Persisted or serialized contracts | Portable canonical trace files are the narrow same-version durable objects. Source documents are application-provided declarations. Console indexes/catalogs are transient, not durable history contracts. |
| Ephemeral diagnostic formats | Canonical NDJSON, journal, live REST/SSE facts, Console projections, fixtures and version marker have protected current consumers and usefulness/fidelity semantics. Java–Go changes are authorized, exact representation/version decision belongs in planning. |
| Internal or accidentally exposed implementation | YamlSkillDefinition/Manifest, SkillGeneration, MissionContext/results, coordinators, runtime interfaces, constructors, autoconfiguration beans and binding machinery are technically public in places but explicitly classified internal. All callers/tests can change together without API shims solely for Java visibility. |

Production wiring is in `LoomspanAutoConfiguration`; constructors, bean methods
and internal interfaces connect Spring subsystems. AGENTS.md expressly rejects
an internal bean-replacement contract. Architecture tests also allowlist internal
public types with rationale separately from the application API. Tests and
fixtures establish current behavior; they do not independently promote internals
to supported contracts. Research found no verified external consumer of these
internal result/definition constructors in this checkout.

## Documentation inventory and drift classification

The repository `agent-skills/loomspan-docs/SKILL.md` was read and applied as the
documentation router after production/test inventory, following
`ai/commands/shared/loomspan-docs-protocol.md`. This package and pom.xml both
identify 1.0.0-beta.8-SNAPSHOT and are from the same checkout.

Relevant fully consulted topics: skill-authoring README, source-verification,
mental-model, output-contracts, planning-task-constraints, planning-concurrency,
rest-skills, and checklists/evaluate-a-skill-design. Java invocation's output
guidance and docs/architecture were also inspected. Documentation locations
relevant to this ticket include the README routing/coverage table; output
contracts; synthesis/step-cost/full-evidence language in mental-model and
planning-concurrency; task count guidance; REST's forbidden-field matrix;
skill-design output/evidence questions; Java invocation's recommendation to
author output schemas for structured results; and traces/debugging as diagnostic
semantics change.

**Drift classification: aligned** for the researched current behavior. The guide
describes mandatory planning final synthesis, N+1 step reservation, complete
task-keyed results without copy accuracy guarantees, required child counts,
captured generations, and Java/REST output limitations consistently with source
and focused tests. `output_from` is not documented because it is not currently
implemented; this is new feature coverage, not evidence of an existing defect.
Examples in the ticket are requested behavior, not executable samples. Repository
test fixtures isolate syntax and runtime cases; no shipped forwarding sample
was found. The authoring library has explicit source anchors and LLM-first
progressive disclosure requirements. No reusable guidance should include the
ticket's motivating application test case or provider-specific claims.

## Existing executable evidence and verification locations

These commands are available verification entrypoints, **not results of runs in
this research step**. Build declares Java 21+ and Maven 3.9+, with JUnit/Surefire.

- `./mvnw.cmd test` is the full Java test entrypoint (CI uses `./mvnw test`).
- Targeted `./mvnw.cmd -Dtest=... test` can select `YamlSkillCatalogTests`,
  `YamlSkillDefinitionTest`, `SkillGenerationManagerTest`,
  `PlanningServiceTest`, `PlanTaskConstraintValidatorTest`,
  `PlanStructureValidatorTest`, `StepLoopMissionExecutionEngineTest`,
  `ConcurrentGroupedExecutionIntegrationTest`, `PlannerEvidenceFlowIntegrationTest`,
  `ExecutionCoordinatorOutputSchemaIntegrationTest`, and
  `SkillGenerationExecutionIntegrationTest`.
- `StepLoopMissionExecutionEngineTest` covers final schema/linter corrections,
  complete input/evidence delivery, assigned action correction, failure,
  timeout/cutoff, late task writes, joined groups, earliest task failure, and
  exact N+1 admission (`rejectsWholeGroupedUnitBeforeAdmissionWhenFinalSynthesisWouldNotFit`,
  `admitsExactlyNAssignmentsPlusFinalAtMaxStepsNPlusOne`).
- `PlannerEvidenceFlowIntegrationTest` captures real local HTTP model requests
  through supported `SkillTemplate`; it covers complete long returns, repeated
  skill identities, nested boundaries, prior-result snapshots and unrelated
  private data exclusions. `ConcurrentGroupedExecutionIntegrationTest` covers
  actual nested executors, auth, depth/timeouts/quotas and branch isolation.
- `SuccessfulSkillCompletionBoundaryTest` and nested success tests protect
  success-only credit; generation execution integration protects root capture
  through active-generation changes. Existing advisor/output-schema tests
  protect child validation/corrections.
- Mandatory after production type changes:
  `./mvnw.cmd -Dtest=LoomspanPublicSurfaceArchitectureTest test`.
- Java trace/journal tests and corpus writers, plus from `loomspan-console`:
  `go test ./internal/traceanalysis ./internal/observability ./internal/mcpadapter ./internal/browserapi`.
  Full Console CI uses `go run ./internal/buildtool verify` and browser Playwright
  E2E; selected expensive verification depends on the eventual UI/protocol diff.
- `python scripts/loomspan_version.py check` and
  `python -m unittest discover scripts/tests -v` check coordinated version and
  skill packaging rules if those surfaces change. No runtime was started,
  application migration performed, reference-suite replay refreshed, or tests
  run during research.

## Architecture documentation and historical context

Current architecture uses immutable generation capture, local declared children,
trusted execution bindings, mission-owned plans/results, isolated physical
branches, deterministic unit joins, and ancestor-aware lifecycle write fencing.
Schema, evidence and lint validation are separate model-output stages. Child
execution returns through the same skill boundary regardless of kind.

`ai/thoughts/framework-feature-design-lens.md` is the current compatibility and
design policy. There are no existing research artifacts in this checkout's
research directory. The other existing ticket concerns observation truncation,
not this feature; it was not treated as executable behavior. No historical
forwarding implementation or related prior research was found. Existing source
already owns all task-result and lifecycle concepts discussed here; research
did not establish dead or unreachable code that requires a separate removal
decision for this ticket.

## Open questions for implementation/test planning

These are implementation decisions, not unresolved product intent requiring a
developer answer at research handoff:

1. Where will effective output metadata be represented and exposed to existing
   callers/tooling/planners? Current public `SkillDescriptor` and bound-tool
   metadata do not expose any output-schema field, and the existing definition
   accessor feeds validators. The plan must distinguish derived metadata from
   authored validation and retain child-local evidence semantics.
2. Which existing complete-set registration location will own chain resolution
   and cycle rejection, and how will its immutable result enter every startup,
   validate and reload generation path?
3. Which authoritative completion diagnostic will identify parent plan/task/
   child result without inventing a model response? Record/activity vocabulary,
   projections, corpus and the compatibility marker decision need explicit
   treatment in the plan. Necessary Java–Go changes are already authorized.
4. How do forwarding completion and admission account for `max_steps` when the
   model synthesis step is absent? Existing N+1 synthesis behavior and tests must
   stay explicit for ordinary skills; exact forwarding accounting is not an
   existing executable behavior.
5. How is a forwarding parent's authored linter treated? Existing linting is
   part of final model validation; the ticket forbids parent output correction
   and content rewriting but does not declare a new linter policy. Planning can
   select coherent declaration applicability from the requested forwarding
   contract without broadening child validation.

All material supplied decisions and findings for the next context are in this
artifact. No developer escalation remains in Step 1.

