# PR 30.3.4 — Migrate TypeSafe evidence metadata and effective catalogs

## Outcome

Remove remaining legacy schema dependencies from native TypeSafe and catalog/display metadata. Preserve descriptive-only TypeSafe outputs and native acceptance. Include request/response decimal fidelity, exact evidence claim names and faithful structural metadata without inherited child-local evidence.

## Scope and sequencing

**Dependencies:** PR 30.2.1, PR 30.2.3, PR 30.2.5 and PR 30.3.1.

**Expected build state:** Framework build should return to green at the end of Phase 3. Remaining failures must be identified, not deferred generically.

PRs 30.1.2–30.1.5 restore the provider transition; PRs 30.2.2–30.3.4 restore schema consumers by their owned boundaries; PR 30.4.3 reruns all blocked integrated checks.

Own native TypeSafe schema consumers, evidence extraction/claim identity and effective catalog metadata. Shared structural validation is already provided. Console UI work is PR 30.4.1. Do not introduce TypeSafe output validation or modify its native evaluation protocol.

Execute in the agreed numerical order unless working on an independent prerequisite. Dependencies describe technical prerequisites, not permission to duplicate another ticket's implementation.

PR 30.2.1 owns shared evidence annotation shape/placement checks, canonical retention
and schema-node extraction. Reuse those checks; repeated placement cases below are
integration regressions here. This ticket owns EvidenceContract extraction/expression
compilation, exact claim lookup and runtime evidence behavior, plus native TypeSafe
attachment rejection throughout supported input schemas.

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

- [ ] TypeSafe accepts the same object-valued answers, extras and descriptive-schema mismatches as before, with native request/retry behavior preserved and exact numeric values in requests, answers and evidence.
- [ ] Evidence annotations retain placement, grammar, original expressions and exact claim names; planning and final claim coverage preserve null/presence and successful-child semantics.
- [ ] Effective direct/forwarded catalog schemas faithfully retain standard constraints without child-local evidence leakage or provider-copy weakening; public descriptor signatures remain unchanged.
- [ ] Every applicable mandatory case in the detailed contract below has executable evidence, or an explicitly identified downstream integration owner and blocked-check record consistent with this ticket's build allowance; no in-scope required behavior is silently deferred.
- [ ] Owned fixtures and callers migrate with the behavior; preservation controls and the public-surface check are run when buildable, and all failures/blocked checks are reported accurately.

**Verification emphasis:** Native mock protocol/request reuse and answer passthrough; precise decimals; evidence extraction/placement; forwarding/catalog snapshots; canonical versus effective schema separation; public-surface checks.

## Detailed accepted contract and evidence

The following bounded extracts carry the applicable decisions, exact acceptance cases
and historical code/test anchors into this ticket. Scope ownership above determines
which cross-boundary statements are implementation here versus preservation context.
Statements about earlier research runs remain historical; do not claim they were rerun.
References to future tickets in retained text mean requirements carried by this ticket
set, not permission to postpone this ticket's owned work. Older proposals do not override
accepted decisions or the explicit exclusions above.

### Contract 1

#### Output evidence metadata — agreed November 11, 2026

Replace the old property-level evidence keyword with x-loomspan-evidence on the same
immediate root output property. For example, properties.likelyCause retains type: string
and changes evidence: classifyIncident and (investigateNetwork or investigateApp) to
x-loomspan-evidence: classifyIncident and (investigateNetwork or investigateApp).
Keep one nonblank string expression, not a metadata object or a separate path map.
Reject legacy annotation syntax; ordinary data properties named evidence or
x-loomspan-evidence remain legitimate properties. This authorizes only this recognized
output annotation, not arbitrary extensions or nested evidence declarations.

Preserve existing placement restrictions (immediate root output properties only),
expression grammar, namespace/direct-child resolution, original authored text and
1-based diagnostic columns. Compile into the existing EvidenceContract separately
from structural schema validation. Canonical schemas retain the annotation; provider
schema copies remove it at schema-node positions without altering standard assertions
or similarly named data properties. Keep existing runtime evidence instructions and
correction behavior; removing the annotation from provider schemas does not remove
framework evidence enforcement.

Rationale: schema validity cannot prove that a direct child executed successfully.
Evidence already has a separate execution-history validator; the migration changes
its declaration vocabulary rather than introducing a new evidence system. Keeping
metadata beside the claim follows the attachment design without a second path language.
Preserve planning coverage of every annotated claim (including optional claims), final
coverage of present claims only, successful-direct-child credit, failed/cancelled-call
non-credit, nested-mission isolation, bound-output checks and TBD-11 correction rules.
A permitted null is still a present claim and needs evidence; optional absence does not.
Evidence proves execution supportability, not factual correctness or value provenance.

Apply already agreed exact property-name semantics to evidence lookup: remove the
current lowercase/trim claim normalization, while retaining existing expression/operator
and namespace rules. Do not conflate claim-name matching with skill-identity parsing.

Mandatory ticket acceptance tests:

