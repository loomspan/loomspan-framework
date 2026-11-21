# Independent review 2 — consumer executor configuration

## Code Review Findings

No remaining actionable findings. Initial review found one P3 documentation defect, corrected below. No P0, P1 or P2 findings.

## Findings Resolved in This Context

### [P3] Describe all three configuration maps in Java example guidance

- **Location:** `agent-skills/loomspan-docs/references/java-api/java-skills.md:209`.
- **Evidence:** The runnable-example paragraph still said “Both configuration maps” although `SkillExecutorInvocation` now supplies `skillConfig`, `executorConfig` and `consumerExecutorConfig`. Other sections already enumerate three configuration maps.
- **Trigger:** An author loads the Java declaration topic to understand the executor example and configuration ownership.
- **Impact:** The guidance incorrectly counts the supported configuration maps and leaves consumer configuration out of its statement about general application data.
- **Correction:** Changed “Both configuration” to “All three configuration”. No other implementation artifact was edited by this context.
- **Verification:** Re-read the complete relevant declaration/accessor guidance against the supported class and resolver; searched routed documentation for stale two-map wording; both repositories pass `git diff --check`. No test was added for this prose correction.

## Open Questions and Assumptions

- None requiring a developer answer. Full profile remains appropriate because the reviewed ticket changes a supported Java handoff and coordinated pack behavior.
- Review began from Framework `cd1c15237cb31770853dfb78a7399f1a4594dbd0` on `main` and Extensions `a6de3966e771a27683d9f6d8f4f91ba0006c3844` on `codex/scaffold-extensions`. Ticket changes are unstaged working-tree changes; there are no staged changes. The supplied untracked research/plans are process artifacts, not additional implementation source. Prior reviews were neither read nor used.
- The pre-existing Decisions `CHANGELOG.md` deletion is outside scope and was preserved. No branch-wide committed feature diff was supplied: the current HEADs are the comparison bases.

## Verification Results

Commands below were actually executed in this fresh context. Maven runs used elevated execution for Windows Mockito attachment and local Maven installation, with output redirected to review-specific temporary logs.

Framework root:

- PASS — `./mvnw.cmd -B '-Dtest=SkillExecutorTest,ApplicationApiValueTest,LoomspanPublicSurfaceArchitectureTest,SupportedSurfaceIntegrationTest,ExecutorConfigurationTests,ExecutorConfigurationStartupTests,ExecutionConfigurationParserTest,ExecutorConfigurationManagerTests,ExecutorDependencyResolutionTest,ExecutorGenerationExecutionIntegrationTest,NamespaceExecutionAuthorityIntegrationTest,SharedDeclarativeAuthorizationIntegrationTest,ExecutorInputTransformIntegrationTest,ExecutorInvocationLifecycleIntegrationTest,ExecutorDiagnosticsIntegrationTest' '-DreuseForks=false' test`: 96 tests, no failures/errors/skips. This includes the required architecture check after production changes.
- PASS — `./mvnw.cmd -B '-DskipTests' install`: installs the current compiled Framework artifact after focused verification; this command does not claim a full test-suite run.
- PASS — `git -c core.safecrlf=false diff --check`.

Extensions root:

- PASS — `mvn -B validate`.
- PASS — `mvn -B -f extensions/decisions/pom.xml install`: all 86 pack tests, no failures/errors/skips.
- PASS — `mvn -B -f extensions/decisions/examples/support-triage/pom.xml verify`: 21 tests, no failures/errors, one optional Console skip; both actual installed-JAR publication tests passed.
- PASS — `git -c core.safecrlf=false diff --check`.

The newly installed Framework JAR matches its target JAR with SHA-256 `5CA1209FF7E7A3582EB1585FFAF13EDEFBC6B381EA5E1B19276756A0AF1DCF1A`. Qualification used the installed Framework and Decisions artifacts, not source substitution or a mocked executor/transport.

- NOT RUN — full Framework repository test suite: this review ran the entire risk-focused 96-test selector instead; no production changes were made in this context. The earlier implementation's claimed full-suite result is not counted as verification performed by this review.
- NOT RUN — optional Console profile and hosted TypeSafe evaluation: no consumed Console protocol changed, and local integration rather than hosted quality/access is the ticket scope.

## Requirements and Plan Conformance

