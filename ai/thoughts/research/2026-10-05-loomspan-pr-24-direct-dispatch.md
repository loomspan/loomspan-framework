---
date: 2026-10-05T16:24:07-07:00
researcher: Codex (model identity unknown)
git_commit: d9c0ff377630360016ee50e3cf97661227899a53
branch: main
repository: loomspan-framework
topic: "PR 24 direct dispatch for fully bound planned tasks"
tags: [research, codebase, planning, input-bindings, dispatch, observations]
status: complete
last_updated: 2026-10-05
last_updated_by: Codex
---

# Research: PR 24 direct dispatch for fully bound planned tasks

## Research question and execution context

Map current executable behavior and consumers for `ai/thoughts/tickets/loomspan-pr-24-direct-dispatch-for-fully-bound-tasks.md` before implementing it under the approved Full 5-Step Pipeline (`full`).

The developer approved execution and explicitly stated: **"we are in development so destructive changes are welcome and no compatibility shims."** This overrides older shim guidance for ticket-scoped source changes. It does not authorize deleting unrelated work or captured evidence. Preserve the existing journal-only field-name redaction exception. Do not add sensitivity classification. No commit, push, release, paid evaluation, or sibling reference-suite modification is authorized. The ticket's statement that ticket creation alone does not start execution is historical; the current developer request starts this pipeline.

Initial checkout had no tracked changes and only the requested untracked ticket. Metadata was gathered with `bash ai/scripts/spec_metadata.sh`; the commit and branch above are its actual output. No implementation or test execution occurred in this research stage.

Research checklist completed: assigned execution; contract projection and assembly; lifecycle/access/generation; provider hooks and accounting; canonical and derived observations; Console consumption; tests and authoring documentation; surface classification.

## Summary

Every assigned task currently generates and parses a parent model response before entering the exact assigned-action validator and shared capability invocation. `BoundCapability` already holds a binding projection compiled from the actual child contract and this parent's bindings. The invoker assembles fresh parent input and accepted prior-unit results at invocation, validates exact transferred values, records provenance, and routes through normal access and generation checks.

The current input-schema node represents a bounded subset of schema semantics. Unsupported raw JSON Schema keywords are not retained by the resolver. The current projected contract permits binding-created nested ancestors to be omitted but may also allow model-created ancestor objects; `allowsEmptyInput()` reports required-field coverage only. These facts matter to conservative eligibility and are design questions for planning.

Actual model accounting lives at provider-attempt boundaries. Task/tool and skill observations have independent normal lifecycle records. Console strictly enumerates canonical record types, but step metadata and ordinary record payloads already carry trusted assignment facts without requiring parent model frames.

## Detailed findings

### Assigned-task path and scheduling

- `src/main/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngine.java:193` owns the planning mission. It records mission admission, runs the planner, partitions the accepted graph into execution units, and preserves completion modes including ordinary synthesis, output forwarding, and output assembly.
- Lines 379–485 check open lifecycle state, reserve whole-unit task slots, capture immutable pre-unit results, admit tasks, submit concurrent members or execute serialized members, join outcomes, and fold them in accepted task order. Each task still costs one step regardless of parent dispatch mechanism; output ownership decides the final synthesis reservation.
- `admitAssignments` at line 606 requires current `PENDING` task state and ready dependencies, stores `IN_PROGRESS`, and records an authoritative admission transition. `executeAssignedTask` at line 644 installs its worker binding and calls `executeOneStep`; exceptions become normal assigned failures. `foldOutcomes` at line 739 is parent-owned and deterministic.
- `executeOneStep` at line 839 opens a `STEP_EXECUTION` frame, records `STEP_STARTED`, builds assigned/final prompts, calls the model at line 903, parses the returned action, records proposed/validated/rejected events, and invokes the tool through `executeToolAction` at line 1096. It closes the same frame with completed/failed/aborted state. Invalid model actions have one correction; child invocation exceptions leave the action loop and do not trigger model repair.
- `src/main/java/ai/loomspan/internal/runtime/step/StepActionValidator.java:64` requires exact assigned task ID, `CALL_TOOL`, current `IN_PROGRESS`, visible tool, and exact planned capability. Its argument validator rejects bound-path overrides and validates the projected argument contract with `SkillInputValidator`. Final response is only accepted after every task completes.

### Receiving contract, projection, and nested presence

