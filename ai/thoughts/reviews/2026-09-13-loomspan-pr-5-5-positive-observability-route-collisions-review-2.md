## Code Review Findings

### [P2] Retain deeper compound functional-route collisions

- **Location:** `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/observability/web/ObservabilityRouteCollisionDetector.java:430`
- **Evidence:** The initial implementation reduced an `and` of two path predicates to literal candidates plus only the reserved root and one-child probe. Two parsed predicates such as `/_loomspan/observability/v1/{collection}/{id}` and `/_loomspan/observability/v1/skills/{name}` therefore produced no intersection even though both match the concrete reserved path `/_loomspan/observability/v1/skills/reserved-probe`; the focused regression failed before the correction.
- **Trigger:** An application registers a functional route whose compound path predicates intersect inside the reserved namespace below the first child, including a broad catch-all combined with a deeper dynamic predicate.
- **Impact:** The detector reports no collision for an inspectable application claim under Loomspan's reserved namespace, so Console activation can proceed contrary to the protected confirmed-collision contract.
- **Recommendation:** Synthesize concrete candidates from the parsed predicate patterns and aligned segments, count them only after both Spring `PathPattern` instances match the candidate, and retain focused dynamic/catch-all regressions.

## Findings Resolved in This Context

- **P2 — deeper compound functional-route collisions:** Added pattern-derived and aligned-segment witnesses that are accepted only after both parsed Spring patterns match, plus regressions for intersecting dynamic predicates and catch-all-plus-dynamic predicates. The focused detector suite, web-boundary integrations, public-surface architecture checks, full framework build, local install, and downstream Sidecar acceptance all pass.

## Open Questions and Assumptions

- None.

## Verification Results

- PASS — `.\mvnw.cmd --batch-mode --no-transfer-progress -pl loomspan-spring-boot-starter '-Dtest=ObservabilityRouteCollisionDetectorTest,ObservabilityActuatorCoexistenceIntegrationTest,ObservabilityCollisionIntegrationTest,ObservabilityHostSecurityIntegrationTest' test`
- FAIL — `.\mvnw.cmd --batch-mode --no-transfer-progress -pl loomspan-spring-boot-starter -Dtest=ObservabilityRouteCollisionDetectorTest test`: the newly added compound-dynamic regression returned `false` before the fix, confirming the finding.
- PASS — `.\mvnw.cmd --batch-mode --no-transfer-progress -pl loomspan-spring-boot-starter -Dtest=ObservabilityRouteCollisionDetectorTest test`
- PASS — `.\mvnw.cmd --batch-mode --no-transfer-progress -pl loomspan-spring-boot-starter '-Dtest=ObservabilityRouteCollisionDetectorTest,LoomspanPublicSurfaceArchitectureTest' test`
- PASS — `.\mvnw.cmd --batch-mode --no-transfer-progress verify`
- PASS — `.\mvnw.cmd --batch-mode --no-transfer-progress install`
- PASS — `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=ConsoleSecurityIntegrationTest test` (from `C:\opendev\code\loomspan-sidecar`)
- PASS — `git diff --check -- . ':(exclude)ai/commands/0_run_pipeline.md' ':(exclude)ai/commands/shared/automation-protocol.md' ':(exclude)ai/commands/write_ticket.md' ':(exclude)ai/thoughts/reviews/**'`

## Requirements and Plan Conformance

- **Implemented:** Unknown and unenumerable handler mappings no longer collide without path evidence; all `RequestMappingInfoHandlerMapping` implementations are enumerated; inspectable exact, child, dynamic, wildcard, explicit URL, resource, and functional overlaps remain protected; unclassifiable functional forms and parse failures do not independently collide; real Boot 4.1 Actuator on a separate management port coexists with enabled Console; valid-key success and missing-key `401` are verified.
- **Partial:** None.
- **Missing:** None.
- **Safe deviations:** The final detector additionally synthesizes and validates concrete witnesses for compound functional intersections. This stays within the plan's positive-evidence rule and closes a confirmed-collision gap exposed during review.
- **Compatibility review:** The detector and registrar remain internal framework machinery; the supported Java allowlist and Spring replacement surface are unchanged. The ticket-authorized removal of unknown-as-collision behavior is contained, while the documented reserved namespace, API-key requirement, confirmed-collision disablement, and host-security coexistence remain protected. No shim is justified because the obsolete behavior is internal and intentionally replaced atomically; there is no Java-to-Go payload or persistence change.

## Skill-Authoring Documentation Impact

- **Assessment:** No impact
- **Rationale:** The diff changes application/operator servlet route discovery and test wiring only; it does not alter manifests, model selection, capability visibility, planning/execution semantics, evidence, author inputs/outputs, RBAC, attachments, limits, traces, debugging, or skill testing guidance.
- **Documents reviewed:** `README.md`; `agent-skills/loomspan-docs/references/skill-authoring/README.md`
- **Evidence checked:** Detector source, servlet auto-configuration and registrar/filter wiring, focused detector tests, Actuator/collision/host-security integrations, and the downstream Sidecar security acceptance test.
- **Coverage table:** Not applicable
- **LLM-first usability:** Not applicable

## Residual Risks and Optional Developer Checks

- None.

## Disposition

- **Candidate clean; fresh review required** — the one P2 finding was fixed and the final internal re-review found no remaining actionable findings, but this context changed production code and tests.
