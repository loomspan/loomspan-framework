# PR 30.4.1 — Update Console schema and attachment views

## Outcome

Update Console contracts/rendering for standard effective schemas and attachment descriptor records. Use existing content endpoints and chunk reconstruction. Preserve original YAML, inert rendering and diagnostic fidelity. No new browser validator or file dereferencing.

## Scope and sequencing

**Dependencies:** PR 30.2.4 and PR 30.3.4.

**Expected build state:** Framework and Console builds should be green.

No new intentional build failure is authorized at this milestone. PR 30.4.3 is the final integrated verification gate, not a waiver of this ticket's checks.

Own Console and corresponding contract/fixture changes only. Framework descriptor production is PR 30.2.4; effective schema production is PR 30.3.4. Embedded producer requirements define preservation/integration checks, not a second implementation of their logic.

Execute in the agreed numerical order unless working on an independent prerequisite. Dependencies describe technical prerequisites, not permission to duplicate another ticket's implementation.

## Shared migration constraints

Exactly three chat integrations remain: shared OpenAI Responses for OpenAI, Meta,
OpenRouter and Ollama; native Anthropic; native Gemini. TypeSafe remains a separate
native evaluator. Retain Spring AI ChatClient/tool-loop ownership. No second adapter,
transport, agent loop, capability registry, conversation database, protocol selector,
automatic fallback or invented public API is authorized.

Canonical inputs, prompts, outputs, tool arguments, traces and diagnostics retain
content fidelity. Do not classify sensitivity, scan secrets, mask or sanitize content.
Enforce authentication, authorization and explicit access boundaries. Existing size
limits and explicit truncation are resource controls. Preserve only the existing
ExecutionJournalProjector field-name redaction exception for the derived journal and
SkillTemplate observer; do not broaden it to canonical traces, Console or invoke results.

The supported Java API is the closed allowlist of public top-level ai.loomspan.api
types in LoomspanPublicSurfaceArchitectureTest. Internal public types are implementation
details; autoconfigure signatures are not an SPI. User-visible keys/behavior require
deliberate migration. Do not expose internal/autoconfigure types in public signatures.
Run LoomspanPublicSurfaceArchitectureTest after production type changes; record it as
blocked if intentional intermediate breakage prevents execution and rerun on restoration.

The accepted standard profile is Draft 2020-12 with NetworkNT Jackson 3 (3.0.9 is the
verified starting candidate, not an irrevocable final pin). Complete contracts have
object roots; property trees support ordinary types, explicit nullable type arrays,
scalar enums/descriptions, homogeneous nested arrays, boolean openness, standard formats
and minimum/maximum/minLength/maxLength/minItems/maxItems. Ordinary inputs additionally
support typed maps and unconstrained {} leaves. Native output/planning typed maps,
general unions/composition, references, defaults, arbitrary patterns and other unapproved
features remain excluded. Internally generated false property schemas preserve binding
ownership. Exact names, standard omission/null/openness and no coercion apply. No
automatic all-required conversion, null insertion, closure, wrapping or map reshaping.
Native strict tools are deferred; retain non-strict schemas, local pre-dispatch validation
and authorization. Retain standard local checks even when a provider ignores assertions.

Deferred Java declaration exploration (Optional wrappers, polymorphic inputs,
array-shaped DTOs and delegating creators) is excluded. Do not restore removed probes
or invent their restrictions. The retained map-key, JsonUnwrapped and UUID corrections
remain owned by PR 30.2.2. Native generated attachments are excluded.

No live provider calls, model downloads, external issue/PR submissions or test-suite
implementation are authorized by this ticket. All verification here is deterministic
or local build/startup. The neighboring suite owns later deployed/live acceptance;
missing live evidence is not permission to weaken Framework requirements.

## Acceptance criteria

- [ ] Console renders original YAML and standard effective schemas inertly, including TypeSafe and forwarding cases, with coherent Java/Go/TypeScript contracts.
- [ ] Attachment views use recorded descriptors and complete chunked payloads through existing endpoints; missing fields render correctly without loading resources or parsing prompts.
- [ ] Console retains content fidelity and existing access boundaries, and separate MCP/agent-evaluation schema systems remain intact.
- [ ] Every applicable mandatory case in the detailed contract below has executable evidence, or an explicitly identified downstream integration owner and blocked-check record consistent with this ticket's build allowance; no in-scope required behavior is silently deferred.
- [ ] Owned fixtures and callers migrate with the behavior; preservation controls and the public-surface check are run when buildable, and all failures/blocked checks are reported accurately.

