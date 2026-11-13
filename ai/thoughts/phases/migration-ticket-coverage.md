# Migration Ticket Coverage

Created November 13, 2026. The 17 PR 30.phase.sequence tickets carry the accepted
Framework requirements into fresh implementation contexts. All recommend the
Full 5-Step Pipeline, with high confidence. Ticket creation does not start execution.

## Ticket index

| Proposed PR | Ticket | Scope owner |
| --- | --- | --- |
| 30.1.1 | [Select and integrate the dependency baseline](../tickets/loomspan-pr-30.1.1-select-and-integrate-the-dependency-baseline.md) | Own only platform selection and mechanical compatibility. Provider behavior belongs to PRs 30.1.2–30.1.5; the canonical validator and declaration migration belong to PR 30.2.1. Confirm NetworkNT compatibility without prematurely replacing all consumers. |
| 30.1.2 | [Replace OpenAI and Ollama paths with shared Responses](../tickets/loomspan-pr-30.1.2-replace-openai-and-ollama-paths-with-shared-responses.md) | Own Responses transport/options, ordinary canonical-message part preservation, precision and legacy OpenAI/Ollama removal. PR 30.1.5 owns lifecycle classification/accounting; PR 30.3.1 owns schema attachment to ordinary outputs and composed-message preservation; PR 30.3.3 owns final-repair history. Establish reusable protocol fixtures without implementing those later policies here. |
| 30.1.3 | [Preserve Anthropic schemas and native options](../tickets/loomspan-pr-30.1.3-preserve-anthropic-schemas-and-native-options.md) | Own Anthropic adapter transport/options only. The embedded typed-map developer contract describes the input schemas this adapter must carry; PRs 30.2.2/30.2.3 own Java generation and local validation, and PR 30.3.1 owns ordinary native-output orchestration. Do not implement the other drivers described in preservation context. |
| 30.1.4 | [Preserve Gemini raw schemas and native options](../tickets/loomspan-pr-30.1.4-preserve-gemini-raw-schemas-and-native-options.md) | Own Gemini adapter transport/options only. The embedded typed-map contract defines wire fidelity, not permission to implement Java generation or another driver. PRs 30.2.2/30.2.3 own generation/validation and PR 30.3.1 owns ordinary output orchestration. |
| 30.1.5 | [Reject explicit refusal and incomplete responses with faithful accounting](../tickets/loomspan-pr-30.1.5-reject-explicit-refusal-and-incomplete-responses-with-faithful-accounting.md) | Own status/evidence retention and exactly-once accounting, not final-repair history. Reuse the canonical parts work from PR 30.1.2. PRs 30.3.1/30.3.2 must exercise the same classifier through their newly migrated output/planning paths; do not duplicate classifiers in those consumers. |
| 30.2.1 | [Introduce canonical standard schemas and lossless ingestion](../tickets/loomspan-pr-30.2.1-introduce-canonical-standard-schemas-and-lossless-ingestion.md) | Own canonical representation, compilation, profile checks, lossless skill-document ingestion and shared diagnostics. Define recognized annotation syntax/placement here; execution consumers belong to PRs 30.2.2–30.3.4. Do not keep legacy DTOs alive solely to make intermediate consumers compile. Preserve separate executor configuration handling. |
| 30.2.2 | [Align generated Java contracts and binding](../tickets/loomspan-pr-30.2.2-align-generated-java-contracts-and-binding.md) | Own Java schema generation, effective binding introspection and demonstrated binding corrections. The embedded unconstrained/runtime contracts constrain what is published and preserved; PR 30.2.3 owns the NetworkNT runtime-value adapter and JSON validation. Provider captures belong to Phase 1 and consumer integration, not additional adapter rewrites here. |
| 30.2.3 | [Migrate input validation and ordinary tool ingress](../tickets/loomspan-pr-30.2.3-migrate-input-validation-and-ordinary-tool-ingress.md) | Own input-validation ingress and callback decoding. Reuse generation from PR 30.2.2. PR 30.2.4 owns attachment-only materialization; PR 30.2.5 owns binding projection/assembly migration. Preserve existing ordering at their seams. The explicit runtime adapter here is not an attachment descriptor adapter. |
| 30.2.4 | [Migrate attachment validation materialization and trace descriptors](../tickets/loomspan-pr-30.2.4-migrate-attachment-validation-materialization-and-trace-descriptors.md) | Own Framework attachment validation/materialization, coordinator records, engine descriptor recording and journal/observer propagation. Console-specific requirements embedded below define the receiving contract; PR 30.4.1 owns their UI implementation. Preserve all attachment behavior when PR 30.3.2 later migrates step schemas. |
| 30.2.5 | [Migrate binding ownership projection and composition](../tickets/loomspan-pr-30.2.5-migrate-binding-ownership-projection-and-composition.md) | Own child-input projection/assembly and output projection/composition/dependency validation. PR 30.3.1 consumes projected output in ordinary generation; PR 30.3.2 consumes it in planning/step paths; PR 30.3.4 owns structural effective-catalog metadata. Preserve binding semantics rather than redesigning them. |
| 30.3.1 | [Apply native contracts to ordinary skill output](../tickets/loomspan-pr-30.3.1-apply-native-contracts-to-ordinary-skill-output.md) | Own ordinary output schema propagation, request mappings and validation/composition. The broad migration checklist below is context: complete this boundary, not other tickets. PR 30.3.2 owns planner/step consumers; PR 30.3.3 owns completed-history/tool-free repair enforcement; PR 30.3.4 owns evidence metadata extraction. Reuse adapter fixes from Phase 1. |
| 30.3.2 | [Migrate planning and step execution contracts](../tickets/loomspan-pr-30.3.2-migrate-planning-and-step-execution-contracts.md) | Own planner and step consumers of the existing shared native-schema path. Preserve existing final-phase tool rejection, action validation, planning correction and step-loop ownership. Do not revive the withdrawn broad StepAction/native-schema finding or invent an additional action response protocol. Ordinary advisor repair is PR 30.3.3. |
| 30.3.3 | [Preserve completed work during ordinary final-output repair](../tickets/loomspan-pr-30.3.3-preserve-completed-work-during-ordinary-final-output-repair.md) | Own ordinary schema/linter/evidence correction history and tool prohibition as one change. Consume existing provider status guards, canonical parts, attachment and binding behavior. Preserve planning and step-loop policies; no alternative tool loop or cross-invocation memory. |
| 30.3.4 | [Migrate TypeSafe evidence metadata and effective catalogs](../tickets/loomspan-pr-30.3.4-migrate-typesafe-evidence-metadata-and-effective-catalogs.md) | Own native TypeSafe schema consumers, evidence extraction/claim identity and effective catalog metadata. Shared structural validation is already provided. Console UI work is PR 30.4.1. Do not introduce TypeSafe output validation or modify its native evaluation protocol. |
| 30.4.1 | [Update Console schema and attachment views](../tickets/loomspan-pr-30.4.1-update-console-schema-and-attachment-views.md) | Own Console and corresponding contract/fixture changes only. Framework descriptor production is PR 30.2.4; effective schema production is PR 30.3.4. Embedded producer requirements define preservation/integration checks, not a second implementation of their logic. |
| 30.4.2 | [Finish authoring migration and removal audit](../tickets/loomspan-pr-30.4.2-finish-authoring-migration-and-removal-audit.md) | Own remaining authoring/documentation conversion and the removal audit. Earlier tickets own functional implementation and their necessary regression fixtures. Fix concrete residue, but do not treat incomplete major features as this ticket's generic cleanup scope. |
| 30.4.3 | [Verify integrated Framework migration readiness](../tickets/loomspan-pr-30.4.3-verify-integrated-framework-migration-readiness.md) | Own final integrated assurance and concrete cross-ticket corrections. Do not silently absorb missing major features or weaken requirements to obtain green checks. Test-suite fixtures/proxies, deployed hosts, budgets and live execution remain a later workstream. |

