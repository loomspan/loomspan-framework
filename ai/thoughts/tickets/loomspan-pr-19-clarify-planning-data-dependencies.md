# PR 19 — Clarify explicit data dependencies in generated plans

## Outcome

Help models declare the data dependencies of planned tasks even when execution
order already ensures the prerequisite results are available. Make the distinction
between execution ordering and explicit causal/dataflow requirements clear with
a small, general clarification in Framework-generated planning guidance.

## Requirements

- Convey this agreed guidance: "Declare dependencies on earlier tasks whose
  results this task directly requires, even when list order or a completed
  parallel group already guarantees those results are available." Equivalent
  wording is acceptable if it preserves this meaning.
- Keep the clarification consistent with existing execution-unit rules:
  consecutive members of a parallel group execute as one unit, the complete unit
  is joined before the next unit starts, and dependencies may reference only
  tasks in earlier execution units. Describe required data relationships without
  implying that `dependsOn` is the sole scheduling mechanism.
- Encourage dependencies on actual required results, not blanket dependencies
  on every preceding task or every member of an earlier parallel group. Do not
  require a redundant transitive closure of the dependency graph.
- Keep the guidance model-, provider-, and domain-independent. Do not add
  equipment-specific instructions, hardcoded skill names, a solved business
  plan, or a model-specific workaround to production prompts.
- Scope this change to planning guidance and its verification. Preserve runtime
  scheduling, validation rules, plan serialization, supported API and configuration
  contracts. Do not automatically repair plans, introduce new mandatory dependency
  validation, edit downstream business skills, or relax downstream acceptance
  criteria as part of this ticket.

## Acceptance criteria

- [ ] Actual generated planning prompts explain that direct result dependencies
  should be declared even when list/group order already guarantees availability.
- [ ] The resulting guidance remains coherent with earlier-unit-only dependency
  references, independent parallel members, and complete-unit join semantics.
- [ ] Verification covers a generic situation in which a later task requires
  one earlier result despite an existing ordering barrier; the guidance does not
  demand dependencies on unrelated earlier tasks or all transitive ancestors.
- [ ] Production guidance contains no domain-specific solution or model/provider
  special case. Runtime, validation, serialized-plan, public API and configuration
  behavior remain unchanged.
- [ ] Report prompt-contract verification separately from any observed model
  response improvement. A live model comparison is optional; neither a provider
  credential nor a successful stochastic response is required for completion.
  Do not claim demonstrated accuracy gains without comparative evidence.

## Context

A Java evaluation in the sibling `loomspan-sidecar-test-suite` used
`z-ai/glm-5.3-flash` with medium reasoning after Framework PR 18. The priority
scenario produced this root plan structure:

```text
assetContext
    -> [serviceHistory, referenceEvidence, serviceTerms] (one parallel group)
    -> assessEquipment
    -> planResolution
```

The final task declared `dependsOn: ["assess"]`, omitting the task ID `getTerms`
for commercial service terms, although its expected output mentioned those terms.
The authored skill said to plan resolution after assessment while supplying the
assessment, asset context, service terms and operating needs. Framework said
`dependsOn` expresses explicit causal or dataflow requirements, but did not
explicitly address dependencies already satisfied by execution-unit order.

The downstream reviewer failed an explicit-dependency assertion. Subsequent
inspection of the exact prompt and runtime corrected an initial interpretation:
this was not a demonstrated scheduling race. The preceding group must complete
before assessment and resolution, so commercial terms were already ordered before
both tasks and were passed downstream in the observed run. The missing edge is
an incomplete declaration of required data, not proof that the model lacked terms
or that the scheduler ran tasks prematurely. Model confusion and any benefit from
the proposed wording remain hypotheses, not established causation.

There is a separate authored-skill tension: it says commercial terms must not
delay technical assessment while placing terms in the preceding joined group.
Do not resolve that business-authoring issue or redesign scheduling in this ticket.
The accepted Framework improvement is the generic clarification above. Weak-model
evaluations are being used to reveal general guidance opportunities, not to tune
Framework to one model or make it supply the solution to a particular business task.

Local evidence in the sibling suite:

- `evidence/live-20261004-085834-2fc4d6/journal.json`, planning request ID
  `a53cf287bf224d19be01e3db523e2195`, includes the prompt and model plan.
- `evidence/priority-planning-prompt-review-20261004/exact-prompt.txt` contains
  the extracted prompt.
- `docs/implementation-status.md` records the corrected interpretation. The older
  `priority-planning-finding.json` in the snapshot follow-up evidence retains a
  superseded scheduling-risk interpretation and must not be treated as authoritative
  on runtime ordering.

Known source hints, to verify against the current checkout: generated planning
rules in `DefaultPlanningService`; execution-unit partitioning in `ExecutionUnit`
and sequential unit traversal/join in `StepLoopMissionExecutionEngine`.

The developer assigned 19 as the proposed PR identifier. This ticket does not
assert that a GitHub PR with that number already exists.

## Execution profile

- **Recommended:** Fast-Track 2-Step Pipeline — Implementation & Review
- **Confidence:** high
- **Rationale:** Intent is settled and the change is bounded to generated planning
  guidance. Targeted verification plus independent review can assess wording and
  semantic consistency; this is behavioral model guidance rather than a purely
  mechanical text correction. No scheduling or external contract change is intended.
- **Reassessment triggers:** Findings that require changes to scheduling,
  validation, plan contracts, public API, or broader production behavior require
  reconsidering scope and the Full 5-Step Pipeline. A materially different dependency
  interpretation must not silently replace the agreed clarification.
