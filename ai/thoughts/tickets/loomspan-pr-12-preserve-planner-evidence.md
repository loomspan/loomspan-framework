# PR 12 — Preserve complete evidence for dependent planner steps and final synthesis

## Outcome

Make complete successful child results available to subsequent dependent planner steps and native final synthesis, including results returned by nested skills. Applications must be able to gather evidence through supported skills and use it downstream without silently losing facts, authoritative quotes, or citations to Framework prompt previews.

## Requirements

- Preserve complete successful direct-task results across subsequent execution units and provide them to dependent assigned-step model requests and native final-synthesis requests. Earlier siblings must remain available alongside the latest result, including after more than five tasks have completed.
- Preserve result identity by task and skill so repeated calls to the same skill cannot overwrite or ambiguously identify one another. Nested completion must preserve the complete child's returned result at the parent boundary; this does not require exposing a nested mission's private intermediate results to its parent.
- Keep compact progress summaries and diagnostic previews separate from authoritative evidence. Neither character truncation nor summary eviction may silently determine which evidence downstream models receive. Preserve existing explicit execution-limit failures; do not introduce a lossy fallback that reports successful evidence delivery.
- Preserve accepted task ordering, dependency enforcement, concurrent group join semantics, branch and mission isolation, authorization, captured generations, and cancellation/late-write fencing. A result must not become visible to an unrelated mission or a sibling before its permitted join boundary.
- Support the existing public `SkillTemplate`, Java `@SkillMethod`, REST leaf, and model-backed YAML contracts. Do not require applications to replace internal beans, infer private reference paths, or adopt a new SPI. Preserve existing application-facing Java signatures and configuration contracts.
- Document the resulting evidence-flow behavior and distinguish it from output-schema validation, successful-child evidence annotations, and reference resolution. Neither schema-valid output nor successful execution alone proves factual correctness or quote fidelity.

## Acceptance criteria

- [ ] A deterministic local protocol-compatible endpoint captures actual outbound requests showing distinct facts beyond the former 100- and 1,000-character boundaries from multiple completed siblings in the dependent assigned-step request and the resulting nested skill input.
- [ ] Native final-synthesis requests contain the complete nested result, including late authoritative quote/citation fields; completion is demonstrated through the public facade with no live provider or credentials.
- [ ] Evidence from earlier tasks survives more than five completions, and repeated skill invocations remain distinguishable by task identity.
- [ ] The same evidence guarantees hold with concurrency enabled and disabled and with sibling completion order reversed. Nested planner-to-planner execution preserves complete returned results without leaking private intermediate or unrelated mission data.
- [ ] Short-result controls, authorization, generation capture, failure handling, and cancellation/isolation checks continue to pass. Failed or unfinished work is not represented as successful evidence.
- [ ] Completeness assertions inspect actual model requests and retain their full requirements: no missing facts injected into mock replies, no downstream answers preloaded into root input, no internal replacements, and no weakened application assertions.
- [ ] Supported-surface architecture checks pass, documentation explains the behavior and reference limitations, and the change requires no new application API, SPI, or configuration migration.
- [ ] Production release versions and the neighboring acceptance-suite repository remain unchanged by this work.

## Context

The investigation independently reproduced the limitation using released Framework `1.0.0-beta.6`, tag commit `50fa1a7dcf1974a3e73ebde2a41bd7ee49a28542`, and HEAD `b920e34d8772b13b327f017c0c37bd3198313e2a`. Java production sources were identical between those revisions.

`StepLoopMissionExecutionEngine` reduces completed results to 100-character summaries and retains a complete latest result. `MissionContext` retains only five summaries. `StepPromptBuilder` clips that latest result to 1,000 characters for assigned steps and final synthesis. Nested inputs consist of model-generated arguments; dependencies do not automatically bind complete result objects into those arguments. Planner requests have no result-retrieval tool, and final synthesis prohibits tool calls.

In the supplied live reproduction, Java's service-history result was only 785 characters, but the required `WO-0820` fact began at offset 109 and disappeared through the earlier-sibling summary limit. Both integration paths omitted it from assessment requests. Java later returned schema-valid empty quote/citation arrays and Framework success, while application validation rejected the assessment. Sidecar's terminal failure was a separate provider transport interruption; do not attribute that failure to evidence truncation or include its repair in this ticket.

Complete results remain available through direct public invocation, completed-execution observations, and ordinary non-planning tool exchanges. Those mechanisms do not repair evidence delivery inside the existing planner. Generated `{"$ref":"task.result"}` objects are ordinary data, and internal `ref://` file resolution is not an automatic task-result store. Turning off planning or moving orchestration into application code would change the workflow rather than resolve this limitation.

Existing investigation artifacts, available in this checkout:

- [Detailed report and source anchors](../../../investigations/evidence-flow/report.txt)
- [Public-facade reproduction and completeness regression](../../../src/test/java/ai/loomspan/integration/PlannerEvidenceFlowIntegrationTest.java)
- [Released-artifact harness](../../../investigations/evidence-flow/pom.xml) and [captured beta.6 requests](../../../investigations/evidence-flow/captures/beta6/parallel-long-requests.ndjson)

Four characterization/control cases passed on each revision; the opt-in completeness regression failed at seven assertions on both. Eight public-surface architecture tests passed. The green characterizations describe the defect, not the desired behavior; retain their controls while promoting complete-evidence assertions into ordinary regression coverage. These artifacts were authored during investigation and may still be untracked; preserve their useful evidence when preparing the implementation change.

The original reproduction is in `C:/opendev/code/loomspan-sidecar-test-suite`, particularly `docs/implementation-status.md`, `evidence/live-20261001-005945/`, and `config/skills/`. Both indexed Framework NDJSON hashes were verified. Keep that repository unchanged; reproducing live-provider behavior is not required to complete this ticket.

A mission-owned collection of complete results keyed by task ID, folded at existing join boundaries and rendered as data in dependent/final prompts, is a nonbinding implementation suggestion. The pipeline may refine the internal design. Merely raising the latest-result limit cannot satisfy the earlier-sibling and summary-eviction requirements. A new public reference/storage protocol, release publication, and guaranteed correctness of arbitrary model answers are outside scope.

The developer supplied PR number **12** for this ticket; GitHub PR metadata was not independently verified during ticket creation. Creating this ticket does not start the implementation pipeline or authorize a release.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** The required behavior is established by independent reproduction, but the internal retention and prompt-composition design must preserve nested mission boundaries, concurrent joins, and lifecycle fencing. Those cross-cutting correctness concerns warrant research, planning, and independent review.
- **Reassessment triggers:** Revalidate against the current checkout if evidence propagation has changed since the investigated HEAD. Any proposal requiring new public contracts, configuration migration, or a separate retrieval protocol exceeds this ticket's constraints and requires developer direction.
