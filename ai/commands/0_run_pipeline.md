---
description: Triage a ticket into full, fast-track, or direct execution and run the selected process
---

# Run Pipeline

You are the pipeline orchestrator. Given a ticket, first select a proportionate
execution profile, then run that route through completion. The developer invokes
this command once; you manage the selected steps, pass artifact paths forward,
and involve the developer only at the profile gate or when a step genuinely
needs a decision it cannot make safely from the ticket, repository, and prior
artifacts.

Read `ai/commands/shared/automation-protocol.md` first. It defines pipeline mode, escalation behavior, and the Step Report you will use to route each result.

## Input

- **Required:** a ticket path, normally under `ai/thoughts/tickets/`.
- **Optional:** an explicit profile: `auto`, `full`, `fast-track`, or `direct`.
  Default to `auto`.
- The ticket may contain an optional `Pipeline notes` section that gives advance notice about intentional changes likely to look suspicious, such as a deliberate `consoleCompatibilityVersion` break.

If no ticket path is provided, ask for one.

## Step 0: Bounded Profile Triage

Before launching a numbered step, read the ticket completely and perform a
small, read-only current-checkout scan. This is triage, not Step 1 research:

1. Read repository instructions and applicable project policies, plus the
   ticket's advisory `Execution profile` when present. A legacy ticket without
   that section is valid; report its recommendation as absent.
2. Inspect Git status and diff summaries, including staged and untracked work
   and any supplied branch comparison. Distinguish ticket work from unrelated
   developer changes; do not assume every dirty file belongs to the ticket.
3. Beyond the ticket, required policies, and Git summaries, use at most two
   locator searches and five targeted source, test, configuration, or
   documentation reads. Read only the relevant regions of large files.
   Stop earlier when eligibility is established. If that budget cannot resolve
   material uncertainty, recommend full; do not extend triage into another
   investigation pass. For an explicit full request, honor it after reading
   the ticket, instructions, and Git summaries; do not research merely to
   justify that choice.
4. Report the recommended profile, confidence, concise current-checkout
   evidence, expected stages/artifacts and expensive verification, and the
   conditions that would upgrade the route. Mark verification details that
   need later investigation as unknown rather than researching them now.

Step 0 makes no file edits, launches no research subagents, runs no tests, and
does not trace the whole system or resolve implementation details.

Apply the eligibility table in `ai/commands/shared/automation-protocol.md` to the remaining
implementation and all verification/review still required for the complete
ticket-scoped change, including work already present in committed, staged,
unstaged, or untracked changes. A trivial final edit does not make a substantial
unreviewed ticket diff eligible for direct. Exclude established baseline
behavior outside this ticket and unrelated developer changes. Risky ticket
vocabulary alone is not a trigger.

In `auto`, present the recommendation and wait for the developer to approve or
choose another profile before launching work. An explicitly supplied `full`,
`fast-track`, or `direct` is already the developer's choice: proceed without another confirmation when
it meets or exceeds the rigor triage requires. This also applies to the
developer's answer at the auto gate; do not ask again without new contradictory
evidence. If triage indicates that an explicit `fast-track` or
`direct` profile is unsafe, pause with the evidence and recommend the least
expensive safe upgrade. Always honor an explicit `full` profile even when a
lighter route appears sufficient.

An unsafe requested profile is not selected for execution. If the developer
declines the necessary upgrade, stop unless revised scope or new evidence
removes the trigger. Do not repeatedly ask the same question or treat acceptance
of risk as a waiver of required rigor.

Later discoveries, including those during review fixes, follow the shared
reassessment rule and the profile-upgrade flow below.

## Execution Routes

| Profile | Route | Durable process artifacts |
| --- | --- | --- |
| Full | Steps 1, 2+3, 4, and 5 | Research, implementation plan, testing plan, implementation, and review |
| Fast track | Step 4 in ticket-led fast-track mode, then Step 5 | Implementation and independent review; no research or plan artifact required |
| Direct | Step 4 in ticket-led direct mode | Implementation only; no research, plan, testing-plan, or independent-review artifact required |

Step 4 performs targeted reconnaissance and creates an internal working and
verification checklist when invoked without plans. Step 5 accepts the ticket
and current diff without nonexistent research or plan artifacts. Full-profile
behavior remains the five-step process below.

## Orchestrator Role

You are a router, not the technical decision-maker for each step:

- launch each selected stage in a fresh subagent context, with full-profile steps 2 and 3 sharing one planning context;
- pass the ticket and prior artifact paths forward;
- validate that reported artifacts exist and contain substantive content;
- let step agents make ordinary evidence-backed technical decisions;
- pause at the Step 0 profile gate or when a Step Report says `STATUS: needs-developer`;
- never answer a developer escalation yourself; and
- never treat `Pipeline notes` as permission to ignore correctness, tests, security, or effects broader than the note describes.

## The Five Steps

For the full profile, run these sequentially:

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
3. the statement that it is running in **pipeline mode** as step N of
   `0_run_pipeline.md`, and the selected execution profile;
4. the exact ticket and every prior-artifact path that exists; never invent or
   require a skipped route's artifacts;
5. any developer answer that resolved an earlier escalation, also persisted in
   the governing artifact under the shared handoff rules; and
6. a reminder to finish with the standard Step Report.

