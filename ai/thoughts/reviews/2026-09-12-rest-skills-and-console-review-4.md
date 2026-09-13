# REST Skills and Console Integration — Review 4

## Code Review Findings

### [P2] Reject source-inapplicable skill fields even when JSON encodes them as null

- **Location:** `loomspan-console/internal/observability/dto.go:144`
- **Evidence:** `validateSkillWireFields` inspected raw JSON field presence but exempted a present field whose raw value was `null`. The same function's contract says source-inapplicable fields are rejected even with empty values, and the testing plan requires strict discriminated source validation across the Java-to-Go boundary.
- **Trigger:** A REST/YAML skill payload containing `"beanName": null` or `"method": null`, or a Java skill payload containing `"sourcePath": null` or `"yaml": null`.
- **Impact:** A malformed or version-skewed producer payload could pass Go decoding despite violating the source-specific REST/YAML/Java shape, weakening exact-version boundary validation and allowing drift to reach MCP/browser consumers.
- **Recommendation:** Reject every inapplicable field by declaration presence, including JSON null, and cover both summary and detail decoders.

### [P3] Describe all three Console diagnostic source variants

- **Location:** `agent-skills/loomspan-docs/references/skill-authoring/mental-model.md:173`
- **Evidence:** The implementation and the same document describe YAML, REST, and Java catalog variants, but the implementation-anchor summary still said the tests cover “both diagnostic source variants.”
- **Trigger:** An LLM uses the mental-model implementation anchors to understand the REST catalog addition.
- **Impact:** The version-aligned guidance understates the new REST variant and conflicts with the document's own executable-evidence map.
- **Recommendation:** State that the cited tests cover all three diagnostic source variants.

## Findings Resolved in This Context

- **P2 resolved:** `validateSkillWireFields` now rejects inapplicable fields solely by raw JSON presence. `TestSkillDecodersRejectInapplicableFieldsByPresence` covers null and empty fields for REST and Java summary/detail payloads. Focused Go tests and full Console verification pass.
- **P3 resolved:** the skill-authoring mental model now says the catalog/fixture/component evidence covers all three source variants.

## Open Questions and Assumptions

- None. The ticket's Pipeline notes clearly authorize the narrow new SPI, REST-only handler cardinality, REST-visible exception behavior, and same-version Console protocol expansion without a legacy reader.

## Verification Results

- PASS — `.\mvnw.cmd test` (1,121 tests)
- PASS — `go test ./internal/observability ./internal/mcpadapter`
- PASS — `.\mvnw.cmd -pl loomspan-spring-boot-starter "-Dtest=ConsoleRestFixtureCorpusTest" "-Dloomspan.console.fixtures.regenerate=true" "-DfailIfNoTests=false" test`
- PASS — repeated `.\mvnw.cmd -pl loomspan-spring-boot-starter "-Dtest=ConsoleRestFixtureCorpusTest" "-Dloomspan.console.fixtures.regenerate=true" "-DfailIfNoTests=false" test`; before/after SHA-256 hashes were identical for every `application-rest` fixture
- PASS — `go run ./internal/buildtool verify` (47 web test files / 521 tests, build, Go repository tests, and configured verification)
- PASS — `git diff --check`
- FAIL — `.\mvnw.cmd -pl loomspan-spring-boot-starter -Dtest=ConsoleRestFixtureCorpusTest -Dloomspan.console.fixtures.regenerate=true -DfailIfNoTests=false test`: PowerShell parsed the dotted unquoted `-D` argument as a lifecycle phase; the corrected quoted invocation passed twice.

## Requirements and Plan Conformance

