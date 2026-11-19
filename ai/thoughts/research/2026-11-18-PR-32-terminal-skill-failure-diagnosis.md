---
date: 2026-11-18T20:03:17-08:00
researcher: GPT-6.1-sol
git_commit: a65cdaa029c346b3301dd9506c7f1741c11f96cd
branch: main
repository: loomspan-framework
topic: "PR 32 terminal skill contract failure diagnosis"
tags: [research, codebase, public-api, validation, failure-selection]
status: complete
last_updated: 2026-11-18
last_updated_by: GPT-6.1-sol
---

# Research into terminal skill contract failure diagnosis

## Research question and execution context

Map the current Framework paths relevant to `ai/thoughts/tickets/loomspan-pr-32-terminal-skill-failure-diagnosis.md`. The ticket requests an optional supported Java diagnosis identifying the receiving or publishing skill, one of four contract stages, and structured schema issues. It requires agreement with canonical terminal selection and availability through direct invocation and atomic handoff with diagnostics set to `NEVER`.

This is Step 1 of the developer-confirmed Full 5-Step Pipeline. Initial checkout is `main` at `a65cdaa029c346b3301dd9506c7f1741c11f96cd`, matching `origin/main`. The ticket was the only untracked file. No production changes or tests were made during research. Metadata came from `bash ai/scripts/spec_metadata.sh`, which reported 2026-11-18 20:03:17 PST. Git user is `matt-loomspan-ai`.

## Summary

The two supported invocation entry points already share preparation and execution. Pre-session rejection throws `SkillInputValidationException` with structured input issues. Executor transforms attribute their exceptions to a qualified YAML skill in `YamlSkillDefinition`, and executor/ordinary model output validation carries structured issues in an internal output exception. Planning validation retains schema issues only while generating feedback and diagnostic outcomes, then throws a general exception at correction exhaustion.

Canonical failure recording already uses a session-local identity map, transparent-wrapper normalization, and explicit wrapper-to-cause links. The step engine selects group failure by primary cancellation, failure classification, and accepted task order. Root completion can select a primary cancellation failure ID while propagating a different exception. The supported `SkillException` currently has only message and message/cause constructors and carries no diagnosis.

## Public entry points and failure delivery

`SkillTemplate` has four invoke overloads covering Object/Map input with or without an observer, plus two validate overloads. `DefaultSkillTemplate` funnels these into `prepareObject` or `prepareMap`, then `invokePrepared`. Local host names resolve in `default`; the capability used for validation has the full canonical name (`DefaultSkillTemplate.java:118`, `:148`, `:156`). `SkillIdentity` normalizes namespace case and renders `namespace-localName` (`SkillIdentity.java:8`).

`SkillInvocationHandoff` exposes Object and Map forms. `DefaultSkillInvocationHandoff.handoff` calls the same template preparation, captures authentication, authorizes, and admits without creating a session (`DefaultSkillInvocationHandoff.java:33`). Its private handle claims an atomic payload once and calls `template.invokePrepared` with the captured authentication and admitted root. `AdmittedSkillInvocation` exposes generation ID, two invoke forms, and release. The Sidecar handoff named in the ticket is this application invocation handoff. The separate `extensions.v1.SkillExecutorInvocation` is the value delivered to executor methods.

`DefaultSkillTemplate.invokePrepared` builds an action against a `LoomspanSession`, supplies a root completion callback for optional observer mapping, then calls `LoomspanSessionRunner.callWithNewSession` or `callWithAdmittedSession` (`DefaultSkillTemplate.java:257`). After execution, existing `SkillException` and `AccessDeniedException` instances propagate. Other runtime exceptions become `SkillException("Skill '…' execution failed.", ex)` (`DefaultSkillTemplate.java:280`). `CompletionPhaseFailure` is unwrapped so an observer error after success retains identity. JVM `Error` is outside the facade runtime wrapper.

`LoomspanSessionRunner` completes/finalizes the session before invoking the root completion callback. On execution failure it calls completion with the original throwable, suppresses a later callback error on that throwable, then rethrows (`LoomspanSessionRunner.java:330`). This callback has both the session and the execution failure available even though the outer template catch has only the propagated exception. Pre-session rejection has no session or callback.

