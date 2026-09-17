## Code Review Findings

### [P3] Use source-label terminology throughout the authoring guide

- **Location:** `agent-skills/loomspan-docs/references/skill-authoring/mental-model.md:66`
- **Evidence:** The same guide describes an arbitrary `SkillDocument.sourceName` as a diagnostic label, and `SkillSourcePathResolver` returns it unchanged. A later sentence said Console showed the "source resource."
- **Trigger:** A skill author consults the capability-type section for an application-supplied document whose label has no backing resource or path.
- **Impact:** The guide implies a physical resource exists and conflicts with the new public input contract.
- **Recommendation:** Say Console shows the source label and original YAML.

## Findings Resolved in This Context

- [P3] Replaced "source resource" with "source label" in `mental-model.md:66`; checked the resulting diff and searched the affected authoring and Console guidance for stale source-path wording.

## Open Questions and Assumptions

- None. The companion generation-retirement ticket is outside this review scope and its untracked file was preserved.

## Verification Results

- PASS — `mvn '-Dtest=YamlSkillCatalogTests,SkillSourcePathResolverTest,DefaultRegisteredSkillCatalogTest,SkillGenerationManagerTest,SkillReloaderTest,PublicSkillReloadIntegrationTest,ConsoleRestFixtureCorpusTest,LoomspanPublicSurfaceArchitectureTest' test` (172 tests).
- PASS — `mvn test -q`.
- PASS — `go test ./...` from `loomspan-console`.
- PASS — `npm run typecheck` from `loomspan-console/web`.
- PASS — `npm test` from `loomspan-console/web` (47 files, 521 tests).
- PASS — `npm run build:web` from `loomspan-console/web`.
- PASS — `git diff --check` after the documentation fix.
- FAIL — `mvn -Dtest=YamlSkillCatalogTests,SkillSourcePathResolverTest,DefaultRegisteredSkillCatalogTest,SkillGenerationManagerTest,SkillReloaderTest,PublicSkillReloadIntegrationTest,ConsoleRestFixtureCorpusTest,LoomspanPublicSurfaceArchitectureTest test`: PowerShell parsed the unquoted comma-separated property as a parameter list; the quoted rerun passed.

## Requirements and Plan Conformance

- **Implemented:** The exact `SkillDocument` record and `SkillReloader` overload are present; configured startup and no-argument preparation remain. Supplied documents are copied, validated, parsed through the common manifest path, and assembled into the existing detached generation and publication lifecycle. Public integration tests exercise REST and model execution without skill files, failure recovery, old admitted work, restart resubmission, and cross-source alternation. Unit tests exercise identity, duplicate, field, model, child, empty-set, freeze, and lifecycle rules. The inspection boundary and Console fixture/UI accept a non-path label.
- **Partial:** None identified.
- **Missing:** None identified.
- **Safe deviations:** Internal catalog input uses a `ByteArrayResource` parser carrier with a separate explicit logical label. No public Spring resource requirement or path interpretation is introduced. The existing `sourcePath` JSON key remains while its value may be a logical label.
- **Compatibility review:** `SkillReloader` and the new record are deliberately supported Application API under the closed architecture allowlist; `RestSkillHandler` remains the sole SPI. The new abstract overload is the ticket-authorized addition; no default-method shim or new bean-replacement contract is justified. Configured keys and YAML manifest syntax remain intact. Java-to-Go current-version fixture, Go validation, UI wording, and exact release-string behavior are coordinated. Internal catalog and diagnostic classes remain implementation detail.

## Skill-Authoring Documentation Impact

- **Assessment:** Affected; the newly allowed diagnostic labels need author-facing explanation.
- **Rationale:** `sourceName` is exact diagnostic text and does not define callable identity or child relationships.
- **Documents reviewed:** `agent-skills/loomspan-docs/SKILL.md`, `references/skill-authoring/README.md`, `mental-model.md`, `rest-skills.md`, `source-verification.md`, and the related Java API and Console guidance.
- **Evidence checked:** `YamlSkillCatalog`, `YamlSkillSource`, `SkillSourcePathResolver`, generation and public integration tests, the Java/Go fixture corpus, and the Console component test. The checked-out skill and Maven project both identify `1.0.0-beta.5-SNAPSHOT`.
- **Coverage table:** Current after the change; source identity and REST diagnostics include supplied labels.
- **LLM-first usability:** Pass after the terminology fix. Drift classification: documentation drift found and resolved; remaining guidance is aligned with executable behavior.

## Residual Risks and Optional Developer Checks

- An application team may optionally verify its own durable snapshot, REST configuration staging, and traffic-readiness sequence in its environment; these are explicitly application-owned.

## Disposition

- **Candidate clean; fresh review required** — the P3 documentation finding was fixed in this review context, and the verification above passed.
