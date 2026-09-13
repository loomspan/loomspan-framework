# PR 5.4 Framework Documentation and Gated Release Readiness Implementation Plan

## Overview

Finish the beta 4 framework release-readiness work after PRs 5.1-5.3: make the root README an exact consumer contract, preserve and verify the version-aligned Loomspan documentation skill, convert the existing cumulative supported-surface fixture from direct LLM execution to the required YAML planner path, publish beta 4 compatibility notes, and create an auditable readiness record. The work prepares and verifies the framework but does not tag, publish, or claim final readiness before the Sidecar SC5 snapshot-integration gate.

## Current State Analysis

The checkout at `de2fc7d1a5154407912f5dd2f35504926e33d9d2` already contains the runtime delivered by PRs 5.1-5.3. The closed application API has thirteen allowlisted types, `RestSkillHandler` is the sole supported SPI, REST declarations and direct execution are implemented, public catalog and validation APIs are present, failure observation preserves the original execution failure, and shutdown uses one framework-owned budget. PR 5.4 should not reimplement those behaviors.

Most checked-in `agent-skills/loomspan-docs` guidance is already source-aligned and versioned `1.0.0-beta.4-SNAPSHOT`. The remaining gaps are cumulative presentation and readiness proof: the root README omits several exact REST/SPI and shutdown distinctions, its release examples incorrectly use a SNAPSHOT as a release/tag, the supported-surface fixture explicitly selects `planning_mode: false`, and there is no beta 4 release-note or gated-readiness record.

## Desired End State

- The root README precisely describes the REST manifest matrix, handler boundary, catalog/pre-check behavior, observer and shutdown contracts, supported surface, and safe beta 4 release commands without exposing internal or autoconfiguration types as supported APIs.
- The checked-in `loomspan-docs` package remains a version-aligned, LLM-first router for REST authoring and application API use; any edits are grounded by the cited source and focused tests rather than duplicating README prose.
- `SupportedSurfaceIntegrationTest` uses its existing local OpenAI-compatible server and one application context to exercise `SkillTemplate -> YAML planner -> Java and REST leaves`, while catalog, both validation input forms, observations, authorization, handler immutability, and failure precedence remain visible only through `ai.loomspan.api` values.
- A public beta 4 release note distinguishes additive APIs, intentional source/binary/behavior changes, REST startup rules, shutdown changes, and exact-version Console diagnostics.
- A repository readiness record identifies the exact installed framework snapshot commit, records local dry-run results, keeps the SC5 and final-release checks explicitly pending until real external evidence exists, and contains no publication action.

### Key Discoveries

- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java:29-42,285-365` is the executable authority for the thirteen supported API types, the one supported SPI, and the prohibition on leaking `internal` or `autoconfigure` signature types.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java:51-56,255-324` implements the exact REST field-presence rules, including rejection of null and empty forbidden fields.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skill/YamlSkillCapabilityRegistrar.java:39-92` implements conditional exactly-one handler binding, exact-name registration, and empty/null result behavior.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skillapi/DefaultSkillCatalog.java:20-46` and `DefaultSkillTemplate.java:77-195` implement the public snapshot and advisory validation/observation paths.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/LoomspanSessionRunner.java:215-278` and `FrameworkExecutionLifecycle.java:53-187` establish observation timing, failure precedence, root ownership, shutdown admission, one deadline, and cutoff.
- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/integration/SupportedSurfaceIntegrationTest.java:42-222` already owns the combined fixture, but `loomspan-spring-boot-starter/src/test/resources/skills/integration/supported-surface-skill.yml:4` explicitly disables planning.
- `scripts/loomspan_version.py:103-121,187-243` is the existing coordinated version authority and rejects SNAPSHOT release tags; `.github/workflows/publish.yml:25-66` and `.github/workflows/console-release.yml:16-163` make manual runs validation-only and reserve publication for matching pushed non-SNAPSHOT tags.
- The installed `C:/Users/mgiacomi/.codex/skills/loomspan-docs` package is versioned `0.1.0-SNAPSHOT` and cannot support exact beta 4 claims. The checkout's `agent-skills/loomspan-docs/SKILL.md:6-8` is aligned to `1.0.0-beta.4-SNAPSHOT` and is the implementation documentation authority for this work.

## What We're NOT Doing

