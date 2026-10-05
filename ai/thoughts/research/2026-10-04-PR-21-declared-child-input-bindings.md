---
date: 2026-10-04T23:38:58-07:00
researcher: Codex
model: Unknown
git_commit: cb37a28e0acf892744c2bbafd6c7cb657be757b1
branch: main
repository: loomspan-framework
topic: "PR 21 declared child input bindings"
tags: [research, codebase, manifests, input-contracts, planning, execution-evidence]
status: complete
last_updated: 2026-10-04
last_updated_by: Codex
execution_profile: full
---

# Research: PR 21 declared child input bindings

**Date:** 2026-10-04 23:38:58 PDT
**Researcher:** Codex
**Git commit:** cb37a28e0acf892744c2bbafd6c7cb657be757b1
**Branch:** main
**Repository:** loomspan-framework

## Research question and execution context

Map current manifest, model argument, child input, accepted-result, dependency,
authorization and evidence behavior for implementing
`ai/thoughts/tickets/loomspan-pr-21-declared-child-input-bindings.md` through the
confirmed Full 5-Step Pipeline (`full`). This document describes existing code;
binding syntax and implementation architecture are decisions for planning.

The developer confirmed the full route and additionally said: **"destructive
changes welcome and no compatibility shims."** This supersedes the ticket's
general no-intentional-break statement where a scoped implementation change is
needed. It permits coherent internal removal and atomic updates without shims;
it does not expand the supported API, result access, or ticket scope. Preserve
ordinary unbound behavior, authorization and isolation required by the ticket.

Initial checkout attribution: only the PR 21 ticket was untracked; there were no
staged or unstaged implementation changes. Research writes only this artifact.
No sibling suite files were changed or used as executable framework evidence.
Frozen suite baselines, captures and unrelated work remain out of scope. Paid
evaluations, replay refresh, release, deployment, publishing, commit and push
are not part of this invocation.

## Research checklist

- [x] Read ticket, repository guidance and shared pipeline protocols.
- [x] Inventory declarations and completed-generation validation.
- [x] Trace model-visible contracts and correction validation.
- [x] Trace receiving validation and model/Java/REST result boundaries.
- [x] Trace accepted direct-task retention and joined execution units.
- [x] Inventory authorization, captured generation and evidence mechanisms.
- [x] Consult version-aligned authoring router and classify documentation.
- [x] Capture metadata and write self-contained research.

## Summary

The current framework accepts structured child declarations containing only
`name`, `min_tasks`, `max_tasks`, and `required`; there is no declared input binding
mechanism. Model action validation, assigned-worker guidance and planning tool
descriptions currently use each child's complete receiving contract. The model
supplies every business argument.

The runtime already has a parent-mission-local accepted plan and complete
successful direct-task results keyed by exact task ID. Results become visible
after coordinator outcome folding, with entire grouped execution units joined
before later units start. PR 20 forwards a selected complete retained String
after every accepted task succeeds. These existing authorities distinguish
accepted results from model correction candidates and isolate nested missions.

The receiving router performs access checks, contract validation and captured
generation ownership checks before child dispatch. Input validation performs
some coercions and normalization; its immutable copies are not universally
recursive. Those current behaviors are relevant to the ticket's exact transfer
and mutation isolation requirements.

## Detailed findings

### 1. Declaration and complete generation

- `src/main/java/ai/loomspan/internal/skill/YamlSkillManifest.java:174` defines
  `AllowedSkillManifest`. It carries four fields and no mapping or dependency
  metadata. `AllowedSkillConstraint.java:8` is an immutable normalized internal
  record with the same task-count meaning.
- `YamlSkillCatalog.java:57` owns the four-field entry allowlist.
  `#validateRawAllowedSkills` at line 493 rejects unknown fields, null/scalar
  entries, malformed exact names, duplicate children, invalid bounds and explicit
  null constraints. Any task-count declaration requires explicit planning mode.
  Binding YAML would currently fail as an unknown entry field.
- `YamlSkillDefinition.java:20` retains normalized constraints and exposes
  `#allowedSkills` at line 78. Existing calls use the constraint's exact name;
  constraints do not define source-result selection.
- `SkillGenerationManager.java:282` checks exact child references in the complete
  set of fixed Java plus parsed YAML/REST skills. Input contracts are resolved
  before catalog construction. `#resolveOutputSchema` at line 298 derives
  forwarding metadata and detects forwarding cycles; this is specifically an
  `output_from` graph, not a child-data dependency graph.
- `SkillGenerationManager.java:352` creates child metadata with complete resolved
  input contracts. Java inputs come from reflected signatures; YAML/REST inputs
  come from authored schemas or a generic object. Java/REST without authored
  output schemas have no fabricated output contract.
