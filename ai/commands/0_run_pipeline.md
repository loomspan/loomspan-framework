---
description: Run the five-step development process end-to-end from a ticket
---

# Run Pipeline

You are the pipeline orchestrator. Given a ticket, run the five commands in `ai/commands/` from research through independent review. The developer invokes this command once; you manage the steps, pass artifact paths forward, and involve the developer only when a step genuinely needs a decision it cannot make safely from the ticket, repository, and prior artifacts.

Read `ai/commands/shared/automation-protocol.md` first. It defines pipeline mode, escalation behavior, and the Step Report you will use to route each result.

## Input

- **Required:** a ticket path, normally under `ai/thoughts/tickets/`.
- The ticket may contain an optional `Pipeline notes` section that gives advance notice about intentional changes likely to look suspicious, such as a deliberate `consoleCompatibilityVersion` break.

If no ticket path is provided, ask for one.

## Orchestrator Role

You are a router, not the technical decision-maker for each step:

- launch each pipeline stage in a fresh subagent context, with steps 2 and 3 sharing one planning context;
- pass the ticket and prior artifact paths forward;
- validate that reported artifacts exist and contain substantive content;
- let step agents make ordinary evidence-backed technical decisions;
- pause only when a Step Report says `STATUS: needs-developer`;
- never answer a developer escalation yourself; and
- never treat `Pipeline notes` as permission to ignore correctness, tests, security, or effects broader than the note describes.

## The Five Steps

Run these sequentially:

| Step | Command | Inputs | Expected result |
| --- | --- | --- | --- |
| 1 | `1_research_codebase.md` | ticket | research document |
| 2 | `2_create_plan.md` | ticket + research document | implementation plan |
| 3 | `3_testing_plan.md` | continue in the step 2 context | testing plan |
| 4 | `4_implement_plan.md` | ticket + implementation plan + testing plan | implementation and verification results |
| 5 | `5_code_review.md` | ticket + implementation plan + testing plan | independent review document |

Steps 2 and 3 deliberately share one context because the testing plan develops the risks and decisions established during implementation planning. All other steps start in fresh contexts. Artifact files, not chat summaries, carry knowledge across those fresh-context boundaries, and step 5 must always be independent from implementation.

## Launching a Step

Give each newly launched subagent a self-contained prompt containing:

1. the command file it must read and follow;
2. `ai/commands/shared/automation-protocol.md`;
3. the statement that it is running in **pipeline mode** as step N of `0_run_pipeline.md`;
4. the exact ticket and prior-artifact paths;
5. any developer answer that resolved an earlier escalation; and
6. a reminder to finish with the standard Step Report.

For step 2, instruct the subagent to continue directly into `3_testing_plan.md` after writing the implementation plan and to return one combined Step Report listing both artifacts. Do not launch a separate step 3 subagent.

## Handling a Step Report

After each step returns:

1. Parse its Step Report. If it is missing or malformed, ask that subagent once to provide a valid report. Continued failure becomes `STATUS: failed`.
2. Verify that each listed artifact exists and is non-trivial. Do not re-review its technical content.
3. Route by status:
   - `complete` — continue;
   - `needs-developer` — use the escalation flow below;
   - `failed` — stop and report the failure unless the report identifies a safe, obvious retry.
4. For step 5, route a complete report by `REVIEW_RESULT` as described in the review loop below.
5. Do not continue past unexplained failed verification that materially affects the next step.

## Escalation Flow

When a step needs the developer:

1. Present its question, evidence, and recommended answer concisely.
2. Wait for the developer's answer.
3. Resume the same subagent when supported; otherwise relaunch that step in a fresh context with the developer's answer and all relevant artifact paths.
4. Continue only after the step returns `STATUS: complete`.

Typical reasons to involve the developer are:

- the ticket permits materially different observable outcomes and repository evidence cannot select one;
- implementation would need to change the ticket's intended outcome or materially expand its scope;
- a compatibility break appears intentional but is not clearly authorized by the ticket or `Pipeline notes`;
- a required external decision or unavailable verification materially affects confidence; or
- the review/fix context cannot safely resolve an actionable finding.

## Review and Fix Loop

Step 5 owns an internal review/fix loop. Each cycle begins in a fresh context and returns one of two successful results:

- `REVIEW_RESULT: clean` — the context completed a full review, found no actionable issues, made no implementation-artifact changes, and completed sufficient verification. Its required review document does not count as an implementation-artifact change. The pipeline is complete.
- `REVIEW_RESULT: fixes-applied` — the context found actionable issues, fixed them, and reviewed again until it believed the work was clean. Because it changed the work, it cannot certify its own fixes. Launch step 5 again in a new fresh context.

For every cycle:

1. Launch `5_code_review.md` with the ticket, implementation plan, testing plan, and the review number to use in its output filename (`1` for the first review, `2` for the next fresh review, and so on). Do not pass prior review documents; each context reviews the current repository state independently.
2. The context performs a complete review before editing, then fixes and re-reviews internally until it finds no remaining actionable issues or needs the developer.
3. Validate its Step Report and review artifact.
4. Route the result:
   - `STATUS: needs-developer` — pause and use the escalation flow;
   - `STATUS: failed` — stop and report the failure;
   - `STATUS: complete` with `REVIEW_RESULT: fixes-applied` — launch another fresh step-5 context;
   - `STATUS: complete` with `REVIEW_RESULT: clean` — finish the pipeline.

There is no separate fix subagent and no arbitrary cycle cap. Continue until a fresh context returns `clean`, or until a context returns `needs-developer` or `failed`.

Only the verification performed by the final context returning `clean` establishes that the pipeline is complete.

## Final Report

When the pipeline completes or stops, report:

```markdown
## Pipeline Report: <ticket>
OUTCOME: complete | needs developer | failed at step <N>
ARTIFACTS:
  - <artifacts by step>
REVIEW: <disposition, review count, and residual risks>
DEVELOPER DECISIONS: <decisions supplied during the run, or none>
VERIFICATION: <results from the final review>
OPTIONAL DEVELOPER CHECKS: <items from the final step-5 report, or none>
```

## Hard Rules

- Do not invent answers to escalations.
- Do not let chat summaries substitute for artifacts.
- Do not let an intentional exception expand beyond what the ticket or its `Pipeline notes` clearly authorizes.
- Do not mark the pipeline complete unless a fresh step-5 context returns `REVIEW_RESULT: clean` with no actionable findings of any priority and sufficient verification.
- Prefer executable verification. Optional developer checks are reported at completion but do not prevent `REVIEW_RESULT: clean` or pipeline completion.
- A stopped pipeline is a valid outcome; report exactly what decision or failure stopped it.
