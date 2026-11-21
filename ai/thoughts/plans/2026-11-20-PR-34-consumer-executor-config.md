# Namespace-owned consumer executor configuration Implementation Plan

## Overview

Extend the Framework-created v1 executor invocation with a separate immutable consumer configuration map selected from the executing YAML leaf's namespace. Reuse namespace configuration, complete publication and reachable-generation capture. Update the sibling Decisions pack so its shared TypeSafe executor uses an owner-controlled endpoint and a consumer-selected API key, with owner-key fallback only on omission.

This is Full 5-Step Pipeline Steps 2–3. The ticket and its developer-approved Pipeline notes govern scope: coordinated changes, existing versions and v1 package, direct consumer updates, no compatibility shims. Preserve unrelated prior-ticket artifact deletions and the sibling Decisions CHANGELOG deletion.

## Current State Analysis

The immutable namespace generation already contains executor data. `SkillDependencyValidator` resolves a registration and selects its owner map, and `SkillAdmissionSnapshot.ExecutorBinding` stores that map and the owner generation ID. `ExecutionCoordinator` constructs the actual invocation from this captured binding. The invocation currently has no consumer accessor.

Reachable capture already retains each leaf's generation and each referenced executor owner's generation. It therefore has all authoritative data required; no additional lease, registry, generation identifier or live lookup is needed. Startup and publication already share namespace-indexed YAML parsing and publication rejects foreign entries. Missing published data already clears the target's old data.

### Key Discoveries

- `src/main/java/ai/loomspan/internal/skill/SkillDependencyValidator.java:165`: resolve the consumer using `definition.identity().namespace()`, independent of the root namespace.
- `SkillDependencyValidator.java:176`: one exact, case-sensitive dot-path selection path owns structural validation; owner selection is mandatory when a selector exists.
- `src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java:325`: capture already retains coherent reachable leaf and executor-owner generations; admission rebuilds bindings outside the registry monitor at line 366.
- `src/main/java/ai/loomspan/internal/skill/SkillAdmissionSnapshot.java:10` and `src/main/java/ai/loomspan/internal/core/ExecutionCoordinator.java:190`: carry the second map through the existing binding and handoff.
- `src/main/java/ai/loomspan/api/extensions/v1/SkillExecutorInvocation.java:17` and `src/main/java/ai/loomspan/internal/core/ExecutorInvocations.java:20`: final supported class, private construction and one internal method-handle bridge.
- Sibling `extensions/decisions/src/main/java/ai/loomspan/decisions/internal/typesafe/TypeSafeTransport.java:69`: settings are captured before the retry loop; keep that property.
- Sibling `TypeSafeRequest.java:15`: leaf `max_attempts` already accepts integer kinds 1–3 with default 3. Packaged TypeSafe authoring guidance currently contradicts this.

## Desired End State

`consumerExecutorConfig()` returns `Map<String,Object>` with the same recursive map/list copying and value fidelity as existing invocation maps. `executorConfig()` remains owner data, `skillConfig()` remains leaf data and ordinary `input()` stays independent. The owner generation ID retains its current meaning.

For a foreign executor reference, select the registration's exact config path from the leaf namespace generation. Missing keys at any map level yield an empty consumer map. A present null, scalar or list at the selected path or an intermediate segment is malformed and fails metadata validation/admission; it is not absence. A selected empty map is valid. Same-namespace execution and selector-free registrations always yield an empty consumer map; owner selection still runs normally. Invalid selectors still fail. Selected contents, including nulls and dotted keys, are ordinary preserved data.

Preparation validates against its existing candidate overlay and foreign metadata view; it stores no foreign runtime binding. Fresh admission selects both maps from captured generations. Retained handoffs use both original maps across publication, removal and recreation. Consumer-only publication changes no owner generation ID and no other namespace's configuration.

TypeSafe accepts only `api_key` in consumer settings. Any consumer `base_url`, including null or an owner-equal value, fails identifying `consumerExecutorConfig.base_url` before HTTP. Other unknown consumer keys fail with their path, matching the pack's strict owner/leaf policy. Select the consumer key by key presence; only omission falls back to owner `api_key`. Validate the selected key as a nonblank string containing printable ASCII header characters and preserve its exact text. A malformed present consumer key never falls back. An unselected owner's key need not be valid or present; owner allowed-field checks remain. Endpoint selection and validation use owner `base_url` exclusively, retaining the existing absent-only default and prefix behavior. Retain leaf retry rules, request bytes and lifecycle checks.

## What We're NOT Doing