`SkillException` is a supported extensible RuntimeException with two public constructors. `SkillInputValidationException` extends it and exposes `getIssues()` as an immutable defensive copy. `SkillInputValidationIssue` is the public three-component record `path`, `code`, `message`. Existing constructors accept a null issue list as empty. `ApplicationApiValueTest:181` protects copying and the record shape, and `:202` explicitly checks the two current SkillException constructors.

## Input schema boundaries

### Root preparation

`DefaultSkillTemplate.prepareMap` resolves the capability and validates its input contract before generation admission, authorization, or session creation (`DefaultSkillTemplate.java:156`). The failed validator result is mapped directly to public input issues with no schema reevaluation. The canonical receiving capability name is available there.

Object null rejection happens after capture and before conversion, with an empty issue list (`DefaultSkillTemplate.java:121`). Map null is allowed for generic/empty-input contracts and otherwise throws a public input exception with an empty list (`DefaultSkillTemplate.java:310`). Conversion failures become ordinary facade failures. These overload-specific rules are existing behavior.

### Runtime routing and assembled child input

`CapabilityExecutionRouter.executeInput` authorizes first, then validates ordinary or assembled arguments with `validateExact` (`CapabilityExecutionRouter.java:53`). Rejection maps the current structured issues into the public input exception. The receiving capability is present before its mission executes. Accepted values proceed to the captured-generation ownership checks and coordinator.

`DefaultCapabilityInvoker` resolves declared binding sources and assembles child arguments. When bindings exist it validates assembled arguments against the receiver's complete input contract before opening a tool frame (`DefaultCapabilityInvoker.java:119`). Rejection uses a public input exception with the receiver's canonical name in its contextual message and mapped issues. Its catch records tool failure/outcome and canonical failure, then rethrows. Missing or ambiguous binding sources throw ordinary errors before this validation verdict. The router performs its existing runtime input validation again after assembly acceptance.

### Planner arguments before dispatch

`StepActionValidator.validateAssigned` verifies assigned task identity, status, tool availability and exact capability. `validateRequiredToolArguments` checks binding overrides before validating `BoundCapability.argumentContract()` (`StepActionValidator.java:151`). This contract is the tool argument contract, which may be projected around bound fields. Schema rejection has the matching tool and full structured validation result, but returns only a formatted `StepValidationResult.rejected(reason)` (`StepActionValidator.java:170`). Binding override and unresolved placeholder errors take their own rejection branches.

`StepValidationResult` currently contains only `valid` and nullable `rejectionReason` (`StepValidationResult.java:10`). Each proposed action produces a fresh result. In `StepLoopMissionExecutionEngine` the action correction loop increments the invalid action counter and throws a general IllegalStateException when its budget is exhausted (`StepLoopMissionExecutionEngine.java:1028`). Parsing exhaustion has a separate general exception path. Corrected actions continue to dispatch, and corrected attempts are not terminal failures. The last schema rejection's structured issues are currently used for feedback text, without a retained typed field on the terminal action failure.

### Validator issue paths and generated Java contracts

`SkillInputValidator` is the shared admission/standard schema authority (`SkillInputValidator.java:17`). It uses a JSON-compatible private view while preserving original Java values in normalized input. Runtime-capable/attachment leaves use generated local wrapper schemas, then strip the temporary `/value` prefix. Standard issues use the canonical validator's instance location and keyword. Local JSON admission issues use `non_string_key`, `non_json_value`, or attachment-related codes.

Its `child` helper constructs standard JSON Pointers by escaping `~` to `~0` and `/` to `~1`; the root is the empty string. Generated callable Java input contracts arrive through the same `CapabilityMetadata.inputContract()` and validation paths. `JavaInputDeclarationTest`, `LoomspanMethodInputSchemaGeneratorTest`, `SkillInputValidatorTest`, and `SupportedSurfaceIntegrationTest` cover generated declaration and supported invocation behavior.

## Executor transform boundaries

`YamlSkillDefinition` owns the prepared YAML identity and compiled input/output transforms. `validateTransformInput` performs JSON admission when input transformation exists (`YamlSkillDefinition.java:27`). The coordinator invokes it before serializing original input/opening the mission frame (`ExecutionCoordinator.java:157`). `executorInput` evaluates the prepared input transform after reference resolution, inside mission work, before executor entry (`YamlSkillDefinition.java:36`, `ExecutionCoordinator.java:181`). Both paths wrap transform errors with `Skill 'canonical-name' input_transform failed` and the original explanation/cause.

