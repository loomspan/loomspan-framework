# PR 28.1 — Share Java discovery and invocation for skills and executors

## Outcome

Establish one Java discovery and proxy-invocation implementation for directly callable `@SkillMethod` methods and reference-only `@SkillExecutor` methods. Replace Java result/error transformation with the accepted shared contract. This is the foundation of the PR 28 series, not a standalone executor runtime.

## Requirements

- Add supported `ai.loomspan.api.SkillExecutor` with `name`, `namespace`, and `config` elements. Omitted name uses the method name; namespace defaults to `default`; `String config() default ""` selects no configuration when empty. Reuse existing identity validation and namespace normalization. Executors accept exactly one `SkillExecutorInvocation` parameter and support the same return types as SkillMethod. Do not generate a callable input schema from the invocation record.
- Add the public record `SkillExecutorInvocation` with exactly five components: `Map<String, Object> input`, `Map<String, Object> skillConfig`, `Map<String, Object> executorConfig`, `String skillName`, and `String executorConfigGenerationId`. Enforce required fields in its public constructor and defensively copy all three maps. No builder, authentication field, context bag, or internal runtime objects belong in this API.
- Extract the map/list container-copy behavior from `RestSkillInvocation` into one internal implementation reusable by invocation construction and PR 28.2 configuration processing. Recursively copy into unmodifiable containers, preserving nulls and non-container leaves, including Spring `Resource` handles. Do not serialize arbitrary objects to copy them or claim that resource bytes or arbitrary object leaves are frozen.
- Use one discovery process for both roles, sharing canonical/bridge method handling, interface-contract resolution, proxyability checks, identity rules, supported declarative security-policy discovery/enforcement verification, and runtime proxy selection. Reject conflicting roles across a method's implementation and interface declarations, not merely two annotations on the same reflected method. Incompatible interface contracts fail deterministically with useful diagnostics.
- Keep separate callable and executor identity indexes within the existing registration/generation design. Duplicate identities within a role fail; a skill and executor may share a qualified name. Mixed-role beans and executor-only beans are supported. Do not add a second scanner, independent registry lifecycle, bean-replacement contract, or directly callable executor route.
- After role-specific argument construction, use one Spring-managed proxy invoker and result converter. Null becomes empty text; non-null strings, including whitespace, remain unchanged; other values are JSON-serialized. Serialization failures propagate. Remove `LoomspanExceptionTransformer`, its default implementation, wiring, and bypasses needed solely to evade exception-to-successful-text conversion. Preserve security and execution controls at their proper owners.
- Add supported `RecoverableSkillException extends SkillException`, with `(String message)` and `(String message, Throwable cause)` constructors. Reject null/blank messages; preserve valid messages unchanged; an optional cause supplies diagnostics, never fallback feedback. Establish one internal recoverability policy: only this designated exception, through recognized invocation-infrastructure wrappers, is recoverable. An application wrapper around it remains terminal. Identify actual reflection/proxy wrappers and test them; do not search arbitrary cause chains. PR 28.5 extends integration through worker/tool boundaries using this same policy.
- Update the closed API/internal allowlists, supported-surface tests, README API summary, and Java API guidance for the additions and changed Java results. Public signatures must not expose internal/autoconfigure types. The only necessary role differences are declaration validation, callable schema ownership, argument construction, and registration destination; identify and justify any further difference in implementation planning.

## Acceptance criteria

- [ ] Shared discovery tests cover JDK/CGLIB proxies, interface annotations, generic bridges, equivalent/conflicting contracts, split interface/implementation role conflicts, explicitly named overloads, mixed-role/executor-only beans, duplicate identities, and same-name cross-role registration.
- [ ] Executors remain reference-only and unsafe proxy invocation fails; real method-security advice is preserved for both roles.
- [ ] Public constructor tests establish the five-component invocation contract, required fields, nested defensive copying, null preservation, and resource/non-container leaf behavior. Recoverable exception constructors preserve exact valid messages and causes and reject invalid messages.
- [ ] Both Java forms share null/string/object conversion and failure propagation, including serialization failure, transparent infrastructure wrapping, and terminal application wrapping. The old exception transformer and alternate conversion path are gone.
- [ ] Supported-surface tests and `LoomspanPublicSurfaceArchitectureTest` cover the deliberate additions; relevant documentation describes the new public contract. End-to-end executor binding is explicitly delivered by PR 28.3, and orchestration disposition by PR 28.5.

## Context and sequencing

First ticket; no PR 28 prerequisite. Implements accepted finding 7 and the Java invocation/classifier portion of finding 3, plus the shared-copy foundation for finding 6 in [Configured Java Skill Executors](../phases/configured-java-skill-executors.md). Read that accepted design and the [feature design lens](../framework-feature-design-lens.md). Exact internal class boundaries remain implementation choices.

Source recheck: `SkillMethodBeanPostProcessor` currently combines discovery, binding, invocation, and result conversion; it JSON-serializes all results and transforms caught runtime failures. `RestSkillInvocation` owns the container-copy baseline. Consolidate those responsibilities rather than copying them into an executor postprocessor. REST API deletion belongs to PR 28.3; extracting its useful copying behavior does not authorize a compatibility adapter.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Supported API additions, proxy/security behavior, and shared Java execution semantics require full assurance despite settled product intent.
- **Reassessment triggers:** Concrete evidence of an unmet accepted requirement; internal decomposition may change without reopening accepted decisions.

## Pipeline notes

- Framework and Sidecar have no deployments or customers. Breaking source/binary/behavioral changes and broken intermediate builds across PR 28 are authorized. Do not add shims, aliases, dual behavior, or temporary executor subsystems to keep intermediate commits working. The complete series must restore a coherent build.
- The SkillMethod string/null behavior change and removal of exception-to-successful-text conversion are intentional. No Sidecar implementation or planner recovery extension is included.
