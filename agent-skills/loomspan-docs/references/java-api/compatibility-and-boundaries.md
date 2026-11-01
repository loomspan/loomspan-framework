---
audience: loomspan-application-developer
status: development
applies_to: bundled-loomspan-revision
coverage: source-verified
---

# Java API Compatibility and Boundaries

## Supported Surface

Application code MAY depend on the twenty-five types in the closed
`ai.loomspan.api` allowlist:

| Type | Supported purpose |
| --- | --- |
| `SkillTemplate` | Validate or invoke a named Java, TypeSafe, or model-backed YAML skill |
| `SkillInvocationHandoff` | Atomically transfer a prepared root invocation to framework ownership |
| `AdmittedSkillInvocation` | Read the captured generation ID, execute once, or release an already-admitted root invocation |
| `SkillReloader` | Validate, prepare, publish, snapshot or remove a complete namespace generation |
| `NamespaceVisibility` | Select Private or explicit Public on a complete publication |
| `SkillDocument` | Supply one in-memory YAML document and a diagnostic source label |
| `ExecutionConfiguration` | Supply reference-only authored YAML for a complete execution candidate |
| `SkillValidationResult` | Inspect issues and complete candidate names/kinds after successful validation |
| `SkillValidationIssue` | Inspect structured draft diagnostics |
| `ValidatedSkill` | Read an exact proposed callable name and public kind |
| `PreparedSkillUpdate` | Read a frozen candidate's ID and catalog and close it if unused |
| `SkillCatalog` | Discover an immutable immutable namespace generation catalog, acquired explicitly or from a prepared candidate |
| `SkillDescriptor` | Read a registered skill's public metadata and exact tool schema |
| `SkillKind` | Distinguish YAML, Java, and TYPESAFE registrations |
| `SkillExecutionView` | Observe the completed invocation session |
| `SkillExecutionEvent` | Read a current-version diagnostic event |
| `SkillMethod` | Mark an application bean method as a directly callable Java skill |
| `SkillExecutor` | Mark a reference-only Java execution method; no callable registration or schema |
| `SkillExecutorInvocation` | Construct or receive the five-component executor handoff with copied map/list containers |
| `RecoverableSkillException` | Signal a designated Java failure with exact corrective text and an optional diagnostic cause |
| `SkillParam` | Describe and declare requiredness for a Java skill parameter |
| `SkillException` | Catch a safe Loomspan facade failure |
| `SkillReloadException` | Catch an operational preparation or publication failure |
| `SkillInputValidationException` | Distinguish invalid caller input |
| `SkillInputValidationIssue` | Inspect structured validation issues |

Changes to these types are compatibility-sensitive. Application code SHOULD
still use the narrowest type needed: inject or mock `SkillTemplate`, use the
annotations on application-owned beans, and consume the public value records.

## Compatibility Changes in This Revision

`SkillExecutor`, `SkillExecutorInvocation` and `RecoverableSkillException` are deliberate additive API types. Java successful results now preserve non-null Strings exactly, use empty text for null/void, and JSON-serialize other values. Java method and serialization failures propagate; exception-to-successful-text conversion is removed. Update callers that decoded JSON-quoted Java Strings or relied on successful error text. This intentional pre-1.0 behavior change has no compatibility mode. See [Java declarations](java-skills.md) for the exact contract and executor delivery boundary.

`SkillKind.TYPESAFE` adds native evaluation identity. Applications with exhaustive
enum switches must handle the new value. The `SkillTemplate` String return type
and five-component descriptor remain unchanged; no typed evaluation facade
or additional SPI is introduced. See [TypeSafe authoring](../skill-authoring/typesafe-skills.md).

`NamespaceVisibility` and namespace-first complete `SkillReloader` validation/preparation replace all incomplete/default-only overloads. Configuration is required; omitted scalars use built-in defaults, with no active/startup inheritance. Removal requires namespace and expected active ID. These are intentional pre-1.0 source/binary/behavior changes without shims. See [namespace publication](skill-reload.md).

`SkillValidationResult` now has two record components, `issues` and `skills`.
Direct constructors and record-pattern users must adopt the two-component shape
and recompile; the former one-argument constructor is removed. The new
`ValidatedSkill` record supplies immutable candidate name/kind metadata. See
[two-stage skill updates](skill-reload.md) for the valid-first contract.

Applications use annotated `@SkillExecutor` methods and executor YAML leaves for application-owned operations. These are intentional pre-1.0 source, binary and behavior changes without compatibility shims. Executor-backed YAML skills classify as `SkillKind.JAVA`; their YAML definitions remain available. See [executor leaves](../skill-authoring/executor-skills.md).

