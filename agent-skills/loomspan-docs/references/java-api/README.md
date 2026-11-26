---
audience: loomspan-application-developer
status: development
applies_to: bundled-loomspan-revision
coverage: initial-source-verified
---

# Loomspan Java API Knowledge Base

## Purpose

This knowledge set documents the supported application-facing Java API of the
Loomspan Spring Boot starter. Use it when writing application code that invokes
skills, declares Java skills, observes completed executions,
discovers or validates skills, or handles errors from the supported facade.

Loomspan is under active development and has no production release yet. These
documents describe the repository revision with which this skill package was
published. In a consumer project, install the skill from the repository tag
matching the Loomspan Maven dependency once releases exist.

## Authority

The supported Java surface is closed. Its executable authority is the exact
allowlist in
`src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java`.
The allowlist currently contains only:

- `SkillTemplate`
- `SkillInvocationHandoff`
- `AdmittedSkillInvocation`
- `SkillInvocationResult`
- `SkillAccessDeniedException`
- `SkillReloader`
- `NamespaceVisibility`
- `SkillDocument`
- `ExecutionConfiguration`
- `SkillValidationResult`
- `SkillValidationIssue`
- `ValidatedSkill`
- `PreparedSkillUpdate`
- `SkillCatalog`
- `SkillDescriptor`
- `SkillKind`
- `SkillExecutionView`
- `SkillExecutionEvent`
- `SkillMethod`
- `SkillExecutor`
- `SkillExecutorInvocation` (v1)
- `ExecutorDiagnostics` (v1)
- `RecoverableSkillException`
- `PlannerInabilityException`
- `SkillParam`
- `SkillException`
- `SkillReloadException`
- `SkillInputValidationException`
- `SkillInputValidationIssue`
- `SkillFailureDiagnosis`
- `SkillContractStage`
- `SkillContractIssue`

The thirty-two supported types comprise thirty in `ai.loomspan.api` and
exactly `SkillExecutorInvocation` and `ExecutorDiagnostics` in
`ai.loomspan.api.extensions.v1`. Arbitrary types in that package are not approved. A Java
`public` modifier does not make any other Loomspan type supported API. Read
[compatibility-and-boundaries.md](compatibility-and-boundaries.md) before
advising an application to depend on another Loomspan type.

For exact behavior, use this evidence order:

1. The architecture allowlist defines which Java types are supported API.
2. Public type signatures and focused API tests define their contracts.
3. Supported-surface integration tests demonstrate consumer composition.
4. Production implementation resolves an edge case without turning internal
   types into consumer guidance.

If bundled guidance and matching source or tests conflict, report the drift.
Follow the [source-verification protocol](../skill-authoring/source-verification.md)
when exact source inspection is required.

## Route a Java API Task

| Developer need | Read first | Then read |
| --- | --- | --- |
| Determine whether a Loomspan type or extension point is supported | [compatibility-and-boundaries.md](compatibility-and-boundaries.md) | The relevant API topic below |
| Invoke or atomically hand off a Java, executor-backed YAML, or model-backed YAML skill from application code | [invocation.md](invocation.md) | [observation-and-errors.md](observation-and-errors.md) when observing or handling failures |
| Discover skills or pre-check a root request | [catalog-and-validation.md](catalog-and-validation.md) | [invocation.md](invocation.md) for execution-time guarantees |
| Validate a proposed complete skill set and inspect candidate names/kinds | [skill-reload.md](skill-reload.md) | [draft validation workflow](../skill-authoring/validation-workflow.md) for authoring feedback |
| Prepare, publish, and retire complete skill and execution generations from complete application-supplied documents and configuration | [skill-reload.md](skill-reload.md) | [executor configuration](../skill-authoring/executor-skills.md) for namespace data rules |
| Declare an application method as a callable skill or reference-only executor | [java-skills.md](java-skills.md) | [reflected input contracts](../skill-authoring/input-contracts.md) for complete input-shape guidance |
| Understand executor declaration syntax, configuration and current limitations | [executor declarations](../skill-authoring/executor-skills.md) | [Java declarations](java-skills.md) |
| Consume an execution view or diagnose facade failures | [observation-and-errors.md](observation-and-errors.md) | [invocation.md](invocation.md) for lifecycle timing |
| Mock Loomspan in an application unit test | [invocation.md](invocation.md) | [compatibility-and-boundaries.md](compatibility-and-boundaries.md) for the supported testing boundary |
| Perform a framework integration test | [compatibility-and-boundaries.md](compatibility-and-boundaries.md) | [invocation.md](invocation.md) |

Do not load every document by default. For preparation, load this index and
the compatibility baseline; then route the concrete task to the smallest
relevant topic set.

