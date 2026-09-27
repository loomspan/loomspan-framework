# PR 11.1 Complete Execution Configuration — Independent Review 1

## Review Scope

Reviewed the unstaged diff on `main`: 16 changed framework production, test, and documentation files. There were no staged or committed branch changes. The untracked ticket, research, and plans were inputs; they are not implementation changes. No prior review artifact was read.

## Findings

No actionable findings.

## Findings Resolved in This Context

None. No implementation artifacts were changed in this review context.

## Open Questions and Assumptions

Sidecar PR 7.1 is a deferred companion release gate under the developer's framework-first sequencing decision recorded in the plan. Its future host-map acceptance test is not framework Step 5 evidence and is not claimed here.

## Verification Results

- PASS — `.\mvnw.cmd -B -ntp '-Dtest=ExecutionConfigurationParserTest,SkillGenerationManagerTest,PublicSkillReloadIntegrationTest,SpringAiProviderIntegrationTest,LoomspanPublicSurfaceArchitectureTest' test` (50 tests).
- PASS — `.\mvnw.cmd -B -ntp test` (1,196 tests).
- PASS — `git diff --check`.
- FAIL — initial unquoted PowerShell invocation of `-Dtest=...,...`: shell parser rejected the comma-separated argument before Maven started; corrected by quoting the Maven property above.

## Requirements and Plan Conformance

- **Implemented:** The new supported `SkillReloader.prepare(documents, configuration, credentialValues)` operation copies host values at entry; the parser requires every authored reference from that map and never consults `Environment` in map mode. Public integration exercises two distinct OpenAI keys and delayed/new roots against fake provider servers, with no sends during preparation. The existing environment path and fresh explicit defaults remain; the parser rejects process fields, literal credentials, duplicate keys, missing and unused references. In-memory Vertex JSON is locally parsed before activation. Generation capture, retry policy, depth, quotas, trace policy, client retirement, abandoned preparation, and shutdown retain existing lifecycle ownership, with focused and existing integration tests. README and bundled authoring/API guidance describe the contract and rotation order.
- **Partial/deferred:** Sidecar PR 7.1 must implement its encrypted host credential source and prove the supported overload against the installed snapshot before final framework release checks. The plan records the developer's framework-first sequencing decision. No Sidecar PR 7.1 acceptance evidence exists yet.
- **Missing framework requirements:** None found.
- **Safe deviations:** The implementation composes existing tests for nested, parallel, retry, physical lifetime, and full settings capture instead of adding one large combined test. The selectors and ownership boundaries are covered by existing focused tests plus the new public key contrast test.
- **Compatibility review:** `SkillReloader` is allowlisted Application API; the overload adds only JDK `Map` to an existing public type, preserving its prior signatures. `RestSkillHandler` remains the sole supported SPI. `LoomspanProperties` and `internal` types are integration machinery, not supported application extensions. Explicit YAML and file configuration behavior are documented contracts; direct-secret and process-field candidate rejection remain. The change introduces no Java-to-Go observability boundary alteration or persisted framework format. No compatibility shim is needed.

## Skill-Authoring Documentation Impact

- **Assessment:** Affected.
- **Rationale:** Credential sources, candidate preparation, generation capture, defaults, and rotation affect skill model configuration guidance.
- **Documents reviewed:** `README.md`, `agent-skills/loomspan-docs/references/java-api/README.md`, `agent-skills/loomspan-docs/references/java-api/skill-reload.md`, `agent-skills/loomspan-docs/references/skill-authoring/README.md`, and `agent-skills/loomspan-docs/references/skill-authoring/model-selection-and-connections.md`.
- **Evidence checked:** Parser, reloader, manager, runtime, provider construction, public integration and focused tests from this checkout; bundled `loomspan-docs` declares the matching `1.0.0-beta.6-SNAPSHOT` version.
- **Drift classification:** Aligned for the changed credential and publication claims. The guidance distinguishes enforced map validation from recommended rotation and the external-revocation limitation.
- **Coverage table:** Current; the model-selection and Java API rows were updated.
- **LLM-first usability:** Pass; model-selection routes to the focused publication contract without duplicating the full workflow.

## Residual Risks and Optional Developer Checks

No optional manual check is needed. The Sidecar PR 7.1 acceptance and release sequence remains a future companion gate, not an unverified framework test result.

## Disposition

**Approve.** Framework diff has no actionable findings after independent review and sufficient local verification.
