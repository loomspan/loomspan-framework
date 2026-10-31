# PR 27.3 — Admit roots against retained reachable namespace snapshots

> Superseded input rule: [PR 27.6](loomspan-pr-27.6-optional-namespace-references.md)
> permits local host input in `default` and owner-relative local authored references.
> Its boundary rules supersede conflicting mandatory-qualification or rejection
> requirements here. Internal identities, catalog lookup, model/runtime selections,
> access, publication and observation contracts remain unchanged.

## Outcome

Make invocation and queued handoff stable across publication/removal by capturing exactly the namespace generations needed by the entry skill's declared reachable graph. Root validation checks the same dependency rules without reserving execution.

## Requirements

- Under the publication/removal synchronization boundary, traverse every declared child of the entry skill transitively, including optional branches, forwarding, and bindings under existing graph rules. Track visited skill identities separately from selected namespaces: visit each skill once, select each reached namespace generation once, and still validate every required reference edge. Shared dependencies/cycles terminate traversal without skipping another reachable skill in an already captured namespace. Existing contract cycle/depth rules remain.
- Capture an immutable authoritative namespace-to-generation association including captured visibility. Acquire one lease per selected generation before leaving synchronization; release partial capture on failure. Retain whole generations, but neither traverse unrelated skills nor retain unrelated namespaces. Java skills remain leaves. No role-based pruning, branch prediction, dependency cache, reverse index, or per-skill versioning.
- Validate reachable existence, source-to-target visibility, contracts, forwarding, bindings, and planning requirements outside the registry lock before any skill executes. Same-namespace access passes visibility; cross-namespace targets must be captured Public. A broken reachable optional branch fails admission; a broken unreachable skill does not. Preserve entry RBAC without eagerly authorizing all reachable children.
- Reuse preparation contract resolution/validators against captured targets. Preserve published prepared derived contracts and compare recomputed schema structures, not raw text or dependency revisions. Formatting/object-key order does not change a contract. Changed fully forwarded output, including an added field and multi-hop forwarding, requires caller republication. Revalidate declared constraints: unrelated child output additions and ordinary child schema changes may pass if constraints and caller-derived contracts still hold. No historical copies of every dependency schema, general compatibility engine, execution-specific schema substitution, or mutated catalog metadata.
- Attach the immutable snapshot and aggregate ownership to admitted invocation/handoff and existing execution binding. All future resolution uses it; never dynamically attach a newer namespace generation. Snapshot membership grants no permission to call unrelated skills. Expose entry generation ID through `AdmittedSkillInvocation.generationId()`, without a public dependency-map API or accessor ownership side effects.
- Failed admission, abandoned handoffs, completed work, and rejected nested-root execution release ownership through existing lifecycle rules. A timeout response must not release resources still owned by physical work. Already admitted/queued invocations retain old generations and visibility after replacement/removal, including overlapping recreation; new affected admissions fail while unrelated entry paths remain available.
- All `SkillTemplate` invocation/validation/observer overloads and `SkillInvocationHandoff` arguments accept the qualified identity through the shared parser at the operation boundary, including namespace normalization and both size limits. Cover both input forms. Capture authentication at existing boundaries. Missing namespaces fail without creation; descriptor names pass directly to operations. No namespace views, separate namespace arguments, or unqualified overloads.
- `SkillTemplate.validate()` reuses dependency capture/validation with temporary ownership, checks input and entry RBAC, then releases on success/failure. It neither registers an admitted root nor reserves execution, invokes skills/providers, or returns a reusable snapshot. Successful validation can be followed by failed invocation after state/authentication changes. Catalog-driven invocation/handoff always performs fresh admission.
- Preserve nested root execution rejection, including admitted-root execution inside an active scope; do not reinterpret it as a child call. Do not add blanket bans on validation or handoff preparation inside an active execution. Host authorization of entry remains the host's responsibility; Private does not prevent authorized direct entry.
- Root namespace selection comes only from the qualified host API argument, never a namespace override in invocation input or model-generated arguments. Do not add membership policy or an authorization SPI. Private visibility is independent of entry RBAC; Public does not imply anonymous entry, management rights, or database access. Each actual root execution creates a fresh session through existing root machinery.
- Admission and validation lease already constructed generations; they do not construct provider clients or another runtime. Their consistent framework snapshot is not a transaction over external systems. Retaining a whole reached generation may retain unused resources, and traversal/lease acquisition may contend under the shared boundary; these are accepted costs, not authorization for caches, per-namespace locks, or new resource accounting.
- Inventory existing derived metadata and reuse its representations/validators; do not introduce a separate dependency-assumption tracking subsystem or generic schema-normalization mechanism. Published catalogs continue describing prepared contracts even when current dependencies make an entry inadmissible. Root validation uses the same captured checks as invocation, but its success can be invalidated by later namespace visibility/existence, authentication, or framework lifecycle changes. Callers needing reserved admission use the existing handoff API.

