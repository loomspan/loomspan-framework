# TypeSafe to Decisions pack: source migration review

Original review date: 2026-11-15. Follow-up/source audit and consolidation: 2026-11-16.
Status: source findings supporting the agreed design; no extraction implementation.

## Document role and evidence limits

This document owns current-code findings and source/test anchors. All agreed
replacement decisions and acceptance requirements live in the
[roadmap](executor-packs-and-typesafe-extraction-roadmap.md); external evidence and
rationale live in the [research document](decisions-executor-pack-research.md).
“Migration” means replacement analysis, not a migration-support deliverable.

The original review inspected production and test sources without running tests or
live provider calls. The later namespace test run and audit status are recorded once
in the roadmap's [verification history](executor-packs-and-typesafe-extraction-roadmap.md#handoff-and-verification-history).
Source findings describe the native baseline, not obligations to retain it.

## Assessment and source map

The proposed pack is feasible in principle but is not a mechanical relocation:
configuration resolution, declaration validation, output metadata, and numeric
decoding currently depend on Framework internals. The source review found no need
for additional TypeSafe-specific Framework capabilities. Actual viability still
requires the Extensions-owned consumer proof specified in the roadmap.

| Native stage | Current source |
| --- | --- |
| Declaration parsing/model binding | `YamlSkillCatalog.loadDefinition`, `validateDeclarationForm` |
| Request validation/input substitution | `TypeSafeDeclaration.compile/resolve` |
| Connection/client construction | `NamedAiConnectionRegistry`, `ProviderConnectionRuntime`, `TypeSafeClient` |
| Dispatch | `ExecutionCoordinator.executeBound`, `MissionWorkExecutor.executeEvaluation` |
| Attempts, responses, evidence | `TypeSafeExecutor.execute` |
| Retirement | Native generation connection ownership |

Source anchors: [catalog](../../../src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java),
[declaration](../../../src/main/java/ai/loomspan/internal/typesafe/TypeSafeDeclaration.java),
[native executor](../../../src/main/java/ai/loomspan/internal/typesafe/TypeSafeExecutor.java),
[client](../../../src/main/java/ai/loomspan/internal/typesafe/TypeSafeClient.java),
[registry](../../../src/main/java/ai/loomspan/internal/autoconfigure/NamedAiConnectionRegistry.java).
Catalog parsing, driver configuration, dispatch, and diagnostics recognize TypeSafe
specially; `ProviderConnectionRuntime` directly references its client. These are
starting anchors for Phase 1's cross-layer removal inventory, not a complete inventory.
Native `TypeSafeExecutor` reserves each physical attempt against mission provider
budget and reports response usage; `TypeSafeClient` connects lifecycle cancellation
to the physical request. Phase 4 owns the accepted replacement differences.

## 1. Invocation registration

`SkillMethodBeanPostProcessor.registerExecutor` accepts exactly the unversioned
`SkillExecutorInvocation`; `SkillExecutorRegistration` stores a function typed to it,
and `ExecutionCoordinator` constructs the five-component public record. Its fields
are input, skill config, executor config, skill name, and executor config generation ID;
it exposes neither accounting nor a trace writer. A proposed versioned parameter
currently fails registration.

