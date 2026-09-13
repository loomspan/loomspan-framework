# REST Skills and Console Integration Implementation Plan

## Overview

Implement the ticketed REST leaf-skill authoring mode as a third registered capability kind and a second direct-execution source. An embedded Spring application will provide exactly one `RestSkillHandler` when REST manifests exist; the framework will validate and resolve each invocation through the existing lifecycle, hand the handler a deeply immutable `RestSkillInvocation`, and expose the distinct REST kind coherently through the Java observability producer, Go Console adapter, MCP tools, TypeScript contracts, React views, and canonical fixtures.

The change is intentionally atomic across framework and Console. The current ticket is binding. Roadmap, phase, readiness, and later-ticket material may clarify sequencing, but does not move PR 5.3 catalog/pre-check/failure-observation work, PR 5.4 cumulative guidance, Sidecar transports, or release activity into this unit.

## Current State Analysis

The shared registry currently supports only `YAML_SKILL` and `JAVA_SKILL`. Model-backed YAML definitions always require a resolved execution configuration, while Java methods register a direct invoker. `ExecutionCoordinator` treats only Java as direct and routes every YAML capability through model execution (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/ExecutionCoordinator.java:86`, `:135`). Authorization, reference resolution, mission timeouts, write fencing, trace finalization, nested tool accounting, and task/evidence credit already surround that authoring-specific branch and should remain the single owners of those concerns.

The public API is a closed eight-type allowlist and explicitly has no SPI today (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java:29`; `README.md:163`). `SkillExecutionEvent` provides a nearby deep-copy precedent, but its diagnostic leaf restrictions are not appropriate for REST invocation inputs because resolved Spring `Resource` handles must remain valid (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/api/SkillExecutionEvent.java:20`). Generic input validation and `BoundCapability` copy only the outer map in some paths, so the new public record must itself own the recursive map/list snapshot.

`YamlSkillCatalog` parses a raw tree before typed binding, then requires `model` and constructs model configuration for every manifest (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java:158`, `:236`). `YamlSkillManifest.Field` records declared execution fields, which is the correct mechanism for rejecting forbidden REST fields even when their values are null or empty (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skill/YamlSkillManifest.java:28`). `YamlSkillDefinition` already stores a nullable execution configuration but currently rejects null and exposes a model-only assertion accessor (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skill/YamlSkillDefinition.java:14`, `:133`). Registration completes Java discovery, adds all YAML definitions to the shared exact-name registry, and only then validates child references (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skill/YamlSkillCapabilityRegistrar.java:29`). That eager completion boundary is also the correct owner for conditional handler cardinality.

Registered-skill observability is currently a binary YAML/Java contract. The Java catalog labels every non-YAML capability as Java (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/runtime/observation/catalog/DefaultRegisteredSkillCatalog.java:35`), and `RegisteredSkillEntry`, Go DTO/service validation, MCP output, TypeScript unions, and React components encode the same two variants. Canonical Java-generated REST fixtures are consumed downstream, so the producer and all consumers must move together.

## Desired End State

- `RestSkillHandler` and `RestSkillInvocation` are the only new supported application-facing types. Their signatures contain JDK types only, the record rejects null `skillName`/`input`, and direct or runtime construction produces an unmodifiable recursive snapshot of map/list containers while preserving null values and leaf identities such as resolved `Resource` handles.
- A manifest is REST-backed only when it declares `rest: true`. Any other declared value fails startup with resource, skill, and field context plus omit-the-field guidance. REST manifests allow only `name`, `description`, `rest`, optional `input_schema`, and optional `rbac_roles`; every ticketed execution field, including `output_schema`, is forbidden by declaration presence. Model-backed manifests still require `model` and retain current validation.
- REST definitions register as `REST_SKILL` with `SkillExecutionDescriptor.none()`, YAML role policy, the existing input contract, YAML declaration location, and a real handler invoker. Exactly one handler bean is resolved only when at least one REST manifest exists; zero and multiple handlers fail with the required complete diagnostics.
- Root and nested REST calls traverse the existing router/coordinator/direct lifecycle. The handler receives validated, reference-resolved input under the captured caller authentication. REST runtime failures remain visible to the facade; access denial is unchanged, existing `SkillException` instances are preserved, other runtime failures are safely wrapped, null results fail with `handler returned null`, and empty strings succeed.
- REST list/detail payloads expose source `REST`, manifest path, and manifest text without handler internals or invented targets. Java, Go, MCP, TypeScript, React, and fixtures agree under the existing exact-project-version beta 4 policy. Runtime traces retain the existing Java-shaped direct frames and do not gain a kind field or model attempts merely for labeling.
- The root README and matching checkout `agent-skills/loomspan-docs` package explain the manifest, SPI, immutable input, authentication, authorization, return/failure, observation, and compatibility semantics with focused executable evidence.

