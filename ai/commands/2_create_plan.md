---
description: Create detailed implementation plans through interactive research and iteration
---

# Implementation Plan

You are tasked with creating detailed implementation plans through a skeptical, thorough, iterative process. In standalone mode this is collaborative and interactive with the developer; in pipeline mode, uncertainty is resolved with evidence or escalated per the automation protocol — never guessed away.

## Configuration (defaults you can adjust per repo)

- **Planning docs directory**: `ai/thoughts/plans/`
- **Research docs directory**: `ai/thoughts/research/`
- **Ticket/requirements directory** (if present): `ai/thoughts/tickets/`
- **Testing plan command**: `3_testing_plan.md` (creates a dedicated test plan artifact; in pipeline mode it runs in this same context immediately after the implementation plan)
- **Skill-authoring knowledge base**: `agent-skills/loomspan-docs/references/skill-authoring/` (must stay synchronized with author-facing framework behavior)

## Shared Protocols

- Follow `ai/commands/shared/automation-protocol.md` for execution modes, the escalation contract, and the Step Report you must end with.
- Follow `ai/commands/shared/loomspan-docs-protocol.md` for the evidence hierarchy, `loomspan-docs` skill usage, and drift classification.

## Initial Response

When this command is invoked:

1. **Check if parameters were provided**:
   - If a file path or ticket reference was provided as a parameter, immediately read the provided files completely and begin the research process

2. **If no parameters provided**, ask the user for: the task/ticket description (or ticket file), any relevant context or constraints, and links to related research or previous implementations. Then wait for input. (In pipeline mode, missing parameters are an escalation.)

## Process Steps

### Step 1: Context Gathering & Initial Analysis

1. **Read all mentioned files immediately and completely**:
   - Ticket/requirements files (e.g., `ai/thoughts/tickets/eng_XXXX.md`)
   - Research documents (e.g., `ai/thoughts/research/YYYY-MM-DD-ENG-XXXX-description.md`)
   - Related implementation plans
   - Any JSON/data files mentioned
   - Read these yourself in the main context before starting broader research steps
   - For Loomspan framework work, read `ai/thoughts/framework-feature-design-lens.md` completely before forming compatibility conclusions

2. **Read the ticket's optional `Pipeline notes`**:
   - Treat a note as advance context for the specific intentional exception it describes
   - If discovered impact is broader or materially different from the note, escalate in pipeline mode rather than stretching the note

3. **Respect the ticket's implementation intent**:
   - Treat implementation details stated as deliberate decisions, requirements, or constraints as part of the ticket's required outcome
   - Treat details clearly presented as suggestions or context as nonbinding input that may be reconsidered using repository evidence
   - If the distinction is material but unclear, resolve it from the ticket and surrounding context or escalate rather than silently reopening or hardening the decision

4. **Analyze and verify understanding**:
   - Cross-reference the ticket requirements with actual code, reading the files research identified as relevant
   - Identify any discrepancies or misunderstandings
   - Note assumptions that need verification
   - Determine true scope based on codebase reality
   - For framework work, inventory affected surfaces and evidence, then determine whether each is Application API, Supported SPI, Configuration and manifest behavior, Persisted or serialized behavior, Ephemeral diagnostics, or Internal or accidentally exposed implementation before deciding how compatibility should be handled
   - Compare any intentional-looking compatibility break with the ticket and `Pipeline notes`; if the developer's intent is not clear, escalate rather than planning the break silently

5. **Resolve remaining questions**:
   - Only surface questions that you genuinely cannot answer through code investigation, and always include your recommended answer with rationale
   - Standalone: present your informed understanding with `file:line` evidence and ask the developer
   - Pipeline: escalate per the automation protocol

### Step 2: Research & Discovery

After getting initial clarifications:

1. **If the user corrects any misunderstanding**:
   - DO NOT just accept the correction
   - Run new research steps to verify the correct information
   - Read the specific files/directories they mention
   - Only proceed once you've verified the facts yourself

2. **Create a research todo list** using whatever todo/task-tracking capability is available to you

