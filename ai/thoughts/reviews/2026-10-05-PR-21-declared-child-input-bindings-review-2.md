# PR 21 Independent Code Review 2

## Code Review Findings

**No actionable findings.** P0: 0; P1: 0; P2: 0; P3: 0.

## Findings Resolved in This Context

None. This context changed no implementation artifact; its only authored change is this review document.

## Open Questions and Assumptions

None requiring a developer decision. Review ran in pipeline Step 5, selected profile `full`, against `main` at `cb37a28e0acf892744c2bbafd6c7cb657be757b1`. The original-checkout attribution in research establishes all present production, tests, documentation, research and plans as PR 21 work. There are no staged changes or committed ticket changes. The scope includes every tracked change and untracked binding implementation/test/topic file, plus the supplied ticket/research/plans. Prior review documents were excluded and were not read.

## Verification Results

- PASS — `.\mvnw.cmd '-Dtest=ChildInputBindingTest,ChildInputBindingProjectionTest,ChildInputBindingAssemblerTest,AcceptedResultDecoderTest,PlanInputBindingDependencyValidatorTest,SkillInputValidatorTest,YamlSkillCatalogTests,SkillGenerationManagerTest' test *> target/pr21-review2-focused.log`: 220 tests, zero failures/errors/skips.
- PASS — `.\mvnw.cmd test *> target/pr21-review2-full.log`: 1377 tests, zero failures/errors, 2 existing optional PR18 diagnostic skips; BUILD SUCCESS. Includes `LoomspanPublicSurfaceArchitectureTest`, all binding tests, reference/forwarding regressions, authentication/authorization, usage, grouped execution and canonical trace/journal suites.
- PASS — `git diff --check`: no whitespace errors; CRLF conversion notices are informational.

Both test commands were run in this independent context against the unchanged candidate. The full suite completed in 2 minutes 57 seconds. Logs are local ignored build output, not new implementation artifacts. No paid provider, external-provider profile, sibling suite, replay refresh, commit, push or release operation was used.

## Requirements and Plan Conformance

Independent defect review preceded conformance conclusions. The reviewed paths include raw YAML parsing through normalized declarations and complete generation, projection through planning/assigned/corrective guidance and action checks, joined worker result retention through binding assembly, and common receiving validation through model/Java/REST dispatch and trace recording.

| Ticket acceptance area | Checked executable evidence |
| --- | --- |
| Both source kinds, renamed fields and complete authoring example | `YamlSkillCatalog`, `AllowedSkillConstraint`, `ChildInputBindingDeclarations`, `SkillGenerationManager`; catalog/generation/path tests and `completeAuthoringExampleLoadsAsOneValidatedGeneration` |
| Large evidence with empty or reasoning-only model arguments | `DeclaredChildInputBindingsIntegrationTest#deliversLargeBoundEvidenceFromEmptyModelArguments`: all nine model/Java/REST producer-consumer pairs; exact 250-record nested payload and BigInteger/BigDecimal equality |
| Effective contracts and prohibited overrides | `ChildInputBindingProjection`, `StepActionValidator`, `StepPromptBuilder`, `DefaultPlanningService`; projection/action/prompt tests and actual assigned override-correction test |
| Null/absence, exact numbers/dates/strings, immutable detached containers | `AcceptedResultDecoder`, `DeepInputValues`, `ChildInputBindingAssembler`, `SkillInputValidator#validateExact`, `MissionContext`; decoder/assembler/input/mission tests and receiving zero-dispatch guards |
| Invalid declarations, source ambiguity/unavailability and failures | Generation graph/path/cardinality checks, dependency validator, assembler and invoker preflight; focused negative matrices, exhausted planning with no stored plan, attributable failure trace |
| Producer ordering, concurrency and parent isolation | `PlanInputBindingDependencyValidator`, step-engine preflight, immutable pre-unit snapshots and accepted join folding; grouped bound-producer tests in both concurrency modes, concurrent/nested/reload isolation and existing lifecycle/cutoff tests |
| Model/Java/REST, authorization and budgets | Common router/coordinator dispatch, unchanged generation capability identity/access guard/usage admission; nine-kind integration, bound authorization denial and tool quota zero-dispatch tests, existing identity and usage suites |
| Raw/effective/provenance canonical evidence | `DefaultCapabilityInvoker`, retained raw results and existing canonical writer/reader; `DeclaredChildInputBindingsTraceTest` success/failure roundtrip and current trace/corpus/journal suites |
| Unbound/ref/output forwarding and closed API | Existing receiving policy when no exact path applies, normal reference-resolution owner, unchanged forwarding return authority; full regression suite and supported-surface architecture test |
| Offline proof and accurate author guidance | Local fake model/REST and registered Java fixtures, tested full guide example, source/test anchors and routing/coverage updates |