- Accepted annotation on immediate root output properties; rejection at root, nested
  properties, array items and input schemas. Reject blank/non-string expressions and
  legacy evidence annotation syntax without aliases or coercion.
- Preserve and/or precedence, parentheses, direct-child identity/namespace validation,
  unknown/wrong-case reference diagnostics, original expression text and token columns;
  diagnostic schema paths use x-loomspan-evidence.
- Preserve planning coverage including optional claims, successful direct-child credit,
  failure/cancellation non-credit, repeated-call set behavior and nested isolation.
- Final validation evaluates present claims after structural acceptance: optional absent
  claims need no credit; present null and non-null claims do. Preserve required-field
  failures, bound-output evidence checks and existing correction budgets/no-tool rules.
- Exact-case and whitespace-distinct claim names remain distinct; do not trim or fold
  property names. Unannotated properties acquire no inferred evidence requirement.
- Canonical metadata and authored source retain the annotation/text. Provider copies
  omit only the recognized schema annotation, retaining standard constraints and data
  properties named evidence or x-loomspan-evidence, including nested property trees.
- Retain existing evidence expression, catalog, planning, binding, correction and
  successful-child boundary regressions. Update authoring examples and diagnostic
  fixtures with the migration; schema display and TypeSafe consumers are reviewed next.

No production implementation, new test-run acceptance or ticket creation is claimed.

Evidence anchors (source/test inspection; new acceptance remains pending):

- `src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java:857` (`String evidence = entry.getValue().getEvidence()`) extracts root-property evidence; the parser/compiled-contract path immediately below it remains the authority. The existing lowercase claim index at `src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java:899` (`compiled.keySet().forEach`) must follow the agreed exact-name migration.
- `src/test/java/ai/loomspan/internal/skill/YamlSkillEvidencePropertyCatalogTest.java:41` (`void loadsEvidenceAnnotationFromImmediateRootOutputProperty`) and `src/test/java/ai/loomspan/internal/skill/YamlSkillEvidencePropertyCatalogTest.java:98` (`void rejectsEvidenceOutsideImmediateRootPropertiesWithFullPath`) establish legacy extraction/placement. Migrate their vocabulary and retain those semantics; these are not tests of the new annotation.

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

#### TypeSafe migration boundary — agreed November 11, 2026

Migrate TypeSafe's shared schema representations while preserving native evaluation
behavior. Authored input_schema adopts standard JSON Schema and shared validation,
including the agreed exact-name/no-coercion, nullability and ordinary-input feature
rules. Keep the required input schema and reject attachments using the new annotation
through all supported schema locations, including map-value schemas. TypeSafe request
state still requires JSON-compatible values; unconstrained input does not admit Java
resource objects or bypass native request checks.

Replace TypeSafeDeclaration's OutputSchemaManifest dependency with the standard schema
representation for its generated descriptive result metadata. Preserve noul, choice
and score shapes, fixed-criteria enums/named probability fields, requiredness,
descriptions and explicit existing object openness. Dynamic probability maps and score
legends remain open objects. Keep probability/confidence/score ranges descriptive;
do not add new numeric assertions or typed-map constraints to these result schemas.
Expose the migrated metadata through the existing catalog/forwarding/display path.

The generated result schema is not currently enforced against TypeSafe responses.
TypeSafeExecutor parses native response JSON, requires an object-valued answers field
and returns that object; preserve this acceptance behavior. Do not introduce generated
result-schema enforcement, authored output schemas, chat structured-output options or
schema-repair retries. Keep native typesafe declaration validation, input substitution,
post-substitution request validation, immutable request reuse, connection/model binding,
transport, retries, cancellation, accounting, diagnostics and answer extraction.
Successful TypeSafe children continue to participate in parent evidence under existing
rules; no TypeSafe-local output evidence declaration is introduced.

Rationale: this removes the legacy schema dependency and integrates shared input
validation without redesigning a working native evaluator. Result-schema enforcement
would add new rejection behavior and policy questions and is outside this migration.
It is not required to display standard descriptive metadata or validate caller inputs.

Mandatory ticket acceptance tests:

- Equivalent descriptive standard schemas for noul, fixed/dynamic choice and
  fixed/dynamic score: preserve enums, named fields, requiredness, explicit openness,
  descriptions and legends; no newly asserted numerical bounds or legacy dialect.
- Standard authored input validation precedes native calls; invalid input prevents
  provider invocation. Preserve required input-schema and attachment rejection,
  including nested/items/map-value declarations and non-JSON native request inputs.
- Preserve template substitution, literal/reference handling, post-substitution native
  request checks and exact request content/reuse across transport retries. Generated
  result metadata must not be sent as chat output configuration or native request fields.
- Preserve acceptance of object-valued answers, including additional answer fields
  and objects not satisfying the descriptive result schema. Malformed JSON and absent,
  null or non-object answers still fail through existing response handling. Do not
  introduce schema repair or additional calls for these failures.
- Preserve response diagnostics, usage recorded for successful HTTP responses even
  when answer extraction fails, cancellation and existing retry ownership/accounting.
- Preserve authored output-schema/unsupported execution-field rejection and empty
  TypeSafe-local evidence contracts; successful child credit remains unchanged.
