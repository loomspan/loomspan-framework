# Skill Namespaces

## PR 27.6 boundary correction

[PR 27.6](../tickets/loomspan-pr-27.6-optional-namespace-references.md) supersedes
conflicting mandatory-input-qualification requirements in PR 27.1–27.5.
Qualification is optional for authored structured references and host entry;
internal identities, catalog lookup, model selections, REST and observations
remain canonical and qualified. Other namespace contracts remain unchanged.

## Status and purpose

Prepared on October 28, 2026, from the Loomspan Cloud design discussions in the sibling `meta-hackathon` repository.

Design consistency review completed on October 29, 2026. Implementation and resource measurements have not been performed by this review.

This document develops skill namespaces with independent atomic publication and Public/Private skill-call visibility. The wanted [optional implementation-specific Java skill configuration feature](optional-javaskill-configuration.md) is preserved in a separate document for discussion after namespace design is complete. It is outside this namespace implementation scope, not rejected.

This is an accepted design and research handoff, not a completed implementation plan. Remaining implementation work consists of the integration details below. All new resource measurement, including selection of scale targets and acceptable thresholds, is deferred until after implementation in [Skill Namespaces Follow-up](skill-namespaces-followup.md); it is not a prerequisite or acceptance gate for PR 27. Ticket creation has been requested separately; this handoff does not itself authorize implementation.

The original configuration and namespace discussions did not inspect their corresponding framework implementation paths. Subsequent framework discussion inspected generation publication, execution binding, lookup, authorization, and Java skill registration. Findings and accepted decisions are recorded below; the proposed namespace behavior is not yet implemented. The earlier identity investigation and its limits are recorded separately.

