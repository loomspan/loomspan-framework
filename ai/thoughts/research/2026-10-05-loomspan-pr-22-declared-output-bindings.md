---
date: 2026-10-05T11:17:07-07:00
researcher: Codex
model: GPT-6
git_commit: 1b5c1a63bf0beb538119be05a8b5b361c96f8e72
branch: main
repository: loomspan-framework
topic: "PR22 declared output bindings: existing implementation map"
tags: [research, codebase, output-bindings, input-bindings, planning, output-schema]
status: complete
last_updated: 2026-10-05
last_updated_by: Codex
---

# Research: PR22 declared output bindings

## Research question and execution context

Map the current Framework mechanisms relevant to implementing `ai/thoughts/tickets/loomspan-pr-22-declared-output-bindings.md`. This is Step 1 of the approved Full 5-Step Pipeline; it describes existing behavior and leaves implementation choices to planning.

Developer decision, received after route approval: **"we are in development so destructive changes are welcome and no compatibility shims"**. This overrides the ticket's general compatibility-preservation wording and any default shim guidance. Downstream stages must use atomic in-repository updates and no compatibility shims. It does not cancel the ticket's explicit preservation of existing `output_from` behavior, ordinary unbound execution, authorization, source isolation, or diagnostic fidelity. Destructive implementation changes within the ticket scope are authorized; unrelated changes and paid evaluations remain outside scope.

Initial checkout: `main` at `1b5c1a63bf0beb538119be05a8b5b361c96f8e72`, only the untracked PR22 ticket. No existing implementation diff. Metadata was gathered using `bash ai/scripts/spec_metadata.sh`; no thoughts-system researcher identity was emitted, so researcher is Codex. The same-checkout `loomspan-docs` skill identifies `1.0.0-beta.8-SNAPSHOT` and was consulted as the skill-authoring documentation router after source/test inventory.

## Summary

PR21 already establishes destination-keyed descriptors, object-only pointers, separate model and complete receiving contracts, immutable invocation sources, one-time accepted-result decoding, detached insertion, exact bound-input validation, and provenance. Whole-child `output_from` already selects an exact accepted task and returns retained text after all accepted work completes. There is no top-level `output_bindings` field or output assembly path today.

Model-backed output has two distinct executable paths: ordinary execution calls a model through schema/evidence/linter advisors; explicit planning executes assigned child tasks and validates final synthesis inside the step-loop engine. Both paths expose the complete authored output schema today. PR22 affects both ownership boundaries, complete-generation declaration checks, accepted-plan validation, diagnostics, and the authoring guide.

## Investigation checklist

- [x] Manifest parsing and complete-generation validation.
- [x] PR21 pointers, projection, source decoding and assembly.
- [x] Ordinary and planning output execution/correction.
- [x] Invocation ownership, joining, cancellation and generation capture.
- [x] Diagnostic consumers and public surface classification.
- [x] Focused tests, build tooling and same-revision documentation.

## Detailed findings

### Manifest and configuration generation

`src/main/java/ai/loomspan/internal/skill/YamlSkillManifest.java:36` includes `OUTPUT_FROM` in the explicitly tracked field enum; `:87` holds its selector. `AllowedSkillManifest` holds `input_bindings` at `:186`, with a reusable input-binding descriptor carrying `from`, `path`, and producer `skill`. No output-binding declaration exists. The manifest tracks declaration presence separately from values so explicit null is meaningful in validation.

`YamlSkillCatalog.java:420` runs raw REST, concurrency, forwarding and allowed-child validation before normalized declaration use. Its raw input-binding validation at `:574` checks non-null object shape, descriptor fields, exact valid skill names, source kinds, pointer escaping, and overlapping destinations. The strict skill-YAML codec in `internal/serialization/LoomspanJacksonCodecs.java:36` rejects duplicate YAML keys and unknown mapped fields. `YamlSkillDefinition.java:93` returns defensive output-schema copies; manifest copying retains declaration-presence metadata. `AllowedSkillConstraint` compiles input descriptors into runtime `ChildInputBinding` objects.

Forwarding raw validation (`YamlSkillCatalog.java:448`) requires explicit planning, a direct child with `required: true` and `max_tasks: 1`, and rejects parent output schema/retry/linter declarations. Complete-set forwarding resolution (`SkillGenerationManager.java:309`) checks target resolution/cycles and derives nullable output metadata through forwarding chains. Ordinary authored model schemas provide shape metadata; Java/REST and undeclared shapes remain unspecified. Metadata removes producer-local evidence annotations and is not a parent validator.