## Requirement ownership

| Requirement family | Implementation owner | Integrated preservation or verification |
| --- | --- | --- |
| TBD-01/02 evidence distinctions and both-workstream completion | 30.4.3 | Every ticket distinguishes policy, historical proof, implementation and live gates |
| TBD-03 shared openai and no Ollama branches | 30.1.2 | 30.4.2 removal audit; 30.4.3 integrated proof |
| TBD-04/05 standard usage semantics | 30.2.1 | 30.2.3 inputs; 30.2.5 binding openness; 30.3.1 prompt/output openness |
| TBD-06 validator/profile/resource policy | 30.2.1 | All schema consumers; 30.4.3 final suite |
| Step-action decimals | 30.3.2 | 30.4.3 |
| Callback decimals, complete JSON and root shape | 30.2.3 | 30.4.3 |
| Ordinary output decimals | 30.3.1 | 30.4.3 |
| TBD-07 request mappings and canonical preservation | 30.3.1 | Driver serialization in 30.1.2/3/4; planner consumers in 30.3.2 |
| Responses wire decimal fidelity / SAI-06 | 30.1.2 | 30.3.1 consumer capture; 30.4.3 |
| SAI-02/03 Anthropic options/root schema | 30.1.3 | 30.3.1 and 30.3.2 |
| SAI-04/05 Gemini raw tool/output schemas | 30.1.4 | 30.3.1 and 30.3.2 |
| TBD-08 non-strict tools and local pre-dispatch checks | 30.1.2/3/4 wire settings; 30.2.3 validation | 30.3.2 null toolArguments; canonical authorization preserved |
| TBD-09 failure timing | 30.2.1 declaration failures; 30.3.1 request/output failures | 30.1.5 status failures; 30.3.2 planning; existing transport owner retained |
| TBD-10 ownership/projection/composition including numeric enums | 30.2.5 | 30.3.1 ordinary output; 30.3.2 step/planning |
| TBD-11 repair history and side effects | 30.3.3 | Parts prerequisite 30.1.2; status prerequisite 30.1.5 |
| TBD-12 status evidence and usage | 30.1.5 | 30.3.1/2 migrated consumer rejection; 30.4.3 |
| TBD-13 Java authority, typed maps, generics, naming, enums, remapping, UUID, recursion and runtime ordering | 30.2.2 | 30.2.3 input runtime checks |
| Declared map key / JsonUnwrapped restrictions and Resource container binding | 30.2.2 | 30.4.3 |
| Unconstrained inputs, open-property compatibility and runtime map keys | 30.2.3 | Generated shape 30.2.2; final public invocation 30.4.3 |
| Runtime marker, authored resource descriptions and direct Java-value adapter | 30.2.2 generation/binding; 30.2.3 validation | Provider extraction 30.3.1; canonical preservation everywhere |
| Skill YAML precision, non-string keys and non-finite numbers | 30.2.1 | TypeSafe consumers 30.3.4 |
| Attachment syntax/profile | 30.2.1 | Runtime 30.2.4; authoring 30.4.2 |
| Attachment MIME, maps, bounded reads, unreadability, failures, names, nulls and paths | 30.2.4 | 30.3.2 step preservation; 30.3.3 correction preservation |
| Attachment descriptors in coordinator/engines/journal/observer | 30.2.4 | Console 30.4.1; final 30.4.3 |
| Evidence annotations, exact claims and effective catalog metadata | 30.3.4 | Shared placement 30.2.1; planning/runtime preservation 30.3.2/3; Console 30.4.1 |
| Native TypeSafe boundary and request/response precision | 30.3.4 | Existing native regressions; no paid evaluation |
| TBD-14 dependency and native effort/cap prerequisites | 30.1.1 platform; 30.1.2/3/4 options | 30.3.1/2 consumer captures; 30.4.3 reconciliation |
| Console contracts and inert schema/attachment views | 30.4.1 | 30.4.3 |
| Authoring, active fixtures and complete legacy removal | Each functional owner migrates its required fixtures; 30.4.2 residual audit | 30.4.3 |