- `src/main/java/ai/loomspan/internal/runtime/tool/BoundCapability.java:29` captures actual `CapabilityMetadata`, immutable parent binding declarations, and one `ChildInputBindingProjection`. Its `invokeAssigned` passes immutable model arguments and accepted source results to the existing invocation closure.
- `src/main/java/ai/loomspan/internal/runtime/input/ChildInputBindingProjection.java:14` constructs the effective argument contract. Whole bound properties disappear from projected properties and required fields. Nested bindings recurse; a binding-created ancestor becomes required only when projected required siblings remain. Serialized model schemas explicitly forbid bound destinations, and `validateModelArguments` rejects even equal/null bound values and nonobject ancestors.
- `src/main/java/ai/loomspan/internal/runtime/input/SkillInputContract.java:19` distinguishes `GENERIC`, `JAVA_REFLECTED`, and `YAML_EXPLICIT`. Generic input is an open object. `allowsEmptyInput()` at line 32 checks only `required().isEmpty()`, so it does not establish that all possible model contributions are absent.
- `SkillInputSchemaNode#allowsAdditionalProperties` treats omitted/true closure and typed extensions as open. A closed explicit empty object differs from absent YAML/blank Java schema, which resolves to generic open input.
- `src/main/java/ai/loomspan/internal/runtime/input/SkillInputContractResolver.java:117` reads type, properties, required, extensions, items, string enums, description/format, and Loomspan attachment/reference metadata. It does not retain arbitrary composition, defaults, constants, conditions, property-count constraints, nullable type arrays, or other unknown keywords. YAML uses `fromManifest` at line 79 and the manifest's supported shape instead of arbitrary JSON Schema.
- `SkillInputValidator#validateObject` at line 134 preserves supplied optional properties, checks required presence/null, validates declared properties, and rejects closed unknown fields. Exact binding paths suppress ordinary scalar/date coercion. Arrays and values are copied without using model-generated prose as a binding source.
- Existing `ChildInputBindingProjectionTest#preservesUnboundRequiredSiblingUnderOriginallyOptionalAncestor` proves required siblings under a binding-created optional ancestor remain required. `#allBoundAncestorMayBeOmittedAndGenericFieldsAreStructurallyForbidden` proves empty arguments can be valid even while an open contract still permits other inputs. Thus empty validity and contribution absence are distinct facts.

### Invocation-local assembly and failure evidence

- `src/main/java/ai/loomspan/internal/runtime/tool/DefaultCapabilityInvoker.java:61` binds each visible capability using this parent's exact constraints and captures the owning mission only when bindings exist.
- `invoke` at line 81 records a tool invocation, checks the current source owner, finds exactly one planned producer, requires its accepted completed result, and calls `ChildInputBindingAssembler` at line 120 with current owning input and the worker's accepted prior-unit results.
- `src/main/java/ai/loomspan/internal/runtime/input/ChildInputBindingAssembler.java:15` checks all destinations before insertion, selects exact named object paths, distinguishes missing keys from null, creates object ancestors for nested insertion, and detaches copied values. Result lookup rejects zero/multiple producers. It records destination, source kind/path, owning mission frame, and exact producer task/skill without duplicating the selected payload in provenance.
- Receiving validation occurs at line 123 with `validateExact`. Source and contract failures record attributable `TOOL_CALL_FAILED` and failure evidence before opening a child tool frame. No source failure causes a model repair loop.
- Lines 143–173 open the normal tool frame with full effective arguments, linked task ID and provenance, log tool start, and call the normal execution router. Completion/failure and metrics remain in this path.

### Lifecycle, security, generation, and approvals

- `src/main/java/ai/loomspan/internal/core/CapabilityExecutionRouter.java:53` rechecks access, complete receiving input, current session binding, and generation ownership before `ExecutionCoordinator.execute`. No underlying business method or REST handler is called here directly.
- `src/main/java/ai/loomspan/internal/core/ExecutionCoordinator.java:84` requires the captured generation, creates the child mission, and at line 153 rechecks access. Java/REST execute through `MissionWorkExecutor` with scoped trusted caller authentication. Model children create their own actual model interaction and execute their own direct/planning engine normally.
- `src/main/java/ai/loomspan/internal/runtime/MissionWorkExecutor.java:36` requires lifecycle admission for new child work, records mission quota, and enforces timed/interrupted/cancelled execution and cutoff cleanup. `MissionLifecycle#requireOpenForNewWork` and ancestry-aware writable permissions fence new/late work.
- `DefaultCapabilityInvoker` records tool quota independently from provider quota. Scheduler admission and child entry remain separate boundaries. `ProviderAttemptCallAdvisor` also checks thread interruption immediately before sends; removing sends makes this model-specific incidental check absent, while child lifecycle/access controls remain present.
- Authorization documentation and executable Java security tests establish JSR-250, YAML roles, local allowlists, trusted caller scope, and live re-evaluation with generation-pinned policy. Repository search did not find a separate built-in approval declaration or approval workflow in production source. Application handler/proxy/business approval checks remain behind the ordinary child execution boundary; the accepted worker contract itself is not an approval gate.