The document separates [design contracts](#namespace-design), [remaining integration work](#implementation-integration-still-to-resolve), [acceptance coverage](#acceptance-coverage), and [prior identity findings](#existing-identity-findings-relevant-to-both-proposals). The [post-implementation follow-up](skill-namespaces-followup.md) defines resource measurement required before considering optimization.

## Product context and responsibility boundary

### Scope and design principles

Implement generic namespace boundaries in Framework and update existing Console consumers as specified in the release-compatibility contract below. Cloud implementation and new Console features are outside scope.

Choose the simplest complete solution and reuse generation preparation/publication, ownership leases, execution bindings, access guards, and existing configuration/validation. Do not introduce a new execution engine, namespace-specific Spring container, separate authorization subsystem, or speculative abstractions. Destructive changes are accepted; the public-surface rules below still apply.

The contracts below are authoritative. Concurrent detached preparation and independent generation-owned runtimes remain selected; optimize resources only when measurements demonstrate a need. Concrete integration types and signatures remain implementation-planning work.

### Accepted deployment patterns

Namespace support must preserve the simplest embedded application experience while supporting API-managed deployments. These are usage patterns of the same framework, not separate runtime modes or configuration systems.

| Deployment pattern | Intended behavior |
|---|---|
| Embedded application shipped through ordinary application deployment | Startup skill files and Java skills without an explicit namespace belong to Private `default`. Developers invoke local `search` or qualified `default-search` using existing startup configuration, without an extra publication. |
| Cloud platform hosting many clients in one framework instance | The platform supplies skill declarations and execution configuration from its repositories through framework publication APIs; local skill files and execution-configuration files are not required. Hard-coded Java skills provide shared capabilities in assigned namespaces. Those namespaces start Private and the platform publishes them as Public, with complete execution configuration, before enabling cross-namespace use. Client namespaces are also populated through the APIs. |
| Departmental deployment with application-defined Java capabilities | Application code assigns fixed Java skills to known departmental namespaces. A department later publishes its SkillPack and complete execution configuration into its namespace, automatically retaining those fixed Java registrations. Same-namespace composition works while Private; sharing with other namespaces requires Public target visibility. |

Java namespace assignment is the exception to placing startup skills in `default`, not an exception to Private startup visibility. A departmental namespace can remain practically unused until its host exposes it and its SkillPack is published; no inactive-registration state or activation API is required. Java skills remain available through host-authorized direct entry under the existing startup rules.

Managed hosts own publication sequencing and application readiness, including repeating their publications after restart. Cloud repository integration, deployment infrastructure, and orchestration remain outside framework scope. Cross-namespace composition directly from startup skill files is not a requirement of these deployment patterns and does not justify additional startup machinery.

### Original motivating context

Loomspan Cloud is intended to let users build, share, and operate persistent micro apps through their AI companions. Companions execute the app's instructional skills and perform its reasoning. Cloud hosts app definitions, provides authenticated access, and invokes hosted capabilities for persistence and deterministic validation.

Cloud will host Loomspan Framework directly, rather than run through loomspan-sidecar. Sidecar code can provide hosting examples; it is not the intended runtime dependency for Cloud.

The framework serves consumers beyond Cloud. Both enhancements should therefore be generic: the framework need not understand customers, departments, apps, Supabase schemas, or Cloud sharing strategies.

| Component | Responsibility |
|---|---|
| Loomspan Framework | Generic skill execution, configuration delivery, namespace boundaries, publication consistency, authentication propagation, and existing authorization controls. |
| Hosting application | Assign business meaning to namespaces; control management and publication permissions; authenticate and route external requests. |
| Loomspan Cloud | App ownership and membership, authorized app/environment context, lifecycle, publication workflow, and Cloud-specific sharing behavior. |
| DataSkill Java implementation | Interpret data definitions and named operations, perform deterministic validation and database operations, and enforce the selected data strategy using trusted Cloud context. |
| Build instructions and companion | Help author app definitions and operations through the supported publication workflow. |

## Namespace design

### Motivation

Source inspection confirmed that current dynamic updates replace a complete global skill generation atomically. Current preparation overloads can reuse active execution configuration when configuration is omitted. The selected namespace design instead requires an explicit complete publication, including all applicable configuration, with no merging or reuse of omitted configuration from a previous publication.

Namespaces are intended to support independently managed sets of skills and associated configuration in one running framework. Updating one namespace should not replace unrelated namespaces' definitions. This is also useful for a company separating departmental skills within one deployment.

### Namespace boundary

The framework assigns no business meaning to namespaces. They isolate definitions, configuration, visibility, and publication, but do not establish complete tenant isolation. Credential isolation, database access, Java mutable state, and resource consumption require their own controls. Namespace visibility grants neither configuration nor observation access.

No reserved Public/shared namespace, cross-namespace enablement switch, or framework-prescribed publisher role is selected. Hosting applications control management permissions and external request routing. Publication, execution authority, and observation contracts follow below.

### Identity rules

- Use one canonical internal identity: `namespace-skillName`, including `default-search`. At host entry, local `search` resolves to `default-search` regardless of ambient execution. In authored references, local `search` resolves against the containing declaration's startup/publication owner; explicit qualified references keep their meaning.
- Namespace identifiers and local declaration names contain ASCII letters, digits, or underscores, starting with a letter or underscore. Hyphens are forbidden in both components, so a qualified reference contains exactly one hyphen separator. Colons, slashes, whitespace, empty components, and additional separators are invalid.
- Normalize namespace names to lowercase through the shared identity parser at every input boundary, including authored references, host arguments, and model-generated plan/step/tool selections. Preserve case-sensitive local skill names. Semantic identity fields, comparisons, displays, and outgoing tool definitions use the canonical spelling; raw diagnostic content remains unchanged. `Tax-calculateTax` normalizes to `tax-calculateTax`; `tax-CalculateTax` identifies a different skill. Do not trim whitespace or replace invalid characters.
- The entire qualified identity must be at most 64 ASCII characters, including the separator. Apply this rule to every skill at Java registration, startup loading, and publication validation/preparation, and to every supplied reference. There are no exceptions for host entry, Java/REST skills, same-namespace calls, or skills not currently offered as model tools. Namespace identifiers are limited to 32 ASCII characters, with canonical grammar `[a-z_][a-z0-9_]{0,31}` after normalization. Enforce the namespace limit in management operations, Java registration, and every qualified reference, independently of the combined limit. A 32-character namespace leaves 31 characters for a skill name; `default` leaves 56.
- A supplied declaration's `name` and `@SkillMethod.name` remain local definition components, using `[A-Za-z_][A-Za-z0-9_]{0,63}`. Publication supplies declaration ownership; `@SkillMethod.namespace` supplies Java ownership and defaults to `default`. Construct and validate the canonical identity from these components. Declaration components remain local. Local reference input is accepted only at the host and authoring boundaries and compiles to the existing canonical identity.
- Authored allowed skills, `output_from`, child-result input/output binding producers and evidence leaves may be local or qualified. Resolve them in shared startup/API preparation before comparisons, dependency validation and executable construction. Compile evidence identifier leaves using owner context without changing grammar, original expression text or token positions. Preserve original YAML bytes and prose. Catalog descriptors/lookups, capability names, plans, provider tools, REST handoffs, traces, metrics and diagnostic skill identities remain qualified.
- Use one internal identity parser/formatter and structured identity at resolution and authorization boundaries. Parse declarations/references during preparation and host arguments at operation entry; avoid repeated string splitting during execution. Compare resolved identities for duplicate detection and binding validation.
- Beyond the two input boundaries, do not introduce runtime shorthand, aliases, imports, wildcards, fallback lookup, ambient-caller-relative names, provider-name encoding, or alternate qualified formats. Missing local targets fail in their resolved namespace even when another namespace has the same local name. Compare canonical identities for duplicate detection and mixed-spelling binding validation; enforce combined length after qualification. Do not search Public namespaces implicitly or introduce namespace inheritance.
- Namespace renaming is creation of a new namespace followed by removal of the old one, not an identity-preserving rename operation. Explicit references to the old namespace must be updated; local authored references follow the new owner.

### Generation layering and identity representations

Keep `SkillGeneration` as the immutable executable generation of one namespace, with the captured execution snapshot above generations. Runtime capability names and generation lookup keys use canonical qualified identity. Update constructor invariants directly: capability keys equal `CapabilityMetadata.name()`, and definition keys equal the canonical identity constructed from generation ownership and the manifest's local declaration name. Do not preserve old local-key behavior through alternate lookup paths or compatibility shims.

The captured snapshot maps canonical namespace identifiers to their selected generations, with namespace identity and captured visibility available alongside each generation. Use one authoritative association, not independently mutable copies. Resolve a structured identity by selecting its namespace's captured generation and looking up its canonical skill identity. Validate generation ownership against the namespace component; a qualified name does not authorize access.

| Context | Representation |
|---|---|
| Declaration `name` and Java annotation `name` | Local definition component, combined with authoritative namespace ownership when constructing the skill identity; local input resolves only at the specified boundaries. |
| Parsed resolution and authorization | Internal structured identity with canonical lowercase namespace and case-sensitive local name. |
| Prepared skill references and runtime capability names | Canonical `namespace-skillName`, at most 64 characters after qualification, including same-namespace and `default` references. |
| Generation identifiers | Existing opaque IDs, unique across all namespaces within a framework instance and never reused, including after removal/recreation. Do not encode skill identity into these IDs. |

Bound child tools, plans, child-result matching, input/output bindings, catalogs, and execution records use the same canonical reference. Provider adapters transmit it unchanged; no provider-to-framework name translation is needed. Diagnostics without a known skill retain their existing unavailable/null behavior. Routes identifying something other than a skill retain their existing meaning.

**Execution binding:** carry access to the immutable captured snapshot, the entry namespace/generation, and the current executing namespace/generation. Entry-session quotas, maximum depth, and existing session-level trace settings use the entry context; current-skill definitions, provider settings, and mission timeouts use the current context. Derive or validate generation associations against the snapshot so a binding cannot pair a namespace with another namespace's generation.

For a child call, resolve the qualified target in the captured snapshot, authorize using the actual source and target namespaces and existing caller identity, then enter the target execution context. Perform source-to-target authorization before replacing the source context. Restore the caller context on success or failure, including asynchronous branch propagation. Keep the session, snapshot, authenticated caller, accumulated usage, and existing mission ancestry intact. A current-generation reference alone cannot replace namespace identity for authorization.

**Observation and API boundaries:** retain existing String fields where canonical identity expresses their meaning cleanly; there is no requirement to retype every mission, frame, metric, or Console DTO. Audit comparisons, joins, sorting, lookup calls, provider adapters, Java/REST handoffs, fixtures, and consumers for the changed semantics. Any session-level `generationId` remains explicitly entry-scoped; retain the captured namespace-to-generation information separately in existing execution/trace metadata. No new observation features are introduced.

Destructive changes are welcome. Update affected map invariants, signatures, public API documentation, allowlists where applicable, and supported-surface tests directly. Add no legacy readers, dual reference formats, or compatibility shims.

### Catalog and public generation-ID contracts

Reuse the existing immutable generation catalog. Select a namespace through the active namespace registry, then return that generation's catalog. Do not introduce a live catalog proxy, aggregate catalog, global catalog revision, additional cache, synchronization service, namespace-enumeration feature, or new catalog resource lifecycle.

| API | Selected contract |
|---|---|
| `SkillReloader.snapshot(namespace)` | Select the current generation of the normalized namespace at call time and return its immutable catalog. Missing/removed namespaces fail explicitly. |
| `SkillCatalog.namespace()` | Canonical lowercase namespace owning the catalog, including an empty catalog. |
| `SkillCatalog.generationId()` | Opaque ID of that one namespace generation. |
| `SkillCatalog.skills()` / `SkillDescriptor.name()` | Immutable metadata with canonical qualified skill identities. Retain unfiltered metadata semantics and deterministic name ordering. |
| `SkillCatalog.skill(name)` | Accept only a valid qualified reference and look it up within this immutable catalog. An unknown identity, including one in another namespace, returns the existing empty lookup result. Never consult another catalog or current publication. Unqualified/malformed arguments are invalid. |
| `ValidatedSkill.name()` | Canonical identity of a skill in the checked namespace proposal. |
| `PreparedSkillUpdate.snapshot()` | Immutable catalog of the prepared candidate's namespace generation. |
| `PreparedSkillUpdate.generationId()` | That candidate generation's opaque ID. |
| `AdmittedSkillInvocation.generationId()` | The captured entry namespace generation's opaque ID, not an ID for the whole dependency snapshot. |

Remove the separately injected startup `SkillCatalog` bean. It is currently an intentionally immutable startup snapshot. Use `SkillReloader.snapshot(namespace)` as the single current-catalog acquisition path; hosts may retain the returned value. Add no no-argument alias or compatibility shim.

Catalog acquisition selects a generation once. A returned catalog remains unchanged after publication/removal and owns no executable provider-resource lease. Discovery is unfiltered host-facing metadata and does not authorize execution. `namespace()` identifies catalog ownership even when it contains no skills; descriptor names are independently qualified identities.

Hosts pass `descriptor.name()` directly to `SkillTemplate` invocation/validation or `SkillInvocationHandoff`, without selecting a namespace view or splitting the name. Catalog-driven invocation performs fresh admission and is not pinned to the catalog's generation. No new public identity type or duplicate local-name accessor is selected.

Validation feedback covers the checked namespace's supplied declarations and fixed Java registrations, not the entire dependency graph. Preserve existing validation-result behavior: validation does not allocate/reserve a generation, and errors prevent treating returned skills as a complete valid proposal. Qualified diagnostic skill names follow the common identity convention.

For entry `orders-placeOrder` calling `tax-calculateTax`, the admitted invocation ID identifies the captured orders generation; the REST handoff ID identifies the captured tax generation. Preserve ID availability after execution, failure, release, or cutoff and its no-ownership-side-effects semantics. ID access does not guarantee an application-owned mapping still exists. Keep the full captured namespace-generation map in internal execution and trace metadata; no new public dependency-map accessor is selected.

### Tool dispatch and reference validation

Provider adapters use the identity contract unchanged and validate any additional supported-provider constraints without renaming skills. For a namespace of length N (at most 32), the local skill name may use at most `63 - N` characters in that identity. Reject oversized identities without truncation, hashing, short handles, or host-entry exceptions.

**Dispatch and correlation.** Parse model-generated plan, step, and tool selections through the shared identity parser, normalizing only namespace case, then match the canonical identity against the exact bound tool surface. For example, `Tax-calculateTax` matches a bound `tax-calculateTax`; `tax-CalculateTax` does not match it. Normalization does not make an unbound target callable. Authorization uses the resolved target and actual source namespace; merely supplying a qualified identity grants no call permission. Audit planner prompts and outputs, ordinary tool calls, step execution, adapters, and result matching to remove local-name assumptions. Preserve raw model/tool diagnostic content; do not rewrite authored prompts or add translation between model names and accepted plan identities.

**Preparation validation.** Validate every candidate skill identity and declared tool surface, including optional children, without relying on runtime role filtering or predicted branches. Use the shared identity parser/formatter for names and uniqueness. Errors identify the declaration/reference, relevant field/source, combined length, and supported limit. Publication adds no second validation pass or dependency revision preconditions.

### REST handoff and retirement identity

Preserve the sole `RestSkillHandler` SPI and the existing `RestSkillInvocation` fields. Its `skillName` carries canonical `namespace-skillName`, its `input` remains the existing invocation arguments, and its `generationId` identifies the captured generation owning the executing REST skill. Do not add a redundant namespace field or a new REST routing subsystem.

For example, a session entering `orders-placeOrder` and calling `tax-calculateTax` delivers `skillName = "tax-calculateTax"` and the captured tax generation's ID to the handler, not the orders entry-generation ID. Equal local REST names in different namespaces are therefore distinguishable.

Reuse the preparation-time REST invoker closure: capture the qualified skill identity and that declaration's owning generation ID when constructing the namespace generation. Dispatch through a retained generation continues to deliver its captured identity and ID after a newer publication or removal. Do not consult the live registry during REST dispatch or derive target identity from caller-controlled input.

Keep generation IDs opaque and unique across all namespaces in a framework instance, with no reuse after removal/recreation. Preserve the existing manager-prefix/counter approach or an equally unambiguous internal generator; per-namespace publication does not imply independently reusable generation IDs. IDs remain process-local identifiers, not a durable restoration contract.

The existing `onGenerationRetired(Consumer<String>)` callback remains sufficient: its argument identifies exactly one generation, allowing existing host-owned resources keyed by generation ID to be cleaned up. No namespace field or replacement callback type is required. Preserve existing retirement ownership conditions, concurrency, listener failure isolation, and documented lack of replay, durable delivery, ordering, or retry guarantees, including possible omission during shutdown.

### Publication rules

- Each namespace has one active immutable generation containing its complete skill declarations, all applicable configuration, and Public/Private visibility.
- Every publication is a complete replacement. There is no partial patching, configuration merging, or implicit reuse of omitted configuration from a previous publication or another namespace.
- A configuration document is required with each publication, but every scalar setting need not be explicitly written. Preserve existing built-in defaults for settings that already support omission. Required fields and existing validation still apply. Effective settings are determined by the supplied document and built-in defaults, with existing explicit credential-reference resolution retained; omitted settings do not inherit values from the previous publication, another namespace, or a new host-configuration fallback mechanism.
- For example, if a setting has a built-in default of `64`, publication A sets it to `100`, and publication B supplies a configuration document omitting that setting, B uses `64`, not `100`. Applying built-in defaults during preparation is complete replacement, not merging with the previous publication. This does not authorize omission of the configuration document itself.
- Complete replacement applies to the supplied declarations and framework configuration. Every generation also includes the fixed Java registrations assigned to that namespace by application code, as current generations include fixed Java registrations. This is not merging with a prior publication: publishers cannot omit, replace, or relocate those fixed registrations.
- Publication uses the captured-base protocol in the management API contract below; there is no unconditional create-or-replace operation.
- Visibility changes are publications and create new generations.
- Visibility defaults to Private on every publication, including creation and replacement. Public must be explicit. Republishing a Public namespace with visibility omitted makes it Private; visibility is never inherited from the previous publication. Startup uses the same Private default.
- Atomic multi-namespace publication is outside the initial design. Coordinated changes require staged, compatible publications.
- Reuse existing generation preparation, atomic activation, stale-update detection, and ownership leases. Old generations remain available to already admitted work until ownership is released. Capture and activation share a synchronization boundary as specified below. Generation IDs are the revision tokens; accepted API shapes are specified below. Concrete snapshot and synchronization implementation details remain ticket work.

### Publication configuration boundary

Reuse the existing `ExecutionConfiguration` document and strict `ExecutionConfigurationParser` boundary. Complete configuration means complete publishable execution configuration, not the hosting application's entire configuration. No new configuration hierarchy, inheritance mechanism, or public configuration type is selected.

| Namespace publication accepts | Host configuration retains |
|---|---|
| `loomspan.connections`, including existing provider options, request timeouts, retry settings, and credential-reference resolution | Framework shutdown settings (`loomspan.shutdown`) |
| `loomspan.models` | Startup skill-document locations (`loomspan.skills`) |
| `loomspan.session`, including quotas, maximum depth, mission timeout, and attachment limits | Observation service enablement, authentication, and retention settings (`loomspan.observability`) |
| `loomspan.execution-trace.persistence` | Other application/Spring configuration |

Preserve the parser's existing accepted nested fields and validation. Host-only and other unsupported keys in a publication configuration are errors during validation/preparation, not silently ignored settings. A namespace publication cannot change host shutdown, startup document loading, or observation-service configuration. Trace persistence remains publishable execution policy and follows the captured entry namespace under the accepted settings rules; observation-service settings remain host-owned.

Under the complete-replacement rules, omitted connections and models are not inherited; declarations requiring a missing model or connection fail existing validation. Explicit credential-reference resolution does not establish general host-configuration inheritance. Startup initialization and the source of settings during execution are defined in their respective sections.

### Revision tokens and management API shape

Reuse the existing opaque, globally unique, never-reused generation ID string as the revision token. Add no separate revision type, revision counter, or catalog revision. Internally, a candidate carries its canonical target namespace, expected base generation ID (or absence), and its own new generation ID. Use a nullable internal base-ID string for absence; null means strictly absent, never a wildcard. `PreparedSkillUpdate.generationId()` continues to identify the candidate's new generation, not the generation it expects to replace. No new public base-revision accessor is required.

Preserve today's captured-base preparation behavior. Read the target base under registry synchronization before expensive preparation, alongside the coherent preparation metadata view. The caller does not supply an expected revision to preparation. If the namespace is absent at that point, the candidate is a creation; otherwise it is a replacement of that exact generation. Capture once; do not refresh the base after validation or before publication to make a stale candidate succeed. This protects against target changes after capture, not changes during an earlier application read/edit cycle. For example, an application can read generation 1, another caller can publish generation 2, and preparation begun afterward captures generation 2 and may successfully replace it. No additional optimistic-edit API is selected.

Keep `publish(PreparedSkillUpdate candidate)`. The candidate is the sole source of target namespace and expected base; add no namespace or expected-revision arguments to publication. Under registry synchronization, compare the captured base with the target's current state, enforce existing framework ownership, candidate one-shot/closed-state, and lifecycle checks, then activate atomically. Removal/recreation cannot make an old base match because IDs are never reused. Candidates prepared against absence compete for creation; after one publishes, another fails unless the namespace is absent again at its publication check. No tombstone or history-of-absence token is introduced.

Keep management operations explicit rather than adding `SkillReloader.inNamespace(...)`. Namespace-targeted validation and preparation take the namespace once along with the complete documents/configuration and applicable existing credential inputs; preserve the accepted visibility payload/default. `snapshot(namespace)` remains the single catalog acquisition path. Removal takes namespace and expected active generation ID as already agreed. `onGenerationRetired(...)` remains framework-wide, and `publish(candidate)` derives its target from the candidate. Remove obsolete public preparation/validation overloads that omit required complete publication inputs; internal startup loading remains available. Do not duplicate all old overloads or add compatibility aliases. Exact parameter ordering and visibility representation remain ticket details within these shapes.

### Preparation, publication, and admission responsibilities

Keep the target namespace's expected-revision check; validate dependency contracts and visibility at admission, without requiring dependency revisions to match preparation. The following table defines the responsibilities of each stage.

| Stage | Responsibility |
|---|---|
| Prepare | Validate the complete candidate against a consistent dependency metadata view; reject detected errors. |
| Publish | Enforce target expected revision/absence and existing candidate ownership, one-shot, closed-state, and framework lifecycle checks; install atomically. |
| Admit | Capture the entry skill's reachable dependency generations, validate execution contracts and assumptions against that snapshot, and retain the generations for the invocation. |

**Preparation remains substantive validation.** Capture a coherent immutable dependency metadata view rather than performing unrelated live-registry reads that can combine versions which never coexisted. Validate every skill in the candidate namespace, resolving its outgoing references and any further metadata dependencies required by existing contract validation. Missing targets, invalid visibility, and incompatible contracts observed in that view reject preparation; they are not downgraded to warnings. Do not scan incoming callers or build a reverse-dependency system. Overlay the candidate for references returning to its own namespace.

Implement that overlay as one resolution rule: if the reference namespace equals the candidate's namespace, resolve against the candidate; otherwise resolve against the captured external metadata view. Apply it to direct references and transitive paths returning to the candidate, including contract/schema resolution. Do not fall back to the old active generation for a missing candidate-local skill, and do not perform repeated live-registry reads during validation. No general multi-candidate overlay engine, staging registry, or transaction abstraction is required. Tests must cover replacement of a local contract, a return-to-candidate dependency path, rejection of a skill omitted from the candidate even if present in the old generation, and stability of the external view during concurrent publication. Existing visibility, reference, and contract validations still apply; this small overlay does not replace them.

Capture the metadata view under the existing publication synchronization boundary and perform expensive validation outside it. Audit exactly what validation reads. If it reads only independently usable immutable metadata, resource leases may not be needed. If it accesses resources affected by generation retirement, protect those resources with existing ownership leases for the period of use. Do not assume all preparation leases can be eliminated, and do not hold external dependency leases through unrelated provider initialization or merely because a prepared candidate awaits publication. Release temporary ownership on success, failure, and abandonment. The concrete metadata capture/ownership implementation is a ticket-level design task, not a new subsystem.

**Publication success means installation, not current executability.** Prepared candidates need no dependency revision map for publication preconditions. Under the activation synchronization boundary, check the target namespace's expected generation (or absence for creation), retain existing candidate and lifecycle checks, and install atomically. A dependency-only change, deletion, visibility change, or removal/recreation does not make the candidate stale. A change to the target namespace still does. Keep existing stale-candidate cleanup for genuine target conflicts. Add no dependency-only stale failure, automatic retry, compatibility-analysis pass, or dependency revalidation inside publication.

For example, preparation of `orders` can succeed using `pricing` generation 7; pricing can then change or disappear; orders publication can still succeed if its own expected revision remains valid. A later invocation that reaches pricing must validate against the generations captured at admission and fail before executing skills if its requirements cannot be satisfied. An entry skill that does not reach pricing is not blocked solely by that unrelated dependency.

**Accepted stable prepared contracts.** Preserve the caller's prepared derived contracts and revalidate its existing declared constraints. Do not pin every dependency schema or reject changes merely because a child's schema differs from its earlier version. At admission, reject changes that would alter a prepared derived contract or invalidate a declared constraint.

A published generation retains the contracts produced during preparation, including dependency-derived output schemas. Existing `SkillGenerationManager.resolveOutputSchema` follows `output_from` during preparation and stores derived schemas in capability metadata used by catalogs and planning. Admission recomputes the caller's derived contracts against the captured dependency view and compares them with the published contracts, while revalidating existing declared constraints using the captured targets. Adopting a changed derived contract requires preparing and publishing the caller again. Do not substitute execution-specific schemas, mutate published metadata, or introduce catalog refresh behavior.

Reuse existing contract resolution and validation against the captured view, comparing derived schema structures using existing representations rather than raw schema text or dependency generation IDs. Formatting and object-key ordering alone must not cause rejection. A change that alters the caller's prepared derived schema, including an additive change to a fully forwarded output schema, requires caller republication in this initial design. For input/output bindings and other existing declared constraints, rerun the applicable validators against the captured contracts; a changed child schema is acceptable when those constraints still hold and the caller's prepared derived contracts remain unchanged. Ordinary child calls do not pin earlier child schemas. Check only the declared reachable execution, including multi-hop forwarding and existing input/output binding and planning requirements. Inventory existing derived metadata before introducing any additional bookkeeping; do not store historical copies of every dependency schema or introduce a separate dependency-assumption tracking subsystem, general schema-compatibility engine, or generic schema-normalization mechanism.

For example, `orders-getQuote` forwards `pricing-quote`, whose prepared output contains `total: number`. If pricing publishes `total: string`, pricing publication succeeds, but new orders admissions reject until orders is republished with the new contract. Adding a field to that fully forwarded output also changes orders' derived contract and requires republication. In contrast, if orders only binds pricing's `total` into a declared numeric output field, an added unrelated pricing field does not require orders republication when existing binding validation still passes and orders' derived contracts remain unchanged. An ordinary child call with no affected derived contract or declared constraint likewise does not reject solely because pricing's schema changed. Already admitted work retains its captured generations. The accepted tradeoff is caller republication when its own derived contract changes, without dependency-wide schema pinning or a second execution-specific metadata representation. Catalog metadata describes that prepared contract, not a guarantee of current dependency compatibility.

Accepted tradeoffs include complete payloads for small edits, caller retries after same-namespace conflicts, and staged changes across namespaces. A publication can succeed even when an external change since preparation means some entry points immediately fail admission; preparing against one coherent view provides useful feedback without promising later availability. In return, remove dependency revision maps used as publication conditions, dependency-only stale failures, and unnecessary re-preparation after harmless dependency updates. Keep preparation snapshot handling only to the extent required for coherent and safe validation.

### Concurrent preparation without a broad lock

Remove `DefaultSkillReloader.preparationLock` and its whole-preparation synchronized block. Do not replace it with per-namespace preparation locks, a lock registry, or a preparation queue. Preparations, including competing preparations for the same namespace, build detached candidates concurrently. Preserve the target expected-revision check at publication: candidates based on the same target revision may both prepare successfully, but only one can replace that revision.

Keep the shared registry synchronization boundary for coherent metadata/execution capture, generation lease acquisition, and atomic publication/removal. Removing preparation serialization does not make registry access lock-free. Separate namespace locks would require additional coordination to capture a cross-namespace view whose generations actually coexisted; they are not selected. Keep expensive validation, provider construction, resource close, and retirement callbacks outside the registry boundary. Reachability traversal and lease acquisition remain inside it under the accepted admission protocol; their contention cost is not eliminated by this decision. Preserve the existing narrow fixed-registration initialization, candidate-state, and lifecycle synchronization where required.

**Source audit findings and implementation constraints.** The audit supports this design but is not an executed concurrency proof. Production lock removal must be verified with the tests below.

- `DefaultSkillReloader.prepareWith` currently serializes candidate construction. Its lifecycle checks before preparation and after construction, candidate ownership, and failure cleanup must survive removal of that wrapper. Publication still checks lifecycle and candidate state. A shutdown observed after construction closes the candidate; a shutdown racing after the final preparation check is rejected at publication. Do not introduce a guarantee of forcibly interrupting slow preparation.
- `YamlSkillCatalog` owns mutable skill/diagnostic maps and resets them during validation. It must be fresh for each preparation; it is not a shared concurrent validator. The production explicit-configuration path already creates fresh catalogs. Internal catalog suppliers and test fixtures must preserve that isolation rather than returning a reusable mutable instance. Keep parsed manifests, validation diagnostics, working maps, and derived schemas candidate-local.
- Explicit configuration parsing creates fresh properties, and the production runtime factory constructs independent provider registries and runtimes. Preserve that ownership and isolate configuration/credential inputs between candidates. This decision introduces no provider sharing or new ownership mechanism.
- `SkillGenerationManager` already uses an atomic generation counter. Preserve globally unique generation IDs under concurrent construction; allocation order need not match publication order.
- Fixed Java registration discovery and REST-handler initialization already have narrow synchronization. Preserve safe, completed initialization and reuse immutable registration metadata; removing the broad preparation lock does not authorize unsynchronized lazy initialization or concurrent mutation of discovered registrations.
- Legacy preparation overloads separately read current catalog settings, properties, and trace policy. The existing preparation lock does not synchronize those reads with publication. The selected required complete-configuration API avoids inheriting settings through repeated active-generation reads. Capture dependency metadata coherently using the existing selected protocol; do not add a lock to preserve the superseded inheritance behavior.
- Each candidate owns its constructed resources until publication transfers ownership. Failure, abandonment, stale rejection, and shutdown must dispose of only that candidate's resources, without closing active generations or resources owned by another preparation. Preserve existing one-shot candidate and runtime cleanup semantics.

Source anchors for tickets: `DefaultSkillReloader.prepareWith` and `publish`; `SkillGenerationManager.currentCatalog`, `currentProperties`, `currentPolicy`, `prepareCatalog`, `initializeFixedDependencies`, and `requireRestHandler`; mutable validation state in `YamlSkillCatalog`; and the runtime factory in `LoomspanAutoConfiguration`. These are internal implementation points, not new extension contracts.

Accepted tradeoffs: simultaneous preparations can temporarily construct more provider resources, and same-namespace competitors can perform work that is later discarded as stale. There is no new scheduling, throttling, retry, or fairness feature. Resource measurement, including concurrent preparation, belongs to the [post-implementation follow-up](skill-namespaces-followup.md). The benefit is removing whole-preparation serialization without maintaining another family of namespace locks or weakening coherent capture and publication.

### Initial publication of cyclic dependencies

Use staged ordinary complete publications to establish cyclic namespace dependencies. Do not add multi-namespace transactions, deferred/unresolved references, automatic stubs, or an automatic cycle-resolution workflow.

For an intended `a -> b -> a` dependency cycle:

1. Publish a valid initial `a` without its reference to `b`, including the skill and contract that `b` will call.
2. Publish `b` referencing the published `a`.
3. Replace `a` with its complete version referencing `b`.

Each intermediate publication must be valid and is callable under normal entry and execution checks. Both targets must be Public for the cross-namespace references, and existing contract validation and role requirements remain applicable. These publications are complete replacements, not patches or special placeholder declarations.

During candidate validation, a dependency path returning to the namespace being replaced resolves to that candidate, not its previously active generation. The target namespace's expected-revision check protects the replacement. Preparation uses its coherent external dependency metadata view without making those revisions publication preconditions; admission checks the dependency versions selected for execution. Track visited skill identities separately from selected namespaces, as in the admission protocol below, so cycles terminate without skipping another reachable skill in the same namespace or suppressing validation of reference edges and required contracts.

Accepted tradeoffs: intermediate versions are real and callable, there is no atomic multi-namespace cutover, and an application must be able to supply valid intermediate versions. If it cannot, that sequence is unsupported by this initial design. Namespace cycles do not override existing schema/contract cycle validation, recursion/depth limits, or execution limits, and do not guarantee recursive execution will terminate successfully.

### Entry-skill reachability and namespace-generation retention

The namespace remains the unit of atomic publication and generation retention. Admission traverses the declared skill graph reachable from the entry skill and captures one complete immutable generation for each namespace reached. It does not scan unrelated skills merely because their namespace is captured.

Traverse all declared children of every reachable skill, including optional branches. Do not predict model choices or executed branches, prune by caller roles, or use RBAC-filtered tool discovery to calculate reachability. Existing output forwarding and input/output binding references must resolve consistently with the declared child graph and retain their existing validation requirements. Java skills remain leaves in the supported call graph; no Java child-call API is introduced.

Admission follows this protocol:

1. Read current namespace publications under the same synchronization boundary used to activate publications.
2. Starting with the entry skill's structured identity, traverse declared child references transitively, including local and cross-namespace references. Track visited skill identities separately from captured namespaces. Visit each skill once and select each reached namespace's current generation once; subsequent references into it use that same captured generation. Visiting one skill in a namespace must not skip another reachable skill there. A visited set terminates traversal through shared dependencies and cycles without suppressing required reference-edge validation. Existing execution recursion/depth rules remain unchanged.
3. Acquire one ownership lease for every selected generation before leaving that synchronization boundary. Do not retain unrelated namespaces. If capture cannot complete, release any ownership already acquired.
4. Validate the reachable graph's dependency contracts and visibility outside the lock, before executing any skill. On failure, release all captured leases and reject admission. Publication may continue during validation because the captured generations are immutable and retained; validation uses the captured state even if newer publications appear meanwhile. An unrelated skill's broken dependency does not fail this admission.
5. Attach the immutable snapshot and its aggregate ownership to the admitted invocation, propagating the snapshot through the existing execution binding. Resolve all skill calls through this snapshot rather than reading the latest publications during execution.

Declared dependencies are authoritative. Execution must stay within declared reachable calls and must not attach another namespace's latest generation dynamically. Snapshot membership does not itself grant call permission: discovery and execution continue to enforce declared-call restrictions, applicable source-to-target namespace visibility, and existing role checks. Retaining a whole namespace generation does not authorize invoking its unrelated skills. Implementation verification must cover every supported dispatch path to ensure declared references are enforced.

Complete-candidate preparation validation is unchanged by entry-skill reachability: validate every skill in the candidate namespace against the coherent preparation view. Publication checks the target revision, not dependency revisions. Entry-skill reachability narrows admission only; it introduces no partial publication, per-skill versioning, or independent per-skill resource ownership.

Queued/admitted handoffs retain the same snapshot until execution ownership is released. Failed admission, abandoned handoffs, and completed execution must release their leases through the existing ownership lifecycle. A timeout response alone must not release resources still owned by running work.

The following tradeoffs are explicitly accepted:

- A broken dependency in an unreachable skill does not block admission. A broken declared reachable branch still blocks admission even if actual execution would not choose it. Missing targets, incompatible contracts, and invalid cross-namespace visibility in the reachable graph are admission failures.
- Whole generations are retained, not individual skills. A reached namespace can therefore retain resources for unused skills within it, and declared reachable branches may never execute. However, namespaces reachable only through unrelated skills are not captured. Long-running or queued invocations can still retain older generation resources.
- Traversal and lease acquisition under the shared synchronization boundary can briefly delay publication and other admissions for large dependency graphs. Contract validation stays outside that boundary; failed admissions may temporarily retain resources while validation runs.
- The initial design requires no dependency caches, reverse-dependency index, branch prediction, or role-based reachability pruning. Tracking visited skills and captured namespaces adds modest bookkeeping while avoiding admission coupling to unrelated skills.

This provides stable framework definitions, configuration, and visibility for an admitted invocation. It does not create a transaction over external systems.

### Provider resource ownership for this phase

Keep independent generation ownership of provider resources for the namespace implementation. Each prepared generation constructs its own `ExecutionRuntime` and provider connection registry through the existing factory; generation retirement/disposal closes those resources through the existing single-shot close path. Invocation snapshots lease existing generations, not newly constructed clients. Preserve cleanup for failed preparation, abandoned/rejected candidates, retirement, removal, and shutdown under existing physical-work ownership rules.

Shared startup settings mean equal configuration values, not shared runtime objects or provider registries. New resource measurement and scale assessment are deferred to [Skill Namespaces Follow-up](skill-namespaces-followup.md) after implementation. Implementation must still verify ownership and cleanup correctness; it introduces no resource sharing, lazy client creation, or benchmark subsystem.

### Execution settings and budgets

Reuse existing configuration, mission timeout, cancellation/lifecycle, session usage, quota, and maximum-depth mechanisms. Namespace support changes which captured configuration supplies a setting; it does not introduce a new budget system.

| Setting | Source and behavior |
|---|---|
| Skill definition, model, provider connection, and credentials | The executing skill's captured namespace generation. |
| Provider request timeout and retry settings | That provider connection's existing configuration. |
| Mission timeout | The executing namespace's configured timeout for that mission; existing parent timeout and cancellation remain in force. |
| Session usage quotas | The entry namespace's captured limits for the entire invocation. |
| Usage counters | The existing shared session counters, accumulating across all namespace calls. |
| Maximum call depth | The entry namespace's captured limit, enforced through existing shared execution ancestry. |
| Attachment maximum size | The captured generation of the executing skill materializing the attachment, enforced at existing materialization checks. |
| Trace persistence policy | The captured entry namespace generation, selected at session creation and unchanged across child calls. |
| Trace configured-limits snapshot | The entry generation's session quota settings, matching the limits enforced for the invocation; not a snapshot of every configuration setting. |

Current-skill configuration follows the executing skill without merging namespaces' settings; entry-session settings remain fixed as specified in the table. Entering a called namespace selects its captured definition and provider configuration; returning restores the caller's execution context. All selected generations remain those retained at admission.

Attachment limits follow the skill performing materialization through the existing `DefaultMissionInputMaterializer` checks. An attachment accepted by a parent can be rejected if a child materializes it under a smaller configured limit. Do not introduce a session-wide attachment budget, minimum-limit composition across namespaces, or new materialization checks outside the existing feature's scope.

Apply the table through existing session-start trace setup: one invocation trace and one persistence policy. A child cannot override the policy, and configuration changes after admission cannot replace the captured values used when the session starts.

Quota lookup must use the captured entry namespace rather than whichever namespace is currently executing. Keep one session and its existing counters for skill invocations, tool invocations, model calls, provider attempts, linter retries, and usage units. Crossing a namespace boundary neither resets accumulated usage nor switches the session's quota limits. Do not introduce per-called-namespace counters, budget transfers, or minimum-limit composition across dependencies.

For example, if `orders` starts with a 20-model-call session limit, consumes six calls, and calls `pricing`, which consumes three more, the session has consumed nine of 20 calls. Pricing's own session quota applies when pricing is the entry namespace; it does not impose an additional quota when orders calls it. Maximum call depth likewise remains the entry limit without resetting ancestry at namespace boundaries.

The existing timeout is a mission timeout, not a deadline beginning at host admission or including host queue time. Preserve existing nested timeout behavior rather than adding deadline propagation or remaining-time arithmetic. If orders has a 60-second mission timeout and calls pricing after 50 seconds, pricing may have its own 30-second mission timeout, but orders' original timeout continues running. The child call does not restart or extend the parent's timeout. Existing parent cancellation and lifecycle enforcement continue to apply.

The accepted tradeoffs and limits are:

- One session-wide budget provides simple accounting but no independent budget for each called namespace and no aggregate namespace limit across callers.
- A called skill uses its own namespace's provider configuration while its usage contributes to the entry session's totals.
- Preserve existing quota enforcement semantics. Some usage checks occur after a response is recorded; namespace work does not convert those checks into strict advance spending reservations.
- No admission-time deadline, host queue expiration, or new end-to-end timeout feature is introduced.
- Existing cancellation limitations remain. A timeout response does not guarantee arbitrary external work has physically stopped and must not prematurely release generation resources still owned by running work.

### Namespace entry and call authority

`SkillTemplate` remains a root-invocation API that creates a fresh execution session. All invocation, validation, observer-invocation, and both `SkillInvocationHandoff` input overloads accept a local default name or an explicit qualified reference. For example, `template.invoke("tax-calculateTax", input)` enters tax, and `template.invoke("default-search", input)` enters default. Local `search` always selects `default-search`, independent of any ambient executing namespace, with no fallback.

Remove the proposed namespace invocation views. Do not add `inNamespace(...)`, separate namespace arguments, qualified/unqualified overload pairs, or another public invocation facade. Parse the target identity once at the operation boundary using the common parser, normalize namespace case, preserve skill-name case, and enforce the 32-character namespace and combined 64-character limits. Resolve unqualified input against default; reject malformed qualified references without trying local reinterpretation. Read/capture authentication at the existing operation boundary and capture generations through validation or admission as specified below. Operations resolve current namespace existence and fail if it is absent; they never create namespaces implicitly.

The hosting application authorizes the entry namespace identified by the skill reference before invoking the framework. The reference is a host API argument, not a namespace override in invocation input or model-generated arguments. The framework applies existing entry-skill RBAC and dependency-admission checks. Host-authorized entry into a Private namespace is permitted: Private visibility restricts calls between namespaces, not initial host entry. No namespace-membership system or new authorization SPI is introduced. Root invocation preserves existing nested-root rejection.

Public/Private controls skill-to-skill visibility, not anonymous internet access, external authentication, publication permissions, or database permissions. RBAC permission never overrides a Private cross-namespace boundary.

| Target visibility | Same-namespace call | Cross-namespace call |
|---|---|---|
| Private | Permitted by visibility | Denied by visibility |
| Public | Permitted by visibility | Permitted by visibility |

During child execution, authenticated caller identity stays the same while the current executing namespace follows the executing skill. Each child call must satisfy all of the following:

- Existing declared-call restrictions.
- Same-namespace access or Public visibility of the target namespace.
- The target skill's existing role requirements.
- Resolution within the retained execution snapshot; snapshot membership alone does not authorize a call.

Extend the existing access guard and reuse its role evaluator. Discovery uses the same authorization policy, and execution independently enforces it. Private orders can call Public pricing, subject to existing restrictions. While pricing executes, its source namespace is pricing; it cannot call back into Private orders merely because the authenticated caller originally entered orders.

### Root validation semantics

`SkillTemplate.validate()` checks input, entry-skill authorization, and reachable dependency validity against a temporary consistent snapshot, then releases it without reserving admission. Invocation captures and validates independently. Validation accepts the same local/default or explicit qualified reference as root invocation. The resolved identity supplies the target namespace; no namespace view is added.

Reuse the admission dependency capture and validation logic rather than maintaining a second policy implementation. Follow the entry skill's declared reachable graph, capture one generation per reached namespace, and protect resources through temporary ownership as required by the existing capture protocol. Validate reference existence, namespace visibility, contracts, and dependency-derived metadata assumptions exactly as for admission. Release all temporary ownership on success or failure. Validation does not register an admitted root, reserve execution, run skills, call providers, or return a reusable execution snapshot.

Preserve the authorization distinction: validate the requested entry skill's existing RBAC; do not eagerly require the caller to satisfy every reachable child's RBAC or prune reachability by roles. Child authorization continues to be enforced by existing discovery and execution checks with the agreed namespace policy.

Success reports validity against the temporary captured view, not a guarantee that a later invocation will succeed. Dependencies, namespace existence/visibility, authorization context, or framework lifecycle may change before invocation. A caller requiring an admitted invocation uses the existing handoff API rather than treating validation as an admission reservation. This does not change the preserved distinction between validation/handoff preparation and nested root execution.

### Nested root behavior

Source inspection confirmed that `LoomspanSessionRunner` installs a fresh session through `ExecutionBindingScope`, which already rejects binding a different session inside an active execution scope. Nested root execution through `SkillTemplate`, or execution of an admitted root handoff within an active execution scope, therefore remains rejected. Namespace work preserves this behavior; it does not introduce the restriction or automatically reinterpret a root invocation as a child call.

The existing execution-binding check does not itself prohibit input validation or preparation of a root handoff within an active execution. Do not add blanket prohibitions on those operations as part of this decision. Validation and handoff preparation must not be described as nested root execution. Existing root ownership and cleanup behavior must remain correct when execution is rejected.

The accepted tradeoffs are that entry authorization remains a host responsibility, Java skills gain no new child-composition API through `SkillTemplate`, and Public skills receive no implicit callback authority into Private callers. Existing child execution paths remain the composition mechanism. Execution-binding enforcement is a framework correctness boundary, not a sandbox for arbitrary host-provided Java code or unmanaged threads.

### Java skill namespace assignment

- Add a `namespace` attribute to `@SkillMethod`, defaulting to `default`. Apply the same lowercase normalization as other namespace entry points.
- The Java developer assigns the namespace in application code. Continue using existing Spring discovery, bean lifecycle, argument binding, result serialization, security-context propagation, and method RBAC.
- Every new generation of the assigned namespace includes its fixed registered Java methods alongside the supplied declarations. A method is not automatically included in other namespaces.
- Publications cannot remove, relocate, copy, override, or reconfigure fixed Java registrations. A supplied declaration colliding with a fixed Java skill in the same namespace is invalid. Equal skill names in different namespaces are distinct identities.
- Changing annotation metadata requires rebuilding and redeploying the hosting Java application. Namespace publication does not load new Java code.
- Namespace assignment establishes ownership, not visibility. Public/Private visibility remains part of namespace publication, and Java role requirements still apply. Cross-namespace sharing uses an explicitly Public namespace.
- No publisher-controlled Java binding manifest, alias system, per-namespace bean lifecycle, or implementation-specific configuration delivery is introduced in this phase.
- Existing Spring-managed bean configuration and mutable state remain host responsibilities. A retained generation preserves the Java binding, not a historical copy of bean state; namespace assignment does not isolate mutable singleton fields.

Illustrative annotation with the agreed new attribute:

```java
@SkillMethod(
    namespace = "tax",
    name = "calculateTax",
    description = "Calculate tax"
)
```

### Startup and restart behavior

Startup discovers Java skills through the existing Spring registration mechanism, groups them by their normalized `@SkillMethod.namespace`, and creates initial namespace generations containing those methods. Methods without an explicit namespace belong to `default`; explicitly assigned methods belong to their named namespaces, not `default`.

Java methods are available through their assigned namespaces after successful startup, subject to existing role checks and namespace call visibility. No separate host publication is required merely to activate Java skills. Reuse existing startup generation preparation and activation rather than introducing a separate registration/activation workflow.

Runtime removal must still leave a namespace absent until explicit recreation; lookup must not rerun startup initialization to recreate it.

- Create `default` and the namespaces assigned to discovered Java methods. The initial `default` generation contains startup skill declarations and its assigned fixed Java registrations. Other startup generations contain their assigned fixed Java registrations.
- Initialize every startup namespace from the same existing application startup execution configuration, including existing built-in defaults for omitted settings. Reuse current settings for session limits, mission timeouts, provider connections, models, and trace persistence. Do not introduce per-namespace startup configuration blocks.
- Every startup namespace is initially Private, including `default`. Its methods remain available through host-authorized direct entry and same-namespace calls, subject to existing RBAC. Cross-namespace calls require Public target visibility.
- Making a startup namespace Public or changing its framework settings uses ordinary complete publication. No new annotation visibility attribute or startup visibility setting is introduced.
- Shared initial settings do not establish ongoing inheritance. A later publication completely replaces that namespace's supplied declarations and framework configuration while retaining its fixed Java registrations. Omitted settings use built-in defaults, never startup values or prior publication values.
- Restart repeats initialization from the application's startup configuration, skill documents, and Java annotations. Runtime publications and removals are not automatically restored. Removing `default` during a run does not prevent its creation on restart, and a namespace made Public at runtime starts Private again.

Accepted tradeoffs: all startup namespaces initially receive the same settings even when some do not use the configured providers; different settings or Public visibility require a complete publication; runtime state does not alter startup configuration. No publication is required merely to activate Java methods, and no persistence, automatic restoration, or new namespace configuration loader is introduced.

**Accepted startup composition boundary.** Startup skill files in `default` can call skills in `default`, including its fixed Java skills, using local `search` or qualified `default-search` without an extra publication. A startup declaration referencing another namespace cannot pass ordinary visibility validation while that target is Private. Do not defer validation or exempt Java targets from visibility checks. Hosts needing cross-namespace composition publish the target namespace as Public and then publish its callers through the ordinary APIs; those caller declarations are not loaded as startup skill files. This is an explicit managed-deployment choice, not an initialization step imposed on ordinary embedded applications.

Keep automatic Private startup and API-driven publication as the complete mechanism. Add no startup visibility setting, Java visibility annotation, dependency-aware startup loader, framework bootstrap coordinator, or separate activation workflow.

### Discovery and observation scope

Discovery and observation changes are limited to identifying namespaces in existing lookups and trace data. Reuse the existing catalogs, event streams, traces, callbacks, authorization, journal projection, and retention mechanisms. No new observation subsystem or feature is introduced.

- All skill identities in records, scoped catalogs, and root-operation arguments use canonical `namespace-skillName`. Equal local declaration names in different namespaces remain unambiguous without namespace invocation views.
- Existing trace, event, execution, and diagnostic records identify the relevant namespace. Distinguish the entry namespace from the namespace of an executing skill where the existing record represents that distinction.
- Existing generation references identify the captured namespace generations associated with the invocation. Obtain identity from the captured execution context, not from a later lookup of current publications.
- Preserve one session, event stream, and trace per invocation, including its cross-namespace work. Do not split traces or callback output at namespace boundaries or reset event sequencing.
- Preserve existing catalog semantics: the host-facing Java `SkillCatalog` is unfiltered metadata; runtime discovery continues to apply declared-call restrictions and the agreed namespace-aware execution authorization policy. Public visibility does not automatically expose all Public skills as model tools.
- Preserve existing observation authorization and access boundaries. Public skill-call visibility grants no observation or configuration access. Do not introduce namespace-specific observer roles or permissions.
- Preserve diagnostic content and existing journal projection behavior. Namespace identity does not introduce new content rewriting or masking.

This phase adds no new filters, query features, observation permissions, or reporting features. Concrete record fields and lookup signatures remain implementation details to resolve within this scope.

### Framework/Console scope and release compatibility

Namespace implementation includes the changes to existing Console consumers, fixtures, tests, and documentation required to correctly interpret Framework identities and execution metadata. Update affected Java, Go, and TypeScript contracts and their consumers together in the same change. This is necessary integration work, not a new Console feature. New Console features and Cloud integration remain outside scope.

Retain the existing `consoleCompatibilityVersion` mechanism. The Framework's filtered `META-INF/loomspan-release.properties` derives it from `${project.version}`; it is the release version, not an independently maintained protocol counter. Console requires the matching release version. The release containing namespace support carries the changed contract; do not introduce a namespace-specific marker, separate protocol version, compatibility range, or alternate negotiation mechanism. Preserve the existing development-build behavior without promising compatibility between development builds.

Change affected contracts and fixtures directly. Add no old-format readers, aliases, fallback parsing, dual local/qualified behavior, trace migrations, or compatibility shims. Existing saved traces from a different release remain subject to the existing compatibility rejection rules; this feature creates no historical-readability guarantee.

### Namespace removal and visibility changes

Visibility changes use ordinary complete namespace publication with the expected-revision check. There is no separate visibility-update operation. New admissions see the new visibility; already admitted invocations, including queued handoffs, retain their captured generation and visibility. Changing Public to Private may cause dependent namespaces' new invocations to fail admission. Publication does not scan or revalidate incoming callers.

Removal is an explicit operation against a namespace and its expected active revision; its exact public API signature remains to be selected. Under the same synchronization boundary used for publication and admission capture:

1. Verify that the namespace exists and its active generation matches the expected revision.
2. Withdraw it from the active namespace registry and mark that generation as superseded for retirement.
3. Retain its resources through the existing ownership leases until already admitted work releases them. Dispatch eligible resource cleanup outside the registry lock using the existing retirement mechanism.

Removal returns after withdrawal; it does not wait for execution to drain. New direct admissions to the removed namespace fail. New admissions whose entry skill's declared reachable graph requires it also fail, including optional reachable branches. A reference from an unrelated unreachable skill does not block admission. Already admitted work continues against its retained snapshot. Prepared updates based on the withdrawn generation cannot publish.

Recreation uses ordinary preparation capturing absence and publication checking that expectation, with a fresh generation identity; callers do not supply a separate preparation revision argument. A stale prepared update based on a previous active generation cannot match the recreated namespace's revision. Old admitted work may continue on the previous incarnation while new admissions use the recreated namespace. Candidates prepared against absence retain the absence-at-publication semantics in the management API section.

The same rules apply to these cases without exceptions:

- **`default`:** removal is allowed. Root operations targeting `default-skillName` fail while it is absent; explicit recreation restores availability subject to normal admission checks. Invocation does not implicitly recreate it.
- **Namespaces with Java registrations:** removal withdraws the namespace from new admissions but does not unregister, relocate, unload, or reconfigure its fixed Java methods or destroy their Spring-managed beans. Already admitted work may continue invoking retained bindings. Recreating the namespace includes the fixed Java registrations assigned to it by application code, as every generation does. Restart rebuilds startup namespaces under the accepted initialization rules above.

Accepted tradeoffs:

- Removal and visibility changes are prospective, not immediate revocation. Already admitted or queued work can outlive them.
- Dependent namespaces remain published; there are no automatic cascading updates or removals. Their new admissions may fail until dependencies are corrected.
- There is no removal-specific drain workflow or forced cancellation. Resources may remain retained after removal returns.
- `default` has no special protection, and removal disables entry into it until recreation.
- Recreation can overlap old and new executions, consistent with generation replacement semantics.

Reuse the active registry, revision checks, generation ownership, and retirement lifecycle. No tombstone-management feature, reverse-dependency system, forced-cancellation feature, or separate namespace shutdown subsystem is introduced.

### Cloud mapping and later lifecycle context

The namespace discussion agreed on one namespace per micro app, with Private app namespaces and shared capabilities in Public namespaces. Cloud controls app ownership, membership, publication authority, and request routing. Ordinary Cloud users will not publish into Cloud's public/shared capability area. No reserved namespace name or fixed list of shared namespaces was selected.

The later lifecycle discussion introduced sandbox and production publications. One namespace per app environment was suggested as a refinement of the original mapping, but remains unconfirmed. The framework should support generic namespace identity rather than assume one namespace equals one Cloud app or environment.

Cloud's agreed later-publication workflow is to stop new operations for the affected app, drain accepted queued/running work, apply schema changes and matching definitions, activate the new version, and reopen only after success. Other apps should remain available. Partial failure leaves the affected app unavailable pending repair. Cloud also checks the companion's app version before execution and rejects outdated requests so instructions can be refreshed.

These are hosting lifecycle requirements that inform namespace research, not a decision to put Cloud migrations, version admission, or publication orchestration in framework core. The gate, drainage integration, and coordination with database changes remain undesigned. Existing timeouts were assumed by the owner, but safe drainage requires verifying that timed-out work can no longer mutate data. Authentication source inspection did not verify drainage or timeout termination guarantees.

## Implementation integration still to resolve

The behavior contracts are accepted; the remaining questions concern implementation, not additional features. Verify current source behavior during planning. Scale targets and resource thresholds belong exclusively to the post-implementation follow-up.

| Integration area | Remaining work within the agreed contracts |
|---|---|
| Identity and API | Integrate the shared parser/formatter, declaration identity construction, canonical generation keys, root arguments, catalog contracts, and management signatures; remove obsolete local-reference and incomplete-publication paths. |
| Execution and authority | Carry entry/current namespace-generation context and caller identity distinctly; integrate source-to-target checks in discovery and every dispatch path, including asynchronous propagation and callback denial. No new host-context API is selected. |
| Preparation and publication | Adapt coherent metadata capture, candidate overlay, target-only revision checks, detached concurrent preparation, fixed-registration initialization, lifecycle checks, and cleanup. Audit validation reads to determine temporary ownership requirements. |
| Dependency contracts | Inventory derived metadata and reuse resolution/validation for forwarding, bindings, planning, and transitive dependencies without historical dependency-schema pinning. |
| Admission and retirement | Choose immutable snapshot/aggregate-lease types; integrate existing execution bindings, partial-failure cleanup, abandoned handoffs, expected-revision removal, and retirement. |
| Startup and configuration | Integrate Java grouping, Private initial generations, shared startup settings, and the strict publishable configuration boundary. |
| Framework/Console | Audit record producers, comparisons, joins, consumers, fixtures, and documentation together under the existing release marker. |

The source anchors in the concurrency audit, the contracts above, and the acceptance coverage below supply the detailed requirements. Concrete types, parameter ordering, visibility payload representation, and removal signature remain planning decisions within those constraints.

## Acceptance coverage

These scenarios verify the contracts above. Keep existing validators and lifecycle tests as the base; update obsolete expectations directly. New resource measurement is excluded from implementation acceptance and deferred to the follow-up document.

### Verify: Generation layering and identity representations

- Two namespaces can declare local `search`, producing distinct `a-search` and `b-search` identities in generation maps, catalogs, tools, plans, bindings, routes, and diagnostics.
- Prepared/runtime references remain qualified. Authored local input resolves to the owner and host local input to default; slash-separated, colon-separated and malformed references are rejected; namespace case normalization preserves duplicate detection and skill-name case.
- A cross-namespace child switches current settings while preserving entry limits, session identity, and the captured snapshot; returning or throwing restores its parent context, including parallel branches.
- Authorization uses the source namespace before the switch, so entering a Public namespace cannot grant callback authority into a Private caller.
- Trace/event/API identity and the captured generation mapping remain accurate after publication/removal. Update existing consumers and fixtures without adding Console features.

### Verify: Catalog and public generation-ID contracts

- Equal local declaration names yield different qualified descriptor names across namespaces.
- Empty published namespaces return self-identifying catalogs; missing/removed namespaces fail acquisition.
- Qualified lookup stays within the selected immutable catalog, including an empty result for another namespace's identity; unqualified/malformed names are rejected.
- Descriptor names work unchanged with invocation, validation, and handoff. Invocation/handoff capture fresh admissions; validation captures a temporary snapshot without reserving admission.
- Retained catalogs remain unchanged; newly acquired catalogs select current generations. Hosts acquire startup catalogs through the reloader.
- Candidate catalogs and validation feedback contain only the target namespace's supplied and fixed Java skills, preserving validation side-effect rules.
- Cross-namespace admission and REST execution report entry and target generation IDs respectively; ID access remains side-effect-free after release/retirement.

### Verify: Common tool and skill-reference representation

- One target has the same qualified name after same-namespace authored compilation, cross-namespace calls and host entry resolution, and in catalog lookup and every model/framework record.
- `a_b-c` and `a-b_c` remain distinct; normalization changes only namespace case.
- Namespace identifiers of 32 characters pass the namespace limit and 33 fail, including management operations and references whose combined length is otherwise valid. Combined identities of 64 characters pass the naming limit and 65 fail when their components are valid. Cover Java registration, startup declarations, publication, optional children, host arguments, and model-generated selections. A declaration with an oversized identity fails even when never offered as a tool.
- Model-generated plan, step, and tool selections normalize namespace case through the shared parser before bound-surface lookup. Verify matching against the canonical target, preservation of skill-name case, rejection of unbound targets after normalization, and preservation of raw model output in diagnostics.
- Bound callbacks, ordinary and planned execution, input/output bindings, authorization, and trace correlation all use the canonical target unchanged.
- Unqualified model/runtime selections and obsolete slash/colon formats are rejected. Adapters introduce no encoding, fallback names, or compatibility aliases.

### Verify: REST handoff and retirement identity

- Two namespaces declaring the same local REST name deliver distinct canonical qualified names to the same handler.
- A cross-namespace call delivers the captured target generation ID, not the session's entry generation ID.
- An admitted invocation calling an older retained REST generation after publication or removal still delivers its old qualified identity and generation ID.
- Generation IDs cannot collide across namespaces or be reused after namespace removal/recreation; stale target updates remain distinguishable from new incarnations.
- Retirement callbacks identify the correct generation and preserve existing lease/physical-work ownership and delivery semantics after replacement or removal.
- Update supported API documentation and tests for the qualified `skillName` semantics, even though the record's field types remain unchanged. Add no compatibility shim or dual local/qualified behavior.

### Verify: Preparation, publication, and admission responsibilities

- Invalid dependencies in the captured preparation view still reject preparation.
- Compatible dependency changes between preparation and publication do not reject publication solely because revisions changed.
- Dependency deletion, incompatible contract changes, and Public-to-Private changes after preparation do not create publication conflicts; affected reachable admissions reject before skill execution, while unrelated entry paths remain admissible.
- Concurrent target updates, target removal/recreation, foreign candidates, closed/already-published candidates, and shutdown retain their existing rejection and cleanup semantics.
- Dependency-derived forwarding schemas remain equal to the caller's prepared derived contracts, including through multi-hop forwarding. Reject changes that alter those derived contracts, including additive changes to fully forwarded output, until the caller is republished. Ignore schema formatting/object-key ordering differences. Verify that admission never substitutes a different execution-specific contract or mutates published catalog metadata.
- Revalidate existing input/output bindings and other declared constraints against captured child contracts. Accept a changed child schema when the constraints still hold and the caller's prepared derived contracts remain unchanged; reject changes that invalidate a constraint. Cover an unrelated added child output field and an ordinary child call with no affected derived contract or constraint to prove that admission does not pin every dependency schema.
- Metadata capture does not mix inconsistent live views, and any temporary preparation ownership is released without closing resources still owned by admitted executions.
- Successful admission remains stable across later dependency publication/removal, using the retained execution snapshot.

### Verify: Publication configuration

Acceptance scenarios should preserve rejection of host-only and unknown keys, verify built-in defaults and no connection/model inheritance on replacement, and verify that namespace publication leaves host configuration unchanged. Reuse existing parser and configuration tests; no second publication parser or configurable boundary is required.

### Verify: Revision and management API

Ticket acceptance tests must distinguish candidate ID from base ID; verify captured-base success when a target changed before preparation; reject target changes after capture without refreshing the base; cover competing replacement/creation candidates, removal/recreation, and absence semantics; preserve foreign/closed/published candidate rejection and cleanup; and confirm unrelated namespace changes do not create conflicts. Update README API examples and supported-surface tests directly. No new public revision type or management facade is needed.

### Verify: Execution settings

Ticket verification should include parent and child namespaces with different attachment limits, demonstrating enforcement where each materializes input, and different trace persistence/quota settings, demonstrating that the trace policy and recorded quota limits remain those of the entry generation throughout cross-namespace execution. Reuse existing attachment validation, session setup, and trace tests rather than adding new budget or trace machinery.

### Verify: Root operations

Ticket tests must cover qualified catalog descriptor names passed directly to all root operations; default and non-default targets; namespace normalization with skill-name case preserved; local/default equivalence, missing default without foreign fallback, malformed and oversized arguments; operation behavior after replacement/removal/recreation; concurrent calls into different namespaces; and authentication captured at each operation. Exercise validation, both input forms, observer invocation, and handoff without duplicating authorization/admission logic. Update README and supported-surface tests directly; no namespace-view API or new public identity type is required.

### Verify: Root validation

Ticket acceptance scenarios should cover valid input and root RBAC with a broken reachable dependency rejecting validation; a broken unreachable skill not blocking validation; matching dependency checks for validation and invocation against unchanged state; cleanup of temporary leases on all outcomes; no root admission or skill/provider execution during validation; and successful validation followed by failed invocation after a dependency change or removal. Include a role-restricted optional child to verify that validation does not introduce eager authorization of all reachable children.

### Verify: Framework/Console integration

Acceptance coverage must verify that existing Console displays, lookups, correlations, and trace readers correctly consume canonical skill identities and captured namespace-generation metadata. Update affected semantic fixtures and tests together, and retain the existing release-match checks. No additional observation filters, queries, or reporting features are required.

### Verify: Concurrent preparation

**Required concurrency acceptance tests.** Replace `SkillReloaderTest.preparationsSerializeWithoutBlockingSnapshots`, which deliberately asserts the old serialization behavior. Use latches/barriers and bounded waits to establish the relevant interleavings rather than relying on sleeps or timing-based throughput assertions.

- Block one candidate during expensive validation or runtime construction and prove another preparation progresses before it is released. Cover both the same target namespace and different target namespaces. Snapshot reads and unrelated publication must also progress while construction is blocked.
- Prepare distinct documents/configurations concurrently and verify skill definitions, derived metadata, diagnostics, credential resolution, runtime settings, and generation IDs remain isolated. Include one invalid candidate alongside a valid one so validation failure cannot contaminate the successful result.
- Prepare two candidates against the same target revision, then race publication: exactly one wins, the other is stale, and cleanup preserves the winning generation. Cover concurrent namespace creation with expected absence as well as replacement.
- Prepare candidates for different namespaces and publish both successfully without either target's revision change invalidating the other. Preserve the accepted absence of dependency-revision publication checks.
- Race publication/removal with metadata and admission capture: captured generations form a coherent view, acquired leases survive retirement, and contract validation occurs outside registry synchronization. Cover partial-capture failure cleanup.
- Exercise construction failure, abandonment, stale rejection, and shutdown during blocked preparation. Verify cleanup is one-shot, does not close another candidate or active generation, and no candidate can publish after shutdown. Preserve cleanup after a failed post-construction lifecycle check.
- Verify concurrent preparation reuses safely initialized fixed Java/REST registrations and never exposes partially initialized registration state. This requires no concurrent invocation of application skill implementations during preparation.

### Verify: Startup and restart

Acceptance coverage should verify that default contains both startup declarations and its assigned Java registrations, while other startup namespaces contain only their assigned Java registrations. Demonstrate embedded same-namespace startup with local or qualified references and no extra publication, startup rejection of a foreign Private target, API publication of Public shared Java capabilities followed by callers, and departmental same-namespace SkillPack publication retaining fixed Java registrations. The managed-host sequence must also work without local skill or execution-configuration files; restart returns to the accepted Private startup state until the host repeats its publications.

## Existing identity findings relevant to both proposals

The later Cloud identity investigation inspected framework commit `52f11dd90c12863b2ec440583cfe2396eda946f6` and sidecar commit `ddae36814247fba74ffb74b0fdff7add04228b50`. The sidecar declared framework `1.0.0-beta.8`; relevant authentication paths were also checked against that release. These findings describe that inspection, not a fresh verification of the current checkout. Existing test source was read; tests were not executed during that investigation.

Authenticated caller propagation into JavaSkill execution already exists. The supported `SkillInvocationHandoff` captures authentication, and framework execution installs an isolated Spring Security context around Java/REST implementation calls. Existing integration-test source exercises concurrent callers and context isolation. Standard Spring Security APIs are the intended way for a JavaSkill to read that identity; internal framework security/session classes are not a supported extension surface.

Cloud therefore did not appear to need a new framework mechanism solely to make authenticated identity available to DataSkill. This does not resolve trusted app/environment context, membership, namespace identity propagation, data authorization, or arbitrary asynchronous work created by a JavaSkill.

Sidecar examples include JWT authentication, authentication retention/revalidation for queued execution, and result ownership by issuer plus subject. These are examples, not selected Cloud credentials or a completed companion-facing authentication flow. Caller identity propagation and forwarding a caller's credential to a database are separate capabilities.

## Research and ticket preparation in this repository

Inspect declaration parsing and validation, JavaSkill registration/binding, publication and reload behavior, lookup and skill composition, invocation/handoff, RBAC, configuration delivery, and execution consistency. Consult sidecar hosting integration where useful. Establish what can be reused before proposing new mechanisms.

Apply this repository's `AGENTS.md` public API classification rules when evaluating candidate contracts. For this work, the owner's explicit development-stage decision permits destructive API changes without compatibility shims. The supported API remains the deliberately allowlisted surface in `ai.loomspan.api`; Java `public` alone does not imply support. `RestSkillHandler` is the sole supported Java SPI. Do not accidentally establish an internal bean-replacement contract or recommend framework internals to consumers. Any necessary new public API must be deliberate, documented, and covered by supported-surface tests under repository guidance.

Implementation planning should verify the relevant current behavior and resolve the integration details above using the smallest design consistent with the accepted contracts. Keep all new resource measurement in the post-implementation follow-up, outside PR 27 implementation tickets. Follow the accepted API shapes; no new SPI is selected, and compatibility shims are not required.

## Source documents

These relative links refer to the sibling source checkout and may need adjustment if the repositories are moved. This document carries the substantive framework context so research need not depend on that checkout being present.

- [002 — Platform Components and Responsibilities](../../../../meta-hackathon/ai/thoughts/phases/002-platform-components-and-responsibilities.md): general framework/Cloud boundaries and hosted primitives.
- [003 — Data Skills V1 Direction](../../../../meta-hackathon/ai/thoughts/phases/003-data-skills-v1-direction.md): named operations, declarative validation, and DataSkill motivation.
- [004 — Optional JavaSkill Configuration and Responsibilities](../../../../meta-hackathon/ai/thoughts/phases/004-optional-javaskill-configuration.md): primary configuration proposal and unanswered questions.
- [005 — Skill Namespaces](../../../../meta-hackathon/ai/thoughts/phases/005-skill-namespaces.md): primary namespace decisions and open mechanics.
- [006 — App Lifecycle](../../../../meta-hackathon/ai/thoughts/phases/006-app-lifecycle.md): environment mapping proposal, publication drainage, and app-version admission context.
- [007 — Identity and JWT Findings](../../../../meta-hackathon/ai/thoughts/phases/007-identity-and-jwt-findings.md): prior source-inspection evidence and its limits.
- [010 — Data Ownership and Access Enforcement](../../../../meta-hackathon/ai/thoughts/phases/010-data-ownership-and-access-enforcement.md): later Cloud/DataSkill enforcement split.
