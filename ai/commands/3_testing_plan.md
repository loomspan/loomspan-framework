---
description: Create a testing plan to identify impacted areas, design tests (including failing tests), and define exit criteria
---

# Testing Plan

You are tasked with creating a focused testing plan for a code change. The goal is to identify what could break, design the right level of automated tests (unit/integration/e2e), and define clear exit criteria.

This command is specifically for designing tests, including specifying a failing test that will prove the problem before the implementation fixes it. It produces a testing plan; it does not edit test code.

## Shared Protocols

- Follow `ai/commands/shared/automation-protocol.md` for execution modes, pipeline judgment, `Pipeline notes`, and the Step Report you must end with. In pipeline mode this command continues in the same context as `2_create_plan.md` immediately after the implementation plan is written, and the two commands share one combined Step Report.
- Follow `ai/commands/shared/loomspan-docs-protocol.md` for the evidence hierarchy, `loomspan-docs` skill usage, and drift classification. Inventory the matching checked-out production source, focused tests, fixtures, samples, codecs, builders, and build configuration before consulting authoring guidance.

## Initial Response

When this command is invoked:

1. **Check if parameters were provided**:
   - If a file path to a plan, ticket, or research doc was provided (or is already in context from `2_create_plan.md`), immediately read any provided files completely

2. **If no parameters provided**, ask the user for: the implementation plan path (preferred) or a short description of the change, any ticket/requirements/research docs that define expected behavior, and how they intend to verify the change. Then wait for input. (In pipeline mode, missing parameters are an escalation.)

## Process Steps

### Step 1: Gather Context

1. **Read all mentioned files immediately and completely**:
   - Implementation plan document(s)
   - Ticket/requirements files
   - Research documents
   - Any logs/error output (if provided)
   - For framework work, read the implementation plan's `Contract and Compatibility Impact` section and use its analysis of supported and internal surfaces to determine compatibility test scope

2. **Locate executable behavior, existing tests, and patterns**:
   - Trace the affected production path and identify where current behavior is parsed, validated, stored, serialized, projected, or executed
   - Find existing tests for the impacted modules
   - Find similar tests in adjacent features
   - Identify conventions for fixtures, builders, codecs, model fakes, clocks, state services, and test naming
   - Establish the current pre-fix failure or behavior from source and focused tests before defining the red test

3. **Consult authoring guidance after the executable inventory**:
   - If the implementation plan marks `Skill-Authoring Documentation Impact` as `Affected`, follow `ai/commands/shared/loomspan-docs-protocol.md`: use the routed knowledge-base documents to enumerate intended author-facing claims, map each claim to focused source, test, fixture, or sample evidence, and record drift explicitly. Do not derive runtime expectations from prose when matching executable evidence disagrees

### Step 2: Define Scope and Risks

Create a short, explicit list of:
- **Behaviors changing** (user-visible and internal)
- **Impacted areas** (files/components)
- **Primary risks** (regressions, edge cases, integrations)
- **Authoring claims requiring evidence** when the change affects `agent-skills/loomspan-docs/references/skill-authoring/` guidance
- **Protected compatibility paths** and their evidence, plus **intentionally removed obsolete paths**

Tests establish existing behavior but do not independently establish a supported compatibility promise. Use the canonical categories from `ai/thoughts/framework-feature-design-lens.md`: Application API, Supported SPI, Configuration and manifest contracts, Persisted or serialized contracts, Ephemeral diagnostic formats, and Internal or accidentally exposed implementation.

When the work affects the Loomspan Console application-adapter REST/SSE, acquisition, problem, or consumed NDJSON boundary, carry forward the Java-to-Go boundary-coordination scope defined by the ticket and plan and cover the affected executable fixtures, exact release-string rejection, and observable semantics.

### Step 3: Plan the Failing Test (When Applicable)

When a bug or incorrect behavior is involved, propose a **minimal failing test** that:
- Fails reliably on the current behavior
- Demonstrates the problem clearly
- Is as low-cost as possible (unit first, then integration if necessary)

If the work is a pure refactor with no behavior change, explicitly say so and focus on regression coverage.

### Step 4: Specify Tests to Add/Update

For each proposed test, specify:
- **Name**
- **Type**: unit / integration / e2e
- **Location**: path where it should live
- **What it proves**: exact expected behavior
- **Inputs/fixtures needed**
- **Mocking strategy** (if any)

Preserve compatibility-path tests only for deliberately protected Application API, Supported SPI, Configuration and manifest contracts, or Persisted or serialized contracts. Update or remove tests that encode an obsolete Internal or accidentally exposed implementation when the intentional change is clearly supported by the ticket or `Pipeline notes`; do not require old and new behavior simultaneously. When Application API, Supported SPI, or accidental public exposure changes, add boundary tests for public signatures, leaked internal types, and unintended extension points where applicable.

For Ephemeral diagnostic formats, test current writer/reader/projector/debugging-tool coherence, diagnostic usefulness, accuracy, ordering, failure visibility, security boundaries, and redaction. Do not require historical trace readability, old schemas, or obsolete fixtures.

When skill-authoring documentation is affected, ensure the proposed focused tests establish the author-facing semantics the updated guidance will describe. Do not add tests merely to exercise prose; test the underlying framework behavior.

### Step 5: Running Tests + Exit Criteria

Define:
- **Commands to run locally** (build + tests)
- **Any required profiles/env vars/test data**
- **Exit criteria** (what must be true to consider the change verified)
- For framework changes, confirmation that protected paths still work and intentionally removed obsolete paths are absent rather than retained behind fallbacks
- Any non-automatable observations worth offering to the developer as optional, nonblocking checks in the final pipeline report

## Output Artifact

Write the testing plan to:
- `ai/thoughts/plans/YYYY-MM-DD-ENG-XXXX-description-testing.md`

If there is no ticket number, omit it.

End with the Step Report from the automation protocol. In pipeline mode, return one combined report for the implementation plan and testing plan.

## Testing Plan Template

Use this structure:

```markdown
# [Feature/Task Name] Testing Plan

## Change Summary
- [What is changing]

## Impacted Areas
- [File/component]

## Risk Assessment
- [High-risk behaviors]
- [Edge cases]
- [Protected compatibility paths and intentionally removed obsolete paths]

## Existing Test Coverage
- [Relevant existing tests]
- [Gaps]

## Bug Reproduction / Failing Test First
- Type: unit/integration/e2e
- Location:
- Arrange/Act/Assert outline:
- Expected failure (pre-fix):

## Tests to Add/Update
### 1) [Test Name]
- Type:
- Location:
- What it proves:
- Fixtures/data:
- Mocks:
- Affected surface: [Application API / Supported SPI / Configuration or manifest behavior / Persisted or serialized behavior / Ephemeral diagnostics / Internal implementation]
- Compatibility expectation: [protected path / intentional removal authorized by the ticket or `Pipeline notes` / current-run diagnostic coherence]

## How to Run
- [Build command]
- [Test command(s)]

## Exit Criteria
- [ ] Failing test exists and fails pre-fix (when applicable)
- [ ] All tests pass post-fix
- [ ] New/updated tests cover the changed behavior and key edge cases
- [ ] Tests cited as evidence for changed skill-authoring guidance establish the documented behavior (when applicable)
- [ ] Protected compatibility paths pass, and intentionally obsolete paths are removed without simultaneous old/new behavior when the ticket or `Pipeline notes` authorizes that change (when applicable)
- [ ] Changed public boundaries and current-run trace obligations are covered according to the plan's compatibility analysis (when applicable)
- [ ] Any non-automatable observations are listed as optional developer checks for the final pipeline report and are not treated as completion gates
```