For step 2, instruct the subagent to continue directly into `3_testing_plan.md` after writing the implementation plan and to return one combined Step Report listing both artifacts. Do not launch a separate step 3 subagent.

For fast-track or direct Step 4, state the selected profile and instruct the
agent to use the ticket as the controlling artifact, perform only the targeted
reconnaissance needed to implement safely, and maintain an internal checklist
instead of creating research or plan artifacts. For fast track, launch Step 5
fresh with the ticket and current repository state after Step 4 completes. For
direct, finish after validating Step 4's report and verification.

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

For an ordinary technical escalation:

1. Present its question, evidence, and recommended answer concisely.
2. Wait for the developer's answer.
3. Persist the answer in the governing artifact. Resume the same subagent when
   supported; otherwise relaunch that step with the answer, selected profile,
   and relevant artifact paths.
4. Continue only after the step returns `STATUS: complete`.

Typical reasons to involve the developer are:

- the ticket permits materially different observable outcomes and repository evidence cannot select one;
- implementation would need to change the ticket's intended outcome or materially expand its scope;
- a compatibility break appears intentional but is not clearly authorized by the ticket or `Pipeline notes`;
- a required external decision or unavailable verification materially affects confidence; or
- the review/fix context cannot safely resolve an actionable finding.

### Profile upgrades

A profile mismatch changes the route; do not use the ordinary resume flow.
Present the evidence and proposed profile, then wait for the developer's choice.
After an accepted upgrade, record the new selected profile and decision in the
governing artifact and replace the remaining route:

- **Direct → fast-track:** continue or relaunch Step 4 with the updated profile
  and ticket, then run Step 5. Previously completed work remains subject to
  verification and independent review.
- **Any light profile → full:** stop the light route and launch fresh Steps 1,
  2+3, 4, and 5 against the preserved current checkout. Research and planning
  must account for existing ticket changes and remaining work. Do not reset
  the checkout, blindly repeat completed edits, or resume Step 4 without the
  newly required plans.

An accepted upgrade needs no further profile confirmation. Preserve all
material decisions, developer answers, scope attribution, and existing
artifacts. Keep review numbers increasing if review already began; do not pass
prior review documents to the new stages. If the developer declines the upgrade,
stop unless revised scope or new evidence removes the trigger. Never loop on an
unchanged recommendation or silently continue under reduced rigor.

## Review and Fix Loop

Step 5 owns an internal review/fix loop. Each cycle begins in a fresh context and returns one of two successful results:

- `REVIEW_RESULT: clean` — the context completed a full review, found no actionable issues, made no implementation-artifact changes, and completed sufficient verification. Its required review document does not count as an implementation-artifact change. The pipeline is complete.
- `REVIEW_RESULT: fixes-applied` — the context found actionable issues, fixed them, and reviewed again until it believed the work was clean. Because it changed the work, it cannot certify its own fixes. Launch step 5 again in a new fresh context.

For every cycle:

1. Launch `5_code_review.md` with the selected profile, ticket, every research or plan artifact
   produced by the selected route, and the review number to use in its output
   filename (`1` for the first review, `2` for the next fresh review, and so
   on). Do not require skipped artifacts and do not pass prior review documents;
   each context reviews the current repository state independently.
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
PROFILE: full | fast-track | direct | unselected
TRIAGE: <ticket recommendation or absent; requested profile; selected profile or pending; confidence and rationale>
LAST STAGE: <last completed or interrupted stage, including Step 0>
ARTIFACTS:
  - <artifacts by step>
REVIEW: <disposition, review count, and residual risks; or not run with reason>
DEVELOPER DECISIONS: <decisions supplied during the run, or none>
VERIFICATION: <actual results, identifying the producing step>
OPTIONAL DEVELOPER CHECKS: <preserved applicable observations, or none>
```

For completed full and fast-track runs, completion evidence comes from the
final fresh Step 5 context returning `clean`. For completed direct runs, it
comes from Step 4; report **No independent review; reduced assurance**, with
review count zero. Validate that Step 4 maps every acceptance criterion to
sufficient verification and has no unresolved required check before completing.
Carry applicable optional observations forward from the governing artifacts
and reports; do not lose them because a route skipped Step 5.

For stopped runs, report only evidence actually obtained and identify the last
completed or interrupted stage. Use `unselected` before a safe profile has been
chosen. While an upgrade is pending, keep the current selected profile and
identify the proposed upgrade as pending in TRIAGE; never report it as accepted.

## Hard Rules

- Do not invent answers to escalations.
- Do not skip Step 0, silently downgrade an explicit profile, or let a ticket's
  advisory profile override contradictory current-checkout evidence.
- A profile changes process cost, not scope or quality obligations. It never
  waives ticket requirements, correctness, required verification, or an
  explicitly required independent review.
- Do not let chat summaries substitute for artifacts.
- Do not let an intentional exception expand beyond what the ticket or its `Pipeline notes` clearly authorizes.
- For full and fast-track profiles, do not mark the pipeline complete unless a
  fresh step-5 context returns `REVIEW_RESULT: clean` with no actionable findings
  of any priority and sufficient verification. Direct completion rests on Step
  4 verification and must be reported explicitly as having no independent review.
- Prefer executable verification. Optional developer checks are reported at completion but do not prevent `REVIEW_RESULT: clean` or pipeline completion.
- A stopped pipeline is a valid outcome; report exactly what decision or failure stopped it.
