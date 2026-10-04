# PR 18 independent code review 1

## Code Review Findings

The findings below were discovered against the unchanged candidate before fixes.
All are resolved in this context; final internal re-review found no actionable findings.

### [P2] Load recorded YAML through production catalog normalization

- **Location:** `src/test/java/ai/loomspan/internal/runtime/step/Pr18RecordedHandoffDiagnosticTest.java:33` and `:61` (capture/evaluator paths; now corrected).
- **Evidence:** The initial harness decoded YAML and directly constructed `YamlSkillDefinition`. `YamlSkillCatalog#validateInputObjectSchema` at `src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java:904` defaults omitted object `additionalProperties` to `false`. The retained `compareOptions.yaml` omits the root keyword. The historical request explicitly closed the root, while the candidate's saved resolved contract had root `additionalProperties: null`, and revised guidance explicitly allowed root extras. Its validator used the same unnormalized definition.
- **Trigger:** Run the diagnostic on the recorded experimental YAML, whose root openness is omitted and nested `context` openness is explicitly true.
- **Impact:** The claimed fixed-contract experiment changed root permissions and did not demonstrate the recorded closed-root/open-context handoff. Envelope equality alone did not prove contract fidelity, and previous live conclusions were unsupported as a controlled comparison.
- **Recommendation:** Load the immutable YAML through the real catalog, assert normalized boundaries and root-extra rejection, retain invalid attempts separately, then repeat the authorized comparison using corrected generated guidance.

### [P3] Exercise actual compact selection at the property threshold

- **Location:** `src/test/java/ai/loomspan/internal/runtime/step/StepPromptBuilderTest.java:587`.
- **Evidence:** Initially the `details.code` child gave schema depth three and raised total properties to seven/eight. Both conditions forced verbose selection even for the nominal six-property case. The assertions checked shared object rules without verifying detail selection.
- **Trigger:** Execute either unforced branch of `compactAndVerboseSelectionsKeepObjectRulesAcrossPropertyThreshold`.
- **Impact:** The named test and documentation claimed compact/verbose threshold protection without exercising compact selection. It could pass if compact guidance were lost or selection regressed.
- **Recommendation:** Use depth two and exactly six/seven total properties; assert type-detail absence in ordinary compact and presence in ordinary/forced verbose while retaining boundary assertions.

### [P3] Score parsing at the Framework's actual acceptance boundary

- **Location:** `ai/scripts/pr18_guidance_diagnostic.py` (`evaluate`, response parsing).
- **Evidence:** The initial evaluator applied strict Python `json.loads` directly to response content. `StepLoopMissionExecutionEngine#parseStepAction` first unwraps fenced blocks and deserializes `StepAction`. Corrected live sample 3 in the old arm returned valid fenced JSON; strict Python parsing rejected it, but the existing Framework parser accepts it.
- **Trigger:** A provider returns an otherwise valid action inside a fenced JSON block.
- **Impact:** The diagnostic could undercount usable handoffs and attribute a parser failure to guidance even though runtime accepts the response.
- **Recommendation:** Invoke the actual internal Framework parser in the opt-in diagnostic before unchanged contract validation; use its accepted actions for business comparison and report strict raw JSON syntax separately. Preserve raw responses and real parser failures.

## Findings Resolved in This Context

- **P2:** Both capture and evaluator now use `YamlSkillCatalog.loadSupplied` on unchanged retained YAML. Assertions protect root closure, context openness, all five required context fields, exact generated root rules, and unknown-root rejection. The Python controls assert the normalized contract and historical/revised root closure. All original UTF-8 attempt artifacts were byte-copied into `invalid-unnormalized-contract-attempt/`, with an explicit invalidation notice, before replacing parent-directory outputs. Six fresh alternating calls were made under existing authorization; none of the invalid attempts support final scoring.
- **P3 threshold:** The fixture now has a depth-two empty open `details` object and exactly six/seven total properties. It asserts scalar detail absence/presence according to ordinary/forced selection. Focused 35-test run and post-fix full regression passed.
- **P3 parsing:** The opt-in evaluator invokes the actual private internal engine parser via test reflection, then validates its `StepAction` using the normalized contract. Python consumes saved accepted actions and records `strictRawJsonParseValid` separately. Final opt-in two-test run passes; all six final responses are Framework-accepted, including the old fenced response.
- Governing plans and diagnostic documentation record normalization, parser fidelity, invalid-run attribution and final outcomes. No production implementation changes were made by this review.

## Open Questions and Assumptions

- No developer decision remains. The selected profile is `full`; shared production model guidance warrants that profile throughout the review/fix cycle.
- Historical temperature and output-token defaults are unknown. Both current arms retain identical omissions and model/reasoning settings. Provider defaults/routing are not claimed immutable.
- The engine capture admits the recorded task at step one and regenerates user JSON formatting. Controlled live arms retain the exact historical step-five envelope and user bytes; only the engine-generated argument-shape section is transplanted. This deliberate difference is independently checked and documented.

