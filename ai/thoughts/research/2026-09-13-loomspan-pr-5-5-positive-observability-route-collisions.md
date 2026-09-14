---
date: 2026-09-13T20:07:42-07:00
researcher: Codex (GPT-5)
git_commit: b84a2e03e8a64ad42b8db71e319d1ef66fd6b313
branch: main
repository: loomspan-framework
topic: "PR 5.5 — Require a positive observability route collision"
tags: [research, codebase, observability, spring-mvc, actuator, route-collisions, security]
status: complete
last_updated: 2026-09-13
last_updated_by: Codex (GPT-5)
---

# Research: PR 5.5 — Require a positive observability route collision

**Date**: 2026-09-13 20:07:42 PDT
**Researcher**: Codex (GPT-5)
**Git Commit**: b84a2e03e8a64ad42b8db71e319d1ef66fd6b313
**Branch**: main
**Repository**: loomspan-framework

## Research Question

Document the current framework behavior and affected surfaces for
`ai/thoughts/tickets/loomspan-pr-5.5-positive-observability-route-collisions.md`:
how Loomspan discovers application route collisions, how an unknown Spring MVC
`HandlerMapping` affects Console activation, how Boot 4.1's additional health
mapping fits the available Spring mapping abstractions, and which tests,
security boundaries, configuration contracts, documentation, and dependent
consumers establish the present behavior.

## Summary

The servlet observability auto-configuration collects every `HandlerMapping`
bean into one internal detector before Loomspan programmatically registers its
own Console handlers. The detector can currently enumerate mappings from three
concrete families: annotated `RequestMappingHandlerMapping`, URL-based
`AbstractUrlHandlerMapping`, and functional `RouterFunctionMapping`. It returns
`true` immediately for any other concrete `HandlerMapping`, regardless of
whether that mapping contains no routes, contains only unrelated routes, or has
inspectable `RequestMappingInfo` entries
(`ObservabilityRouteCollisionDetector.java:38-64`).

Boot 4.1's `AdditionalHealthEndpointPathsWebMvcHandlerMapping` is one such
unrecognized family in the current detector. Version-aligned Boot 4.1 source
shows that it extends `AbstractWebMvcEndpointHandlerMapping`, which extends
Spring MVC's `RequestMappingInfoHandlerMapping`; through the generic
`AbstractHandlerMethodMapping<RequestMappingInfo>` base it exposes
`getHandlerMethods()`. It does not extend Loomspan's recognized
`RequestMappingHandlerMapping`. When Boot constructs it without an application
server health endpoint, its `initHandlerMethods()` returns without registering
entries, but the current type-based fallback still reports a collision.

A collision disables the complete optional observability adapter before its
runtime or routes are registered. The API-key filter is a separate registered
servlet filter over the reserved namespace, and it authenticates only when the
activation coordinator is enabled. Existing integration tests separately
protect confirmed route ownership and the host-Spring-Security/API-key
coexistence boundary. The detector and registrar are internal implementation;
the reserved route/configuration behavior is user visible, but no supported
Java API or SPI is involved.

## Detailed Findings

### 1. Auto-configuration and activation sequence

- `LoomspanObservabilityWebAutoConfiguration` is servlet-only and ordered after
  Loomspan and Spring MVC auto-configuration. It creates an infrastructure
  `ObservabilityRouteCollisionDetector` from the application context's complete
  `List<HandlerMapping>` (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/autoconfigure/LoomspanObservabilityWebAutoConfiguration.java:36-40`,
  `:88-93`).
- The same auto-configuration obtains the specifically named
  `requestMappingHandlerMapping` through an `ObjectProvider` and passes it to
  `ObservabilityRouteRegistrar`, while the detector receives all mapping beans
  (`LoomspanObservabilityWebAutoConfiguration.java:95-113`).
- `ObservabilityRouteRegistrar.afterSingletonsInstantiated()` first evaluates
  opt-in state, MVC availability, configuration validity, and collision state.
  Any collision logs a sanitized namespace diagnostic, disables activation,
  and returns before creating the observability runtime or registering Console
  routes (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/observability/web/ObservabilityRouteRegistrar.java:75-102`).