### Model hooks and accounting

- `src/main/java/ai/loomspan/internal/chat/ProviderAttemptCallAdvisor.java:48` owns each real provider send, interruption check, provider-attempt reservation, sent/received/failure trace, usage, metrics, and unchanged-request provider retries. An eliminated parent interaction has no actual send or advisor call to account.
- `src/main/java/ai/loomspan/internal/springai/SpringAiChatClientAssembler.java:111` filters final output-schema, evidence, and linter advisors out of step-execution ChatClients. Final synthesis validates through the step engine; directly invoked model children use their own appropriate advisors/engine.
- `DefaultSkillAdvisorResolver` and Spring AI assembler are internal machinery; no supported arbitrary advisor or bean-replacement SPI was identified. `RestSkillHandler` remains the sole supported Java SPI.
- `src/main/java/ai/loomspan/internal/runtime/usage/DefaultSessionUsageService.java:72` reserves actual physical attempts; line 56 accounts returned model response usage; line 100 independently accounts tool invocations. Actual provider/model quota demand can decrease while task, tool, mission, child depth and authorization requirements remain.

### Observations and Console consumers

- Canonical `TraceRecordType` already contains step start/action validated/proposed/rejected/completed/failed and tool lifecycle events. Normal step frames carry trusted `planId`, assigned task, step, group, and effective concurrency via `trustedStepIdentity`.
- `ExecutionJournalProjector#toJournalEntry` projects skill frame lifecycle, tool start/result/failure, plans, validation, results, and step failure. Ordinary proposed/validated actions are not public journal events. `SkillTemplate` observer delivery uses this derived journal; its existing field-name redaction exception remains confined there.
- `LiveActivityProjector` selects a bounded existing step/model/tool vocabulary and derives current activity. A step without a model child can retain ordinary step/tool lifecycle, but explicit dispatch provenance must come from the runtime's own recorded decision, not missing-frame inference.
- `loomspan-console/internal/traceanalysis/enums.go:3` mirrors Java record enums and rejects unknown types; `parser.go:199` performs that check. `frames.go:216` consumes assignment metadata. `plans.go` and plan tests consume authoritative admission/join facts independently of model frames. `query_records.go` validates record filters; MCP schema snapshots expose the closed vocabulary.
- Console browser timeline and records present canonical frames and records. Existing binding raw/effective-input separation is exercised by `DeclaredChildInputBindingsTraceTest`. If planning selects a new canonical record type, Java/Go enum and schema/fixture consumers are a coordinated current-release surface; if using existing records with metadata, the exact projection/display needed for origin visibility remains to be established by tests.
- Console compatibility marker is framework-owned and exact-version; both development builds allow best-effort complete validation. Historical evidence is not to be rewritten. No persisted application-domain format or acquisition/authentication protocol change is inherent in skipping a provider call.

## Surface inventory and classification

| Category | Current evidence and relevant surfaces |
| --- | --- |
| Application API | Closed `ai.loomspan.api` allowlist in `LoomspanPublicSurfaceArchitectureTest`; `SkillTemplate`, admission, immutable observer values. Observer timing and truthful child/tool lifecycle are deliberate contracts. No new signature is required by the ticket. |
| Supported SPI | `RestSkillHandler` only. Ordinary handler authorization/approval logic stays on the invocation path. |
| Configuration and manifest contracts | Existing `input_schema`, parent `input_bindings`, explicit planning, task limits/concurrency, child/output ownership; no new author mode is requested. Automatic eligible parent dispatch intentionally changes request-count behavior. |
| Persisted or serialized contracts | No business persistence change identified; portable saved canonical traces require the same Console marker. No historical migration is authorized. |
| Ephemeral diagnostic formats | Canonical step/tool traces, live activities, derived journal/public diagnostic event details, Console records and frames, provider usage. Actual call-count reduction and dispatch source require honest current-version evidence. |
| Internal or accidentally exposed implementation | Projection, schema nodes/resolver, bound capability/invoker, step engine/validator, coordinator, Spring advisors, internal constructors/interfaces and Spring beans. Public modifiers are technical visibility; architecture test deliberately inventories these without making them consumer API. |

