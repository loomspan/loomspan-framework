## Code Review Findings

No actionable findings.

## Findings Resolved in This Context

None.

## Open Questions and Assumptions

- This is the framework PR 11 review. The companion Sidecar PR 7 must separately compile and test against the installed framework artifact before the combined feature is complete.
- The external incidents demo must move its trace YAML key when adopting this revision, as the ticket explicitly notes.

## Verification Results

- PASS — `mvn '-Dtest=LoomspanPublicSurfaceArchitectureTest,LoomspanSessionPropertiesTest,ExecutionConfigurationParserTest,PublicSkillReloadIntegrationTest,SkillReloaderTest,SkillGenerationManagerTest' test` (51 tests).
- PASS — `mvn test` (1,189 tests).
- PASS — `mvn install -DskipTests` (local framework artifact installed).
- PASS — `git diff --check`.
- PASS — `rg -n '^execution-trace:' README.md src agent-skills` (no obsolete top-level YAML examples).
- FAIL — `mvn -Dtest=LoomspanPublicSurfaceArchitectureTest,LoomspanSessionPropertiesTest,ExecutionConfigurationParserTest,PublicSkillReloadIntegrationTest,SkillReloaderTest,SkillGenerationManagerTest test`: PowerShell parsed the unquoted comma list; the quoted rerun passed.

## Requirements and Plan Conformance

- Implemented: `ExecutionConfiguration` and the public `SkillReloader` overloads permit a complete candidate. The parser validates only execution settings, applies startup defaults, checks candidate model aliases, rejects process settings and direct secrets, and resolves external references during preparation. Public integration tests exercise new alias publication, pending root connection and retry retention, and lack of provider traffic during validation/preparation.
- Implemented: `SkillGeneration` now owns an execution runtime, and generation capture/owner leases retain clients until physical work completes. Session creation selects captured depth and trace policy; binding scoped execution selects captured connections, timeout, attachment size, and quotas. Focused tests exercise captured trace/depth/quota, provider retry/connection overlap, candidate closure, retirement, and YAML startup binding.
- Implemented: `application.yml` remains the embedded startup path. `loomspan.execution-trace.persistence` binds with the existing `ONERROR` default; the former top-level key is intentionally unbound. Public docs and bundled authoring topics describe the migration and supported API boundary.
- Partial: The dedicated testing plan proposes more individual boundary tests for each quota, attachment limit, timeout, concurrent trace persistence, and provider construction faults than the present suite contains. Source paths were inspected and full regression passed; a real external provider check remains optional. This does not block the reviewed framework change, but Sidecar integration is required for the combined feature.
- Safe deviations: Candidate parsing uses the existing default YAML mapper for supplied skills and parses authored settings twice so references are resolved only after skill validation. This does not change publication semantics.
- Compatibility review: `SkillReloader` and `PreparedSkillUpdate` are allowlisted Application API. Existing overloads remain; `close()` has a default implementation. The new `ExecutionConfiguration` is allowlisted and public signatures expose no internal/autoconfigure type. `RestSkillHandler` remains the sole SPI. The trace-key move is the narrow pre-1.0 break authorized in the ticket's Pipeline notes; no legacy alias was retained. Internal and autoconfigure types are not supported Java API per `AGENTS.md`.

## Skill-Authoring Documentation Impact

- **Assessment:** Affected.
- **Rationale:** Model aliases/connections, retries, limits, and traces now follow captured generations.
- **Documents reviewed:** `agent-skills/loomspan-docs/references/skill-authoring/README.md`, `mental-model.md`, `model-selection-and-connections.md`, `traces-and-debugging.md`, `validation-workflow.md`, and Java API `skill-reload.md`/`compatibility-and-boundaries.md`.
- **Evidence checked:** Candidate parser, generation/runtime wiring, session and provider call paths, focused tests, and startup YAML test.
- **Coverage table:** Current; it accurately marks detailed per-limit guidance incomplete.
- **LLM-first usability:** Pass; publication workflow is routed to the Java API topic and model guidance links there without duplicating the full contract.
- **Drift classification:** aligned for the behavior covered by source and focused tests.

## Residual Risks and Optional Developer Checks

- Optional: in an environment with a provisioned external credential, publish a new connection and make a real provider request to check third-party SDK provisioning.
- Sidecar PR 7 should be built and tested using the locally installed framework artifact; no Sidecar integration result is claimed here.

## Disposition

**Approve** — no actionable findings; verification is sufficient for the framework change.
