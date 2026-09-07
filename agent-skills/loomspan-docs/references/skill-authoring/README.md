---
audience: loomspan-skill-builder
status: development
applies_to: bundled-loomspan-revision
coverage: initial
---

# Loomspan Skill Authoring Knowledge Base

## Purpose

This directory is the AI-first knowledge base for an LLM that collaborates with a developer to design, build, review, and diagnose Loomspan skill trees.

Loomspan is under active development and has no production release yet. These documents describe the Loomspan revision with which this skill package was published. When working from a source checkout, use the skill package from that same checkout. When working from a Maven dependency, install the skill from the matching repository tag once releases exist. The guide is deliberately incomplete: a missing topic must not be treated as either unsupported or fully understood.

The intended future consumer is a SkillBuilder application, potentially built with Loomspan itself. Until then, an LLM can use these documents directly while working in the repository.

## Authority and Verification

Use the following evidence order:

1. **This knowledge base** explains intended authoring semantics, design judgment, and recommended patterns.
2. **Focused tests** demonstrate behavior the repository deliberately protects.
3. **Valid and invalid fixtures** demonstrate accepted and rejected manifest shapes.
4. **Application examples**, when available for the matching framework version, demonstrate composition in representative applications.
5. **Production source** resolves exact runtime behavior and edge cases.

The executable framework remains authoritative for what the corresponding revision accepts and does. If this guide conflicts with matching tests or production code, do not silently choose one. Report the conflict, identify whether it affects authoring advice, and treat it as documentation drift or a possible framework defect.

Read [source-verification.md](source-verification.md) before performing a source-level investigation.

## How to Route an Authoring Task

| Developer need | Read first | Then read |
| --- | --- | --- |
| Understand Loomspan skill trees | [mental-model.md](mental-model.md) | Relevant topic documents below |
| Design or review a new tree | [checklists/evaluate-a-skill-design.md](checklists/evaluate-a-skill-design.md) | [mental-model.md](mental-model.md) |
| Design or diagnose reflected Java skill inputs | [input-contracts.md](input-contracts.md) | [mental-model.md](mental-model.md) for declaration ownership |
| Design or diagnose an `output_schema` contract | [output-contracts.md](output-contracts.md) | [evidence-contracts.md](evidence-contracts.md) for supportability or [traces-and-debugging.md](traces-and-debugging.md) for retry diagnostics |
| Declare an annotation-only Java skill | [Java skills](../java-api/java-skills.md) | [input-contracts.md](input-contracts.md) |
| Configure Java/YAML roles or diagnose authorization | [authorization.md](authorization.md) | [mental-model.md](mental-model.md) for local visibility |
| Add evidence-backed output claims | [evidence-contracts.md](evidence-contracts.md) | [mental-model.md](mental-model.md) |
| Declare or diagnose generated-plan task counts | [planning-task-constraints.md](planning-task-constraints.md) | [evidence-contracts.md](evidence-contracts.md) when output supportability is also required |
| Configure planning concurrency or diagnose task grouping/dependencies | [planning-concurrency.md](planning-concurrency.md) | [planning-task-constraints.md](planning-task-constraints.md) when child task counts are also constrained |
| Select a model or configure its connection | [model-selection-and-connections.md](model-selection-and-connections.md) | [mental-model.md](mental-model.md) |
| Diagnose retries, usage, terminal failures, or a nested runtime path with the packaged Agent Skill | [traces-and-debugging.md](traces-and-debugging.md) | The relevant validation or evidence topic |
| Resolve ambiguity or an edge case | [source-verification.md](source-verification.md) | The topic's implementation anchors |

Do not load every document by default. Start with the routing entry most relevant to the developer's goal and expand only when the task crosses another documented concern.

## Coverage

