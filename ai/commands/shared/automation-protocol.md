# Automation Protocol

Shared protocol for the five commands in `ai/commands/`. It lets each command work either directly with a developer or as one step inside `0_run_pipeline.md`.

## Execution Modes

- **Standalone mode:** A developer invoked the command directly. Follow the command's normal interactive process.
- **Pipeline mode:** The orchestrator invoked the command, or continued into it from step 2 to step 3, and explicitly identified the pipeline step.

If pipeline mode was not stated explicitly, assume standalone mode.

## Pipeline-Mode Judgment

In pipeline mode there is no developer inside the step's context. Use repository evidence and prior artifacts to make ordinary technical decisions autonomously.

Ask for the developer only when a decision materially affects observable behavior, compatibility, security, correctness, or scope and cannot be resolved confidently from the ticket, repository, or prior artifacts. Do not escalate naming choices, routine implementation details, or a clearly best-supported option.

When escalation is necessary:

1. finish any independent work that remains safe;
2. set `STATUS: needs-developer`;
3. state one focused question;
4. include the relevant evidence; and
5. recommend an answer with rationale.

When the orchestrator returns the developer's answer, treat it as authoritative and continue.

## Pipeline Notes

A ticket may contain an optional `Pipeline notes` section. It gives advance notice about intentional changes that might otherwise cause a step to stop, such as a deliberate compatibility break.

- If a concern is clearly and narrowly covered by a note, proceed and record the decision in the step artifact or report.
- If the actual impact is broader or materially different, ask the developer.
- A note never waives correctness, security, adequate tests, or coherent changes within the scope it describes.

## Artifacts Carry Context

Anything a later step needs must be written into a durable artifact: research documents, plans, testing plans, or the implementation itself. Material decisions may be echoed in `DECISIONS`, but must also appear in the artifact that governs later work. Review documents are developer-facing audit records; fresh review cycles inspect the current repository state rather than consuming prior reviews. Chat output is a short receipt, not a second channel containing unique technical information.

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
- List only commands actually run under `VERIFICATION`; never turn an unrun command into a pass.
- `OPTIONAL_DEVELOPER_CHECKS` carries useful non-automatable observations forward to the final pipeline report. These checks are not completion gates.
- Step 5 reports `clean` only when its context completed a full review, made no implementation-artifact changes, found no actionable issues of any priority, and completed sufficient verification. Writing its required review document does not count as an implementation-artifact change.
- Step 5 reports `fixes-applied` whenever its context changed code, tests, documentation, configuration, fixtures, plans, or other implementation artifacts, after its final internal re-review finds no actionable issues and verification is sufficient. Writing only the cycle's review document does not count as a fix. A fresh step-5 context must review implementation fixes.
- Keep the report concise and machine-readable.
