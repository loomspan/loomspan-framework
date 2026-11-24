# PR 37.7 — Verify naming examples across the documentation sets

## Outcome

Give Framework and Console users a consistent current-state reference, and record the downstream documentation surfaces affected by PR 37.

## Requirements

- Audit the README, Java API docs, authoring guides, packaged Agent Skill instructions and references, Console help, setup/deployment guides, snippets and sample applications in this repository. Remove obsolete naming migration narratives and write the docs as if the portable contract had always applied.
- Check signatures, prose, YAML, relative paths, tool names, requests and responses against the implemented boundary. Review single-hyphen references in context to distinguish a legitimate local name from a stale reference to a foreign target.
- Include a read-only audit of Sidecar README, SDK docs, HTTP examples, authoring packs and deployment guidance, and Cloud publication, companion-pack, promotion, export and plugin guidance that exists. Record reviewed sets, evidence and downstream follow-ups in the ticket's completion record. Sidecar and Cloud edits belong to their own workstreams, apart from the Sidecar handoff in PR 37.8.

## Acceptance criteria

- [x] Framework and Console examples use the documented input shape for each boundary and resolve to the intended target. Verification cites runnable examples, tests or implemented signatures for the examples checked.
- [x] The published documentation teaches only the current naming system. The completion record identifies the sets actually reviewed across all four products and distinguishes verified local examples from pending downstream work.
- [x] Downstream findings identify the affected documents and boundary so the Sidecar handoff can cite them.

## Context

Follows PR 37.6 in the local PR 37 series. PR 37.8 verifies the combined implementation and finalizes the Sidecar handoff. See the [portable naming proposal](../future/portable-skill-naming.md).

## Execution profile

- **Recommended:** Fast-Track 2-Step Pipeline — Implementation & Review
- **Confidence:** high
- **Rationale:** This is a bounded documentation and example audit against behavior established by the preceding tickets.
- **Reassessment triggers:** Reassess if an example exposes an unresolved public contract or requires changes to production behavior.

## Pipeline notes

PR 37 is a destructive replacement across undeployed products. Remove superseded naming behavior without aliases, shims or migration instructions. Write current-state documentation. Intermediate commits stay local and may break later consumers. Record deferred checks for PR 37.8 and verify this ticket's behavior as far as the local build allows.

## Execution notes

- Selected profile is fast-track, approved by the developer's series-wide profile authorization. This is a documentation/example audit against implemented behavior at `c1f5093`.
- The developer permits breaking changes without compatibility shims and requires current-state documentation. The Sidecar handoff in PR 37.8 must remain uncommitted in that sibling repository.
- Baseline tracked/staged work was clean. The untracked portable naming proposal and PR 37.8 ticket are developer inputs and are preserved.
- Application API, configuration/manifest and Console serialized/diagnostic boundaries are documented here. This ticket changes guidance only and introduces no Java types, signatures, SPI, Spring wiring or serialized behavior.
- Applied the checkout's `loomspan-docs` router to the `java-api` and `skill-authoring` sets after inspecting the matching implementation and focused tests. Bundle and checkout both identify `1.0.0-beta.10-SNAPSHOT`. The drift classification for the corrected paragraphs is **documentation drift**. Other reviewed naming guidance is **aligned**. Downstream findings below describe pending adoption in separate products.

## Completion record

### Local documentation inventory and evidence

The audit searched the current published Markdown sets and inspected their identity-bearing prose, signatures, code, YAML, paths and sample requests/responses. Historical `ai/thoughts` records, installer version-selection test packages and dated Console evaluation replay artifacts retain their provenance and are outside current published authoring guidance.