## Exact embedded source coverage

Review follow-up: PR 30.2.1 also carries the attachment declaration shape, nested/items
and typed-map placement rules, and evidence declaration placement/rejection cases from
the accepted attachment/evidence sections. PRs 30.2.4 and 30.3.4 explicitly reuse those
shared checks for integration coverage. Malformed-MIME parsing and attachment runtime
work remain in 30.2.4; evidence expression compilation/runtime and native TypeSafe
attachment rejection remain in 30.3.4. No ticket dependencies or build allowances change.

Ranges below refer to the November 13 ticket-authoring snapshots. They provide audit
traceability, not instructions to reread entire documents in place of ticket content.
Repeated ranges carry preservation context; the ownership table and ticket scope decide
who implements each boundary. Technical requirements and acceptance cases are embedded
in the tickets; raw reports, captured requests and source snapshots remain linked artifacts.

| Ticket | Source | Lines | Opening text |
| --- | --- | --- | --- |
| 30.1.1 | roadmap | 220–239 | Phase 1 Select and verify the dependency |
| 30.1.1 | roadmap | 585–599 | Dependency selection is a platform decision |
| 30.1.1 | research | 437–452 | Adapter and SDK evidence |
| 30.1.1 | research | 609–640 | Executable verification follow-up — November 10, 2026 |
| 30.1.2 | roadmap | 106–185 | The problem we are solving |
| 30.1.2 | roadmap | 240–264 | Phase 2 Integrate the Responses adapter |
| 30.1.2 | roadmap | 600–668 | What Spring AI already supplies |
| 30.1.2 | roadmap | 688–877 | Observability must distinguish content from protocol state |
| 30.1.2 | roadmap | 1263–1303 | Responses schema decimal fidelity — agreed audit correction, November 12, 2026: |
| 30.1.2 | research | 414–422 | The four routes behind openai |
| 30.1.3 | roadmap | 1963–2039 | Accepted SkillMethod/SkillExecutor developer model and typed-map inputs — November 11, 2026 |
| 30.1.3 | research | 459–465 | 2. Anthropic output: native outputConfig(...) carries an intact raw schema. |
| 30.1.4 | roadmap | 1963–2039 | Accepted SkillMethod/SkillExecutor developer model and typed-map inputs — November 11, 2026 |
| 30.1.4 | research | 466–477 | 4. Gemini output: GoogleGenAiChatModel.jsonToSchema() deserializes into |
| 30.1.4 | research | 641–661 |  |
| 30.1.5 | roadmap | 669–687 | Response status and retry ownership need explicit handling |
| 30.1.5 | roadmap | 1810–1954 | TBD-12: Refusals, incomplete responses, and partial tool calls |
| 30.2.1 | roadmap | 272–405 | Settled schema decisions and rationale |
| 30.2.1 | roadmap | 930–951 | TBD-04: Shared schema format versus identical usage restrictions |
| 30.2.1 | roadmap | 985–1037 | TBD-06: Initial JSON Schema profile and validation behavior |
| 30.2.1 | roadmap | 2616–2651 | Skill YAML decimal precision — agreed audit correction: preserve finite decimal |
| 30.2.1 | roadmap | 2681–2696 | Non-finite YAML numbers — agreed audit correction: reject non-finite numeric |
| 30.2.1 | research | 92–115 | Declarations and current validation |
| 30.2.1 | research | 727–753 | Existing limits and coverage |
| 30.2.1 | research | 755–817 | NetworkNT 3.0.9 control semantics |
| 30.2.2 | roadmap | 1963–2039 | Accepted SkillMethod/SkillExecutor developer model and typed-map inputs — November 11, 2026 |
| 30.2.2 | roadmap | 2058–2371 | Retained generated-input corrections |
| 30.2.2 | roadmap | 2697–2778 | Non-attachment runtime-reference metadata — agreed November 11, 2026 |
| 30.2.3 | roadmap | 1075–1113 | Ordinary tool-callback decimal precision — agreed audit correction: decode raw |
| 30.2.3 | roadmap | 1304–1419 | TBD-08: Native strict tool-argument enforcement |
| 30.2.3 | roadmap | 2317–2404 | Unconstrained Object input values — agreed November 11, 2026 |
| 30.2.3 | roadmap | 2652–2680 | Runtime map keys — agreed audit correction: reject non-string keys in runtime |
| 30.2.3 | roadmap | 2697–2735 | Non-attachment runtime-reference metadata — agreed November 11, 2026 |
| 30.2.3 | roadmap | 2779–2832 | Direct Java runtime-value validation — agreed November 11, 2026 |
| 30.2.4 | roadmap | 2833–3177 | Attachment input declaration — agreed November 11, 2026 |
| 30.2.4 | research | 1014–1105 | TBD-13 attachment investigation — November 11, 2026 |
| 30.2.5 | roadmap | 396–404 | Retain internally generated false property schemas for framework-bound child-input |
| 30.2.5 | roadmap | 1502–1667 | TBD-10: Output-binding projection and fully bound outputs |
| 30.2.5 | research | 120–123 | / Java-generated tool inputs / LoomspanMethodInputSchemaGenerator, SkillMethodBeanPostProcessor / Generator already labels schemas 2020-12. Produces closed para |
| 30.3.1 | roadmap | 406–530 | Shared baseline and documented provider differences |
| 30.3.1 | roadmap | 939–984 | TBD-05: Omission, explicit null, and object openness |
| 30.3.1 | roadmap | 1114–1145 | Ordinary output-validation decimal precision — agreed audit correction: parse |
| 30.3.1 | roadmap | 1180–1262 | TBD-07: Selecting provider differences without a capability registry |
| 30.3.1 | roadmap | 1448–1501 | TBD-09: Unsupported schemas/models and failure timing |
| 30.3.1 | research | 365–435 | Provider feature matrix |
| 30.3.2 | roadmap | 1038–1074 | Step-action decimal precision — agreed audit correction: decode model-supplied |
| 30.3.2 | roadmap | 1420–1447 | Step-action null toolArguments — agreed audit correction: reject explicitly null |
| 30.3.2 | roadmap | 1448–1501 | TBD-09: Unsupported schemas/models and failure timing |
| 30.3.2 | research | 125–128 | / Tool exposure/execution / BoundCapability, SpringAiToolCallbackAdapter, ExecutionCoordinator / Schema strings cross capability metadata into provider tools; l |
| 30.3.3 | roadmap | 1668–1809 | TBD-11: Correction history, tool availability, and side effects |
| 30.3.4 | roadmap | 2405–2615 | Output evidence metadata — agreed November 11, 2026 |
| 30.3.4 | research | 133–144 | TypeSafeDeclaration |
| 30.4.1 | roadmap | 2468–2517 | Effective-schema display and catalog metadata — agreed November 11, 2026 |
| 30.4.1 | roadmap | 3119–3177 | Attachment descriptor propagation — agreed audit correction: after successful |
| 30.4.1 | research | 145–161 | DefaultSkillCatalog |
| 30.4.2 | roadmap | 3–102 | Direction and scope |
| 30.4.2 | roadmap | 826–877 | Remove the old OpenAI-compatible implementation |
| 30.4.2 | research | 104–115 | 8 files contain input_schema: and 31 contain output_schema:; under |
| 30.4.2 | research | 162–169 | Migrate output-contracts, |
| 30.4.3 | roadmap | 531–572 | Phase 3 Verify protocol and framework behavior |
| 30.4.3 | roadmap | 878–929 | Required acceptance evidence |
| 30.4.3 | roadmap | 3178–3225 | TBD-14: Live acceptance matrix and implementation gates |
| 30.4.3 | roadmap | 3519–3542 | Evidence, limitations and outstanding gates |
| 30.4.3 | research | 609–661 | Executable verification follow-up — November 10, 2026 |