Generation-level input-binding validation (`SkillGenerationManager.java:294`) has parent input and child receiving contracts. `ChildInputBindingDeclarations.java:14` checks parent paths, destination paths, overlap, unknown/self/cyclic producers and impossible task counts. It does **not** currently compare selected producer output schemas or source/destination types. Open/unconstrained paths stop static traversal and defer to runtime.

Complete generations carry definitions, contracts, effective metadata and captured model settings. `ExecutionCoordinator.java:154` selects the engine from the captured definition and creates the model interaction in standard or step mode. `SkillGenerationExecutionIntegrationTest`, `BindingIsolationIntegrationTest` and `DesignatedChildResultIntegrationTest#runningForwardingTreeKeepsCapturedSchemaAndTargetAfterReload` establish reload/snapshot behavior. The PR22 declaration checks require information from the same complete generation, including effective producer output metadata where actually declared.

### PR21 value ownership and insertion

`internal/runtime/input/ObjectFieldPath.java:10` parses object-only JSON Pointers. An empty source selects the root; an empty destination is forbidden. Numeric-looking tokens are literal object keys, not array indexes. `/` can select an empty object key, and `~0`/`~1` decode tilde/slash. Runtime selection traverses maps only, so arrays can be transferred whole but cannot be traversed by index.

`ChildInputBindingProjection.java:14` compiles a projected receiving contract and reserved destination checks. Bound subtrees are removed from model-owned properties and required lists; serialized schemas explicitly forbid their names, including in open objects. Nested projection retains unbound siblings and promotes an originally optional ancestor when insertion makes its unbound required siblings mandatory. `ChildInputBindingProjectionTest#preservesUnboundRequiredSiblingUnderOriginallyOptionalAncestor` and `#allBoundAncestorMayBeOmittedAndGenericFieldsAreStructurallyForbidden` describe this behavior. Model destinations are checked by presence, including null/equal values, and nonobject blocking ancestors are rejected.

`ChildInputBindingAssembler.java:16` takes model arguments, bindings, owning-parent input, pre-unit accepted results, and the parent mission frame ID. It copies containers, rejects overrides before source selection, requires exactly one matching producer, distinguishes map absence from null, inserts detached selected subtrees, and returns immutable arguments plus exact paths and provenance. The assembler itself trusts its supplied results; the caller establishes accepted-task identity/status/ownership.

`DefaultCapabilityInvoker.java:105` verifies the current owner, accepted plan, unique producer task, completed task status, and matching retained task ID/skill before assembly. `SkillInputValidator.java:30` validates complete receiving arguments while disabling coercion/date rewriting beneath exact paths. Unbound input retains ordinary coercion and date normalization. These are input semantics; `OutputSchemaValidator` already validates output without scalar coercion or date rewriting.

`AcceptedResultDecoder.java:12` decodes once with big integer/decimal handling and trailing-token failure. Valid JSON becomes immutable values, including JSON-quoted Java strings; malformed/plain text stays verbatim text. It does not recursively decode JSON-looking strings. `MissionContext.CompletedTaskResult` (`core/MissionContext.java:146`) stores original accepted text and decoded values separately.

### Source isolation, planning, full-work completion

`MissionContext.java:69` captures an immutable validated input map once. Each owning YAML mission has its own parent link, accepted plan, successful-direct-skill set and task-keyed retained results (`:129`). The session/physical-branch/mission `ExecutionBinding` is explicit; results are not shared in a global map by skill name. `ExecutionCoordinator.java:111` captures mission input before invocation and enforces access at `:148`.

`PlanInputBindingDependencyValidator.java:13` requires exactly one producer when an input-bound consumer appears in a plan, an explicit `dependsOn` edge and an earlier execution unit. Independent producers can share a group. These input-specific dependency edges are for a consuming child's work; final output assembly has no such child consumer today.

`DefaultPlanningService.java:310` combines structure, count, evidence and input-binding violations within the existing bounded planning correction. Validation occurs again before plan acceptance (`:362`), and required-child visibility is checked before requesting a plan (`:463`). Its rendered guidance contains producer dependencies and effective projected input contracts. Forwarding adds designated-child guidance (`:511`).

`StepLoopMissionExecutionEngine.java:327` begins from an accepted plan, checks input dependencies and retains the forwarding task ID. Execution units are admitted and run with physical branch ownership; parallel branches join (`:449`, `:649`) before ordered outcome folding. Accepted successful results are recorded at `:745`. Failure/cutoff prevents normal final publication and late writes are fenced by mission lifecycle. Final synthesis begins only after unit execution (`:487`).