`ExecutorTransform.applyToInput` requires JSON input and an ObjectNode result (`ExecutorTransform.java:36`). Scalar, array and null results fail before Java executor invocation. `requireJsonInput` rejects runtime handles unsupported by the declared transform. The same class compiles declarations separately during preparation using JSLT Parser, which is outside runtime contract-stage attribution.

`executorOutput` evaluates only a declared output transform after successful executor work (`YamlSkillDefinition.java:50`). `ExecutorTransform.applyToOutput` strictly parses exactly one complete JSON value, evaluates JSLT, checks publishable finite JSON values and serializes the result (`ExecutorTransform.java:46`). The definition wraps runtime errors with its own canonical name and the JSLT/Framework explanation. An authored `error("…")` survives in that message/cause chain. A handler failure bypasses output transformation.

`ExecutionCoordinator.publishExecutorResult` records raw executor output and transform status, then validates the transformed candidate if a schema is declared (`ExecutionCoordinator.java:347`). It invokes the handler once and has no semantic retry loop for executor leaves. Transform failure and subsequent schema failure are distinct existing branches.

## Output schema boundaries

### Shared validator and output exception

`OutputSchemaValidator.validate` uses strict, lossless JSON parsing with trailing-token rejection followed by the immutable canonical validator (`OutputSchemaValidator.java:31`). Completed schema failures retain validator keyword, instance location and explanation. Blank/malformed JSON produces code `invalid_json` with the parser explanation and available bounded parser facts. Operational validator failures propagate as runtime failures rather than completed validation verdicts.

Existing output diagnostic root paths are `$`, whereas non-root paths come directly from `error.getInstanceLocation().toString()` (`OutputSchemaValidator.java:43`, `:54`). The new public shape requested by the ticket has different root semantics, requiring an empty root pointer while preserving these existing diagnostics. Keys containing literal slash/tilde use the validator's instance-location escaping. The public input issue shape already has the three basic issue fields the ticket requests, but is currently input-specific.

`LoomspanOutputSchemaValidationException` is internal despite being public Java. It carries canonical skill name, raw candidate, immutable validation issues, attempt count, retry limit and failure mode. Its contextual message summarizes the first issue. `OutputSchemaValidationIssue` also contains optional parser facts beyond path/code/message.

`OutputSchemaValidationIssue` currently normalizes every textual fact during construction (`OutputSchemaValidationIssue.java:25`). In particular, its path has control characters replaced with spaces, is trimmed, and is truncated to 256 code points. Its message is limited to 512 code points. These are the existing diagnostic representation rules, so the stored output issue can differ from the validator's original instance location for long keys, trailing spaces or control characters. The validator has the original location before constructing this record. This distinction matters when planning the ticket's standard JSON Pointer contract while retaining existing diagnostic paths.

### Executor publication and ordinary model output

Executor publication validates once after optional output transformation. A mismatch throws the internal output exception with the actual result issues, attempt 1 and zero retries (`ExecutionCoordinator.java:371`). `skillOutput` is recorded only after validation acceptance. Raw executor output remains available under existing diagnostics limits. A transform that publishes null at a number field therefore takes output schema validation after a successful output transform.

`OutputSchemaCallAdvisor.adviseCall` validates ordinary candidate text or `OutputBindingComposition.Result.validation()` (`OutputSchemaCallAdvisor.java:113`). It passes on success and requests existing semantic correction on rejection. Only exhaustion throws the internal output exception containing that attempt's complete issue list (`OutputSchemaCallAdvisor.java:165`). Outcome/trace summaries are separately limited to four issues. Corrected model output returns successfully without throwing that attempt's validation exception.

### Planning output and assembly

`StepLoopMissionExecutionEngine.validateFinalResponseForSkill` checks schema first, evidence second, linter third (`StepLoopMissionExecutionEngine.java:1333`). `validateOutputSchema` retains a complete validator result locally, records a schema outcome, then reduces rejection to text and attempt/exhaustion state (`:1406`). The shared final-response exhaustion branch throws IllegalStateException (`:1015`). A schema failure corrected before an evidence/linter terminal rejection is therefore an earlier distinct verdict.

