# PR 28.3 — Execute configured Java leaves through captured namespace generations

## Outcome

Deliver executor-backed YAML execution through one namespace-generation system, shared dependency resolution, and one retained admission snapshot. Remove obsolete REST execution and API support rather than creating a parallel executor lifecycle.

## Requirements

- Each namespace generation owns callable declarations, fixed reference-only executor registrations, and immutable executor configuration from PR 28.2. Separate skill/executor name indexes permit the same qualified name; they do not authorize separate activation, registry, configuration revision, or retirement lifecycles. Initialize namespaces containing only executors.
- Consolidate dependency semantics for exactly two reference roles: `allowed_skills` denotes callable children; `executor` denotes execution behavior/configuration. Share identity and namespace visibility resolution, then apply role-specific structural checks. Local executor names resolve in the YAML skill's namespace; qualified names resolve explicitly, without fallback. Same-namespace and foreign Public executors are allowed; foreign Private executors are rejected. `default` is not Public visibility.
- The shared resolution owner validates annotation paths and selects configuration subtrees. Paths follow PR 28.2's dot-path contract. An omitted reference yields an empty map; an explicit missing, null, scalar, or list subtree fails; an empty map succeeds. Domain settings remain invocation-only. Do not duplicate binding in preparation, admission, and invocation.
- Preparation overlays the complete candidate namespace on captured foreign metadata. It performs no application execution, retains no foreign runtime resources, and does not permanently bind foreign executor configuration/policy into the caller generation. Publication retains the target-base check and atomic installation; foreign changes after preparation do not add publication-time revalidation.
- Admission traverses root-reachable callable skills and their executor dependencies, selecting and retaining one coherent generation per needed namespace. Preserve capture under the existing registry ownership mechanism and expensive validation outside its monitor. Retaining an executor namespace neither traverses unrelated skills/dependencies nor grants callable authority over them; executors never become callable children/tools.
- Invocation uses only the captured executor registration and selected configuration subtree. Construct `SkillExecutorInvocation` from validated/reference-resolved input, the executing YAML skill's configuration and canonical qualified name, executor configuration, and the executor namespace's generation ID even when no config path is declared. Never consult live registrations or independently select configuration during retained execution.
- Fixed executors follow fixed SkillMethod lifecycle: replacement includes registrations with newly supplied configuration; removal blocks fresh dependent admissions while retained work finishes; explicit recreation restores assigned registrations with new configuration. Removal does not destroy Spring beans. Use existing ownership/retirement notifications; add no resource preparation/cleanup hooks. Application/Spring owns resources and shutdown.
- Route both Java forms through one Java execution branch and shared invocation/lifecycle, preserving reference input handling, authorization hooks, observations, cancellation, and limits. Their argument construction differs; no executor engine is introduced. Executor leaves classify as public `SkillKind.JAVA` and retain their YAML definitions. Separate execution classification from declaration provenance in generation/metadata/routing; PR 28.6 completes diagnostic wire/UI provenance.
- Delete `RestSkillHandler`, `RestSkillInvocation`, `SkillKind.REST`, handler discovery/count checks, wiring, and obsolete execution branches. Remove their allowlist entries and update supported-surface tests, README API summary, and repository guidance that calls REST the sole supported SPI. No additional public SPI or internal bean-replacement surface is selected.
- After REST removal, assess `CapabilityKind` against `SkillKind`. If it remains only a one-to-one translation, remove it and use one execution classification. Retain it only with concrete evidence of distinct required behavior and an explanation of why one classification is insufficient; do not expand it to encode declaration provenance. Update affected production callers/tests directly.

## Acceptance criteria

- [ ] Same/cross-namespace executor leaves execute through captured generations; missing references, forbidden namespace access, malformed/missing/non-map config paths fail at the specified boundaries. Executor-only namespace startup and same-name cross-role coexistence work without executor callability.
- [ ] Replacement/removal/recreation tests distinguish fresh and retained admissions and verify executor configuration/generation identity, fixed registrations, resource retention/retirement, and unchanged Spring bean ownership. No live lookup or foreign configuration copying controls retained execution.
- [ ] A foreign dependency change between preparation and publication leaves target-base publication semantics intact; fresh admission validates the newly selected graph while previously retained work remains coherent. Metadata preparation neither executes application code nor retains foreign resources.
- [ ] Root-reachable capture does not validate/traverse unrelated skills merely because their namespace is retained, and executor retention grants no unrelated callable authority.
- [ ] Both Java forms use shared routing; executor-backed YAML definitions survive generation validation/lookup with `SkillKind.JAVA`. The invocation carries only the agreed input/configuration/identity data with resolved resource leaves preserved.
- [ ] REST API/runtime/kind support is removed, API/guidance changes are documented, and the mandatory `CapabilityKind` assessment results in removal if redundant or concrete recorded justification if retained. Run `LoomspanPublicSurfaceArchitectureTest` for changed production types.

## Context and sequencing

Depends on [PR 28.2](loomspan-pr-28.2-executor-declarations-and-configuration.md), including PR 28.1. Owns finding 1, finding 6's generation ownership, finding 8's remaining REST removal, and finding 5's Framework execution classification in [the accepted design](../phases/configured-java-skill-executors.md). Read the [feature design lens](../framework-feature-design-lens.md). PR 28.4 supplies shared composed authorization; PR 28.6 updates the diagnostic catalog protocol. Do not add temporary per-boundary checks or compatibility paths to bridge those tickets.

Source recheck: `SkillGenerationManager.capture` follows only `allowedSkills`; `SkillDependencyValidator` already shares metadata graph checks across preparation/admission; `SkillGeneration` rejects Java definitions under current kind assumptions. `CapabilityKind` currently maps one-to-one to `SkillKind`. These internal shapes are replaceable. Keep this ticket cohesive around generation ownership and captured execution rather than introducing a second resolver to minimize edits.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Namespace concurrency/retention, authority boundaries, supported API removal, and shared routing are central cross-cutting changes.
- **Reassessment triggers:** Concrete evidence that the accepted two-role generation model cannot meet a committed requirement; implementation difficulty alone is not a reason for another subsystem.

## Pipeline notes

- Breaking API/SPI/manifest/runtime changes and broken intermediate builds are explicitly authorized; Framework and Sidecar have no customers or deployments. Add no compatibility shims, deprecated REST value, migration reader, or fallback. Sidecar adaptation does not gate deletion.
- Later tickets finish authorization composition and Console integration. Record any blocked verification precisely; do not retain obsolete architecture merely to keep intermediate commits green. The series must finish with working builds and all required checks passing.
