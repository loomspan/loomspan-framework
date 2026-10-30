# PR 27.5 — Integrate namespace identities across Framework and Console

## Outcome

Finish the PR 27 series with existing Framework and Console surfaces consistently identifying captured namespace execution, and verify the complete namespace feature without adding observation features or compatibility machinery.

## Requirements

- Update affected Framework record producers and Java, Go, and TypeScript contracts/consumers together. Semantic skill identity strings are canonical `namespace-skillName`; audit comparisons, joins, sorting, lookups, route meanings, trace readers, metrics, fixtures, and displays for former local-name assumptions. Keep String fields where they express the contract cleanly rather than retyping every record.
- Existing execution/trace metadata retains the captured namespace-to-generation mapping, with entry namespace distinguished from executing namespace where the record represents that distinction. Session-level `generationId` remains explicitly entry-scoped. Obtain identity from captured context, never current publication; preserve accuracy after removal/replacement/recreation. Diagnostics with unknown skill keep existing null/unavailable behavior, and routes identifying something other than a skill retain their meaning.
- Preserve one session, event stream, trace, event sequence, and observer output flow per invocation across namespaces. Keep host catalog metadata unfiltered; runtime discovery remains permission filtered. Public skill-call visibility grants no observation/configuration access. Keep existing observation authorization and retention; no namespace filters, queries, enumeration, reporting features, observer roles, or new subsystem.
- Preserve canonical prompt/input/output/tool/evidence/diagnostic content. Retain only the existing `ExecutionJournalProjector` field-name redaction exception for its derived journal and observer events, unchanged in scope. Namespace identities add no sensitivity classification or content rewriting.
- Retain `consoleCompatibilityVersion` as the exact matching release version derived through filtered `META-INF/loomspan-release.properties` from `${project.version}`, including existing development-build behavior. Do not introduce an independent protocol counter, namespace marker, range, or alternate negotiation. Directly update affected fixtures/contracts; no legacy readers, dual formats, trace migrations, or historical readability promise.
- Complete consumer-facing README and affected examples/fixtures for canonical names, namespace catalog acquisition, complete replacement, Private startup, explicit Public sharing, fresh root admission versus temporary validation, removal/recreation, and entry-versus-target generation IDs. Explain generic host publication/entry responsibilities without prescribing Cloud infrastructure or claiming complete tenant isolation.
- Demonstrate embedded same-namespace startup without extra publication; API-managed Public shared Java capabilities and API-populated callers without local skill/configuration files; departmental publication retaining fixed Java methods; and restart returning to Private startup state. Explain staged cyclic publication and prospective removal/visibility effects without adding bootstrap or drainage machinery.
- Close recorded integration/verification gaps from PR 27.1–27.4. Earlier tickets own their focused tests; this ticket provides final cross-component assurance, fixes integration defects, and confirms coverage of the accepted design rather than deferring known namespace correctness work. Run required supported-surface and affected Console verification, including semantic fixture agreement and release-match behavior. No claim of success based on intentionally broken intermediate commits.
- Keep independent generation-owned runtimes. All new resource measurement, benchmark workloads, capacity thresholds, provider sharing, and optimization are outside this series and deferred to [Skill Namespaces Follow-up](../phases/skill-namespaces-followup.md) after implementation. Ordinary lifecycle cleanup and concurrency correctness tests remain required.
- Documentation must distinguish generic namespace ownership from complete tenant isolation: credentials, database permissions, mutable Java state, and resource consumption still need host/application controls. No reserved shared namespace, publisher role, namespace membership, or app/environment mapping is prescribed. Cloud hosts Framework directly; sidecar examples do not make sidecar a runtime dependency or select a Cloud authentication scheme. App migration gates, drainage, version admission, and repository integration remain host work outside this feature.
- Preserve exact-release rejection for saved traces from a different release and do not promise compatibility between development builds. New skill-call visibility does not grant observation access or change the meaning of existing authorization boundaries.

## Acceptance criteria