With output bindings, `validateContributionFailure` accepts the actual composition verdict and uses the schema correction budget (`StepLoopMissionExecutionEngine.java:579`). The fully bound path first calls `composeEmpty` and throws a general `binding_output_contract` error directly on its invalid schema result (`:516`). `requireCompleteOutputPolicies` then validates a fully bound assembled result against final policies and throws a general `binding_output_contract` error when a policy fails (`:573`). These paths share typed validation and text-only exhaustion structures.

`OutputBindingComposition` owns selected source snapshots, the projected model contribution contract and complete output validation (`OutputBindingComposition.java:14`). During construction it validates each selected destination value using a generated object wrapper with property `value` and throws `binding_output_validation` with formatted issues on failure (`:46`). At that boundary the originating output definition is available in the primary constructor, while the delegated constructor currently receives only schema/bindings/input/results. The wrapper's issue locations are relative to `/value`, and the destination's pointer is separately available. Composition checks unavailable sources/evidence independently before accepting final output.

`compose` checks model overrides, validates the projected candidate, inserts bound data, and validates the whole candidate against the original schema (`OutputBindingComposition.java:72`). It returns a typed validation result for ordinary schema failures, including complete assembled failures. `AcceptedResultDecoder` preserves malformed/plain text as a String, after which composition asks the shared output validator to produce the verdict. Binding model overrides currently synthesize code `binding_model_override` at diagnostic root `$`.

## Canonical causation and terminal selection

`LoomspanSession` constructs a session-local IdentityHashMap of throwable to RecordedFailure for every session, independent of the persistence policy (`LoomspanSession.java:51`, `:104`). This record currently contains failure ID, origin frame ID and provider-attempt link. `recordFailure` is lock-protected (`:148`).

`recordFailure` normalizes transparent wrappers using `SkillFailurePolicy.unwrapInvocationFailure`. An already-recorded effective throwable reuses its failure ID. Otherwise it walks the cause chain, identity-guards cycles and bounds depth at 64. It finds an existing cause failure and emits `causedByFailureId` while giving an ordinary application wrapper its own failure ID. Suppressed failures are outside this walk. Throwable registration happens before the best-effort trace append, and recording errors become session failure-recording state.

`SkillFailurePolicy` unwraps reflection, undeclared checked exceptions, worker ExecutionException, and Spring AI ToolExecutionException (`SkillFailurePolicy.java:10`). An ordinary application wrapper remains its own classification/identity. The canonical record can still link it to an already-recorded cause. Failure recording and recovery classification share this policy.

`StepLoopMissionExecutionEngine.propagateUnitFailure` first propagates primary lifecycle cancellation. Otherwise it scans accepted outcomes in task order and chooses the first terminal-classified failure, remembering the first recoverable failure only as fallback (`StepLoopMissionExecutionEngine.java:740`). Worker completion or recorded event order can differ from this order. It never selects an arbitrary schema event.

`ExecutionCoordinator` catches the propagated error and selects canonical `terminalFailureId` (`ExecutionCoordinator.java:220`). A primary cancellation wins when present. It can supply an existing failure ID even when `ex` is a schema failure from another path. Without primary cancellation, it records the propagated exception, including causal wrapper linkage. The same terminal ID is passed to the top-level TraceCompletion (`:292`).

`MissionLifecycle.PrimaryCancellation` contains cause, optional recorded failure ID and deadline (`MissionLifecycle.java:648`). The lifecycle's primary cause and ID live independently of diagnostic output. The coordinator may observe a failure different from the primary cause. This distinction is visible in lifecycle tests and matters for a public diagnosis on the final facade failure.

`LoomspanSession.finalizeTrace` receives the selected completion and finalizes the handle (`LoomspanSession.java:582`). Existing ExecutionTrace is a small summary with completed/errored/path/policy and does not expose terminal ID. The session's last output schema outcome is retained root diagnostic state, independent of causal selection. It can describe a recorded child failure that did not win. `LoomspanSessionRunner.finalizeSessionTrace` provides a fallback for standalone sessions whose coordinator has not already completed the trace (`LoomspanSessionRunner.java:366`).

