# PR 30.2.5 — Migrate binding ownership projection and composition

## Outcome

Migrate child-input and output-binding schema consumers together. Preserve snapshots, producer identity, exact values, whole-object/array transfers, required-work checks and fully bound synthesis skipping. Implement exact-name matching, standard openness and numeric enum equality.

## Scope and sequencing

**Dependencies:** PR 30.2.1 and PR 30.2.3.

**Expected build state:** Binding components should compile; final generation paths may remain broken until Phase 3.

PRs 30.1.2–30.1.5 restore the provider transition; PRs 30.2.2–30.3.4 restore schema consumers by their owned boundaries; PR 30.4.3 reruns all blocked integrated checks.

Own child-input projection/assembly and output projection/composition/dependency validation. PR 30.3.1 consumes projected output in ordinary generation; PR 30.3.2 consumes it in planning/step paths; PR 30.3.4 owns structural effective-catalog metadata. Preserve binding semantics rather than redesigning them.

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

- [ ] Projected contracts exclude framework-owned destinations, including false property schemas, without weakening canonical final validation or allowing equal/null model overrides.
- [ ] Standard openness, exact names and exact numeric enum overlap govern publication, contribution detection and composition; fully bound closed output skips synthesis only after required work/source checks.
- [ ] Snapshot/producer/isolation controls, whole-value transfers, nested ancestors and invalid-source rejection remain covered.
- [ ] Every applicable mandatory case in the detailed contract below has executable evidence, or an explicitly identified downstream integration owner and blocked-check record consistent with this ticket's build allowance; no in-scope required behavior is silently deferred.
- [ ] Owned fixtures and callers migrate with the behavior; preservation controls and the public-surface check are run when buildable, and all failures/blocked checks are reported accurately.

**Verification emphasis:** Partial/full bindings, nested nullable ancestors, open residual space, reserved false schemas, equal/null override rejection, exact numeric transfers, invalid sources, producer changes and unchanged canonical/projected contracts.

## Child-input preservation requirements

Migrate ChildInputBindingProjection, ChildInputBindingDeclarations and
ChildInputBindingAssembler with the same canonical tree. Preserve parent-input and
accepted-child-result selection, authorization, producer identity, ownership exclusion,
assembled receiving-input validation and dispatch-proof semantics. Keep framework-bound
destinations structurally forbidden through false property schemas; allow all-bound
ancestors to be omitted where existing projection does. Preserve typed extensions,
array items and formatting. ChildInputBindingProjectionTest's
allBoundAncestorMayBeOmittedAndGenericFieldsAreStructurallyForbidden and
preservesTypedExtensionsArrayItemsAndFormattingWhileExcludingReservedNames are controls.
Standard instance validation does not replace binding-path or compatibility analysis.
Do not expand paths into arbitrary map keys, array elements, wildcards or general schema
algebra. Carry approved bounds/formats through all projected and assembled checks.

## Detailed accepted contract and evidence

The following bounded extracts carry the applicable decisions, exact acceptance cases
and historical code/test anchors into this ticket. Scope ownership above determines
which cross-boundary statements are implementation here versus preservation context.
Statements about earlier research runs remain historical; do not claim they were rerun.
References to future tickets in retained text mean requirements carried by this ticket
set, not permission to postpone this ticket's owned work. Older proposals do not override
accepted decisions or the explicit exclusions above.

### Contract 1

Retain internally generated `false` property schemas for framework-bound child-input
destinations. Preserve existing override checks and assembled-input validation, and
include this generated shape in adapter schema-fidelity tests. This does not expand
authored-schema scope.

Preservation evidence: `src/main/java/ai/loomspan/internal/runtime/input/ChildInputBindingProjection.java:56` (`properties.put(tokens.get(i), false)`) emits the false property schema; `src/test/java/ai/loomspan/internal/runtime/input/ChildInputBindingProjectionTest.java:59` (`void allBoundAncestorMayBeOmittedAndGenericFieldsAreStructurallyForbidden`) and `src/test/java/ai/loomspan/internal/runtime/input/ChildInputBindingProjectionTest.java:72` (`void preservesTypedExtensionsArrayItemsAndFormattingWhileExcludingReservedNames`) assert its serialization and ownership protection. These local assertions are not provider acceptance evidence.

