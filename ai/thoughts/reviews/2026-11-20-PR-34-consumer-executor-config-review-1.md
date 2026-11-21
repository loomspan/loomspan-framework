## Code Review Findings

No actionable findings remain after the correction below. The initial independent review identified one P2 documentation defect; no P0, P1 or P3 findings.

## Findings Resolved in This Context

### [P2] Preserve Public visibility in the shared billing publication examples

- **Location:** `agent-skills/loomspan-docs/references/java-api/skill-reload.md:259`; sibling `../loomspan-extensions/extensions/decisions/docs/executor.md:185`.
- **Evidence:** Both new examples originally called `prepare("billing", billingDocuments, config)` while their surrounding guidance described continued support-parent invocation of billing leaves. `DefaultSkillReloader.prepare` supplies `NamespaceVisibility.PRIVATE` for this overload on every publication, rather than retaining the previous visibility. `SkillReloaderTest#targetOnlyReplacementResetsVisibilityConfigurationAndOmittedSkills` explicitly verifies that behavior; `SkillDependencyValidator.resolveReference` denies foreign references to Private namespaces.
- **Trigger:** Follow either example to rotate configuration for Public billing leaves used by a support parent.
- **Impact:** Fresh support-parent admission fails after publication even though direct billing calls still work, contradicting the documented recipe.
- **Correction:** Both examples now pass `NamespaceVisibility.PUBLIC` explicitly and explain the default. This is a documentation correction, not a change to visibility semantics or the ticket's general three-argument publication example, which remains valid for direct-only use.
- **Verification:** The full Framework install passed, including the visibility-default regression and actual Public consumer/parent publication tests. Installed-JAR `PublicationConsumerTest` also passed. Both repository whitespace checks passed. No new implementation-mirroring test was needed for the documentation-only fix.

## Open Questions and Assumptions

- None. The selected profile remains full: this work affects supported API and cross-repository executor configuration.

## Review Scope and Independent Assessment

- Reviewed the current unstaged change against Framework HEAD `cd1c15237cb31770853dfb78a7399f1a4594dbd0` and Extensions HEAD `a6de3966e771a27683d9f6d8f4f91ba0006c3844`, both on their current checkouts. Neither repository had staged changes. Untracked research/plans were read as supplied governing artifacts; prior review artifacts were neither located nor read. The existing sibling Decisions CHANGELOG deletion is unrelated and preserved.
- Inventory: six Framework production files, API/value and executor resolution/integration tests plus mechanical internal factory callers; four Decisions production files, settings/request/transport tests and installed-artifact example harness/tests; Framework and pack consumer/authoring documentation. No versions, dependencies, serialized protocols, Console payloads or compatibility markers changed.
- Traced configuration parsing and target authority through immutable generations, candidate overlay, coherent reachable capture, admission binding and actual `ExecutionCoordinator` invocation. Consumer selection uses the leaf namespace, the same selector, optional missing keys and strict malformed nodes. Same-namespace/selector-free paths retain owner semantics. Both maps remain captured through existing leases, without a live registry lookup or merge.
- Reviewed recursive container copying, null/key/scalar/object fidelity, supported signature closure, authorization and visibility checks, preparation versus admission ownership, and current-run diagnostics. The journal exception is untouched; no content classification or rewriting was introduced.
- Traced TypeSafe selected-key presence/fallback and validation, owner-only endpoint, unknown/disallowed setting rejection, request preparation, capture before retries, shared transport lifecycle and physical sends. Existing local HTTP and installed-artifact tests assert destination, header, body, retry bounds and isolation rather than only mocked handoff values.
- Completed the independent defect review before plan-conformance assessment and editing. Re-reviewed the complete resulting change and the two corrected examples; no further actionable findings remain.

## Verification Results

Commands below were executed in this fresh review context, using escalated execution for Windows Mockito attachment and Maven artifact installation.

Framework working directory `C:/opendev/code/loomspan-framework`:

- PASS — `./mvnw.cmd -B '-Dtest=SkillExecutorTest,ApplicationApiValueTest,LoomspanPublicSurfaceArchitectureTest,SupportedSurfaceIntegrationTest,ExecutorConfigurationTests,ExecutorConfigurationStartupTests,ExecutionConfigurationParserTest,ExecutorConfigurationManagerTests,ExecutorDependencyResolutionTest,ExecutorGenerationExecutionIntegrationTest,NamespaceExecutionAuthorityIntegrationTest,SharedDeclarativeAuthorizationIntegrationTest,ExecutorInputTransformIntegrationTest,ExecutorInvocationLifecycleIntegrationTest,ExecutorDiagnosticsIntegrationTest' '-DreuseForks=false' test`: 96 tests, no failures/errors/skips; architecture suite ran 8 tests.
- PASS — `./mvnw.cmd -B '-DreuseForks=false' '-DforkCount=4' '-Dsurefire.rerunFailingTestsCount=1' install`: 2,379 tests, no final failures/errors, two skips, one reported flake; installed current Framework. The unchanged `ConcurrentGroupedExecutionIntegrationTest#parentTimeoutRevokesLateWritesFromAnActualNestedDirectMission` failed its child-start assertion at line 402 under the 100 ms parent budget, then passed its configured immediate retry in 0.422 seconds. Its model-only direct-mission path does not reach the changed executor invocation branch; this reproduces the Step 4 baseline observation, not a new ticket defect.
- PASS — `git -c core.safecrlf=false diff --check`.

Extensions working directory `C:/opendev/code/loomspan-extensions`:

