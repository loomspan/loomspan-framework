# PR 30.2.4 — Migrate attachment validation materialization and trace descriptors

## Outcome

Implement attachment annotation consumption and direct-Resource validation. Group MIME parsing/case handling, typed-map traversal, bounded size/hash reads, unreadable-resource failures, missing filenames, null-preserving containers and JSON Pointer paths. Record descriptors correctly in coordinator and both engines; propagate through journal/observer. Correct generic failure advice.

## Scope and sequencing

**Dependencies:** PR 30.2.1 and PR 30.2.3.

**Expected build state:** Attachment paths should pass focused tests; broader output/planning migration may remain incomplete.

PRs 30.1.2–30.1.5 restore the provider transition; PRs 30.2.2–30.3.4 restore schema consumers by their owned boundaries; PR 30.4.3 reruns all blocked integrated checks.

Own Framework attachment validation/materialization, coordinator records, engine descriptor recording and journal/observer propagation. Console-specific requirements embedded below define the receiving contract; PR 30.4.1 owns their UI implementation. Preserve all attachment behavior when PR 30.3.2 later migrates step schemas.

Execute in the agreed numerical order unless working on an independent prerequisite. Dependencies describe technical prerequisites, not permission to duplicate another ticket's implementation.

PR 30.2.1 owns shared attachment annotation shape/placement checks, canonical retention
and schema-node extraction. Reuse those checks; the repeated declaration cases below
are integration regressions here. This ticket still owns the malformed-MIME syntax
correction at loading, Resource validation/materialization and runtime propagation.

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

- [ ] All agreed reference/direct Resource, map/nested/array, MIME, size/readability, missing-name and null cases preserve original structure and fail before model execution when invalid.
- [ ] Coordinator and engine records retain accurate JSON Pointer locations and descriptors once per owning frame, including no-model completion and failure after successful materialization.
- [ ] Journal/observer propagation retains descriptor collections under the existing narrow journal exception, while canonical traces remain faithful; failed materialization creates no successful descriptor record.
- [ ] Every applicable mandatory case in the detailed contract below has executable evidence, or an explicitly identified downstream integration owner and blocked-check record consistent with this ticket's build allowance; no in-scope required behavior is silently deferred.
- [ ] Owned fixtures and callers migrate with the behavior; preservation controls and the public-surface check are run when buildable, and all failures/blocked checks are reported accurately.

**Verification emphasis:** Reference/direct Resource cases, nested/maps/arrays, nulls, filenames, precise associations, bounded reads, no invocation on failure, descriptor-only planning, media execution, canonical traces and the existing journal-redaction exception.

## Detailed accepted contract and evidence

The following bounded extracts carry the applicable decisions, exact acceptance cases
and historical code/test anchors into this ticket. Scope ownership above determines
which cross-boundary statements are implementation here versus preservation context.
Statements about earlier research runs remain historical; do not claim they were rerun.
References to future tickets in retained text mean requirements carried by this ticket
set, not permission to postpone this ticket's owned work. Older proposals do not override
accepted decisions or the explicit exclusions above.

### Contract 1

#### Attachment input declaration — agreed November 11, 2026

Preserve attachment inputs; defer native generated attachments beyond v1. This does
not remove existing tool-produced resource references or authorize new provider image
creation tools, file downloads/storage, output media APIs, or generated-media replay.

Keep attachment metadata on its schema node using `x-loomspan-attachment` (Option A),
not a separate `input_attachments` path map and not `type: attachment`:

```yaml
input_schema:
  type: object
  required: [image]
  properties:
    image:
      type: string
      description: Ticket image.
      x-loomspan-attachment:
        media_type: image
        allowed_content_types: [image/jpeg]
  additionalProperties: false
```

`type: string` describes the JSON reference form; it does not mean a direct Java
Resource must be stringified. Direct Resource validation follows the agreed attachment-only adapter below; do not
silently drop support or bypass structural checks.
The annotation identifies attachment execution metadata; it does not introduce a
custom JSON type or alter standard assertion meanings. Retain existing `media_type`
and `allowed_content_types` names, values and validation requirements inside it.
The annotation does not itself grant resource access or prove provider support.