- When those gates pass, the registrar creates the runtime, registers the exact
  GET handlers, adds a namespace fallback for the root and `/**`, and only then
  enables the activation coordinator (`ObservabilityRouteRegistrar.java:104-125`,
  `:185-215`). On registration failure it unregisters the routes accumulated in
  the current activation attempt, closes the provisional runtime, and disables
  activation (`:127-133`, `:255-269`).

### 2. Current collision classification

- Annotated mappings are recognized only through the concrete
  `RequestMappingHandlerMapping` type. Every `RequestMappingInfo` key returned by
  `getHandlerMethods()` contributes its `getPatternValues()` strings to overlap
  analysis (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/observability/web/ObservabilityRouteCollisionDetector.java:42-46`,
  `:67-77`).
- Explicit URL mappings are recognized through `AbstractUrlHandlerMapping`.
  The detector checks both the legacy string handler map and the parsed
  `PathPattern` handler map (`ObservabilityRouteCollisionDetector.java:47-51`,
  `:79-99`). Generic static-resource fallbacks at `/**`, `/*`, and `/{*path}`
  are ignored, while a resource handler dedicated to the reserved namespace or
  its parent remains a collision (`:101-108`).
- Functional mappings are recognized through `RouterFunctionMapping`. A
  visitor extracts path predicates, combines nested prefixes and leaf paths,
  and submits the resulting path strings to the common overlap routine
  (`ObservabilityRouteCollisionDetector.java:52-56`, `:110-116`, `:179-230`).
  Functional routes with no path constraint, functional resources, unknown
  router forms, negated predicates, and predicate structures the visitor cannot
  classify are presently represented as collision states rather than as an
  empty mapping (`:184-192`, `:202-209`, `:232-247`, `:250-260`, `:334-355`).
- After those three checks, any `HandlerMapping` that is not an instance of one
  of the three recognized concrete families causes an immediate collision
  (`ObservabilityRouteCollisionDetector.java:57-62`). There is no class-name or
  Boot-version exception and no reflective route inspection path.
- The common overlap function parses a candidate with `PathPatternParser` and
  tests the reserved root and one child probe. It also performs
  case-insensitive reserved-prefix recognition and a segment comparison that
  treats `{`, `*`, or `?` as dynamic (`ObservabilityRouteCollisionDetector.java:118-170`).
  A runtime parse failure is itself reported as overlap (`:129-132`).

### 3. Executable detector coverage

- `ObservabilityRouteCollisionDetectorTest` covers exact, segment-variable,
  child wildcard, and catch-all annotated routes
  (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/observability/web/ObservabilityRouteCollisionDetectorTest.java:23-38`).
- It covers a case-insensitive host mapping, direct functional and explicit URL
  collisions, and an `or` predicate containing a reserved route (`:40-85`).
- It expressly codifies the present fail-closed branches for a functional
  predicate without a path constraint, a functional resource lookup whose
  supplied resource pattern is `/assets/**`, and an arbitrary lambda
  `HandlerMapping` (`:87-115`).
- It also establishes non-collision behavior for unrelated annotated,
  functional, and explicit URL routes, including a root mapping and unrelated
  dynamic routes, and establishes collision behavior for resource handlers
  rooted inside `/_loomspan/observability/v1/**` or its `/_loomspan/**` parent
  (`:117-147`).
- A fresh baseline run on the recorded commit passed all nine current detector
  tests: `./mvnw.cmd --batch-mode --no-transfer-progress -pl
  loomspan-spring-boot-starter -Dtest=ObservabilityRouteCollisionDetectorTest
  test` (9 tests, 0 failures, 0 errors, 0 skipped).

### 4. Boot 4.1 additional health mapping

- The repository imports Spring Boot dependency management at `4.1.0`; the
  starter has optional MVC dependencies but no current Actuator test dependency
  (`pom.xml:53-70`,
  `loomspan-spring-boot-starter/pom.xml:81-109`).
- The version-aligned local Boot source archive
  `~/.m2/repository/org/springframework/boot/spring-boot-webmvc/4.1.0/spring-boot-webmvc-4.1.0-sources.jar`
  defines `AdditionalHealthEndpointPathsWebMvcHandlerMapping` as a subclass of
  `AbstractWebMvcEndpointHandlerMapping`. Its constructor accepts a nullable
  exposed health endpoint and groups; when the health endpoint is null,
  `initHandlerMethods()` returns without adding a route (archive entry
  `org/springframework/boot/webmvc/actuate/endpoint/web/AdditionalHealthEndpointPathsWebMvcHandlerMapping.java:41-75`).
- The same Boot source archive defines `AbstractWebMvcEndpointHandlerMapping`
  as a subclass of Spring's `RequestMappingInfoHandlerMapping`, not
  `RequestMappingHandlerMapping` (archive entry
  `org/springframework/boot/webmvc/actuate/endpoint/web/AbstractWebMvcEndpointHandlerMapping.java:80-108`).
  Spring's hierarchy continues through
  `AbstractHandlerMethodMapping<RequestMappingInfo>`, whose public
  `getHandlerMethods()` returns the registered `RequestMappingInfo` map. Thus
  Boot's mapping has standard path-aware mapping data when entries exist, but
  it enters the current detector's unknown-type branch.
- Boot's `WebMvcHealthEndpointExtensionAutoConfiguration` creates an additional
  health mapping for server-namespace additional paths. Its management-context
  configuration separately creates one when the management port differs and
  selects management-namespace additional paths (same source archive, entries
  `org/springframework/boot/webmvc/autoconfigure/actuate/endpoint/web/WebMvcHealthEndpointExtensionAutoConfiguration.java:46-66`
  and
  `org/springframework/boot/webmvc/autoconfigure/actuate/web/WebMvcEndpointManagementContextConfiguration.java:155-176`).

### 5. Reserved namespace and authentication boundary

- `ObservabilityApiPaths.ROOT` is the single internal root constant
  `/_loomspan/observability/v1`; all concrete Console endpoints derive from it
  (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/observability/web/ObservabilityApiPaths.java:3-17`).
- Auto-configuration registers `ObservabilityApiKeyFilter` for the exact root
  and one-level servlet wildcard and all dispatcher types, at order `-99`
  (`LoomspanObservabilityWebAutoConfiguration.java:115-129`).
- The filter passes through when activation is disabled. When enabled, it sets
  `Cache-Control: no-store`, requires exactly one syntactically valid
  `X-loomspan-Api-Key`, performs a constant-time byte comparison with the
  configured key, installs a framework operator authentication for downstream
  processing, and restores the prior Spring Security context in `finally`
  (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/observability/web/ObservabilityApiKeyFilter.java:42-69`,
  `:99-130`). Missing or invalid keys receive a `401` Loomspan problem and no
  instance header (`:57-65`, `:133-146`).
- `ObservabilityCollisionIntegrationTest` establishes that an application exact
  mapping at the reserved `instance` path remains host-owned and usable while
  Loomspan activation is wholly disabled
  (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/observability/web/ObservabilityCollisionIntegrationTest.java:23-63`).
- `ObservabilityHostSecurityIntegrationTest` establishes that host Spring
  Security must permit the reserved namespace to reach Loomspan, while
  Loomspan's key remains mandatory, business paths remain host-controlled, and
  forwarded dispatches through the namespace are filtered
  (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/observability/web/ObservabilityHostSecurityIntegrationTest.java:28-69`,
  `:89-120`).

### 6. Configuration and documentation

- `loomspan.observability.enabled` is a startup opt-in and defaults to `false`;
  its nested API key and retention settings are bound through
  `LoomspanProperties.Observability`
  (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/autoconfigure/LoomspanProperties.java:391-428`).
  Additional configuration metadata documents the opt-in and the API-key shape
  (`loomspan-spring-boot-starter/src/main/resources/META-INF/additional-spring-configuration-metadata.json:10-30`).
- The root README documents the reserved namespace, the mandatory per-request
  key, Spring Security coexistence, and the statement that an overlapping
  application mapping leaves the complete optional adapter disabled
  (`README.md:527-549`, `:590-610`). It does not document unknown
  `HandlerMapping` types as collisions.
- Documentation drift classification: **possible framework defect**. The
  README describes disabling on an overlapping application mapping, while the
  live detector also disables on an empty or unrelated unknown mapping without
  identifying an overlap. The controlling ticket and its Pipeline note
  explicitly identify that behavior as the defect being changed, while
  preserving the documented reserved-namespace and API-key semantics.
- The bundled `loomspan-docs` package is version-aligned at
  `1.0.0-beta.4-SNAPSHOT`, matching the repository POM. Its Java API index and
  compatibility baseline classify `ai.loomspan.internal` and internal Spring
  beans as unsupported extension dependencies and say configuration behavior
  remains user-visible even though binding types are not application Java API
  (`agent-skills/loomspan-docs/references/java-api/README.md`,
  `agent-skills/loomspan-docs/references/java-api/compatibility-and-boundaries.md`).
  That package does not document collision-detector edge cases.
- Skill-authoring impact assessment: **no impact**. Route collision and servlet
  activation occur at the application/operator integration boundary; no skill
  manifest syntax, planner behavior, capability visibility, author input/output
  contract, trace interpretation, or skill debugging procedure passes through
  this detector.

### 7. Contract classification and consumers

- **Application API:** no affected type is in the thirteen-type closed
  `ai.loomspan.api` allowlist
  (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java:29-42`).
- **Supported SPI:** no affected SPI exists. `RestSkillHandler` remains the sole
  supported SPI, and route collision is not an application replacement seam.
- **Configuration and manifest contracts:** the existing
  `loomspan.observability.enabled` and `loomspan.observability.auth.api-key`
  startup behavior is user visible. This ticket adds no key or manifest field.
- **Persisted or serialized contracts:** the collision decision does not alter
  a persisted format or Console JSON/NDJSON shape.
- **Ephemeral diagnostic formats:** no trace or current-run diagnostic schema is
  produced by the detector. Its observable diagnostic is a sanitized startup
  warning when activation is disabled.
- **Internal or accidentally exposed implementation:** the detector and
  registrar are public Java classes under `ai.loomspan.internal` solely for
  framework-owned composition. The architecture test records the detector as
  "Public only for framework-owned namespace inspection" and the registrar as
  "Public only for framework-owned programmatic route lifecycle"
  (`LoomspanPublicSurfaceArchitectureTest.java:53-71`). Auto-configuration bean
  methods are package-private infrastructure beans and have no
  `@ConditionalOnMissingBean` replacement contract
  (`LoomspanObservabilityWebAutoConfiguration.java:88-113`).
- The in-repository consumers are the auto-configuration, registrar, focused
  unit tests, collision integration test, host-security integration test,
  architecture allowlist, configuration metadata, and README described above.
- The authoritative dependent Sidecar ticket requires separate-port Actuator
  health/readiness, independent JWT protection for `/v1/**`, and Console API-key
  protection for the reserved namespace
  (`C:/opendev/code/loomspan-sidecar/ai/thoughts/tickets/2026-09-13-authenticated-execution-api.md:95-100`,
  `:220-223`). Its current `ConsoleSecurityIntegrationTest` starts with
  `management.server.port=0`, enables Loomspan observability, and expects a
  valid Console API key to receive `200`, while neither the Console key nor JWT
  substitutes for the other API's credential
  (`C:/opendev/code/loomspan-sidecar/src/test/java/ai/loomspan/sidecar/security/ConsoleSecurityIntegrationTest.java:16-42`).
  This is a dependent application test rather than a framework repository test
  or framework Java API consumer.

## Code References

- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/observability/web/ObservabilityRouteCollisionDetector.java:38-64` — three recognized mapping families and the unknown-type collision fallback.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/observability/web/ObservabilityRouteCollisionDetector.java:67-170` — annotated, explicit URL, resource, and common path-overlap handling.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/observability/web/ObservabilityRouteCollisionDetector.java:179-247` — functional router traversal and its fail-closed states.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/observability/web/ObservabilityRouteRegistrar.java:75-133` — activation gates, route registration, and failure handling.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/autoconfigure/LoomspanObservabilityWebAutoConfiguration.java:88-129` — collection of all handler mappings, registrar wiring, and API-key filter registration.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/observability/web/ObservabilityApiKeyFilter.java:42-103` — activation-aware API-key enforcement and SecurityContext restoration.
- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/observability/web/ObservabilityRouteCollisionDetectorTest.java:23-147` — current collision/non-collision matrix.
- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/observability/web/ObservabilityCollisionIntegrationTest.java:39-63` — confirmed collision leaves the application route owned by the host.
- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/observability/web/ObservabilityHostSecurityIntegrationTest.java:47-120` — application security and Loomspan key coexistence.
- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java:29-71` — closed API and internal classification.
- `README.md:527-610` — consumer-facing namespace, credential, route, and coexistence behavior.

## Architecture Documentation

The observability web adapter uses programmatic Spring MVC registration rather
than component-scanned controller annotations. A startup coordinator owns a
single activation state: the route registrar validates configuration and route
ownership, constructs all adapter runtime state, registers handlers, and then
publishes enabled activation. Both handler execution and the namespace servlet
filter consult that same state. The collision detector is therefore upstream
of both route availability and API-key authentication, but its classification
logic does not modify the filter's credential checks.

The framework compatibility policy classifies the route machinery as internal
implementation even where Java or Spring requires public visibility. The
user-visible contracts at this boundary are the documented `loomspan.*`
configuration behavior, reserved HTTP namespace, and operator authentication
semantics. There is no supported application-facing Java extension or bean
replacement surface for collision detection.

## Historical Context (from ai/thoughts/)

- `ai/thoughts/tickets/loomspan-pr-5.5-positive-observability-route-collisions.md` — controlling ticket; records the Boot 4.1/Sidecar observation, positive-overlap outcome, retained authentication and confirmed-collision requirements, and intentional replacement of the unknown-type fail-closed policy.
- `ai/thoughts/tickets/loomspan-pr-5.2-rest-skills-and-console.md` — earlier Console/REST delivery ticket; records the Console as a coordinated diagnostic consumer but contains no separate route-collision implementation decision.
- `ai/thoughts/framework-feature-design-lens.md` — current compatibility lens; defines the contract categories used above and states that public/internal Spring wiring does not by itself create supported API or SPI.
- `C:/opendev/code/loomspan-sidecar/ai/thoughts/tickets/2026-09-13-authenticated-execution-api.md` — dependent Sidecar requirements for Console/API-key, JWT, and separate-port Actuator coexistence.
- `C:/opendev/code/loomspan-sidecar/ai/thoughts/phases/phase-sc5.md` — future packaged lifecycle phase that retains separate management-port probes and optional Console pairing.

## Related Research

No earlier framework research document in `ai/thoughts/research/` covers this
collision detector. The dependent Sidecar checkout contains in-progress
research and planning artifacts for its own authenticated execution ticket;
those are outside this framework ticket's implementation boundary.

## Open Questions

The planning step must resolve these implementation/testing details from the
ticket and current behavior:

1. The current generic unknown-`HandlerMapping` fallback, unclassifiable
   functional predicate/router states, functional resource lookups, and invalid
   path-pattern parse failures all produce collisions through distinct branches.
   Planning must map the ticket's positive-overlap rule to each branch while
   retaining genuinely path-unconstrained mappings and confirmed wildcard
   overlaps.
2. Boot 4.1 Actuator is not currently a starter test dependency. Planning must
   establish whether framework verification should construct the standard Boot
   mapping directly, start an Actuator/separate-management-port application, or
   combine focused abstraction tests with the dependent Sidecar integration
   test.
3. The ticket requires a locally installed beta.4 snapshot and the dependent
   Sidecar Console/separate-port test after framework verification. Planning
   must record the exact cross-check order without treating Sidecar source edits
   as framework scope.