- **Implemented — direct and parent maps/input:** `resolveExecutor` selects the consumer from `definition.identity().namespace()`, independent of the root. `ExecutionCoordinator` passes three separate captured configuration maps through the sole private-constructor bridge. `ExecutorGenerationExecutionIntegrationTest` proves actual direct execution, two billing leaves, independent other consumer, support parent, same-namespace execution and unchanged business input.
- **Implemented — selection and fidelity:** one traversal implements exact dot-path semantics with mandatory owner selection and optional missing consumer keys. Present malformed nodes fail. The same-namespace branch and selector-free paths return empty consumer maps. Resolver tests protect missing/empty/case/literal-dot/malformed cases; API tests exercise recursive container copies, null and non-string nested keys, numeric fidelity, leaf identities, source mutation, map separation and cycle rejection. Existing immutable-container behavior is reused, without arbitrary-object freezing.
- **Implemented — publication/capture/authority:** generation capture already retains every reachable leaf and executor owner coherently. Binding reads only captured generation data, with no live invocation lookup or preparation-time foreign runtime binding. Actual retained-handoff tests prove old consumer and owner maps survive rotation/removal/recreation. Fresh calls see consumer-only rotation and omission; other consumers and the owner ID remain unchanged. Foreign-section rejection covers validation and both preparation paths, and an actual secured consumer invocation asserts denial without an application call. Existing namespace/private/declarative-advice regressions passed.
- **Implemented — TypeSafe policy and actual transport:** settings capture accepts only consumer `api_key`, rejects every consumer URL/unknown setting, selects the key by presence, validates only the selected key, and resolves endpoint exclusively from owner settings with the existing default. Transport captures settings once before its retry loop. Full pack tests cover invalid keys/URLs, owner fallback, exact values, zero-send rejection, concurrent keys, retry bytes, attempts and lifecycle. The installed-JAR suite exercises a Public owner without a key, foreign direct/parent leaves, rotation, omission/fallback, old retained endpoint/key, consumer URL rejection and owner-only operation.
- **Implemented — public surface and documentation:** one accessor is added to the already allowlisted final v1 invocation, with no public constructor/builder or new supported type. Exact method-set tests and architecture checks pass. README, Java/reload guidance, executor/TypeSafe topics, coverage indexes and pack documentation describe the current behavior. The stale map count found here is corrected.
- **Partial/Missing:** none after correction.
- **Safe deviations:** isolated Framework JVMs avoid the documented reused-JVM fixture interaction. Settings allowed-field checks live in the single pack settings collaborator; obsolete internal helper signatures are directly updated. Review verification uses a focused Framework suite plus a fresh install and full pack/consumer runs, rather than rerunning every unrelated Framework test.
- **Compatibility review:** supported Application API gains only `consumerExecutorConfig(): Map<String,Object>`. Existing accessors and owner-generation meaning remain. Namespace configuration/publication and provider precedence are protected behavior and have executable coverage. Internal binding/private constructor/request helper signatures change atomically without shims as expressly authorized by the ticket. No SPI, bean replacement surface, version/package change, serialized format, REST/SSE/NDJSON field or compatibility marker is introduced. No internal/autoconfigure type leaks into a supported signature.
- **Security/lifecycle/operations:** map ownership is selected from authoritative captured namespace metadata. Visibility, publication target authority, composed authorization and invocation-time proxy advice remain in place. No content classification/rewriting, credential resolver, automatic merge, new diagnostic field, cache or resource lifecycle was added. Existing derived-journal redaction is untouched. Settings stay request-local on the existing shared transport, whose lifecycle tests pass.

## Skill-Authoring Documentation Impact

- **Assessment:** Affected.
- **Rationale:** Authors need the third map, leaf-owned identity, optional selection, complete consumer publication, retained capture and provider-specific key/endpoint/retry policy.
- **Documents reviewed:** Framework README; `agent-skills/loomspan-docs/SKILL.md`; Java API and skill-authoring indexes; `java-skills.md`, `skill-reload.md`, `executor-skills.md`, `typesafe-skills.md`; pack README, executor/transport/tested-builds docs and support-triage README. Used the matching checkout's skill as router after executable-path investigation.
- **Evidence checked:** all changed production paths; capture/publication/parser/container collaborators; changed API/resolver/execution tests; full pack and installed example fixtures/tests; actual verification above.
- **Drift classification:** corrected the one stale Java-topic map count as documentation drift. The remaining reviewed feature guidance is aligned with matching source and focused/installed tests. No unresolved semantic discrepancy remains.
- **Coverage table:** Current; owner/consumer publication and TypeSafe provider-local evidence are routed to appropriate topics and named executable tests.
- **LLM-first usability:** Pass after the correction. Generic selection/capture, supported Java accessors and pack-specific precedence are separated into their owning topics, with concrete startup/publication examples and explicit hosted-provider limitations.

## Residual Risks and Optional Developer Checks

- Focused verification does not exercise every unrelated Framework subsystem; no complete Framework suite is claimed from this context. Actual ticket behaviors, required architecture classification and downstream installed artifacts were verified.
- Hosted TypeSafe availability/quality and optional Console inspection remain outside local acceptance scope. Optional developer checks: none.
- Because this context changed documentation, a new independent context must certify the resulting candidate; this context cannot return `clean`.

## Disposition

**Candidate clean; fresh review required.** One P3 finding was fixed; final internal re-review found no remaining actionable issues. Production code and tests were unchanged by this review.