- No merge engine, new configuration root, Framework domain-key policy, credential resolver or executor preparation callback.
- No consumer generation accessor, trace payload for configuration, protocol fields, Console changes or compatibility-marker bump.
- No new supported Java SPI, bean replacement contract, public builder or public invocation constructor.
- No artifact/package version changes, compatibility overloads or migration machinery.
- No unrelated cleanup or restoration of existing deletions.

## Skill-Authoring Documentation Impact

**Impact: Affected.** Authors must know leaf-owned consumer identity, optional selection, retained versus fresh publication behavior and TypeSafe's provider-specific policy.

- **Documents to update:** Framework `README.md`; `@SkillExecutor` and invocation JavaDoc; `agent-skills/loomspan-docs/references/skill-authoring/executor-skills.md`, `typesafe-skills.md`, and `README.md`; `references/java-api/java-skills.md`, `skill-reload.md`, and its README coverage row. In the sibling pack update `README.md`, `docs/executor.md`, `docs/transport.md`, and the support-triage example README/recipe documentation describing its exercised behavior. Update `docs/tested-builds.md` with actual Step 4 build identities and commands after verification.
- **Supporting evidence:** Framework dependency-resolution, invocation-value and real execution tests below; Decisions settings/transport tests and installed-JAR publication consumer test. Cite stable class/method anchors in product docs, not ticket IDs or volatile line numbers.
- **Coverage table update: Required.** Extend executor configuration and Java declaration rows for separate owner/consumer maps and leaf-generation capture; keep the TypeSafe row explicit about pack-owned local HTTP evidence and hosted-provider limitations.
- **LLM-first usability:** Keep generic lookup/capture rules in executor guidance, Java accessor semantics in Java guidance and provider policy in the TypeSafe topic/pack. Include a self-contained owner-URL/billing-key YAML example and complete target publication example; link existing routing rather than create another topic. Distinguish mandatory owner maps from optional consumer entries and immutable containers from arbitrary leaf-object freezing.
- **Drift classification:** Existing generic owner selection, visibility, complete publication and capture guidance is **aligned** with current code. Owner-only enumeration describes the current implementation and must be expanded with this change. The TypeSafe topic's claim that every leaf `config` key is rejected is **documentation drift**: replace it with the already implemented `max_attempts` 1–3/default 3 rule. No unresolved defect or semantics decision remains.

## Contract and Compatibility Impact

| Surface | Impact and supporting evidence | Planned compatibility treatment |
| --- | --- | --- |
| Application API | Allowlisted `SkillExecutorInvocation` gains one `Map<String,Object>` accessor. `SkillExecutor` selector documentation expands; `SkillReloader` signatures remain. | Preserve existing accessor meanings and lifecycle; update exact method-set expectations and architecture checks. |
| Supported SPI | None under AGENTS and the closed supported-surface test. Annotation declarations do not create an SPI. | Add none. |
| Configuration and manifest contracts | Existing `loomspan.executor-config.<namespace>` and selector now also supply optional consumer maps; no manifest syntax changes. TypeSafe consumer policy is pack-owned. | Preserve owner mandatory lookup, exact path semantics, namespace authority and complete replacement. Add explicit optional consumer rules. |
| Persisted or serialized contracts | No serialized invocation format or new durable state. TypeSafe native request/response body shapes stay unchanged. | Retain existing YAML ingestion and native protocol behavior; no migration. |
| Ephemeral diagnostic formats | Existing executor identity, owner ID, content and diagnostic lifecycle remain sufficient. | Preserve current-run coherence and content fidelity; no new fields, no protocol-marker change. Existing journal projection exception remains untouched. |
| Internal or accidentally exposed implementation | Binding record, resolver helper, private invocation constructor, internal bridge and test factories change. | Update every caller atomically; do not retain obsolete signatures. No autoconfiguration extension points. |

- **Evidence of supported contracts:** ticket requirements, Framework AGENTS, `LoomspanPublicSurfaceArchitectureTest`, README and verified Decisions consumption.
- **Intentional compatibility changes:** internal construction signatures and TypeSafe settings collaborators change directly under Pipeline notes. Supported invocation only adds an accessor; existing owner-only behavior is retained.
- **In-repository consumers:** all `ExecutorInvocations.create` call sites and binding constructors; API method-set assertions; Framework executor fixtures/tests; Decisions invocation mocks and settings helpers; installed-JAR example harness and docs.
- **Public-surface delta:** one method on the already allowlisted final v1 type; no type additions, public construction, leaked internal signature types or Spring replacement points. Adding an allowlist type is unnecessary; keep the closed allowlist intact.
- **Shim decision: No shim.** No protected constructor or internal call signature exists; ticket explicitly requires direct coordinated updates and existing versions.
- **Java-to-Go boundary coordination: Not required.** REST/SSE, acquisition, problems and consumed NDJSON are unchanged. Run Framework diagnostics regressions; no Go fixture churn or compatibility-marker bump is justified.
- **Pipeline notes alignment: Aligned.** Both checkout changes ship together at their current versions. Any discovery requiring a changed external protocol or broader break must return to the orchestrator.