## Acceptance criteria

- [ ] Capture handles same-namespace multiple skills, optional and role-restricted branches, shared children, and cycles while retaining exactly reachable namespace generations. Unreachable broken dependencies do not block entry; reachable missing/Private/incompatible targets reject before execution.
- [ ] Publication/removal racing capture yields a coherent view with leases acquired before retirement. Partial failure releases acquired ownership; contract validation outside synchronization permits publication to progress.
- [ ] Dependency-only changes after preparation can publish, but admission rejects altered caller-derived contracts or invalid constraints; compatible changes pass. Cover multi-hop/additive fully forwarded output, equivalent schema formatting/order, unrelated added bound-child output, and ordinary child calls without schema pinning.
- [ ] Later replacement/removal/visibility change/recreation cannot alter an admitted or queued invocation's snapshot. Abandonment, failure, completion, and timeout-with-running-work obey ownership/retirement semantics. Generation ID access stays entry-scoped and side-effect-free after release.
- [ ] Every root operation accepts canonical descriptor names, default/non-default targets, namespace case normalization, and both input forms, while rejecting invalid/unqualified/oversized targets and absent namespaces. Concurrent roots and per-operation authentication remain isolated.
- [ ] Validation checks input/root RBAC and the same dependency policy as admission, invokes no skills/providers, reserves no root, and releases temporary leases on all outcomes. A role-restricted optional child does not introduce eager child RBAC. A later dependency change can invalidate a previously validated invocation.
- [ ] Nested-root execution remains rejected with cleanup; validation/handoff preparation are not newly prohibited. README and supported-surface tests document fresh admission versus temporary validation; run `LoomspanPublicSurfaceArchitectureTest`.
- [ ] A catalog retained across replacement does not pin later invocation/handoff; those operations capture the new active generations, while validation uses and releases its own temporary view. Exercise all root overloads, including observer invocation, after replacement/removal/recreation. Input data cannot override the qualified root target.
- [ ] With valid input and entry RBAC, broken reachable dependencies reject root validation, broken unreachable dependencies do not, and validation/invocation agree against unchanged state. A role-restricted optional child remains in reachability without eager child RBAC. Neither validation nor admission creates a provider runtime/client.
- [ ] After caller preparation, independently exercise dependency deletion, incompatible contract change, and Public-to-Private change: caller publication succeeds if its target base still matches, affected new validation/admission fails before skill execution, unrelated entry paths remain admissible, and already admitted work retains its snapshot. Admission never mutates published catalog contracts.

## Context and sequencing

Depends on PR 27.1–27.2. PR 27.4 completes child dispatch, context switching, and runtime authorization using this snapshot; PR 27.5 completes observation consumers and series verification. The accepted [design](../phases/skill-namespaces.md), particularly Entry-skill reachability, Stable prepared contracts, Root operations/validation, and Removal acceptance, is the detailed contract.

Reuse current execution binding, ownership, and validators rather than a new execution engine or authorization subsystem. Supported API stays closed and does not expose internal snapshot types. Cloud readiness/drainage, new deadlines, Java child APIs, and resource measurement are excluded; measurement is [post-implementation work](../phases/skill-namespaces-followup.md).

The accepted design and its acceptance coverage are normative, including distinctions omitted from a short ticket summary. Reuse PR 27.2's contract/visibility policy rather than independently implementing admission policy. A declared reachable callback from Public pricing into Private orders already makes that entry inadmissible; PR 27.4 must also independently deny an attempted forbidden callback at dispatch. Tests must not weaken admission to force an invalid graph to execute. PR 27.3 owns snapshot coherence, retention, root authorization, and admission checks; PR 27.4 owns context switching and child role enforcement, and PR 27.5 owns trace serialization/Console consumers.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Changes admission authorization, concurrency, lifecycle, and public root-operation behavior.
- **Reassessment triggers:** Reassess if scope materially changes; current derived metadata and physical-work ownership must be researched during planning.

## Pipeline notes

- Destructive API changes are authorized without compatibility shims or alternate identity formats.
- The developer reviews and commits each ticket; leave changes uncommitted and do not sync. Intermediate downstream breakage is allowed; record exact verification blockers, do not conceal them with bridges or claim passing checks. PR 27.5 closes all remaining integration gaps.