- `SkillGeneration.java:16` captures immutable exact maps of metadata and
  definitions. `#owns` uses capability object identity, not a matching name.
  A binding-specific model contract is currently absent from both generations
  and invocation-local tools.

### 2. Model-facing argument contracts and corrections

- `DefaultCapabilityInvoker.java:57` binds each child as a `BoundCapability` using
  the original metadata. `BoundCapability.java:22` exposes original input schema
  and metadata. `#invoke` copies only the outer argument map.
- `DefaultPlanningService.java:275` validates planning attempts: visible required
  children are checked before model calls, raw structure is checked before plan
  conversion, then task-count and evidence coverage checks run. All validators
  share one total planning correction. No valid plan is stored if the corrected
  attempt remains invalid.
- `DefaultPlanningService.java:721` renders callback input schemas into planning
  tool descriptions. The assigned action renderer uses
  `StepPromptBuilder.java:232` to get `tool.metadata().inputContract()` and feeds
  it to the shared `SkillInputPromptRenderer`, in both compact and verbose modes.
  Verbose mode is also used after action rejection.
- `StepActionValidator.java:133` checks visible exact tool names, the assigned
  task's capability, and input arguments. At line 167 it validates arguments
  against the original complete contract and rejects unresolved model placeholder
  values. Generic contracts skip this action-level shape validation.
- `StepLoopMissionExecutionEngine.java:893` invokes this validator before a tool
  call. Rejected candidate text is replayed in the corrective request, and the
  same assignment and contract remain authoritative. A corrected model response
  is not a completed child result.
- `StepLoopMissionExecutionEngine.java:1044` passes raw action arguments to
  `BoundCapability.invoke`. There is presently no assembly step between action
  validation and child receiving validation.

Focused existing evidence: `StepLoopMissionExecutionEngineTest`
`#retriesWhenConcreteToolSchemaRequiresMissingArguments` first rejects `{}` and
then invokes with the required field; it checks actual correction messages.
`#preservesScopedGuidanceAndEvidenceThroughObjectBoundaryCorrection` and
`#sendsAuthoredDescriptionsInCompactVerboseAndCorrectiveModelRequests` protect
nested closed/open boundaries and consistent corrective guidance.

### 3. Receiving input, value fidelity and invocation paths

- `CapabilityExecutionRouter.java:49` validates the complete input contract
  after access checks, then passes normalized inputs to the coordinator. The
  router verifies current-session binding and generation ownership at lines
  62–70. It serves Java, REST and model skills.
- `SkillInputContractResolver.java:32` resolves reflected contracts and at line
  40 resolves YAML inputs. `SkillInputSchemaNode.java:9` is the existing recursive
  internal representation: object properties/required/openness, typed additional
  properties, items, enum, format, attachment and runtime-reference flags. A
  separate projected model argument contract is not represented today.
- `SkillInputValidator.java:19` validates and normalizes. Typed object requiredness
  at line 140 treats absent or null required fields as missing. Unconstrained
  values accept explicit null and recursively copy JSON maps/lists. Thus explicit
  null is permissible only where the unchanged receiving contract permits it.
- `SkillInputValidator.java:196` preserves integer numeric objects, including
  `BigInteger`, but coerces numeric strings into int/long. `#validateNumber`
  preserves Number values and coerces string values through Double parsing.
  Boolean strings and dates also have existing normalization. The ticket's bound
  source fidelity cannot be established by shape validity alone.
- Generic input validation makes an outer immutable map. Open undeclared object
  values are copied as-is at line 164. Array values without item contracts are
  only outer-copied. These paths do not establish deep isolation for bound data.
- `ExecutionCoordinator` owns each child mission and common dispatch. It resolves
  runtime file references and chooses step execution only for explicit planning
  manifests. `DefaultMissionInputMaterializer.java:61` renders canonical input
  and attachment descriptors. Its rendered `traceSafeInput` is a model/diagnostic
  representation, while the engine still receives the original validated input;
  these are distinct source boundaries.
- `SkillMethodBeanPostProcessor.java:504` invokes the managed Java method and
  serializes its return with Jackson at line 505. A Java String is JSON-quoted;
  object returns are serialized object text. Java typed argument conversion is
  later done at line 634.
- `SkillGenerationManager.java:543` invokes the sole public REST handler with a
  `RestSkillInvocation` carrying generation identity and arguments. REST returns
  non-null text unchanged, including application-authored JSON/envelopes.
- Model final responses have existing schema/evidence/linter checks before
  successful return. Step-loop final response serialization converts textual
  final responses to their text and structured responses to JSON text. Results
  are then retained as complete Strings, rather than typed application objects.

Therefore one decode of a retained serialized result at the result boundary is
different from recursively parsing strings inside its decoded value. Existing
forwarding preserves the raw String with neither operation. No current result
reference or schema proves the content of unspecified Java/REST outputs.

### 4. Dependencies, joined units and accepted results

