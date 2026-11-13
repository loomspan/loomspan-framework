# PR 30.2.2 — Align generated Java contracts and binding

## Outcome

Migrate Java generation directly to canonical schemas. Group the retained Java corrections: resolved generics, naming, enum names, interface remapping, UUID, map-key restrictions, JsonUnwrapped rejection, recursion/runtime-leaf ordering, authored descriptions and demonstrated Resource container binding. Preserve SkillExecutor's YAML-owned contract and existing cleanup.

## Scope and sequencing

**Dependencies:** PR 30.2.1.

**Expected build state:** Java declaration subsystem should compile; other schema consumers may remain broken.

PRs 30.1.2–30.1.5 restore the provider transition; PRs 30.2.2–30.3.4 restore schema consumers by their owned boundaries; PR 30.4.3 reruns all blocked integrated checks.

Own Java schema generation, effective binding introspection and demonstrated binding corrections. The embedded unconstrained/runtime contracts constrain what is published and preserved; PR 30.2.3 owns the NetworkNT runtime-value adapter and JSON validation. Provider captures belong to Phase 1 and consumer integration, not additional adapter rewrites here.

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

- [ ] Advertised Java names/types/requiredness/descriptions match actual binding across all retained correction cases, including inherited resolved generics and interface name collisions.
- [ ] Unsupported map-key declarations, enabled JsonUnwrapped and genuinely recursive exposed DTOs fail registration with method/path diagnostics; finite generics and supported runtime leaves remain valid.
- [ ] Assignable Resource container leaves preserve identity without internal serialization or reads, and existing conversion/stream cleanup controls pass.
- [ ] Every applicable mandatory case in the detailed contract below has executable evidence, or an explicitly identified downstream integration owner and blocked-check record consistent with this ticket's build allowance; no in-scope required behavior is silently deferred.
- [ ] Owned fixtures and callers migrate with the behavior; preservation controls and the public-surface check are run when buildable, and all failures/blocked checks are reported accurately.

**Verification emphasis:** Registration-to-validation-to-invocation cases, no publication for rejected declarations, finite generic controls, runtime leaf identity, descriptions, conversion failures and stream closure. Deferred Java edge cases remain excluded.

## Detailed accepted contract and evidence

The following bounded extracts carry the applicable decisions, exact acceptance cases
and historical code/test anchors into this ticket. Scope ownership above determines
which cross-boundary statements are implementation here versus preservation context.
Statements about earlier research runs remain historical; do not claim they were rerun.
References to future tickets in retained text mean requirements carried by this ticket
set, not permission to postpone this ticket's owned work. Older proposals do not override
accepted decisions or the explicit exclusions above.

### Contract 1

#### Accepted SkillMethod/SkillExecutor developer model and typed-map inputs — November 11, 2026

Preserve the existing developer-facing roles and signatures. SkillMethod remains a
Java-first callable declaration: Java parameters, SkillParam metadata and existing
runtime-reference handling produce its canonical standard JSON Schema. It already
emits standard schema vocabulary; the migration removes lossy internal conversion and
custom structural validation, not its Java authoring model. SkillExecutor remains a
reference-only implementation accepting one SkillExecutorInvocation; its YAML skill
owns the business-input contract. Migrate that YAML to standard schema syntax without
reflecting the invocation/context/configuration record into a competing input schema.
Preserve duplicate callable-name rejection, executor handoff and configuration behavior.
No new annotations, signature redesign, schema override/merge system or public API is
selected. Existing ownership is a preservation requirement, not a new policy mechanism.

Compile generated and authored input contracts through the same standard pipeline.
Validate before Java binding; conversion cannot rescue an invalid contract. Preserve
map parameters and existing resource handling with the separately agreed adapters.
Already agreed exact-name/no-coercion and omission/null rules still apply: preserving
signatures does not preserve acceptance of numeric strings as integer inputs.
Recursive Java input types are rejected as agreed below; unconstrained Object input support is agreed below;
this agreement does not promise every existing signature is supported.

For example, Map<String, Integer> quantities retains a named object property whose
additionalProperties is {type: integer}. NetworkNT validates each entry before Java
binding. Allow that input vocabulary for authored schemas too, rather than creating a
Java-only exception. Maps used for execution/configuration/internal state are unaffected.
Keep existing runtime-reference traversal of map-value schemas and whole-value binding
transfers. Do not introduce arbitrary-key binding paths or general schema algebra.

