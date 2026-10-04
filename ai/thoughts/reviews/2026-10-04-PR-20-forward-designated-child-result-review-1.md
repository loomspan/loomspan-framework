# PR 20 designated-child result — independent review 1

## Code Review Findings

### [P2] Accept producer-bounded forwarding identity previews in live activity

- **Location:** `loomspan-console/internal/live/dto.go:143` (the original candidate used `strings.TrimSpace(value) == ""` here).
- **Evidence:** `PlanStructureValidator` and `PlanTask` accept any nonblank task ID without trimming. `LiveActivityProjector#addScalar` retains only the first `ExecutionObservationLimits.TEXT_CODE_POINTS` (256) code points of scalar detail text. The new forwarding-specific Go validator incorrectly applied canonical nonblank identity semantics to that preview. `live.Service` treats validation failure as `response_decode` and stops consuming the activity stream.
- **Trigger:** A valid task ID consisting of 256 spaces followed by `selected-task`. Canonical task selection and forwarding succeed, but the emitted live `linkedTaskId` contains only the spaces.
- **Impact:** A successful invocation disrupts Console live activity consumption because the matching Framework-produced event is rejected.
- **Recommendation:** Validate live detail fields as required nonempty String previews; retain strict nonblank validation of complete canonical forwarding facts. Document where complete identity is available and cover both producer truncation and consumer acceptance.
- **Status:** Resolved in this context. No remaining actionable findings after internal re-review.

## Findings Resolved in This Context

- P2 above: changed only live forwarding field validation from nonblank to nonempty String text. Full canonical `decodeResultForwarding` remains strict and unchanged. Added `TestForwardedActivityAcceptsBoundedTaskIdentityPreview`, retained null/empty/nonstring rejection, added Java `forwardingTaskPreviewRetainsExistingScalarBoundsWithoutChangingCanonicalIdentity`, and documented the preview/full-record distinction in the existing debugging topic. Existing resource bounds, canonical values, returned results and lifecycle ownership were preserved. No aliases, fallback, compatibility reader or new representation was introduced.
- Red reproduction: `go test ./internal/live -run TestForwardedActivityAcceptsBoundedTaskIdentityPreview -count=1` failed with `forwarding linkedTaskId must be a nonblank string` before the production correction. The Java regression demonstrates the actual projector output and unchanged full canonical identity.

## Open Questions and Assumptions

- None requiring a developer decision. The parent supplied the full profile and explicit necessary Java–Go/no-shim development decisions, which are also persisted in the implementation/testing plans.
- Initial scope: branch `main`, HEAD `5c46862d04c85ba0d37c40b52feb35094a280b67`; no staged changes or committed ticket delta. The candidate was the complete unstaged/untracked ticket change (87 tracked changed files plus new integration/boundary tests and six forwarding fixtures, and governing artifacts). Research records the initial checkout contained only this ticket, and no unrelated dirty implementation was identified. Prior review artifacts were neither located nor read.
- The full independent candidate review, candidate-finding tracing, tests, requirements mapping and documentation-impact assessment were completed before the first implementation-artifact edit. This context then fixed the finding and re-reviewed the resulting complete change. The selected full profile remained appropriate throughout.

## Verification Results

Commands below were executed in this review context, not copied from implementation receipts. Root commands used PowerShell and Console commands used `loomspan-console` as working directory. Logs are ignored task-owned files under `target/pr20-verification/`.