For a nested attachment, place the annotation on the nested property schema. For
arrays of attachments, place it on the `items` schema; it applies to each item.
For arrays of objects containing attachments, place it on the relevant property under
`items.properties`. Walk actual schema nodes, not arbitrary field names. No wildcard
path syntax, duplicate metadata tree, or new path-resolution engine is needed.
Keep it input-only; TypeSafe's existing attachment rejection remains unchanged.

Retain the annotation in the canonical declaration for loading, framework handling,
and authoring tools. Explicitly remove this framework annotation from provider schema
copies while preserving the node's standard keywords. This is deliberate framework-
metadata extraction, separate from TBD-07 assertion omission rules. Do not strip
arbitrary `x-*` keys or expand this into an undocumented extension mechanism.

Tickets must carry the exact syntax, nesting/items semantics, provider-copy extraction,
and the agreed Resource-validation adapter explicitly. Migrate existing YAML
fixtures, parsing/schema models, consumers and Console authoring/display to this
shape; reject the removed dialect, with no dual-format compatibility path. Preserve
resource resolution/access boundaries, repeatability, MIME/image-signature checks,
size limits, materialization, descriptors and existing Java resource conversions.
No automatic provider upload, media conversion, or protocol fallback is authorized.

Required tests: equivalent single/nested/array attachment declarations; requiredness
and surrounding object/array structure; malformed metadata; legacy-dialect rejection;
TypeSafe rejection; canonical annotation retention; provider annotation removal with
standard keywords and similarly named data properties intact; existing reference and
Resource materialization regressions. Tickets must not prescribe fake reference values,
Resource serialization or an alternative validation strategy beyond the agreed adapter.

**Attachment MIME declarations — agreed audit correction:** validate every declared
allowed_content_types entry during loading with the same MIME parser used by media
construction. Report the skill, schema location and invalid entry for malformed MIME
syntax. Preserve existing nonblank/nonempty checks; do not introduce a new MIME
allowlist or infer provider support from syntactic validity. Add focused rejection
tests for malformed nonblank entries, including one malformed entry among valid
entries, and valid-MIME controls. A local probe loaded allowed_content_types:
[not-a-mime] and materialized that content type before the media MIME parser rejected
it for lacking a slash. Existing catalog tests cover missing content types and
misplaced metadata, not this late failure. This implements the agreed malformed-
metadata/load-time rejection boundary; implementation and focused tests remain
pending, and no provider call was made by the probe.

Evidence anchors (source/test inspection; new acceptance remains pending):

- `src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java:1009` (`private void validateAttachmentInputSchema`) validates declarations but checks allowed entries only for nonblank text; `src/main/java/ai/loomspan/internal/springai/SpringAiModelInteraction.java:57` (`MimeTypeUtils.parseMimeType(attachment.contentType())`) parses MIME values for media construction.
- `src/test/java/ai/loomspan/internal/skill/YamlSkillCatalogTests.java:962` (`requiresAllowedContentTypesForAttachmentInputAndRejectsMediaFieldsElsewhere`) covers missing lists/placement, not malformed MIME syntax in an otherwise nonempty list.

**Attachment MIME capitalization — agreed audit correction:** use parsed MIME values
consistently for allowed-type comparison and normalized type/subtype selection for
image-signature checks. Preserve the authored declaration; do not lowercase arbitrary
parameter values or add wildcard/provider-support rules. Add focused lowercase,
uppercase and mixed-case tests with identical valid JPEG bytes, plus controls for
genuinely different MIME types and invalid signatures. Cover filename detection and
the single-allowed-type fallback. A local materializer probe accepted image/jpeg but
rejected IMAGE/JPEG for the same JPEG, although Spring's MIME parser considers them
equal. The declaration parser correction alone does not fix this runtime mismatch.
Implementation and focused tests remain pending; no provider capability is inferred.

Evidence anchors (source/test inspection; new acceptance remains pending):

- `src/main/java/ai/loomspan/internal/runtime/attachment/DefaultMissionInputMaterializer.java:174` (`if (!schema.allowedContentTypes().contains(contentType))`) compares raw strings; `src/main/java/ai/loomspan/internal/runtime/attachment/DefaultMissionInputMaterializer.java:248` (`boolean valid = switch (contentType)`) matches lowercase image types only.
- `src/test/java/ai/loomspan/internal/runtime/attachment/DefaultMissionInputMaterializerTest.java:54` (`rejectsAttachmentWhenContentTypePolicyDoesNotMatch`) and `src/test/java/ai/loomspan/internal/runtime/attachment/DefaultMissionInputMaterializerTest.java:76` (`rejectsImageAttachmentWhenFilenameAndBytesDoNotMatch`) cover real mismatches, not equivalent MIME type/subtype capitalization.

