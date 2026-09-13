# PR 5.4 Framework Documentation and Gated Release Readiness Testing Plan

## Change Summary

- Convert the existing cumulative supported-surface test from direct LLM execution to an explicit YAML planner that invokes one Java and one REST leaf through supported APIs.
- Complete README and beta 4 compatibility guidance against already-implemented REST, catalog, validation, observation, shutdown, and Console-version contracts.
- Verify the checked-in `loomspan-docs` routing and exact claims against focused executable anchors.
- Run and record local release dry runs and snapshot installation while keeping SC5 and final release-commit verification pending until real external evidence exists.

## Impacted Areas

- `loomspan-spring-boot-starter/src/test/resources/skills/integration/supported-surface-skill.yml` — explicit planner selection and bounded step budget.
- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/integration/SupportedSurfaceIntegrationTest.java` — planner responses, public observation assertions, and retained cumulative API/SPI checks.
- `README.md` — exact consumer contract and corrected release commands.
- `agent-skills/loomspan-docs/references/skill-authoring/*` and `references/java-api/*` — cumulative route/claim verification and only evidence-backed corrections.
- `docs/releases/1.0.0-beta.4.md` — compatibility/release note.
- `ai/thoughts/release-readiness/1.0.0-beta.4.md` — command and external-gate evidence.
- `scripts/loomspan_version.py`, `scripts/tests/test_loomspan_version.py`, `.github/workflows/publish.yml`, and `.github/workflows/console-release.yml` — existing mechanisms exercised, not redesigned.

## Risk Assessment

- The scripted OpenAI exchange may accidentally continue proving direct tool calling rather than plan creation and assigned task execution.
- Planner responses can become order-dependent if the two test tasks are grouped for concurrency; the fixture must keep them serialized.
- A cumulative test could import internal plan/session types and thereby undermine the supported-surface proof even while passing.
- README wording could overstate input sanitization, Resource immutability, pre-check reservation, future-child authorization, history availability, shutdown bounds, or Sidecar behavior.
- Release examples could still pair a SNAPSHOT POM with a SNAPSHOT tag, or a validation-only workflow could be described as publication.
- The readiness record could falsely turn local preparation into final readiness or accept historical grounding evidence instead of current SC5 integration.
- **Protected compatibility paths**: all thirteen allowlisted application API types/signatures; the single `RestSkillHandler` SPI; documented REST/configuration behavior; observer failure precedence; exact project-version diagnostic compatibility; no-session validation; current-run diagnostic security/usefulness.
- **Intentionally removed obsolete paths**: no old success-only observer mode, no pre-shutdown root-admission behavior, no default-method shim for the new `SkillTemplate` methods, no REST forbidden-field tolerance, no SNAPSHOT release tags, and no schema-counter/legacy-reader/cross-version fallback.
- **Authoring claims requiring executable evidence**: exact REST field presence, generic schema default, exact-name registration, role and reference processing, success credit, direct no-model execution, handler cardinality and invocation shape, recursive container immutability/Resource limit, SecurityContext scope, result/failure behavior, catalog snapshot fields, validate null rules/side-effect limits, observer history/precedence, and one-budget shutdown semantics.

## Existing Test Coverage

- `YamlSkillCatalogTests#rejectsInvalidRestValuesAndForbiddenFieldsByPresence` covers every invalid `rest` form and null/empty presence for all forbidden fields.
- `YamlSkillCapabilityRegistrarTests` covers shared registration, duplicate names, conditional handler cardinality, no-model metadata, one-call invocation, empty success, null failure, and propagated failures.
- `ApplicationApiValueTest` covers public record shape, recursive REST invocation immutability, permitted nulls, and Resource identity.
- `DefaultSkillCatalogTest#buildsEagerUnfilteredImmutablePublicSnapshotWithExactSchemas` covers eager ordering, unfiltered scope, exact schemas, missing lookup, and immutability.
- `DefaultSkillTemplateTest` covers both validation overloads, null/error ordering, no execution/binding, calling authentication, failure history, original-failure precedence, success-observer propagation, and no callback on invalid input.
- `FrameworkExecutionLifecycleTest` and `FrameworkShutdownIntegrationTest` cover admission, root lifetime, one deadline, listener ordering, resource lifetime, cutoff, and validation without reservation.
- `SupportedSurfaceIntegrationTest` already combines public catalog/validate/invoke/observer, Java and REST leaves, auth, and immutable handoff, but its fixture uses `planning_mode: false`; it does not prove the required planner path.
- `LoomspanPublicSurfaceArchitectureTest` covers the closed allowlist, sole SPI, package classifications, and absence of internal/autoconfigure signature leaks.
- `scripts/tests/test_loomspan_version.py` covers version consistency and SNAPSHOT tag rejection.
- Gap: no current test proves the cumulative `SkillTemplate -> YAML planner -> Java + REST` flow through the public observer view.
- Gap: no automated documentation check currently prevents SNAPSHOT release/tag examples or verifies the exact REST forbidden-field list; use focused textual assertions in the implementation/review workflow rather than introduce a new production abstraction.
- Gap: framework tests cannot prove Sidecar HTTP/JWT/route/queue/container wiring. That remains an explicit external integration gate.

## Bug Reproduction / Failing Test First

- **Type**: integration
- **Location**: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/integration/SupportedSurfaceIntegrationTest.java` with `src/test/resources/skills/integration/supported-surface-skill.yml`
- **Arrange**: retain the existing application context, public `SkillTemplate`/`SkillCatalog`, Java leaf, REST handler, role-bearing caller, and local OpenAI-compatible server. Add a public-view assertion that the completed root contains a `PLAN_CREATED` event before child tool results.
- **Act**: invoke `supportedSurfaceSkill` through `SkillTemplate` exactly as the current test does.
- **Assert**: the public view includes planner creation/update activity and successful Java/REST leaf results; no internal plan/session object is accessed.
- **Expected failure (pre-fix)**: the current manifest explicitly uses `planning_mode: false`, so the direct executor never creates a plan and the `PLAN_CREATED` assertion fails. Simply flipping the manifest before updating the stub also fails because the first scripted response is a tool-call response rather than the required plan JSON, proving the fixture must model the full planner protocol.

## Tests to Add/Update

### 1. `invokesYamlPlannerAndBothLeafKindsThroughSupportedSurface`

- **Type**: integration
- **Location**: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/integration/SupportedSurfaceIntegrationTest.java`
- **What it proves**: one real Spring context and local provider stub execute a YAML planner with exact tasks for Java and REST leaves, then synthesize both results through `SkillTemplate`; the public observer shows plan and tool lifecycle events.
- **Fixtures/data**: update `supported-surface-skill.yml` to `planning_mode: true` with a bounded `max_steps`; enqueue valid plan JSON, assigned Java task/tool response, assigned REST task/tool response, and final synthesis; retain `supported-rest-leaf.yml`.
- **Mocks**: external model transport only via the existing `MockWebServer`; application REST behavior remains the real test bean. No framework internal beans are mocked or replaced.
- **Affected surface**: Application API, Supported SPI, Configuration or manifest behavior, Ephemeral diagnostics.
- **Compatibility expectation**: protected public path and current-run diagnostic coherence.

### 2. Retain catalog and both validation overloads in the cumulative scenario

- **Type**: integration
- **Location**: `SupportedSurfaceIntegrationTest.java`
- **What it proves**: the eager unfiltered public catalog contains YAML/Java/REST descriptors and exact missing lookup; Map and DTO/Object validation work without execution, while root authorization is rechecked.
- **Fixtures/data**: existing three skill definitions, `DirectRequest`, authorized and denied Spring Security tokens.
- **Mocks**: none beyond the provider stub; verify validation consumes no provider response and invokes no handler.
- **Affected surface**: Application API, Configuration or manifest behavior.
- **Compatibility expectation**: protected path.

### 3. Retain REST handoff, authentication, and result/failure behavior

- **Type**: integration
- **Location**: `SupportedSurfaceIntegrationTest.java`
- **What it proves**: direct REST invocation preserves a deep immutable container snapshot and caller authentication, returns empty/nonempty handler text unchanged, and ordinary handler failure crosses as safe `SkillException`.
- **Fixtures/data**: mutable nested list/map, authenticated caller, success and `fail` message cases. Add an empty-result case here only if doing so does not duplicate the focused registrar test; otherwise cite the owner test.
- **Mocks**: real application `RestSkillHandler` test bean.
- **Affected surface**: Application API, Supported SPI.
- **Compatibility expectation**: protected path.

### 4. Retain failure observation and exception precedence

- **Type**: integration
- **Location**: `SupportedSurfaceIntegrationTest.java`
- **What it proves**: a post-session REST failure yields one available nonempty `SkillExecutionView` while the original facade failure remains primary; pre-session validation rejection produces no observer callback, while execution-time authorization rejection follows the already-implemented lifecycle semantics.
- **Fixtures/data**: REST `fail` input, invalid-input case, authorized/denied callers, `AtomicReference<SkillExecutionView>` and callback counter.
- **Mocks**: real handler; no trace-store or observer-registry replacement.
- **Affected surface**: Application API, Ephemeral diagnostics.
- **Compatibility expectation**: protected observer/failure path and current-run diagnostic coherence.

### 5. Preserve the closed supported boundary

- **Type**: architecture/unit
- **Location**: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java`
- **What it proves**: documentation/test changes add no public types, no second SPI, no accidental bean replacement contract, and no `internal`/`autoconfigure` types in supported signatures.
- **Fixtures/data**: existing exact allowlists.
- **Mocks**: none.
- **Affected surface**: Application API, Supported SPI, Internal implementation.
- **Compatibility expectation**: protected public boundary; no preservation promise for internals.

### 6. Verify version coordination and safe release examples

- **Type**: unit plus documentation check
- **Location**: `scripts/tests/test_loomspan_version.py`, `README.md`, `docs/releases/1.0.0-beta.4.md`
- **What it proves**: POM/module/skill versions remain coordinated; tag creation rejects SNAPSHOT; documented release commands use `1.0.0-beta.4`/`v1.0.0-beta.4`; development and Sidecar integration continue using `1.0.0-beta.4-SNAPSHOT` without a snapshot repository.
- **Fixtures/data**: existing temporary repository fixtures in `test_loomspan_version.py`; focused `rg` checks over README release blocks.
- **Mocks**: existing script-test Git fixtures only.
- **Affected surface**: Configuration or manifest behavior, Persisted or serialized behavior.
- **Compatibility expectation**: protected coordinated release behavior and intentional removal of invalid SNAPSHOT tag guidance.

### 7. Verify authoring claims against owner tests

- **Type**: focused unit/integration suite plus documentation audit
- **Location**: existing `YamlSkillCatalogTests`, `YamlSkillCapabilityRegistrarTests`, `ApplicationApiValueTest`, `DefaultSkillCatalogTest`, `DefaultSkillTemplateTest`, `SuccessfulSkillCompletionBoundaryTest`, `FrameworkExecutionLifecycleTest`, and `FrameworkShutdownIntegrationTest`; routed `agent-skills/loomspan-docs` documents.
- **What it proves**: every exact REST, catalog, validation, observation, error, authorization, and shutdown claim in updated guidance has matching executable evidence, and the routing/coverage tables remain accurate.
- **Fixtures/data**: existing valid/invalid REST definitions, schemas, Spring contexts, auth tokens, fake clocks/executors, and execution journals.
- **Mocks**: retain each owner test's established narrow fakes; do not add a second cumulative harness.
- **Affected surface**: all documented Application API, Supported SPI, Configuration or manifest behavior, and Ephemeral diagnostics surfaces.
- **Compatibility expectation**: protected paths and current-run diagnostic coherence; no old/new dual behavior.

### 8. Record gated release readiness

- **Type**: release verification / external integration
- **Location**: `ai/thoughts/release-readiness/1.0.0-beta.4.md`
- **What it proves**: local commands and snapshot install ran on an identified commit; final readiness cannot be claimed without current SC5 evidence and final validation-only workflow results.
- **Fixtures/data**: Git commit/status, tool versions, command output summaries, Sidecar commit/run links, and workflow run links.
- **Mocks**: none. Framework-only substitutes are explicitly unacceptable for SC5.
- **Affected surface**: Configuration/release behavior, Persisted diagnostic compatibility, Application API integration.
- **Compatibility expectation**: protected external integration gate and exact-version policy.

## How to Run

- Environment: Java 21+, Maven 3.9+ through the repository wrapper, Python 3, no release credentials for local dry runs, and a clean committed worktree for any version/tag command that enforces it.
- Red/focused test: `./mvnw --batch-mode --no-transfer-progress -pl loomspan-spring-boot-starter -Dtest=SupportedSurfaceIntegrationTest test`
- Public architecture: `./mvnw --batch-mode --no-transfer-progress -pl loomspan-spring-boot-starter -Dtest=LoomspanPublicSurfaceArchitectureTest test`
- REST/catalog/facade owners: `./mvnw --batch-mode --no-transfer-progress -pl loomspan-spring-boot-starter -Dtest=YamlSkillCatalogTests,YamlSkillCapabilityRegistrarTests,ApplicationApiValueTest,DefaultSkillCatalogTest,DefaultSkillTemplateTest,SuccessfulSkillCompletionBoundaryTest test`
- Shutdown owners: `./mvnw --batch-mode --no-transfer-progress -pl loomspan-spring-boot-starter -Dtest=FrameworkExecutionLifecycleTest,FrameworkShutdownIntegrationTest test`
- Version consistency: `python scripts/loomspan_version.py check`
- Version script tests: `python -m unittest discover -s scripts/tests -p "test_*.py"`
- Full verification: `./mvnw --batch-mode --no-transfer-progress clean verify`
- Release packaging dry run without signing or publication: `./mvnw --batch-mode --no-transfer-progress -Prelease -pl loomspan-spring-boot-starter -am -DskipTests -Dgpg.skip=true verify`
- Install the development snapshot for Sidecar: `./mvnw --batch-mode --no-transfer-progress install`
- Documentation guards: `rg -n "model|prompt|thinking_level|allowed_skills|planning_mode|concurrency|max_steps|linter|output_schema|output_schema_max_retries" README.md agent-skills/loomspan-docs/references` and `rg -n "tag .*SNAPSHOT|v1\.0\.0-beta\.4-SNAPSHOT" README.md docs/releases`
- External gate: run Sidecar SC5's packaged HTTP/JWT/queue/container suite against the locally installed framework commit and record its exact command/run link; no framework-only command can replace this gate.
- Final validation: after SC5 and any framework fixes, repeat version/script/full/release-profile checks on the final commit and manually dispatch Console Release and Maven Central Release in validation mode. Confirm publish/upload jobs were skipped.

## Exit Criteria

- [x] The planner-path assertion fails against the old direct fixture and passes after the fixture/protocol update.
- [x] The cumulative test proves YAML planner, Java leaf, REST leaf, catalog, both validation input forms, public-only observation, failure history, exception precedence, immutable handler input, and authorization without importing internal application-facing types.
- [x] All focused owner tests and the full build pass.
- [x] `LoomspanPublicSurfaceArchitectureTest` still reports exactly thirteen API types and only `RestSkillHandler` as supported SPI.
- [x] Every changed skill-authoring/java-api claim is supported by cited production source plus a focused test/fixture/sample; routing and coverage remain LLM-first and version-aligned.
- [x] README and release notes identify additive APIs, intentional breaks, exact REST/startup/security/error rules, one-budget shutdown, and exact-version Console policy without promising new sanitization or compatibility.
- [x] README contains no SNAPSHOT release/tag command and accurately distinguishes manual validation from tag-triggered publication.
- [x] Protected compatibility paths pass; intentionally obsolete behavior is absent rather than retained behind overloads, aliases, fallbacks, adapters, deprecated paths, legacy readers, or dual modes.
- [x] The readiness record contains the exact snapshot commit and successful local checks/install.
- [ ] SC5 snapshot integration proves every ticketed external behavior against that commit; any framework fixes are reinstalled and all affected integration checks rerun.
- [ ] Final local and validation-only workflow checks pass on the final release commit, with publication jobs skipped.
- [x] No tag, push, Maven Central publication, Console release publication, or Sidecar release occurs under this ticket.
- [x] Optional human review of the compatibility note is recorded as nonblocking, not treated as a completion gate.
