## Code Review Findings

No actionable findings remain after the completed review/fix cycle.

## Findings Resolved in This Context

### [P2] Qualify the prohibition on all-member dependencies

- **Location:** `src/main/java/ai/loomspan/internal/runtime/planning/DefaultPlanningService.java:660` and `agent-skills/loomspan-docs/references/skill-authoring/planning-concurrency.md:38`.
- **Evidence:** The initial candidate said "Do not add dependencies on unrelated earlier tasks or every member of an earlier parallel group." The prohibition on every member was unconditional. The existing topic's valid `fetch-a`, `fetch-b`, `combine` example explicitly depends on both group members, and `PlanStructureValidatorTest#acceptsOmittedNullAndExactValidGroupsWithEarlierUnitDependencies` accepts equivalent references.
- **Trigger:** A later task directly requires results from every member of an earlier joined group.
- **Impact:** The planner receives conflicting instructions: declare every directly required result, but do not depend on every group member. This can encourage the same incomplete dependency declarations the ticket aims to address. No observed stochastic model failure is claimed.
- **Fix applied:** Prohibit all-member dependencies merely because tasks share a group; explicitly include all members when every result is directly required. Align the topic and selective-result prompt assertion, and add `PlanningServiceTest#planningPromptAllowsEveryEarlierGroupMemberWhenAllResultsAreRequired` to capture actual requests and preserve both accepted edges.
- **Verification:** The full focused command passed after the fix with 116 tests. The complete resulting ticket-scoped diff was re-reviewed for correctness, contract preservation, documentation coherence, and test adequacy before disposition.

## Open Questions and Assumptions

- None. Research and plan artifacts are intentionally absent under the approved fast-track route. No skipped-stage artifacts were required or created.
- Fast-track eligibility remains valid: changes and fixes are bounded to generated planning guidance, documentation, and offline verification. Scheduling, lifecycle, authorization, public APIs, validation, and external contracts do not change.

## Verification Results

- PASS — `mvn "-Dtest=PlanningServiceTest,PlanStructureValidatorTest,ExecutionUnitTest,StepLoopMissionExecutionEngineTest,LoomspanPublicSurfaceArchitectureTest" test`: independently run before fixes, 115 tests; independently rerun after fixes, 116 tests; both runs had zero failures, errors, or skips.
- PASS — `git diff --check`: independently run before and after fixes; no whitespace errors. Git emitted existing CRLF-to-LF normalization warnings for the changed documentation.
- NOT RUN — `mvn test`: the entire repository suite is disproportionate to two planning-guidance lines; targeted tests cover actual prompt capture, accepted fields, validation rejection, execution-unit ordering/join, and the mandatory supported-surface architecture checks.
- NOT RUN — live model comparison: optional, no credentials needed for this offline contract change; no accuracy improvement is asserted.

## Requirements and Plan Conformance

- **Scope reconstructed independently:** branch `main`, unstaged diff against `HEAD`; five modified files, no staged changes or untracked files at review start. The production delta is two lines in `DefaultPlanningService`; other changes are focused tests, the planning topic, its coverage index, and ticket acceptance/execution notes. The checkout was clean before the run according to the supplied scope. No prior review artifacts were located or read.
- **Declared direct-result relationships despite barriers:** `buildPlanningPrompt` supplies the agreed sentence through `requestPlanAttempt`, `SkillPromptComposer`, and the actual `ModelInteractionRequest`. The captured-request test exercises both singleton and grouped predecessors.
- **Earlier units, independent members, full joins:** adjacent prompt rules preserve all three statements. `PlanStructureValidator` rejects unknown, self, same-unit, and forward references; `ExecutionUnit.partition` groups consecutive members; `StepLoopMissionExecutionEngine` traverses units sequentially and joins all ordinary member outcomes before subsequent admission. Focused validator, partition, and engine tests passed.
- **Selective edges and no mandatory closure:** the scripted source/required/unrelated/consume plan preserves only `required` on `consume`, with no transitive `source` or unrelated edge. Added verification also preserves both directly required edges when all group results are needed.
- **General guidance and unchanged implementation:** production text contains no business-specific example, hardcoded skill, or provider/model exception. The actual diff changes no scheduling, validation, serialization, configuration, or plan-repair code.
- **Evidence distinction:** tests are scripted prompt-contract checks, not model-response comparisons. Topic and ticket explicitly distinguish that evidence from unobserved accuracy gains.
- **Partial / missing:** none.
- **Safe deviations:** an additional all-required group case clarifies the settled requirement to declare actual direct results; it introduces no new dependency interpretation or runtime behavior.
- **Compatibility review:** `DefaultPlanningService` is technically public internal implementation, explicitly classified by the architecture allowlist. No supported `ai.loomspan.api` signature or sole supported `RestSkillHandler` SPI changes, no new types or replacement beans, no configuration/manifest validation change, no serialized/diagnostic format or Java-to-Go protocol change. Existing author-facing execution semantics remain protected. No compatibility shim is warranted.
- **Security / operational review:** no trust/access boundary, tool input, retained data, diagnostic rendering, resource bound, or authorization logic changes. No sensitivity classification or masking is introduced; the existing journal exception remains untouched.

## Skill-Authoring Documentation Impact

- **Assessment:** Affected.
- **Rationale:** Authors and models need to distinguish ordered availability from explicit required-result declarations, selective dependencies from blanket group edges, and guidance from runtime enforcement.
- **Documents reviewed:** matching-checkout `agent-skills/loomspan-docs/SKILL.md`; `references/skill-authoring/README.md`, `planning-concurrency.md`, and `source-verification.md`; repository documentation/source protocol and framework design lens.
- **Evidence checked:** actual production prompt composition/request, `SimpleChatClient` serialization and request capture, both new planning tests, `PlanStructureValidator` and focused tests, `ExecutionUnit` and tests, engine traversal/join and focused execution tests.
- **Coverage table:** Current; the planning coverage row includes direct-result guidance despite ordering barriers, and the topic cites both focused tests.
- **LLM-first usability:** Pass after fixes; the topic is self-contained, retains routing and concise normative distinctions, provides the valid all-member example, and clearly marks offline evidence limitations.
- **Drift classification:** aligned after fixes. The initial prompt/document ambiguity was resolved to match valid direct-result relationships and unchanged executable behavior.

## Residual Risks and Optional Developer Checks

- Prompt-contract tests prove guidance delivery and unchanged deterministic acceptance, not that a model follows the wording consistently.
- Optionally compare live model responses on representative domain-independent plans before/after if accuracy evidence is desired. This is not a completion gate.
- The broader repository suite was not run; the focused suite is sufficient for this bounded guidance change.

## Disposition

**Candidate clean; fresh review required** — one P2 finding resolved, zero remaining P0/P1/P2/P3 findings. This context modified production guidance, tests, and documentation; a separate fresh Step 5 review must establish final pipeline completion.
