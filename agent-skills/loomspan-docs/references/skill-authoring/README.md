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

The consumer is an application developer and their AI assistant, including when this package is installed outside the framework repository. Framework contribution and release engineering are separate workflows.

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
| Bind parent inputs/results or determine automatic assigned dispatch | [input-bindings.md](input-bindings.md) | [planning-concurrency.md](planning-concurrency.md); [output-contracts.md](output-contracts.md) for forwarding |
| Design input contracts or diagnose model handoff quality | [input-contract-design.md](input-contract-design.md) | [input-contracts.md](input-contracts.md) for Java reflection; [executor-skills.md](executor-skills.md) for executor manifests |
| Design or diagnose reflected Java skill inputs | [input-contracts.md](input-contracts.md) | [mental-model.md](mental-model.md) for declaration ownership |
| Choose synthesis, full/mixed assembly or designated-child forwarding; design or diagnose output contracts | [output-contracts.md](output-contracts.md) | [evidence-contracts.md](evidence-contracts.md) for supportability or [traces-and-debugging.md](traces-and-debugging.md) for retry diagnostics |
| Declare an annotation-only Java skill or reference-only executor; inspect Java results/failures | [Java skills](../java-api/java-skills.md) | [input-contracts.md](input-contracts.md) |
| Declare an executor leaf or supply executor configuration | [executor-skills.md](executor-skills.md) | [Java declarations](../java-api/java-skills.md); [publication](../java-api/skill-reload.md) |
| Declare Choice, Noul, or Score evaluation leaves through TypeSafe or OpenRouter | [typesafe-skills.md](typesafe-skills.md) | [input-bindings.md](input-bindings.md); [output-contracts.md](output-contracts.md) for parent composition |
| Configure Java/YAML roles or diagnose authorization | [authorization.md](authorization.md) | [mental-model.md](mental-model.md) for local visibility |
| Add evidence-backed output claims | [evidence-contracts.md](evidence-contracts.md) | [mental-model.md](mental-model.md) |
| Declare or diagnose generated-plan task counts | [planning-task-constraints.md](planning-task-constraints.md) | [evidence-contracts.md](evidence-contracts.md) when output supportability is also required |
| Diagnose missing planner facts or citations | [complete task evidence](planning-concurrency.md#complete-task-evidence) | [evidence-contracts.md](evidence-contracts.md) for name-only supportability |
| Configure planning concurrency or diagnose task grouping/dependencies | [planning-concurrency.md](planning-concurrency.md) | [planning-task-constraints.md](planning-task-constraints.md) when child task counts are also constrained |
| Select a model or configure its connection | [model-selection-and-connections.md](model-selection-and-connections.md) | [mental-model.md](mental-model.md) |
| Diagnose step-action correction, retries, usage, terminal failures, or a nested runtime path with the packaged Agent Skill `loomspan-console` | [traces-and-debugging.md](traces-and-debugging.md) | The relevant validation or evidence topic |
| Validate a complete draft YAML skill set before staging | [validation-workflow.md](validation-workflow.md) | [Java skill reload API](../java-api/skill-reload.md) for application integration |
| Configure execution budgets or diagnose shutdown | [execution-limits.md](execution-limits.md) | [invocation](../java-api/invocation.md) for pending admissions |
| Diagnose observability HTTP or trace artifact transfers | [observability-http.md](observability-http.md) | [traces-and-debugging.md](traces-and-debugging.md) for interpretation |
| Resolve ambiguity or an edge case | [source-verification.md](source-verification.md) | The topic's implementation anchors |

Do not load every document by default. Start with the routing entry most relevant to the developer's goal and expand only when the task crosses another documented concern.

## Coverage

| Topic | Coverage | Notes |
| --- | --- | --- |
| Skill-tree mental model | Initial, source-verified | Shared Java/YAML identity, immutable reachable snapshots, complete publication, temporary validation, root capture, local visibility, lifecycle, and nesting |
| Shared skill identity | Preparation/publication source-verified | Local declarations, owner-relative authored references and default host shorthand compiled to qualified identities (NamespaceIdentityFoundationTest, SkillReachableAdmissionTest, NamespaceStartupIntegrationTest), strict catalog/model/runtime references, namespace/combined limits, parsed bindings, duplicates, target feedback and content fidelity; active namespace registry, Private startup and removal are implemented; reachable root admission is covered by SkillAdmissionSnapshotTest and SkillReachableAdmissionTest; captured runtime authority/settings and model dispatch are covered by NamespaceAccessGuardTest and NamespaceExecutionAuthorityIntegrationTest; captured entry/target generation facts and existing Console contract consumers are covered by NamespaceObservationIntegrationTest and the shared semantic fixture corpus |
| Evidence contracts | Source-verified | Immediate-root property annotations, strict scalar/placement rules, Boolean direct-child expressions, planning/final truth sets, metadata isolation, enforcement, nested mission isolation, and separation from complete result delivery and factual correctness |
| Source verification | Initial | How an LLM should use guide, tests, fixtures, samples, and production code together |
| Skill-design review | Initial | Cross-cutting questions; not a manifest validator |
| Declared child input bindings | Source-verified | Exact object pointers, receiving/projected contracts, requiredness, strict transfer, direct producer dependencies, isolation, automatic conservative fully bound/zero-input dispatch, optional/nested/unsupported fallback, contract-specific required/optional/Framework-owned prompt guidance with neutral unknown-shape summaries, model/Java/executor child reasoning and provenance; no expressions or aggregation |
| Input-contract design | Design guidance | Required data, nested open/closed boundaries, model guidance, source fidelity limits and caller compatibility; not a complete schema-language reference |
| Executor YAML/configuration | Source-verified | Explicit forms, immutable input/skill/executor configuration, exact dot-path binding, same/foreign namespace visibility, generation-owned fixed registrations, executor-only startup, captured execution and retained replacement/removal/recreation (`ExecutorDeclarationTests`, `ExecutorConfigurationTests`, `ExecutorConfigurationStartupTests`, `ExecutorDependencyResolutionTest`, `ExecutorGenerationExecutionIntegrationTest`). Composed YAML/Java authorization and worker advice boundaries are covered by `SkillRoleEvaluatorTest` and `SharedDeclarativeAuthorizationIntegrationTest`. Executor registrations confer no callable authority; `rest:` is unsupported syntax. `DefaultRegisteredSkillCatalogTest`, `ExecutorGenerationExecutionTest`, `ConsoleRestFixtureCorpusTest` and Console `TestAllCallableFormsPreserveProvenanceAndRejectInvalidContracts` cover JAVA execution with YAML declaration/source, executor identity and implementation. |
| TypeSafe evaluation leaves | Source-verified | Complete two-route setup and alias switching; fixed/dynamic mixed batches; scoped substitution and errors; descriptive schema and receiver binding checks; direct invocation, selected-answer assembly and unchanged forwarding; transport/cancellation/billing limits; native catalog/Console/MCP evidence and capture state; published examples exercised on deterministic fixtures, no live accuracy claim |
| Java declarations, results and failures | Source-verified | [Java API topic](../java-api/java-skills.md) owns shared callable/executor discovery, proxy/security requirements, exact string/null/JSON results, five-component defensive-copy invocation and designated exceptions; `SkillExecutorTest`, `SharedJavaDiscoveryInvocationTest` and `SupportedSurfaceIntegrationTest`. Captured YAML executor execution and secured retained handoff are covered by `ExecutorGenerationExecutionIntegrationTest`; actual argument advice and worker restoration for both Java forms by `SharedDeclarativeAuthorizationIntegrationTest`; designated standard feedback, direct/step termination and exact messages are covered by `ExplicitJavaFailureHandlingIntegrationTest`; no automatic replay or step correction loop. |
| Input contracts | Initial, source-verified | Reflected Java `Object`, generic and typed maps, DTOs, arrays, requiredness boundaries, shared root/child reflected contracts, validation, scoped object guidance and path-associated authored descriptions in compact/verbose/corrective requests, and illustrative required/optional examples; declared bindings use separate projected argument contracts and exact full receiving validation; complete pure-YAML schema syntax remains undocumented |
| Output contracts | Source-verified | Supported `output_schema` vocabulary, normalization, validation, retries, model guidance; strict `output_from`, declared `output_bindings`, exact full/mixed assembly, projected ownership, provenance, exact full-work completion, model/Java/executor examples and chains, stable prepared metadata and fresh structural admission checks (SkillAdmissionContractsTest), unspecified schema and ownership limits |
| Prompts | Focused, source-verified | Recursive output-contract guidance and [planning objective fidelity and assigned-action boundaries](planning-concurrency.md#execution-behavior) are covered; general private prompt composition still needs a dedicated topic |
| Planning and nested planning | Focused, source-verified | Task counts, concurrency applicability, exact grouping, ordered units, direct-result dependency guidance despite ordering barriers, coordinator assignment, automatic contribution-free dispatch with unchanged step/admission limits, exact worker correction, synthesis/forwarding/full-assembly/mixed-assembly completion and step costs, unconditional output-producer uniqueness without artificial dependencies, enabled-group fork/join, serialized opt-out, task-ordered folding, mission-wide cutoff cleanup, hierarchical late-write fencing, diagnostic isolation, complete task-keyed prior-unit/final results, nested returned-only boundaries, declared child input bindings alongside explicit unbound arguments, and reference limitations are covered |
| Capability visibility and RBAC | Focused, source-verified | JSR-250 class/method/interface policies, required enforcement, role prefix/hierarchy parity, local allowlists, captured foreign visibility, entry RBAC without eager child checks, scoped caller authentication, YAML AND captured executor policy (`SkillRoleEvaluatorTest`, `ExecutorDependencyResolutionTest`), metadata-only checks and actual dispatch rechecks (`SharedDeclarativeAuthorizationIntegrationTest`); expression advice remains invocation-only |
| Attachments and virtual files | Not yet documented | Requires separate source verification |
| Model selection and connections | Focused, source-verified | Framework model aliases, named connections, thinking levels, exact provider-call budgets/range/defaults and mission/retry distinctions, environment and host-map credential references including Vertex JSON, generation capture, rotation, migration, diagnostics, and complete OpenRouter response acceptance with separately bounded error evidence |
| Observability HTTP diagnostics | Source-verified | Canonical names, captured namespace-generation metadata, existing unfiltered host inspection and unchanged authorization; ObservabilityRestIntegrationTest, ObservabilityArtifactIntegrationTest, ObservabilityHostSecurityIntegrationTest and ConsoleTraceFixtureCorpusTest |
| Execution limits and quotas | [Focused, source-verified](execution-limits.md) | Startup defaults, complete candidate session settings, entry-scoped shared quotas/depth/trace, current target provider/mission/attachment settings, captured provider-attempt quota and run-start diagnostic comparison |
| Traces and debugging | Source-verified | Authoritative Framework/model dispatch identity and reasons without fictitious request frames; declared binding provenance with raw-model/effective-input separation; authoritative designated-task forwarding, validated output assembly, exact source ownership and explicit skipped synthesis, and schema inspection, mission-input null fidelity with unchanged business validation, complete latest-candidate replay for schema and step-action correction with separately bounded parser/validation feedback and explicit provider/resource failures, unchanged invalid-action allowance, thirteen-tool discovery, current runtime status, visible complete live branches with exact snapshot simultaneity, authoritative MCP whole-plan queries, assignment-aware canonical timeline rows, admission/join events plus accepted-order plan projections, inline same-plan status/note comparisons with exact nullable values and explicit bounded/unavailable evidence, exact nullable observed-overlap semantics, independent finalized/acquired/imported discovery, compact/detailed frames and records, descriptor/inline/exact semantic reads, lossless byte-budgeted traversal, usage/failures, deliberate raw forensics, and current-run/transport limitations |
| Testing skill trees | Focused validation workflow | [validation-workflow.md](validation-workflow.md) covers complete-set checks and candidate name/kind metadata; runtime execution testing remains undocumented |

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

Publication coverage: complete namespace management, captured metadata preparation, Private startup and removal/recreation are implemented and covered by `SkillReloaderTest`, `SkillDependencyValidatorTest` and `NamespaceStartupIntegrationTest`. Reachable snapshot/admission and temporary validation are covered by `SkillAdmissionSnapshotTest`, `SkillAdmissionContractsTest`, and `SkillReachableAdmissionTest`. Captured runtime authority/settings and canonical model selections are covered by `NamespaceAccessGuardTest`, `NamespaceSettingsTest`, and `NamespaceExecutionAuthorityIntegrationTest`. Captured namespace observation metadata and existing Console consumers are verified through NamespaceObservationIntegrationTest, focused HTTP/trace tests and ConsoleTraceFixtureCorpusTest; runtime inspection retains its existing authorization and retention.

Failure diagnostics coverage: `ExplicitJavaFailureHandlingIntegrationTest` covers both Java roles through direct, actual standard-tool, and explicit-step execution. `LoomspanSessionTest` and current Java/Go diagnostic fixtures cover classification, exact exception text, transparent identity and application-wrapper cause linkage.