## Implementation Approach

Use the existing binding as the sole resolved authority. Extend its state with the consumer map, freeze it with `ImmutableContainers.copyMap`, and pass it into the supported invocation. Refactor selector mechanics only enough to distinguish mandatory owner lookup from optional consumer absence, using one path grammar/traversal implementation. Preserve clear namespace/selector/leaf context in structural failures without adding domain validation or invoking applications.

Alternatives: leaving the Framework unchanged forces per-leaf duplication or business-input credentials and fails the required centralized namespace handoff. Merging maps obscures ownership and puts provider precedence in Framework. A second registry or invocation-time lookup duplicates generation authority and breaks retained capture. Extending the existing binding is the smallest complete design and introduces only the requested map. No unrelated dead-code cleanup is necessary.

## Phase 1: Bind and expose captured consumer configuration

### Changes Required

1. In `SkillDependencyValidator.resolveExecutor`, resolve owner normally, then use the leaf namespace view for foreign consumer selection. Keep fixed owner registration validation strict. Share selector grammar/traversal, handling missing map keys as optional only for consumers; reject present malformed nodes.
2. Extend `SkillAdmissionSnapshot.ExecutorBinding` with the independently copied consumer map. Use existing captured generations; leave capture traversal, leases and owner `generationId` semantics unchanged.
3. Add the private field, constructor argument, copy, null guard and public accessor to `SkillExecutorInvocation`. Update `ExecutorInvocations` method type and invocation arguments, `ExecutionCoordinator`, every bridge caller and binding test factory directly. Keep input, skill and owner maps independent and lifecycle accessors live.
4. Update `ApplicationApiValueTest` exact method set and `SkillExecutorTest`/value tests for all four maps (input plus three configuration maps), nested keys/nulls, Resource and other non-container identities, source mutation and unmodifiable nested containers. Keep no-public-construction assertions.
5. Add resolver and real execution/publication coverage specified in the testing plan. Include support parent -> billing leaf, two billing leaves, another consumer and consumer-only rotation.

### Success Criteria

- [x] Focused Framework command from the testing plan passes, including supported-surface and architecture tests.
- [x] Tests establish exact optional/malformed selection, leaf identity, retained maps and owner-ID stability without live lookup.
- [x] No new supported types or construction/bean replacement paths appear.

## Phase 2: Apply TypeSafe policy and qualify the installed pack

### Changes Required

1. In sibling `TypeSafeSettings`, capture owner and consumer maps once, enforce provider allowed fields, select/validate the key and owner endpoint. Update `TypeSafeTransport.send` to supply both maps before its existing retry loop. Retain existing request serialization, leaf attempts, cancellation, deadlines and shared transport lifecycle.
2. Keep provider policy in settings/pack validation. Existing `TypeSafeRequest.prepare` owner allowed-field validation may be retained or moved into settings as a single coherent responsibility; do not add an obsolete overload or duplicate credential selection. Update every settings call and mock directly.
3. Extend `TypeSafeSettingsTest`, `TypeSafeTransportTest` and helpers for consumer selection/rejection, fallback, endpoint stability, exact key text and retry capture. Preserve owner-only tests.
4. Extend example `ConsumerHarness` with explicit target namespace and visibility publication support; keep it test-only and use public Framework APIs. Publish owner Public before consumers, use qualified `default-typesafe` from foreign leaves, and keep complete per-target settings. Add installed-pack scenarios for local HTTP, two billing leaves, support parent, another consumer and retained handoff across billing-only publication. No mocked executor/transport for these scenarios.

### Success Criteria

- [x] Framework installs at the unchanged version; sibling root descriptor validation and Decisions install pass.
- [x] Support-triage `verify` proves real endpoint, Authorization header, leaf retry bound, rejection before HTTP, owner-only operation and consumer publication/retention.
- [x] No live hosted TypeSafe credentials or provider access is required.

## Phase 3: Synchronize consumer and authoring guidance

### Changes Required