- Catalog, forwarded effective schemas and Console expose the migrated descriptive
  schema consistently, with original source text preserved and no provider-copy mutation.

No production implementation, new test-run acceptance or ticket creation is claimed.

**TypeSafe response decimal precision — agreed audit correction:** parse native
response envelopes with lossless decimal handling before recording response evidence
and extracting answers. Keep this correction local to TypeSafe response parsing;
do not change shared mapper behavior or introduce result-schema validation. Reuse
the arbitrary-precision decoding approach already present in AcceptedResultDecoder.
Add focused mock-response integration tests with long fractional and exponent-form
decimal values, including nested answers, asserting exact numeric values in returned
answers and trace evidence using precision-preserving assertions. Retain object-answer
acceptance, usage accounted once and existing malformed-response handling.
The exact current codec/extraction operations rounded 0.12345678901234567890123456789
to 0.12345678901234568 in a local probe. Source inspection shows successful parsing
records the parsed envelope rather than the raw body; the full executor path was not
run with that fixture. Existing large/extended-answer tests use integer fixtures and
default-mapper tree comparisons, which do not establish decimal precision.
Implementation and focused tests remain pending; no live provider claim is made.

Evidence anchors (source/test inspection; new acceptance remains pending):

- `src/main/java/ai/loomspan/internal/typesafe/TypeSafeExecutor.java:89` (`envelope = codec.readTree(raw)`) parses with the default schema mapper; `src/main/java/ai/loomspan/internal/typesafe/TypeSafeExecutor.java:97` (`responseEvidence.put("envelope", envelope)`) records the parsed value; `src/main/java/ai/loomspan/internal/typesafe/TypeSafeExecutor.java:121` (`return codec.writeValueAsString(answers)`) returns extracted answers.
- `src/test/java/ai/loomspan/integration/TypeSafeSkillIntegrationTest.java:298` (`void completeExtendedAndLargeAnswersPassThroughBothRoutePrefixes`) uses integer/large-string fixtures and default-mapper equality. Reuse this mock HTTP harness with exact decimal assertions; `src/main/java/ai/loomspan/internal/runtime/input/AcceptedResultDecoder.java:12` (`USE_BIG_DECIMAL_FOR_FLOATS`) is the existing lossless-decoding implementation to reuse.

**TypeSafe request decimal precision — agreed audit correction:** make
ResolvedRequest.json() reparse captured request decimals losslessly while retaining
its detached-copy behavior. Template resolution can preserve a BigDecimal in jsonText
only for the subsequent default-mapper reparse to round it before the executor adds
the model and serializes the request body. Keep this correction local; preserve
embedded-string substitution and immutable request reuse across retries.
Add focused tests for long fractional/exponent-form numeric substitutions, including
nested values, repeated json() reads and mutation isolation, and captured HTTP request
bodies across retries using precision-preserving assertions. Existing scalar-fidelity
coverage uses 1.25 embedded into a string and does not establish this numeric path.
A local request-construction probe retained 0.12345678901234567890123456789 in the
resolved JSON text but emitted 0.12345678901234568 after json() and executor-style
serialization. No HTTP/provider call was made. This is separate from response parsing;
implementation and focused tests remain pending.

Evidence anchors (source/test inspection; new acceptance remains pending):

- `src/main/java/ai/loomspan/internal/typesafe/TypeSafeDeclaration.java:74` (`public record ResolvedRequest`) reparses with JSON.readTree; `src/main/java/ai/loomspan/internal/typesafe/TypeSafeExecutor.java:36` (`ObjectNode request =`) consumes that tree before adding model and serializing the body.
- `src/test/java/ai/loomspan/internal/skill/TypeSafeDeclarationTests.java:93` (`void parsedYamlScalarFidelityAndResolvedCopiesRemainExact`) checks detached copies and embedded BigDecimal(1.25) text, not long decimal numeric substitutions. Extend it and the captured-request integration harness named in the response finding.

## Retained technical context

Historical research establishes the stated adapter/library behavior and limitations.
Broader historical fixtures do not expand the accepted v1 profile.

* [TypeSafeDeclaration](../../../src/main/java/ai/loomspan/internal/typesafe/TypeSafeDeclaration.java)
  requires JSON-compatible request state, substitutes `${/input/...}` references, and
  derives descriptive output schemas from `noul`, `choice`, and `score` questions.
  Fixed criteria produce enumerated choices/probability keys; dynamic criteria produce
  open maps; score results include a legend. Probability bounds currently appear in
  descriptions, not structural assertions. Its generated result tree uses the old
  `OutputSchemaManifest` and therefore must migrate too.
  [TypeSafeExecutor](../../../src/main/java/ai/loomspan/internal/typesafe/TypeSafeExecutor.java)
  remains a separate native evaluator, not a chat-model adapter. The catalog requires
  TypeSafe input schemas, rejects attachments, and rejects authored output-schema
  configuration for TypeSafe skills. Do not add chat structured-output options or
  a new authored TypeSafe result contract incidentally.

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