- **Implemented:** The closed public surface contains exactly the ticketed `RestSkillHandler` and `RestSkillInvocation` additions with JDK-only signatures, null rejection, recursive map/list snapshots, null preservation, and Resource leaf identity. Architecture, public-value, and supported-surface tests provide executable evidence.
- **Implemented:** REST manifests accept only Boolean `true`, forbid every model/planning/output field by declaration presence, retain generic or explicit input contracts and YAML roles, avoid model resolution/configuration, and preserve model-backed YAML validation.
- **Implemented:** Registration uses `REST_SKILL`, `SkillExecutionDescriptor.none()`, the shared exact-name registry, YAML declaration locations, eager completion, conditional exactly-one-handler validation, and post-discovery child-reference validation.
- **Implemented:** Root and nested REST invocations reuse input validation, reference resolution, authorization, scoped authentication, mission timeout/cutoff/fencing, task/evidence success credit, and direct trace lifecycle. Null/empty/failure/fatal boundaries remain distinct from Java's exception-to-text adapter.
- **Implemented:** Java producer, fixtures, Go decoder/service/MCP, TypeScript unions, React catalog/detail, and e2e behavior agree on REST path/YAML diagnostics without handler target leakage or a trace kind field. The exact project-version and dual-development-marker policy remains unchanged.
- **Implemented:** Root README and both matching-checkout knowledge sets route and document REST manifest/SPI/security/failure/diagnostic semantics. The stale source-count wording found in this review was corrected.
- **Partial:** The testing plan's historical first-red observation cannot be reproduced from the completed repository state; it is correctly left unchecked and is not a current behavior gap.
- **Missing:** None.
- **Safe deviations:** Tests are placed in existing broad lifecycle/registration fixtures rather than every suggested optional new class; their externally meaningful coverage matches the ticket.
- **Compatibility review:** The two allowlisted API types are the only supported-surface expansion. Internal/autoconfigure changes have no shim, no bean-replacement contract was added, model-YAML and Java behavior remain protected, and the atomic REST Console expansion follows the ticket-authorized beta-4 exact-version policy with no schema counter or backward reader.

## Skill-Authoring Documentation Impact

- **Assessment:** Affected; final state aligned.
- **Rationale:** REST adds author-visible manifest syntax, handler availability, immutable/resolved input, YAML RBAC, direct lifecycle, failure behavior, and Console diagnostics.
- **Documents reviewed:** `agent-skills/loomspan-docs/references/skill-authoring/README.md`, `source-verification.md`, `mental-model.md`, `rest-skills.md`; `agent-skills/loomspan-docs/references/java-api/README.md`, `compatibility-and-boundaries.md`, and `rest-skills.md`; root `README.md`.
- **Evidence checked:** `YamlSkillCatalog`, `YamlSkillDefinition`, `YamlSkillCapabilityRegistrar`, `ExecutionCoordinator`, public API/architecture tests, supported-surface integration, registered-skill catalog/fixture tests, Go DTO/service/MCP tests, TypeScript/React/e2e tests, and full verification.
- **Coverage table:** Current after the correction.
- **LLM-first usability:** Pass. Routing is focused, field rules are tabular, enforced behavior is separated from limitations, and stable test/source anchors are provided.
- **Version note:** The installed `loomspan-docs` skill reports `0.1.0-SNAPSHOT`, while this checkout is `1.0.0-beta.4-SNAPSHOT`; the installed skill was used only for routing/protocol, and version-sensitive conclusions use matching-checkout documentation, source, fixtures, and tests.

## Residual Risks and Optional Developer Checks

- None. The npm audit output reported three moderate dependency advisories, but the declared Console verification passed and no evidence tied those pre-existing advisories to this change.

## Disposition

- **Candidate clean; fresh review required** — two actionable findings were fixed in this context, the resulting state was re-reviewed, and required verification passes.

## Step Report: 5_code_review
STATUS: complete
ARTIFACTS:
  - ai/thoughts/reviews/2026-09-12-rest-skills-and-console-review-4.md
SUMMARY: Candidate clean after resolving one P2 Java-to-Go validation defect and one P3 documentation drift item. Fresh review required because implementation artifacts changed.
DECISIONS:
  - Treat JSON null as field presence at the source-discriminated Console boundary because the plan requires inapplicable fields to be rejected and canonical Java fixtures omit them.
DEVELOPER QUESTION: none
EVIDENCE: none
RECOMMENDATION: none
VERIFICATION:
  - PASS — `.\mvnw.cmd test`
  - PASS — `go test ./internal/observability ./internal/mcpadapter`
  - PASS — `.\mvnw.cmd -pl loomspan-spring-boot-starter "-Dtest=ConsoleRestFixtureCorpusTest" "-Dloomspan.console.fixtures.regenerate=true" "-DfailIfNoTests=false" test` (twice; identical hashes)
  - PASS — `go run ./internal/buildtool verify`
  - PASS — `git diff --check`
  - FAIL — `.\mvnw.cmd -pl loomspan-spring-boot-starter -Dtest=ConsoleRestFixtureCorpusTest -Dloomspan.console.fixtures.regenerate=true -DfailIfNoTests=false test`: unquoted dotted PowerShell argument; corrected invocation passed
OPTIONAL_DEVELOPER_CHECKS:
  - none
REVIEW_RESULT: fixes-applied
NEXT: Launch a fresh step-5 review context against the updated repository state.