- No changes to REST, catalog, validation, observer, shutdown, or Console runtime behavior unless the later Sidecar gate exposes a genuine framework defect.
- No new public API, SPI, Spring replacement contract, release/version abstraction, compatibility schema counter, range negotiation, legacy reader, adapter, or internal compatibility shim.
- No reload, retry, extra-handler, token-exchange, input/result/error/history sanitization guarantee, or cross-version diagnostic support.
- No duplicate integration harness and no movement of PR 5.1-5.3 feature-unit tests into PR 5.4.
- No Sidecar implementation, documentation, workflow seeding, remote access, credentials, registry provisioning, tag, Maven Central publication, GitHub release, or GitHub PR creation.

## Skill-Authoring Documentation Impact

**Impact**: Affected

- **Rationale**: The ticket makes REST declaration and invocation semantics, authorization, validation, observation, error, and Java comparison guidance part of the beta 4 author-facing contract. The checked-in package is already aligned, so implementation should perform a focused cumulative review and edit only genuine routing, boundary, or completeness gaps; it must not create redundant prose solely to force a diff.
- **Documents to update**: `agent-skills/loomspan-docs/references/skill-authoring/README.md` and `rest-skills.md`, plus `agent-skills/loomspan-docs/references/java-api/README.md`, `compatibility-and-boundaries.md`, `rest-skills.md`, `catalog-and-validation.md`, `invocation.md`, and `observation-and-errors.md` when the cumulative review finds a concrete omission. `agent-skills/loomspan-docs/SKILL.md` changes only through the existing version tool when the project version changes.
- **Supporting evidence**: `YamlSkillCatalogTests`, `YamlSkillCapabilityRegistrarTests`, `ApplicationApiValueTest`, `DefaultSkillCatalogTest`, `DefaultSkillTemplateTest`, `SuccessfulSkillCompletionBoundaryTest`, `FrameworkExecutionLifecycleTest`, `FrameworkShutdownIntegrationTest`, and the updated `SupportedSurfaceIntegrationTest` fixture.
- **Coverage table update**: Required only if routing scope or confidence changes. Existing REST and Java API entries are already source-verified, so a content-preserving review does not justify changing coverage labels.
- **LLM-first usability**: Preserve progressive disclosure: the two knowledge-set READMEs route by developer task, REST authoring owns manifest rules, Java REST owns the handler SPI, catalog/validation owns advisory pre-checks, invocation owns lifecycle timing, observation/errors owns diagnostic trust and exception precedence, and compatibility owns the closed public boundary. Exact field tables and limitations stay self-contained and cross-linked without duplicating the root README.
- **Drift classification**: Checked-in skill-authoring and Java API topics are **aligned** with live source/tests; root README REST, shutdown, and release instructions show **documentation drift**. The globally installed skill is version-mismatched and excluded from version-sensitive conclusions. No possible framework defect was found.

## Contract and Compatibility Impact

| Surface | Impact and supporting evidence | Planned compatibility treatment |
| --- | --- | --- |
| Application API | Documentation and cumulative tests cover all thirteen allowlisted `ai.loomspan.api` types. Catalog types and REST types are additive; `SkillTemplate`'s two abstract `validate` methods are source/binary-sensitive. | Preserve the implemented signatures and document the deliberate beta 4 impacts. Run the architecture allowlist test; do not add types. |
| Supported SPI | `RestSkillHandler#handle(RestSkillInvocation)` is the sole supported SPI; handler cardinality is conditional on REST manifests. | Preserve exactly one supported SPI and explicitly reject any implication that catalog injection or public Spring beans are replacement points. |
| Configuration and manifest contracts | Documentation changes cover exact REST field presence/default/roles and positive `loomspan.shutdown.timeout: 30s`. The test fixture changes the root from direct execution to explicit planning. | Preserve current runtime contracts. Update documentation and fixture atomically; do not add compatibility syntax or dual behavior. |
| Persisted or serialized contracts | REST already expands application-adapter and Console semantics; canonical diagnostics use the framework-owned exact project-version marker. Release/readiness documents are durable project records, not runtime formats. | Retain exact matching beta 4 framework/Console versions, missing/unequal rejection, dual-development best effort, and no legacy reader/schema counter. |
| Ephemeral diagnostic formats | The public observer view and current-run trace must remain useful through planner, Java, REST, failure, and shutdown paths, without a comprehensive sanitization promise. | Assert current writer/mapper coherence and planning evidence through the public view; do not promise historical readability. |
| Internal or accidentally exposed implementation | The fixture necessarily relies on framework wiring but application-facing assertions and imports must remain limited to `ai.loomspan.api`; `autoconfigure` types only bootstrap the test context. | No compatibility preservation for internal decomposition and no new replacement seam. Update only the existing fixture and its scripted provider exchanges. |