**Verification emphasis:** Contract/parity tests, effective-schema display, TypeSafe metadata, attachment details, missing optional fields and chunked payloads.

## Console contract boundaries

Follow loomspan-console/AGENTS.md. Java/Go/TypeScript DTOs, fixtures and semantic tests
move coherently; exact consoleCompatibilityVersion matching and an empty-on-restart
in-memory catalog do not need aliases, backfills or dual readers. No public descriptor
signature change is required for the agreed schema/attachment display.

Console consumes canonical trace artifacts, not journal-redacted events. Preserve
recorded content and access boundaries. Reuse record/content endpoints and logical
payload reconstruction for chunked FRAME_METADATA_RECORDED attachment collections.
Keep raw inspection, missing-field behavior and inert text rendering. No prompt parsing,
resource dereferencing, file reads or live-activity attachment projection is selected.
Console's Go MCP output schema validation and agent-evaluation schemas are separate
contracts; retain them and run affected parity regressions rather than replacing them.

Existing verification entry points include go test ./..., go run ./internal/buildtool
verify, the web tests and ConsoleTraceFixtureCorpusTest. Use LF for committed fixture
files and README. Intentional fixture regeneration must preserve semantic assertions;
do not regenerate expected output merely to conceal a failure.

## Detailed accepted contract and evidence

The following bounded extracts carry the applicable decisions, exact acceptance cases
and historical code/test anchors into this ticket. Scope ownership above determines
which cross-boundary statements are implementation here versus preservation context.
Statements about earlier research runs remain historical; do not claim they were rerun.
References to future tickets in retained text mean requirements carried by this ticket
set, not permission to postpone this ticket's owned work. Older proposals do not override
accepted decisions or the explicit exclusions above.

### Contract 1

#### Effective-schema display and catalog metadata — agreed November 11, 2026

Preserve effective output schemas as structural result metadata, distinct from canonical
skill declarations and execution-local evidence contracts. SkillDependencyValidator
currently prepares this metadata, follows output_from to its producer, and deliberately
excludes child-local evidence annotations. DefaultSkillCatalog and
DefaultRegisteredSkillCatalog expose the prepared schema; ObservabilityDtoMapper passes
it through and Console SkillDetail renders it as inert text beside original YAML.
TypeSafe contributes its generated descriptive result schema through the same path.

Migrate this representation to standard JSON Schema. Replace the old fixed-field
reconstruction with a faithful structural copy removing only recognized execution
annotations at schema-node positions. Preserve all supported standard constraints,
descriptions and similarly named data properties. Do not use a provider-adapted request
copy for catalog metadata: provider-specific omissions must not weaken the displayed
contract. Canonical declarations, original YAML and compiled evidence contracts retain
their metadata; the structural view omits x-loomspan-evidence deliberately.

Rationale: a forwarding skill can expose its producer's output structure without
appearing to inherit that producer's private child-execution requirements. Retaining
canonical annotations does not require propagating them into every derived view.
Preserve existing forwarding/binding semantics, source text fidelity, catalog snapshots,
public descriptor signatures and Console display behavior. No browser validator, new
schema UI or extra public API is required. Keep Unspecified when no output schema exists.

Mandatory ticket acceptance tests:

- Direct and output_from-forwarded effective schemas retain standard assertions,
  descriptions, nullability and supported nested structure without legacy nullable
  vocabulary or fixed-field keyword loss. Preserve forwarding chains and cycle errors.
- Exclude child-local x-loomspan-evidence from structural catalog/display metadata,
  while canonical declarations, original YAML and compiled evidence contracts retain
  annotation/expression content. No producer evidence requirements become parent rules.
- Preserve data properties named evidence or x-loomspan-evidence, including nested
  properties. Schema-node extraction must not delete arbitrary matching data names.
- Public catalog, observability DTO and Console agree on the effective schema. Preserve
  exact original YAML separately and inert text rendering; absent schemas remain
  Unspecified. Cover TypeSafe-derived schema propagation and forwarding.
- Provider request adaptations do not mutate effective/catalog/canonical schemas.
  Preserve generation snapshot coherence and existing binding/output authority tests.
- Update affected Java and Console fixtures/contracts as needed without changing
  public descriptor signatures or adding an independent client-side schema validator.

No production implementation, new test-run acceptance or ticket creation is claimed.

Evidence anchors (source/test inspection; new acceptance remains pending):

