# Code Review: PR 5.5 — Positive Observability Route Collisions (Review 1)

## Code Review Findings

No actionable findings remain.

## Findings Resolved in This Context

### [P2] Preserve confirmed intersections of compound functional path predicates

- **Location:** `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/observability/web/ObservabilityRouteCollisionDetector.java:415`
- **Evidence:** The initial candidate reduced an `AND` of two differing inspectable path predicates to an empty unclassifiable result. `path("/**").and(GET("/_loomspan/observability/v1"))` therefore produced no candidate even though Spring evaluates both predicates against the same request and the reserved root satisfies both.
- **Trigger:** An application declares a functional route whose compound predicate combines a broad path pattern with an exact or otherwise compatible reserved-namespace path.
- **Impact:** Loomspan could enable and register Console routes despite a confirmed application mapping in its reserved namespace, violating the protected collision policy and potentially taking precedence over the application's functional handler.
- **Resolution:** The predicate reducer now retains only concretely demonstrated intersections: a literal matched by the other pattern or a reserved-root/child witness matched by both patterns. The focused suite adds the broad-wildcard-plus-exact regression, so unclassifiable state alone still does not cause a collision.

## Open Questions and Assumptions

- None.

## Verification Results

- PASS — `.\mvnw.cmd --batch-mode --no-transfer-progress -pl loomspan-spring-boot-starter -Dtest=ObservabilityRouteCollisionDetectorTest test`
- PASS — `.\mvnw.cmd --batch-mode --no-transfer-progress -pl loomspan-spring-boot-starter '-Dtest=ObservabilityRouteCollisionDetectorTest,ObservabilityActuatorCoexistenceIntegrationTest,ObservabilityCollisionIntegrationTest,ObservabilityHostSecurityIntegrationTest' test`
- PASS — `.\mvnw.cmd --batch-mode --no-transfer-progress -pl loomspan-spring-boot-starter -Dtest=LoomspanPublicSurfaceArchitectureTest test`
- PASS — `.\mvnw.cmd --batch-mode --no-transfer-progress verify` (1,138 tests, 0 failures, 0 errors, 0 skipped)
- PASS — `.\mvnw.cmd --batch-mode --no-transfer-progress install` (1,138 tests and local `1.0.0-beta.4-SNAPSHOT` install)
- PASS — `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=ConsoleSecurityIntegrationTest test` from `C:\opendev\code\loomspan-sidecar`
- PASS — `git diff --check`

## Requirements and Plan Conformance

- **Implemented:** Boot 4.1 Actuator with a separate management port contributes the real additional-health mapping without disabling Console; the integration test proves enabled activation, `200` with the configured Console key, and the existing `401` problem without it.
- **Implemented:** Empty or unrelated unknown `HandlerMapping` implementations and unavailable functional route metadata no longer count as collisions solely from uncertainty.
- **Implemented:** General `RequestMappingInfoHandlerMapping`, annotated, explicit URL, resource, and functional mappings retain positive exact, child, dynamic, case-insensitive, broad-wildcard, compound-alternative, and path-unconstrained collision coverage. The review fix additionally preserves a confirmed broad-wildcard/exact functional conjunction.
- **Implemented:** Existing collision ownership and host Spring Security coexistence integrations pass; authentication code and registration/filter wiring are unchanged.
- **Implemented:** The Actuator dependency is test scoped, no Boot class-name exemption or configuration switch was added, and the public-surface architecture allowlist passes unchanged.
- **Implemented:** The beta.4 snapshot installs and the unchanged dependent Sidecar Console security test passes against it.
- **Partial:** None.
- **Missing:** None.
- **Safe deviations:** Existing security-free observability fixtures exclude Actuator management security because the new test-scoped dependency activates that auto-configuration; the dedicated host-security integration remains unchanged. The review's functional conjunction fix is within the plan's requirement to retain positively known compound-predicate evidence.
- **Compatibility review:** The detector remains unsupported `ai.loomspan.internal` implementation. User-visible configuration keys, reserved namespace, confirmed-collision disablement, and API-key behavior are preserved; removal of unknown-as-collision behavior is the intentional internal change authorized by the ticket's Pipeline note. No Application API, Supported SPI, persisted/serialized contract, diagnostic schema, bean-replacement contract, or compatibility shim is added.

## Skill-Authoring Documentation Impact

- **Assessment:** No impact
- **Rationale:** The diff changes application/operator servlet startup classification and test wiring only; it does not change manifests, model selection, planning/execution semantics, evidence, skill I/O, capability visibility, RBAC, attachments, quotas, traces, debugging, or skill testing guidance.
- **Documents reviewed:** `README.md`, `ai/commands/shared/loomspan-docs-protocol.md`
- **Evidence checked:** Detector and auto-configuration source, focused detector/security/Actuator tests, public-surface architecture test, and dependent Sidecar integration
- **Coverage table:** Not applicable
- **LLM-first usability:** Not applicable
- **Drift classification:** Aligned after the fix; executable behavior now matches the README's disable-on-overlapping-application-mapping statement without documenting internal enumeration edge cases.

## Residual Risks and Optional Developer Checks

- None.

## Disposition

- **Candidate clean; fresh review required** — one P2 finding was fixed in this context, the final internal re-review found no remaining actionable issues, and all required verification passed.