### Key Discoveries

- `CapabilityMetadata` already separates kind, invoker, execution descriptor, access policy, input contract, tool descriptor, and `SkillSource`; REST needs no parallel registry or source subtype (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/CapabilityMetadata.java`; `SkillSource.java`).
- `SkillExecutionDescriptor.none()` is the existing representation for direct capabilities and should be reused (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/SkillExecutionDescriptor.java:16`).
- `DefaultSkillTemplate` preserves `AccessDeniedException` and an existing `SkillException`, wraps other runtime failures, and currently stringifies null results, so null rejection belongs in the REST invoker before facade conversion (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skillapi/DefaultSkillTemplate.java:91`, `:145`).
- `ScopedAuthentication` saves and restores the exact previous security context, and the direct coordinator branch resolves references before invoking the capability (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/security/ScopedAuthentication.java:19`; `ExecutionCoordinator.java:135`).
- `DefaultCapabilityInvoker` grants task/evidence credit only after successful nested return and records failures without success credit, so REST must reuse it rather than introduce REST-specific accounting (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/runtime/tool/DefaultCapabilityInvoker.java:57`, `:135`).
- `ConsoleRestFixtureCorpusTest` owns the byte-identical Java-to-Go REST corpus; its current skill fixture map has only YAML and Java details (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/observability/web/ConsoleRestFixtureCorpusTest.java:40`, `:137`).
- The checkout is `1.0.0-beta.4-SNAPSHOT`, while the installed `loomspan-docs` skill metadata is `0.1.0-SNAPSHOT`. Version-sensitive decisions therefore use checked-out source/tests and `agent-skills/loomspan-docs`; the installed skill was used only for routing. The matching checkout documentation and executable source are aligned on the pre-change YAML/Java-only, no-SPI state.

## What We're NOT Doing

- No HTTP client, framework-owned/default handler, URL/header/credential manifest fields, routing vocabulary, handler target diagnostics, output mapping/schema, retry, repair, sanitization guarantee, or token exchange.
- No per-skill or multiple-handler selection and no dependency on a future public catalog.
- No implicit parent-input merge, context bag, new trusted-metadata parameters, or Sidecar JSON-only restriction on the SPI.
- No third execution engine, model interaction, synthetic plan, trace-frame kind field, kind inference from the current catalog, or change to Java reflection-adapter exception behavior.
- No PR 5.3 consumer catalog/pre-check/failure-observation feature, PR 5.4 cumulative release guidance, Sidecar route/transport/JWT work, catalog reload/versioning, or release action.
- No schema counter, compatibility range, legacy reader, migration, fallback, historical catalog, cross-version fixture, or shim for internal/autoconfigure signatures.

## Skill-Authoring Documentation Impact

**Impact**: Affected

- **Rationale**: REST is a new YAML authoring mode with a strict declaration matrix, handler availability rule, direct lifecycle, YAML role policy, input/reference semantics, and distinct return/failure behavior. Authors and embedded application developers must understand both the manifest contract and the new supported handler SPI.
- **Documents to update**: add focused routed topics `agent-skills/loomspan-docs/references/skill-authoring/rest-skills.md` and `agent-skills/loomspan-docs/references/java-api/rest-skills.md`; update both knowledge-set `README.md` routing/coverage tables, `skill-authoring/mental-model.md`, `skill-authoring/authorization.md`, `skill-authoring/input-contracts.md`, `java-api/compatibility-and-boundaries.md`, `java-api/invocation.md`, `java-api/observation-and-errors.md`, and root `README.md`.
- **Supporting evidence**: `YamlSkillCatalogTests`, `YamlSkillDefinitionTest`, `YamlSkillCapabilityRegistrarTests`, `ApplicationApiValueTest`, `LoomspanPublicSurfaceArchitectureTest`, `DefaultSkillTemplateTest`, `ExecutionCoordinatorTest`, `JavaSkillAuthenticationScopeIntegrationTests`, `SupportedSurfaceIntegrationTest`, registered-skill DTO/catalog tests, canonical fixtures, and Console Go/React/MCP tests.
- **Coverage table update**: Required. The current skill-authoring table marks the YAML manifest reference as undocumented and the Java API table marks framework SPI unsupported; both statements change materially. Route REST manifest authors and handler implementers to focused topics instead of duplicating the complete matrix across broad documents.
- **LLM-first usability**: The new authoring topic will lead with applicability and an exact allowed/forbidden field table, distinguish enforced runtime rules from recommendations and limitations, and link to authorization/input topics. The Java API topic will separately own handler bean, immutable handoff, authentication, return, and exception semantics. Broad indexes and mental-model pages will remain routing summaries.
- **Drift classification**: **aligned (pre-change)**. Matching checkout docs accurately describe the current YAML/Java-only implementation and lack of SPI. The ticket creates an intentional atomic behavior-and-documentation delta; it is not evidence of a pre-existing defect. After implementation, focused tests and updated coverage tables must restore alignment.