## Source snapshot identities

- `spring-ai-responses-migration.md` SHA-256: `1157dd4d4d68605ae8b0e9caf91ea6465fed262d98ebb980b3ceba33d35c3b44`
- `standard-json-schema-research.md` SHA-256: `b5cc7e3053a03971afb95579d029352942aa3b440b52c1fca4512d06a46a51ac`

## Deliberately excluded or later work

- Deferred Optional/polymorphic/array-shaped/delegating-creator Java investigations
  stay excluded; removed details and probes are not reconstructed.
- The already completed stream-closure repair is preservation context, not new work.
- Broader historical validator benchmarks, alternative libraries, excluded schema
  features and superseded proposals are evidence only. Applicable findings, versions,
  limits and reproduction locations are carried into tickets; raw measurement tables
  remain in the research report and retained artifacts, not new acceptance requirements.
- Deployed/live test-suite implementation follows Framework completion. The accepted
  suite owns its protected fixtures, host matrix, models, budgets, access/pricing,
  deadlines and paid execution. Framework tickets carry only their deterministic
  prerequisites and downstream evidence boundaries.
- The earlier OpenRouter comparison-scope question remains open for later suite work;
  no extra comparison or budget expansion is authorized.
- External upstream submissions and live provider calls are not authorized by these
  tickets. Exact fixed artifact availability is an implementation evidence gate.

## Build restoration and verification

PR 30.1.1 may expose the explicit provider transition restored by 30.1.2–30.1.5.
PR 30.2.1 may break consumers; 30.2.2 restores Java declarations, 30.2.3 inputs/callbacks,
30.2.4 attachments, 30.2.5 bindings, 30.3.1 ordinary output, 30.3.2 planning/step and
30.3.4 TypeSafe/catalog consumers. PR 30.3.3 completes ordinary repair integration.
The Framework build should be green by the end of Phase 3. Phase 4 requires green
Framework/Console checks; 30.4.3 reconciles and reruns every blocked required check.
An allowed intermediate failure is a recorded obligation, never passing evidence.