## Scope and Independent Reconstruction

- Branch `main`, HEAD `b32c5d50ad1eff336d4c14bc9a4ca9cd521f48d0`, upstream `origin/main`. `git diff origin/main...HEAD --stat` is empty: no committed branch delta. Index diff is empty. The review scope consists of unstaged and untracked ticket implementation.
- Tracked scope: one production renderer; validator, builder and engine tests; routed input-contract documentation and its README coverage row.
- Untracked scope: renderer test, opt-in recorded-handoff diagnostic test, Python diagnostic, ticket/research/plans and generated evidence (including original encoding-failure artifacts). Prior review artifacts were not searched or read. No unrelated implementation changes were identified.
- Traced resolved metadata through `StepPromptBuilder#formatToolArgumentGuidance`, detail selection, renderer, `SkillPromptComposer`, engine ordinary/corrective `ModelInteractionRequest` construction, exact assignment/input validation and tool execution. Also checked YAML catalog normalization and codec ownership. Complete prior results and mission/rejected-candidate evidence remain separate from generated illustrations.
- Renderer rules use immutable resolved nodes and a shared recursive walk in both modes. Every object gets its own required/optional/closed/open/constrained-extra rule. Array and typed-map paths recurse without the old rule-depth cutoff. JSON quoting distinguishes literal dotted names; generic and strict-empty identities remain intact. Examples are explicitly illustrative and openness does not prescribe automatic data copying.
- Reviewed applicable correctness, security/access, compatibility, lifecycle, concurrency, resource, diagnostic and documentation concerns. No runtime authorization, provider, invocation, schema, reference, persistence or configuration implementation changes exist. The singleton codec is reused; rendering has no shared mutable request state. Necessary rule growth follows the already-recursive finite schema illustration, with no new network work or runtime data propagation. No schema-independent or model/business-specific production branch was introduced.
- Content is preserved; no sensitivity classification or automatic rewriting was introduced. The existing journal-projector exception is untouched. Provider authorization uses the existing header and is not stored in request bodies. Source captures, sibling skill/replay/business/configuration/deployment files remain unchanged.

## Verification Results

Commands were run from `C:/opendev/code/loomspan-framework` in this review context:

- PASS — `./mvnw.cmd '-Dtest=SkillInputPromptRendererTest,StepPromptBuilderTest,SkillInputContractResolverTest,SkillInputValidatorTest,StepActionValidatorTest,StepLoopMissionExecutionEngineTest,LoomspanPublicSurfaceArchitectureTest' test`: 148 tests, no failures/errors/skips, initial candidate.
- PASS — `./mvnw.cmd test`: initial candidate 1262 tests; repeated after normalization/threshold fixes 1262 tests; zero failures/errors and two expected opt-in diagnostic skips in both runs.
- PASS — `./mvnw.cmd '-Dtest=Pr18RecordedHandoffDiagnosticTest,StepPromptBuilderTest' '-Dpr18.diagnostic=true' test`: 35 tests, no failures/errors/skips.
- PASS — `python ai/scripts/pr18_guidance_diagnostic.py prepare`: immutable source reconstruction, exact complete trace/provider linkage and current provenance.
- PASS — `python ai/scripts/pr18_guidance_diagnostic.py controls`: corrected normalized root/open-child and exact guidance-only envelope controls.
- PASS — `python ai/scripts/pr18_guidance_diagnostic.py live`: six fresh alternating calls, all HTTP200; saved complete bodies/responses/timings.
- PASS — `./mvnw.cmd '-Dtest=Pr18RecordedHandoffDiagnosticTest' '-Dpr18.diagnostic=true' test`: final two opt-in tests, no failures/errors/skips; actual capture and actual parser/normalized validator evaluation.
- PASS — `python ai/scripts/pr18_guidance_diagnostic.py evaluate`: six outbound-arm equalities, separate structural/business outcomes and source-integrity outputs. Repeated after the final neutral accuracy-summary wording change.
- PASS — `python -m py_compile ai/scripts/pr18_guidance_diagnostic.py`.
- PASS — `git diff --check`, including after fixes/documentation.
- Read-only evidence audit independently recomputed primary/protected source hashes, reconstructed trace sequence 480's complete messages, compared old/new settings and all content outside guidance, and compared six saved outbound requests. Final integrity outputs report all five primary and 136 protected sibling files unchanged.
- The full post-fix suite preceded the final opt-in-only parsing refinement. The final opt-in run recompiled and exercised that refinement; normal tests disable this diagnostic. No further full-suite repetition was warranted.

