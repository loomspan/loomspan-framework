# PR 5.5 — Positive Observability Route Collisions Testing Plan

## Change Summary

- Generalize handler-method route inspection from the annotation-specific
  `RequestMappingHandlerMapping` to `RequestMappingInfoHandlerMapping`.
- Stop treating unknown or unenumerable mapping implementations and functional
  route forms as collisions without positive overlapping path evidence.
- Preserve collision detection for all inspectable exact, child, dynamic,
  case-insensitive, functional, explicit URL, and broad-wildcard overlaps, plus
  genuinely path-unconstrained functional leaf routes.
- Prove real Spring Boot 4.1 Actuator health probes on a separate management
  port coexist with an activated, API-key-protected Console.

## Impacted Areas

- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/observability/web/ObservabilityRouteCollisionDetector.java`
  — startup classification of handler-method, URL, and functional mappings.
- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/observability/web/ObservabilityRouteCollisionDetectorTest.java`
  — focused positive/negative collision matrix and obsolete fail-closed tests.
- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/observability/web/ObservabilityActuatorCoexistenceIntegrationTest.java`
  — new real Boot 4.1 separate-management-port regression.
- `loomspan-spring-boot-starter/pom.xml` — test-scoped Actuator dependency.
- Existing `ObservabilityCollisionIntegrationTest`,
  `ObservabilityHostSecurityIntegrationTest`, and
  `LoomspanPublicSurfaceArchitectureTest` — protected regression paths.
- `C:/opendev/code/loomspan-sidecar/src/test/java/ai/loomspan/sidecar/security/ConsoleSecurityIntegrationTest.java`
  — downstream installed-snapshot acceptance check; no Sidecar edit is in
  scope.

## Risk Assessment

- The main security/correctness risk is a false negative that lets Loomspan
  register over a path an inspectable application mapping owns. The positive
  exact/dynamic/wildcard matrices and collision integration protect this.
- A false positive could continue disabling Console for Boot Actuator or custom
  infrastructure. Both a general handler-method unit test and a real Actuator
  integration protect this.
- Functional predicates have distinct meanings currently collapsed into an
  empty path list. A leaf with no path constraint is a known all-path mapping
  and must still collide; an unclassifiable predicate provides no positive path
  evidence; a path-unconstrained nested predicate must not erase a known leaf.
- Compound predicates can expose a known overlapping alternative alongside
  information the visitor cannot classify. Tests must ensure known positive
  evidence is retained without converting uncertainty itself into a collision.
- Invalid pattern parsing must not be sufficient collision evidence, while
  independent exact-prefix/segment evidence and valid broad wildcards remain
  detected.
- The Console authentication boundary must remain unchanged: successful
  activation still yields `401` without the configured key and success with it.
- **Protected compatibility paths**: reserved namespace, API-key enforcement,
  confirmed collision disablement, host Spring Security coexistence,
  configuration keys, and the closed application API.
- **Intentionally removed obsolete paths**: fail-closed collision solely from
  an unknown `HandlerMapping`, unknown router callback, unclassifiable
  predicate, functional resource callback, or parse exception. This internal
  behavior change is expressly authorized by the ticket and Pipeline note.
- Skill-authoring claims require no evidence because the implementation plan
  classifies skill-authoring impact as no impact.

## Existing Test Coverage

- `ObservabilityRouteCollisionDetectorTest` already covers annotated exact,
  variable, wildcard, catch-all, and case-insensitive routes; functional and
  explicit URL routes; compound `or`; unrelated routes; generic resource
  fallbacks; and resource handlers under the reserved namespace
  (`:23-147`).
- The same suite currently expects unknown mappings and functional resource
  lookups to collide (`:87-115`); these are obsolete internal expectations that
  must be replaced atomically.
- `ObservabilityCollisionIntegrationTest:39-63` proves an inspectable exact
  application collision disables the complete adapter and leaves the host route
  usable.
- `ObservabilityHostSecurityIntegrationTest:47-120` proves host security must
  permit the namespace to reach Loomspan, the key is mandatory, business routes
  stay host-controlled, and forwarded dispatches are filtered.
- `LoomspanPublicSurfaceArchitectureTest:29-71` protects the closed application
  API and classifies the detector/registrar as internal.
- Gap: the starter has no test-scoped Actuator dependency and no integration
  that starts the actual Boot 4.1 additional-health mapping with a separate
  management port.

## Bug Reproduction / Failing Test First

- **Type**: Unit.
- **Location**:
  `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/observability/web/ObservabilityRouteCollisionDetectorTest.java`.
- **Arrange**: Create (a) an arbitrary empty `HandlerMapping` and (b) a minimal
  test subclass of `RequestMappingInfoHandlerMapping` with no registered
  methods. Optionally register an unrelated `RequestMappingInfo` to the latter.
- **Act**: Construct `ObservabilityRouteCollisionDetector` with each mapping and
  call `hasCollision()`.
- **Assert**: Each result is `false`.
- **Expected failure (pre-fix)**: The arbitrary mapping returns `true` through
  the unknown-type fallback. The general handler-method subclass also returns
  `true` because it is not the annotation-specific recognized subclass, even
  when `getHandlerMethods()` is empty.

After that minimal red test is recorded, add the actual Boot integration and
the expanded edge matrix before considering the change verified.

## Tests to Add/Update

### 1. `ignoresEmptyAndUnrelatedUnclassifiableHandlerMappings`

- **Type**: Unit.
- **Location**: `ObservabilityRouteCollisionDetectorTest.java`.
- **What it proves**: Unknown mapping type alone is not positive overlap
  evidence, whether it is empty or returns no enumerable route metadata.
- **Fixtures/data**: Test-local `HandlerMapping` stubs returning no handler.
- **Mocks**: None; use concrete lambdas/stubs.
- **Affected surface**: Internal implementation.
- **Compatibility expectation**: Intentional removal authorized by the ticket
  and Pipeline note.

### 2. `inspectsGeneralRequestMappingInfoHandlerMappings`

- **Type**: Unit.
- **Location**: `ObservabilityRouteCollisionDetectorTest.java`.
- **What it proves**: A non-annotation-specific
  `RequestMappingInfoHandlerMapping` is false when empty or unrelated and true
  when a registered `RequestMappingInfo` is exact, child/dynamic, or broad over
  the reserved namespace.
- **Fixtures/data**: Minimal test subclass overriding mapping discovery;
  registered `RequestMappingInfo` values for `/health`, reserved exact, and a
  broad wildcard.
- **Mocks**: None.
- **Affected surface**: Internal implementation.
- **Compatibility expectation**: New general inspection path; confirmed
  collisions remain protected.

### 3. `classifiesFunctionalRoutesOnlyFromPositivePathEvidence`

- **Type**: Unit.
- **Location**: `ObservabilityRouteCollisionDetectorTest.java`.
- **What it proves**:
  (a) a path-unconstrained leaf (for example `accept(JSON)`) still collides;
  (b) an unclassifiable predicate/router callback alone does not collide;
  (c) a functional resource callback with no exposed path does not collide;
  (d) a path-unconstrained nested predicate plus `/health` does not collide;
  and (e) known reserved alternatives in inspectable compound predicates still
  collide.
- **Fixtures/data**: Spring functional route builders plus small test-only
  predicate/router implementations where needed.
- **Mocks**: None.
- **Affected surface**: Internal implementation.
- **Compatibility expectation**: Protect known/unconstrained overlaps and
  intentionally remove unknown-as-collision behavior.

### 4. `doesNotInferCollisionOnlyFromPatternParseFailure`

- **Type**: Unit.
- **Location**: `ObservabilityRouteCollisionDetectorTest.java`.
- **What it proves**: An unparseable, lexically unrelated candidate does not
  collide solely because parsing threw; valid exact, dynamic, and wildcard
  candidates remain covered by the retained matrix.
- **Fixtures/data**: A directly constructed `SimpleUrlHandlerMapping` whose
  handler map contains a malformed, lexically unrelated pattern; this existing
  explicit-URL seam reaches `overlaps` without widening production visibility.
- **Mocks**: None.
- **Affected surface**: Internal implementation.
- **Compatibility expectation**: Intentional removal authorized by the positive
  overlap rule.

### 5. `actuatorSeparateManagementPortDoesNotDisableAuthenticatedConsole`

- **Type**: Integration.
- **Location**:
  `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/observability/web/ObservabilityActuatorCoexistenceIntegrationTest.java`.
- **What it proves**: Boot 4.1's real Actuator health/readiness setup with
  `management.server.port=0` contributes its standard mapping without disabling
  Console; activation is enabled, a valid-key Console request succeeds, and a
  missing-key request returns the existing `401` problem.
- **Fixtures/data**: Minimal `@SpringBootConfiguration`, empty skill catalog
  fixture already used by observability tests, enabled probes, observability
  opt-in, and a valid 32-byte API key. Add
  `spring-boot-starter-actuator` only at test scope.
- **Mocks**: None; use the real Boot application context and HTTP or MockMvc
  boundary appropriate to the two-port setup.
- **Affected surface**: Configuration behavior and internal implementation.
- **Compatibility expectation**: Protect configuration, namespace, and API-key
  contracts while correcting the false positive.

### 6. Existing confirmed-collision and host-security regressions

- **Type**: Integration.
- **Location**: `ObservabilityCollisionIntegrationTest.java` and
  `ObservabilityHostSecurityIntegrationTest.java`.
- **What it proves**: An inspectable host route still wins and disables the
  adapter; when activated, Console still requires its own key and coexists with
  host security without authenticating business routes.
- **Fixtures/data**: Existing test applications and keys unchanged.
- **Mocks**: None.
- **Affected surface**: Configuration behavior and internal implementation.
- **Compatibility expectation**: Protected path.

### 7. Closed public surface regression

- **Type**: Architecture.
- **Location**: `LoomspanPublicSurfaceArchitectureTest.java`.
- **What it proves**: No application API, supported SPI, leaked internal
  signature, or accidental Spring extension point is added by the change.
- **Fixtures/data**: Existing allowlists unchanged.
- **Mocks**: None.
- **Affected surface**: Application API, Supported SPI, and internal
  implementation.
- **Compatibility expectation**: Protected path; no public-surface delta.

### 8. Installed-snapshot Sidecar acceptance

- **Type**: Cross-repository integration.
- **Location**:
  `C:/opendev/code/loomspan-sidecar/src/test/java/ai/loomspan/sidecar/security/ConsoleSecurityIntegrationTest.java`.
- **What it proves**: The actual dependent application can combine its JWT
  execution security, separate-port Actuator, and Console API key using the
  newly installed beta.4 snapshot; a Console key cannot authenticate `/v1/**`
  and a JWT cannot replace the Console key.
- **Fixtures/data**: Existing Sidecar test and JWT fixtures unchanged.
- **Mocks**: Existing test JWT material only.
- **Affected surface**: Configuration behavior at a dependent application
  boundary; no Java-to-Go protocol change.
- **Compatibility expectation**: Protected downstream behavior; no Sidecar
  workaround or source edit.

## How to Run

Run from `C:\opendev\code\loomspan-framework` unless stated otherwise:

1. Record the failing detector test before production edits:
   `.\mvnw.cmd --batch-mode --no-transfer-progress -pl loomspan-spring-boot-starter -Dtest=ObservabilityRouteCollisionDetectorTest test`.
2. Re-run the focused detector after implementation with the same command.
3. Run focused web-boundary regressions:
   `.\mvnw.cmd --batch-mode --no-transfer-progress -pl loomspan-spring-boot-starter '-Dtest=ObservabilityRouteCollisionDetectorTest,ObservabilityActuatorCoexistenceIntegrationTest,ObservabilityCollisionIntegrationTest,ObservabilityHostSecurityIntegrationTest' test`.
4. Run the public-surface architecture test:
   `.\mvnw.cmd --batch-mode --no-transfer-progress -pl loomspan-spring-boot-starter -Dtest=LoomspanPublicSurfaceArchitectureTest test`.
5. Run the full framework build:
   `.\mvnw.cmd --batch-mode --no-transfer-progress verify`.
6. Install the exact updated snapshot:
   `.\mvnw.cmd --batch-mode --no-transfer-progress install`.
7. From `C:\opendev\code\loomspan-sidecar`, run the dependent acceptance test:
   `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=ConsoleSecurityIntegrationTest test`.

No credentials, external model provider, or non-loopback service should be
required. The integration tests allocate random application/management ports.

## Exit Criteria

- [x] The minimal unknown/general-handler-method reproduction test is observed
  failing pre-fix for the expected detector branches.
- [x] Empty and unrelated unknown mappings no longer report collisions.
- [x] The general `RequestMappingInfoHandlerMapping` family is enumerated and
  detects actual reserved-namespace overlaps without vendor-specific checks.
- [x] Functional tests distinguish known paths, path-unconstrained leaves,
  identity nesting, and unavailable route information.
- [x] Confirmed exact, child, dynamic, case-insensitive, functional, explicit
  URL, resource-namespace, and broad-wildcard overlaps remain detected.
- [x] The real Boot 4.1 Actuator/separate-management-port application activates
  Console, accepts the valid key, and rejects the missing key.
- [x] Existing collision ownership and host-security integration tests pass.
- [x] `LoomspanPublicSurfaceArchitectureTest` passes with no allowlist change.
- [x] Full framework `verify` passes.
- [x] The updated `1.0.0-beta.4-SNAPSHOT` installs locally and the unchanged
  Sidecar `ConsoleSecurityIntegrationTest` passes against it.
- [x] No obsolete unknown-as-collision fallback, class-name exemption, dual
  policy, new SPI, bean override contract, or configuration switch remains.
- [x] No skill-authoring document or coverage-table update is required because
  no author-facing claim changed.
- [x] There are no non-automatable developer observations required for
  completion.
