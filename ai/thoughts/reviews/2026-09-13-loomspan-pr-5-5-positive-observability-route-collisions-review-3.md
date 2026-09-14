# PR 5.5 — Positive Observability Route Collisions — Review 3

## Code Review Findings

No actionable findings remain in the final repository state.

## Findings Resolved in This Context

### [P2] Require positive evidence from every side of a functional `AND`

- **Location:** `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/observability/web/ObservabilityRouteCollisionDetector.java:408`
- **Evidence:** The candidate implementation combined an unclassifiable predicate with a known reserved path by retaining the known path. A concrete always-false unknown predicate combined with `GET(ObservabilityApiPaths.INSTANCE)` therefore reported a collision even though the conjunction cannot match any request.
- **Trigger:** A functional route uses an unclassifiable custom predicate in an `AND` expression with an inspectable reserved-namespace predicate.
- **Impact:** Console is disabled from uncertainty rather than a positively identified overlap, recreating the false-positive policy this ticket removes for a compound functional form.
- **Resolution:** An `AND` operand that is wholly unclassifiable now makes the conjunction unclassifiable instead of borrowing paths from the other operand. Known alternatives from `OR` still retain their positive path evidence. A regression using an always-false unknown predicate failed before the correction and passes afterward.

## Open Questions and Assumptions

- None.

## Verification Results

- FAIL — `.\mvnw.cmd --batch-mode --no-transfer-progress -pl loomspan-spring-boot-starter -Dtest=ObservabilityRouteCollisionDetectorTest test`: the new unknown-`AND` regression reproduced the false positive before the production correction (12 tests, 1 failure).
- PASS — `.\mvnw.cmd --batch-mode --no-transfer-progress -pl loomspan-spring-boot-starter '-Dtest=ObservabilityRouteCollisionDetectorTest,ObservabilityActuatorCoexistenceIntegrationTest,ObservabilityCollisionIntegrationTest,ObservabilityHostSecurityIntegrationTest' test` (15 tests, 0 failures, 0 errors).
- PASS — `.\mvnw.cmd --batch-mode --no-transfer-progress -pl loomspan-spring-boot-starter -Dtest=LoomspanPublicSurfaceArchitectureTest test` (8 tests, 0 failures, 0 errors).
- PASS — `.\mvnw.cmd --batch-mode --no-transfer-progress verify` (1,138 tests, 0 failures, 0 errors).
- PASS — `.\mvnw.cmd --batch-mode --no-transfer-progress install` (1,138 tests, 0 failures, 0 errors; beta.4 snapshot installed).
- PASS — `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=ConsoleSecurityIntegrationTest test` from `C:\opendev\code\loomspan-sidecar` (1 test, 0 failures, 0 errors).
- PASS — `git diff --check`.

## Requirements and Plan Conformance

- **Implemented:** The detector enumerates the general `RequestMappingInfoHandlerMapping` family, ignores unknown mapping types without positive route evidence, preserves confirmed annotated, functional, explicit URL, resource, dynamic, and broad-wildcard overlaps, and no longer treats parse failure alone as collision evidence.
- **Implemented:** The real Boot 4.1 Actuator integration uses a separate random management port, proves `AdditionalHealthEndpointPathsWebMvcHandlerMapping` is present, proves Console activation succeeds, and verifies both valid-key success and missing-key `401` behavior.
- **Implemented:** Actuator remains test scoped. Existing security-free observability fixtures exclude only Actuator's management-security auto-configuration, while the dedicated host-security integration remains secured.
- **Implemented:** Full framework verification, snapshot installation, and the unchanged dependent Sidecar Console security acceptance all pass.
- **Partial:** None.
- **Missing:** None.
- **Safe deviations:** The additional unknown-`AND` regression and correction refine the planned compound-predicate matrix to enforce the ticket's positive-evidence rule; they do not expand the supported surface or behavior scope.
- **Compatibility review:** The detector is internal implementation. User-visible configuration keys, the reserved namespace, confirmed-collision disablement, API-key enforcement, and host-security coexistence remain protected. Replacing unknown-as-collision behavior is expressly authorized by the ticket's Pipeline note. No application API, supported SPI, bean replacement point, configuration switch, class-name exemption, or compatibility shim was added; the public-surface architecture allowlist remains unchanged.

## Skill-Authoring Documentation Impact

- **Assessment:** No impact.
- **Rationale:** The diff changes only startup ownership detection for the optional Console HTTP namespace and test wiring for Actuator. It does not change manifest syntax, model selection, mappings, planning or execution semantics, evidence, skill input/output contracts, capability visibility, RBAC, attachments, limits, traces, debugging, or skill testing guidance.
- **Documents reviewed:** None; the executable diff has no author-facing skill semantics to route into the knowledge base.
- **Evidence checked:** Detector source and unit matrix, observability registrar and auto-configuration wiring, real Boot Actuator integration, confirmed-collision integration, host-security integration, and public-surface architecture test.
- **Coverage table:** Not applicable.
- **LLM-first usability:** Not applicable.

## Residual Risks and Optional Developer Checks

- None. The ticket explicitly leaves custom unclassifiable mappings that secretly claim the reserved namespace unsupported.

## Disposition

- **Candidate clean; fresh review required** — one P2 false-positive finding was fixed in this context, all final verification passed, and no actionable finding remains after the complete re-review.