- **Evidence of supported contracts**: the architecture allowlist, root `AGENTS.md`, ticket requirements, checked-in version-aligned knowledge sets, public interfaces/records, and `SupportedSurfaceIntegrationTest` consumer usage.
- **Intentional compatibility changes**: beta 4 adds REST and catalog public types, adds two abstract `SkillTemplate.validate` methods without a default-method shim, expands observers to mappable post-session failures without a success-only mode, rejects new roots during shutdown, and semantically expands Console diagnostics under an exact project-version marker.
- **In-repository consumers to update**: `README.md`; the routed `agent-skills/loomspan-docs` references if gaps remain; `SupportedSurfaceIntegrationTest`; `supported-surface-skill.yml`; the new public release note; and the new readiness record. Existing generated Console fixtures change only if verification proves a producer change, which is not expected.
- **Public-surface delta**: None in PR 5.4. The release note describes the already-landed beta 4 delta; production signatures, constructors, and Spring extension points remain unchanged.
- **Shim decision**: **No shim.** The ticket explicitly requires one coherent pre-1.0 beta 4 contract and forbids internal compatibility shims. Documentation and cumulative verification do not justify new compatibility machinery.
- **Java-to-Go boundary coordination**: **Not required for new implementation.** PR 5.4 records the already-coordinated REST semantic expansion and exact project-version policy. It does not change REST/SSE, acquisition, problem, or NDJSON shapes; any integration-discovered boundary change must update Java, Go, TypeScript, fixtures, tests, documentation, and the compatibility decision together before proceeding.
- **Pipeline notes alignment**: **No notes.** The ticket itself explicitly authorizes the listed pre-1.0 compatibility changes and exact-version policy. No broader break is planned.

## Implementation Approach

Treat executable behavior as fixed and make documentation, cumulative proof, and readiness evidence converge on it. First turn the existing supported-surface scenario into a planner-backed contract test using the same server and fixture. Then close human and agent guidance gaps and add one public release note. Finally run local preparation checks, install the exact snapshot for Sidecar, and write the readiness record with a hard pending gate. After SC5 evidence arrives, repeat affected checks on the final commit and trigger only validation-mode workflows; tagging and publication remain a separately authorized action.

## Phase 1: Convert the Cumulative Fixture to the YAML Planner Path

### Overview

Reuse the existing integration test and local OpenAI-compatible stub while making planner selection and both child executions executable facts.

### Changes Required

#### 1. Planner manifest

**File**: `loomspan-spring-boot-starter/src/test/resources/skills/integration/supported-surface-skill.yml`

**Changes**:

- Replace `planning_mode: false` with explicit `planning_mode: true`.
- Add the smallest deterministic `max_steps` needed for two child tasks plus final synthesis.
- Keep the same named model connection and exact `allowed_skills` entries; do not add another root or test-only public concept.

#### 2. Scripted planner/provider exchange and assertions