**Attachment map-value traversal — agreed audit correction:** materialization and
ExecutionCoordinator's mission-input trace traversal must
select a named property schema when present, otherwise schema-valued
additionalProperties, and recurse through that selected schema. The current
DefaultMissionInputMaterializer copies unnamed properties unchanged, so migrating
attachment-valued maps requires this traversal change. ExecutionCoordinator likewise
selects only named properties and passes unnamed values through unchanged before
opening the mission frame. Apply the same schema selection there so attachment map
values receive descriptors in FRAME_OPENED; later materialization or descriptor
records do not repair that earlier input record. Reuse existing reference
resolution, descriptor construction, resource checks and media collection; do not
interpret boolean openness or unmarked values as attachment declarations.

Add focused map-value materialization tests for reference strings and direct repeatable
Resources, permitted/prohibited nulls, and invalid resources. Assert original map keys,
descriptor placement and collected media associations, including nested map values.
Verify named properties take precedence over additionalProperties, ordinary siblings
remain validated, and existing MIME/signature/repeatability/size checks still apply.
Permitted nulls produce no media and survive immutable container copying. Preserve
canonical inputs/schemas and descriptor-only planning versus media-bearing execution.
Add focused coordinator tests for reference and direct repeatable Resource map values,
including nested values and named-property precedence. Assert original keys and
descriptor placement in FRAME_OPENED without Resource serialization; preserve explicit
nulls under the coordinator null-recording requirement below. Existing named-field
materialization and coordinator tests do not establish map-value coverage; this
is pending work for the agreed typed-map scope, not a claim of current YAML support.

Evidence anchors (source/test inspection; new acceptance remains pending):

- `src/main/java/ai/loomspan/internal/runtime/attachment/DefaultMissionInputMaterializer.java:108` (`SkillInputSchemaNode childSchema = schema.properties().get(childName)`) selects only named children; `src/main/java/ai/loomspan/internal/core/ExecutionCoordinator.java:431` (`private Object traceSafeNode`) likewise traverses only named properties before frame recording.
- `src/test/java/ai/loomspan/internal/runtime/attachment/DefaultMissionInputMaterializerTest.java:32` (`materializesRefAttachmentToDescriptorAndResource`) and `src/test/java/ai/loomspan/internal/core/ExecutionCoordinatorTest.java:928` (`redactsDeclaredAttachmentInputBeforeOpeningMissionFrame`) cover named image fields, not map-value declarations or named-property precedence.

#### Direct Resource inputs and attachment validation — agreed

Retain direct repeatable Spring Resource inputs for v1 alongside existing ref://
inputs. There is no supported public resource-registration API: the internal VFS
resolves session-local files, while SkillTemplate creates a fresh session and exposes
no pre-invocation registration operation. Do not require applications to use internal
session/VFS types, or add a public registration API merely to accommodate JSON validation.

Keep the validation adapter limited to declared attachment nodes. Preserve the original
input and canonical schema. Validate reference strings against their standard schema
and existing reference syntax before resolution; preserve existing resource checks for
direct Resource values and resolved resources. String constraints govern reference
strings, not resource filenames/bytes. Existing attachment size settings govern bytes.
Use existing materialized attachment descriptors in an internal JSON validation view
and the corresponding descriptor contract at attachment leaves in a private schema
copy. Preserve every property, array position, and parent constraint. Missing and null
values must not be manufactured into descriptors. Validate ordinary sibling values,
requiredness, additionalProperties and array bounds through the shared validator.
Neither this view nor its schema is sent to providers or returned as rewritten caller
input. Do not stringify Resources, invent reference placeholders, or skip surrounding
validation. Do not expand this adapter to ordinary JSON fields or automatically reuse
it for separately marked Java resource-parameter conversion.

Tickets must include both reference and direct-Resource cases, nested/array cases,
missing required fields, invalid ordinary siblings, array bounds, undeclared fields,
invalid MIME/signatures, nonrepeatable resources, unreadable resources and size failures.
Verify canonical inputs/schemas remain unchanged and standard provider schemas do not
contain internal descriptor contracts. Retain current execution routing: planning
currently receives attachment descriptors, while execution receives media; changing
planner access to file bytes requires a separate decision, not an incidental migration.

