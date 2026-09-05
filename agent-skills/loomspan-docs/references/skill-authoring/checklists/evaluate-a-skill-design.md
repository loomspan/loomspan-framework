---
audience: loomspan-skill-builder
status: development
applies_to: bundled-loomspan-revision
coverage: initial
---

# Evaluate a Loomspan Skill Design

## Purpose

Use this checklist while collaborating with a developer on a new or changed skill tree. It guides judgment; it does not replace manifest validation, tests, or topic-specific authoring documents.

Read [the skill-tree mental model](../mental-model.md) first. Consult only the topic documents relevant to the proposed tree.

## 1. Define the Mission Boundary

- What does the entry skill promise to do?
- What business input must the application developer provide?
- Can that developer understand the call from the entry input contract alone?
- What structured result, if any, does the application depend on?
- Which responsibilities are deliberately outside the mission?

Reject an entry design that requires callers to understand internal child propagation or planner mechanics merely to form a valid request.

## 2. Assign Responsibilities

- Which decisions require model reasoning, interpretation, selection, or synthesis?
- Which operations have deterministic correctness rules or controlled side effects?
- Which deterministic operations should be direct Java `@SkillMethod` skills?
- Does each skill have one coherent responsibility?
- Does nesting create a useful abstraction boundary, or only add another model call?
- Could a specialist be reused without depending on hidden parent state?

## 3. Design the Capability Surface

- Does every Java or YAML registered name match `^[A-Za-z_][A-Za-z0-9_]{0,63}$` and use the exact same case in every reference?
- Do `SkillTemplate`, `allowed_skills`, plans, and evidence-expression references use registered names rather than bean/method diagnostic locations?
- Which direct child capabilities does each LLM-backed skill need?
- Are those children declared locally through `allowed_skills`?
- Is the surface narrower than the global catalog?
- Does the parent depend only on direct children rather than hidden grandchildren?
- Are RBAC and execution-time access requirements compatible with every required branch?
- Could removal of a protected child make a required contract impossible for some callers?

## 4. Design Inputs and State

- Which values are ordinary business input?
- Are those values explicit in the relevant skill and tool contracts?
- Does each Java skill's reflected input contract express its intended caller and tool contract?
- Are Java inputs owned by method signatures and `@SkillParam`?
- Have obsolete YAML wrappers been removed, with their chosen names/descriptions on annotations?
- Are names unique across both sources and are all child references resolvable?
- Are model, planning, schema, linting, retry, tool-selection, and evidence declarations kept on LLM-backed parents with Java execution remaining annotation-defined?
- If a different public input shape is required, is there a separate deterministic Java skill with its own name and signature?
- Is any trusted identity, tenant, correlation, deadline, or provenance value being delegated to the model without a framework reason?
- Is mutable global, thread-local, or session-bag behavior being introduced?
- Does evolving information belong in tool results, evidence, plans, artifacts, or traces instead?

Do not invent an undocumented propagation mechanism to reduce argument repetition.

Reflection defines one Java skill's input contract; it does not propagate business inputs between missions.

## 5. Decide Whether Planning Is Needed

- Does the skill need an explicit task plan, dynamic decomposition, and a bounded step loop?
- If so, is `planning_mode: true` explicit?
- If not, can it use direct mission execution, including when nested?
- If a direct skill has visible children, is direct model-directed tool use sufficient without a framework-managed task plan?
- Is `max_steps` bounded for the mission?
- Can the planner complete the mission using only its visible direct children?
- Does each direct child intentionally need no bound, a positive minimum, a maximum, or both?
- Is every positive-minimum child visible and authorized for all callers who must run this parent?
- Are maximum-only children correctly treated as optional, and is every effective minimum no greater than its maximum?
- Are unconditional generated-task counts modeled separately from property-level evidence requirements?
- Are selective branches truly alternatives, and do prompts and contracts preserve that choice?
- Does every extra planning level justify its model cost and failure surface?
- Has the design avoided adding a planning boundary merely because the skill is nested?
- Are model-compatibility claims limited to configurations and tree shapes that were actually tested?

Planning depth is not itself a quality goal. Prefer the least complex execution semantics that truthfully fit each skill's mission, while retaining planning wherever genuine decomposition or child selection is required.

Read [planning-task-constraints.md](../planning-task-constraints.md) before declaring `min_tasks`, `max_tasks`, or `required`.

## 6. Design Output and Evidence

- Which top-level output fields are claims about the mission result?
- Which claims need deterministic supportability enforcement?
- Is each requirement a nonblank string `evidence` annotation on an immediate root `output_schema` property, never on the root, a nested property, or array items?
- What minimum Boolean expression over successful direct children supports each claim?
- Does every reference exactly match a direct `allowed_skills` name, including case?
- Are conjunctions written with `and`, genuine substitutes with `or`, and mixed precedence made explicit with parentheses?
- Can every declared claim be covered during planning without forcing unrelated OR branches?
- Does successful completion of each referenced child justify support for the claim?
- Are workflow order and multi-intent branch policy kept outside the evidence expression?
- Are nested child internals kept behind the child capability boundary?

Read [evidence-contracts.md](../evidence-contracts.md) before authoring property-level evidence requirements.

## 7. Preserve Production Safeguards

- Is authorization enforced by the framework rather than prompts?
- Do supported Java policies have actual JSR-250 advice enabled, with matching YAML role prefix/hierarchy semantics?
- Have local allowlists and caller authentication isolation been checked against [authorization](../authorization.md)?
- Can the model override a value it should not control?
- Are tenant and resource boundaries preserved across nesting?
- Are validation failures deterministic and actionable?
- Are sensitive inputs and outputs appropriately represented in traces?
- Are timeouts, depth, tool, model-call, and usage limits appropriate?
- Is cancellation behavior important to the operations being invoked?

If the current knowledge base does not document the safeguard, verify it through [source-verification.md](../source-verification.md).

## 8. Plan Verification

- What is the smallest successful path?
- What selective alternative branches must be exercised?
- What tool or child failure should be simulated?
- What invalid input should fail before execution?
- What unsupported output claim should be rejected?
- What depth, step, or quota limit is relevant?
- What trace or journal facts prove the intended tree executed?
- Can tests assert branch and contract behavior without depending on exact model prose?

## 9. Apply the Framework Feature Lens

When authoring friction suggests a framework change, stop treating the workaround as an automatic requirement. In the Loomspan source repository, evaluate the request through `ai/thoughts/framework-feature-design-lens.md`.

Ask especially:

- Would the feature remain useful with a highly capable planner?
- Is it a framework responsibility or model weakness?
- Does it improve both the skill developer and entry caller experience?
- Does it introduce ambient state, mutation, implicit inheritance, or hidden precedence?
- Is the abstraction smaller and clearer than the friction it removes?
- Has the concern appeared in multiple independent skill trees?

Record recurring but unproven concerns as possible future features rather than silently expanding the framework.

## Expected Review Output

Summarize:

1. the proposed entry contract;
2. each skill's responsibility and type;
3. the direct visibility graph;
4. business input and state ownership;
5. planning boundaries and selective branches;
6. output and evidence semantics;
7. production safeguards and unresolved questions;
8. verification scenarios;
9. any friction that may signal a future framework feature.