Flag existing generated unconstrained schemas or internal projection needs outside
this baseline; they do not automatically expand authoring scope.

### Contract 2

#### TBD-10: Output-binding projection and fully bound outputs

**Status: Resolved — preserve existing binding behavior; migrate its schema representation.**
Do not introduce a new binding system or expand path capabilities. Use the existing
projection/composition authority with standard JSON Schema trees and the shared
validator in place of legacy manifest-schema classes and validators.

#### Required implementation behavior

- Preserve the full canonical output contract. Derive a separate model-contribution
  schema by excluding framework-bound destinations and adjusting required ancestors
  and nullability as existing projection does. This ownership projection is distinct
  from provider adaptation under TBD-07. Do not weaken the full final-output contract.
- Preserve object-only JSON Pointer traversal: tokens select named object fields;
  numeric tokens are literal object keys, not array indexes. Transfer whole arrays
  or objects as bound values. Do not add array-element traversal, wildcard selection,
  root-destination binding, or automatic wrapping/conversion.
- Apply the agreed standard exact property-name semantics consistently across schema
  lookup, path matching, ownership checks, compatibility checks, and composition.
  Existing case-insensitive matching is intentionally removed in this breaking
  migration; do not retain it through aliases or normalize user field names.
- Preserve loading/dependency checks for binding source/destination structural
  compatibility, producer relationships, and existing path/overlap restrictions.
  Static checks do not prove future runtime values; do not build a general JSON Schema
  implication solver. Flag any existing check that cannot be faithfully migrated.
- Preserve invocation-local source selection and snapshots, accepted-plan/producer
  identity checks, owning-frame/lifecycle checks, and bound-value provenance. Validate
  selected values against their destination contracts before the output model call.
  Internal destination-subschema validation may validate an array/scalar value; the
  root-object rule applies to complete declared contracts, not every subschema check.
- Reject model attempts to supply bound destinations even when values are equal to
  selected values or null. Validate the model contribution against its projected
  schema before inserting bound values. Preserve nested ancestor construction and
  requirements for unbound siblings; a nested binding must not erase those requirements.
- Compose exact selected values with the valid model contribution, then validate the
  complete result against the original canonical schema. Preserve existing complete-
  output policy checks and provenance/assembly diagnostics. Do not rewrite values to
  make them fit, or ask the model to repair framework-owned bound values.
- Preserve detection of whether model contribution is needed. If none remains,
  assemble from bindings and skip final-output model generation. Required accepted
  work must still complete; preserve source-availability checks and do not skip
  planning/tasks/tools merely because final synthesis is unnecessary. An open object
  can still have model-owned extra properties even when every declared field is bound.
- Carry all applicable approved v1 constraints, including new bounds/formats, through
  projection and local validation. Send the projected schema through the existing
  provider driver using only TBD-07 mappings. Preserve the canonical and projected
  contracts independently of the request copy. No competing projection engine or
  binding API expansion is authorized.

#### Mandatory ticket handoff and acceptance

Tickets must explicitly reproduce these behaviors and the intentional change to
exact property matching. Identify the existing implementation paths to migrate:
`OutputBindingProjection`, `OutputBindingComposition`, dependency validation,
`OutputSchemaCallAdvisor`, and both mission execution engines. Inventory their
existing tests and retain equivalent coverage in the standard-schema implementation;
do not treat a new validator's successful schema validation as proof of ownership
or execution correctness. No compatibility layer for the legacy schema classes.

Required acceptance cases include partial and fully bound outputs; nested bound
fields with required unbound siblings and nullable ancestors; whole-array/object
values; literal numeric property names; exact-case mismatches; model overrides with
equal/null values; invalid bound values; invalid model contributions; and complete-
output validation after composition. Include open objects that still permit model
contribution, unavailable/mismatched producers, changed plans, and required work
completion before skipping synthesis. Assert actual provider requests contain the
projected contract subject only to approved mappings, original schemas remain
unchanged, and existing bounds/formats are enforced at the appropriate boundaries.