Update the exact documents identified above in the same change. Describe owner/consumer/leaf ownership, dot paths, empty and malformed cases, namespace authority, Public sharing, complete publication, captured retention, owner ID and TypeSafe policy. Replace stale map counts and leaf-retry prose. Product documentation describes current supported behavior without PR sequencing, migration history or verification receipts; `docs/tested-builds.md` retains its purpose as build evidence. Keep pack-owned claims grounded in pack tests and the installed-JAR example.

### Success Criteria

- [x] Relevant coverage rows cite tests that were actually implemented and run.
- [x] Documentation examples use valid complete publication and preserve the distinction between optional consumer entry and mandatory owner map (which may be empty).
- [x] Full Framework install and full example verification pass after final production/test edits; review diff for exact-version and protocol stability.

## Testing Strategy

Use focused resolver/value tests for structure and immutability, Framework integration for direct/parent authority and public retained handoffs, pack settings/HTTP tests for provider policy and retry stability, then installed-JAR example verification against the locally installed Framework. Detailed cases, commands and acceptance mapping are in `2026-11-20-PR-34-consumer-executor-config-testing.md`.

## Performance Considerations

No extra namespace capture or runtime lookup. The selected consumer subtree receives the same bounded-by-existing-input container copying as current maps at binding and invocation construction. No HTTP or application callbacks run during metadata validation. Do not add speculative caches or benchmarks; existing deterministic concurrency tests exercise coherent capture.

## Migration Notes

No deployments or version migration. Install the changed Framework locally, rebuild/install Decisions and verify its independent example at existing versions. Update internal call sites atomically without shims. Namespace publication remains complete replacement; omission is an intentional clearing operation.

## References

- Ticket: `ai/thoughts/tickets/loomspan-pr-34-consumer-executor-config.md`.
- Research: `ai/thoughts/research/2026-11-20-PR-34-consumer-executor-config.md`.
- Policies: `AGENTS.md`, sibling `AGENTS.md`, `ai/thoughts/framework-feature-design-lens.md`, `ai/commands/shared/loomspan-docs-protocol.md`.
- Existing patterns: `ExecutorDependencyResolutionTest`, `ExecutorGenerationExecutionIntegrationTest`, sibling `TypeSafeSettingsTest`, `TypeSafeTransportTest` and `PublicationConsumerTest`.

## Step 4 execution evidence

- Used the checked-out, version-matched `agent-skills/loomspan-docs/SKILL.md` as router after source tracing. Generic owner/capture guidance was aligned; the TypeSafe blanket leaf-config rejection was documentation drift and now documents existing `max_attempts` 1–3/default 3.
- Initial `./mvnw.cmd -B '-Dtest=ExecutorDependencyResolutionTest' test` failed test compilation on the new `consumerConfiguration()` assertion before adding the carrier. This is the valid pre-feature red evidence.
- The intermediate selection-only red attempt `./mvnw.cmd -B '-Dtest=ExecutorDependencyResolutionTest#foreignLeafSelectsIndependentOwnerAndConsumerSubtrees' test` reached Mockito setup but the sandbox prevented JVM attach (`could not open (5) pipe`). It was interrupted and is not value-red evidence. The original owner-only `resolveExecutor` implementation supplied no consumer configuration; no source rollback was used to manufacture red evidence.
- Tests require escalated execution in this Windows environment for Mockito JVM attach. The first escalated API/architecture/resolver run passed 34 tests. A new parent fixture initially lacked the complete receiving input schema needed for automatic bound dispatch; the fixture was corrected, and isolated API/execution tests passed 11 tests.
- The exact planned combined focused command, in a reused JVM, failed after `ExecutorConfigurationStartupTests`: later Spring contexts could not read `LoomspanAutoConfiguration.class` and release metadata, although the class files existed. These untouched startup tests run successfully in isolation. Repeating the same selector with `'-DreuseForks=false'` passed all 96 tests with no failures/errors/skips. JVM isolation is a verification-run adaptation; no unrelated production/test lifecycle workaround was added.
- Provider allowed-field checks were consolidated in `TypeSafeSettings`; `TypeSafeRequest.prepare` now receives input alone, with all internal callers/tests updated. This keeps settings policy in one collaborator and captures owner/consumer settings once before transport retries.

Required focused verification passed:

```powershell
./mvnw.cmd -B '-Dtest=SkillExecutorTest,ApplicationApiValueTest,LoomspanPublicSurfaceArchitectureTest,SupportedSurfaceIntegrationTest,ExecutorConfigurationTests,ExecutorConfigurationStartupTests,ExecutionConfigurationParserTest,ExecutorConfigurationManagerTests,ExecutorDependencyResolutionTest,ExecutorGenerationExecutionIntegrationTest,NamespaceExecutionAuthorityIntegrationTest,SharedDeclarativeAuthorizationIntegrationTest,ExecutorInputTransformIntegrationTest,ExecutorInvocationLifecycleIntegrationTest,ExecutorDiagnosticsIntegrationTest' '-DreuseForks=false' test
```

Extensions root `mvn -B validate` passed. Full Framework install and installed-pack verification are still in progress; later entries record actual results.

The full isolated build uses `./mvnw.cmd -B '-DreuseForks=false' '-DforkCount=2' install` (a preliminary serial isolated install was interrupted to use two forks). It encountered a timing failure in untouched `ConcurrentGroupedExecutionIntegrationTest#parentTimeoutRevokesLateWritesFromAnActualNestedDirectMission`: line 402's child-start latch did not fire before the fixture's 100 ms parent budget. This model-only direct-mission path does not invoke the modified executor branch. The remainder of the full suite continues; follow-up verification will record whether repetition resolves this cold-JVM/load-sensitive failure without changing unrelated lifecycle code.

Full Framework verification and installation passed on 2026-11-20:

```powershell
./mvnw.cmd -B '-DreuseForks=false' '-DforkCount=4' '-Dsurefire.rerunFailingTestsCount=1' install
```

Result: 2,379 tests, zero final failures/errors, two existing skips, one explicitly reported flake in the untouched 100 ms parent-timeout fixture; its immediate warm retry passed in 0.424 seconds. The earlier two-fork full build ran the same 2,379 tests and had only this one failure. No unrelated lifecycle test or implementation was modified. Full install completed at 2026-11-20T21:06:11-08:00 and ran `LoomspanPublicSurfaceArchitectureTest` after production changes.

Framework source base: `cd1c15237cb31770853dfb78a7399f1a4594dbd0` plus this working tree. Target and installed `loomspan-spring-boot-starter-1.0.0-beta.10-SNAPSHOT.jar` both have SHA-256 `5CA1209FF7E7A3582EB1585FFAF13EDEFBC6B381EA5E1B19276756A0AF1DCF1A`. Toolchain: JetBrains Java 21.0.2, Framework Maven wrapper 3.9.11, sibling Maven 3.9.6. Versions and v1 package are unchanged.

Cross-repository verification completed successfully in dependency order:

```powershell
# Extensions root, after current Framework install
mvn -B validate
mvn -B -f extensions/decisions/pom.xml '-Dtest=TypeSafeSettingsTest,TypeSafeRequestTest,TypeSafeTransportTest' test
mvn -B -f extensions/decisions/pom.xml install
mvn -B -f extensions/decisions/examples/support-triage/pom.xml verify
```

Results: descriptor validation PASS; focused Decisions suite 69 tests PASS; Decisions full install 86 tests PASS (no failures/errors/skips); independent installed-JAR consumer verify 21 tests with zero failures/errors and one optional Console skip. Both `PublicationConsumerTest` methods passed, including actual foreign leaf/parent HTTP selection, retries, consumer-only publication, omission, fallback and retained old owner endpoint/key. The installed Decisions JAR and target JAR have identical SHA-256 `655D482A19483860EF1F3FB555DBACFD6D9FFECA196235E093EEC3C5D4245580`; source base is `a6de3966e771a27683d9f6d8f4f91ba0006c3844` plus this working tree. Example verify finished at 2026-11-20T21:08:50-08:00. No source-tree substitution or live hosted provider was used.

Final scope/diff review: the closed API gains only the requested accessor; no new supported type/construction/SPI/bean path, version change, protocol payload or compatibility marker change. Existing journal behavior is untouched. Every acceptance criterion is mapped to resolver/API tests, actual Framework parent/direct/handoff execution, existing authority/advice/lifecycle regressions, provider settings/HTTP tests and installed-JAR publication evidence. Routed authoring guidance and coverage tables match these anchors; the TypeSafe retry drift is corrected. Both repository `git diff --check` commands pass. Balanced code fences were checked in changed Markdown. The pre-existing Decisions CHANGELOG deletion is preserved; otherwise untouched source files retain their original bytes/line endings.

Optional Console profile, demo `exec:java` and hosted-provider quality/access checks were NOT RUN: no consumed protocol changed and they are outside the local acceptance criteria. Optional developer checks: none. Step 5 fresh independent review remains required by the Full 5-Step Pipeline.