**File**: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/integration/SupportedSurfaceIntegrationTest.java`

**Changes**:

- Replace the direct tool-call/final response pair with the framework's existing OpenAI planner protocol: a valid two-task plan targeting `supportedJavaLeaf` and `supportedRestLeaf`, one assigned step response per task using the schema-permitted message, and final synthesis that consumes both results.
- Keep both tasks deterministic/serialized in the fixture so response ordering is stable; this test proves planner composition, not concurrency.
- Assert the public observation contains `PLAN_CREATED`, child tool activity/results, and successful completion, proving the planner path without inspecting internal plan/session types.
- Keep catalog assertions for all three kinds, exact missing lookup, both Map and Object validation calls, REST immutable handoff, scoped authentication, direct REST success, failure-history delivery with the original `SkillException`, and authorization rejection.
- Tighten recorded-request assertions so the first request is plan creation, later step requests expose only the appropriate public leaf schema, and final synthesis sees both Java and REST results.

### Success Criteria

#### Automated Verification

- [x] The changed cumulative test fails against the old `planning_mode: false` fixture because no public `PLAN_CREATED` event exists.
- [x] Focused integration test passes: `./mvnw --batch-mode --no-transfer-progress -pl loomspan-spring-boot-starter -Dtest=SupportedSurfaceIntegrationTest test`
- [x] Public boundary test passes: `./mvnw --batch-mode --no-transfer-progress -pl loomspan-spring-boot-starter -Dtest=LoomspanPublicSurfaceArchitectureTest test`
- [x] No second integration harness or production API is added.

---

## Phase 2: Complete Human, Agent, and Compatibility Guidance

### Overview

Make the root documentation exact, preserve the knowledge set's LLM-first routing, and add a durable beta 4 compatibility note.

### Changes Required

#### 1. Consumer README

**File**: `README.md`

**Changes**:

- Enumerate the exact ten REST-forbidden fields and say that null and empty presence are forbidden; retain required nonblank name/description, optional schema/roles, generic-object default, exact-name registration, shared input/reference/authorization path, success-only credit, and direct no-model execution.
- Describe `RestSkillInvocation`'s two components, recursively immutable map/list snapshot, allowed null values, preserved non-container/Resource identity, and the content boundary. State the REST-only exactly-one handler rule, no framework default, handler routing/URL/header/credential ownership, SecurityContext access rather than identity/session arguments, schema-permitted model inputs, one handler call, empty success/null failure, REST propagation versus Java adapter behavior, and no new sanitization guarantee.
- Make catalog descriptor fields, exact missing lookup, both validation overloads/null rules, matching exceptions, session/trace/callback-free behavior, no admission reservation, future-child limit, and execution-time recheck explicit.
- Keep observer timing/history/precedence and shutdown's positive default, one overall budget, independent event gates, continuing mission/nested limits, resource lifetime, cutoff fencing/interruption, and framework-owned boundary explicit. Identify Sidecar's separate immediate dispatch/queue rule without presenting it as framework behavior.
- Replace all release/tag examples with `1.0.0-beta.4` and `v1.0.0-beta.4` at the release step while retaining `1.0.0-beta.4-SNAPSHOT` only for development/local integration. Clearly separate validation-only manual workflow runs from tag-triggered publication.
- Link the beta 4 release note and gated readiness record from the release section.

#### 2. Version-aligned knowledge set

**Files**: routed documents listed in `Skill-Authoring Documentation Impact`

**Changes**:

- Compare every ticket claim with its existing topic owner and named executable anchor.
- Edit only concrete omissions or routing ambiguity. Preserve separate REST manifest/SPI, catalog/pre-check, invocation, observation/error, authorization, and compatibility topics; do not duplicate the release procedure into authoring guidance.
- Keep implementation anchors stable by class/test name, update coverage only if scope/confidence changes, and ensure an LLM loading only the routed documents can distinguish enforcement, recommendations, optional choices, and limitations.

#### 3. Beta 4 compatibility and release note

**File**: `docs/releases/1.0.0-beta.4.md`

**Changes**:

- Summarize new REST and catalog APIs and the sole handler SPI.
- Identify `SkillTemplate.validate` as an intentional source/binary interface change requiring application implementations/fakes to add both methods and recompile, with no default-method shim.
- Identify observer failure delivery and shutdown admission/budget as behavioral changes, with no success-only or old-shutdown compatibility mode.
- State REST manifest/startup migration rules and exact handler/result/security boundaries.
- Record the Console marker rationale: matching project versions, exact released equality, dual-development best effort, and no schema counter, legacy reader, range, or cross-version guarantee.
- Separate release notes from readiness: this document describes compatibility and must not claim that the external gate, final validation, tagging, or publication occurred.

### Success Criteria

#### Automated Verification

- [x] `python scripts/loomspan_version.py check`
- [x] `python -m unittest discover -s scripts/tests -p "test_*.py"`
- [x] README/document searches find every exact REST-forbidden field and `v1.0.0-beta.4`, and find no SNAPSHOT release/tag command.
- [x] Relevant documentation claims cite existing source/test anchors and the knowledge-set README routing remains internally valid.
- [x] `./mvnw --batch-mode --no-transfer-progress -pl loomspan-spring-boot-starter -Dtest=LoomspanPublicSurfaceArchitectureTest test`

---

## Phase 3: Record Local Snapshot Preparation and Preserve the External Gate

### Overview

Produce auditable local readiness evidence without converting preparation into a release claim.

### Changes Required

#### 1. Gated readiness record

**File**: `ai/thoughts/release-readiness/1.0.0-beta.4.md`

**Changes**:

- Record the exact Git commit, branch/worktree state, Java/Maven environment, current coordinated snapshot version, commands, timestamps, and results for version check, script tests, focused tests, `clean verify`, release-profile verify, and `mvn install`.
- Record the exact framework commit installed for Sidecar CI/development and require reinstall after any framework change.
- Use explicit status groups: local preparation, SC5 snapshot integration, integration-discovered framework remediation/reinstall/retest, final release-commit local checks, manual Console Release validation, and manual Maven Central Release validation.
- Leave SC5 and all downstream final checks `PENDING` until linked external evidence identifies the Sidecar commit/run and proves pre-dispatch validation/authorization, async JWT propagation through the YAML planner to REST handler and callback host, history modes, and packaged shutdown/resource ordering.
- State that validation mode must not publish and that no tag/publication is authorized by this record.

#### 2. Local verification and snapshot install

**Files**: no production source; append command evidence to the readiness record

**Changes**:

- Run the README dry-run sequence against the current implementation commit.
- Install `ai.loomspan:loomspan-spring-boot-starter:1.0.0-beta.4-SNAPSHOT` locally with `./mvnw --batch-mode --no-transfer-progress install` and record the exact commit.
- If any command or integration fails, record the failure rather than marking the corresponding gate complete; fix only in-scope framework defects, reinstall, and rerun affected checks.

#### 3. Post-SC5 final verification checkpoint

**File**: `ai/thoughts/release-readiness/1.0.0-beta.4.md`

**Changes**:

- After externally supplied SC5 evidence is present, verify it against the ticket's exact criteria and current framework commit.
- Rerun the full local dry run on the final commit, then manually dispatch Console Release and Maven Central Release workflows in validation mode and record run URLs/results.
- Keep tag creation, pushes, artifact upload, and publication outside this ticket and outside automated completion.

### Success Criteria

#### Automated Verification

- [x] Coordinated versions pass: `python scripts/loomspan_version.py check`
- [x] Version tooling tests pass: `python -m unittest discover -s scripts/tests -p "test_*.py"`
- [x] Full build passes: `./mvnw --batch-mode --no-transfer-progress clean verify`
- [x] Release packaging dry run passes without signing/publishing: `./mvnw --batch-mode --no-transfer-progress -Prelease -pl loomspan-spring-boot-starter -am -DskipTests -Dgpg.skip=true verify`
- [x] Snapshot installs locally from the recorded commit: `./mvnw --batch-mode --no-transfer-progress install`
- [ ] Final versions of those checks pass again after SC5 integration and any framework changes.
- [ ] Validation-only workflow runs complete on the final release commit and their publication jobs remain skipped.
- [x] The readiness record cannot show final readiness while SC5 evidence or either final workflow validation remains pending.

#### Optional Developer Checks

- Confirm the public beta 4 compatibility note is understandable to an application upgrading from beta 3.

## Testing Strategy

### Unit Tests

- Retain PR 5.1-5.3 owner tests as the authoritative proof for exact REST presence/cardinality/result rules, public value immutability, catalog/schema fidelity, validation null/error behavior, observer precedence, and shutdown lifecycle behavior.
- Retain `scripts/tests/test_loomspan_version.py` as the authority for coordinated replacement, derived fixture refresh, clean-worktree checks, and SNAPSHOT tag rejection.

### Integration Tests

- Update only `SupportedSurfaceIntegrationTest` to prove the cumulative public flow through a real YAML planner and both direct leaf kinds.
- Run the full Maven suite to catch application context, Console fixture, architecture, and lifecycle regressions.
- Treat real Sidecar HTTP/JWT/queue/container verification as an external gate with durable linked evidence, not as something this repository's test harness can simulate convincingly.

The dedicated testing plan in `ai/thoughts/plans/2026-09-12-loomspan-pr-5-4-framework-documentation-readiness-testing.md` specifies the red test, exact cases, commands, and exit criteria.

## Performance Considerations

The cumulative test will make more local stub calls because a planner adds plan creation, bounded task execution, and final synthesis. Keep tasks serialized and the response corpus minimal to avoid nondeterminism and unnecessary suite time. Documentation and readiness records have no runtime performance impact.

## Migration Notes

Applications upgrading from beta 3 must recompile and add both `validate` methods to any custom `SkillTemplate` implementation/fake, provide exactly one `RestSkillHandler` only when REST manifests are present, update REST YAML to the exact leaf-only field matrix, tolerate failure observer delivery, and account for new-root rejection/one-budget shutdown. No shim or dual-mode migration path is planned. Framework and Console diagnostic artifacts require exact matching resolved versions; historical/cross-version compatibility remains unsupported.

## References

- Original ticket: `ai/thoughts/tickets/loomspan-pr-5.4-framework-documentation-and-readiness.md`
- Research: `ai/thoughts/research/2026-09-12-loomspan-pr-5-4-framework-documentation-readiness.md`
- Design lens: `ai/thoughts/framework-feature-design-lens.md`
- FW4 phase: `ai/thoughts/phases/phase-fw4.md`
- Beta 4 roadmap: `ai/thoughts/phases/beta4-rest-skills-and-sidecar-roadmap.md`
- Similar cumulative integration: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/integration/SupportedSurfaceIntegrationTest.java:42-222`
- Version-aligned routing: `agent-skills/loomspan-docs/references/skill-authoring/README.md` and `agent-skills/loomspan-docs/references/java-api/README.md`