## Requirements and Plan Conformance

| Ticket criterion | Final evidence |
| --- | --- |
| Closed root/open child with local requiredness | Renderer both-mode tests; builder integration; engine correction test; corrected recorded root/open-context capture assertions |
| Other open/closed combinations, arrays and constrained extras; nonexhaustive/optional examples | Recursive renderer matrix includes open-root/closed-child, empty objects, omitted reflected openness, consecutive arrays, deep typed additional arrays/objects, enums, unconstrained values and literal dotted keys |
| Compact, verbose, corrective and generalized guidance | Both-mode unit coverage; corrected true six/seven-property selection test; actual ordinary/rejected/corrected request assertions with shipment fields |
| Unchanged validation/data/references/permissions/API | Existing resolver/validator/action-validator and architecture tests plus complete repository suite; production diff confined to renderer; exact accepted arguments and rejected candidate assertions |
| Actual generated recorded handoff | Engine model-boundary capture using production-catalog-normalized original YAML; full request/schema/provenance retained |
| Controlled repeated GLM comparison | Final six alternating old/revised requests differ only in generated guidance, with fixed model/reasoning settings and immutable authored contract/instructions/evidence; actual parser and validator outcomes retained |
| Separate correctness from accuracy; preserve failures/business criteria | Final report distinguishes schema validity from exact evidence retention; both invalid experiments remain retained and excluded; no business thresholds or model-specific exceptions changed |

- **Implemented:** All criteria are supported by final code/tests/documentation/evidence. No partial or missing criteria remain.
- **Safe deviations:** Author descriptions were deferred as the ticket permits. The opt-in test admits a fixed recorded plan rather than replaying an entire changing mission. Diagnostic-only reflection uses the actual private parser to avoid a parallel acceptance policy and adds no production API.
- **Live observation:** Old and revised each have 3/3 Framework-parsed, exact-assignment/identity, normalized-contract-valid actions. All six retain the five mandatory source values. Old 0/3 and revised 3/3 retain all canonical context and full upstream data/determination/quotes exactly. Both issued quotes are copied exactly once in every revised action. The old fenced response is runtime-valid despite invalid strict raw JSON syntax. This is one fixed handoff with three samples per arm, not reliable or universal accuracy evidence.
- **Compatibility:** `SkillInputPromptRenderer` is explicitly internal collaboration in the architecture allowlist, not supported API despite its public modifier. No allowlisted API signature, `RestSkillHandler` SPI, Spring replacement contract, configuration/manifest default, durable format or Console/Java-to-Go boundary changes. No shim is justified; obsolete prompt wording/recursion is removed atomically. YAML defaults are exercised, not changed. No ticket pipeline notes authorize a semantic break, and none is present.

## Skill-Authoring Documentation Impact

- **Assessment:** Affected.
- **Rationale:** Authors need accurate expectations of illustration, optionality, object-scoped restrictions/permissions and business field ownership; enforcement remains separate.
- **Documents reviewed:** `agent-skills/loomspan-docs/SKILL.md`, skill-authoring `README.md`, `input-contracts.md`, `source-verification.md`; checked-out skill metadata and Maven version align at `1.0.0-beta.8-SNAPSHOT`.
- **Evidence checked:** Renderer/resolver/schema node/validator, exact builder selection, correction integration, corrected recorded catalog/capture path, focused tests and unchanged source YAML.
- **Coverage table:** Current; the Input contracts row identifies scoped guidance and required/optional illustrations while retaining incomplete pure-YAML syntax coverage.
- **LLM-first usability:** Pass. The added subsection is self-contained, distinguishes illustrations from enforced rules, explains paths and typed extra values, and keeps business selection with authored instructions. Named test anchors support claims without adding consumer-facing internal APIs.
- **Drift classification:** Final authoring guidance is aligned. Initial diagnostic documentation claiming a fixed recorded closed-root comparison was documentation drift because its harness bypassed normalization; it is now explicitly superseded and corrected. No production schema defect was found.

## Residual Risks and Optional Developer Checks

- Provider sampling/defaults/routing and broader model/mission accuracy cannot be established by this small diagnostic. The reported six outcomes are retained observations, with no universal conclusion.
- The diagnostic is opt-in and requires unchanged sibling source evidence; ordinary Maven tests do not require that checkout or credentials.
- No optional developer checks are required. No deployment occurred, so no provider-disabled service restoration was needed.

## Disposition

**Candidate clean; fresh review required.** One P2 and two P3 findings were fixed; no P0/P1 findings and no open actionable findings remain after complete internal re-review. Because this context changed tests, diagnostic logic, evidence and governing documentation, `REVIEW_RESULT` is `fixes-applied`. A new independent context must review the final repository state.