`SkillCatalog.generationId()` identifies its process-local immutable namespace generation. Executor invocations receive the captured executor namespace generation ID separately from the executing YAML skill identity. See [two-stage skill updates](skill-reload.md).

The handoff facade and handle are additive and leave every `SkillTemplate`
signature unchanged. The three catalog types are additive. The two `SkillTemplate.validate`
methods are an intentional pre-1.0 source- and binary-sensitive interface
change: application implementations and hand-written fakes or mocks of
`SkillTemplate` MUST add both methods and recompile. There is no default-method
compatibility shim.

`AdmittedSkillInvocation` now requires the abstract `String generationId()` method. Handwritten implementations and fakes must supply the captured process-local ID and recompile; there is no default method or fabricated fallback. Reading the ID does not retain admission ownership or guarantee an application-owned durable snapshot mapping.

Observers passed to `SkillTemplate.invoke` now receive a callback for mappable
post-session failures as well as successes. Applications upgrading from the
earlier success-only behavior SHOULD make observer handling idempotent and
MUST apply the same security and retention policy to both outcomes. There is no
success-only compatibility mode.

`SkillDescriptor` now has five components: `name`, `description`, `kind`,
`inputSchema`, and nullable String `outputSchema`. Direct constructors and
record-pattern users MUST adopt the five-component shape and rebuild. There is
no four-argument compatibility constructor. Equality, `toString()` and serialized
record shape include output metadata. This extends the existing supported type;
no additional application API or SPI is introduced. See [catalog metadata](catalog-and-validation.md).

## Unsupported Dependencies

Application code MUST NOT treat these areas as supported extension API:

- `ai.loomspan.internal`, including public classes, constructors,
  methods, records, and interfaces;
- `ai.loomspan.autoconfigure`, whose public types exist for Spring
  Boot integration and configuration binding;
- internal Spring beans such as registries, routers, resolvers, coordinators,
  model factories, or virtual-file-system components;
- bean names or method signatures as alternative skill invocation identities.

Loomspan exposes no supported Java SPI or contract for replacing framework beans. Do not recommend an internal type merely because it
is accessible to the Java compiler or application context.

Documented configuration keys and behavior remain user-visible contracts even
though their binding types are not application extension API. This knowledge
set does not yet document those configuration contracts.

## Testing Boundary

For an application unit test, mock or fake `SkillTemplate`; it is the supported
facade and has no implementation types in its signatures.

Java-only integration tests can invoke an annotated bean through `SkillTemplate` without a model. For YAML integration tests, configure a real or local protocol-compatible named
connection and invoke a YAML skill through `SkillTemplate`. A supported test
MAY expose an application bean method with `@SkillMethod` and `@SkillParam`,
allow the YAML skill to call its exact name through `allowed_skills`, and observe only
`SkillExecutionView` values. It SHOULD NOT replace Loomspan internal beans.

`SupportedSurfaceIntegrationTest` is the source anchor for this composition:
it verifies a single `SkillTemplate` bean, invokes an LLM-backed YAML skill
through a local OpenAI-compatible endpoint, permits application Java
leaf calls, directly invokes an executor YAML leaf, and receives a public observation
view.

## Architecture Invariants

`LoomspanPublicSurfaceArchitectureTest` protects these boundaries:

- the `api` package has exactly the twenty-five allowlisted public top-level types;
- the separately classified `autoconfigure` types are framework integration,
  not application API;
- every externally accessible Loomspan top-level type is classified;
- no supported Java SPI or internal bean-replacement contract exists;
- supported public signatures do not expose `internal` or `autoconfigure`
  types.

Framework and Console use the coordinated exact project version as their compatibility marker; there is no independent schema counter, range negotiation, legacy reader, or snapshot-to-snapshot compatibility rule. Only dual `development` markers bypass the version promise while retaining ordinary complete validation. Execution classification and declaration source are distinct: public executor leaves classify as Java while their retained declaration is YAML. Diagnostics preserve that source alongside the resolved executor identity and Java implementation; see [skill diagnostics](../skill-authoring/observability-http.md#skill-classification-and-declaration).

## Source Anchors

- `LoomspanPublicSurfaceArchitectureTest` defines the executable classification.
- `ai/loomspan/api/package-info.java` states the supported package boundary.
- Root `AGENTS.md` records the repository's compatibility policy.
- Root `README.md`, under “Explore further,” gives the consumer-facing summary.