**Omitted additionalProperties and synthesis — agreed audit correction:** migrate
OutputBindingProjection's model-contribution decision to standard object openness:
omitted additionalProperties leaves an object open, just as explicit true does.
Apply this at nested object ancestors as well as the root; preserve whole-subtree
ownership when the entire object is bound. Add omitted/true/false projection tests
and focused standard/step-engine tests proving final generation remains enabled when
model-owned space remains, while closed fully bound outputs still skip synthesis.
Retain required-work and source-availability checks. A local projection probe returned
modelContributionRequired=false for omission and false, but true for explicit true;
existing tests distinguish explicit true/false without establishing standard omission
behavior. This is pending migration work against the agreed openness policy, not an
existing defect under legacy closed-by-default output semantics. Implementation and
focused verification remain pending.

Implementation evidence for this correction (repository-relative paths; line numbers
are audit-time anchors, with symbols retained for navigation after edits):
- `src/main/java/ai/loomspan/internal/outputschema/OutputBindingProjection.java:43`,
  `hasSpace`: line 44 recognizes only explicit true as open; line 49 recurses into
  partially bound objects.
- `src/main/java/ai/loomspan/internal/runtime/DefaultMissionExecutionEngine.java:99`
  skips final generation when `composition.modelContributionRequired()` is false;
  `src/main/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngine.java:379`
  derives the corresponding decision from the same projection.
- `src/test/java/ai/loomspan/internal/outputschema/OutputBindingCompositionTest.java:33`,
  `fullBindingAndOpenOrOptionalResidualSpace`, covers explicit closed/open objects;
  its `schema` helper at line 19 defaults to false, so omission is not exercised.

**Binding-validation openness — agreed audit correction:** remove the legacy
insertion of additionalProperties: false into output-schema copies used for binding
declaration validation. Omission must remain open for both producer and destination
schemas, including nested objects. Preserve existing explicit-false path rejection,
structural compatibility checks and runtime obligations for unknown/open branches;
do not infer future producer fields or expand binding path syntax.

Add focused publication-validation cases comparing omitted/true/false openness on
producer and destination objects, including nested paths and whole-object compatibility.
Omitted and true must have equivalent openness behavior; false must still reject
statically impossible paths/transfers. Retain runtime missing-source and invalid-bound-
value rejection, with no model fallback. A local helper probe of default insertion
followed by source-path selection rejected omission and false but accepted true at
root and nested objects. This is pending migration work under standard openness, not
a defect under legacy closed-by-default semantics. Full publication verification and
implementation remain pending; no provider capability is inferred.

Evidence anchors:

- `src/main/java/ai/loomspan/internal/skill/SkillDependencyValidator.java:233`
  (`applyOutputDefaultOpenness`) recursively inserts false. Calls at lines 215 and
  229 apply it to producer schemas and destination metadata respectively;
  `selectBindingSchema` at line 243 then rejects undeclared closed-object paths.
  `checkBindingCompatibility` at line 265 also uses openness for object transfers.
- `src/test/java/ai/loomspan/internal/skill/SkillGenerationManagerTest.java:90`
  (`validatesOutputBindingPathsTypesAndProducerCountsAgainstCompleteGeneration`)
  covers closed source/destination rejection and an explicitly open producer at
  line 138, but not standard omitted-openness equivalence.
- `src/test/java/ai/loomspan/internal/outputschema/OutputBindingCompositionTest.java:49`
  (`missingAndInvalidSourcesFailBeforeModelButNullableNullSucceeds`) and
  `src/test/java/ai/loomspan/integration/DeclaredOutputBindingsOrdinaryIntegrationTest.java:111`
  (`dynamicBoundTypeMismatchFailsBeforeProviderAndWithoutAssembly`) supply runtime
  rejection controls to preserve. These are inspected tests, not new acceptance runs.

**Numeric enum equality — agreed audit correction:** use exact numeric-value
equality when comparing numeric enum members in binding compatibility checks, retaining
ordinary type-sensitive equality for other scalar values. Do not use floating-point
rounding, epsilon comparisons or string coercion. Preserve the existing overlap check
and runtime bound-value validation; do not introduce a schema implication solver.

Add focused publication-validation tests for equivalent spellings (1, 1.0, 1e0 and
1.00), genuinely different high-precision numbers, disjoint enums, and numeric versus
string values. Retain existing string-enum intersection and runtime rejection controls.
A local helper probe using lossless decimal parsing rejected 1 -> 1.0, 1 -> 1e0 and
1.0 -> 1.00 as statically incompatible. These are pending migration cases: legacy
catalog schemas restrict enums to strings, so the probe does not establish a currently
reachable numeric-enum publication defect. Implementation, migrated publication tests
and live provider acceptance remain pending.