- `src/main/java/ai/loomspan/internal/skill/SkillDependencyValidator.java:359` (`private static Map<String, Object> outputSchemaMetadata`) reconstructs selected legacy fields and omits child evidence; resolveOutputSchema immediately above follows forwarding chains.
- `src/test/java/ai/loomspan/internal/skill/SkillDependencyValidatorTest.java:21` (`void transitiveReturnUsesCandidateDeclarationsContractsAndVisibility`) covers transitive contracts; `loomspan-console/web/src/observability/SkillDetail.test.tsx:49` (`TypeSafe detail retains source label, original declaration and descriptive schema`) covers inert display of existing schema text, not preservation of newly supported standard keywords.

### Contract 2

**Attachment descriptor propagation — agreed audit correction:** after successful
materialization, record the existing attachment descriptors once per owning mission
frame in both standard and step engines, before planning or a possible no-model
completion. Reuse FRAME_METADATA_RECORDED with an attachments collection in its data
payload and existing internal trace recording. Record descriptors only, never Resource
objects or file contents. Preserve original references, field paths, available names,
MIME/media types, sizes, hashes and metadata; direct Resources remain resources, with
no invented reference or filename. A later mission failure retains this evidence;
failed materialization must not manufacture successful descriptors.

Console consumes canonical trace artifacts, not the execution journal. Add an
Attachments detail view for these records using its existing record/content endpoints
and logical payload reconstruction, including chunked payloads. Display the recorded
descriptor fields without parsing model prompts or dereferencing files. Existing raw
record inspection remains available. No new public API or endpoint is required.
Project the same per-frame descriptors into the existing SKILL_FINISHED journal entry,
including failed missions after successful materialization, so the existing observer
mapping carries them too. Retain the journal's existing field-name redaction exception
without broadening it or applying it to canonical traces or Console. Live attachment
display is deferred: the bounded scalar live-activity projection does not carry this
collection automatically.

Add focused tests for both engines recording descriptors once on the correct mission
frame, including no-model completion and failure after materialization; failed
materialization must not emit successful descriptors. Cover reference and direct
Resource inputs, multiple/nested attachment association, absent names/optional fields,
and absence of file bytes/Resource serialization. Verify journal and observer details
retain the collection with existing journal redaction behavior. Console tests must
render descriptor fields from completed traces, including chunked payloads and missing
optional fields, using existing endpoints without resolving references or loading file
contents. Existing raw-record/chunked-content tests establish reusable transport and
rendering behavior, not attachment-view coverage. Source inspection and the isolated
journal projection probe establish the propagation gap; implementation, these focused
tests and live acceptance remain pending.

Remaining verification is implementation acceptance, not authority to invent policies:
verify real supported files on selected provider/model routes, multiple attachment
association/order, attachment-plus-schema/tool calls, and attachment preservation in
correction requests under TBD-11. The existing mock MIME matrix proves serialization
only. Unsupported media follows TBD-09, without conversion/protocol fallback. Preserve
the existing per-attachment size limit; no new count/aggregate limit is selected here.