Run the complete executor quickstart (`docs/quickstart-executors.md` in the matching framework checkout) for local HTTP and SQL-shaped application execution without a provider. For generation-keyed resource cleanup, read [retirement guidance](../java-api/skill-reload.md#executor-owned-application-resources).

## Coverage

| Topic | Coverage | Notes |
| --- | --- | --- |
| Supported public types | Source-verified | Exact thirty-two-type allowlist, selected v1 types and package boundaries |
| Catalog and pre-checks | Source-verified | Explicit namespace acquisition, reusable namespace/localName metadata with derived qualifiedName (ApplicationApiValueTest and PortableDeclarationIdentityIntegrationTest), local-only catalog lookup and explicit pair invoke/validate/handoff (DefaultSkillCatalogTest and SkillReachableAdmissionTest), malformed/missing lookup, candidate IDs and exact optional publication labels, active namespace registry and removal; fresh reachable admission and temporary validation are covered by SkillReachableAdmissionTest; shared composed executor entry/filter/dispatch checks are covered by `SharedDeclarativeAuthorizationIntegrationTest` |
| Two-stage skill updates | Source-verified | Complete skill and execution candidate, candidate-wide Environment or copied exact-map variable markers, frozen generation, exact host-owned publication label, process-local ID, one-shot publication, candidate disposal, safe retirement callback, and application-owned readiness and cleanup. Executor namespace IDs can key application-owned resources through existing retirement listeners; shutdown remains application-owned. `PortableDeclarationIdentityIntegrationTest#suppliedLabelsRemainExactThroughValidationAndPreparation` verifies exact diagnostic labels with normal and explicit-map preparation; `StartupSkillPathTest` verifies loader-owned filesystem, classpath and JAR path checks. |
| Skill invocation | Source-verified | Explicit namespace/localName invocation and atomic single-use handoff with captured generation ID, exact result text and root session ID. `ApplicationApiValueTest`, `DefaultSkillTemplateTest` and `SupportedSurfaceIntegrationTest` cover signatures, values, optional mapping and caller identity; `SkillReachableAdmissionTest` covers admission. |
| Java declarations and results | Source-verified | Callable/executor discovery shares proxy/security checks; exact string/null/JSON results, constructor copying and designated failures are covered by `SkillExecutorTest`, `SharedJavaDiscoveryInvocationTest` and `SupportedSurfaceIntegrationTest`. Independent owner/leaf-consumer maps, complete consumer publication and retained capture (`ExecutorDependencyResolutionTest`, `ExecutorGenerationExecutionIntegrationTest`); captured YAML executor binding/policy and secured retained execution are covered by `ExecutorDependencyResolutionTest` and `ExecutorGenerationExecutionIntegrationTest`; actual argument advice on root/child mission workers and context restoration for both forms by `SharedDeclarativeAuthorizationIntegrationTest`; designated standard feedback, direct/step termination and exact messages are covered by `ExplicitJavaFailureHandlingIntegrationTest`; no automatic replay or step correction loop. Reflected callable inputs live in `skill-authoring`. `ExecutorApplicationIntegrationTest` adds actual HTTP root/standard-child execution, Config Data, default ref-to-Resource resolution and SQL-shaped domain neutrality; the runnable executor quickstart verifies separate application composition. `ExecutorInvocationLifecycleIntegrationTest` and `MissionLifecycleTest` cover live cancellation, original parent budgets, no deadline/expiry and cleanup-grace separation; bounded diagnostic snapshots, states, containment and canonical ordering are covered by `ExecutorDiagnosticsIntegrationTest`, `BoundedExecutorDiagnosticCaptureTest` and `DefaultExecutorDiagnosticsTest`. |
| Execution observations | Source-verified | Immutable completed view/event values, canonical namespace identities, entry versus target generation facts, one observer flow and unchanged trust boundary; NamespaceObservationIntegrationTest and ConsoleTraceFixtureCorpusTest |
| Facade failures | Source-verified | Nullable pre-session identity and immutable session-bearing failure snapshots, Spring-compatible post-session denial, optional selected contract diagnosis, observer failure precedence and fatal errors. `DefaultSkillTemplateTest`, `ExecutorOutputSchemaIntegrationTest`, `SupportedSurfaceIntegrationTest`, `LoomspanSessionTest` and `StepLoopMissionExecutionEngineTest` cover identity isolation, origin, absence, reuse and canonical selection. |
| Configuration properties | Not documented here | Public configuration behavior is user-facing but its binding types are not application Java API |
| Framework extension SPI | Source-verified | No supported Java SPI or internal bean-replacement contract; annotations register application-owned methods |

## Normative Language

- **MUST / MUST NOT** identifies a constraint enforced by the current API,
  architecture tests, or runtime behavior.
- **SHOULD / SHOULD NOT** identifies the recommended application default.
- **MAY** identifies an optional supported choice.

Publication coverage: complete namespace management, captured metadata preparation, Private startup and removal/recreation are implemented and covered by `SkillReloaderTest`, `SkillDependencyValidatorTest` and `NamespaceStartupIntegrationTest`. Reachable snapshot/admission and temporary validation are covered by `SkillAdmissionSnapshotTest`, `SkillAdmissionContractsTest`, and `SkillReachableAdmissionTest`. Captured cross-namespace dispatch is verified by `NamespaceExecutionAuthorityIntegrationTest`; executor configuration-owner identities are covered by `ExecutorGenerationExecutionIntegrationTest`. Current installed namespace labels, empty inventory and restart installation are covered by `NamespacePublicationObservationIntegrationTest`; immutable candidate/active labels and variable source modes by `SkillReloaderTest`. Captured namespace observation metadata and existing Console consumers are verified through NamespaceObservationIntegrationTest, focused HTTP/trace tests and ConsoleTraceFixtureCorpusTest, with entry-scoped generation IDs and independent observation authorization.

Failure diagnostics coverage: `ExplicitJavaFailureHandlingIntegrationTest` covers both Java roles through direct, actual standard-tool, and explicit-step execution. `LoomspanSessionTest` and current Java/Go diagnostic fixtures cover classification, exact exception text, transparent identity and application-wrapper cause linkage.