## Verification map

Existing offline anchors include `ChildInputBindingProjectionTest`, `ChildInputBindingAssemblerTest`, `StepActionValidatorTest`, `StepLoopMissionExecutionEngineTest`, `DeclaredChildInputBindingsTraceTest`, `DeclaredChildInputBindingsIntegrationTest`, `BindingIsolationIntegrationTest`, `ConcurrentGroupedExecutionIntegrationTest`, Java security/authentication integration tests, session usage and lifecycle tests, and `ConsoleTraceFixtureCorpusTest`.

`DeclaredChildInputBindingsIntegrationTest#deliversLargeBoundEvidenceFromEmptyModelArguments` uses a local `MockWebServer` through the supported facade across nine model/Java/REST combinations. The producer's entire declared input is bound; consumer schemas retain optional reasoning or generic Java-map extensions. The test presently asserts two parent dispatch actions and preserves child output correction, large integers/decimals, exact arrays and optional reasoning. Its exact action-count expectation is affected by the ticket. `#completeAuthoringExampleLoadsAsOneValidatedGeneration` extracts every YAML fence in the binding topic and currently requires exactly three complete declarations, so documentation additions need coordinated fixture handling.

Normal root verification is `.\mvnw.cmd test`; focused suites use quoted `"-Dtest=..."` in PowerShell. Required production-type check: `.\mvnw.cmd test "-Dtest=LoomspanPublicSurfaceArchitectureTest"`. Java release is 21; Maven wrapper pins 3.9.11. `mvn.cmd` and `go.exe` are installed; `java` was not resolved by `Get-Command` in this shell, so actual Java runtime selection must be established before testing. Console commands from its AGENTS are `go test ./...`, `go run ./internal/buildtool verify`, and root `ConsoleTraceFixtureCorpusTest`. These are mapped checks, not research-stage passes.

## Documentation evidence and drift

Applied `agent-skills/loomspan-docs/SKILL.md` as the required documentation router after executable inventory; its `1.0.0-beta.8-SNAPSHOT` matches this checkout's pom. Relevant topics are input bindings, planning concurrency, trace/debugging, authorization, execution limits, and Java observation/error values. `README.md` owns the distributed knowledge-base coverage and routing.

Classification: **aligned** for currently investigated binding projection, exact source transfer, assigned-action ownership, planning admission/join, provider accounting, trusted authorization and observer timing. Existing docs describe the current model argument generation path and do not yet describe the requested optimization; that is prospective ticket work, not current-code drift. R6 has authoring impact because eligibility, parent/child distinction, provider-hook absence and traces/call counts change what authors need to understand. No source-backed need for a new YAML mode or public SPI was identified.

## Historical context and related research

The checkout contains this ticket, the feature design lens, and an observation-truncation ticket; no prior PR21/22/23 research/plan artifact is present. The current source/tests, rather than historical PR labels, establish the live binding/output semantics. The ticket embeds historical sibling-suite measurements and caveats; sibling captures were neither needed nor altered. No dollar, timing, reliability or preferred-model savings claim was inferred.

## Open questions for planning

1. How will proof support for unsupported/ambiguous raw schema be represented beside the same contract authority used by validation, given that resolver nodes currently discard unknown keywords?
2. What nested proof subset is selected? Current assembler creates ancestors and projection accepts omitted empty ancestors; whole bound subtrees and model-created optional ancestor presence require distinct reasoning. The ticket permits conservative fallback rather than a general solver.
3. Which existing step event and metadata record origin plus bounded reason codes, and which current Console/public diagnostic presentation needs updating so origin is visible without inferring missing model evidence? A new canonical enum would have coordinated consumers.
4. Which focused end-to-end fixture exercises application approval enforcement and invocation-boundary cancellation/access changes after admission? No built-in approval mechanism was found; tests should use existing application execution boundaries.

These are bounded implementation/test design choices resolvable during planning with the approved ticket. No developer decision blocks research completion.