- `PlanTask.java:10` carries `dependsOn`, exact capability name and parallel group.
  Its readiness checks require named dependencies to be COMPLETED.
- `PlanStructureValidator.java:27` validates raw plan topology. It requires every
  dependency to refer to an earlier execution unit, rejects unknown/self/forward
  references, and prohibits same-group edges. A group is one consecutive run,
  contains at least two members, and uses an exact grammar. `PlanStructureValidatorTest`
  protects earlier-unit acceptance, malformed references and cycles through the
  ordering rule. Dependencies currently originate entirely in model-generated
  plans; declarations do not require particular causal edges.
- `ExecutionUnit.java:18` partitions accepted list order into singleton and
  consecutive grouped units. `StepLoopMissionExecutionEngine.java:362` fixes that
  partition, preflights unit membership and completed earlier dependencies,
  admits complete groups atomically, and forks isolated worker branches.
- At line 407 every member receives the same pre-unit complete-results snapshot.
  Enabled groups execute concurrently; disabled groups serialize but retain the
  same snapshot. Results from another member in that unit are unavailable to
  fellow members regardless of physical completion time.
- `StepLoopMissionExecutionEngine.java:730` folds successful worker outcomes only
  at the coordinator join boundary. It marks the task complete, records successful
  direct skill credit and stores the exact returned String. Failures contribute
  no successful retained result. Later units begin only after the full successful
  join. Worker writes are fenced by existing mission lifecycle/cutoff handling.
- `MissionContext.java:112` returns immutable snapshots of complete retained
  results. `#recordCompletedTaskResult` at line 124 uses put-if-absent task IDs;
  entries carry task ID, skill name and non-null String. Nested missions own
  separate contexts and only export their final return to their parent. This is
  neither a session-global name index nor a reference store.
- PR 20 forwarding at `StepLoopMissionExecutionEngine.java:349` selects exactly
  one accepted task, and at line 488 checks unchanged accepted plan and complete
  successful work before returning its exact retained result. It records an
  authoritative forwarding event and performs no parent final model call or
  parent duplicate output validation.

Focused existing tests: `StepLoopMissionExecutionEngineTest`
`#reverseCompletionFoldsParentStateInTaskOrderAndPublishesOnce` checks that a
physically completed sibling is not yet in parent state and that final evidence
and joined transitions follow accepted order. `#groupMembersUseOnlyPreUnitEvidenceInEitherConcurrencyMode`
and `#followingUnitWaitsForEveryConcurrentGroupMember` cover snapshot/join behavior.
`DesignatedChildResultIntegrationTest#forwardsModelJavaAndRestResultsThroughSkillTemplate`,
`#parallelRootsForwardOnlyTheirOwnTaskResults`, and
`#runningForwardingTreeKeepsCapturedSchemaAndTargetAfterReload` are existing offline
public-path evidence for producer boundaries and isolation.

### 5. Authorization, usage, generations and canonical evidence

- Local visibility is filtered from declarations by `ToolSurfaceService` and
  its visibility resolver; runtime `AccessGuard` is independently enforced by
  router/coordinator. Caller authentication is propagated through scoped runtime
  binding, rather than input identifiers.
- `DefaultCapabilityInvoker.java:85` records tool-call usage and opens an
  invocation frame whose parameters include `arguments` and linked task ID.
  It logs call/result/failure evidence through existing state services. It
  delegates to the router without changing the capability generation or identity.
- Action raw model text is retained by model interaction/step trace mechanisms;
  current tool-frame arguments are the actual arguments passed to the router.
  Because those are currently identical, there is no binding provenance record
  or separately named assembled input today.
- Existing canonical traces support generic parameters, metadata and payloads.
  Content stays intact; only existing explicit size/resource policies apply.
  `ExecutionJournalProjector` remains a distinct derived journal with the
  repository's specifically supported field-name redaction exception.
- Console consumes canonical NDJSON and derived indexes. This research establishes
  no need to alter REST/SSE/acquisition/problem envelopes, but planning must assess
  actual chosen trace fields or new record types against Java writer/Go reader
  contracts and fixtures. Additional canonical fields and semantic record types
  are different compatibility questions; the final design determines impact.

## Surface inventory and classification

