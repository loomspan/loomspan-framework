# PR 5.5 — Positive Observability Route Collisions Implementation Plan

## Overview

Change the internal observability route-collision detector so it disables the
Loomspan Console only when available route information positively identifies an
overlap with `/_loomspan/observability/v1/**`. The implementation will inspect
Spring MVC's general `RequestMappingInfoHandlerMapping` family, which includes
Spring Boot 4.1 Actuator's additional-health-path mapping, while preserving the
reserved namespace, Console API-key authentication, and fail-safe behavior for
confirmed application overlaps.

## Current State Analysis

`LoomspanObservabilityWebAutoConfiguration` supplies every `HandlerMapping` bean
to one internal detector before the registrar creates any Console runtime or
route (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/autoconfigure/LoomspanObservabilityWebAutoConfiguration.java:88-113`). The detector currently
enumerates only `RequestMappingHandlerMapping`, `AbstractUrlHandlerMapping`, and
`RouterFunctionMapping`; every other mapping type immediately reports a
collision (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/observability/web/ObservabilityRouteCollisionDetector.java:38-64`).

This makes Spring Boot 4.1's empty
`AdditionalHealthEndpointPathsWebMvcHandlerMapping` disable Console even though
that class belongs to the path-aware `RequestMappingInfoHandlerMapping` family.
The same fail-closed policy also appears in distinct functional-router branches:
unclassifiable predicates, resource lookup callbacks, and unknown router forms
are treated as collisions without a known overlapping path
(`ObservabilityRouteCollisionDetector.java:179-260`, `:334-355`). A leaf
functional route with no path predicate is different: it applies across all
paths and therefore is a positive broad overlap.

The collision gate is upstream of route registration and activation
(`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/observability/web/ObservabilityRouteRegistrar.java:75-133`). Authentication remains independently
enforced by `ObservabilityApiKeyFilter` once activation succeeds
(`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/observability/web/ObservabilityApiKeyFilter.java:42-69`, `:99-146`).

## Desired End State

- All `RequestMappingInfoHandlerMapping` implementations are inspected through
  their standard `RequestMappingInfo` keys, without a Boot class-name or version
  exception.
- Known annotated, handler-method, explicit URL, and functional paths that
  overlap the reserved root or children still disable Console, including exact,
  dynamic, and broad-wildcard forms.
- An empty, unrelated, or non-enumerable custom `HandlerMapping`, an unknown
  functional router form, and a functional resource callback with no exposed
  path information do not create a collision merely because enumeration is
  unavailable.
- A functional leaf route with no path restriction still collides because it
  positively applies to the reserved namespace. A path-unconstrained nested
  predicate acts as an identity prefix so a known unrelated leaf remains
  unrelated.
- A real Boot 4.1 application with Actuator probes on a separate management port
  starts Console; a valid Console key succeeds and a missing key is rejected.
- Confirmed application ownership continues to prevent Loomspan registration,
  and the closed supported Java surface remains unchanged.

Verification consists of the focused detector tests, focused observability
integration tests, the public-surface architecture test, the complete starter
verification, a local `1.0.0-beta.4-SNAPSHOT` install, and the dependent
Sidecar Console/separate-management-port integration test.

### Key Discoveries

- Boot 4.1's additional-health mapping extends
  `RequestMappingInfoHandlerMapping` through
  `AbstractWebMvcEndpointHandlerMapping`, and its empty form exposes an empty
  `getHandlerMethods()` map (research artifact, Detailed Findings 4).