## Contract and Compatibility Impact

| Surface | Impact and supporting evidence | Planned compatibility treatment |
| --- | --- | --- |
| Application API | Existing `SkillTemplate`, observations, annotations, and exceptions retain signatures and semantics; public invocation now accepts REST registered names through the same facade. | Preserve all existing entry points and behavior. |
| Supported SPI | Add `RestSkillHandler` and `RestSkillInvocation` in `ai.loomspan.api`, explicitly authorized by the ticket and closed allowlist. | Intentional narrow expansion; protect exact signatures, immutability, and supported-surface use. No other bean replacement becomes supported. |
| Configuration and manifest contracts | Add exact `rest: true` syntax, strict field matrix, conditional exactly-one-handler rule, YAML roles, generic/explicit input contracts, and cross-kind child/name validation. | Atomic explicit extension. Preserve all non-REST YAML validation. Reject obsolete/invalid REST shapes rather than supporting dual behavior. |
| Persisted or serialized contracts | No new cross-version durable format. Complete canonical traces retain the existing exact-version portability rule. | Preserve exact matching version and `development` behavior; no migration or legacy reader. |
| Ephemeral diagnostic formats | Registered-skill list/detail gains distinct REST semantics using existing path/YAML fields. Direct runtime frames remain kind-free and Java-shaped; bounded failure/redaction behavior remains current-version coherent. | Update Java writer, Go/MCP/TypeScript/React readers, tests, and fixtures atomically; do not add speculative fields. |
| Internal or accidentally exposed implementation | `CapabilityKind`, YAML manifest/definition/catalog/registrar, direct dispatch, auto-configuration, registry Javadoc, and unused normalization helpers change. | Update/remove atomically without compatibility constructors, overloads, aliases, or bean replacement contracts. |

- **Evidence of supported contracts**: the binding ticket and Pipeline notes authorize the two SPI types; `LoomspanPublicSurfaceArchitectureTest` and root `AGENTS.md` define the closed public boundary; README and matching knowledge-set docs define existing application behavior; manifest tests and author documentation protect YAML semantics.
- **Intentional compatibility changes**: the first supported SPI is added; REST handler cardinality applies only when REST manifests exist; REST exceptions bypass Java's exception-to-text adapter; REST expands same-version Console protocol semantics. Each is explicitly authorized by the ticket/Pipeline notes.
- **In-repository consumers to update**: public API/package docs, architecture/value/integration tests, manifest/definition/catalog/registrar tests and fixtures, auto-configuration tests, coordinator/facade/security/nested-lifecycle tests, observability records/mappers/catalog/tests, canonical REST fixtures, Go DTO/service/MCP code and goldens, TypeScript unions/API fixtures, React catalog/detail/trace tests, README, and both documentation knowledge sets.
- **Public-surface delta**: exactly two new top-level public types: functional interface `RestSkillHandler` with `String handle(RestSkillInvocation)` and record `RestSkillInvocation(String, Map<String,Object>)`. No existing signature, constructor, Spring extension point, or package boundary changes.
- **Shim decision**: **No shim.** Existing application API remains intact; the new SPI is additive. All changed registry, manifest-definition, auto-configuration, observability DTO, and Console signatures are internal/current-version contracts that can move atomically. The ticket explicitly rejects legacy readers and internal compatibility machinery.
- **Java-to-Go boundary coordination**: **Required.** The Java registered-skill producer, `application-rest` fixture corpus (including `skill-rest-detail.json`), Go wire validation/service/MCP translation, TypeScript unions, React rendering, and goldens must ship together. Existing exact release-marker rejection and `development` validation tests remain authoritative; no separate version counter is introduced.
- **Pipeline notes alignment**: **Aligned.** The plan confines the supported expansion to the named SPI, retains REST-only bean validation and REST-visible exceptions, and applies the matching beta 4 framework/Console policy without a backward reader.

