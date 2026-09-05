---
audience: loomspan-skill-builder
status: development
applies_to: bundled-loomspan-revision
coverage: initial
---

# Source Verification Protocol for Skill Authoring

## Purpose

Use this protocol when the AI skill-authoring guide does not cover a question, when an edge case matters, or when documentation and observed behavior appear inconsistent.

Open source access gives a SkillBuilder the ability to verify Loomspan rather than relying on prose alone. Source inspection should be focused and evidence-driven; it should not replace the guide's explanation of intent and recommended design.

## Repository Alignment

This project has no production release yet. Use the guide, tests, samples, and source from the same repository revision. In a Loomspan source checkout, inspect that checkout. In a consumer project, inspect the official Loomspan GitHub tag matching the Maven dependency once releases exist. If matching source is unavailable, keep the answer within the bundled guide's documented coverage and state the limitation.

Do not mix bundled documentation with source from another branch, stale clone, fork, or remote default branch without stating the mismatch. When releases exist, inspect the tag that corresponds to the developer's Loomspan dependency.

## Evidence Order

### 1. Read the relevant guide topic

Extract:

- the intended semantic rule;
- whether the statement is enforced behavior, recommended design, or a known limitation;
- the named implementation anchors;
- any declared coverage boundary.

If the topic is marked not documented, do not infer rules from adjacent topics.

### 2. Read focused behavioral tests

Prefer tests with names that state the behavior under investigation. Determine:

- what setup creates the condition;
- what exact behavior is asserted;
- whether both success and failure paths are covered;
- whether the test protects a public contract or only an internal helper.

Tests are strong evidence of intended behavior, but they may have incomplete coverage.

### 3. Inspect fixtures and implemented samples

Use fixtures to learn accepted and rejected syntax. Use samples to learn composition patterns.

Distinguish:

- production sample manifests that are loaded and tested;
- test-only fixtures designed to isolate one rule;
- planned sample tickets that have not been implemented;
- historical plans or research that may describe an earlier design.

Do not present a planned ticket as executable current behavior.

### 4. Trace the production path

Follow the narrowest relevant path from public input to enforcement. For a contract feature, this commonly includes:

```text
manifest type
    -> catalog validation
    -> normalized runtime model
    -> execution integration
    -> trace/error behavior
```

Read complete methods and their immediate collaborators when practical. Search for all call sites before concluding that a helper defines the entire behavior.

### 5. Compare all evidence

Classify the outcome:

- **Aligned:** guide, tests, and production code agree.
- **Documentation drift:** code and tests agree but the guide is stale or incomplete.
- **Possible framework defect:** intended semantics and tests conflict with production behavior, or the framework gives instructions inconsistent with its validator.
- **Unresolved:** evidence is insufficient or contradictory.

Report the classification and supporting anchors. Do not silently rewrite the developer's skill to accommodate a possible defect.

## What Source Code Can and Cannot Establish

Source code can establish:

- accepted structures and types;
- validation timing and error behavior;
- runtime state transitions;
- exact propagation and isolation mechanics;
- retry, limit, and failure paths.

Source code alone does not reliably establish:

- why the behavior exists;
- whether an awkward behavior is intentional or transitional;
- which of several supported patterns is recommended;
- what future compatibility the project intends;
- whether a convenience conflicts with Loomspan's design values.

Use the authoring guide for that reasoning. When working in the Loomspan source repository, also consult `ai/thoughts/framework-feature-design-lens.md`.

## Stable Reference Style

When adding implementation anchors to the guide:

- name the source-repository-relative path or stable class/test anchor as code
  text; do not add Markdown links that escape the distributable skill package;
- name the relevant class and method or test in prose;
- avoid durable line-number references;
- prefer a focused test or fixture alongside production code;
- state what the anchor proves;
- avoid linking broad directories without explaining what to search for.

Example:

```markdown
- `EvidenceCoverageValidator#validatePlanCoverage` defines how planned
  exact planned child names satisfy required evidence expressions.
- `EvidencePlanningIntegrationTest#acceptsEitherInvestigatorWithoutRequiringBothAndRendersTheCanonicalExpression`
  protects the successful planning case.
```

## Source Investigation Checklist

- [ ] Confirm the repository revision being examined matches the guide and dependency.
- [ ] Check the topic's coverage status.
- [ ] Read the documented semantic rule before implementation details.
- [ ] Identify focused success and failure tests.
- [ ] Distinguish implemented samples from plans and tickets.
- [ ] Find the manifest or public API boundary.
- [ ] Find startup validation.
- [ ] Find runtime enforcement and all relevant call sites.
- [ ] Find trace, retry, and terminal failure behavior.
- [ ] Compare guide, tests, fixtures, samples, and implementation.
- [ ] Report discrepancies explicitly.
- [ ] Update the guide when the current semantics are confidently established.

## Safety and Scope

Treat source browsing as read-only investigation unless the developer separately requests changes. Do not execute untrusted repository scripts merely to understand authoring semantics. Prefer official Loomspan repository content over forks unless the developer explicitly works from a fork.