Evidence anchors:

- `src/main/java/ai/loomspan/internal/skill/SkillDependencyValidator.java:280`
  (`checkBindingCompatibility`) compares enum members with JsonNode.equals; the
  declaration-validation caller at line 218 can reject before runtime validation.
- `src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java:938` and line 996
  restrict legacy output/input enums to string schemas. The v1 scalar-enum scope
  removes that restriction; replacing the value validator alone does not fix the
  separate binding compatibility comparison.
- `src/test/java/ai/loomspan/internal/skill/SkillGenerationManagerTest.java:36`
  (`nativeSchemasEnterExistingBindingCompatibilityAndCapturedCatalogs`) exercises
  overlapping/disjoint string enums and dynamic producer metadata, not numeric
  equivalence. `src/test/java/ai/loomspan/internal/outputschema/OutputBindingCompositionTest.java:49`
  (`missingAndInvalidSourcesFailBeforeModelButNullableNullSucceeds`) supplies runtime
  rejection controls. These are inspected tests, not newly run acceptance results.

If migration exposes an existing behavior outside the approved v1 feature scope or
requires a new binding policy, flag the concrete case for discussion. Do not let the
implementing LLM invent path semantics, value coercion, compatibility shims, or a
schema-transformation framework. Correction history remains owned by TBD-11.

## Retained technical context

Historical research establishes the stated adapter/library behavior and limitations.
Broader historical fixtures do not expand the accepted v1 profile.

| Surface | Repository evidence | Findings and migration relevance |
| --- | --- | --- |
| Java-generated tool inputs | [LoomspanMethodInputSchemaGenerator](../../../src/main/java/ai/loomspan/internal/serialization/LoomspanMethodInputSchemaGenerator.java), [SkillMethodBeanPostProcessor](../../../src/main/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessor.java) | Generator already labels schemas 2020-12. Produces closed parameter/bean objects, required properties, string enums, arrays, schema-valued `additionalProperties` for maps, and `{}` for Java `Object`. Recursive Java types currently terminate as an unconstrained object; report unsupported recursive types rather than silently weaken contracts. `SkillParam` changes names/descriptions/requiredness. Runtime-capable types are rewritten to strings with `x-loomspan-runtime-ref-capable`. Generated and authored schemas should enter the same standard compiler; Java reflection remains a declaration source. |
| Child argument bindings | [ChildInputBindingProjection](../../../src/main/java/ai/loomspan/internal/runtime/input/ChildInputBindingProjection.java), [ChildInputBindingDeclarations](../../../src/main/java/ai/loomspan/internal/runtime/input/ChildInputBindingDeclarations.java), [ChildInputBindingAssembler](../../../src/main/java/ai/loomspan/internal/runtime/input/ChildInputBindingAssembler.java) | Project model arguments, reserve framework-owned destinations, select parent input or accepted child results, then validate assembled receiving input. The advertised schema currently inserts boolean `false` at reserved properties. Closed/all-bound shape proof controls dispatch eligibility. Ordinary JSON Schema validation is not schema containment proof or binding-path analysis. |
| Output ownership | [OutputBindingProjection](../../../src/main/java/ai/loomspan/internal/outputschema/OutputBindingProjection.java), [OutputBindingComposition](../../../src/main/java/ai/loomspan/internal/outputschema/OutputBindingComposition.java), [SkillDependencyValidator](../../../src/main/java/ai/loomspan/internal/skill/SkillDependencyValidator.java) | Removes bound properties and adjusts required ancestors/nullability; checks prohibited model overrides; composes exact values. `output_from` delegates result/schema authority to a producer. Projection assumes a simple property tree and case-insensitive output paths. Open objects, unions, and cross-property assertions complicate projection. |
| Runtime references | [RefResolver](../../../src/main/java/ai/loomspan/internal/vfs/RefResolver.java), Java postprocessor above | `ref://` is a runtime execution value, separate from schema syntax. Generic input recursively resolves refs; explicit contracts traverse selected properties/items/typed maps and resolve marked nodes. Resolved `Resource`, `InputStream`, and byte arrays are not JSON instances. Do not validate them by inventing JSON Schema types. |

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