## Implementation Approach

Build the change in executable slices that establish the public and manifest contracts first, then registration and direct execution, then observability and Console consumers, and finally the supported-surface fixture and documentation. Keep capability kind as the execution-source authority and `SkillSource` as declaration location. Resolve the handler lazily by bean name only after the completed catalog proves REST manifests exist, then capture that single application bean in REST capability invokers. Use one private recursive snapshot implementation owned by `RestSkillInvocation`; copy map/list containers and preserve permitted nulls and non-container leaves, including `Resource` handles.

The direct runtime branch should be generalized by an explicit directly-invocable predicate (`JAVA_SKILL` or `REST_SKILL`), not by treating every non-YAML future kind as direct. All YAML-only definition access stays guarded by `YAML_SKILL`. Registered-skill mapping likewise uses an exhaustive kind switch so a future kind cannot silently become Java.

## Phase 1: Establish the Supported SPI and REST Manifest Contract

### Overview

Add the only two public types and make the YAML loader represent and validate REST declarations without weakening model-backed manifests.

### Changes Required

#### 1. Public REST handoff types and boundary tests

**Files**:

- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/api/RestSkillHandler.java` (new)
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/api/RestSkillInvocation.java` (new)
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/api/package-info.java`
- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/api/ApplicationApiValueTest.java`
- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java`

**Changes**:

- Add the exact ticketed functional-interface and record signatures with JDK-only exposed types.
- In the record compact constructor, reject null components and recursively detach/freeze every map/list container. Preserve null values and arbitrary non-container leaf identities; specifically do not read, serialize, or claim immutability for Spring `Resource` contents.
- Extend exact top-level type, signature, constructor/accessor, and classification allowlists while retaining the prohibition on leaked internal/autoconfigure types and unintended public extension points.
- Add value tests for outer/nested mutation resistance, unmodifiable returned containers, null values, leaf identity, null component rejection, and direct application construction.

#### 2. Manifest representation and definition invariants

**Files**:

- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skill/YamlSkillManifest.java`
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skill/YamlSkillDefinition.java`
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java`
- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/skill/YamlSkillCatalogTests.java`
- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/skill/YamlSkillDefinitionTest.java`
- relevant manifest fixtures under `loomspan-spring-boot-starter/src/test/resources`

**Changes**:

- Add declared-field tracking for `rest` and retain raw-tree access long enough to distinguish omission from false, null, string, numeric, collection, or object values. Accept only literal boolean true; produce resource/skill/`rest` diagnostics telling authors to omit the field for non-REST skills.
- Enforce REST's complete whitelist by declared presence, not resolved value: reject `model`, `prompt`, `thinking_level`, `allowed_skills`, `planning_mode`, `concurrency`, `max_steps`, `linter`, `output_schema`, and `output_schema_max_retries` even when null or empty. Retain required name/description and existing schema/RBAC validation; omission of `input_schema` continues to mean the generic object contract.
- Branch definition construction before model resolution. REST definitions carry no execution configuration, evidence, linter, output schema, or child constraints; model definitions still require and resolve model execution exactly as today.
- Make the definition invariant kind-aware: null execution configuration is valid only for `rest: true`; `requireExecutionConfiguration()` remains the model-only assertion boundary.
- Remove unused `normalizeStringListMap`/`normalizeStringList` methods with these owning edits.

### Success Criteria

#### Automated Verification

- [x] Public values and architecture pass: `.\mvnw.cmd -pl loomspan-spring-boot-starter -Dtest=ApplicationApiValueTest,LoomspanPublicSurfaceArchitectureTest -DfailIfNoTests=false test`
- [x] Manifest and definition tests pass: `.\mvnw.cmd -pl loomspan-spring-boot-starter -Dtest=YamlSkillCatalogTests,YamlSkillDefinitionTest -DfailIfNoTests=false test`
- [x] Tests cover every invalid `rest` value and each forbidden field with null/empty presence as applicable, plus unchanged non-REST model requirements.