3. **Run parallel research steps for comprehensive discovery (when supported)**:
   - Break the investigation into focused, independent tracks and run them concurrently when possible
   - Example tracks:
     - **Codebase locator**: find the primary entry points and file ownership for the feature
     - **Implementation tracer**: trace data flow and call paths across layers
     - **Pattern finder**: find similar existing features to model the approach after
     - **Tests/examples finder**: locate existing tests, fixtures, and usage examples
     - **Notes/history**: search `ai/thoughts/` for prior research, plans, or decisions
   - Always request/record concrete `file:line` references for findings

4. **Choose the design**:
   - Preserve deliberate implementation decisions and constraints from the ticket; choose among only the implementation details the ticket leaves open
   - Weigh the realistic design options against the codebase evidence and the design lens; for framework options, compare the protected consumers, intended breaks, atomic repository updates, public-surface delta, and shim/no-shim decision
   - A public modifier, interface, constructor, Spring bean, `@ConditionalOnMissingBean`, existing test, or previous implementation does not by itself establish a supported contract. Use the canonical policy in the design lens rather than inferring protection from technical exposure
   - Standalone: present the current state, design options with pros/cons, and open questions to the developer and get alignment before proceeding
   - Pipeline: choose the option best supported by evidence, write the material choice and rationale into the implementation plan, and echo it in `DECISIONS`; escalate only genuine coin-flips or decisions that materially change the ticket's outcome or scope

5. **Assess skill-authoring documentation impact**:
   - Determine whether the proposed work has skill-authoring impact per `ai/commands/shared/loomspan-docs-protocol.md`, then follow that protocol for the knowledge-base inventory and drift classification
   - Identify the focused tests, fixtures, samples, and production code that will support any new or changed guidance, and whether the README coverage table must change
   - Carry the result into the mandatory `Skill-Authoring Documentation Impact` section of the final plan. An omitted assessment is not equivalent to "No impact"

6. **Plan testing before implementation**:
   - Run (or recommend, standalone) `3_testing_plan.md` after the plan is written; in pipeline mode it runs immediately in this same context
   - The testing plan should outline impacted areas, a failing test (when applicable), and exit criteria

### Step 3: Plan Structure Development

Settle the phase structure (names, ordering, granularity) before writing details. Standalone, get the developer's feedback on the outline first; pipeline, proceed directly once the structure follows the ticket's boundaries.

### Step 4: Detailed Plan Writing

1. **Write the plan** to `ai/thoughts/plans/YYYY-MM-DD-ENG-XXXX-description.md`
   - Format: `YYYY-MM-DD-ENG-XXXX-description.md` where:
     - YYYY-MM-DD is today's date
     - ENG-XXXX is the ticket number (omit if no ticket)
     - description is a brief kebab-case description
   - Examples:
     - With ticket: `YYYY-MM-DD-ENG-1478-parent-child-tracking.md`
     - Without ticket: `YYYY-MM-DD-improve-error-handling.md`
2. **Use this template structure**:

```markdown
# [Feature/Task Name] Implementation Plan

## Overview

[Brief description of what we're implementing and why]

## Current State Analysis

[What exists now, what's missing, key constraints discovered]

## Desired End State

[A Specification of the desired end state after this plan is complete, and how to verify it]

### Key Discoveries:
- [Important finding with file:line reference]
- [Pattern to follow]
- [Constraint to work within]

## What We're NOT Doing

[Explicitly list out-of-scope items to prevent scope creep]

## Skill-Authoring Documentation Impact

**Impact**: [Affected / No impact]

- **Rationale**: [Explain which author-facing behavior changes, or why the change is purely internal and does not alter authoring guidance]
- **Documents to update**: [`agent-skills/loomspan-docs/references/skill-authoring/...`, or `None`]
- **Supporting evidence**: [Focused tests, fixtures, samples, and/or production source that establish the documented behavior]
- **Coverage table update**: [Required / Not required, with rationale]
- **LLM-first usability**: [How routing, topic boundaries, self-contained guidance, terminology, and explicit limitations will remain clear; or `Not applicable` when there is no impact]

If the impact is `Affected`, include each documentation change in the appropriate implementation phase below. Do not defer it to an unspecified follow-up. If existing documentation conflicts with executable behavior, call out the discrepancy explicitly rather than silently choosing one.

## Contract and Compatibility Impact

Required for Loomspan framework work. Cover every category, including categories with no impact:

| Surface | Impact and supporting evidence | Planned compatibility treatment |
| --- | --- | --- |
| Application API | [Affected entry points, evidence, and protected consumers, or no impact] | [Preserve or intentional break] |
| Supported SPI | [Affected extension points, evidence, and protected consumers, or no impact] | [Preserve or intentional break] |
| Configuration and manifest contracts | [Affected documented behavior and developer/skill-author impact, or no impact] | [Preserve or explicit atomic break] |
| Persisted or serialized contracts | [Durable intent and evidence, or no impact] | [Preserve or intentional break] |
| Ephemeral diagnostic formats | [Current-run trace impact, or no impact] | [Current-version coherence and security treatment] |
| Internal or accidentally exposed implementation | [Affected surfaces and technical exposure, or no impact] | [Atomic removal/update or justified preservation] |

- **Evidence of supported contracts**: [Documentation, explicit API/SPI allowlist, explicit ticket requirements, and/or verified consumer usage]
- **Intentional compatibility changes**: [Each intentional break supported by the ticket or `Pipeline notes`, with its impact, or none]
- **In-repository consumers to update**: [Callers, tests, samples, fixtures, configuration, manifests, and documentation]
- **Public-surface delta**: [Types, signatures, constructors, and Spring extension points added or removed]
- **Shim decision**: **[Shim / No shim].** [For a shim, identify the protected contract, explain why atomic change is inappropriate, name the mechanism, and state its removal condition]
- **Java-to-Go boundary coordination**: **[Not applicable / Not required / Required].** [When the application-adapter REST/SSE, acquisition, problem, or consumed NDJSON boundary is affected, identify the synchronized Java, Go, fixture, test, and documentation changes that must ship together.]
- **Pipeline notes alignment**: **[Aligned / Needs developer / No notes].** [Explain whether any intentional compatibility change is clearly covered by the ticket's optional notes. Broader or different impact requires developer input.]

## Implementation Approach

[High-level strategy and reasoning]

## Phase 1: [Descriptive Name]

### Overview
[What this phase accomplishes]

### Changes Required:

#### 1. [Component/File Group]
**File**: `path/to/file.ext`
**Changes**: [Summary of changes]

```[language]
// Specific code to add/modify
```

### Success Criteria:

#### Automated Verification:
- [ ] Project build/check passes: `[project build/check command]`
- [ ] Unit tests pass: `[unit test command]`
- [ ] Linting/formatting passes: `[lint/format command]`
- [ ] Integration tests (if any) pass: `[integration test command]`
- [ ] Skill-authoring guidance changed in this phase is supported by the cited tests, fixtures, samples, or production source (when applicable)
- [ ] Relevant `agent-skills/loomspan-docs/references/skill-authoring/` documents and the README coverage table are updated (when applicable)
- [ ] Changed skill-authoring guidance satisfies the README's `LLM-First Authoring Standard` (when applicable)

#### Optional Developer Checks:
- [Non-automatable observation the developer may choose to perform, or omit this section]

---

## Phase 2: [Descriptive Name]

[Similar structure with automated success criteria and optional developer checks only when useful...]

---

## Testing Strategy

### Unit Tests:
- [What to test]
- [Key edge cases]

### Integration Tests:
- [End-to-end scenarios]

 **Note**: Prefer a dedicated testing plan artifact created via `3_testing_plan.md` for full details (impacted areas, failing test first, commands to run, exit criteria). Keep this section as a high-level summary.

### Optional Developer Checks:
- [Non-automatable observation worth including in the final pipeline report, or omit this section]

## Performance Considerations

[Any performance implications or optimizations needed]

## Migration Notes

[If applicable, how to handle existing data/systems]

## References

- Original ticket/requirements: `ai/thoughts/tickets/eng_XXXX.md` (or wherever the repo stores tickets)
- Related research: `ai/thoughts/research/[relevant].md`
- Similar implementation: `[file:line]`
```

### Step 5: Sync and Review

- **Standalone**: present the draft plan location and iterate with the developer (phase scoping, success criteria, technical details, missing edge cases) until they are satisfied.
- **Pipeline**: do not report yet. Make ordinary evidence-backed decisions autonomously; any remaining material uncertainty belongs in `DEVELOPER QUESTION`, not in the plan. Continue directly into `3_testing_plan.md`, then return the combined Step Report required below.

## Important Guidelines