## Contract inventory and consumer boundaries

| Category | Current evidence and exposure |
| --- | --- |
| Application API | Exact allowlist in `LoomspanPublicSurfaceArchitectureTest.java:29` includes SkillTemplate, SkillInvocationHandoff, AdmittedSkillInvocation, SkillException, SkillInputValidationException and SkillInputValidationIssue. Their signatures and constructors are consumer contracts. README summarizes the closed surface. |
| Supported SPI | Repository policy and architecture tests state there is no supported Java SPI or internal bean replacement surface. Annotated application methods register declarations within the existing API. |
| Configuration and manifest contracts | Existing input_schema, input_transform, output_transform, output_schema, correction budgets and diagnostics retention govern the paths above. Ticket authorizes reporting changes and identifies runtime loading/compilation, missing sources and authorization as outside the four stages. No new syntax is prescribed. |
| Persisted or serialized contracts | No new durable format is specified by the ticket. Sidecar HTTP response representation is explicitly consumer work. Existing public exception/value signatures are separate from HTTP/NDJSON formats. |
| Ephemeral diagnostic formats | Canonical ERROR_RECORDED, causedByFailureId, root terminalFailureId, output schema outcomes and observer events describe current execution. Session identity state and canonical completion are the existing runtime authorities. |
| Internal or accidentally exposed implementation | All core/runtime/outputschema types described here, constructors and Spring factory methods are internal despite public modifiers. Their existing Java signatures have no supported consumer compatibility obligation. |

`LoomspanAutoConfiguration` creates DefaultSkillTemplate and DefaultSkillInvocationHandoff (`LoomspanAutoConfiguration.java:301`, `:316`). Their beans provide supported interfaces, while internal constructors are implementation wiring. Supported signatures cannot expose these internal types. New top-level public API types need deliberate allowlist entries and supported-surface coverage under AGENTS.md. The architecture test separately enumerates technically public internals.

The ticket does not request Console adapter REST/SSE, acquisition/problem representation or consumed NDJSON changes. Current canonical failure linkage and public failure observation are relevant diagnostic consumers, but the source inventory establishes no need for a new Java-to-Go field or protocol marker just to add Java diagnosis. A later implementation decision to change a consumed trace field would require that boundary assessment in planning.

## Existing executable verification anchors

- `ApplicationApiValueTest` protects public constructors, immutable issue copying and API value shapes. `LoomspanPublicSurfaceArchitectureTest` enforces all public type classifications and signature boundaries.
- `SupportedSurfaceIntegrationTest#invokesYamlPlannerAndBothLeafKindsThroughSupportedSurface` exercises ordinary application composition with supported entry points and local protocol-compatible provider responses. The ticket calls for consumer assertions using supported types alone.
- `DefaultSkillTemplateTest` protects input preparation, exception passthrough, AccessDeniedException identity, Object/null behavior, no observer before session, observer timing, fatal Errors and failure suppression. It includes a preceding recorded failure that differs from the facade failure (`:262`). It also has handoff lifecycle tests and sessions with TracePersistencePolicy.NEVER (`:120`).
- `ExecutorOutputSchemaIntegrationTest#invalidCandidatesFailOnceWithLocationAndConstraint` covers invalid_json, type, required, bounds and additionalProperties (`:103`). `#schemaMismatchIsTerminalAcrossCallerRoutes` covers direct, ordinary and planned child routes (`:241`). `#dependentChildOnlyConsumesValidProducerResults` prevents dependent execution after a transformed null fails the producer schema (`:271`). Existing tests inspect the internal exception through causes and trace records.
- `ExecutorOutputTransformIntegrationTest#transformFailureIsTerminalAfterExactlyOneExecutorCall` covers malformed raw JSON and authored JSLT error across direct, ordinary and planning routes, and verifies one handler call (`:167`). `ExecutorInputTransformIntegrationTest` and `ExecutorInputTransformTest` cover input admission, non-object results and generation capture.
- `StepLoopMissionExecutionEngineTest#parallelFailureSelectionUsesClassificationThenAcceptedOrder` forces task two to finish before task one and verifies reverse error record order plus selected failure (`:3069`). Existing neighboring tests cover reverse successful completion, multiple failures and cancellation. It is an engine-level test, so complete facade/canonical agreement still needs integrated evidence.
- `LoomspanSessionTest` covers repeat recording, transparent wrapper identity, application cause links, cycle/depth handling, concurrency and session isolation (`:116`, `:145`, `:204`, `:238`, `:259`). Lifecycle suites cover primary cancellation and cutoff behavior.
- `OutputSchemaCallAdvisorTest`, `OutputSchemaValidatorTest`, `OutputBindingCompositionTest`, `DeclaredOutputBindingsOrdinaryIntegrationTest` and `DeclaredOutputBindingsPlanningIntegrationTest` cover ordinary/planning correction and composition boundaries. `DeclaredChildInputBindingsIntegrationTest` covers runtime receiving validation before child dispatch.