Implemented: all ticket criteria and plan phases have matching executable evidence. Partial: none. Missing: none. The historical red-first run is recorded in the implementation artifacts; this review does not claim to have reproduced the pre-implementation failure.

Safe deviations: dedicated projected schema, assembly and dependency collaborators remain internal; the obsolete internal `CapabilityInvoker` interface was removed atomically. Bound preflight normalizes only unbound contributions before tool-frame capture and the common router repeats validation with exact paths, keeping evidence and delivered input coherent. Focused verification was followed by the entire offline suite instead of repeating every adjacent focused filter individually.

Compatibility review: the Application API allowlist and sole `RestSkillHandler` SPI are unchanged, with no leaked internal signature types or added replacement-bean contract. New manifest behavior is opt-in and documented. All new technically public top-level helpers are classified as internal by the architecture test. The developer's destructive-change/no-shim direction is persisted in research and plan; removal and caller updates stay inside ticket scope. No compatibility shim was introduced. Trace provenance uses existing extensible tool-frame payloads; there is no new closed Java-to-Go envelope, record type, compatibility marker or persisted binding store. Current writer/reader/projector coherence passes. Authentication, authorization, generation ownership, invocation limits, mutation isolation and source ownership remain explicit boundaries. Canonical content fidelity and the existing derived-journal redaction exception are preserved.

## Skill-Authoring Documentation Impact

- **Assessment:** Affected.
- **Rationale:** Binding syntax, projected argument contracts, conditional unique producer dependencies, exact receiving behavior and trace provenance change author-facing semantics.
- **Documents reviewed:** repository `README.md`; repository-local `agent-skills/loomspan-docs/SKILL.md`; authoring `README.md`, `source-verification.md`, `input-bindings.md`, and changed guidance in `input-contracts.md`, `input-contract-design.md`, `planning-task-constraints.md`, `planning-concurrency.md`, `output-contracts.md`, `traces-and-debugging.md`.
- **Evidence checked:** Production paths and named focused tests above, full guide YAML generation validation, actual local public invocations and canonical trace roundtrips. The bundled skill and checkout both identify `1.0.0-beta.8-SNAPSHOT`; it was used as a router after establishing executable behavior.
- **Drift classification:** aligned. Object-only pointers/root selection, exact source ownership, strict bound validation, bottom-up requiredness, reserved-path exclusions, one-total planning correction and unchanged String forwarding match executable behavior. No live-model reliability claim is made.
- **Coverage table:** Current; new topic routing and relevant input/planning/evidence entries describe implemented limits.
- **LLM-first usability:** Pass; focused applicability, exact syntax, complete schemas, normative enforcement, explicit limitations and adjacent-topic routing are present.

## Residual Risks and Optional Developer Checks

The two optional `Pr18RecordedHandoffDiagnosticTest` methods remain skipped because `pr18.diagnostic` was not enabled. They are not required binding verification. No required offline check remains unrun. Live model reliability and business-reasoning quality remain outside the feature's claims and this authorization. Optional developer checks: none.

## Disposition

**Approve.** Full independent review found no actionable issue, made no implementation changes and completed sufficient verification. `REVIEW_RESULT: clean`.