**Unreadable attachments — agreed audit correction:** an IOException during the
existing bounded digest read must fail materialization with the attachment field path
and original cause, rather than returning a null hash and accepting the Resource.
Unknown content length remains acceptable when the subsequent bounded read succeeds;
reuse that read without adding a separate readability pass or changing MIME/size policy.
Add focused tests for stream-opening and mid-read failures, a readable unknown-length
control, and failure preventing model invocation. The local materializer probe accepted
an unreadable named PDF Resource while the readable control produced a hash; this
reproduces the materializer defect, not provider behavior. Implementation and full
pipeline/live acceptance remain pending.

Evidence anchors (source/test inspection; new acceptance remains pending):

- `src/main/java/ai/loomspan/internal/runtime/attachment/DefaultMissionInputMaterializer.java:311` (`private String digest`) catches IOException and returns null after the bounded read; `src/main/java/ai/loomspan/internal/runtime/attachment/DefaultMissionInputMaterializer.java:188` (`String digest = digest(resource, path)`) accepts that result into a descriptor.
- `src/test/java/ai/loomspan/internal/runtime/attachment/DefaultMissionInputMaterializerTest.java:119` (`rejectsMissingRefWithFieldSpecificMessage`) checks resolution failure; `src/test/java/ai/loomspan/internal/runtime/attachment/DefaultMissionInputMaterializerTest.java:76` (`rejectsImageAttachmentWhenFilenameAndBytesDoNotMatch`) checks signatures. Neither tests an unreadable resolved non-image Resource; use a document fixture to reach the digest failure itself.

**Bounded attachment size reads — agreed audit correction:** avoid the unconditional
Resource.contentLength() pre-read, which can consume the entire stream before applying
the size limit. Use the existing bounded digest pass to calculate both actual byte
count and hash, retaining them in the descriptor. Preserve the configured per-attachment
limit and stop oversized reads after bounded excess detection; do not add another limit
or a separate counting pass. Add counting-stream tests for oversized repeatable
Resources with inherited stream-reading contentLength(), including unknown-length
resources, and accepted inputs at/below the limit with accurate size/hash metadata.
Assert bounded bytes consumed before rejection and no model invocation on failure.
The local helper probe consumed all 65,536 bytes before rejecting under a 16-byte
limit; it demonstrated excessive reading, not acceptance of oversized input. Existing
ByteArrayResource size tests prove rejection but not bounded reading. Implementation
and full pipeline/live acceptance remain pending.

Evidence anchors (source/test inspection; new acceptance remains pending):

- `src/main/java/ai/loomspan/internal/runtime/attachment/DefaultMissionInputMaterializer.java:181` (`Long size = contentLength(resource)`) invokes the unbounded pre-read before `src/main/java/ai/loomspan/internal/runtime/attachment/DefaultMissionInputMaterializer.java:311` (`private String digest`) performs bounded reading.
- `src/test/java/ai/loomspan/internal/runtime/attachment/DefaultMissionInputMaterializerTest.java:132` (`rejectsAttachmentAboveConfiguredSizeLimit`) uses ByteArrayResource with a cheap known length and asserts rejection only, not bytes read. Extend with the counting repeatable Resource described above.

**Attachment failure diagnostics — agreed audit correction:** remove the unconditional
"Use a model/driver that supports the declared attachment media" advice from
DefaultMissionExecutionEngine's generic attachment-failure wrapper. Retain factual
skill/model/attachment context and the original exception as cause; unknown failures
do not establish media incompatibility. Evidenced incompatibility handling remains
governed by TBD-09, without adding a new classifier or changing retry behavior.
Update the focused wrapper test to assert preserved context/cause and absence of
unsupported-media advice for a generic failure; retain the cancellation-preservation
regression. The existing wrapsProviderFailureWithAttachmentContext test expects that
advice for a fixture throwing only IllegalStateException("boom"). This finding is
established by source/test inspection, not live provider evidence. Implementation and
focused verification remain pending.

Evidence anchors (source/test inspection; new acceptance remains pending):

