# PR 28.7 — Complete executor integration verification and consumer guidance

## Outcome

Finish the PR 28 series with a coherent working Framework and Console, representative application-owned executors, complete consumer documentation, and evidence that the accepted architecture and production boundaries hold together. Close integration gaps rather than treating prior ticket completion as proof of feature readiness.

## Requirements

- Verify the complete feature delivered by PR 28.1–28.6 using shared Java-form tests plus focused boundary integrations. Reuse useful existing REST scenarios as executor scenarios, removing obsolete assertions instead of retaining old production behavior. Keep tests already owned by earlier tickets; add end-to-end coverage where those tests cannot prove integration.
- Demonstrate an executor-backed skill calling its hosting/calling application or another microservice through a representative local HTTP integration. Include a SQL-shaped executor/configuration example or fixture as a second use case showing that both configuration maps remain general application-owned data. This does not require production database infrastructure or Framework HTTP/SQL implementations, domain schemas, transport policies, or Sidecar migration.
- Exercise real Spring proxies, validated/reference-resolved inputs including `ref://` resource leaves, root and child execution, composed authorization/authentication restoration, shared Java output conversion, observations, cancellation/limits, and retained foreign executor configuration through publication/removal/recreation. Preserve root-reachable authority; a retained executor namespace does not expose unrelated skills.
- Verify direct failure propagation, actual standard-tool-manager designated feedback, step termination, failed-result exclusion, canonical failure identity/classification/cause linkage, Console provenance, and normal accounting/deadlines in the integrated system. No step correction loops, planner inability outcomes, or broader recovery are included.
- Finish README, packaged `agent-skills/loomspan-docs` Java API/authoring/configuration/reload/observation guidance and indexes, runnable samples, manifests, current fixtures/tests, and repository instructions. Replace obsolete REST-skill guidance with executor guidance and remove stale links. Preserve historical design context where appropriate; it must not be presented as current consumer instructions.
- Explain the supported annotation/record/exception contracts, reference-only identity, `SkillKind.JAVA` catalog behavior, separate skill/executor configuration, dot-path and null/omission rules, executor namespace ownership, complete replacement, metadata-only validation, and invocation-only domain errors. Document deliberate SkillMethod string/null/failure changes and the three orchestration routes accurately.
- Explain application-owned resource management: `executorConfigGenerationId()` is the captured executor namespace generation ID even without a config path, not a resource-management promise. Applications may register existing public `SkillReloader.onGenerationRetired(...)` listeners early, match IDs, keep callbacks prompt, and own cleanup/shutdown because notifications may be omitted during shutdown. No new lifecycle hook or context field. Resource handles do not snapshot underlying bytes.
- Audit the final architecture for duplicate Java discovery/invocation, authorization evaluators, configuration authorities, dependency rules, failure classifiers, implicit declaration-kind inference, and obsolete REST/transformer paths. Confirm every necessary SkillMethod/SkillExecutor difference has a concrete requirement and recorded justification. Resolve in-scope residual duplication; do not add compatibility or speculative abstractions merely to finish verification.
- Complete all affected Framework/Console checks, including `LoomspanPublicSurfaceArchitectureTest` after production-type changes, current Java/Go fixture parity, and the repository-prescribed Console verification. Fix in-scope integration failures. Record actual results and genuine environmental limitations without calling unexecuted checks passing.

## Acceptance criteria

- [ ] Representative HTTP invocation proves the feature can call application/microservice code without consumer migration; the second configuration use case proves no Framework domain interpretation or enforced split between operation/infrastructure data.
- [ ] Integrated tests demonstrate proxy/security, input/reference, root/child, observation/cancellation/limits, and retained-generation boundaries for both Java forms and executor-specific configuration. Tests use the actual startup ingestion and standard tool-manager paths where those boundaries matter.
- [ ] The three failure routes, failed-result exclusion, budget/deadline continuity, canonical identity/classification, and current Console provenance work together with no recovery extension or automatic retry.
- [ ] Consumer docs, samples, allowlists, fixtures, and repository guidance describe only the final supported contract; REST skill API/kind/manifest/runtime and old Java error-text behavior are gone. No compatibility shims or temporary duplicate subsystems remain. `CapabilityKind` assessment/removal evidence is present.
- [ ] All affected builds and required checks pass, or a genuine external verification blocker is explicitly reported without declaring the feature complete. Production contract defects are fixed within the accepted architecture.

## Context and sequencing

Depends on [PR 28.6](loomspan-pr-28.6-catalog-provenance-and-console.md) and all prior PR 28 tickets. Read [the accepted design](../phases/configured-java-skill-executors.md) and [feature design lens](../framework-feature-design-lens.md). All eight findings are requirements, not eight independent subsystems. The source was rechecked before ticket creation; existing REST tests are scenario evidence, not behavior to preserve. No executor production implementation was performed during ticket writing.

Coverage ownership for the series:

| Accepted finding | Implementation ownership |
| --- | --- |
| 1 — Unified dependency resolution/retention | PR 28.3 |
| 2 — Shared authorization | PR 28.4 |
| 3 — Invocation/failure policy | PR 28.1 shared invoker/classifier; PR 28.5 orchestration |
| 4 — Canonical failure meaning/identity | PR 28.5, including current diagnostic consumers |
| 5 — Classification/provenance | PR 28.3 routing/enum assessment; PR 28.6 catalog/Console |
| 6 — Immutable generation configuration | PR 28.1 copy primitive; PR 28.2 ingress; PR 28.3 ownership/binding |
| 7 — Java discovery/proxy invocation | PR 28.1 |
| 8 — Authoritative manifests/REST removal | PR 28.2 declarations; PR 28.3 runtime/API; PR 28.6 diagnostic contracts |

Each implementation ticket owns its focused tests and directly affected guidance. This ticket owns final integration, complete consumer guidance, and removal/completion audit, not postponed basic correctness.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** This is final cross-component assurance and correction of an architectural feature, including production boundaries and removal completeness, not a documentation-only cleanup.
- **Reassessment triggers:** An unmet accepted requirement or residual production defect; fix within scope, and raise a genuinely new product decision with concrete evidence rather than silently expanding architecture.

## Pipeline notes

- Framework and Sidecar have no customers/deployments. Breaking changes and intermediate broken builds were authorized throughout PR 28; this final ticket must restore coherent working builds and finish the accepted feature. Do not add adapters or retain obsolete APIs to accomplish that.
- Sidecar's HTTP executor reimplementation and Planner Failure Recovery are separate efforts. Their absence is not a blocker and their implementation is not authorized here. Preserve content fidelity and the existing derived-journal/observer redaction exception without broadening it.