| Reviewed set | Naming boundary and result | Executable or implemented evidence |
| --- | --- | --- |
| Root `README.md` and `docs/README.md`, `docs/setup.md`, `docs/architecture.md` | Exact declaration components, startup ownership, owner-relative authored dependencies, separate root components and colon-qualified catalogs are aligned. `output_from: {skill: finish}` is a valid local reference. | `SkillIdentity`, `SkillTemplate`, `SkillInvocationHandoff`, `SkillDescriptor`, `ValidatedSkill`, `SkillCatalog`; `NamespaceIdentityFoundationTest` and `PortableDeclarationIdentityIntegrationTest` |
| `docs/quickstart-java.md` and `docs/quickstart-executors.md` | `triage-support-request`, `send`, `query`, `http` and `sql` are valid local components. Both quickstarts pass `default` and the local name separately. Startup `skills/**/*.yaml` path examples reflect loader validation. The executor demo's local references resolve to its declared default-owned executors. | Implemented annotation and facade signatures; `StartupSkillPathTest`, `PortableDeclarationIdentityIntegrationTest`, `ExecutorApplicationIntegrationTest` and `SupportedSurfaceIntegrationTest`. Hosted model output is outside this audit. |
| Packaged routers `agent-skills/loomspan/SKILL.md`, `agent-skills/loomspan-install/SKILL.md`, `agent-skills/loomspan-docs/SKILL.md` and their metadata | Agent Skill frontmatter names are valid lowercase names. Product routing and version selection contain no alternate runtime name syntax. | Manifest contents and matching package version |
| Entire `agent-skills/loomspan-docs/references/java-api/` set | Public root signatures, explicit snapshot selection, local-only lookup, metadata accessors, declarations and reload diagnostic labels are aligned. Corrected observation guidance from a single-hyphen reference to `namespace:local-name` and added the raw provider/resolved distinction. | `ApplicationApiValueTest`, `DefaultSkillCatalogTest`, `PortableDeclarationIdentityIntegrationTest`, `NamespaceObservationIntegrationTest`; production API declarations |
| Entire `agent-skills/loomspan-docs/references/skill-authoring/` set, including checklists | Declaration grammar, pair length, default/supplied ownership, every authored dependency field, qualified operator leaves, path casing, plans and workers are aligned. Corrected the model-selection paragraph to request-bound `--` dispatch and colon worker assignments. Corrected the observability identity shape and replaced the removed manager test anchor. Updated the model-selection coverage row. | `SkillIdentityTest`, `NamespaceIdentityFoundationTest`, `EvidenceExpressionParserTest`, `StartupSkillPathTest`, `RequestBoundToolCallingManagerTest`, `SpringAiToolCallbackAdapterTest`, `StepActionSelectionTest` and `NamespaceExecutionAuthorityIntegrationTest` |
| Complete binding example in `input-bindings.md` and output/evidence/planning YAML examples | `default:producer`, `default:finish-report`, `default:assess-equipment` and `default:plan-resolution` preserve default-owned targets. Names such as `classify-incident`, `prepare-report` and `repeat-contact` are local components. Generated task IDs and `parallelGroup` use their own contracts. | `DeclaredChildInputBindingsIntegrationTest#completeAuthoringExampleLoadsAsOneValidatedGeneration`, authored-resolution and parser matrices |
| `docs/console-setup.md`, `loomspan-console/README.md`, `loomspan-console/docs/mcp-contract-verification.md` | Catalog tuples and exact qualified drill-downs are aligned. Corrected the setup prompts to refer to the current quickstart's `default:triage-support-request`. | `DefaultRegisteredSkillCatalogTest`; Console `TestCatalogIdentityTupleIsRequiredAndExact`, `TestCatalogSelectsEachOwnerOfTheSameLocalName`, `SkillCatalog.test.tsx` and `target-context.spec.ts` |
| Complete `loomspan-console/agent-skills/loomspan-console/` runtime package and Console UI help/labels | Runtime/tool guide and playbooks use complete returned identity keys, preserve raw provider names and distinguish resolved skill facts. UI reads `registeredName` and recorded raw function names without a naming conversion. | Go `loomspan-console/internal/observability/service.go`, MCP `loomspan-console/internal/mcpadapter/skills.go`, TS `loomspan-console/web/src/observability/SkillCatalog.tsx`, `loomspan-console/web/src/observability/TraceRecords.tsx`, current shared namespace trace corpus |
| `docs/console-setup.md`, `docs/maintainers/`, `docs/building-jslt.md`, `loomspan-console/docs/` and release/setup sections | Reviewed installation, deployment, packaging and diagnostic links. Artifact filenames, servlet namespaces and model aliases such as `default-model` retain their own meanings. No naming migration instructions were found in current published guidance. | Current paths/configuration and public signatures. Changes to a namespace publication and provider legacy option descriptions are ordinary documented behavior. |