1. **Be Skeptical**:
   - Question vague requirements
   - Identify potential issues early
   - Ask "why" and "what about"
   - Don't assume - verify with code

2. **Be Collaborative (standalone) or Auditable (pipeline)**:
   - Standalone: get buy-in at major steps and allow course corrections
   - Pipeline: make evidence-backed decisions, persist them in the plan, echo them in `DECISIONS`, and escalate what evidence cannot settle

3. **Be Thorough**:
   - Read all context files completely before planning
   - Research actual code patterns using parallel sub-tasks
   - Include specific file paths and line numbers
   - Write measurable automated success criteria; list non-automatable developer observations separately and only when useful
   - Include an explicit, evidence-backed skill-authoring documentation impact assessment in every final plan
   - Prefer repo-standard wrapper commands (e.g., `make`, `mvn`, `gradle`, `npm`, `just`, etc.) over ad-hoc multi-step commands

4. **Be Practical**:
   - Focus on incremental, testable changes
   - Consider migration and rollback
   - Think about edge cases
   - Include "what we're NOT doing"

5. **Track Progress**:
   - Track planning tasks using whatever todo/task-tracking capability is available
   - Update todos as you complete research
   - Mark planning tasks complete when done

6. **No Open Questions in Final Plan**:
   - If you encounter open questions during planning, STOP: research them, ask the developer (standalone), or escalate (pipeline)
   - Do NOT write the plan with unresolved questions; every decision must be made before finalizing
   - Do not leave skill-authoring documentation impact unresolved or use "update docs if needed" as a placeholder
   - For framework work, do not finalize while it remains unclear whether an affected surface is deliberately supported or internal, or whether its behavior should be preserved or intentionally changed. Do not leave vague migration placeholders or unexplained compatibility machinery such as overloads, aliases, fallbacks, adapters, deprecated paths, legacy readers, duplicate interfaces, or dual behavior

7. **Stop After Planning (Do Not Implement)**:
   - This command's scope is strictly limited to research and creating the plan document(s). Do NOT write source code changes.
   - Standalone: once the plan is written and approved, STOP and end your turn; implementation requires a separate explicit prompt.
   - Pipeline: after the implementation plan is written, continue directly into `3_testing_plan.md` in this same context, then end with one combined Step Report listing both artifacts. Implementation still belongs to a later fresh stage.

## Success Criteria Guidelines

**Make automated verification the completion standard:**

1. **Automated Verification** (can be run automatically):
   - Commands that can be run: `make test`, `npm run lint`, etc.
   - Specific files that should exist
   - Code compilation/type checking
   - Automated test suites

2. **Optional Developer Checks** (nonblocking observations reported at pipeline completion):
   - UI/UX observations that cannot be exercised by available automation
   - Performance observations requiring an unavailable production-like environment
   - Other useful checks that cannot reasonably be automated

Do not make optional developer checks a plan exit criterion. If an acceptance criterion matters for correctness, design executable evidence for it wherever reasonably possible. The pipeline may still complete with optional checks outstanding and must carry them into its final report.

**Format example:**
```markdown
### Success Criteria:

#### Automated Verification:
- [ ] Database migration runs successfully: `make migrate`
- [ ] All unit tests pass: `go test ./...`
- [ ] No linting errors: `golangci-lint run`
- [ ] API endpoint returns 200: `curl localhost:8080/api/new-endpoint`

#### Optional Developer Checks:
- Observe the feature in the production-like UI environment unavailable to the pipeline
```

## Common Patterns

### For Database Changes:
- Start with schema/migration
- Add store methods
- Update business logic
- Expose via API
- Update clients

### For New Features:
- Research existing patterns first
- Start with data model
- Build backend logic
- Add API endpoints
- Implement UI last

### For Refactoring:
- Document current behavior
- Plan incremental changes
- Determine from evidence how each affected framework surface should be treated before making compatibility decisions
- Preserve deliberately protected contracts; for an intentional break clearly supported by the ticket or `Pipeline notes`, remove obsolete behavior and update in-repository consumers atomically
- Include a concrete migration strategy only when a protected contract or explicit ticket requires one; otherwise record the no-shim decision

## Sub-task Spawning Best Practices

When spawning research sub-tasks, give each a focused scope with concrete repo paths (not vague labels like "backend"), request specific `file:line` references, and cross-check unexpected results against the actual codebase before accepting them.