| Category from design lens | Existing evidence and ticket relationship |
| --- | --- |
| Application API | Closed `ai.loomspan.api` allowlist in `LoomspanPublicSurfaceArchitectureTest`; existing facade, validation/reload results and descriptors are supported. Ticket calls for no new consumer-facing Java type. |
| Supported SPI | `RestSkillHandler` is the sole SPI; model/Java/REST binding support must operate through current dispatch rather than creating a replacement hook. |
| Configuration and manifest contracts | Structured allowed-child entries, complete input schemas, planning-mode applicability, counts, grouping and `output_from` are documented and tested. New opt-in entry syntax is author-facing impact. |
| Persisted or serialized contracts | Accepted results are runtime Strings; no cross-version binding store exists. Portable canonical traces require exact Console compatibility marker as specified by the design lens. |
| Ephemeral diagnostic formats | Trace frame parameters, model attempts, task events, plan transitions, derived journal and Console projections explain current runs. Raw/effective input provenance is currently absent. |
| Internal or accidentally exposed implementation | Manifest DTOs, constraints, BoundCapability, input nodes/validators, mission context, plan, dependency validators, runtime services, constructors and Spring wiring are internal despite public modifiers. In-repository tests are consumers, not proof of an external extension promise. |

Spring configuration wiring exposes implementation services; no supported bean
replacement contract is established. No external usage was assumed from public
modifiers or `@ConditionalOnMissingBean`. User-visible configuration keys and
manifest semantics remain separately assessed. Existing tests/fixtures/docs
must track atomic internal edits; developer explicitly requires no shims.

## Documentation alignment and authoring impact

Consulted repository-local `agent-skills/loomspan-docs/SKILL.md` as the mandatory
documentation router, after executable investigation. Its marker and `pom.xml`
both identify `1.0.0-beta.8-SNAPSHOT`; source and bundled skill are in this same
checkout. Read the authoring index, mental model, source-verification protocol,
input contracts, planning task constraints, planning concurrency and output
contracts. The framework maintenance pipeline owns this work; the skill is a
supplementary router, not executable authority or an approval gate.

**Drift classification: aligned for the scoped baseline.** Documentation currently
states explicit model-authored child inputs, no parent-input/dependency merging,
complete mission-local direct results, unchanged String forwarding, strict
structured child declarations and earlier-unit joins. These agree with source
and focused tests. Binding support is absent from both executable code and
guidance. The task intentionally changes those author-facing semantics for opted-in
declarations, so documentation impact is **Affected**.

Routing/coverage owners are `references/skill-authoring/README.md`,
`planning-task-constraints.md`, `planning-concurrency.md`, `input-contracts.md`
and `output-contracts.md`; the final syntax needs a complete example and a routed
binding topic or equivalent focused coverage. Current unqualified sentences
about no merging/explicit model arguments are baseline claims to revisit when
the feature exists. `ref://` remains a session file/attachment feature; ordinary
JSON Schema `$ref` does not retrieve completed tasks.

## Historical context and related research

The tracked `ai/thoughts/` tree contains the design lens and observation-truncation
ticket, but no prior research/plans for PR 21 or PR 20. Current PR 20 source,
integration tests and output-contract guide provide the checked-out foundation.
The ticket itself carries sibling-suite motivation without requiring the frozen
external artifacts to be modified or imported as runtime fixtures.

## Open questions for planning

These are ordinary internal design choices left by the ticket, not requests for
additional developer authorization:

1. Pick one unambiguous object-field path syntax and its precise root/source
   selection rules; detect duplicate raw YAML keys before map collapse and
   ancestor/descendant destination overlap.
2. Define declaration normalization and complete-generation checks, including
   child producer name/cardinality/dependency cycles and receiving destination
   shape when contracts are known. Do not infer unknown producer output shapes.
3. Choose where a separate model argument contract is stored/published and how
   recursively bound siblings affect required ancestor containers. Open objects
   need explicit override prohibition even after bound properties are omitted.
4. Define exact result decoding once at the accepted return boundary, including
   structured JSON, plain text, JSON-quoted Java Strings, null and malformed text,
   without guessing or recursively parsing nested source strings.
5. Specify how receiving validation honors exact bound source values despite
   current coercion/date normalization while retaining unbound behavior. Include
   lossless numeric handling and deep copy/isolation for generic/open branches.
6. Choose enforcement/feedback for declaration dependencies using the current
   ordered-unit model. Producers cannot share a unit with a consumer even under
   concurrency:false. One accepted successful producer task must remain unique.
7. Choose trace provenance fields and verify whether they require coordinated
   Java/Go fixture or compatibility-marker changes. Reuse raw model evidence and
   existing actual invocation inputs instead of creating duplicate result stores.

## Verification inventory

Research was read-only source/test/fixture inspection; no test command was run
and no provider was called. Metadata script actually run:
`bash ai/scripts/spec_metadata.sh`. Maven is available as
`c:\hamdev\maven\bin\mvn.cmd`; repository also has `mvnw.cmd`, targeting Java 21
and Surefire 3.5.2. Later stages must select deterministic offline checks after
design; `LoomspanPublicSurfaceArchitectureTest` is explicitly mandatory whenever
production types change. Existing relevant suites include YAML catalog/generation,
input contract/validator/renderer, planning structure/count/service, step action/
prompt/engine, grouped integration, planner evidence and designated result
integration, router, authorization, usage and canonical trace/journal contracts.
