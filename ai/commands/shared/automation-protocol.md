# Automation Protocol

Shared protocol for the five commands in `ai/commands/`. It lets each command work either directly with a developer or as one step inside `0_run_pipeline.md`.

## Execution Modes

- **Standalone mode:** A developer invoked the command directly. Follow the command's normal interactive process.
- **Pipeline mode:** The orchestrator invoked the command, or continued into it from step 2 to step 3, and explicitly identified the pipeline step.

If pipeline mode was not stated explicitly, assume standalone mode.

## Execution Profiles

Profiles select pipeline stages; they do not change standalone/pipeline mode.
Direct is still pipeline mode. The orchestrator supplies the selected profile
to every stage. Ticket recommendations, saved execution notes, and absent
artifacts are not authorization to select or change a profile.

Use this single eligibility table for advisory ticket recommendations, Step 0
triage, and later reassessment. Only `0_run_pipeline.md` selects the run's route.

| Profile | Eligibility |
| --- | --- |
| Full | Material discovery or design remains; materially different approaches remain viable; or the ticket-scoped change awaiting assurance changes supported API or extension contracts, security or authorization boundaries, lifecycle or concurrency behavior, persisted/serialized contracts, external protocols, migrations, or broad/cross-cutting production behavior. |
| Fast-track | No full trigger applies; intent and implementation direction are settled; the affected area is bounded; targeted reconnaissance, implementation, required verification, and independent review provide sufficient assurance. |
| Direct | No full trigger applies; the entire ticket-scoped change awaiting assurance is localized, mechanically clear, and low risk; narrow verification is sufficient; there are no material compatibility, security, lifecycle, data, deployment, or cross-component implications; and neither the ticket nor repository instructions require independent review. |

Recommend full when a full trigger applies or material uncertainty prevents
establishing eligibility. Otherwise recommend direct only when every direct
condition holds; use fast-track when its conditions hold. Confidence explains
the evidence; it does not override these conditions or developer choice.
Documenting or testing existing behavior does not by itself change its contract
or trigger full. Assess the actual ticket diff and outstanding assurance, not
the vocabulary or subsystem it mentions.

"Mechanically clear" means the intended edit follows directly from settled
requirements and local evidence, without an unresolved behavioral or design
choice. "Material" means affecting observable behavior, scope, a protected
boundary, or required verification/review. A bounded affected area can be
identified without unresolved cross-component dependencies. "Safe" means the
route satisfies these conditions and all ticket and repository obligations,
not merely that a model believes it can write the code.

### Reassessment during execution

In pipeline mode, reassess eligibility as new evidence appears, including
during Step 5 review and before applying review fixes. A light-profile mismatch
requires escalation even when the technical solution is clear. Stop affected
implementation, preserve completed work, and return `STATUS: needs-developer`
with the evidence and a recommended profile: full for any full trigger or
unresolved material uncertainty; otherwise fast-track when direct no longer
qualifies. Continue only independent work whose safety does not depend on the
profile decision. Do not finish the affected implementation under reduced
rigor while waiting.

The orchestrator owns the route change. Do not launch skipped stages yourself
or continue merely because a developer answer was forwarded; wait for the
orchestrator's updated invocation. A profile never waives scope, correctness,
security, compatibility analysis, documentation, required verification, or an
explicitly required review.

## Pipeline-Mode Judgment

In pipeline mode there is no developer inside the step's context. Use repository evidence and prior artifacts to make ordinary technical decisions autonomously. The mandatory profile-reassessment rule above is an exception to the ordinary technical-escalation threshold below.

Ask for the developer only when a decision materially affects observable behavior, compatibility, security, correctness, or scope and cannot be resolved confidently from the ticket, repository, or prior artifacts. Do not escalate naming choices, routine implementation details, or a clearly best-supported option.

When escalation is necessary:

1. finish any independent work that remains safe;
2. set `STATUS: needs-developer`;
3. state one focused question;
4. include the relevant evidence; and
5. recommend an answer with rationale.

