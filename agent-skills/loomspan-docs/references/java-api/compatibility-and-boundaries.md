---
audience: loomspan-application-developer
status: development
applies_to: bundled-loomspan-revision
coverage: source-verified
---

# Java API Compatibility and Boundaries

## Supported Surface

Application code MAY depend on the eighteen types in the closed
`ai.loomspan.api` allowlist:

| Type | Supported purpose |
| --- | --- |
| `SkillTemplate` | Validate or invoke a named Java, REST, or model-backed YAML skill |
| `SkillInvocationHandoff` | Atomically transfer a prepared root invocation to framework ownership |
| `AdmittedSkillInvocation` | Execute once or release an already-admitted root invocation |
| `SkillReloader` | Prepare, publish, or snapshot a complete skill generation |
| `PreparedSkillUpdate` | Read a frozen candidate's ID and catalog before publication |
| `SkillCatalog` | Discover an immutable startup, prepared-candidate, or current snapshot of registered skills |
| `SkillDescriptor` | Read a registered skill's public metadata and exact tool schema |
| `SkillKind` | Distinguish YAML, Java, and REST registrations |
| `SkillExecutionView` | Observe the completed invocation session |
| `SkillExecutionEvent` | Read a current-version diagnostic event |
| `SkillMethod` | Mark an application bean method as a directly callable Java skill |
| `SkillParam` | Describe and declare requiredness for a Java skill parameter |
| `SkillException` | Catch a safe Loomspan facade failure |
| `SkillReloadException` | Catch an operational preparation or publication failure |
| `SkillInputValidationException` | Distinguish invalid caller input |
| `SkillInputValidationIssue` | Inspect structured validation issues |
| `RestSkillHandler` | Implement every YAML-declared REST leaf through the sole supported SPI |
| `RestSkillInvocation` | Receive the exact skill name, immutable validated/resolved input, and captured generation ID |

Changes to these types are compatibility-sensitive. Application code SHOULD
still use the narrowest type needed: inject or mock `SkillTemplate`, use the
annotations on application-owned beans, and consume the public value records.

## Compatibility Changes in This Revision

Two-stage updates deliberately add `SkillReloader`, `PreparedSkillUpdate`, and
`SkillReloadException` to the supported API. `SkillCatalog` now requires
`generationId()`, and `RestSkillInvocation` is a three-component record with
`generationId` alongside name and input. Existing application implementations
of `SkillCatalog` and direct two-argument `RestSkillInvocation` construction
must be updated and recompiled; there is no compatibility method or constructor.
The ID is process-local and supplied by Loomspan for handler calls, not inferred
from business input or the current active catalog. See [two-stage skill updates](skill-reload.md).

The handoff facade and handle are additive and leave every `SkillTemplate`
signature unchanged. The three catalog types are additive. The two `SkillTemplate.validate`
methods are an intentional pre-1.0 source- and binary-sensitive interface
change: application implementations and hand-written fakes or mocks of
`SkillTemplate` MUST add both methods and recompile. There is no default-method
compatibility shim.

Observers passed to `SkillTemplate.invoke` now receive a callback for mappable
post-session failures as well as successes. Applications upgrading from the
earlier success-only behavior SHOULD make observer handling idempotent and
MUST apply the same security and retention policy to both outcomes. There is no
success-only compatibility mode.

## Unsupported Dependencies

Application code MUST NOT treat these areas as supported extension API:

- `ai.loomspan.internal`, including public classes, constructors,
  methods, records, and interfaces;
- `ai.loomspan.autoconfigure`, whose public types exist for Spring
  Boot integration and configuration binding;
- internal Spring beans such as registries, routers, resolvers, coordinators,
  model factories, or virtual-file-system components;
- bean names or method signatures as alternative skill invocation identities.

Loomspan exposes only `RestSkillHandler` as a supported Java SPI and no supported contract for
replacing framework beans. Do not recommend an internal type merely because it
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
through a local OpenAI-compatible endpoint, permits application Java and REST
leaf calls, directly invokes the REST leaf, and receives a public observation
view.

## Architecture Invariants

`LoomspanPublicSurfaceArchitectureTest` protects these boundaries:

- the `api` package has exactly the eighteen allowlisted public top-level types;
- the separately classified `autoconfigure` types are framework integration,
  not application API;
- every externally accessible Loomspan top-level type is classified;
- the only supported SPI is `RestSkillHandler`; no separate `spi` package exists;
- supported public signatures do not expose `internal` or `autoconfigure`
  types.

When changing Loomspan production types, run this architecture test. New
application-facing API must be a deliberate project decision: place it in
`ai.loomspan.api`, add it to the closed allowlist, document it in the
root README and this knowledge set, and add supported-surface tests.

REST is also a semantic expansion of the framework-to-Console protocol in
beta 4 even though the JSON record shape did not gain a separate version
field. Framework and Console therefore retain the coordinated exact project
version as their compatibility marker; there is no independent schema counter,
range negotiation, legacy reader, or snapshot-to-snapshot compatibility rule.
Only dual `development` markers bypass the version promise while retaining
ordinary complete validation.

## Source Anchors

- `LoomspanPublicSurfaceArchitectureTest` defines the executable classification.
- `ai/loomspan/api/package-info.java` states the supported package boundary.
- Root `AGENTS.md` records the repository's compatibility policy.
- Root `README.md`, under “Invoking a skill,” gives the consumer-facing summary.
