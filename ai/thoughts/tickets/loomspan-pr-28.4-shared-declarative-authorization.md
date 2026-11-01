# PR 28.4 — Apply one composed authorization policy across Java skill execution

## Outcome

Root validation, planner tool filtering, and execution use the same caller and declarative authorization semantics. An executor-backed YAML skill requires both its own policy and its captured executor's Java policy, while actual Spring advice remains enforced at invocation.

## Requirements

- Consolidate existing root-policy evaluation and `AccessGuard` evaluation around one shared composition/evaluation owner. SkillMethod uses its Java declarative policy. Executor leaves use a conjunction of YAML and executor policies, both evaluated against the same resolved caller using existing role prefixes, hierarchies, and evaluator. Do not flatten constituents into one alternative-role list or create an executor authorization service/policy language.
- Resolve executor policy from PR 28.3's captured registration. Do not permanently copy foreign policy into the caller generation or consult live registrations during retained work. Preserve callable authority separately: captured entry-target and declared-child rules still govern calls; retaining a namespace is not permission to call unrelated skills.
- Planner filtering omits a skill if either constituent denies it. Root validation and execution report denial through their existing boundaries. Recheck authorization on execution even after filtering. PermitAll/unrestricted constituents cannot override restrictive ones; DenyAll denies the conjunction; alternatives remain alternatives within each constituent.
- Root validation and tool discovery remain metadata-only and never execute application code or argument-dependent advice. Preserve supported `@RolesAllowed`, `@PermitAll`, and `@DenyAll` resolution and enforcement verification from shared discovery.
- Invoke actual Spring proxies with actual arguments so all applicable method-security advice runs, including argument-dependent rules. Declarative evaluation does not predict arbitrary advice or replace it. Preserve authentication capture, explicit-caller/session-fallback semantics, worker-thread propagation, and context restoration for roots/children, failures, and retained work.
- Keep the invocation record's five components; add no authentication accessor or context bag. Management/catalog visibility is unchanged. The extra YAML policy is a necessary declaration-boundary difference, not a reason for separate role evaluators or execution controls.
- Update relevant access-control/Java-executor guidance and maintain focused shared authorization tests for both Java forms.

## Acceptance criteria

- [ ] Both policies permitting allows execution; either denying rejects it. Tests distinguish role alternatives within a constituent from conjunction across constituents, including PermitAll, DenyAll, role prefixes, and hierarchies.
- [ ] Root validation, planner filtering, and root/child execution agree on declarative outcomes through one shared implementation. Discovery stays metadata-only and invocation rechecks authorization.
- [ ] Real Spring proxies enforce invocation-only argument-dependent advice; success/failure paths restore authentication, including worker threads and explicit-caller/session fallback.
- [ ] Retained cross-namespace work uses its captured executor policy after foreign replacement/removal; fresh work uses the newly admitted view. Namespace retention and earlier filtering cannot bypass denial or grant unrelated callable authority.
- [ ] No public authentication/context surface, executor-specific authorization engine, or management visibility change is introduced. Updated guidance and `LoomspanPublicSurfaceArchitectureTest` cover the changed production surface.

## Context and sequencing

Depends on [PR 28.3](loomspan-pr-28.3-generation-owned-executor-execution.md). Owns accepted finding 2 in [Configured Java Skill Executors](../phases/configured-java-skill-executors.md); apply the [feature design lens](../framework-feature-design-lens.md). Source recheck found direct root evaluation in `DefaultSkillTemplate` and separate evaluation in `DefaultAccessGuard`, whose captured source/target authority checks must survive consolidation. Use shared discovery's security metadata and existing authentication scopes rather than adding independent executor checks at each boundary.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** The ticket changes security-policy composition across discovery, validation, and execution.
- **Reassessment triggers:** Concrete gaps in supported declarative policy resolution or proxy enforcement; ordinary internal consolidation is already accepted.

## Pipeline notes

- Framework/Sidecar have no deployments. Internal/API breaks and intermediate build failures across this series are allowed; no compatibility overloads, duplicate policy implementations, or temporary executor authorization subsystem should be retained.
- Planner recovery, Sidecar migration, arbitrary advice pre-evaluation, and new authentication APIs are outside scope.