- [ ] Existing Console displays, lookups, correlations, and trace readers distinguish equal local skill names across namespaces and correctly interpret entry/target generations and the captured mapping, even after publication/removal. Java/Go/TypeScript contracts and semantic fixtures agree.
- [ ] Cross-namespace execution preserves one session/trace/event sequence and existing observer behavior, content fidelity, and authorization. Unknown-skill diagnostics and non-skill route meanings remain intact; no new observation features or masking are introduced.
- [ ] Existing exact-release compatibility checks and development-build behavior remain valid without extra counters, old-format fallback readers, or trace migrations.
- [ ] Documentation and end-to-end scenarios cover embedded, file-free managed, departmental, restart, staged cycle, and removal/recreation use with qualified references and supported public APIs only. No internal bean-replacement contract or new SPI is exposed.
- [ ] All accepted implementation coverage in the design is accounted for across the series: identity/catalog limits; complete publication/configuration; target-only revision checks and candidate overlay; concurrent detached preparation; reachable snapshot admission and stable derived contracts; root validation; authority/settings/REST identity; startup/removal; and Framework/Console integration. Remaining broken-build or verification gaps from earlier slices are resolved with evidence.
- [ ] `LoomspanPublicSurfaceArchitectureTest` and applicable Framework/Console checks pass, including required Console semantic fixture and build verification under repository guidance. New capacity measurements are not required for completion and no unmeasured scale claim is made.

## Context and sequencing

Final ticket, depending on PR 27.1–27.4. The accepted [namespace design](../phases/skill-namespaces.md), particularly Discovery/observation, Release compatibility, and Acceptance coverage, defines the final behavior. Follow `loomspan-console/AGENTS.md` when changing Console consumers and fixtures. Update wire producers and consumers in this same slice rather than spreading a contract transition across releases.

These are five sequential local commits for one PR 27 implementation, sized for GPT 6.1 Sol with focused outcomes rather than independently deployable features. The full series must be coherent before sync. No Cloud implementation, new Console features, optional implementation-specific Java configuration, or follow-up resource measurement is included.

The accepted design and all implementation acceptance scenarios are normative. Source findings in that document are historical leads to verify, not executed proofs. Resolve concrete types/record fields/signatures within the accepted contracts; do not replace those contracts with a different feature design. Use this ownership map when closing the series, including explicit carry-forward verification blockers from earlier tickets:

| Accepted coverage | Owning ticket(s) |
| --- | --- |
| Identity grammar/lengths, canonical generation keys, immutable namespace catalogs, Java annotation | 27.1; 27.2 management/startup boundaries; 27.3 host arguments; 27.4 model boundaries |
| Complete publication/configuration, candidate overlay, captured-base/absence revisions, Java registration/startup/restart, removal, staged cycles | 27.2 |
| Detached concurrent preparation, candidate isolation and cleanup, metadata capture/retirement races | 27.2; 27.3 execution-capture/retention races |
| Reachable graph capture, prepared derived contracts, bindings, root invocation/validation/handoff, partial/final ownership release | 27.3 |
| Source authority, discovery and every dispatch path, callback denial, async context, entry/current settings, REST target IDs | 27.4; 27.3 early admission rejection |
| Canonical trace/event identities, captured generation mapping, existing Console consumers, fixtures, release checks | 27.5 |
| Public surface, README, affected focused checks | Each owning ticket; 27.5 verifies final agreement and closes blocked checks |

This map allocates verification, not additional features or a new test subsystem. Reuse existing validators and lifecycle/fixture tests and update obsolete assertions directly. Do not defer a settled requirement merely because it spans tickets or appears only in the authoritative design's acceptance coverage.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Changes serialized observation contracts across languages and completes cross-cutting integration assurance.
- **Reassessment triggers:** Reassess if scope materially changes; concrete record fields and consumer locations remain implementation-planning work.

## Pipeline notes

- Breaking supported/wire contracts is authorized for this development-stage release; no compatibility shims, migrations, or fallback readers.
- Earlier ticket commits may intentionally be broken and are not synced individually. This final ticket must resolve all series integration gaps and complete verification; intermediate-breakage permission is not a final acceptance waiver. Do not sync or start implementation merely because these tickets exist.