Sources: [registration](../../../src/main/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessor.java),
[registration record](../../../src/main/java/ai/loomspan/internal/core/SkillExecutorRegistration.java),
[handoff construction](../../../src/main/java/ai/loomspan/internal/core/ExecutionCoordinator.java).
Disposition: [Phase 1](executor-packs-and-typesafe-extraction-roadmap.md#phase-1--finalize-the-reviewed-public-contract-and-removal-inventory).

## 2. Configuration numeric decoding

Native TypeSafe uses the lossless skill-document number path. However,
`SkillDocumentValues.decode` separately reads executor `config` through
`YamlValues.read`, the ordinary numeric constructor. Namespace `executor-config`
uses the ordinary path too. `YamlSkillCatalog` then installs that raw map as skill
configuration. Consequently, moving precise literal criteria/state from `typesafe:`
to `config:` can convert decimals to floating point before pack execution. A pack
Jackson mapper cannot recover digits already lost.

Sources: [skill decoding](../../../src/main/java/ai/loomspan/internal/serialization/SkillDocumentValues.java),
[YAML constructors](../../../src/main/java/ai/loomspan/internal/serialization/YamlValues.java),
[catalog config binding](../../../src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java).
Evidence: `TypeSafeDeclarationTests.numericSubstitutionsAndRepeatedDetachedReadsPreserveArbitraryPrecision`
and `TypeSafeSkillIntegrationTest.nestedDecimalsRemainExactInRetryBodiesAnswersAndTraceEvidence`.

Disposition: [request construction and numeric behavior](executor-packs-and-typesafe-extraction-roadmap.md#request-construction-and-validation).

## 3. Native credential/model resolution

Native skills select `model: alias`, resolved through Framework model/connection
configuration. Executor leaves reject `model`; the invocation supplies configuration
maps, not a provider registry/client or resolved native credentials.

Executor maps deliberately preserve literal `${...}` and `api-key-ref` values.
`ExecutionConfigurationParser` resolves credential references in native connections,
not executor maps; supplied credential values used only by executor maps can be
rejected as unused. Moving native connection YAML under `executor-config` is not
an equivalent setup.

Sources: [configuration parser](../../../src/main/java/ai/loomspan/internal/skill/ExecutionConfigurationParser.java),
[executor configuration tests](../../../src/test/java/ai/loomspan/autoconfigure/ExecutorConfigurationStartupTests.java),
[native configuration guide](../../../agent-skills/loomspan-docs/references/skill-authoring/typesafe-skills.md).

Disposition: [captured credentials and endpoints](executor-packs-and-typesafe-extraction-roadmap.md#captured-credentials-and-endpoints).

## 4. Declaration validation and binding

Native compilation rejects malformed question declarations and input expressions
when a skill is loaded. Generic executor configuration only checks structural maps,
references, and selected configuration paths. It has no supported pack validation
hook during `SkillReloader.validate/prepare`.

The native resolver is a source reference for whole-value JSON Pointer
substitution, scalar-only embedded substitution, escaped expressions, and
null/omission behavior. These are evidence for designing the pack binding, not a
requirement to preserve legacy expression syntax.

Sources: [declaration resolver](../../../src/main/java/ai/loomspan/internal/typesafe/TypeSafeDeclaration.java),
[dependency validation](../../../src/main/java/ai/loomspan/internal/skill/SkillDependencyValidator.java).

Disposition: [request construction and validation](executor-packs-and-typesafe-extraction-roadmap.md#request-construction-and-validation).

## 5. Output metadata and Java result conversion

Native `TypeSafeDeclaration` derives a descriptive output schema, exposed through
catalog inspection and parent forwarding. Executor leaves reject `output_schema`,
and `SkillDependencyValidator.resolveOutputSchema` excludes executor definitions.
A pack cannot publish equivalent generated metadata through the proposed invocation.

Native output metadata is descriptive; it does not enforce answer semantics.
The ordinary Java executor path also has a return-conversion boundary:
[`JavaSkillInvoker`](../../../src/main/java/ai/loomspan/internal/core/JavaSkillInvoker.java)
converts Java null to empty output, passes Java strings through as raw text, and
serializes other return values. This is the source evidence behind the agreed
JSON null/string consumer checks, not a new Framework behavior requirement.

Sources: [output schema selection](../../../src/main/java/ai/loomspan/internal/skill/YamlSkillDefinition.java),
[dependency validation](../../../src/main/java/ai/loomspan/internal/skill/SkillDependencyValidator.java),
[binding checks](../../../src/main/java/ai/loomspan/internal/skill/BindingSchemaCompatibility.java),
[inspection test](../../../src/test/java/ai/loomspan/internal/observability/web/TypeSafeRegisteredInspectionIntegrationTest.java).

Disposition: [complete response and Java return boundary](executor-packs-and-typesafe-extraction-roadmap.md#complete-response-and-java-return-boundary).

## 6. Native response and endpoint handling

Native success extracts the entire object-valued `answers` payload, so current
bindings can use `/repeat/noul`. The client supports a configured prefix plus
`/v1/systemone`, including existing OpenRouter configuration where protocol-compatible.
These source facts do not establish current external service availability.

The current client explicitly disables automatic retries and redirect handling.
The replacement decision and its acceptance test are recorded in the roadmap's
[Automatic redirects](executor-packs-and-typesafe-extraction-roadmap.md#automatic-redirects) section.

Sources: [native execution](../../../src/main/java/ai/loomspan/internal/typesafe/TypeSafeExecutor.java),
[HTTP client tests](../../../src/test/java/ai/loomspan/internal/typesafe/TypeSafeClientTest.java),
[integration tests](../../../src/test/java/ai/loomspan/integration/TypeSafeSkillIntegrationTest.java).

Disposition: [HTTP handling](executor-packs-and-typesafe-extraction-roadmap.md#transport-and-http-outcome-handling)
and [response contract](executor-packs-and-typesafe-extraction-roadmap.md#complete-response-and-java-return-boundary).

## 7. Timeout and cancellation evidence

The current Apache client response/socket timeout is an inactivity bound. The test
`responseTimeoutIsInactivityWhileProgressingChunksMayExceedIt` demonstrates a response
lasting beyond the configured timeout while chunks keep arriving. Copying the client
and setting its timeout to `remainingTime()` would therefore not guarantee a total
request deadline. Native lifecycle cancellation currently provides the stronger abort.

`MissionWorkExecutor` enforces mission timeout with timed `Future.get(...)`;
`MissionLifecycle` also tracks cancellation-cleanup grace, while
`FrameworkExecutionLifecycle` owns shutdown timing. Execution budget and cleanup
grace are therefore distinct source concepts. Implementation guidance and acceptance
checks are in the roadmap's settled contract rather than duplicated here.

Sources: [client](../../../src/main/java/ai/loomspan/internal/typesafe/TypeSafeClient.java),
[timeout and cancellation tests](../../../src/test/java/ai/loomspan/internal/typesafe/TypeSafeClientTest.java).

Disposition: [cancellation contract](executor-packs-and-typesafe-extraction-roadmap.md#settled-cancellation-contract-and-mandatory-ticket-handoff).

## 8. Resource lifetime and namespaces

The native client constructs an Apache HTTP client/pool per instance; native
generation ownership connects those resources to retirement. Existing executor
generations retain captured configuration for active work, and public retirement
notifications are available for generation-owned resources.

Executor-backed skills already participate in input/access checks, composition,
captured configuration, and ordinary observation. They use the `JAVA` classification.
Multiple YAML skills can reference an executor. Source/tests confirm private
namespaces allow same-namespace references and public namespaces permit foreign
references subject to authorization. Executors are reference-only. This verifies
existing behavior, not original design intent; the earlier concern became a
documentation clarification, not a new namespace workstream or acceptance gate.
The follow-up inspected `SkillExecutor.namespace()`, executor registration, and
`SkillDependencyValidator` reference/visibility resolution; its test-run evidence
is recorded in the roadmap's verification history.

Sources: [executor binding](../../../src/main/java/ai/loomspan/internal/skill/SkillDependencyValidator.java),
[public retirement API](../../../src/main/java/ai/loomspan/api/SkillReloader.java),
[resource ownership guide](../../../agent-skills/loomspan-docs/references/java-api/skill-reload.md),
[generation tests](../../../src/test/java/ai/loomspan/integration/ExecutorGenerationExecutionIntegrationTest.java).

Dispositions: [namespace relationship](executor-packs-and-typesafe-extraction-roadmap.md#existing-namespace-relationship-retained)
and [transport lifetime](executor-packs-and-typesafe-extraction-roadmap.md#transport-and-http-outcome-handling).

## Using this evidence in implementation

The eight dispositions above are settled; the linked roadmap sections own their
implementation requirements. Reuse relevant native fixtures, adapting intentionally
changed expectations, under [consumer acceptance](executor-packs-and-typesafe-extraction-roadmap.md#recipe-and-consumer-acceptance).
The roadmap also owns [ticket handoffs](executor-packs-and-typesafe-extraction-roadmap.md#settled-test-ownership-and-mandatory-ticket-handoff),
[removal](executor-packs-and-typesafe-extraction-roadmap.md#phase-6--remove-native-typesafe-implementation-from-core),
and [remaining choices](executor-packs-and-typesafe-extraction-roadmap.md#remaining-implementation-choices-within-settled-scope).
Recheck source anchors before coding; this review does not prove the proposed pack works.