Forwarding checks unchanged plan identity, valid status, matching capability/task set, every task completed, every retained task result and selected task identity (`:490`). It records `RESULT_FORWARDED` and returns original text. Forwarding needs N task slots; ordinary final synthesis reserves another slot (`:366`). This offers existing evidence for all-work completion, selected identity and skipped synthesis, but assembled completion is absent.

### Ordinary output and planning final synthesis

`DefaultMissionExecutionEngine.java:60` owns ordinary execution within `MissionWorkExecutor`. It materializes input, composes the authored prompt, opens a model frame, calls the model and returns its content. It has no direct output assembly or bound-input fast path.

`internal/chat/DefaultSkillAdvisorResolver.java:100` adds `OutputSchemaCallAdvisor` for an authored schema, then evidence and regex-linter advisors when declared. `OutputSchemaCallAdvisor.java:86` augments the initial request with the complete schema; its loop validates each complete candidate, records outcomes, and corrects using the baseline request plus the original rejected candidate. It permits one initial response plus `output_schema_max_retries` corrections. Planning calls bypass these final-response advisors, and `SpringAiChatClientAssembler.java:113` strips schema/evidence/linter advisors from step-mode interactions.

`StepLoopMissionExecutionEngine.java:834` builds each final request through `StepPromptBuilder.buildFinalResponsePrompt` with the complete authored schema. It preserves complete retained result evidence for reasoning. Parsed `FINAL_RESPONSE` actions go through linter, schema and evidence validation (`:1158`). Output validation at `:1241` uses the skill retry budget, and rejected candidates return through the same final-step correction loop. Already accepted task work is outside this loop. `OutputSchemaPromptAugmentor.java:31` is the shared recursive contract renderer used for normal and planning guidance.

`internal/outputschema/OutputSchemaValidator.java:37` parses candidate JSON and recursively validates object/array/scalar/nullable/string-enum rules. Requiredness is independent of nullability. Output object properties are matched case-insensitively, with duplicate case variants rejected; schema declarations likewise reject case-insensitive duplicates. Formats/descriptions are guidance rather than transformations or format checks. Root output schemas are objects; typed nested arrays are rejected by the catalog. The output schema model includes `nullable`, which is not a field in the PR21 input schema model; blindly converting output contracts into input contracts would not establish preservation of output semantics.

### Diagnostics and Console boundary

PR21 tool frames contain assembled arguments plus `inputBindings` provenance (destination, source kind/path, owner frame, exact producer task/skill) without duplicate selected payloads. Original model actions and retained child text remain available. Model correction and schema outcomes have explicit recorded events and current diagnostic size/lifetime controls.

Forwarding is an authoritative event: `core/TraceRecordType.java:16`, `ExecutionStateService.java:44`, and `DefaultExecutionStateService.java:404`. Console knows its enum and projected semantics in `loomspan-console/internal/traceanalysis/enums.go:32`, `record_facts.go:18`, `internal/live/dto.go`, `internal/browserapi/trace_analysis.go`, and `internal/mcpadapter/{trace_contracts,output_schemas}.go`. Tests include `traceanalysis/result_forwarding_test.go`, `browserapi/result_forwarding_test.go` and `mcpadapter/result_forwarding_test.go`. PR22 has no specified trace record representation yet; planning must make the assembly event/provenance shape concrete and identify any affected Java-to-Go enum/DTO/fixture consumers before implementation.

General diagnostic content must stay faithful. Retain the existing derived-journal `ExecutionJournalProjector` field-name redaction exception, without extending it to prompts, canonical traces, arguments or invoke results.

## Contract inventory

| Lens category | Current exposure and evidence | PR22 relevance |
| --- | --- | --- |
| Application API | Closed public `ai.loomspan.api` allowlist in `LoomspanPublicSurfaceArchitectureTest.java:29`; invocation/result/catalog/reload facade | No ticket requirement for a new Java API. Existing facade exercises composed results. |
| Supported SPI | `RestSkillHandler` is the sole supported Java SPI | Direct REST results are sources; no new SPI or inferred REST schema. |
| Configuration and manifest contracts | Authoring docs, strict YAML declarations, schemas, task counts, retries, input/output forwarding | New top-level mapping; projected model ownership and required producer-plan behavior. |
| Persisted or serialized contracts | Same-version portable canonical trace boundary exists; no application result-history contract discovered | A diagnostic encoding choice can affect strict current trace consumers; no cross-version shim authorized. |
| Ephemeral diagnostic formats | Model/tool/plan/schema events, provenance, Console NDJSON readers and projectors | Assembly and skipped synthesis need observable owner facts, original evidence, coherent current consumers. |
| Internal or accidentally exposed implementation | All `ai.loomspan.internal` classes/records/interfaces/constructors and runtime beans; auto-config integration types | Atomic internal redesign is permitted. Technical public modifiers/beans/tests do not imply supported API/SPI. |