When the orchestrator returns the developer's answer to an ordinary technical
escalation, preserve it in the governing artifact and continue. Profile
upgrades follow the orchestrator's route-change flow instead.

## Pipeline Notes

A ticket may contain an optional `Pipeline notes` section. It gives advance notice about intentional changes that might otherwise cause a step to stop, such as a deliberate compatibility break.

- If a concern is clearly and narrowly covered by a note, proceed and record the decision in the step artifact or report.
- If the actual impact is broader or materially different, ask the developer.
- A note never waives correctness, security, adequate tests, or coherent changes within the scope it describes.

## Artifacts Carry Context

Anything a later step needs must be written into a durable artifact: the ticket,
research, plans, testing plans, or the implementation itself. Material decisions
may be echoed in `DECISIONS`, but must also appear in the governing artifact.
When plans are intentionally absent, use a concise `Execution notes` section in
the existing ticket for material decisions, resolved developer answers,
ticket-scope attribution (including unrelated dirty changes to preserve), and
observations needed by later contexts. Keep transient checklists internal;
do not create substitute research or plan artifacts.

Persist this context before a handoff, escalation, or relaunch. The orchestrator
records newly received answers and accepted profile changes before launching
the next context. Do this during authorized execution, never during read-only
Step 0. Preserve the advisory `Execution profile` separately; historical choices
in execution notes do not authorize a new run.

Review documents are audit records, not handoff artifacts. Do not copy prior
review findings, conclusions, or claimed verification into execution notes.
Fresh reviewers read requirements and material decisions but independently
reconstruct and verify the current change. Do not rewrite unchanged notes or
add routine review receipts to the ticket; any necessary ticket or plan edit
by Step 5 still counts as an implementation-artifact change. Chat output and
Step Reports are receipts, not a second channel of unique technical context.

## Step Report

Every numbered pipeline step (`1` through `5`) ends its final message with this report in both modes:

```markdown
## Step Report: <command name>
STATUS: complete | needs-developer | failed
ARTIFACTS:
  - <path written or updated> # or: none
SUMMARY: <at most three concise sentences>
DECISIONS:
  - <material autonomous decision and rationale> # or: none
DEVELOPER QUESTION: none
EVIDENCE: none
RECOMMENDATION: none
VERIFICATION: # steps 4 and 5 only; otherwise omit
  - PASS — `<exact command>`
  - FAIL — `<exact command>`: <brief reason>
  - NOT RUN — `<command>`: <reason>
OPTIONAL_DEVELOPER_CHECKS: # steps 4 and 5 only; otherwise omit
  - <non-automatable observation, or: none>
REVIEW_RESULT: clean | fixes-applied # completed step 5 only; otherwise omit
NEXT: <single recommended next action>
```

In pipeline mode, steps 2 and 3 return one report named `2_create_plan + 3_testing_plan` that lists both artifacts.

For `STATUS: needs-developer`, replace the three `none` values with one focused question, the evidence that makes it necessary, and the recommended answer. For `STATUS: failed`, explain the failure in `SUMMARY` and the safest next action in `NEXT`.

Rules:

- `complete` means the requested artifact or implementation is complete and no developer decision remains.
- `needs-developer` means work cannot safely continue without an answer.
- `failed` means the step could not produce its required result because of a tool, environment, or unrecoverable execution failure.
- Use PASS/FAIL only for commands actually run. Label unrun checks NOT RUN with
  their reason and residual risk; never turn an unrun check into a pass.
- `OPTIONAL_DEVELOPER_CHECKS` carries useful non-automatable observations forward to the final pipeline report. These checks are not completion gates.
- Step 5 reports `clean` only when its context completed a full review, made no implementation-artifact changes, found no actionable issues of any priority, and completed sufficient verification. Writing its required review document does not count as an implementation-artifact change.
- Step 5 reports `fixes-applied` whenever its context changed code, tests, documentation, configuration, fixtures, plans, or other implementation artifacts, after its final internal re-review finds no actionable issues and verification is sufficient. Writing only the cycle's review document does not count as a fix. A fresh step-5 context must review implementation fixes.
- Keep the report concise and machine-readable.