The single-hyphen review interpreted references by field. A local `warehouse-lookup` in `orders` means `orders:warehouse-lookup`; `warehouse:lookup` names the foreign target. `default-model` is a model alias. Java `findOrder()` is shown only with an explicit valid annotation name. Task IDs, input property names such as `customerMessage`, configuration data and raw provider evidence were preserved.

### Read-only Sidecar inventory and follow-ups

Reviewed sibling revision `4f4d683`. Paths below are relative to `../loomspan-sidecar/`. The sibling's existing untracked `ai/thoughts/future/` content was preserved. These examples document the current Sidecar and require downstream work before using the completed Framework contract.

| Reviewed documents/examples | Evidence and pending boundary |
| --- | --- |
| `README.md:65`, `README.md:83`, `docs/quickstart.md:4`, `docs/quickstart.md:92`, `examples/quickstart/README.md:4`, `examples/quickstart/triage-support-request.yaml:1`, `examples/quickstart/invoke-triage.py:18` | Declaration and invocation examples still use `triageSupportRequest` and a name-only execution URL. Update the complete example to a valid local name and an explicit namespace/local HTTP contract. |
| `docs/integration.md`, `agent-skills/loomspan-sidecar-authoring/references/integration.md:114` and `:143` | Execution reference teaches `POST /v1/skills/{name}/executions`; diagnosis example uses `default-tsTriageStructured`. Document exact components in requests and catalog results, and colon-qualified resolved failure facts. The future Sidecar route shape remains its workstream's decision. |
| `sdks/java/README.md:37`, `:39`, `:68`, `:200`; `sdks/python/README.md:398`, `:400`, `:415`, `:417`, `:431`, `:435` | Java and sync/async Python clients take one name, reuse `skill.name`, and teach path-segment validation instead of the component grammar. Update discovery values, get/submit signatures, argument validation, request encoding and examples together. |
| `docs/http-executor.md:16`, `agent-skills/loomspan-sidecar-authoring/references/rest-configuration.md:16`, `agent-skills/loomspan-sidecar-authoring/references/client-usage.md:37`, `agent-skills/loomspan-sidecar-authoring/examples/remote-authoring/record.yaml:1` | `updateOrder` and `readRecord` declarations are invalid under Framework grammar. `http` remains a valid default-owned executor. Convert declarations and semantic references together while preserving REST path parameters, business data and exact source labels. |
| `examples/quickstart/sidecar/skills/planner.yaml:1`, `:8`, `:9`, `examples/quickstart/sidecar/skills/identity-leaf.yaml:1`, `examples/quickstart/sidecar/skills/detail-leaf.yaml:1` | `quickstartPlanner`, `identityLeaf` and `detailLeaf` need valid declaration spellings and corresponding authored references. Preserve which leaf each dependency selects. |
| Authoring `SKILL.md`, all references under `agent-skills/loomspan-sidecar-authoring/references/`, `docs/setup.md`, `docs/architecture.md`, `docs/remote-recipes.md`, `docs/operations.md`, `docs/console-setup.md`, `examples/production/README.md` | Review file-mode loader path validation, exact supplied diagnostic labels, default owner, current catalog components and full resolved labels. Deployment references mostly delegate to Framework and Sidecar integration docs, so update those links/examples coherently. |
| `agent-skills/loomspan-sidecar-authoring/references/configuration-bundles.md:42`, `:64` and `agent-skills/loomspan-sidecar-authoring/references/management-api.md:140` | Bundle filenames are generated ordinal paths; `sourceLabel`/`sourceName` remain diagnostic labels. Preserve them verbatim. Document path checks at file/archive loading and identity checks at Framework validation/preparation without interpreting source labels as paths. |

### Read-only Cloud inventory and follow-ups

Reviewed sibling revision `49b83f3`. Cloud currently publishes planning documents under `../loomspan-cloud/ai/thoughts/future/`; no product README, SDK manual, standalone plugin guide or deployed publication examples exist in that checkout. Reviewed the prototype, product/backend/engine roadmaps, execution-engine API, publication pack format and application-variable conformance material.