- `src/main/java/ai/loomspan/internal/runtime/DefaultMissionExecutionEngine.java:162` (`Use a model/driver that supports`) appends unsupported-media advice to the generic wrapper.
- `src/test/java/ai/loomspan/internal/skill/MissionExecutionEngineTest.java:393` (`wrapsProviderFailureWithAttachmentContext`) asserts that advice for boom; `src/test/java/ai/loomspan/internal/skill/MissionExecutionEngineTest.java:420` (`attachmentContextDoesNotReplaceACancellationFailure`) is the cancellation control to retain.

**Missing attachment filenames — agreed audit correction:** repeatable direct
Resources need not supply a filename. Preserve a missing name through both
ExecutionCoordinator's mission-input descriptor and LoomspanAttachment.descriptor
without inventing a filename or relaxing resource checks. Both currently insert a
nullable name and call Map.copyOf, which rejects null entries. Use immutable
null-preserving descriptor copies. Existing single-allowed-MIME detection remains
applicable when filename detection cannot supply a type.

Add focused materializer and coordinator regressions using a repeatable Resource
with no filename, valid signature bytes and one allowed MIME type. Assert successful
descriptor creation, retained missing name, collected resource/media and normal
coordinator progression; retain named-resource and MIME/signature/repeatability/size
failure regressions. The local materializer probe reproduced a NullPointerException
for a missing name while the named control passed; the coordinator path was inspected,
not reproduced. This is an existing defect distinct from permitted-null input values.
Implementation and full pipeline/live acceptance remain pending.

Evidence anchors (source/test inspection; new acceptance remains pending):

- `src/main/java/ai/loomspan/internal/runtime/attachment/LoomspanAttachment.java:29` (`public Map<String, Object> descriptor()`) inserts name before Map.copyOf; `src/main/java/ai/loomspan/internal/core/ExecutionCoordinator.java:466` (`private Map<String, Object> attachmentPlaceholder`) does the same for direct Resource names.
- `src/test/java/ai/loomspan/internal/runtime/attachment/DefaultMissionInputMaterializerTest.java:32` (`materializesRefAttachmentToDescriptorAndResource`) uses a named file; `src/test/java/ai/loomspan/internal/core/ExecutionCoordinatorTest.java:928` (`redactsDeclaredAttachmentInputBeforeOpeningMissionFrame`) uses the named-field reference path. Neither proves unnamed direct Resources survive descriptor copying.

**Nullable attachment handling — agreed:** absent optional attachment fields produce
no media. Explicit null produces no media only where permitted by the standard schema;
otherwise it fails validation. Required nullable fields must still be present. Preserve
nulls in the input/validation view, and never manufacture descriptors for missing or
null values. Update materialization to skip permitted null attachment values. Tickets
must test absent optional, absent required, permitted null, prohibited null, and nested/
array-item null cases. This is a targeted migration adjustment, not permission to
ignore nulls or relax requiredness globally. Preserve descriptor-only planning and
media-bearing execution; remaining attachment work is verification as listed below.

**Coordinator null recording — agreed audit correction:** preserve explicit null
as null in ExecutionCoordinator's recorded mission input. Its current attachment
placeholder branch manufactures an attachment descriptor for null before materialization;
fixing materialization alone does not correct the recorded input. Validation continues
to decide whether null is permitted; recording must not invent a descriptor or substitute
a replacement value. Add focused coordinator tests asserting that permitted null stays
null in recorded input and produces no descriptor/media, while prohibited null still
fails validation. The helper-level probe reproduced the incorrect descriptor; full
coordinator verification and implementation remain pending.

Implementation note: skipping a permitted null attachment at its leaf is not sufficient.
`DefaultMissionInputMaterializer.materializeNode` currently uses `Map.copyOf` and
`List.copyOf` for surrounding containers, and `RenderedMissionInput` uses `Map.copyOf`
for its input map; these copies reject null entries. Preserve permitted nulls through
these copies while retaining immutable containers, missing-field semantics, property
names and array positions. Reuse existing null-preserving container-copy machinery
where appropriate. The already required null cases must exercise materialization and
`RenderedMissionInput`, not only schema validation, while retaining ordinary sibling
validation and existing resource checks. This names implementation work for the agreed
null policy; it does not broaden that policy or authorize production changes here.

Evidence anchors (source/test inspection; new acceptance remains pending):