- PASS — `.\mvnw.cmd '-Dtest=YamlSkillCatalogTests,SkillGenerationManagerTest,StepLoopMissionExecutionEngineTest,DesignatedChildResultIntegrationTest,ApplicationApiValueTest,LoomspanPublicSurfaceArchitectureTest,ConsoleTraceFixtureCorpusTest,ConsoleRestFixtureCorpusTest,ConsoleSseFixtureCorpusTest,ExecutionJournalProjectorTest,LiveActivityProjectorTest' test *> target/pr20-verification/review1-focused.log`: 320 tests, zero failures/errors/skips. This independently checks configuration/metadata, completion/lifecycle, supported facade, closed public surface, read-only generated corpus matching and journal/live projections.
- PASS — `.\mvnw.cmd test *> target/pr20-verification/review1-full-java.log`: 1,317 tests, zero failures/errors, two existing skips. Completed against the candidate before the added regression was compiled; Java production code was not changed by this review fix. The unchanged invalid-action exhaustion test passed in this review's full run.
- PASS — `.\mvnw.cmd '-Dtest=LiveActivityProjectorTest,LoomspanPublicSurfaceArchitectureTest' test *> target/pr20-verification/review1-fix-java.log`: final added projector regression plus public architecture checks, 28 tests, zero failures/errors/skips.
- PASS — `go test ./...`: initial full Console Go suite, exit 0; Go reported cached results where applicable.
- FAIL (expected red, resolved) — `go test ./internal/live -run TestForwardedActivityAcceptsBoundedTaskIdentityPreview -count=1`: concrete bounded-preview defect above.
- PASS — `go test ./internal/live ./internal/traceanalysis -count=1`: uncached live and canonical analysis checks after correction, including strict full canonical identity and single/nested forwarding fixture projection; exit 0.
- PASS — `go test ./... *> ../target/pr20-verification/review1-fix-go.log`: full Go suite after correction, exit 0.
- PASS — `go run ./internal/buildtool verify *> ../target/pr20-verification/review1-console-verify.log`: initial standard Console verification, exit 0; frontend typecheck, 46 files/522 tests with coverage, production build, Go and fixture/evaluation verification.
- PASS — `go run ./internal/buildtool verify *> ../target/pr20-verification/review1-fix-console-verify.log`: final standard verification after correction, exit 0; frontend typecheck, 46 files/522 tests with coverage, production build, Go and fixture/evaluation verification all completed.
- PASS — `python scripts/loomspan_version.py check`: consistent `1.0.0-beta.8-SNAPSHOT`.
- PASS — `python -m unittest discover scripts/tests -v *> target/pr20-verification/review1-python.log`: 13 tests, exit 0.
- PASS — `git diff --check` and `git -c core.safecrlf=false diff --check`: exit 0; ordinary line-ending warnings are not content failures.
- NOT RUN — fixture regeneration: this review did not change canonical fixture writers or corpus bytes; the read-only Java corpus checks and Go readers verified existing generated content.
- NOT RUN — `go test -race ./...` and browser navigation E2E: the correction changes scalar validation only; no Go shared-state ownership or new navigation was introduced. Standard checks and focused boundary regressions cover affected behavior.

## Requirements and Plan Conformance

All seven ticket acceptance criteria were independently traced to current code and executable evidence:

| Acceptance criterion | Evidence and review conclusion |
| --- | --- |
| Prerequisite orchestration and exact model/Java/REST result, no final parent synthesis; chains | `StepLoopMissionExecutionEngine` selects the accepted exact direct task ID before traversal and returns its mission-retained String without parse/reserialize. `forwardingRunsPrerequisiteBeforeSelectedTask`, `forwardingPreservesEveryDirectResultString`, complete-unit/later-work tests and facade model/Java/REST/no-schema/chain tests assert exact results and absent parent final requests. Java quoted Strings and REST business-looking envelopes retain their producer contract. |
| Full work/child success, missing/failed/ambiguous/cancelled/ordinary failures cannot succeed or synthesize | Normal unit admission, ordered joins/folds and first-failure propagation remain the execution owner. Completion checks plan identity/status/task bindings plus every retained successful result under the existing writable lifecycle fence. Missing/ambiguous task, missing/mismatched/stale retained completion, selected/later failure and parameterized timeout late-write tests fail closed and emit no forwarding record. Existing depth/quota/auth/shutdown and ordinary lifecycle tests remain in the full passing suite. |
| Strict declarations/plans/cycles, isolation/access/definition identity | Raw loader requires an object with exact skill name, explicit planner mode, direct required unique child, and disallows schema/retry/linter presence including null. Existing constraint validator enforces exact task count and existing structure/visibility/single-correction paths remain authoritative. Complete-generation DFS rejects self/multinode forwarding cycles and missing targets before activation while ordinary recursive allowlists remain valid. Facade latch-based concurrent roots and midexecution publication retain own results, old targets and old immutable metadata. |
| Effective schema or unspecified; validation stays with producer | `SkillGenerationManager#resolveOutputSchema` resolves only forwarding edges in the complete candidate generation and memoizes results. Normalized metadata omits child-local evidence, while the authored definition schema and evidence contract remain unchanged. Java/REST/no-schema producers resolve null. Catalogs and bound planner metadata use this generation-owned tool descriptor. Facade model/chain correction tests exercise producer validation and no duplicate final parent requests. |
| Ordinary synthesis unchanged; closed Java surface | Reservation is zero for forwarding and one for synthesis; ordinary final `executeOneStep`/validators remain intact. Existing N+1 admission/correction tests and `ordinarySynthesisRemainsAvailable` pass. Public descriptor deliberately has five components/one constructor, API value tests prohibit obsolete constructor, mandatory architecture suite passes, no new top-level API/SPI/bean replacement path was introduced. |
| Accurate diagnostics and task identity without fictitious parent model response | Trace recorder emits `RESULT_FORWARDED` against the exact current parent mission frame under the state-service fence. Journal/live vocabularies and typed Java/Go/TS record/activity mappings agree. Java-produced single/nested corpus records follow complete task status and precede mission closure; Go persisted record facts and browser/MCP mappings retain exact full identity, without model attempts. Live details use existing bounded previews; the resolved finding aligns consumer validation with that distinction. |
| Complete reusable author guidance | Updated routing/coverage, output contracts, planning/budget/REST/debug references, design checklist, Java references, README and architecture explain opt-in syntax, meaningful orchestration, synthesis choice, exact return/no fallback, chains/schema ownership, limitations and examples for all three child kinds. No motivating equipment-specific answer or provider workaround is introduced. |