---

## Phase 2: Register REST Capabilities and Enforce Handler Cardinality

### Overview

Resolve the optional application handler at eager registration completion and add REST capabilities to the shared exact-name namespace.

### Changes Required

#### 1. Capability kind, registrar, handler lookup, and auto-configuration

**Files**:

- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/CapabilityKind.java`
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/CapabilityRegistry.java`
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skill/YamlSkillCapabilityRegistrar.java`
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/autoconfigure/LoomspanAutoConfiguration.java`
- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/CapabilityMetadataTest.java`
- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/skill/YamlSkillCapabilityRegistrarTests.java`
- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/autoconfigure/LoomspanAutoConfigurationTests.java`

**Changes**:

- Add `REST_SKILL`; update kind tests to assert the intentional three-kind internal set.
- Correct registry Javadoc to describe the internal shared capability registry and remove the stale YAML-only/public claim.
- Pass a lazy bean-discovery facility into the registrar. After Java discovery and catalog load, inspect the REST definition set. If it is empty, do not resolve or validate `RestSkillHandler` beans. Otherwise enumerate deterministic bean names, fail zero with every REST manifest resource, fail multiple with every handler bean name, and resolve the sole bean.
- Register REST definitions with `SkillExecutionDescriptor.none()`, YAML roles, YAML path-only `SkillSource`, the existing resolved YAML input contract and tool schema, and an invoker that constructs `RestSkillInvocation`, delegates once, rejects null with `handler returned null`, and returns empty/nonempty strings unchanged.
- Keep model YAML registration unchanged and validate child references after every kind is registered. Rely on the existing registry collision exception so REST/YAML, REST/Java, and REST/REST conflicts report both declarations.
- Update internal constructor call sites atomically; do not add compatibility overloads or conditionally replaceable internal beans.

#### 2. Registration-focused tests

**Files**:

- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/skill/YamlSkillCapabilityRegistrarTests.java`
- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/autoconfigure/LoomspanAutoConfigurationTests.java`

**Changes**:

- Add valid registration assertions for REST kind, no execution configuration, YAML roles, generic/explicit input contract, source path, and handler delegation.
- Add zero/multiple handler diagnostics, verify handler beans are irrelevant with no REST manifests, and verify registration remains idempotent.
- Add all cross-kind collision directions and REST/REST collision assertions with both locations.
- Add YAML-parent-to-REST child resolution and preserve unknown-child startup failure.

### Success Criteria

#### Automated Verification

- [x] Registrar, metadata, and auto-configuration tests pass: `.\mvnw.cmd -pl loomspan-spring-boot-starter -Dtest=CapabilityMetadataTest,YamlSkillCapabilityRegistrarTests,LoomspanAutoConfigurationTests -DfailIfNoTests=false test`
- [x] A no-REST application starts with zero, one, or multiple handler beans without resolving or validating them.
- [x] Every handler-count and collision failure contains the complete deterministic locations required by the ticket.

---

## Phase 3: Execute REST Through the Shared Direct Lifecycle

### Overview

Generalize direct dispatch without changing the surrounding lifecycle or Java's established adapter semantics.

### Changes Required

#### 1. Direct coordinator dispatch and facade outcomes

**Files**:

- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/ExecutionCoordinator.java`
- REST invoker implementation colocated with registration under `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skill/` if a named class is clearer than a registrar lambda
- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/ExecutionCoordinatorTest.java`
- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/skillapi/DefaultSkillTemplateTest.java`

**Changes**:

- Select YAML definitions/model execution only for `YAML_SKILL`; explicitly route both Java and REST through the current direct branch. Keep authorization, `MissionWorkExecutor`, scoped authentication, reference resolution, mission frames, failure capture, trace finalization, timeouts, cancellation, quotas, depth, shutdown admission, and late-write fencing in their existing owners.
- Ensure the REST handler receives the resolved input snapshot on the actual execution thread and no raw input, session/trace identifiers, description, authentication parameter, or context bag.
- Do not use `SkillMethodBeanPostProcessor`'s exception-to-text adapter. Let handler runtime failures reach the coordinator/facade. Preserve facade handling of `AccessDeniedException`, existing `SkillException`, other runtime failures, JVM errors, and observer timing.
- Assert direct REST frames use the skill name and contain no model-attempt records or new kind field; retain bounded failure stack/message capture and existing redaction.

