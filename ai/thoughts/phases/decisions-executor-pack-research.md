# Decisions Executor Pack: provider research and design rationale

Status: dated research supporting the agreed design; not an implemented API.
Research date: 2026-11-15. Document consolidation: 2026-11-16; provider research was not refreshed.

## Document role and decision references

This document owns external evidence and rationale. The
[roadmap](executor-packs-and-typesafe-extraction-roadmap.md) is the sole authority
for agreed decisions, scope, acceptance criteria, and delivery. The
[migration review](typesafe-executor-pack-migration-review.md) owns local code/test findings.

| Topic | Authoritative contract |
| --- | --- |
| Scope, breaking changes, public API | [Roadmap scope](executor-packs-and-typesafe-extraction-roadmap.md#scope-rule-for-implementation-and-future-tickets), [policy](executor-packs-and-typesafe-extraction-roadmap.md#mandatory-breaking-change-policy-for-this-work), [Phase 1](executor-packs-and-typesafe-extraction-roadmap.md#phase-1--finalize-the-reviewed-public-contract-and-removal-inventory) |
| Diagnostics and failure containment | [Phase 2](executor-packs-and-typesafe-extraction-roadmap.md#phase-2--framework-managed-executor-observation) |
| Console/MCP inspection | [Phase 3](executor-packs-and-typesafe-extraction-roadmap.md#phase-3--generic-console-integration) |
| Cancellation and accounting | [Phase 4](executor-packs-and-typesafe-extraction-roadmap.md#phase-4--cooperative-lifecycle-and-explicit-migration-behavior) |
| Packaging, config, transport, results, recipe | [Phase 5](executor-packs-and-typesafe-extraction-roadmap.md#phase-5--prove-the-separate-decisions-executor-pack) |
| Namespaces | [Existing relationship](executor-packs-and-typesafe-extraction-roadmap.md#existing-namespace-relationship-retained) |
| Tickets and completion | [Ownership](executor-packs-and-typesafe-extraction-roadmap.md#settled-test-ownership-and-mandatory-ticket-handoff), [completion gate](executor-packs-and-typesafe-extraction-roadmap.md#v1-completion-gate-implementation-complete) |

## Evidence and limitations

The TypeSafe baseline comes from live documentation reviewed during the original
discussion and the repository implementation. No live accuracy, latency, cost, or
account-availability comparison was run. Documentation examples do not prove equal
calibration or behavior between models. Links below are dated evidence, not current
service guarantees; the roadmap assigns documentation/fixture refresh to implementation.

## Design rationale and discussion history

The initial discussion considered a provider-neutral decisions feature, first in
core and then in a pack. Provider-specific request semantics and native confidence
meanings favored the selected TypeSafe-first design. The image research below also
showed why capabilities cannot be reduced to a permanent provider-wide text-only flag.
The initial concern about losing native visibility/accounting led to separating
diagnostic evidence from enforcement and authoritative accounting. The resulting
decisions live in the roadmap, rather than retaining the earlier parity proposal here.

Versioned Java invocation types make the supported handoff concrete without a separate
manifest. A single contract avoids an independently selected execution/presentation
compatibility matrix. The annotation mechanism is not a general Java SPI.
The earlier execution/evidence/presentation layering remains useful as an ownership
model; it does not require pack-authored UI descriptors or operation handles.

Separate Maven distribution lets provider integration evolve independently. One
artifact avoids premature modules; ordinary POM identity and directory conventions
avoid building a second dependency/discovery platform. A recipe keeps application
decisions separate from reusable Java transport logic. A small documentation index
can support both humans and AI before catalog automation has demonstrated value.
A future central index can aggregate entries without hosting binaries; optional
CLI/Console scaffolding can use the same POM and directory conventions.

Useful implementation references:
[Maven dependency mechanism](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html),
[Spring Boot external auto-configuration](https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html).
The selected repository convention and recipe requirements now live in Phase 5.

## TypeSafe provider baseline

Gemini and OpenAI research and requirements are deferred to their follow-up work.
Their planned inclusion in this extension does not establish contracts for them.

### TypeSafe

The native endpoint is `/v1/systemone`. Instructions and criteria can carry
structured JSON. Current Loomspan supports the text/JSON Choice/Noul/Score subset,
requires object-valued answers, and passes those answers through without enforcing
their descriptive schema. See the [HTTP reference](https://docs.typesafe.ai/api/system-one)
and [current Loomspan guide](../../../agent-skills/loomspan-docs/references/skill-authoring/typesafe-skills.md).

TypeSafe is no longer strictly text-only: images in state, instructions, and
criteria are documented behind `TypeSafe-Danger-Zone-Images: true`. The docs call
this experimental and not production-ready. Jev 1.13/1.14 accept images; Box
localization requires Jev 1.14. Assets use references and uploaded/base64 image
data. Treat this as a model/version/opt-in capability, not universal support.
[Image documentation](https://docs.typesafe.ai/images.md)

Choice confidence adjusts the highest probability relative to an even split;
Score confidence also considers distance across levels. Noul has no separate
confidence. For a two-option distribution of 0.75/0.25, Choice confidence is 0.50.
[Confidence definitions](https://docs.typesafe.ai/confidence)

## Follow-up research: bounded Java-object serialization

Research date: 2026-11-15. This is a source/documentation review, not a benchmark
or proof of hard resource isolation. It does not expand the agreed v1 JSON-only
payload contract or select a new dependency.

| Candidate | Useful existing mechanism | Limit for Loomspan |
| --- | --- | --- |
| Jackson Core | Streaming JSON generation and configurable output nesting depth | Needs additional byte, traversal, and event budgets; does not sandbox arbitrary object code |
| Gson | Ordinary reflective object serialization reads fields rather than bean getters | Circular references are documented as unsupported; custom adapters and collection behavior still need control |
| Datadog Java debugger serializer | Bounded object capture with depth, field/collection/string limits, timeout checks, and known collection handling | Agent-internal implementation with product dependencies, not an established standalone library contract |

Jackson is already a dependency in the root POM (`tools.jackson.core:jackson-databind`).
Its reviewed `StreamWriteConstraints` implementation limits nesting depth; read-side
string/document limits are not object-serialization limits. Instance-local constraints
avoid changing global defaults. A candidate implementation is a
small bounded JSON-value walker feeding Jackson's streaming generator and a capped
output sink; building an unlimited string/tree before truncating would not bound
capture work. This is a technique to validate, not code already
implemented. [Jackson write constraints](https://github.com/FasterXML/jackson-core/blob/3.x/src/main/java/tools/jackson/core/StreamWriteConstraints.java)

Gson is a useful example of avoiding ordinary bean getters through field-based
serialization, but its user guide explicitly warns about circular-reference
recursion. Switching serializers would not itself solve bounded object capture.
[Gson user guide](https://google.github.io/gson/UserGuide.html)

Datadog's `SerializerWithLimits` is a closer example of diagnostic object inspection:
it checks budgets during traversal and distinguishes known collections from arbitrary
implementations. Study those resource-control techniques, not wholesale adoption of
its product-specific policies or agent dependencies. This review did not establish a
supported standalone Maven artifact for that helper.
[Datadog serializer source](https://github.com/DataDog/dd-trace-java/blob/master/dd-java-agent/agent-debugger/src/main/java/com/datadog/debugger/util/SerializerWithLimits.java)

A timeout check cannot interrupt a getter, iterator, custom serializer, or `toString()`
that never returns. Java task cancellation only attempts interruption; a timed-out
wait does not prove the work stopped. Consequently, a worker timeout alone is not
hard CPU/memory isolation. This is an inference from the execution model, not a
claim that all serializers are unsafe.
[Java Future cancellation contract](https://docs.oracle.com/en/java/javase/24/docs/api/java.base/java/util/concurrent/Future.html)

The research supported the bounded-capture choice now specified in
[Phase 2](executor-packs-and-typesafe-extraction-roadmap.md#phase-2--framework-managed-executor-observation).
Even `Map`/`List` interfaces, arbitrary `Number` subclasses, and custom adapters can
execute application code. Predictable traversal therefore depends on concrete
supported containers or a controlled JSON representation, not merely an interface type.
No reviewed library establishes a universal in-process guarantee for arbitrary
objects, and no new library is justified for v1 by this review.