- **Partial:** None.
- **Missing:** None.
- **Safe deviations:** Exact proposed test names were consolidated into existing families; existing planning cardinality/visibility/correction tests protect the reused validator instead of a duplicate forwarding validator. Corpus diagnostics use canonical recorder/writer fixtures plus actual runtime/facade tests for execution behavior. No new task-link navigation was added. The review fix clarifies live previews without changing exact canonical provenance.
- **Compatibility review:** The allowlisted `SkillDescriptor` constructor/record arity, equality and serialized shape are deliberately changed under explicit no-shim authorization, with all in-repository constructors/docs updated. `SkillTemplate` String results, `RestSkillHandler`, existing nonforwarding YAML, authorization and execution limits remain protected. Internal signatures and ephemeral writer/reader projections change atomically. The marker is still the coordinated unreleased snapshot release String per the recorded version decision; current fixture/new record semantics do not promise historical snapshot readability. `TestProcessorRejectsTraceWithoutCompatibilityMarker`, `TestProcessorRejectsIncompatibleCompatibilityMarker`, `TestProcessorCompatibilityMarkerValidationMatrix` and `TestClientConsumesCommittedInstanceFixtureOnlyAfterExactCompatibility` remain in the passing full Go checks; no marker ranges, repair readers or fallbacks were introduced.
- **Security/ownership/resource assessment:** No visibility widening or alternate/global result lookup; ordinary bound capability auth/inputs/captured-generation ownership remains authoritative. The feature reuses mission result storage and lifecycle instead of a second mutable store/executor. Canonical/returned content is unchanged and no sensitivity scanner/masking path was introduced. Existing journal field-name projection exception remains narrow. Memoized graph resolution and exact-ID result access avoid repeated active-catalog traversal; standard resource admission/bounds remain.

## Skill-Authoring Documentation Impact

- **Assessment:** Affected; final drift classification **aligned** after the resolved preview clarification.
- **Rationale:** New manifest declaration, alternate completion/step cost, derived output metadata and diagnostics directly affect skill authors. Producer validation and ordinary synthesis remain separate contracts.
- **Documents reviewed:** repository `agent-skills/loomspan-docs/SKILL.md`, skill-authoring README/source-verification/mental-model/output-contracts/planning-task-constraints/planning-concurrency/rest-skills/traces-and-debugging/design checklist; Java API catalog-and-validation/invocation/compatibility-and-boundaries; root README and architecture; fixture README. Same checked-out package and product version were used after executable inventory.
- **Evidence checked:** strict YAML loader/candidate-generation paths, authored schema/evidence consumers, planning validators/bound metadata, mission completion and result retention, actual producer serializer/REST return path and facade HTTP tests, captured generation/lifecycle tests, trace recorder and Java/Go corpus/projections, TS inert-text presentation and output schemas.
- **Coverage table:** Current; routes forwarding to the existing focused output-contracts topic, planning/debugging additions are reflected in topic coverage.
- **LLM-first usability:** Pass. Decision table and minimal separately scoped model/Java/REST examples explain required/prohibited fields and output representation; no duplicate schema authority or universal reasoning/accuracy guarantee. Exact canonical identity and bounded live-preview distinction are explicit.

## Residual Risks and Optional Developer Checks

- No required unverified acceptance criterion or external developer decision remains. No live provider, application migration or rollout was requested or performed.
- Existing two full-Java skips remain skips. Maven emits existing BeanPostProcessor/JDK/validation warnings and the frontend build emits its existing large-chunk warning; completed commands return success. No invariant was weakened to bypass a failure.
- Full canonical forwarding identity remains exact; existing live preview limits can shorten it. Use the canonical record rather than a bounded live value for full identity. This is documented resource behavior, not sensitivity classification or alteration of results.
- Optional developer checks: None required; deterministic executable checks cover the ticket's intended behavior.

## Disposition

**Candidate clean; fresh review required.** One P2 finding was identified and fixed; zero remaining P0/P1/P2/P3 findings after complete internal re-review. This context changed four implementation artifacts (Go live DTO/test, Java projector test, debugging guidance), so it must return `fixes-applied`; only a fresh independent review may certify completion.