#### 2. Authentication, nesting, credit, and concurrency integration

**Files**:

- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/security/JavaSkillAuthenticationScopeIntegrationTests.java` (rename/generalize only if the broader ownership is clearer)
- focused nested/direct lifecycle tests adjacent to `ExecutionCoordinatorTest` and `DefaultCapabilityInvoker` tests
- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/integration/SupportedSurfaceIntegrationTest.java` (final public proof completed in Phase 5)

**Changes**:

- Exercise root and nested REST calls under authorized and denied callers, assert handler-visible `SecurityContextHolder` authentication, and verify exact prior context restoration after success and failure on concurrent workers.
- Exercise required-child/task/evidence credit only after success. Assert denial, cancellation, null return, and runtime failure earn none.
- Extend existing sequential/concurrent parent cases rather than constructing a parallel harness. Preserve primary failure ordering, sibling/join behavior, mission timeouts, shutdown cancellation, depth/quota checks, and write fences through REST direct calls.

### Success Criteria

#### Automated Verification

- [x] Coordinator, facade, security, and nested-tool focused tests pass: `.\mvnw.cmd -pl loomspan-spring-boot-starter -Dtest=ExecutionCoordinatorTest,DefaultSkillTemplateTest,JavaSkillAuthenticationScopeIntegrationTests -DfailIfNoTests=false test`
- [x] Root and nested success, denial, cancellation, null, empty string, existing `SkillException`, ordinary runtime failure, and trace outcomes match the ticket exactly.
- [x] Existing Java exception-to-text tests remain unchanged and passing.

---

## Phase 4: Extend Java Observability and Canonical REST Fixtures

### Overview

Make REST a first-class registered-skill source while retaining manifest-backed declaration data and current direct trace semantics.

### Changes Required

#### 1. Registered-skill producer and DTO invariants

**Files**:

- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/runtime/observation/catalog/DefaultRegisteredSkillCatalog.java`
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/runtime/observation/catalog/RegisteredSkillEntry.java`
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/observability/web/ObservabilityDtoMapper.java`
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/observability/web/dto/ObservabilityDtos.java`
- corresponding catalog/DTO/web integration tests

**Changes**:

- Replace binary non-YAML-is-Java branches with exhaustive YAML/REST/JAVA handling.
- Treat REST as manifest-backed: source `REST`, nonblank source path, manifest YAML detail, and no bean/method. Treat Java as bean/method-only. Enforce inapplicable fields even when supplied empty.
- Do not expose handler bean names, class/method details, URLs, headers, credentials, or targets for REST.
- Verify list sorting/pagination/detail lookup and existing direct trace rendering continue without a trace kind discriminator or model attempts.

#### 2. Canonical fixture corpus

**Files**:

- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/observability/web/ConsoleRestFixtureCorpusTest.java`
- `loomspan-console-fixtures/application-rest/skills-page.json`
- `loomspan-console-fixtures/application-rest/skill-rest-detail.json` (new)
- affected mirrored browser/MCP fixtures only where owned by their consumer tests

**Changes**:

- Add a canonical REST entry to the skill page and a REST detail payload containing source path and unchanged manifest text.
- Regenerate with the owning Java fixture test, then repeat regeneration and verify byte identity/no second change. Do not regenerate trace/analysis corpora unless executable content actually changes.
- Keep `consoleCompatibilityVersion` derived from `${project.version}` and the current beta 4 pin; do not add a schema version.

### Success Criteria

#### Automated Verification

- [x] Catalog/DTO tests pass: `.\mvnw.cmd -pl loomspan-spring-boot-starter -Dtest=DefaultRegisteredSkillCatalogTest,ObservabilityDtoMapperTest,ConsoleRestFixtureCorpusTest -DfailIfNoTests=false test`
- [x] Fixture regeneration succeeds: `.\mvnw.cmd -pl loomspan-spring-boot-starter -Dtest=ConsoleRestFixtureCorpusTest -Dloomspan.console.fixtures.regenerate=true -DfailIfNoTests=false test`
- [x] A second identical regeneration changes no fixture bytes, and a normal fixture check then passes.

---

## Phase 5: Update Console Protocol Consumers and Rendering Atomically

### Overview

Teach every Go, MCP, TypeScript, and React consumer the REST variant before the protocol unit lands.

### Changes Required

#### 1. Go wire decoding, service invariants, and browser API flow

**Files**:

- `loomspan-console/internal/observability/dto.go`
- `loomspan-console/internal/observability/dto_test.go`
- `loomspan-console/internal/observability/service.go`
- `loomspan-console/internal/observability/service_test.go`
- affected `loomspan-console/internal/browserapi` tests/fixtures

**Changes**:

- Accept exactly `YAML`, `REST`, or `JAVA`. Apply manifest-backed path/YAML requirements to YAML and REST while preserving REST as a distinct source string; keep Java bean/method-only.
- Reject unknown sources and source-inapplicable fields even when empty at raw decode and service validation.
- Consume the new canonical Java fixture and prove REST list/detail transport through browser-facing APIs without altering scope/cursor semantics.

#### 2. MCP skills tools and goldens

**Files**:

- `loomspan-console/internal/mcpadapter/skills.go`
- `loomspan-console/internal/mcpadapter/skills_test.go`
- `loomspan-console/internal/mcpadapter/contracts.go`
- `loomspan-console/internal/mcpadapter/contracts_test.go`
- `loomspan-console/internal/mcpadapter/testdata/tools-list-response.json`
- `loomspan-console/internal/mcpadapter/testdata/skills-list.json`
- `loomspan-console/internal/mcpadapter/testdata/skill-detail.json`

**Changes**:

- Render REST label/source path and unchanged manifest text like a manifest-backed declaration while retaining the distinct kind.
- Update tool descriptions/schema enums and golden outputs; never expose handler internals or invent a target.

#### 3. TypeScript contracts, React catalog/detail, and trace regression

**Files**:

- `loomspan-console/web/src/api/contracts.ts`
- `loomspan-console/web/src/observability/SkillCatalog.tsx`
- `loomspan-console/web/src/observability/SkillCatalog.test.tsx`
- `loomspan-console/web/src/observability/SkillDetail.tsx`
- `loomspan-console/web/src/observability/SkillDetail.test.tsx`
- affected API fixture tests and `loomspan-console/web/e2e` scenarios

**Changes**:

- Add REST members to the discriminated summary/detail unions with source path and YAML text, and no bean/method fields.
- Render label `REST`, source path, and manifest text in list/detail. Keep Java and YAML labels/content unchanged.
- Add a browser/e2e path that loads a REST skill and opens its detail and a REST direct trace without misclassification or rendering error. Assert no handler implementation details or invented endpoint text appear.

#### 4. Version-policy regression

**Files**:

- existing `loomspan-console/internal/applicationclient/client_test.go`
- existing `loomspan-console/internal/traceanalysis/processor_test.go`
- version/build declarations only if generated verification requires synchronized output

**Changes**:

- Preserve tests rejecting resolved missing/non-string/blank/unequal markers and accepting exact matches.
- Preserve ordinary complete validation when both sides are `development` without promising compatibility. Record in nearby contract documentation/tests that the new REST semantic kind is why matching beta 4 artifacts must ship together even where JSON field names are unchanged.

### Success Criteria

#### Automated Verification

- [x] All Console Go tests pass from `loomspan-console`: `go test ./...`
- [x] Console declaration, generated asset, TypeScript, React, and e2e checks pass: `go run ./internal/buildtool verify`
- [x] REST list/detail/trace UI and MCP goldens identify REST distinctly and expose only manifest-backed diagnostic location/text.
- [x] Exact-version and `development` compatibility tests retain their current behavior.

---

## Phase 6: Complete Supported-Surface Proof and Version-Aligned Guidance

### Overview

Demonstrate the feature through supported APIs and publish evidence-backed author/application guidance in the same unit.

### Changes Required

#### 1. Existing supported-surface integration fixture

**Files**:

- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/integration/SupportedSurfaceIntegrationTest.java`
- its existing local protocol-compatible model stub and YAML resources

**Changes**:

- Extend the existing harness with a `RestSkillHandler` application bean, REST manifest, YAML planner that can call the REST child, and existing Java leaf; do not introduce another integration harness.
- Invoke root and nested paths only through `SkillTemplate` and public types. Assert validated/resolved immutable handler input, authorized/denied execution, public observation shape, REST/Java child success, and no dependency on internal types.

#### 2. Root and version-aligned documentation

**Files**:

- `README.md`
- `agent-skills/loomspan-docs/references/skill-authoring/README.md`
- `agent-skills/loomspan-docs/references/skill-authoring/rest-skills.md` (new)
- `agent-skills/loomspan-docs/references/skill-authoring/mental-model.md`
- `agent-skills/loomspan-docs/references/skill-authoring/authorization.md`
- `agent-skills/loomspan-docs/references/skill-authoring/input-contracts.md`
- `agent-skills/loomspan-docs/references/java-api/README.md`
- `agent-skills/loomspan-docs/references/java-api/rest-skills.md` (new)
- `agent-skills/loomspan-docs/references/java-api/compatibility-and-boundaries.md`
- `agent-skills/loomspan-docs/references/java-api/invocation.md`
- `agent-skills/loomspan-docs/references/java-api/observation-and-errors.md`

**Changes**:

- Document the exact ten-type supported surface and identify the handler as the sole supported SPI without implying framework bean replacement.
- Add an exact REST manifest field table and minimal example. Document handler cardinality, input validation/reference resolution and deep snapshot, explicit business input, Resource limitation, scoped authentication, YAML roles, null/empty results, REST-versus-Java exception distinction, direct observation/trace shape, and same-version Console diagnostics.
- Update routing/coverage tables and link focused topics. Apply the LLM-first standard: keep claims locally actionable, distinguish enforcement/recommendation/limitation, and cite named tests/source anchors.

### Success Criteria

#### Automated Verification

- [x] Supported-surface integration and architecture tests pass: `.\mvnw.cmd -pl loomspan-spring-boot-starter -Dtest=SupportedSurfaceIntegrationTest,LoomspanPublicSurfaceArchitectureTest -DfailIfNoTests=false test`
- [x] Full Java reactor passes: `.\mvnw.cmd test`
- [x] Full Console verification passes from `loomspan-console`: `go test ./...` and `go run ./internal/buildtool verify`
- [x] Every exact authoring claim is backed by a focused source/test/fixture anchor; both knowledge-set coverage tables reflect REST support and satisfy their LLM-first checklist.

## Testing Strategy

### Unit Tests

- Begin with a REST manifest loading test that currently fails because `rest` is unknown/model is required, then build the public value, exact field-matrix, definition invariant, handler-cardinality, collision, invoker/facade, observability DTO, Go decoder/service, MCP, and React component coverage described in the dedicated testing plan.
- Preserve regression tests for model-backed YAML validation, Java exception-to-text behavior, exact-name identity, authorization, input schema validation, null/empty semantics, immutable values, source-inapplicable wire fields, and exact release markers.

### Integration Tests

- Extend the existing supported-surface application to prove YAML planner -> REST leaf and Java leaf composition through public APIs, including authorization and observation.
- Exercise REST root/nested lifecycle behavior, security-context restoration, credit/failure/cancellation behavior, and direct traces without model attempts.
- Regenerate and consume the Java-owned REST fixture corpus, then run all Go/React/e2e/buildtool checks.

See `ai/thoughts/plans/2026-09-12-rest-skills-and-console-testing.md` for the complete test inventory and exit criteria.

## Performance Considerations

- Deep copy is linear in the submitted argument graph's map/list contents and occurs once at the public handler handoff. Do not serialize values or traverse Resource contents.
- Handler discovery and cardinality validation occur once at eager registration completion and are skipped when no REST manifests exist.
- REST reuses direct mission execution and adds no model request, retry engine, catalog lookup per handler selection, or additional trace frame.

## Migration Notes

This is an additive pre-1.0 authoring mode and narrow supported SPI. Existing YAML and Java applications require no migration. Framework and Console beta 4 artifacts containing REST catalog semantics must be upgraded together through the existing coordinated project version. There is no legacy reader, schema migration, compatibility alias, or internal constructor shim.

## References

- Original ticket: `ai/thoughts/tickets/loomspan-pr-5.2-rest-skills-and-console.md`
- Research: `ai/thoughts/research/2026-09-12-rest-skills-and-console.md`
- Design lens: `ai/thoughts/framework-feature-design-lens.md`
- Roadmap: `ai/thoughts/phases/beta4-rest-skills-and-sidecar-roadmap.md`
- Framework phases: `ai/thoughts/phases/phase-fw1.md`, `ai/thoughts/phases/phase-fw3.md`
- Future boundary references: PR 5.3 and PR 5.4 tickets under `ai/thoughts/tickets/` (sequencing context only; not scope authority for this plan)