- Existing unit coverage deliberately encodes the obsolete unknown-mapping and
  functional-resource fail-closed behavior, so those assertions must be
  replaced rather than preserved alongside the new policy
  (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/observability/web/ObservabilityRouteCollisionDetectorTest.java:87-115`).
- Existing collision and host-security integrations already protect confirmed
  route ownership and API-key/Spring Security coexistence
  (`ObservabilityCollisionIntegrationTest.java:39-63`,
  `ObservabilityHostSecurityIntegrationTest.java:47-120`).
- The starter uses Spring Boot `4.1.0`, but does not currently have Actuator on
  its test classpath (`pom.xml:53-70`,
  `loomspan-spring-boot-starter/pom.xml:81-109`).

## What We're NOT Doing

- Adding a Boot-version or Actuator-class-name allowlist.
- Adding a public Java API, supported SPI, replaceable Spring bean contract, or
  configuration switch for collision policy.
- Weakening or bypassing the Console API-key filter, changing Spring Security
  guidance, or allowing Loomspan to take over a confirmed application route.
- Guaranteeing detection for an application that hides a reserved-namespace
  claim behind an uninspectable custom mapping; the ticket explicitly leaves
  such deliberate claims unsupported.
- Changing Console REST/SSE payloads, trace data, persistence, configuration
  keys, resource-handler fallback precedence, or Sidecar source code.
- Broadly redesigning functional-router predicate analysis beyond retaining
  positively known path evidence and distinguishing path-unconstrained routes
  from unclassifiable routes.

## Skill-Authoring Documentation Impact

**Impact**: No impact

- **Rationale**: Collision detection is application/operator servlet
  integration behavior. It does not change skill manifests, capability
  visibility, planner semantics, author inputs/outputs, trace interpretation,
  or skill debugging procedures.
- **Documents to update**: None.
- **Supporting evidence**: The behavior is contained in
  `ObservabilityRouteCollisionDetector`, `ObservabilityRouteRegistrar`, and
  observability web integration tests; the version-aligned skill-authoring
  knowledge base contains no collision-detector guidance.
- **Coverage table update**: Not required; no skill-authoring topic changes.
- **LLM-first usability**: Not applicable.

Documentation drift is classified as **possible framework defect**: README
claims already describe disabling on an overlapping application mapping, while
the current implementation additionally disables on unknown empty mappings.
Correcting the code aligns executable behavior with that statement, so no
README or skill-authoring edit is planned.

## Contract and Compatibility Impact

| Surface | Impact and supporting evidence | Planned compatibility treatment |
| --- | --- | --- |
| Application API | No affected type belongs to the closed thirteen-type `ai.loomspan.api` allowlist (`LoomspanPublicSurfaceArchitectureTest.java:29-42`). | Preserve; add no type or signature. |
| Supported SPI | No affected SPI exists; collision detection is framework-owned route infrastructure. | Preserve; add no extension point or bean replacement contract. |
| Configuration and manifest contracts | Existing `loomspan.observability.enabled` opt-in, API-key setting, reserved namespace, and disable-on-confirmed-overlap behavior remain user-visible. No skill manifest behavior changes. | Preserve keys and documented behavior; correct the false-positive activation defect. |
| Persisted or serialized contracts | No persisted state, Console JSON/NDJSON, or compatibility marker is affected. | Preserve unchanged. |
| Ephemeral diagnostic formats | No trace schema is affected. The existing sanitized startup collision warning remains applicable only to confirmed collisions. | Preserve current warning/security behavior; do not add an unknown-mapping warning contract. |
| Internal or accidentally exposed implementation | `ObservabilityRouteCollisionDetector` and registrar are public only for framework-owned composition (`LoomspanPublicSurfaceArchitectureTest.java:53-71`). Detector classification and its tests change. | Replace obsolete internal fail-closed branches atomically; no compatibility shim. |

- **Evidence of supported contracts**: The architecture allowlist establishes
  Java API classification; README sections at `README.md:527-610` establish the
  reserved namespace, API key, overlapping-route behavior, and host-security
  guidance; the controlling ticket expressly preserves those contracts.
- **Intentional compatibility changes**: Unknown or unclassifiable mapping
  implementations no longer disable Console without positive overlap evidence.
  This internal behavioral break is explicitly authorized by the ticket's
  Pipeline note.
- **In-repository consumers to update**:
  `ObservabilityRouteCollisionDetectorTest`, a new Boot/Actuator integration
  test, and the starter test dependency list. Existing collision, security, and
  architecture tests remain unchanged and are rerun as protected regressions.
- **Public-surface delta**: None. No application-facing type, signature,
  constructor, supported SPI, or Spring extension point is added or removed.
- **Shim decision**: **No shim.** The changed branches are unsupported internal
  policy, the ticket requires one coherent positive-overlap rule, and retaining
  the old behavior would reproduce the defect.
- **Java-to-Go boundary coordination**: **Not required.** No Console REST/SSE,
  acquisition, problem, or consumed-NDJSON contract changes. Sidecar is a
  dependent application verification target only.
- **Pipeline notes alignment**: **Aligned.** The only intentional break is the
  narrowly authorized replacement of fail-closed handling for unclassifiable
  mappings; authentication and confirmed collisions remain protected.

## Implementation Approach

Reuse the detector's existing `RequestMappingInfo` and path-overlap analysis,
but bind it to the general Spring MVC handler-method abstraction rather than
the annotation-specific subclass. Replace boolean-by-absence functional path
classification with an explicit distinction between known paths,
path-unconstrained predicates, and unclassifiable predicates. Only known
overlapping paths or a leaf route proven to be path-unconstrained may set the
collision result; unavailable enumeration does not.

For a candidate path string, retain the existing case-insensitive, segment,
wildcard, and parsed-pattern checks. A parse exception alone must no longer be
treated as overlap; any positive lexical namespace/wildcard evidence should be
evaluated independently, and an otherwise unparseable candidate is
unclassifiable rather than colliding.

This is the smallest coherent design because it uses Spring's existing
path-aware hierarchy, avoids reflective inspection and vendor exemptions, and
removes the duplicate implicit authority that equated “unknown” with “route
owner.” No new framework concept or public surface is necessary.

## Phase 1: Encode the Positive-Overlap Contract

### Overview

Change focused tests first so the false positive is reproduced and the
protected collision matrix is explicit before production logic changes.

### Changes Required

#### 1. Detector behavior matrix

**File**: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/observability/web/ObservabilityRouteCollisionDetectorTest.java`

**Changes**:

- Replace `failsClosedForUnclassifiableHandlerMapping` with cases proving empty
  and unrelated unknown `HandlerMapping` implementations do not collide.
- Add a test-only `RequestMappingInfoHandlerMapping` subclass and prove the
  general Spring abstraction detects a reserved path but ignores empty and
  unrelated mappings. This is the focused abstraction-level regression for
  Boot's mapping family without coupling production code to Actuator.
- Replace the functional-resource fail-closed expectation with a non-collision
  expectation when the visitor exposes no path.
- Add functional cases that distinguish an unclassifiable predicate/router
  form from a genuinely path-unconstrained leaf, and a path-unconstrained nested
  predicate with an unrelated leaf.
- Retain and, where useful, parameterize the existing exact, child, dynamic,
  case-insensitive, explicit URL, functional, and broad-wildcard collision
  matrix. Add an unparseable non-overlap case so parse failure cannot become the
  sole evidence of collision.

### Success Criteria

#### Automated Verification

- [x] The new unknown-mapping and general handler-method tests fail against the
  pre-fix detector for the expected false-positive/false-negative reasons:
  `.\mvnw.cmd --batch-mode --no-transfer-progress -pl loomspan-spring-boot-starter -Dtest=ObservabilityRouteCollisionDetectorTest test`.
- [x] Existing positive exact, child, dynamic, functional, explicit URL, and
  broad-wildcard assertions remain present.

---

## Phase 2: Generalize Inspection and Remove Unknown-as-Collision Branches

### Overview

Implement the general positive-overlap policy in the internal detector without
changing registration or authentication components.

### Changes Required

#### 1. Handler-method mapping inspection

**File**: `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/observability/web/ObservabilityRouteCollisionDetector.java`

**Changes**:

- Match `RequestMappingInfoHandlerMapping`, enumerate its
  `getHandlerMethods().keySet()`, and feed each `RequestMappingInfo` pattern into
  the existing overlap routine.
- Remove the final “unrecognized concrete type means collision” fallback.
- Keep `AbstractUrlHandlerMapping` and `RouterFunctionMapping` inspection as
  separate supported families and preserve resource-fallback special handling.

#### 2. Functional route evidence states

**File**: `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/observability/web/ObservabilityRouteCollisionDetector.java`

**Changes**:

- Preserve explicit predicate results instead of converting both
  path-unconstrained and unclassifiable states to an empty string list.
- Treat a path-unconstrained leaf as colliding with all paths. Treat a
  path-unconstrained nested predicate as an identity prefix and combine it with
  inspectable leaves.
- Do not set collision solely for unclassifiable nested predicates, leaf
  predicates, resource callbacks, or unknown router callbacks. Preserve any
  independently known overlapping alternative exposed by compound predicates.
- Make invalid path parsing non-colliding unless the existing independent
  lexical namespace/segment checks positively establish overlap.

### Success Criteria

#### Automated Verification

- [x] Focused detector suite passes:
  `.\mvnw.cmd --batch-mode --no-transfer-progress -pl loomspan-spring-boot-starter -Dtest=ObservabilityRouteCollisionDetectorTest test`.
- [x] Production code contains no class-name/version check for Boot Actuator and
  no new configuration or public extension point.

---

## Phase 3: Prove Boot 4.1, Security, and Compatibility Coexistence

### Overview

Add a real Actuator/separate-management-port regression and execute the complete
framework and downstream verification sequence.

### Changes Required

#### 1. Test-only Actuator dependency

**File**: `loomspan-spring-boot-starter/pom.xml`

**Changes**: Add `spring-boot-starter-actuator` with `test` scope so the starter
can reproduce Boot 4.1's actual additional-health mapping. Do not add Actuator
to the starter's production dependency surface.

#### 2. Actuator coexistence integration

**File**: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/observability/web/ObservabilityActuatorCoexistenceIntegrationTest.java`

**Changes**:

- Start a minimal Boot application with observability enabled, a valid API key,
  Actuator health probes, and `management.server.port=0`.
- Assert the activation coordinator is enabled, a valid-key request to a
  Console endpoint succeeds, and the same request without the key receives the
  existing `401` problem response.
- Keep the test focused on real Boot auto-configuration; do not reproduce
  Sidecar JWT policy or add Sidecar code to the framework repository.

#### 3. Protected regression verification

**Files**:

- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/observability/web/ObservabilityCollisionIntegrationTest.java`
- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/observability/web/ObservabilityHostSecurityIntegrationTest.java`
- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java`

**Changes**: No source changes expected. Run them to prove confirmed application
ownership, API-key/host-security coexistence, and the closed Java surface.

**Implementation note**: Adding Actuator to the test classpath also activates
Boot's management-security auto-configuration in existing observability fixtures
that deliberately exclude normal servlet security. Those security-free fixtures
explicitly exclude only `ManagementWebSecurityAutoConfiguration`; the dedicated
host-security integration remains fully secured and unchanged.

### Success Criteria

#### Automated Verification

- [x] Focused detector and web-boundary integrations pass:
  `.\mvnw.cmd --batch-mode --no-transfer-progress -pl loomspan-spring-boot-starter '-Dtest=ObservabilityRouteCollisionDetectorTest,ObservabilityActuatorCoexistenceIntegrationTest,ObservabilityCollisionIntegrationTest,ObservabilityHostSecurityIntegrationTest' test`.
- [x] Public surface remains closed:
  `.\mvnw.cmd --batch-mode --no-transfer-progress -pl loomspan-spring-boot-starter -Dtest=LoomspanPublicSurfaceArchitectureTest test`.
- [x] Complete framework verification passes:
  `.\mvnw.cmd --batch-mode --no-transfer-progress verify`.
- [x] Updated beta.4 snapshot is installed for the dependent build:
  `.\mvnw.cmd --batch-mode --no-transfer-progress install`.
- [x] Against that installed snapshot, the dependent Sidecar acceptance test
  passes without Sidecar edits, from `C:\opendev\code\loomspan-sidecar`:
  `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=ConsoleSecurityIntegrationTest test`.

---

## Testing Strategy

### Unit Tests

Use the detector suite as the primary behavior matrix. First make unknown and
general handler-method mapping cases red, then cover inspectable overlaps,
unrelated paths, unconstrained functional leaves, unclassifiable functional
forms, resources, compound predicates, wildcard behavior, and parse failures.

### Integration Tests

Use one real Boot 4.1 Actuator/separate-port application to prove the reported
startup failure is corrected and API-key enforcement remains active. Retain the
existing confirmed-collision and host-security integrations, then run the
dependent Sidecar `ConsoleSecurityIntegrationTest` only after installing the
new local snapshot. See the companion testing plan for exact cases and exit
criteria.

## Performance Considerations

Startup inspection remains linear in the number of handler mappings and
enumerated route patterns. Generalizing the handler-method type does not add
reflection, request-time work, or persistent state. Preserve short-circuiting
once a positive collision is found.

## Migration Notes

No consumer migration or configuration change is required. Applications with
unknown handler mappings may newly activate Console; they remain responsible
for honoring Loomspan's documented reserved namespace. Applications with an
inspectable overlap continue to see Console disabled. Rollback is the ordinary
artifact rollback, but it reintroduces the Actuator false positive.

## References

- Original ticket: `ai/thoughts/tickets/loomspan-pr-5.5-positive-observability-route-collisions.md`
- Related research: `ai/thoughts/research/2026-09-13-loomspan-pr-5-5-positive-observability-route-collisions.md`
- Design lens: `ai/thoughts/framework-feature-design-lens.md`
- Primary implementation: `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/observability/web/ObservabilityRouteCollisionDetector.java:38-170`
- Functional analysis: `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/observability/web/ObservabilityRouteCollisionDetector.java:172-453`
- Existing test matrix: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/observability/web/ObservabilityRouteCollisionDetectorTest.java:23-147`
- Dependent acceptance test: `C:/opendev/code/loomspan-sidecar/src/test/java/ai/loomspan/sidecar/security/ConsoleSecurityIntegrationTest.java:16-42`