- `src/main/java/ai/loomspan/internal/core/ExecutionCoordinator.java:431` (`private Object traceSafeNode`) enters the attachment branch before checking the value; `src/main/java/ai/loomspan/internal/core/ExecutionCoordinator.java:466` (`private Map<String, Object> attachmentPlaceholder`) manufactures the placeholder. `src/main/java/ai/loomspan/internal/runtime/attachment/DefaultMissionInputMaterializer.java:118` (`return Map.copyOf(traceSafe)`), `src/main/java/ai/loomspan/internal/runtime/attachment/DefaultMissionInputMaterializer.java:127` (`return List.copyOf(traceSafe)`) and `src/main/java/ai/loomspan/internal/runtime/attachment/RenderedMissionInput.java:14` (`traceSafeInput =`) are the null-rejecting copies.
- `src/test/java/ai/loomspan/internal/core/ExecutionCoordinatorTest.java:60` (`nestedChildPreservesNullContextThroughValidationExecutionAndCanonicalTrace`) covers ordinary null context; `src/test/java/ai/loomspan/internal/core/ExecutionCoordinatorTest.java:928` (`redactsDeclaredAttachmentInputBeforeOpeningMissionFrame`) covers a non-null attachment. Neither tests a null attachment through recording and materialization.

**Unambiguous attachment paths — agreed audit correction:** use JSON Pointer for
attachment descriptor fieldPath in both coordinator recording and materialization.
Escape object-name tokens (~ as ~0 and / as ~1) and represent array positions as
index tokens. Preserve original input keys; no new path-resolution engine or input
name restriction is needed. The current dot/bracket concatenation gives a literal
a.b property and nested a/b properties the same fieldPath. A local materializer probe
reproduced two different attachments both labeled a.b; the original nested input tree
remained distinct, so this is ambiguous descriptor location metadata, not evidence of
swapped media. Add coordinator/materializer tests distinguishing literal dotted and
bracketed names from nested properties and array items, including / and ~ escaping.
Assert the same unambiguous locations survive descriptor recording and the planned
journal/observer and Console views. Existing single-field tests do not cover path
collisions. Implementation and focused verification remain pending.

Evidence anchors (source/test inspection; new acceptance remains pending):

- `src/main/java/ai/loomspan/internal/runtime/attachment/DefaultMissionInputMaterializer.java:341` (`private String join`) and `src/main/java/ai/loomspan/internal/core/ExecutionCoordinator.java:431` (`private Object traceSafeNode`) build dotted properties/bracketed indexes; both must use the agreed pointer spelling.
- `src/test/java/ai/loomspan/internal/runtime/attachment/DefaultMissionInputMaterializerTest.java:32` (`materializesRefAttachmentToDescriptorAndResource`) and `src/test/java/ai/loomspan/internal/core/ExecutionCoordinatorTest.java:928` (`redactsDeclaredAttachmentInputBeforeOpeningMissionFrame`) use a simple image key and cannot expose literal dotted/bracketed-key collisions.

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

#### TBD-13 attachment investigation — November 11, 2026

Historical investigation evidence; subsequent accepted decisions settle attachment
placement and defer native generated attachments beyond v1. No production code or dependencies changed, no hosted calls
or uploads performed. Pinned Spring AI 2.1.0-RC2 sources and an executable mock-HTTP
probe distinguish framework behavior, adapter serialization, and provider support.

#### Existing framework behavior

`YamlSkillCatalog` accepts input-only `type: attachment`, with mandatory `media_type`
(image/pdf/audio/video/file) and nonempty `allowed_content_types`. TypeSafe rejects
attachment nodes. `DefaultMissionInputMaterializer` walks declared properties/items,
accepts strict `ref://` strings or repeatable Spring Resource values, resolves resources
through existing services, checks allowed MIME types, checks image signatures, and
checks the configured size limit (20 MiB default) using known size and streamed digest
reading. It produces attachment descriptors plus resources. Filename detection covers
common images, PDF, TXT, JSON and CSV; otherwise a single allowed MIME type supplies
the type. The media-type enum does not establish provider acceptance.

`SpringAiModelInteraction` sends each attachment via user-message `media(mime, resource)`.
This is an inline resource path, not an implemented provider Files API upload/download
lifecycle. Java Resource/InputStream/byte[] tool parameters use existing runtime
reference conversion; they are not automatically provider-native attachments merely
because they contain binary data. Preserve that distinction when choosing declarations.

#### Reproducible adapter verification

Run with Java 21 JAVA_HOME and Maven:

```text
mvn -f docs/maintainers/probes/tbd-06/pom.xml test -Dtest=AttachmentProbeTest
```