Maven uses Java release 21 and Surefire 3.5.2 (`pom.xml:46`, `:58`). The repository has `mvnw.cmd`. Research ran no tests, so the anchors above establish coverage from inspection rather than fresh passing results.

## Documentation alignment and impact

The repository-bundled `agent-skills/loomspan-docs/SKILL.md` metadata and pom.xml both identify `1.0.0-beta.10-SNAPSHOT`. The documentation was consulted from this checkout after source/test investigation. Relevant routes are `java-api/README.md`, `compatibility-and-boundaries.md`, `invocation.md`, `observation-and-errors.md`, and the skill-authoring executor topic and source-verification protocol.

**Aligned.** `observation-and-errors.md` documents the current public exception taxonomy, input issues, pre-session observer absence, original failure precedence and causal wrapper diagnostics. The executor topic accurately describes one-time transformation/publication, optional authored output schema, terminal mismatch, raw/published diagnostics and skipped dependent execution. No current document promises the new structured diagnosis.

**Documentation drift.** `java-api/invocation.md` still says effective executor schema metadata creates no executor validation guarantee, whereas the executable coordinator and the executor authoring topic now enforce a declared executor output schema. The narrow assertion about Framework not generating a provider answer schema remains supported. This is a wording discrepancy in the existing invocation reference, resolved by current source and focused tests. It does not create an open product decision or change ticket scope.

Authoring impact exists in failure interpretation and debugging guidance because authors can identify their failing skill/stage without retained diagnostics. Existing transform, schema, correction and dispatch semantics remain the ticket's baseline. README is an explicit deliverable. The routed public error reference and closed API inventories describe the same consumer contract, and the executor/debugging routes currently direct readers toward diagnostic evidence. The applicable coverage tables live in the two knowledge-set READMEs. Exact new claims depend on the implementation and focused tests produced by later steps.

## Historical context and related research

- `ai/thoughts/research/2026-11-18-PR-31.4.4-executor-output-schema.md` maps the earlier executor publication/schema work before the current commit. Its current executable result is the coordinator boundary and focused tests above.
- `ai/thoughts/research/2026-11-18-PR-31.4.3-executor-output-transform.md` records the preceding transform boundary. Its plans and testing plan are supplementary historical evidence.
- `ai/thoughts/research/2026-11-16-PR-31.1.2-bounded-executor-diagnostics.md` describes the shared executor observation/tool boundaries and the distinction between developer diagnostics and terminal failure.
- `ai/thoughts/framework-feature-design-lens.md` supplies the exact contract categories and current pre-1.0 assessment framework. Root AGENTS.md closes the supported Java surface and requires data fidelity while retaining the established ExecutionJournalProjector exception.

## Open questions for planning

No unresolved product decision requires developer input at research completion. The ticket leaves the Java diagnosis shape to implementation. Planning still needs to specify the additive public signature and constructor treatment, its immutable issue value, and its integration with the existing public input exception.

Planning also needs to specify how the existing session failure identity and selected root completion carry the originating diagnosis through causal wrappers and cancellation precedence, including unavailable/failing diagnostic writes. The current code paths above provide both originating validator results and the canonical selection decisions. No research finding requires changing validation, retries, recovery, executor calls or canonical failure selection.

The output assembly inventory includes selected destination validation, complete bound output policies, projected contribution validation and complete assembled schema validation. Their differing local pointer bases, original validator locations versus bounded diagnostic issue paths, and shared non-schema policy paths remain concrete design details to settle in the implementation/testing plans.