Attachment investigation completed November 11, 2026: see the
[TBD-13 attachment evidence](../phases/standard-json-schema-research.md#tbd-13-attachment-investigation--november-11-2026).
Offline captures verify the existing Resource-to-media path through all three pinned
adapters (18 input cases), plus OpenAI/Gemini generated-image parsing (two output
cases). Images/PDFs share a serialization path; other MIME types differ. No live
acceptance is claimed. Loomspan currently extracts model response text, not generated
media. Subsequent decisions below/above settle attachment-input design and defer native
generated attachments. This evidence does not authorize new provider tools, media
generation, file storage or public API.

Evidence anchors (source/test inspection; new acceptance remains pending):

- `src/main/java/ai/loomspan/internal/runtime/DefaultMissionExecutionEngine.java:88` (`RenderedMissionInput renderedInput =`) and `src/main/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngine.java:236` (`RenderedMissionInput renderedInput =`) are the successful-materialization insertion points; retain their existing descriptor helpers. `src/main/java/ai/loomspan/internal/runtime/trace/ExecutionJournalProjector.java:103` (`details.put("skillName", record.route())`) builds lifecycle details without attachments.
- `src/main/java/ai/loomspan/internal/skillapi/DefaultSkillTemplate.java:252` (`observer.accept(executionViewMapper.map(session))`) and `src/main/java/ai/loomspan/internal/skillapi/SkillExecutionViewMapper.java:42` (`List<SkillExecutionEvent> events =`) show the existing journal-to-observer route.
- `src/test/java/ai/loomspan/internal/skill/MissionExecutionEngineTest.java:342` (`planningReceivesDescriptorsAndMissionTraceRedactsAttachmentBytes`) and `src/test/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngineTest.java:1894` (`stepLoopSendsAttachmentMediaOnExecutionSteps`) prove existing planning/media behavior, not frame-metadata recording; `src/test/java/ai/loomspan/internal/runtime/trace/ExecutionJournalProjectorTest.java:24` (`projectsMissionLifecycleWithoutExposingInputOrAuthentication`) covers lifecycle projection without this collection.
- `loomspan-console/web/src/observability/TraceRecords.tsx:212` (`async function readCompleteContent`) reconstructs content ranges, while its generic raw-record action is the existing fallback. `loomspan-console/web/src/observability/TraceRecords.raw.test.tsx:101` (`loads all ranges and pretty prints the complete raw record envelope`) proves chunked raw transport, not an Attachments view.

## Retained technical context

Historical research establishes the stated adapter/library behavior and limitations.
Broader historical fixtures do not expand the accepted v1 profile.

* [DefaultSkillCatalog](../../../src/main/java/ai/loomspan/internal/skillapi/DefaultSkillCatalog.java)
  exposes input/output schema metadata; [DefaultRegisteredSkillCatalog](../../../src/main/java/ai/loomspan/internal/runtime/observation/catalog/DefaultRegisteredSkillCatalog.java)
  and [ObservabilityDtoMapper](../../../src/main/java/ai/loomspan/internal/observability/web/ObservabilityDtoMapper.java)
  expose original YAML and effective output metadata to Console.
  [SkillDetail.tsx](../../../loomspan-console/web/src/observability/SkillDetail.tsx)
  displays them inertly; [TraceRecords.tsx](../../../loomspan-console/web/src/observability/TraceRecords.tsx)
  displays schema outcomes/issues. Preserve original author text and diagnostic
  content; update contracts/fixtures when diagnostic shapes change. Inspection found
  display/source handling here, not a separate browser JSON Schema validator.
* Console's [MCP output schemas](../../../loomspan-console/internal/mcpadapter/output_schemas.go)
  use `github.com/google/jsonschema-go/jsonschema` for reflected DTO validation and
  compact advertised schemas. These are Console MCP response contracts, not Loomspan's
  custom skill dialect. Do not replace the Go validator with Java or treat the MCP
  schema builder as an obsolete chat integration. Its schema strings and diagnostic
  DTO consumers still need regression coverage when the framework format changes.
  The Console agent-eval JSON schemas are also separate contracts, not migration targets
  merely because they are schemas.

## Pipeline notes

- Destructive updates are encouraged: this repository is in development with no
  deployments to support. Remove superseded implementations in place; do not add
  legacy readers, aliases, dormant paths, compatibility shims or fallback solely to
  keep intermediate commits building. Respect the supported public API boundary above.
- The developer commits between tickets without syncing. The stated intermediate
  build failures are authorized; identify each affected consumer/test and restoration
  owner. Do not mark blocked tests passed, remove assertions or skip required checks
  into success. Final integrated acceptance requires green checks.
- Preserve unrelated worktree changes and retained probes. Resource stream closure
  in SkillMethodBeanPostProcessor is already repaired, with success/failure/nested
  tests; preserve it rather than treating it as missing migration implementation.
- Source anchors and retained reports below are historical navigation/evidence, not
  fresh passing-test claims. Verify hints against the current checkout. Before claiming
  a new defect, trace callers, validation, execution and tests against the accepted
  requirement. Routine implementation choices are autonomous; broader product or
  architecture departures require a concrete evidence-backed question.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** This ticket changes or provides outstanding assurance for a cross-cutting migration involving serialized contracts, external protocols or execution lifecycle behavior. Settled intent does not eliminate integration research, test planning or independent review.
- **Reassessment triggers:** A materially changed scope or checkout should be reassessed under the shared protocol; this recommendation does not waive any required review or verification.

## Reference navigation

- [Agreed overview](../phases/migration-ticket-overview.md)
- [Coverage ledger](../phases/migration-ticket-coverage.md)
- [Migration roadmap](../phases/spring-ai-responses-migration.md)
- [Historical schema research](../phases/standard-json-schema-research.md)
- [TBD-06 reproduction and retained evidence](../../../docs/maintainers/probes/tbd-06/README.md)