- PASS — `mvn -B validate`.
- PASS — `mvn -B -f extensions/decisions/pom.xml '-Dtest=TypeSafeSettingsTest,TypeSafeRequestTest,TypeSafeTransportTest' test`: 69 tests, no failures/errors/skips, after current Framework install.
- PASS — `mvn -B -f extensions/decisions/pom.xml install`: 86 tests, no failures/errors/skips.
- PASS — `mvn -B -f extensions/decisions/examples/support-triage/pom.xml verify`: 21 tests, no failures/errors, one optional Console skip; both actual installed-pack publication tests passed.
- PASS — `git -C ../loomspan-extensions -c core.safecrlf=false diff --check` (run from Framework).

Installed and target JAR SHA-256 values matched: Framework `5CA1209FF7E7A3582EB1585FFAF13EDEFBC6B381EA5E1B19276756A0AF1DCF1A`; Decisions `655D482A19483860EF1F3FB555DBACFD6D9FFECA196235E093EEC3C5D4245580`. No source-tree substitution or hosted provider was used.

## Requirements and Plan Conformance

- **Implemented — direct/parent maps and isolation:** `ExecutorGenerationExecutionIntegrationTest#leafConsumerConfigurationIsCapturedIndependentlyAcrossParentsAndPublication` exercises two billing leaves, an independent consumer, a deliberately different support map, same-namespace behavior and explicit business input. Installed-pack publication tests independently assert actual selected credentials and native request bodies. Existing input/Resource identity and authority/advice regressions pass.
- **Implemented — immutable supported accessor and lookup:** `SkillExecutorTest`, `ApplicationApiValueTest`, resolver optional/malformed matrices, startup/parser tests and the architecture suite verify the added map, independent values, container fidelity, exact selector rules, required owner behavior and empty cases. The exact supported invocation method set adds only `consumerExecutorConfig`.
- **Implemented — publication/capture/authority:** Framework integration and installed-pack tests verify rotation, omitted consumer data, retained handoff maps/endpoint, owner ID stability, independent namespaces and target-only publication rejection. Existing retained-owner replacement/removal/recreation, coherent capture, candidate overlay and authorization tests remain green.
- **Implemented — TypeSafe policy and actual transport:** settings tests verify consumer presence, malformed-key failure without fallback, optional unused owner key, disallowed URL/unknown fields and default/prefix behavior. Real transport tests cover captured values, concurrency and retries; installed-artifact tests cover the complete Framework-to-executor-to-HTTP path and owner-only behavior.
- **Implemented — documentation and supported surface:** reviewed README, JavaDoc, both routed authoring/Java indexes, executor, TypeSafe and reload topics, pack README/executor/transport/tested-builds and example README. Corrected the publication visibility defect found here. Architecture ran after production changes.
- **Partial/Missing:** None.
- **Safe deviations:** Domain allowed-field validation moved coherently from request preparation into settings capture; all callers are updated. Isolated JVMs and the documented warm retry address verification-environment behavior without unrelated implementation changes. The plan's permitted compile-red/source-evidence route is recorded accurately; no additional historical red claim was inferred.
- **Compatibility review:** The closed allowlist deliberately supports the v1 invocation; it gains one accessor with no internal types in its public signature. No public constructor, builder, new supported type, SPI or bean replacement surface was added. Internal factory/binding and pack collaborator signatures update atomically without obsolete overloads, consistent with the ticket's coordinated no-shim decision. Existing accessor meanings and artifact/v1 versions are retained. No Java-to-Go coordination is needed because the consumed boundary is unchanged.

## Skill-Authoring Documentation Impact

- **Assessment:** Affected.
- **Rationale:** Authors need leaf-owned consumer identity, optional versus malformed selection, independent captured maps, complete target replacement and TypeSafe-specific key/endpoint policy.
- **Documents reviewed:** Framework `README.md`; `SkillExecutor`/`SkillExecutorInvocation` JavaDoc; `agent-skills/loomspan-docs/SKILL.md`; skill-authoring index/source-verification/executor/TypeSafe guidance; Java API index/declarations/reload guidance; sibling Decisions README, executor/transport/tested-builds and support-triage README.
- **Evidence checked:** Production selection/copy/capture/authority/transport paths; resolver, API, generation execution, authority/advice, settings/HTTP and installed-JAR consumer tests described above.
- **Coverage table:** Current. Owner/consumer maps and provider-specific retries/limits are routed to their owning topics and named evidence.
- **Drift classification:** Aligned after the two publication examples were corrected. The implementation also corrects the previous TypeSafe blanket leaf-config rejection to the existing 1–3/default-3 retry rule. The checked-out skill version matches the Framework version; no separately installed skill was substituted.
- **LLM-first usability:** Pass after correction. Examples distinguish required owner maps, optional consumer data, exact supported accessors, complete publication and Public sharing. Hosted-provider quality and account access remain explicitly outside local evidence.

## Residual Risks and Optional Developer Checks

- One unchanged timing-sensitive test flaked and passed immediate retry; reported explicitly above. Two existing Framework skips and the optional consumer Console skip remain.
- NOT RUN — optional Console-profile verification, demo `exec:java`, hosted-provider access/quality: outside ticket acceptance; no consumed protocol changed. Local real executor/transport coverage is complete.
- Optional developer checks: None. No unresolved acceptance gap remains in this context, but these documentation fixes require another fresh independent review under the pipeline rules.

## Disposition

**Candidate clean; fresh review required.** One P2 documentation finding was fixed. No implementation behavior was changed and no actionable finding remains; this context returns `REVIEW_RESULT: fixes-applied`.