Provider implementation scope:

- Responses: preserve the full input schema and explicitly send tool strict=false.
  OpenAI, Meta, OpenRouter and Ollama share this mapper; route acceptance is separate.
- Anthropic: preserve nested typed-map schemas and fix the already identified root
  constraint loss. This root fix is independently required for ordinary contracts.
- Gemini: preserve the canonical schema through raw parametersJsonSchema, bypassing
  both GoogleGenAiToolCallingManager's OpenAPI conversion and the final parameters(Schema)
  conversion. A final-setter change alone is insufficient. Raw SDK serialization is
  evidenced; the exact Spring AI integration remains an implementation gate, not an
  established options-only workaround. Keep the existing tool manager/loop ownership.

This is the simplest evidenced direction: retain useful Java declarations and fix
concrete schema-fidelity gaps. It needs no second validator, new tool loop, map conversion,
provider registry or silent schema weakening. Output/planning feature restrictions stay
separate. Relevant provider contracts: [OpenAI function calling](https://developers.openai.com/api/docs/guides/function-calling),
[Anthropic strict tool use](https://platform.claude.com/docs/en/agents-and-tools/tool-use/strict-tool-use),
[Google FunctionDeclaration](https://aistudio.google.com/api-reference/caching#Resource:FunctionDeclaration).

Mandatory ticket acceptance tests:

- Generate and validate string-, integer- and record-valued maps, nested maps and maps
  inside arrays/records. Preserve value constraints, descriptions, requiredness and
  closed record properties. Invalid entries fail with precise paths before invocation;
  integer-valued maps reject numeric strings under the agreed no-coercion policy.
- Equivalent authored and generated input contracts have equivalent standard validation.
  Preserve input values, Java binding, runtime-reference traversal and whole-map binding
  transfer without expanding binding path syntax. Retain direct runtime-value adapter
  tests for marked leaves reached through map-value schemas.
- SkillMethod parameter metadata remains effective. YAML executor input validation
  precedes invocation; SkillExecutorInvocation input/configuration/context handoff and
  duplicate callable-name rejection remain unchanged. Do not generate a business-input
  contract from the executor's invocation record.
- Capture actual requests through all three integrations. Assert complete nested map
  schemas and ordinary root constraints, explicit Responses strict=false, and Gemini
  parametersJsonSchema fidelity without prior lossy conversion. Preserve options and
  canonical schemas; no fallback or replacement of constrained values with open maps.
- Later suite gate, not execution in this ticket: verify selected live provider/model routes, including a typed-map tool alongside a
  separate supported structured final-output contract. Shared mapper proof does not
  prove Meta/OpenRouter/Ollama route acceptance. Missing credentials or rejected features
  remain explicit gates, not passing results. Full framework validation must prevent
  invalid tool arguments from invoking Java side effects.
- Continue rejecting typed-map native output/planning contracts under their deferred
  scope; input support must not silently enable excluded output features.

Existing AdapterTest sources/captures and RC2 source inspection support these findings;
no new live calls, production implementation or ticket creation accompany this agreement.

### Contract 2

#### Retained generated-input corrections

**Declared map key types — registration restriction agreed November 12, 2026:**
Reject generated Java map declarations whose resolved key type requires conversion of
JSON property names. Preserve String-keyed maps and raw/Object-keyed maps accepting
string keys; do not convert property names or introduce property-name schema features.
Use the agreed effective Java-type traversal, including nested DTOs, collection items,
map values and inherited generic bindings. Report the method, parameter/property path
and unsupported key type at registration, before publishing a misleading contract.
Runtime non-string-key rejection still applies separately to incoming values.

The [preserved probe](../../../docs/maintainers/probes/tbd-13-map-key-types/README.md)
registers Map<Integer,String>, validates two distinct string keys "1" and "01", and
invokes the method with only one integer-keyed entry: the second value wins after
binding. A nonnumeric string key also validates but fails during Java binding. The
isolated registration-check candidate rejects direct and nested integer-key declarations;
String-keyed, raw and Object-keyed controls preserve both string keys. Four existing
generator and two binder test methods pass against it. This is actual local registration,
validation and invoker evidence, not public SkillTemplate, NetworkNT or live capability.

Implementation and full acceptance remain pending. Add registration tests for other
numeric, enum and custom key types, inherited resolved generics and full diagnostics;
retain valid nested maps and marked resource-map values. Inspect effective application
mapper behavior where custom key deserializers are configured; the probe does not
establish fidelity for arbitrary custom mapper configuration. Reuse existing registration
and binding machinery rather than designing a general serializer-to-schema system.

Evidence anchors (audit-time lines and symbols):

- `src/main/java/ai/loomspan/internal/serialization/LoomspanMethodInputSchemaGenerator.java:64`
  (`schemaFor`) emits map value schemas without checking key types.
- `src/main/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessor.java:596`
  (`materializeValue`) preserves keys; `:567` (`convertArgument`) converts to the
  declared JavaType, including key conversion.
- `src/test/java/ai/loomspan/internal/serialization/LoomspanMethodInputSchemaGeneratorTest.java:70`
  (`typedMapSchemasRemainConstrainedAfterObjectBroadening`) tests typed values with
  String keys. Binder controls at `SkillMethodBeanPostProcessorTest.java:118` and
  `:154` do not cover declared numeric-key collisions. The runtime-map-key and
  open-property corrections do not cover this all-string-key counterexample.

**JsonUnwrapped declarations — agreed November 12, 2026:** reject enabled
@JsonUnwrapped in generated Java input contracts at registration, using effective
binding-mapper introspection and diagnostics identifying the method, parameter and
property path. Preserve disabled annotations and ordinary nested DTOs. This is an
explicit bounded declaration restriction: these annotated DTOs cannot be used directly
as generated SkillMethod inputs. Do not ignore the annotation, rewrite values or add
a general flattening/serializer system. Supporting prefixes/suffixes, collisions,
requiredness and runtime-reference mapping would require a separate design decision.

The [preserved probe](../../../docs/maintainers/probes/tbd-13-unwrapped/README.md)
reproduces a published nested contract versus flattened Jackson binding: flattened
input is rejected, while schema-valid nested input executes with its supplied leaf
lost. A temporary rejection candidate preserves disabled-annotation/ordinary DTO
controls; four existing generator and two binding test methods pass. Existing naming,
enum, UUID, generic and resource corrections do not handle structural unwrapping.
This is local registration/validation/invoker evidence, not full public invocation,
migrated NetworkNT/provider or live acceptance. Implementation remains pending.

Add registration rejection cases for fields, getters, effective application mixins,
prefix/suffix settings and nested/items/map locations, with method/path diagnostics
and no publication/invocation. Preserve disabled annotations and existing DTO/binding
coverage. Evidence anchors: LoomspanMethodInputSchemaGenerator.java:114/:120/:121
introspects then emits/requires a nested named property; SkillMethodBeanPostProcessor.java:567
binds through the application mapper, with bean-map materialization at :623. Files are
under src/main/java/ai/loomspan/internal/serialization and core. Existing generator and
postprocessor tests do not cover unwrapping. The separate read-only-property probe found
correct exclusion by creation introspection; no read-only-property correction is selected.

**UUID input contracts — agreed November 12, 2026:** map java.util.UUID to the
existing generated string scalar shape, including nested properties, collection items
and map values. Preserve descriptions, requiredness, surrounding contracts, method
signatures and existing Java conversion failures. Do not add format assertions or a
general serializer-to-schema system incidentally. Existing naming/enum/generic/resource
corrections do not repair this scalar classification.

The [preserved probe](../../../docs/maintainers/probes/tbd-13-uuid/README.md) uses actual
registration, validation and registered invokers: UUID strings fail the current published
object contract but bind successfully; advertised leastSignificantBits/mostSignificantBits
objects validate but fail binding before the method body. Nested record/list/map cases
show the same mismatch. An isolated string-mapping candidate fixes the tested shapes;
four existing generator and two binding tests pass. This is an existing local mismatch,
not a new validator defect. Full public-invocation, migrated-validator, adapter and live
acceptance remain pending, as does production implementation.

Add focused registration/validation/invocation tests for those shapes, unchanged
requiredness/descriptions/sibling validation and malformed binding controls. Evidence:
LoomspanMethodInputSchemaGenerator.java:93/:114 omits UUID from scalar classification
and introspects its bean properties; SkillMethodBeanPostProcessor.java:337/:567 generates
then binds through the application mapper. These are under src/main/java/ai/loomspan/internal/
serialization and core. Existing LoomspanMethodInputSchemaGeneratorTest.java:26/:50/:70
and SkillMethodBeanPostProcessorTest.java:118/:154 do not cover UUID. The probe README
retains commands, candidate scope and evidence limitations.

**Enum names and Java binding — agreed audit correction:** generated string-enum
choices must agree with Java binding for enum constants with JsonProperty names.
The current generator uses String.valueOf while Jackson binding honors the annotated
name: an isolated probe with JsonProperty("wire-ready") on READY showed that READY
passed schema validation but failed binding, while wire-ready bound successfully but
failed validation. Replacing the legacy validator alone does not fix this mismatch.
Align generation and binding for this bounded case without introducing a general
custom-serializer schema system or changing method signatures. Add a registration/
invocation regression proving the advertised value validates and reaches the intended
enum constant; retain ordinary enum coverage. The probe establishes the narrow mismatch,
not full invocation or live-provider acceptance. Implementation remains pending.

Evidence anchors (source/test inspection; new acceptance remains pending):

- `src/main/java/ai/loomspan/internal/serialization/LoomspanMethodInputSchemaGenerator.java:90` (`values.add(String.valueOf(value))`) advertises Java string values; `src/main/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessor.java:567` (`return objectMapper.convertValue(materializedValue, parameterJavaType)`) binds using Jackson.
- `src/test/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessorTest.java:118` (`invokesCapabilityUsingEnvelopeMap`) is the existing registration/binding harness, but does not exercise an enum with JsonProperty. Add the annotated-enum case there; the isolated probe described above is not a committed regression test.

**Interface parameter-name remapping — agreed audit correction:** preserve the
existing support for SkillMethod declarations on interfaces implemented by Spring
beans. When interface contract names differ from implementation parameter names,
build the final properties and required list from the original generated parameter
schemas by position, rather than removing and reinserting names in the same mutable
map/list. Overlapping names must not overwrite a later parameter's schema or alter
its requiredness. Preserve the existing contract-selection rules, implementation
types, SkillParam descriptions/requiredness, runtime-reference handling and binding
by contract name; no signature restriction or new public API is needed.

Add a registration/validation/invocation regression with swapped interface and
implementation parameter names and different types, plus mixed required/optional
parameters. Assert both properties retain the correct types and descriptions, the
required list matches the selected contract metadata, and valid contract-name input
reaches the intended argument positions. Retain ordinary matching-name and existing
interface/proxy invocation coverage. A local registration probe published only
right:string for interface (String left, int right) implemented as (String right,
int left): intended input failed schema validation although direct binding returned
the expected result. The existing secured generic JDK-proxy integration test passed
through SkillTemplate.invoke(), establishing interface support but not collision
coverage. The remapping fix and its focused tests remain pending; no provider
capability is inferred from this local evidence.

Evidence anchors (source/test inspection; new acceptance remains pending):

- `src/main/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessor.java:333` (`private String buildInputSchema`) mutates properties in place; `src/main/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessor.java:393` (`required.remove(index)`) mutates required names while remapping.
- `src/test/java/ai/loomspan/integration/JavaSkillIntegrationTest.java:109` (`void securedGenericJdkProxyRetainsCanonicalInputContractAndActualMethodAdvice`) proves interface/proxy invocation; `src/test/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessorTest.java:137` (`handlesMissingOptionalParameterWithoutCrashing`) covers ordinary optional arguments. Neither exercises swapped names. The earlier passing proxy test is historical local evidence, not a rerun during this backfill.

**Java property naming and binding — agreed audit correction:** generate Java input
property names using the application binding mapper's property introspection, while
retaining the dedicated schema mapper for schema JSON handling. Production currently
generates properties with the schema mapper but binds with the application mapper;
an application naming strategy can therefore make the published contract disagree
with binding. Preserve default naming, explicit property-name annotations, required
lists and nested runtime-reference traversal under the same property names. This is
a naming-alignment correction, not a general custom-serializer schema system or a
change to framework-owned trace/observability serialization.

Add a registration/validation/invocation regression using a configured SNAKE_CASE
application mapper and a nested Java property. Assert the published schema and
resolved contract use first_name for Java firstName, valid input reaches the method
unchanged, and the unadvertised spelling is rejected under the closed contract.
Retain default-name coverage and exercise production's separate mapper wiring.
The local registration/binding probe found firstName passed validation but bound
to null, while first_name failed validation but bound to Ada when invoked directly.
Existing generator tests use the same mapper for generation and conversion; the
snake-case observability integration fixture does not establish skill-input binding
coverage. Implementation and focused verification remain pending; no provider
capability is inferred from this local evidence.

Evidence anchors (source/test inspection; new acceptance remains pending):

- `src/main/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessor.java:129` (`private SkillMethodBeanPostProcessor(ObjectMapper objectMapper, ObjectMapper schemaMapper`) wires separate mappers; `src/main/java/ai/loomspan/internal/serialization/LoomspanMethodInputSchemaGenerator.java:114` (`.introspectBeanDescriptionForCreation(type)`) chooses schema property names; `src/main/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessor.java:638` (`private Map<String, JavaType> propertyTypes`) uses binding-side introspection for runtime leaves.
- `src/test/java/ai/loomspan/internal/serialization/LoomspanMethodInputSchemaGeneratorTest.java:20` (`private final ObjectMapper objectMapper`) shares one mapper between generation and contract conversion; `src/test/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessorTest.java:154` (`publishesDescriptionsAndBindsUnconstrainedReflectedInputs`) exercises ordinary input binding. Neither proves production wiring with a custom naming strategy.

**Inherited generic parameters — agreed audit correction:** resolve method parameter
types against the concrete bean class and reuse those resolved types for generated
schemas, runtime-reference marking and Java binding. Current context-free resolution
of an inherited T can collapse it to Object even when the bean extends Base<Integer>.
Preserve existing declaration selection and proxy invocation; no new signature or
public API is needed. Add a registration/validation/invocation regression for an
inherited T resolved to Integer: publish an integer contract, accept integer input,
and reject strings before entering the method. Include a resolved runtime-reference
parameter to verify marking and binding use the same type information. Retain existing
concrete-override generic-interface coverage and resolved generic recursion checks.
A local registration/invocation probe published value:{} for Base<T>.increment(T)
inherited by Base<Integer>'s concrete subclass: 4 validated and returned 5, while a
non-integer string validated then failed with ClassCastException. This establishes
the local mismatch, not provider behavior. Implementation and focused tests remain
pending.

Evidence anchors (source/test inspection; new acceptance remains pending):

- `src/main/java/ai/loomspan/internal/serialization/LoomspanMethodInputSchemaGenerator.java:41` (`mapper.constructType(parameter.getParameterizedType())`), `src/main/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessor.java:354` (`applyRuntimeInputSemantics(parameterSchema, objectMapper.constructType(parameter.getParameterizedType()))`) and `src/main/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessor.java:538` (`JavaType parameterJavaType = objectMapper.constructType`) independently resolve without the concrete owning class.
- `src/test/java/ai/loomspan/integration/JavaSkillIntegrationTest.java:109` (`void securedGenericJdkProxyRetainsCanonicalInputContractAndActualMethodAdvice`) covers a concrete override; `src/test/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessorTest.java:72` (`inheritedSkillUsesSpringDeclaringClassPolicySemantics`) checks inherited security policy, not inherited generic parameter resolution.

#### Recursive Java input types — agreed November 11, 2026

Reject recursive types exposed through generated skill-input contracts at registration,
before publishing the capability. Replace the current visiting-class fallback to
{type: object} with a clear error identifying the method, parameter/property path and
recursive type cycle. That fallback avoids endless generation but silently drops the
recursive node's field constraints; later Java binding is not equivalent validation.
Faithful arbitrary-depth recursive contracts require references or another recursive
validation mechanism. Schema references remain excluded from v1; do not add a separate
Java validator, schema truncation or an invented expansion-depth limit.

Detect direct and mutual recursion, including cycles through collection items and
map-value types. Track the active expansion path using resolved type information:
repeated classes in independent branches are not cycles, and finite generic nesting
such as Box<Box<String>> must not fail merely because the raw Box class repeats.
This concerns exposed input contracts, not recursive application structures behind
an executor or ordinary acyclic nested DTOs. Runtime values and standalone Object
acceptance are separate concerns.

Mandatory ticket acceptance tests:

- Reject direct self-reference and mutual recursion with method, input path and cycle
  diagnostics; reject before capability publication or provider invocation.
- Reject recursion through arrays/collections and typed-map values, including nested
  combinations; do not emit an open-object fallback, partial contract or depth cutoff.
- Accept repeated DTO types in sibling properties and separate parameters, acyclic
  nested DTOs, and finite resolved generic nesting such as Box<Box<String>> with all
  leaf constraints retained. Include a genuinely recursive generic declaration to
  ensure resolved-type handling does not miss cycles.
- Preserve unrelated valid SkillMethod declarations and YAML-owned executor contracts;
  do not inspect/restrict recursive data structures internal to executor implementations.
- Replace the existing recursiveDtoFallbackRemainsBoundedAndObjectShaped acceptance
  expectation with explicit registration rejection and nonrecursive regression cases.

Source evidence: LoomspanMethodInputSchemaGenerator.schemaFor currently uses a set of
raw classes and emits only type: object when a class repeats; the existing generator
unit test explicitly asserts that fallback. This agreement is not a production change
or new passing-test claim.

Evidence anchors (source/test inspection; new acceptance remains pending):

- `src/main/java/ai/loomspan/internal/serialization/LoomspanMethodInputSchemaGenerator.java:104` (`else if (!visiting.add(raw))`) emits the open fallback; `src/test/java/ai/loomspan/internal/serialization/LoomspanMethodInputSchemaGeneratorTest.java:90` (`recursiveDtoFallbackRemainsBoundedAndObjectShaped`) explicitly asserts that legacy fallback. Replace that expectation as agreed; `src/test/java/ai/loomspan/internal/serialization/LoomspanMethodInputSchemaGeneratorTest.java:70` (`typedMapSchemasRemainConstrainedAfterObjectBroadening`) supplies nonrecursive map controls.

**Runtime-resource sequencing — agreed November 12, 2026:** recognize existing
Resource, InputStream and byte[] leaves before ordinary bean-property expansion and
recursive-contract rejection. Reuse the existing supported-type predicate/semantics,
including nested properties, collection items and map values, rather than maintaining
divergent type lists. These leaves expose marked string contracts, not their Java
implementation internals. Preserve strict rejection for genuinely recursive exposed
DTOs, resolved-type cycle tracking, finite generic nesting and the separately agreed
description, naming and container-binding corrections. No public API change is needed.

The [preserved probe](../../../docs/maintainers/probes/tbd-13-runtime-recursion/README.md)
uses actual registration: the current code registers direct/nested Resource inputs;
replacing the generator's fallback with rejection first fails on java.io.File before
runtime rewriting. An isolated candidate recognizing runtime leaves first preserves
identical published resource schemas and direct validation/invocation, while rejecting
real DTO cycles, including beside a Resource. Seven existing binding/cleanup test
methods passed with the candidate. This is a reproduced migration-order hazard, not
a current Resource-registration defect. The candidate retains raw-class cycle tracking;
it is not the complete agreed resolved-generic implementation or full/live acceptance.

Tickets must preserve this ordering and add combined registration cases for direct,
nested/items/map resource leaves, genuine DTO cycles alongside them, finite generic
nesting, metadata fidelity and existing binding/cleanup behavior. Evidence anchors:
SkillMethodBeanPostProcessor.java:337 generates before :354 applies runtime semantics;
:410/:457 rewrites resource leaves and :662 defines their type predicate.
LoomspanMethodInputSchemaGenerator.java:104/:114 handles cycles/bean traversal.
Both are under src/main/java/ai/loomspan/internal/ in core and serialization.
Existing SkillMethodBeanPostProcessorTest.java:330 verifies reference-friendly schemas;
LoomspanMethodInputSchemaGeneratorTest.java:90 still expects recursive fallback. Neither
combines strict recursion rejection with resource registration. Implementation is pending.

#### Unconstrained Object input values — agreed November 11, 2026

Preserve unconstrained JSON-compatible values in ordinary generated and authored input
contracts. Java Object maps to an empty schema {}, optionally with description metadata;
this applies to named parameters, nested fields, collection items and map values.
Invocation roots remain objects with named arguments. Unlike the recursive fallback,
Object deliberately declares no value constraints, so the empty schema is faithful.
Do not replace it with type: object or type: string, stringify values, infer constraints
from runtime contents, or introduce a separate Java-only schema feature. Native
output/planning scope is unchanged; this agreement concerns ordinary inputs.

Retain JSON-compatibility checks before conversion: JSON scalar values, finite numbers,
string-keyed maps and lists are accepted recursively; arbitrary POJOs, Resources,
InputStreams, binary arrays and non-finite floating-point values do not acquire
permission merely because the declared Java target is Object. Do not serialize Java
internals to manufacture acceptable JSON. Existing explicitly marked resource adapters
remain separate. Under explicit contracts, ref:// strings in unconstrained values stay
ordinary strings, including nested values; preserve separate generic-contract behavior.

Preserve descriptions and surrounding requiredness, object openness and array bounds.
Under standard semantics {} permits actual null. Required controls presence: a required
Object value may be null but must be present. Replace the old ordinary-validation
required-null-as-missing behavior here; optional absence stays absent. Keep canonical
input/schema intact and preserve Java binding after successful validation. Provider
transport and selected live routes must carry/accept unconstrained leaves without
silent narrowing; existing mapper evidence is not live acceptance proof.

Mandatory ticket acceptance tests:

- Accept string, boolean, finite numeric, object, array and null values, including
  mixed nested JSON structures, through equivalent generated/authored input schemas.
  Cover standalone Object parameters, DTO fields, collection items and map values.
- Required absent fails, required present null passes, optional absence remains absent
  and optional present null passes. Keep invocation roots object-shaped.
- Reject non-string object keys, non-finite floats/doubles, arbitrary POJOs, Resource,
  InputStream and byte[] values, including nested occurrences, before Java invocation.
  Do not consume streams or serialize object internals during these checks.
- Preserve literal ref:// strings under explicit unconstrained contracts, unchanged
  values, descriptions and Java binding. Retain distinct marked-resource and generic
  input resolution regressions.
- Verify ordinary sibling failures, closed-object extra fields, required properties and
  surrounding array bounds still fail; unconstrained leaves grant no parent bypass.
- Capture all three integrations' actual tool requests with unconstrained leaves and
  descriptive-only leaves. No inferred type, substituted string/object contract, lost
  description or canonical mutation. Include selected live-route acceptance alongside
  local invalid-input/non-invocation fixtures; untested routes remain explicit gaps.

Existing generator and postprocessor tests demonstrate intended JSON-value acceptance
and binding. No production changes, new test-run acceptance or tickets accompany this
agreement.

Evidence anchors (source/test inspection; new acceptance remains pending):

- `src/main/java/ai/loomspan/internal/serialization/LoomspanMethodInputSchemaGenerator.java:99` (`else if (raw == Object.class)`) already emits an empty schema; `src/test/java/ai/loomspan/internal/serialization/LoomspanMethodInputSchemaGeneratorTest.java:50` (`javaObjectGeneratesUnconstrainedProviderSchema`) tests non-null JSON values, while `src/test/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessorTest.java:154` (`publishesDescriptionsAndBindsUnconstrainedReflectedInputs`) exercises descriptions and binding. These do not establish the new required-present-null behavior or live route acceptance.

### Contract 3

#### Non-attachment runtime-reference metadata — agreed November 11, 2026

Retain the existing boolean `x-loomspan-runtime-ref-capable` marker as internal
Java-input metadata and preserve its existing conversion behavior. The Java
postprocessor generates string schemas with this marker for Resource, InputStream
and byte[] parameters, including nested properties and collection items. Under
explicit contracts, RefResolver uses marked nodes to select reference resolution;
ordinary unmarked strings remain strings. Preserve the separate existing generic
input resolution behavior. This agreement does not add an authored YAML feature or
approve arbitrary framework annotations.

Keep the marker in canonical generated contracts and internal metadata. Remove only
this recognized annotation from provider schema copies, walking actual schema nodes
and retaining their string type, description and standard assertions. A data property
named `x-loomspan-runtime-ref-capable` must remain a data property.

For example, the canonical generated leaf remains
`{"type":"string","description":"Provide a ref:// URI for the resource content.","x-loomspan-runtime-ref-capable":true}`;
its provider copy contains the same type and description without the annotation.
The caller's reference string is validated before resolution and subsequently passed
through existing authorized resolution and Java conversion. Preserve conversion and
stream cleanup, including existing direct runtime-object support.

Rationale: SkillInputValidator currently accepts Resource, InputStream and byte[]
at marked string leaves. Replacing it with ordinary JSON string validation would
break those inputs; resolving strings first would likewise present non-JSON objects
to the validator. Preserve this Java invocation concern separately from attachment
materialization. Do not automatically apply attachment descriptors, MIME checks or
repeatability requirements. The separately agreed direct Java-value validation
mechanism follows below.

Mandatory ticket acceptance tests: marked versus unmarked reference strings;
string validation before resolution; direct Resource, InputStream and byte[] inputs;
nested properties and array items; existing conversion success/failure and stream
cleanup; authorization and generic-resolution regressions; canonical marker retention;
provider-copy annotation removal with standard assertions, descriptions and similarly
named data properties intact. Preserve original values and canonical schemas. Existing
source/test inspection is evidence of behavior, not a new test-run acceptance claim.

**Authored parameter descriptions — agreed audit correction:**
`SkillMethodBeanPostProcessor` currently applies `SkillParam.description` before
`rewriteAsRefFriendlyString` clears the node and installs generic reference guidance.
Retain a nonblank authored description when rewriting Resource, InputStream and byte[]
parameter schemas; use the existing generic guidance only when no nonblank description
is supplied. Preserve the authored text exactly, string schemas, runtime-reference
markers, requiredness, conversion and stream cleanup. This corrects an existing
metadata loss; it does not redesign Java declarations or resource binding.

Add focused registration tests for each of Resource, InputStream and byte[] with
an authored SkillParam description. Assert exact description retention in the published
tool schema and resolved input contract, alongside string type, runtime marker and
requiredness. Cover absent/blank descriptions retaining existing generic guidance.
Reuse existing conversion/cleanup regressions. Existing tests cover ordinary Object
descriptions and unannotated runtime-reference schemas separately; they do not prove
this combination. Source inspection establishes the loss; implementation and new
passing-test evidence remain pending.

Evidence anchors (source/test inspection; new acceptance remains pending):

- `src/main/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessor.java:382` (`objectSchema.put("description", skillParam.description())`) writes the authored description before `src/main/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessor.java:457` (`private void rewriteAsRefFriendlyString`) clears the node and installs a generic description.
- `src/test/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessorTest.java:154` (`publishesDescriptionsAndBindsUnconstrainedReflectedInputs`) covers ordinary descriptions; `src/test/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessorTest.java:330` (`publishesRefFriendlyInputSchemasForRefCapableParameters`) covers runtime markers/types. Neither combines an authored description with a rewritten runtime-reference parameter.

**Resource container binding — agreed audit correction:** preserve already assignable
Resource leaves through container binding without serializing their internals. Current
recursive materialization retains the Resource, but the subsequent whole-container
ObjectMapper.convertValue attempts to serialize it. A local registration/validation/
invocation probe accepted a Resource directly and preserved its identity, while the
same object in List<Resource> passed validation but failed binding when Jackson tried
to access its URI. This is not an incompatible target-type conversion.
Add focused validation/invocation tests for direct and reference-resolved List<Resource>
inputs, asserting original object identity and no content reads during binding. Retain
existing Resource-to-string/byte conversion and stream-cleanup tests. Existing typed
collection tests convert Resources to strings and do not prove Resource pass-through.
The list failure is reproduced; verify other affected container shapes before extending
the correction beyond demonstrated cases. Implementation and focused tests remain
pending; no new public API or general serializer system is selected.

Evidence anchors (source/test inspection; new acceptance remains pending):

- `src/main/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessor.java:570` (`private Object materializeValue`) preserves Resource leaves, but `src/main/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessor.java:567` (`return objectMapper.convertValue(materializedValue, parameterJavaType)`) then converts the entire container.
- `src/test/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessorTest.java:306` (`passesResourceBackedRefsThroughResourceAndInputStreamParameters`) covers direct leaves; `src/test/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessorTest.java:373` (`materializesResourceLeavesInsideTypedCollectionParameters`) converts to strings, and `src/test/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessorTest.java:350` (`materializesNestedResourceLeavesInsideTypedRecordParameters`) converts to String/byte[] leaves. These do not prove a Resource-valued container remains intact.

## Reproduction anchors

Retained focused reproductions (Java 21/Maven; inspect each README's candidate
boundary before use):

```powershell
./docs/maintainers/probes/tbd-13-map-key-types/run.ps1
./docs/maintainers/probes/tbd-13-unwrapped/run.ps1
./docs/maintainers/probes/tbd-13-uuid/run.ps1
./docs/maintainers/probes/tbd-13-runtime-recursion/run.ps1
```

These are historical local registration/binding mechanisms, not complete public
invocation, migrated NetworkNT or live evidence. Preserve the distinction between
strict recursion's sequencing hazard and current working resource registration.

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