Autoconfiguration constructs ordinary and step-loop engines at `LoomspanAutoConfiguration.java:438` and `:453`. Its signatures/wiring are implementation integration machinery, not a consumer replacement contract. Public-surface architecture also allowlists technically public internal declarations (`LoomspanPublicSurfaceArchitectureTest.java:344`), so any added/moved production types must keep that executable inventory coherent. The mandated architecture test must run after production changes.

## Documentation assessment and historical context

Consulted same-checkout skill: `agent-skills/loomspan-docs/SKILL.md`, skill-authoring `README.md`, `source-verification.md`, `input-bindings.md`, and `output-contracts.md`. **Drift classification: aligned** for the PR21 binding and current synthesis/forwarding semantics investigated. Output assembly is absent from both current implementation and guide; this is new feature coverage, not existing behavioral drift. Guide routing/coverage currently presents synthesis versus whole-child forwarding, so PR22 requires explicit full/mixed assembly coverage and complete schema examples, with related input-binding and diagnostic routing maintained.

No prior binding/forwarding research or plan artifact was found under `ai/thoughts/research` or `ai/thoughts/plans`; the PR22 ticket and `ai/thoughts/framework-feature-design-lens.md` provide intent/policy. Optional sidecar paid-run evidence was not needed to establish mechanics and was not read or executed. No out-of-scope dead-code removal is established by this focused research.

## Verification/tooling evidence

`pom.xml:45` compiles Java 21; Maven enforces Java 21+ and Maven 3.9+. Surefire 3.5.2 runs JUnit5 tests and integration-named tests in the ordinary test phase. `java` is absent from this PowerShell PATH, but `.\mvnw.cmd -version` works and selects Maven 3.9.11 with JetBrains Java 21.0.2 from `c:\hamdev\jbrsdk21`; use the wrapper.

Focused baseline command:

```powershell
.\mvnw.cmd -q '-Dtest=ChildInputBindingProjectionTest,ChildInputBindingAssemblerTest,AcceptedResultDecoderTest,PlanInputBindingDependencyValidatorTest,OutputSchemaValidatorTest,LoomspanPublicSurfaceArchitectureTest' test
```

The command passed (exit code 0). It checks existing primitives and the public-surface baseline; it does not establish PR22 correctness. Relevant broader deterministic suites are `YamlSkillCatalogTests`, `SkillGenerationManagerTest`, `PlanningServiceTest`, `StepLoopMissionExecutionEngineTest`, `OutputSchemaCallAdvisorTest`, `OutputSchemaPromptAugmentorTest`, `DeclaredChildInputBindingsIntegrationTest`, `BindingIsolationIntegrationTest`, `DesignatedChildResultIntegrationTest`, and `DeclaredChildInputBindingsTraceTest`. Existing integration fixtures use local MockWebServer/provider responses through `SkillTemplate`; no paid model is needed. If the diagnostic shape changes, corresponding Go suites and protocol fixtures belong to the impacted verification inventory.

## Open questions for planning

These are implementation design decisions resolvable from the ticket and current mechanisms, not unresolved product questions or approval requests:

1. Represent reusable binding descriptors/projection/assembly without duplicating PR21 authority, while retaining output nullable, case/property rules and schema metadata.
2. Define full-bound detection for nested/optional/open contracts: optional unbound fields still permit model contribution, and open fields cannot be silently counted as fully owned.
3. Place complete assembly and bounded model-contribution correction across both ordinary/advisor and step-loop paths; bound-source failures must terminate without model reconstruction or child replay.
4. Validate statically knowable source/destination compatibility from the captured complete generation while leaving unknown Java/REST/open shapes for runtime checks.
5. Make output producer requirements unconditional in accepted plans without introducing false ordering among independent producers.
6. Choose assembly/provenance diagnostics and map all affected current Java/Console consumers and fixtures. Keep original model and accepted result evidence intact.

## Related research

None found for this ticket's prerequisites in the repository research/plan directories. Concrete source/test anchors above and the same-revision authoring guide are the downstream evidence map.
