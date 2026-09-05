---
description: Write a ticket that gives the five-step development pipeline the intent it cannot discover from code
---

# Writing a Loomspan Ticket

A ticket is the input to `0_run_pipeline.md`. It should explain the result the developer wants, not duplicate the research, implementation planning, test planning, implementation, or review performed by the five pipeline steps.

Keep tickets direct. The pipeline can discover code structure, existing tests, implementation patterns, affected files, and verification commands from the repository.

## Conversational Handoff

This command is normally invoked after the developer and AI have discussed a
feature and resolved its material design questions. Use the full conversation,
not merely the invocation message, as source material.

The ticket will be consumed by the pipeline in fresh contexts that do not have
access to this conversation. Make the ticket independently understandable:

- Carry forward the background needed to understand why the outcome matters.
- Record every agreed product decision, constraint, scope boundary,
  compatibility expectation, dependency, and sequencing requirement that a
  later step cannot safely reconstruct from the repository.
- Include a rejected alternative only when its rejection is a deliberate
  decision that a later step might otherwise reopen.
- Do not use phrases such as "as discussed," "the approach above," or other
  references to chat context. Put the actual information in the ticket.

Do not reopen decisions already settled in the conversation or require the
developer to repeat them. Repository evidence may refine nonbinding
implementation suggestions, but it must not silently override agreed product
intent.

Before writing, identify any unresolved question that could materially change:

- observable behavior or acceptance criteria;
- included or excluded scope;
- compatibility or migration expectations;
- security or correctness requirements;
- dependencies or required ordering; or
- externally controlled behavior the repository cannot determine.

If such a question remains, ask one focused question and do not create the
ticket yet. Do not invent product intent to fill the gap. Otherwise, create the
ticket immediately.

## Output and PR Number

Write the completed ticket to `ai/thoughts/tickets/`. Give every ticket a
proposed PR number and use it in both the filename and the title:

- Filename: `loomspan-pr-<number>-<short-kebab-case-slug>.md`
- Title: `# PR <number> — <Short outcome-oriented title>`

Choose the number before writing the ticket:

1. Inspect the filenames in `ai/thoughts/tickets/` for a `pr-<number>` segment
   (including older names such as `loomspan-console-pr-20-...`). If any match,
   use one greater than the highest local number.
2. If no local filename matches, run
   `gh pr list --state all --limit 10 --json number`, find the highest returned
   PR number, and use the next number. If the repository has no PRs, start at
   `1`.
3. Confirm that the resulting target filename does not already exist before
   writing it. If the GitHub CLI lookup is required but cannot be run or does
   not identify the repository, ask the developer instead of guessing.

The number is a proposed PR identifier used to order and correlate the ticket;
do not imply that a GitHub PR with that number already exists.

## What the Pipeline Needs

A useful ticket answers:

1. **What outcome is wanted?** Describe the observable result or problem being solved.
2. **What behavior matters?** State requirements the pipeline could not safely infer from code alone.
3. **How will we know it is done?** Give concise, observable acceptance criteria.
4. **Is anything intentionally surprising?** Optionally add `Pipeline notes` for a change likely to look accidental, such as an intentional compatibility break.

Context, links, source hints, design constraints, or test ideas are welcome when they carry useful intent, but they are not required sections. The pipeline verifies hints rather than treating them as authoritative.

## What the Pipeline Discovers

Do not make the ticket author reproduce work owned by later steps:

- Step 1 locates and documents the current implementation, consumers, tests, fixtures, and relevant history.
- Step 2 turns the ticket's requirements and deliberate implementation decisions into a detailed plan, choosing the implementation details the ticket leaves open and analyzing compatibility impact.
- Step 3 designs test coverage, selects test locations and fixtures, finds the correct commands, and defines exit criteria.
- Step 4 implements the plans and runs their verification.
- Step 5 independently reviews the result, fixes any actionable findings and re-reviews until it believes the work is clean, then repeats in a fresh context whenever fixes were applied. The final fresh review reruns appropriate verification and maps acceptance criteria to evidence.

Exact source anchors, test cases, and verification commands therefore belong in a ticket only when they express a real constraint or save important context—not as required ceremony.

## Pipeline Notes

`Pipeline notes` is the one optional pipeline override. Use it to give advance notice about an intentional change that research, planning, implementation, or review might otherwise treat as a reason to stop and ask.

Example:

```markdown
## Pipeline notes

- Breaking `consoleCompatibilityVersion` is expected for this work. Update all
  current Java and Go consumers together; compatibility with the previous
  version is not required.
```

Another example:

```markdown
## Pipeline notes

- Removing the old configuration key is intentional. Do not add an alias or
  fallback for it.
```

Notes are interpreted narrowly:

- A clearly covered concern does not require developer involvement.
- A broader or materially different impact still requires the pipeline to ask.
- Notes do not waive correctness, security, tests, or coherent updates within the described work.
- If there are no expected exceptions, omit the section.

## Timeless Ticket Example

The following is intentionally generic. Replace the placeholders with the actual desired behavior; do not copy the instructional text into a finished ticket.

````markdown
# PR <number> — <Short outcome-oriented title>

## Outcome

<Describe the observable result and why it is needed.>

## Requirements

- <Behavior the completed change must provide.>
- <Deliberate implementation decision or constraint the pipeline must preserve.>
- <Important edge case or constraint that cannot be inferred safely.>

## Acceptance criteria

- [ ] <Observable result that review can map to code or test evidence.>
- [ ] <Another observable result.>

## Context

<Background needed for a fresh context, plus any relevant links, dependencies,
sequencing, explicit scope exclusions, settled rejected alternatives, known
source areas, or nonbinding implementation suggestions. Omit this section only
when the outcome, requirements, and acceptance criteria are independently
sufficient.>

## Pipeline notes

- <Optional intentional exception likely to look accidental. Omit this section
  when there are no expected exceptions.>
````

## Writing Guidance

- Lead with the desired outcome, and include implementation details when they capture deliberate decisions or constraints agreed during ticket planning.
- Save the ticket under `ai/thoughts/tickets/` using the assigned PR number in both the filename and title.
- Make clear which implementation details are required and which are suggestions that step 2 may reconsider using repository evidence.
- Decide product or compatibility intent that repository evidence cannot decide for you.
- Avoid vague phrases such as "as appropriate," "where possible," or "handle gracefully" when different interpretations would change behavior.
- Keep acceptance criteria about results, not task lists or internal file changes.
- Use `Pipeline notes` only for genuine expected exceptions, not ordinary requirements.
- A pipeline-ready ticket has enough information for an agent to choose between materially different outcomes without guessing.

## Pipeline-Readiness Check

Before completing the command, verify that:

- The ticket is self-contained for a fresh context and contains no placeholders,
  unresolved questions, or references that depend on this conversation.
- Materially different observable outcomes cannot satisfy the ticket equally.
- Requirements, deliberate constraints, nonbinding suggestions, and scope
  exclusions are distinguishable.
- Every requirement is represented by at least one observable acceptance
  criterion.
- Known dependencies and sequencing constraints are recorded.
- Intentional compatibility breaks are explicitly authorized in `Pipeline notes`.
- No later step must recover unique intent or background from the chat.
- The ticket does not prescribe research, planning, tests, or source changes
  that the pipeline can determine itself.

If the check fails because material developer intent is missing, ask one
focused question and do not create or finalize the ticket until the answer is
resolved. If it passes, report the exact ticket path, proposed PR number, and
that the ticket is pipeline-ready. Create only the ticket; do not start
`0_run_pipeline.md` unless the developer explicitly asks.