| Document and location | Evidence and pending boundary |
| --- | --- |
| `roadmap.md:132` | Namespace generation still specifies `u{numericUserId}_{appSlug}`, underscore slugs and a 32-character maximum. Select portable namespace components and account for destination skill pairs' combined 64-character provider limit. |
| `publication-package-format.md:7`, `:24`, `:48`, `:62` | The existing top-level namespace directory and matching companion name are useful foundations. The text permits `Orders`/`orders` declarations. Require exact component grammar, canonical `SKILL.md`, lowercase definition paths relative to the owner directory and the Framework pair limit. General companion materials such as `README.md` retain authored spelling under archive rules. |
| `execution-engine-api.md:30`, `:61`, `:74`; `java-execution-engine-roadmap.md:83` | Invoke shape includes namespace but teaches a qualified encoded `skill`, including `claims-reviewInvoices`. Use separate namespace and local skill components and pass both directly into Framework facade/handoff signatures. Reuse `ValidatedSkill`/`SkillDescriptor` component accessors. Publication source labels remain exact diagnostic text. |
| `go-backend-roadmap.md:19`, `:57`, `:61` | Upload/path validation and promotion need destination component and complete pair validation. Promotion already renames the top directory, companion name and executor owner key. Validate every destination skill pair and preserve explicit foreign references, ordinary config and companion content. |
| `publication-package-format.md:56`, `go-backend-roadmap.md:63`, `v1-prototype-brief.md:25` | Client-only export and promotion should retain the application's companion identity and direct invocation components. These are pending designs, without executable evidence in Cloud. |
| Entire Cloud future set searched for plugin distribution guidance | No dedicated plugin submission contract exists. If a plugin is offered, validate the plugin/exported companion pair against that host's combined limit separately from runtime function pair validation. The Framework proposal supplies the future design context. |

No Sidecar or Cloud file was edited by this ticket. PR 37.8 should cite this inventory from the Sidecar future handoff and preserve the verified Framework/downstream distinction.

### Verification

The focused acceptance command passed **144 tests, zero failures/errors and one conditional symbolic-link capability skip**. The skipped case is `StartupSkillPathTest#literalSymlinkKeepsItsLogicalFilename`, whose host capability requirement is independent of these documentation edits. Log is `target/pr377-docs-verification.log`.

```powershell
.\mvnw.cmd test '-Dtest=SkillIdentityTest,PortableDeclarationIdentityIntegrationTest,StartupSkillPathTest,ApplicationApiValueTest,DefaultSkillCatalogTest,NamespaceIdentityFoundationTest,EvidenceExpressionParserTest,NamespaceExecutionAuthorityIntegrationTest#exactProviderOfferExecutesResolvedChildAndKeepsRawEvidence+plannedAssignedAndAutomaticCallsUseExactQualifiedIdentity,DeclaredChildInputBindingsIntegrationTest#completeAuthoringExampleLoadsAsOneValidatedGeneration,DefaultRegisteredSkillCatalogTest,LoomspanPublicSurfaceArchitectureTest' '-DfailIfNoTests=false' '-DargLine=-javaagent:C:/Users/mgiacomi/.m2/repository/org/mockito/mockito-core/5.24.0/mockito-core-5.24.0.jar'
```

A static scan of the 48 current published Markdown files under `README.md`, `docs/`, `agent-skills/`, `loomspan-console/README.md`, `loomspan-console/docs/` and `loomspan-console/agent-skills/` checked all 19 root declaration/manifest `name:` examples against the component grammar, length and reserved substrings. All passed. The same scan found no remaining `namespace-localName`, `namespace-local-name`, `CanonicalToolCallingManager` or `triageSupportRequest` markers. Single-hyphen references were reviewed semantically as recorded above.

`git -c core.safecrlf=false diff --check` passed. Source/signature inspection confirms there are no production changes in this ticket. The sibling working-tree statuses match their initial state, with only the existing untracked Sidecar future directory present.

Complete integrated Framework/Console builds and the previously recorded shared variable-fixture reconciliation remain assigned to PR 37.8. Hosted provider accuracy, downstream Sidecar/Cloud implementation and standalone deployment qualification were not run by this documentation audit. No required PR 37.7 check remains open. Optional developer checks are none.