[AttachmentProbeTest.java](../../../docs/maintainers/probes/tbd-06/src/test/java/AttachmentProbeTest.java)
uses the same ChatClient user-media Resource API as Loomspan. Two tests pass on Boot
4.2.0-RC2/Jackson 3.1.8, Spring AI 2.1.0-RC2, OpenAI SDK 4.80.0, Anthropic SDK 3.4.0,
Google GenAI SDK 1.73.0, Java 21. The input test covers 18 driver/MIME combinations.
The mock endpoint deliberately returns 400 after capturing serialization; synthetic
fixture bytes are not valid representative media. This proves adapter transport shape
and pre-HTTP rejection, NOT actual file decoding, model acceptance or interpretation.
No live route, full framework execution, attachment-plus-tools/schema combination,
streaming, or alternate Boot platform was validated by this probe.

Retained [evidence](../../../docs/maintainers/probes/tbd-06/evidence/attachments/)
includes request JSON, exceptions, summary CSV, output results, test report and source
JAR hashes. Pinned source: ResponsesItemMapper, AnthropicChatModel, GoogleGenAiChatModel.

| Resource MIME | OpenAI Responses | Anthropic user-media path | Gemini |
| --- | --- | --- | --- |
| image/png | input_image, inline data URL | image/base64 | inlineData |
| application/pdf | input_file, inline file_data | document/base64 | inlineData |
| text/plain, application/json | input_file | Rejected before HTTP | inlineData |
| audio/wav | Rejected before HTTP | Rejected before HTTP | inlineData |
| video/mp4 | Serialized as input_file; NOT proof of video API support | Rejected before HTTP | inlineData |

Images/PDFs are the shared tested serialization baseline. Do not infer that every
image subtype or every provider/model route works from this PNG fixture. Anthropic's
standard Media mapper specifically supports PNG/JPEG/GIF/WebP and PDF; SDK/native
text-document features do not imply they work through Loomspan's current Media path.
The Responses mapper rejects audio explicitly. Passing MP4 through its generic file
branch does not establish API support. Gemini passes MIME/data through; endpoint/model
support must still be verified. Do not add automatic modality/protocol fallback.

#### Attachments generated as output

Synthetic HTTP responses prove the pinned OpenAI Responses adapter converts an
image_generation_call result to AssistantMessage Media, and Gemini converts inlineData
to Media; tests assert MIME and exact decoded bytes. They do not enable image-generation
tools, prove live generation, or establish schema-output coexistence. OpenAI's mapper
explicitly skips generated assistant media on replay; Gemini has a media replay path.
Any future multi-turn generated-media feature must account for this difference.

Loomspan's `SpringAiModelInteraction` extracts only first-generation getText() and
returns ModelInteractionResult(String content, context). It does not expose assistant
media or implement generated-file retrieval/storage. Thus Spring AI media parsing is
not end-to-end Loomspan attachment-output support. Existing tool-produced resource
references are a separate feature, not proof of native model-media output.

Official provider documentation identifies distinct output mechanisms:
- [OpenAI image-generation tool](https://developers.openai.com/api/docs/guides/tools-image-generation)
  returns base64 image data in Responses tool output.
- [Gemini image generation](https://aistudio.google.com/docs/image-generation) supports
  image output with suitable models. The pinned adapter handles inline binary parts.
- [Anthropic Files API](https://platform.claude.com/docs/en/build-with-claude/files)
  supports downloading files produced by hosted code execution/skills. This is a
  tool/file lifecycle, not an ordinary native image-output part. The pinned Anthropic
  mapper retains other native content as UnknownPart rather than downloading files.

Current official [OpenAI file-input docs](https://developers.openai.com/api/docs/guides/file-inputs)
and [Spring AI multimodality docs](https://docs.spring.io/spring-ai/reference/api/multimodality.html)
provide context; pinned code and captures define the tested adapter boundary.

## Reproduction anchors

Historical media serialization reproduction, using Java 21 and Maven:

```powershell
mvn -f docs/maintainers/probes/tbd-06/pom.xml test '-Dtest=AttachmentProbeTest'
```

Two tests cover 18 input MIME/driver cases and two synthetic generated-image responses
on Boot 4.2.0-RC2 only. Synthetic bytes and HTTP replies establish mapping, not real
file parsing, hosted acceptance, generated-media support or schema-plus-tools behavior.

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