| Topic | Coverage | Notes |
| --- | --- | --- |
| Skill-tree mental model | Initial, source-verified | Shared Java/YAML identity, complete registration, root invocation, local visibility, lifecycle, and nesting |
| Shared skill identity | Source-verified | Exact `name` format, case sensitivity, no-rewrite policy, propagation, duplicates across sources, and no diagnostic-location aliases |
| Evidence contracts | Source-verified | Immediate-root property annotations, strict scalar/placement rules, Boolean direct-child expressions, planning/final truth sets, metadata isolation, enforcement, and nested mission isolation |
| Source verification | Initial | How an LLM should use guide, tests, fixtures, samples, and production code together |
| Skill-design review | Initial | Cross-cutting questions; not a manifest validator |
| YAML manifest reference | Not yet documented | Inspect current manifest, catalog validation, tests, and samples when required |
| Input contracts | Initial, source-verified | Reflected Java `Object`, generic and typed maps, DTOs, arrays, requiredness boundaries, shared root/child reflected contracts, validation, and planner guidance; complete pure-YAML schema syntax remains undocumented |
| Output contracts | Source-verified | Supported `output_schema` vocabulary, normalization, validation, retries, model guidance, and current limitations |
| Prompts | Focused, source-verified | Recursive output-contract guidance is covered; general private prompt composition still needs a dedicated topic |
| Planning and nested planning | Focused, source-verified | Task counts, concurrency applicability, exact grouping, ordered units, dependencies, coordinator assignment, exact worker correction, step costs, enabled-group fork/join, serialized opt-out, task-ordered folding, mission-wide cutoff cleanup, hierarchical late-write fencing, and diagnostic isolation are covered |
| Capability visibility and RBAC | Focused, source-verified | JSR-250 class/method/interface policies, required enforcement, role prefix/hierarchy parity, local allowlists, and scoped caller authentication; expression security is outside this contract |
| Attachments and virtual files | Not yet documented | Requires separate source verification |
| Model selection and connections | Initial, source-verified | Framework model aliases, named connections, drivers, thinking levels, migration, and diagnostics |
| Execution limits and quotas | Foundational | Trace guidance covers model-attempt and usage quota effects plus run-start diagnostic comparison; complete limit configuration remains undocumented |
| Traces and debugging | Source-verified | Thirteen-tool discovery, current runtime status, visible complete live branches with exact snapshot simultaneity, authoritative MCP whole-plan queries, assignment-aware canonical timeline rows, admission/join events plus accepted-order plan projections, inline same-plan status/note comparisons with exact nullable values and explicit bounded/unavailable evidence, exact nullable observed-overlap semantics, independent finalized/acquired/imported discovery, compact/detailed frames and records, descriptor/inline/exact semantic reads, lossless byte-budgeted traversal, usage/failures, deliberate raw forensics, and current-run/transport limitations |
| Testing skill trees | Not yet documented | The design checklist gives initial review prompts only |

## Normative Language

These documents use:

- **MUST / MUST NOT** for behavior required by current framework validation, execution semantics, security, or an explicitly stated authoring invariant.
- **SHOULD / SHOULD NOT** for the recommended Loomspan authoring default.
- **MAY** for an optional supported choice.

Each document should distinguish enforced behavior from design guidance and known limitations. Do not present a recommendation as a runtime requirement.

## LLM-First Authoring Standard

The primary consumer of this knowledge base is an LLM collaborating with a developer, not a person reading the documentation from beginning to end. Optimize for correct retrieval, interpretation, and task execution. Human readability remains useful, but it is not the organizing goal.

- Lead with applicability, author-facing semantics, constraints, and required decisions. Add background only when it changes how the guidance should be applied.
- Preserve progressive disclosure. Keep routing in this README accurate, keep topic documents focused, and link to adjacent topics instead of repeating them.
- Make ordinary guidance self-contained enough for an LLM to act without source inspection. Use source anchors to verify or deepen the guidance, not as a substitute for explaining it.
- Use consistent framework terminology and explicit normative language. Clearly separate enforced behavior, recommended design defaults, optional choices, and known limitations.
- Prefer compact, structured representations such as decision tables, checklists, exact field names, and minimal examples when they reduce ambiguity. Do not add structure that merely restates the same content.
- Omit conversational introductions, rhetorical transitions, motivational prose, repeated summaries, and historical narrative that does not affect the documented revision.
- Keep examples minimal and evidence-backed. State when an example is partial, illustrative, invalid, or omits surrounding configuration.
- State unknowns, incomplete coverage, and conflicts explicitly so an LLM does not turn missing information into a claim.
- Prefer stable repository-relative references and named implementation anchors over volatile line-number citations.
- Optimize for signal and precision, not token reduction alone. Do not remove qualifications, edge cases, or distinctions needed to apply the guidance correctly.

Before accepting a change, verify that an LLM loading only the routed documents can determine:

1. When the guidance applies.
2. What is required, prohibited, recommended, and optional.
3. What evidence supports material behavioral claims.
4. What remains unknown, incomplete, or conflicting.
5. Which document to load next if the task crosses the current topic's boundary.

## Development Rules for This Knowledge Base

- Follow the LLM-first authoring standard above for every new or changed topic.
- Reuse tested fixtures and sample manifests instead of duplicating large examples when practical.
- Add a topic only after its current semantics have been researched and can be stated without speculation.
- Update the routing and coverage tables whenever a topic is added, its task boundary changes, or its confidence changes.

At the first production release, the repository tag should version this knowledge base with the corresponding source and tests.
