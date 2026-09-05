---
audience: loomspan-application-developer
status: development
applies_to: bundled-loomspan-revision
coverage: source-verified
---

# Java API Compatibility and Boundaries

## Supported Surface

Application code MAY depend on the eight types in the closed
`ai.loomspan.api` allowlist:

| Type | Supported purpose |
| --- | --- |
| `SkillTemplate` | Invoke a named Java or YAML skill |
| `SkillExecutionView` | Observe the completed invocation session |
| `SkillExecutionEvent` | Read a current-version diagnostic event |
| `SkillMethod` | Mark an application bean method as a directly callable Java skill |
| `SkillParam` | Describe and declare requiredness for a Java skill parameter |
| `SkillException` | Catch a safe Loomspan facade failure |
| `SkillInputValidationException` | Distinguish invalid caller input |
| `SkillInputValidationIssue` | Inspect structured validation issues |

Changes to these types are compatibility-sensitive. Application code SHOULD
still use the narrowest type needed: inject or mock `SkillTemplate`, use the
annotations on application-owned beans, and consume the public value records.

## Unsupported Dependencies

Application code MUST NOT treat these areas as supported extension API:

- `ai.loomspan.internal`, including public classes, constructors,
  methods, records, and interfaces;
- `ai.loomspan.autoconfigure`, whose public types exist for Spring
  Boot integration and configuration binding;
- internal Spring beans such as registries, routers, resolvers, coordinators,
  model factories, or virtual-file-system components;
- bean names or method signatures as alternative skill invocation identities.

Loomspan currently exposes no supported Java SPI and no supported contract for
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
through a local OpenAI-compatible endpoint, permits an application
method call, and receives a public observation view.

## Architecture Invariants

`LoomspanPublicSurfaceArchitectureTest` protects these boundaries:

- the `api` package has exactly the eight allowlisted public top-level types;
- the separately classified `autoconfigure` types are framework integration,
  not application API;
- every externally accessible Loomspan top-level type is classified;
- no Loomspan-specific `spi` package or type exists;
- supported public signatures do not expose `internal` or `autoconfigure`
  types.

When changing Loomspan production types, run this architecture test. New
application-facing API must be a deliberate project decision: place it in
`ai.loomspan.api`, add it to the closed allowlist, document it in the
root README and this knowledge set, and add supported-surface tests.

## Source Anchors

- `LoomspanPublicSurfaceArchitectureTest` defines the executable classification.
- `ai/loomspan/api/package-info.java` states the supported package boundary.
- Root `AGENTS.md` records the repository's compatibility policy.
- Root `README.md`, under “Invoking a skill,” gives the consumer-facing summary.

