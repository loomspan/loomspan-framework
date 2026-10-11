# TypeSafe primitives draft roadmap

Status: Review corrections and transport/accounting decisions accepted, including TypeSafe fidelity, the name-collision exception, and Score criteria validation. No TypeSafe implementation authorization.
Updated: 2026-10-10.

Loomspan should support recognizable TypeSafe skills that authors can invoke
directly through `SkillTemplate` or compose as children of other skills. Preserve
TypeSafe's vocabulary and primitive semantics while adding Loomspan's registered
contracts, execution controls, and diagnostics.

Use this roadmap to test examples and refine the contract before creating tickets.
The phases below describe a proposed delivery sequence, not authorization to begin
implementation. Syntax examples are proposals and do not work in Loomspan today.

## Agreed direction

- Integrate TypeSafe itself as a first-class capability. Its familiar programming
  model and name recognition are part of the value.
- Support both direct TypeSafe account API access and access through an OpenRouter
  account in the initial implementation.
- Register TypeSafe skills in the same callable skill surface as other skills.
  Direct and child invocation should share the same input and output contract.
- Return the contents of TypeSafe's `answers` object as the skill result, retaining
  named questions and complete answer objects. Keep the full provider response in
  canonical execution diagnostics.
- Support Choice, Noul, and Score using TypeSafe terminology, including `state`,
  `questions`, `instructions`, and `criteria`.
- Follow TypeSafe's supported request contract and preserve its behavior. Additional
  Loomspan restrictions require a demonstrated integration need and must stay scoped
  to that need. Authoring advice is not a request-rejection rule. The explicitly
  accepted case-insensitive name-collision restriction remains for compatibility
  with Loomspan's existing schema and binding behavior.
- Let authors parameterize a recognizable TypeSafe request using skill inputs.
  Author flexibility is an explicit design goal; retain the familiar TypeSafe
  structure instead of requiring a different binding mechanism for each primitive.
- Explore concrete examples before committing to implementation tickets.
- Keep routing, confidence gates, and composite scoring outside the initial
  primitives implementation.

## Proposed contract to exercise

### Architecture and development constraints

Solve the agreed requirements completely with the simplest design that satisfies
them. Simplicity means fewer concepts, execution paths, and sources of truth to
maintain; it does not mean omitting required behavior, validation, diagnostics, or
failure handling. Both connection routes, all three primitives, dynamic criteria,
and direct and child invocation remain in scope.

Build for the needs in this roadmap and the framework's current behavior. Do not
add extension points, generic provider frameworks, configurable policies, or
abstraction layers for hypothetical future features. Introduce a shared abstraction
only where current call sites need it and it makes the implementation simpler.
Deferred patterns impose no architectural requirements on this work.

Minimize technical debt as part of delivering each phase. Change an unsuitable
internal boundary directly, remove superseded code, and update all affected callers,
tests, fixtures, and documentation in the same change. Avoid duplicated contract
logic, parallel lifecycle machinery, temporary adapters, and knowingly incomplete
integration left for a later cleanup. Keep refactoring focused on what this feature
requires; this is not authorization for unrelated redesign.

Loomspan is still in development. Breaking replacements and removal of obsolete
code or contracts are welcome when they produce the simpler complete design.
Do not add compatibility shims, deprecated forwarding methods, legacy readers,
dual formats, or fallback execution paths to preserve superseded behavior. This
explicit direction governs this feature even where general repository guidance
discusses compatibility shims. Public API changes must still be deliberate and
reflected in the closed allowlist, documentation, and supported-surface checks;
those checks establish the intended current API, not a requirement to retain the
old one. Preserve behavior that remains a requirement, not obsolete implementation
shapes. This policy concerns code and contracts, not deleting user data or history.

At each phase review, identify the current requirement served by each new layer,
verify that shared rules have one owner, and remove the replaced path. A phase is
complete when its required behavior and affected consumers work together without
a compatibility bridge or a deferred cleanup needed to make the design coherent.

### Scope review: what this feature actually adds

The implementation scope is a TypeSafe leaf declaration, input substitution within
its request, request-shape checks, one System One HTTP execution path supporting
both account routes, and registration through existing execution/catalog/Console
surfaces. Existing authorization, bindings, retry budgets, usage accounting,
tracing, and reload semantics must work with that path; they are integration
requirements, not requests to redesign those facilities.

This review removes earlier over-specification:

| Earlier implication | Selected scope |
| --- | --- |
| Richer generated schemas require expanded framework schema machinery | Describe named answers using existing schema vocabulary; put numeric ranges in descriptions and keep dynamic maps open |
| Prove template references from input schemas at registration | Check expression syntax at registration; resolve references against actual input at invocation |
| Canonicalize numbers specially for interpolation | Use the existing JSON codec's number representation |
| Two drivers and route-specific adapters | One `typesafe` driver for System One; connection base URL and API key select the route |
| Add specialized Console evaluation summaries | Display the skill kind and captured request/response using existing catalog and trace views |
| Binding examples require a new compatibility checker | Retain existing parent output-binding registration checks and runtime destination validation; add no stronger schema proof system |
| Exhaustive cross-product acceptance matrix | Test shared contracts once and route/entry-point integration where behavior differs |

No general JSON Schema expansion, generic template engine, extra lifecycle,
output validator, new quota/cost system, model discovery, routing engine, or new
observer API is required. Author examples demonstrate data supplied to TypeSafe;
they do not authorize built-in business rules or automated actions.

### Invocation contract

The author declares a business input schema and a TypeSafe request template.
Loomspan validates the input, resolves the template into a structured request,
validates that request, calls TypeSafe, and returns the contents of the successful
response's `answers` object. Trust TypeSafe's answer contract; do not revalidate it.

The current proposal fixes question names and primitive types per declaration.
This is an initial registered-skill authoring boundary that provides stable catalog
schemas, not a TypeSafe restriction. TypeSafe accepts a questions map per request.
Dynamic question maps/types remain outside the agreed initial scope; if they become
a current need, revisit this boundary rather than letting schema convenience alone
exclude them. This integration does not claim to expose every dynamic request form.
One skill may declare one question or a batch of independent questions. A batch
does not allow one question to consume another question's answer.

Model selection and connection configuration remain outside the request template.
Keep `SkillTemplate`'s existing String return contract; a TypeSafe result would be
JSON text. A new typed Java facade is not part of this proposal.

### Request template example

```yaml
name: assessSupportContact
description: Assess escalation and previous contact.
model: typesafeDefault

input_schema:
  type: object
  properties:
    times:
      type: integer
  required: [times]
  additionalProperties: false

typesafe:
  state: >-
    I have asked ${/input/times} times now.
    Can I please just talk to a real person?
  questions:
    is_human_escalation:
      type: noul
      instructions: Is the customer asking for a human agent?
    is_repeat_contact:
      type: noul
      instructions: Has the customer contacted support about this before?
      criteria:
        "true": Mentions a prior attempt, ticket, or that they have asked before
        "false": No sign of any previous contact
```

Input `{"times": 3}` would produce the state string
`I have asked 3 times now. Can I please just talk to a real person?`
(the `>-` YAML scalar removes the trailing newline).
The `typesafeDefault` alias would identify a configured TypeSafe model.

### Substitution semantics

Resolve expressions within a parsed document, then serialize the resulting JSON.
Do not substitute directly into raw JSON or YAML source.

| Form | Proposed meaning |
| --- | --- |
| `"Contacted us ${/input/times} times"` | Interpolate a scalar into a string |
| `"${/input/context}"` | Replace the entire value, retaining its JSON type |

For example, structured state could contain:

```yaml
state:
  customerMessage: "${/input/message}"
  previousContacts: "${/input/history}"
  accountContext: "${/input/account}"
```

An array in `history` remains an array; an object in `account` remains an object.
Quotes and newlines in strings remain data. Inserted values are never evaluated
again as templates. Expressions select input data and do not execute code.

The proposed mechanism applies to state, instructions, and criteria. Missing
references fail before the provider call. Objects and arrays require whole-value
substitution rather than implicit conversion inside a string. The Phase 0 decisions
below define escaping, conversion, null handling, and traversal.

### Output contracts

Prefer a framework-derived output schema over a second author-maintained schema.
The agreed skill result is the contents of the provider's `answers` object,
serialized as JSON text through the existing String return contract. There is no
outer `answers` wrapper and no provider metadata in the business result.

Preserve each named answer's complete structure and values, including its type,
probabilities, confidence, and legend where applicable. Do not collapse an answer
to its selected value or unwrap the question name for a single-question skill.
Use one consistent result shape without an output-mode setting initially.

For example, a Choice answer is returned as:

```json
{
  "department": {
    "type": "choice",
    "choice": "technical",
    "confidence": 0.78,
    "probabilities": {"technical": 0.85, "sales": 0.0, "billing": 0.15}
  }
}
```

These values are illustrative. The generated output schema describes the named
answers at the root. A child-result binding uses `/department/choice`, not
`/answers/department/choice`. Direct invocation and nested invocation return the
same shape on both connection routes.

Preserve the full provider response in canonical execution diagnostics, including
`model`, `usage`, and provider-specific metadata. Feed usage into existing runtime
accounting. Use the existing authenticated Console trace view for that envelope;
keep the current derived-journal observer contract. No new metadata API is needed.

| Declaration | Proposed schema treatment |
| --- | --- |
| Fixed Choice keys with templated descriptions | Describe the fixed choice enum and probability fields |
| Fixed Noul question | Derive the Noul answer shape |
| Fixed Score level count with templated descriptions | Describe answer fields and state the level range in a description |
| Entire Choice criteria map supplied by input | Catalog describes a broader shape; validate the resolved request's criteria map before sending |
| Entire Score criteria array supplied by input | Catalog describes a broader shape; validate the resolved request's criteria array before sending |
| Entire question map supplied by input | Out of scope; no generic evaluator or extension hook |

Include dynamic criteria in the initial scope. Do not advertise invocation-dependent
enums or level ranges as fixed catalog guarantees. The Phase 0 decisions below
specify schema ownership, validation, and binding behavior.

Choice and Score confidence describes the answer distribution, not guaranteed
correctness. Noul expresses probability of yes and has no separate confidence.
Preserve these distinctions rather than inventing a common confidence field.

## Phase 0 Validate the authoring design

Current phase. Work through examples and record the exact resolved request,
catalog output schema, request validation, and response extraction behavior.

The companion [TypeSafe primitive authoring examples](typesafe-primitives-examples.md)
captures the examples discussed so far, expected substitution behavior, and
judgment-design suggestions. These are design walkthroughs, not executed tests.
They use `${/input/...}` and cover dynamic criteria, exact resolution, schema
construction, and failure cases. The roadmap owns the contract; examples illustrate it.

| Example | Decision or behavior to exercise |
| --- | --- |
| Two Noul questions over templated text | Basic authoring, batch shape, scalar interpolation |
| Whole structured state with nested arrays | JSON type preservation and path rules |
| Choice with fixed candidates and variable descriptions | Stable output enum despite changing wording |
| Choice with runtime candidate map | Dynamic criteria and resolved-request validation |
| Score with fixed descriptive levels | Score range, distribution, and legend |
| Score with runtime level array | Dynamic level count and catalog precision |
| Mixed Choice, Noul, and Score batch | Independent questions and heterogeneous answer schemas |
| Quotes, newlines, Unicode, and literal template markers | Data fidelity, escaping, and single-pass resolution |
| Missing path, explicit null, wrong type, empty criteria | Registration versus invocation errors |
| Direct invocation and parent-bound input | Same business contract at both entry points |
| Unreadable response or missing/non-object `answers` | Basic response handling and diagnostic fidelity |

### Review findings and repository constraints

The direction is sound, but this is a new execution path, not a small chat-provider
adapter. These findings change the phase estimates:

- `YamlSkillManifest.InputSchemaManifest` does not support `minimum`, `minItems`,
  or `maxItems`. Remove those keywords from these examples. Keep the existing
  author-facing schema subset; enforce primitive cardinalities in resolved-request
  validation. Adding general JSON Schema authoring is outside this feature.
- `ProviderConnectionRuntime` requires a `ChatModel`, and
  `NamedAiConnectionRegistry` constructs and destroys chat integrations. Refactor
  that internal boundary so the existing lifecycle owns both current chat resources
  and TypeSafe evaluation resources. Keep one registry and one creation/shutdown
  ownership model, with the concrete execution paths each needs. Do not build a
  separate TypeSafe lifecycle, a generic capability/plugin system, a dummy chat
  model, or a supported replacement-bean SPI. Replace affected internal signatures
  and callers directly; the exact class structure is an implementation choice.
- `OutputSchemaValidator.propertyNamesMatch` is case-insensitive. TypeSafe results
  bypass this validator and its output-repair advisor entirely. Preserve provider
  keys verbatim. Source value lookup is exact, but destination schema lookup,
  output projection, and composition include case-insensitive matching. Reject
  case-colliding question IDs and Choice candidate keys on the declaration/request
  side, using the existing schema-name comparison rule. No response identity
  validator or change to shared binding semantics is needed.
- `DefaultSkillCatalog` exports the tool's schema. Derive it once from the registered
  question declaration and use it for both surfaces. The existing schema vocabulary
  is sufficient for descriptive answer shapes; numeric bounds and typed dynamic
  maps do not justify extending framework schema representations or consumers.
- `ChildInputBindingDeclarations` checks parent-input paths and producer topology;
  it does not establish complete producer-output-to-receiver schema compatibility.
  Separately, `SkillGenerationManager.validateOutputBindings` checks a parent's
  `output_bindings` at registration using available source/destination schemas.
  `checkBindingCompatibility` checks types, nullability, enum intersection, and
  known nested schemas. `OutputBindingComposition` also validates actual selected
  values at runtime. Reuse these existing checks with the generated TypeSafe schema;
  no stronger static proof system, binding syntax change, or new checker is needed.

### Phase 0 decisions

These decisions incorporate the accepted fresh-context review corrections and
subsequent transport/accounting agreements. Acceptance of these corrections does
not authorize TypeSafe tickets or implementation.

**Declaration and identity.** Presence of an object-valued `typesafe` block selects
the execution path; `null` or a scalar is an authoring error. Require `name`,
`description`, `model`, and an object-root `input_schema`; allow `rbac_roles`.
Inputs must be JSON-compatible; reject `attachment` schema nodes initially rather
than implicitly converting resources into text or adding multimodal transport.
The only fields inside `typesafe` are `state` and nonempty `questions`; question
objects contain `type`, optional `instructions`, and primitive-appropriate `criteria`,
with requiredness and accepted values following TypeSafe's contracts below.
Use existing strict manifest parsing for unknown fields and duplicate keys;
do not add a separate YAML parser or repository-wide parsing change for TypeSafe.
Reject declared `rest`, `prompt`, `thinking_level`, `planning_mode`, `concurrency`,
`max_steps`, `allowed_skills`, `linter`, `output_schema`, `output_schema_max_retries`,
`output_from`, and `output_bindings`, including false, null, or empty declarations.
TypeSafe skills are leaves; a normal parent can invoke, bind, or forward their results.
Rejecting `output_from` on the leaf does not prohibit a parent from selecting it.
Do not inherit default thinking settings or run planning, linting, or output repair.

**Name collisions.** Continue Loomspan's authored-schema rule for sibling names:
this is an explicitly accepted Loomspan compatibility restriction, not a claimed
TypeSafe requirement. Retain it to keep the integration consistent with Loomspan's
existing schema and binding behavior; no shared case-matching redesign is required.
question IDs must be unique after `toLowerCase(Locale.ROOT)`, as must candidate
keys within each Choice question. Reject `Billing` alongside `billing`; either
spelling alone is valid and is preserved. Check question IDs and fixed Choice keys
at registration, before deriving their named schema properties. For a Choice map
supplied by input, apply the same rule to the resolved map before HTTP. This keeps
fixed and dynamic criteria consistent without redesigning schema consumers.

Report the two conflicting names and their declaration/request location. Do not
lowercase, merge, or rename keys in the actual request, result, or diagnostics.
The rule is scoped to question IDs and Choice candidate keys, not arbitrary state,
structured descriptions, or provider responses. Names in different question maps
do not collide. Exact duplicate YAML keys remain subject to existing parsing rules.

Add `SkillKind.TYPESAFE` deliberately, so both public catalog and Console expose
the execution kind. Existing kinds still describe supported execution paths;
`SkillDescriptor` needs no additional fields for this requirement. Update affected
switches, decoders, supported-surface tests, and README together. Do not add aliases
or legacy decoding to accommodate older consumers. This is the only currently
needed public Java surface change; no typed facade, driver SPI, or public provider
envelope. If implementation exposes another necessary change, assess it against
the current requirements rather than preserving a signature through a shim.

**Console source metadata.** Console's `source` is a separate wire field with its
own validators and MCP output schemas; adding `SkillKind.TYPESAFE` alone does not
make those consumers accept it. Follow the existing REST manifest precedent:
publish `source: TYPESAFE`, a nonblank `sourcePath`, the original YAML in detail
responses, and the generated `outputSchema`. Do not populate Java `beanName` or
`method` fields. Summary responses retain the existing summary shape without YAML.
No additional source-kind field, metadata API, or alternate wire format is needed.

Reuse `YamlSkillSource` and `SkillSourcePathResolver` unchanged for locations:
discovered resources use the current configured-root/filename rules; an application-
supplied `SkillDocument` reports its `sourceName` verbatim as `sourcePath`, as the
resolver already does for `suppliedLabel`. It is a descriptive label, not necessarily
a filesystem path. Do not invent a synthetic URI, resolve it against the filesystem,
or normalize it into a path. Detail YAML is the captured original document content,
not a reserialized manifest or the invocation's resolved TypeSafe request.

**Template grammar.** Parse YAML into JSON-compatible values first. Walk string
values only within `state`, `instructions`, and `criteria`; never template keys,
question IDs, primitive types, model aliases, schemas, or connection settings.
All object keys are literal, including keys containing template-like text. They
are never evaluated or rewritten. Primitive types remain literal enum values.

- An expression is `${` plus `/input` or `/input/` followed by JSON Pointer tokens,
  then `}`. No whitespace trimming, default expressions, functions, wildcards,
  fragments, URI decoding, or access to configuration/environment/other answers.
- Decode `~1` as `/` and `~0` as `~`, once, in that order. Other `~` escapes fail.
  Empty object-key tokens are valid. On arrays only `0` or `[1-9][0-9]*` is an
  index; reject `-`, signs, leading zeros, and out-of-range indices. Numeric object
  keys are ordinary keys. Traversing a scalar or null fails.
- The first `}` closes a token; nested `${` inside a token is invalid. Keys
  containing `}` cannot be addressed directly in this first syntax; supply their
  containing object as a whole value. This is a deliberate grammar limitation.
- `$${` emits literal `${` and consumes that opener without interpreting the
  following text. Other dollar signs and backslashes are literal. An unescaped
  `${` must form a valid input expression; malformed or unterminated forms fail
  registration. Escaped literal text is never reparsed.
- Exactly one expression and no surrounding characters replaces the entire node
  with a deep copy of the selected JSON value. Even surrounding whitespace makes
  it interpolation. Strings are copied verbatim; booleans become `true`/`false`;
  numbers use the existing JSON codec's locale-independent representation, without
  custom decimal normalization. Whole values retain JSON type and numeric value;
  exponent spelling, insignificant zeros, and object ordering are not promised.
- Embedded null, object, or array values fail. Whole-value null survives resolution,
  then must satisfy its destination's request contract. Missing values always fail;
  optional input does not imply fallback, omission, or empty text. No schema defaults
  are inserted. A caller must supply every referenced optional value when invoking.
- Values inserted from input are never scanned for templates. Preserve Unicode,
  newlines, and YAML's parsed scalar content; no trimming after YAML parsing.

Skill documents are read as bytes by `YamlSkillCatalog`; the current path does not
apply Spring placeholder expansion. Keep that boundary. Spring `${ENV_VAR}`
configuration placeholders belong only to application configuration. A `${ENV_VAR}`
inside a templated TypeSafe field is an authoring error unless escaped as literal
text. Application-supplied `SkillDocument` content has the same rules.

**Resolved requests and timing.** Validate declaration shape, template syntax,
static question structure, and literal request constraints at registration. Do not
analyze input schemas to prove path existence, expression types, or bounds.
Use the JSON library's JSON Pointer support for traversal and escaping rather
than building another path engine; the template scanner only identifies references
and literal escapes. Validate ordinary skill input first at invocation, then resolve
once against the validated, normalized input, validate the entire request, and only
then consume a provider attempt. Allow
references through open objects and optional properties; check their presence at
invocation. Capture one immutable resolved request for all retries.

Retain existing input behavior: ordinary inputs may be coerced, while author-bound
subtrees are validated exactly. Identical accepted, normalized inputs produce the
same resolved request through either entry point; identical raw values need not
be accepted through both. Do not change shared binding semantics to erase that
distinction. Ensure input values are JSON-compatible before serialization, including
finite numbers: ordinary `SkillInputValidator` number validation can accept
non-finite values. This is a request-side check, not answer validation. Application
input normalization is separate from provider request validation: do not add another
coercion or rewriting pass over resolved TypeSafe request fields.

Validate against TypeSafe's supported request shapes, with only the accepted
name-collision exception above. `state` accepts strings, objects, or arrays;
nested JSON values may include null or numbers. Instructions may be omitted or
be strings, objects, arrays, or null, subject to primitive-specific requirements.
Preserve omission and explicit null; do not supply replacement text or descriptions.

Choice criteria is a map with 1–255 entries, whose descriptions are strings,
objects, arrays, or null. Do not introduce a two-candidate minimum or manufacture
a fallback candidate. Score criteria is an ordered, nonempty array with at most
10 levels. Each level may be a string, object, array, or null. Accept singleton
arrays and preserve every entry and its order, including nulls; removing entries
would change the level indices. Do not synthesize answers or fallback levels.

Noul criteria may be omitted, null, or an object whose only permitted keys are
`true` and `false`, each independently optional. Descriptions may be strings,
objects, arrays, or null. Do not require both keys or fill in a missing side.
TypeSafe's Python reference requires criteria describing an outcome when Noul
instructions are absent/null; allowing optional keys is not permission to send a
question with neither instructions nor an outcome description. Follow that
primitive-specific requirement rather than a universal instructions requirement.
The official [NoulQuestion interface](https://docs.typesafe.ai/sdk/javascript/api/interfaces/NoulQuestion)
documents optional keys and nullable criteria/instructions; the
[Python question reference](https://docs.typesafe.ai/sdk/python/api/types/questions)
explains their relationship. Choice and Score also document optional instructions
in their official SDK references.

Do not trim or rewrite keys or descriptions. Criteria may be supplied by one
whole-value expression, including Noul criteria. Fixed question IDs/types remain
the registered-skill authoring boundary described above. Template escaping only
disambiguates authoring syntax; it must preserve the intended resolved content.

The official references disagree on some Score criteria forms. The
[HTTP reference](https://docs.typesafe.ai/api) says
Score should have at least two levels, while the Python SDK schema has `minItems: 1`.
The [structured-question guide](https://docs.typesafe.ai/primitives/advanced) permits
null Score levels, while the HTTP table and Python Score schema omit that form.
The agreed local rule is the nonempty, at-most-ten array described above, including
singleton arrays and null levels. TypeSafe remains authoritative for acceptance
on the wire; propagate provider errors through normal failure handling. Local
acceptance does not guarantee server acceptance. Targeted live verification may
provide useful evidence but is not a prerequisite to ticket creation and does not
justify speculative local restrictions. An exhaustive provider-conformance suite
is not required.

**Descriptive catalog schema.** Derive the schema from the registered questions;
`output_schema` is rejected even when it duplicates that schema. Use the existing
`type`, `properties`, `required`, `enum`, `description`, and boolean
`additionalProperties` vocabulary. No new schema AST, generic compiler, schema
inference system, or richer schema consumer is needed. An internal question
representation may serve request checks and schema derivation without becoming a
framework-wide contract abstraction.
Every object below is `type: object`; named answer fields are required.

| Node | Generated catalog contract |
| --- | --- |
| Root | One property per declared question, all required, `additionalProperties: false` |
| Every answer | Fixed string `type` via a singleton enum; `additionalProperties: true` to retain provider extensions |
| Noul | Required `type`, `noul`; `noul` is number with description `Probability of yes, from 0 to 1.` |
| Choice | Required `type`, `choice`, `confidence`, `probabilities`; choice is string and confidence is number |
| Fixed Choice | Choice enum is the declared keys; probabilities has those required numeric properties and `additionalProperties: false` |
| Dynamic Choice | No choice enum; probabilities is an open object with a description of its candidate-to-probability mapping |
| Score | Required `type`, `score`, `confidence`, `probabilities`, `legend`; score and confidence are numbers |
| Fixed Score of N levels | Score description states range 0 through N-1; probabilities has required numeric properties `0` through `N-1` and `additionalProperties: false` |
| Dynamic Score | Score description explains range 0 through supplied level count minus one; probabilities is an open object |
| Every Score legend | Open object described as provider-returned level descriptions; do not infer or constrain the value representation |
| Numeric descriptions | Describe confidence/probability ranges as 0 to 1; these are documentation, not new bound keywords or checks |

Do not infer narrower dynamic shapes from input-schema
descriptions or examples. Resolve criteria locally for each request; do not generate
invocation-specific output schemas or validate returned answers against them.
Never mutate the registered generation or cache resolved criteria solely by skill name.

Legend deliberately has broad value types: the HTTP table describes strings but
structured level inputs are supported, and no verified serialization contract for
their returned descriptions is established here. Preserve every returned value;
do not check level keys, demand equality with input criteria, or fabricate a legend.
The open object is sufficient for this feature; no legend-specific investigation
or more precise schema is required to deliver it.

These schemas describe the expected provider result for callers and parent skills;
they are not a TypeSafe response-validation gate or a guarantee of judgment correctness.
They do participate in the existing parent output-binding registration checks.
Return the entire `answers` object, including any additional answer names or fields.
Do not add answer-schema checks through a shared tool or skill-result validator.

**Bindings.** `/department/choice` and `/bug_severity/score` are stable source paths.
Dynamic probability keys are not guaranteed to exist; whole probability maps can
be copied, and an explicit key lookup may fail at runtime. Keep the existing
exact source-value lookup and existing destination validation/canonicalization;
do not describe all binding operations as case-sensitive. Declaration/request name
checks prevent case collisions in the generated answer properties without adding
output validation. A dynamic Choice bound to a
receiver enum can fail the receiver contract; do not widen the enum, invent a
fallback, or silently omit a value. No new static subtype checker or dynamic
selector syntax is required. Template array traversal does not expand the existing
object-field-only binding language. Parent output projection may select answer
fields, but the TypeSafe leaf's own result always contains complete answers.

**Parent forwarding.** A normal parent may select a TypeSafe child through
`output_from: {skill: <child-name>}`, subject to existing forwarding constraints:
the parent declares `planning_mode: true`, and the selected direct allowed child
has `required: true` and `max_tasks: 1`. Retain the existing exclusions of parent
`output_schema`, `output_schema_max_retries`, `linter`, and `output_bindings` when
using `output_from`, and the requirement that all accepted work completes.

The parent inherits the child's descriptive effective output schema and returns
the retained child's complete named-answers JSON text unchanged. Forwarding adds
no `answers` wrapper, provider metadata, final synthesis, repair, or answer
validation. Empty `{}` and provider-added answer names or fields pass through as
they do on the leaf; the inherited schema is descriptive, not a forwarding gate.
Child failures propagate through existing behavior. Reuse schema inheritance in
`SkillGenerationManager.resolveOutputSchema`, retained-result forwarding in
`StepLoopMissionExecutionEngine`, and the existing `RESULT_FORWARDED` trace event.
No TypeSafe-specific forwarding mechanism is needed.

Keep the distinction between the two existing binding paths explicit:

| Binding path | Registration and runtime behavior |
| --- | --- |
| Parent `output_bindings` | Use the generated TypeSafe output schema in the existing effective-schema map. Reject known incompatible types, nullable-to-nonnullable sources, and disjoint source/destination enums at registration. Keep existing nested-schema checks and actual-value destination validation at runtime. |
| Child `allowed_skills.input_bindings` | Keep existing path/topology checks. This path does not perform the same producer-output schema comparison; validate the assembled receiver input at invocation. |

For parent output bindings, a fixed Choice enum `[billing, technical]` bound to
`[sales]` fails registration. Binding it to `[billing]` can register because the
enums overlap, but an actual `technical` value fails destination validation at
runtime. Enum overlap is not proof that every producer value is acceptable.
A Score's `number` bound to `integer` fails registration; the existing widening
exception allows `integer` to `number`. The answer's singleton `type` enum also
participates when selected by a binding. A dynamic Choice has no source enum, so
its actual selected value remains a runtime check against a constrained destination.
Unknown/open source branches retain existing behavior; do not infer unavailable
schemas or bypass known-schema checks merely because TypeSafe answers pass through.

**Response handling and failure.** After HTTP success, decode the response using
the normal JSON decoder into a JSON object/tree and require a top-level `answers`
object. Do not introduce typed per-primitive response DTOs that revalidate or drop
provider fields. An unreadable
body or missing, null, or non-object `answers` fails the invocation. This is the
minimal extraction check needed to return the promised result, not an answer
validation subsystem. An empty object passes; do not introduce a separate
non-emptiness or expected-question-count rule.

Return that object's contents as JSON text, preserving all names, fields, and
values. Do not check individual answer presence/types, candidate membership,
numeric ranges, probability sums, selected choices, score consistency, or legends.
Do not require model or usage metadata as a condition of business-result success.
Read available usage through existing accounting conventions; missing or unusable
usage is recorded with `UsagePrecision.UNAVAILABLE`, not as exact zero or a text
estimate. Preserve the full envelope in diagnostics.
No special parsing rules, output-schema validation, coercion, mathematical checks,
or repairs are added for TypeSafe responses. Ordinary JSON serialization may change
whitespace or object ordering, not the returned data.

Response extraction failures are terminal with no output-repair or provider retry.
Preserve the failed response in diagnostics; do not manufacture an empty result.
Map transport failures into existing provider failure categories and reuse the
bounded retry policy and backoff calculation. Loomspan owns the TypeSafe retry
loop: each iteration reserves and traces an attempt and makes at most one HTTP
send. Reuse `ProviderRetryDecider` and the existing budget and trace components.
`ProviderAttemptCallAdvisor` owns the current Spring AI-specific loop and cannot
execute System One calls unchanged. Keep the TypeSafe integration internal and
narrow; do not redesign every provider. The current chat HTTP classifier does
not treat 529 as transient: its retryable set is 408/429/500/502/503/504, and 529
currently becomes `PERMANENT` / `SERVER_ERROR`. The System One path must explicitly
translate 529 to `TRANSIENT` / `PROVIDER_OVERLOADED`. Those classification/category
values already exist; the TypeSafe status mapping is new. Do not add
a separate retry engine, policy knobs, or provider error taxonomy. Cancellation
and mission expiry stop retries. Preserve provider bodies, status, and retry evidence.

**Accounting and observation.** One evaluation batch is one logical evaluation and
one skill invocation, regardless of question count. Each HTTP attempt consumes one
provider attempt; nested dispatch retains existing tool-invocation accounting.
Question count is diagnostic metadata, not a new quota unit. Account a received
successful HTTP response's available usage once, including when answers extraction
fails, while the execution remains writable; do not multiply by question count.
Missing usage remains visibly unknown under existing accounting
policy. Cost remains optional provider metadata, not a substitute for token units.
Retries can incur additional provider charges; never claim exactly-once inference.

Keep the logical evaluation distinct from existing accounting counters:
`SessionUsageSnapshot.recordModelUsage` increments `modelCalls` for every recorded
response, and `DefaultSessionUsageService` enforces the corresponding quota. Do not
promise exactly one counted model call if multiple attempts contribute usage.
Retain these semantics and follow chat's existing accounting boundaries. HTTP
errors record failed attempts and bounded provider diagnostics; do not add
TypeSafe-specific extraction of usage from HTTP error bodies into model usage
or quota counters. Successful HTTP responses use the usage mapping below,
including when subsequent answers extraction fails, while execution is writable.
Late responses use the existing execution write gate; do not bypass the cutoff
or introduce background accounting. Framework totals can therefore omit provider
charges for failed or cancelled requests and are not a complete billing ledger.

Reuse the existing session usage service, attempt reservations, response-based
model-call counters, quota enforcement, metrics, and lifecycle write gates. Keep
TypeSafe-specific work to mapping its native usage fields into the existing
accounting record and integrating calls at the corresponding execution boundaries.
Do not add a usage service, quota model, or synthetic chat response to obtain reuse.

The extraction boundary needs a small System One mapping: `ModelUsageExtractor`
accepts Spring AI `ChatResponse` and falls back to text estimates. Do not route
TypeSafe through it or construct a synthetic chat response. The shared accounting
layer already accepts `ModelUsageRecord` and supports `UsagePrecision.UNAVAILABLE`;
reuse those types and services directly.

| System One usage | Existing accounting representation |
| --- | --- |
| Usable `usage.input_tokens` and `usage.output_tokens` | Map to `promptUnits` and `completionUnits`; their sum is `totalUnits`; precision is `EXACT` |
| Both counts explicitly zero | Exact zero usage, still `EXACT`; do not use the chat extractor's positive-count test |
| Missing, partial, or unusable counts | `ModelUsageRecord(0, 0, 0, UNAVAILABLE, nativeUsage)` using the existing unavailable convention; preserve any reported fields in native usage and canonical diagnostics |

Usable counts are nonnegative integers representable by the existing accounting
record, including their sum. Reading these counters safely is metadata extraction,
not a new provider-response validator: unusable usage does not invalidate otherwise
usable answers. The zero counters on an `UNAVAILABLE` record are existing storage
placeholders, not a claim that the provider used zero tokens. Preserve the precision
flag through existing metrics and observation; do not invent missing token counts
from serialized state, questions, answers, or cost. TypeSafe never emits `HEURISTIC`.
Unavailable usage contributes no token estimate to the usage-unit quota; existing
call/attempt limits still apply. No new quota policy or usage model is required.

Record original declaration/generation, resolved request, route, requested and served
model IDs, attempts, full response or failure body, and acceptance/rejection in
canonical traces, subject to existing explicit capture/size controls. Trace-disabled
or truncated capture must be reported honestly, not called a full retained response.
Reuse existing captured declaration/generation references rather than introducing
a second provenance store. Connection identity and endpoint identify the route;
no new route classification system is required.
Reuse Console's authenticated trace detail view, but adapt its model-detail parser
and rendering: `TraceRecords.tsx` currently requires chat request `messages` and
response string `content`. Define a native evaluation payload representation that
retains the System One request and full provider envelope, plus raw text for
malformed response bodies. Render these as ordinary JSON/text in the existing view
without manufacturing chat messages. Deliver the representation and affected
consumers together when traces are first emitted, not as later Phase 4 work. No new
evaluation dashboard, probability chart, or specialized summary is required. Extend
internal trace fields only where current fields cannot faithfully represent the
evaluation. Update affected Java/Go/TypeScript consumers and fixtures together.
Keep `SkillTemplate` observation on its
existing derived journal; do not add arbitrary provider envelopes or a public
observer API. Retain the existing projector exception only in that derived journal.

Exit when the examples have consistent expected behavior and the user agrees the
contract is ready to become implementation work. Until then, revise this roadmap
rather than maintaining competing specifications in tickets.

## Connection options

Both routes are required. A skill's state, questions, and input contract should
remain unchanged when its configured model alias switches connection routes.
Credentials belong to the selected connection, not to skill inputs or templates.
The initial authentication design uses account-issued API keys rather than an
interactive account login.

Current documented endpoints, checked 2026-10-09:

| Route | Evaluation endpoint | Credential |
| --- | --- | --- |
| Direct TypeSafe | `https://api.typesafe.ai/v1/systemone` | TypeSafe API key |
| OpenRouter System One | `https://openrouter.ai/api/v1/systemone` | OpenRouter API key |

The [TypeSafe API](https://docs.typesafe.ai/api) documents direct Bearer-key access.
The [OpenRouter System One guide](https://openrouter.ai/docs/guides/community/typesafe-sdk)
documents compatible request and answer shapes, including the three primitives.
It accepts bare TypeSafe model IDs and namespaced IDs, returns OpenRouter model
identifiers, and adds `id`, `provider`, and `usage.cost`. Its model-listing response
differs from TypeSafe's. OpenRouter also offers a separate Decisions endpoint.

Prefer the compatible System One endpoint for the initial OpenRouter integration.
The design should share evaluation transport where practical, while keeping route
configuration and provider differences explicit. Do not send these requests through
the existing chat-completions path.

Use one `typesafe` connection driver and one System One HTTP implementation.
Both account routes use this protocol; `base-url` and `api-key` select the service.
Reuse the existing connection configuration fields, including `request-timeout`,
`headers`, and `provider-retry`, while extending their driver-specific validation
for `TYPESAFE`. Field reuse does not mean validation can remain unchanged. No second
driver, route enum, profile selector, or route-specific metadata adapter is needed;
preserve the envelope and read the shared usage fields.
Base URL means service prefix without `/v1/systemone`, defaulting to
`https://api.typesafe.ai`. OpenRouter uses explicit `https://openrouter.ai/api`.
Use normal URI handling to append the path segments `v1/systemone` to the service
prefix, preserving `/api` for OpenRouter. Require an absolute HTTP(S) service URL
without query or fragment; do not repair malformed URLs or discover endpoints.
In `LoomspanProperties`, `validateHeaders` currently permits nonempty headers only
for OPENAI and ANTHROPIC. Extend that allowlist to TYPESAFE and retain the shared
header-name and non-null-value checks. Add TYPESAFE to `validateRequiredFields`
with a required `api-key`, and implement the base-URL default and validation above.
The current `validateApplicableOptions` base-URL check only prohibits the setting
for Gemini; it is not an exhaustive URL switch needing another case. Its existing
checks already reject `openai` and `gemini` option blocks on other drivers; retain
that behavior for TYPESAFE. Preserve validation behavior for existing drivers.
Current validation does not establish credential-header precedence. Reject configured
`Authorization` headers case-insensitively so the selected API key remains the sole
source of the generated Bearer header. No custom proxy configuration feature is needed.

```yaml
loomspan:
  connections:
    typesafeDirect:
      driver: typesafe
      api-key: ${TYPESAFE_API_KEY}
    typesafeRouter:
      driver: typesafe
      base-url: https://openrouter.ai/api
      api-key: ${OPENROUTER_API_KEY}
  models:
    typesafeDefault:
      connection: typesafeDirect
      provider-model: jev-latest
```

These are proposed configuration keys/values, not working configuration today.
Switch the alias's connection to `typesafeRouter` to change route. Send
`provider-model` verbatim; route-specific IDs may require updating it too. Reject
chat options and thinking-level declarations on System One models/connections,
and reject a chat skill selecting such an alias or a TypeSafe skill selecting a
chat alias. Do not add model discovery or automatic model/route fallback initially.
Connection configuration follows the existing application lifecycle; skill reload
does not imply hot-reloading credentials or connection resources.

Provider metadata remains outside the skill result and its catalog output schema;
preserve it in canonical diagnostics. Do not require OpenRouter-only fields
on direct TypeSafe responses or assume identical model identifiers, usage details,
errors, or discovery APIs. Switching routes must not silently rewrite question
content or substitute another primitive implementation.

## Agreed HTTP client and retry ownership

Use Apache HttpClient 5 directly for the TypeSafe HTTP transport, with automatic
retries and redirects disabled. Declare the HTTP-client dependency explicitly.
Use the same transport implementation for direct TypeSafe and OpenRouter routes.
Loomspan owns retries as specified above, so client recovery cannot silently add
HTTP sends outside the attempt budget and trace records.

Reuse one client per configured connection within the existing connection registry
and close it during normal cleanup and construction-failure cleanup. Extend the
current chat-model-only lifecycle internally; do not add a transport SPI or a
parallel connection subsystem. Apply the timeout/cancellation and response-size
rules below.

## Agreed timeout and cancellation behavior

Reuse the existing chat mission lifecycle: mission timeout, caller interruption,
framework shutdown, bounded cleanup, and the existing cutoff for late writes.
Make the TypeSafe transport respond to framework cancellation; do not assume
interrupting a Java future alone proves the HTTP request has stopped. Do not add
a public cancellation API, another execution engine, or a cancellation subsystem.
Local cancellation cannot guarantee that the provider stops processing or billing.

As with chat clients, map the existing `request-timeout` field to the chosen
client's transport timeout behavior and document the exact Apache settings used,
their scope, and their defaults when the field is omitted. Do not describe a
response/read timeout as a total elapsed-time deadline, add a separate per-attempt
deadline mechanism, or introduce TypeSafe-specific timeout configuration. The
existing mission timeout remains the framework's overall execution control.
Transport timeout failures use existing failure/retry handling while the mission
is active; mission cancellation stops new attempts and interrupts retry backoff.

A late response cannot restore a cancelled invocation to success or bypass the
existing write cutoff. Usage treatment remains the separate accounting decision.
Verify configured transport timeouts, cancellation during HTTP and retry backoff,
and late-write rejection with focused fixtures. These checks must demonstrate the
actual client's behavior, not only interruption of a mock chat client.

## Agreed response-size and diagnostic capture behavior

Loomspan imposes no additional response-body byte ceiling for TypeSafe. Direct
TypeSafe and OpenRouter use the same response-acceptance behavior. Preserve the
complete returned `answers` object; do not add response-limit configuration.
Provider/model limits, normal parser constraints, available memory, and the agreed
timeout/cancellation behavior still apply. This is not a promise of unlimited
responses or a reason to disable existing parser constraints.

Provider-error diagnostic capture remains bounded at 1 MiB, with explicit
`truncated` and `captureLimitBytes` metadata. That capture limit must never reject
or truncate an otherwise valid result. Keep diagnostic capture separate from
response acceptance and minimal `answers` extraction.

Verify a valid response larger than 1 MiB through both TypeSafe routes, preserving
the full answers, and verify bounded error evidence with truthful truncation
metadata. The existing OpenRouter chat restriction must not carry into System One.
[PR 26](../tickets/loomspan-pr-26-separate-openrouter-response-acceptance-from-diagnostics.md)
separately addresses that chat inconsistency; this contract does not depend on
implementing that ticket first.

## Decision status before implementation

The reviewed Score, HTTP client/retry ownership, timeout/cancellation, response-size,
and accounting questions are settled by the rules above. Concrete Apache timeout
settings and their defaults must still be documented and verified during implementation
within the agreed transport semantics; no new total-deadline mechanism is authorized.
TypeSafe ticket creation and implementation still require separate authorization.
The accepted response handling remains request validation followed by minimal
`answers` extraction and passthrough; these decisions do not reopen answer validation.

## Phase 1 Declarations and template contracts

Implement manifest parsing, question definitions, input references, structured
substitution, request validation, and output-schema derivation. Validate what is
knowable from literal declarations at registration; resolve input references and
check input-dependent request constraints at invocation. No static input-schema analysis.

Include descriptive catalog schema derivation and resolved-request validation.
The generated output schema is descriptive; no response validator or invocation-
specific output schema is needed. Do not add general-purpose YAML schema keywords to make the
examples parse. Check generation-local schemas and existing binding consumers here.

Exit with the Phase 0 examples covered by focused contract tests, including errors
and dynamic criteria. No provider credentials are needed for this phase.

## Phase 2 TypeSafe provider execution

Implement direct TypeSafe and OpenRouter connection and model configuration,
HTTP execution, minimal `answers` extraction, and provider failure translation. Integrate
deadlines, cancellation, retries, attempt accounting, and usage with existing
runtime controls using the accounting decisions above. Refactor the chat-only
assumptions in the existing connection lifecycle and configuration validation first;
retain one framework-managed lifecycle with direct internal changes and no bridge
for the replaced chat-only representation. Trace attempts and preserve
raw request/response bodies in this phase; no hidden SDK retries.

Extend the existing connection validation as specified in Connection options; do
not introduce a parallel configuration subsystem. Add focused configuration tests
for both route examples, permitted custom headers, a missing API key, incompatible
driver-specific option blocks, and the base-URL and Authorization-header rules.
Verify the direct default endpoint and preservation of OpenRouter's `/api` prefix.
Use the agreed Apache client, Loomspan-owned retry loop, and existing mission
cancellation behavior described above. Document and test the concrete transport
timeout mapping without promising a new per-attempt total deadline.

`SpringAiProviderIntegration.httpFailure` is currently private to the chat
integration; do not assume the System One client can already reuse it directly.
Use the existing `ProviderFailureTranslator`/`ProviderFailureDetails` boundary for
System One failures, including the explicit 529 mapping above. If sharing HTTP
classification/Retry-After parsing warrants extracting a small internal helper,
keep existing chat mappings unchanged and apply the 529 override in the System One
translator. Extraction is an implementation option, not a required new subsystem.
Changing chat-provider retry behavior is not necessary for this feature and must
not happen accidentally through a shared status table.

Include a System One 529-then-success fixture proving that the selected retry-loop
integration applies the existing bounded policy when enabled, and a terminal error case
proving no retry. Retain coverage of attempt limits and cancellation. If common
classification code moves, verify that chat classifications remain unchanged.
Verify that client retries and redirects do not add HTTP sends, each send has an
attempt reservation and trace, and registry cleanup closes the connection client.

Map System One usage directly into the existing accounting record as specified
above, bypassing the chat-only usage extractor. Verify exact input/output counts
and their total, explicitly reported zero counts, missing/partial/unusable usage
as `UNAVAILABLE`, and absence of text estimation even with a nonempty request or
answer. Keep chat usage extraction unchanged; do not generalize it or add a second
session accounting service for this integration.

Preserve original state, questions, answers, and failure details in canonical
diagnostics. An invalid response or failed request must not become a fabricated
low-confidence answer. Recheck the live TypeSafe API contract during implementation.

Exit with controlled HTTP fixtures covering answer passthrough, unreadable responses,
missing/null/non-object `answers`, empty `answers` passthrough,
timeouts, retryable failures, non-retryable failures, and accounting behavior.
Cover all three primitives, mixed batches, and dynamic criteria in shared contract
fixtures. Exercise both route configurations through the shared HTTP client; do
not multiply every primitive/error case by route. Verify endpoint construction,
credential selection, model identifiers,
and optional provider metadata. Live checks on both services should be recorded
separately when credentials are available; fixture results alone do not establish
live service parity. Matching contracts do not promise identical judgments.

## Phase 3 Registered skill execution

Connect the declaration and provider path to direct and nested invocation, input
validation, authorization, catalog schemas, bindings, and captured-generation
consistency. Keep nested execution inside the caller's existing execution session.

Update the concrete dispatch and generation invariants, not only registration:
`ExecutionCoordinator` currently loads declarations only for `YAML_SKILL`, directly
invokes only Java/REST capabilities, and otherwise enters the chat mission engines.
`SkillGeneration` accepts only YAML/REST definitions. Add the TypeSafe leaf to these
boundaries deliberately and run its provider execution inside the existing mission
and captured-session lifecycle. Reuse `MissionWorkExecutor` for mission accounting
and cancellation coordination; no additional execution engine is needed. Apply
the agreed timeout/cancellation rule and verify the HTTP client's integration.

### Binding integration implementation guidance

The selected approach is to supply the generated TypeSafe schema to the existing
registration path, not change the binding compatibility algorithm. Based on the
current code, this is expected to be a small integration change within Phase 3;
that assessment is not a completed implementation or a guarantee about the whole
TypeSafe feature's size.

1. Use the descriptive schema derived in Phase 1. Do not create a second binding-
   specific schema or an invocation-specific output schema.
2. Extend `SkillGenerationManager.resolveOutputSchema` to include TypeSafe
   declarations in the existing effective output-schema map. The public catalog,
   tool metadata, and binding checks must use the same generated schema.
   Existing `output_from` schema resolution must also let a forwarding parent
   inherit that schema without adding response validation.
3. Let `validateOutputBindings` and `checkBindingCompatibility` consume that schema
   through their existing path. Keep their compatibility rules unchanged, including
   enum intersection rather than subset proof, the integer-to-number exception,
   and existing handling of open/unknown branches. Do not special-case TypeSafe
   to bypass these checks or extend them to child input bindings.
4. Retain existing actual-value destination validation during composition and
   receiver-input validation during child invocation. These checks enforce the
   consuming application's contract; they do not validate the TypeSafe response.
5. Add focused integration coverage for the registration/runtime cases specified
   in the Bindings section and companion examples. Confirm schema publication and
   accepted bindings as well as rejection timing. Reuse existing binding tests;
   do not build a second compatibility test framework.

No new schema framework, compatibility checker, response validator, or shared
binding redesign is authorized by this work. If the integration appears to require
one, first revisit how the generated schema enters the existing representation.
Record the concrete obstacle and discuss alternatives before expanding scope;
do not silently add machinery or bypass checks to satisfy the ticket. Carry this
guidance and its exclusions into the Phase 3 ticket when ticket creation is requested.

Runtime tracing is part of this phase and Phase 2, not postponed until Console UI
work. Verify original and resolved declarations can be understood from diagnostics
without treating a TypeSafe evaluation as a text-generation or planning loop.

Expose the public `TYPESAFE` kind and update the internal Console wire
representation and all affected consumers together, with supported-surface checks.
Use the current format only; do not add legacy readers or dual formats. Do not leave consumers
unable to decode a kind or trace emitted by this phase until Phase 4.

Include the separate `source: TYPESAFE` contract in this integration. Update the
Java `DefaultRegisteredSkillCatalog` manifest branch, `RegisteredSkillEntry`
validation, and applicable capability/definition checks; Go observability summary
and detail validation in `service.go`; and the MCP adapter's `skillSourceSchema`
in `output_schemas.go` plus its tool descriptions in `skills.go`. Include affected
TypeScript source unions/decoders and UI tests. Reuse existing non-Java text/UI
rendering where it already handles `sourcePath` and YAML; do not rewrite working
renderers solely to add a new source value.

Verify list and detail through both Console and MCP for a discovered TypeSafe
manifest and an application-supplied `SkillDocument`. Assert `source: TYPESAFE`,
the existing location rule (including an opaque supplied label), unchanged detail
YAML, generated output schema, and absence of Java locations. Update affected
schemas and fixtures together. The MCP adapter is a required consumer of this
change, not later optional UI work.

Exit with end-to-end direct and child invocation coverage, failure propagation,
execution limits, and reload behavior. Confirm effective schemas work with the
existing consumers rather than introducing a second contract authority.
Verify that results contain named answers directly, including single-question
skills, and that bindings use those root question names. Check that full provider
metadata remains in canonical diagnostics and usage accounting without entering
the business result or its derived schema.

Include denied invocation before HTTP, parent-bound dynamic criteria, parent output-
binding registration rejection for disjoint fixed enums, number-to-integer bindings,
and incompatible singleton type enums; runtime rejection for overlapping enums with
an actual disallowed value and for invalid assembled child inputs; missing dynamic
probability key; registration rejection of
case-colliding question IDs and fixed Choice keys, pre-HTTP rejection of
case-colliding dynamic Choice keys, and preservation of accepted key spelling,
mixed-batch passthrough, response extraction failure, overlapping invocations with different
criteria, and reload while a captured-generation invocation is running. Check
deadline/cancellation during retry backoff and late responses after cancellation.
Include one focused parent-forwarding integration case asserting inherited schema,
unchanged child result text (including empty or extended answers), the existing
`RESULT_FORWARDED` event, absence of final synthesis/repair, and child-failure
propagation. Reuse existing forwarding tests and constraints.

## Phase 4 Console and authoring guidance

Expose TypeSafe skill identity and meaningful evaluation details in Console.
Use the existing catalog and trace-detail views. Add only the kind label and
rendering needed for the captured request/response; no new authoring UI or
TypeSafe-specific visualization is part of this phase.
Document configuration, template semantics, primitive results, dynamic-schema
limits, and complete direct and nested examples. Update the versioned authoring
guidance and consumer-facing documentation when the feature is implemented.
Include direct TypeSafe and OpenRouter setup and an example switching the connection
behind a model alias without editing the skill's questions or input contract.

Exit when authors can discover, invoke, and investigate the capability using its
documented contract. Live-provider evaluation may supplement deterministic tests;
model answers should not be assumed identical on every run.

## Repository boundaries

Follow [repository guidance](../../../AGENTS.md). Preserve canonical prompt and
diagnostic content; retain the existing derived-journal exception without extending
it. Use authentication, authorization, and explicit access boundaries.

Keep implementation types internal. `RestSkillHandler` remains the sole supported
Java SPI. Deliberate changes to the closed application-facing API require its
allowlist, documentation, and supported-surface checks. Run
`LoomspanPublicSurfaceArchitectureTest` for production-type changes, alongside
tests appropriate to each implementation phase.

## Ticket conversion

### Phase assessment

Retain four delivery phases as review boundaries, not four equal-sized tickets.
Phase 1 now consists of bounded request substitution, request checks, and descriptive
schemas using the existing vocabulary; it does not include schema infrastructure
or static analysis. Phase 2 adapts the current connection ownership to one new HTTP
protocol and reuses runtime controls. Phase 3 has the highest integration risk:
direct and child execution must share the captured generation, authorization,
quotas, results, and diagnostics. Phase 4 is existing-view integration and guidance.
Estimate work from these concrete changes, not the earlier broader architecture.

Execute 1 → 2 → 3 → 4 for integration acceptance; drafting transport fixtures can
overlap Phase 1 once its contract is stable. Split Phase 2's internal connection
refactor from route transport if it cannot be reviewed coherently, and split Phase 3
by integration responsibilities if necessary. Do not split by primitive: shared
mixed-batch and dynamic-schema behavior needs one contract. UI design can begin
earlier, but Console's ability to read emitted data is a current requirement, not deferred
polish. No ticket count or effort estimate is a commitment yet.

After Phase 0, create one parent feature ticket and provisionally four implementation
tickets only when the user requests ticket creation. Use Phases 1 through 4 as
provisional boundaries. Reassess the split against the final
contract rather than treating four as a requirement. Phase 3 depends on Phases 1
and 2; Phase 4 completes the supported experience.

Tickets should link to one agreed contract and contain their scope, dependencies,
acceptance examples, required checks, and exclusions. Sol 6.1 is the intended coding
agent. Avoid making the implementation agent settle unresolved product semantics.
For each proposed task, identify whether it adds TypeSafe behavior or connects an
existing mechanism. Examples and edge-case tests are not permission to expand a
shared subsystem. Preserve existing static binding checks; exclude stronger schema
proofs, generalized templates, richer schema
support, new retry/accounting policies, and specialized Console UI explicitly.
Use representative direct/child and route cases plus targeted regressions, not an
exhaustive Cartesian product of the walkthroughs. Create no tickets until requested.

## Fresh context review

Start with this roadmap and the companion examples. Review the design and phases
before creating tickets or implementing code. No TypeSafe implementation or live
provider validation has been performed as part of these design walkthroughs.

Preserve the settled decisions: normal skill registration and invocation, familiar
TypeSafe primitives, both connection routes, and named answers returned directly
with complete answer objects. The full provider envelope belongs in canonical
diagnostics. Proposed YAML syntax and remaining details are still open to revision.

The accepted corrections preserve the agreed feature scope. The agreed transport
and accounting sections are authoritative; do not silently replace their behavior
during implementation. User acceptance of review corrections remains separate
from authorization to create TypeSafe tickets or implement the feature.
Live checks, when credentials are available, should exercise the supported request
forms through both routes, including targeted verification where official request
references disagree. They do not need to prove provider mathematics or narrow the
open legend schema. Fixture success is
not a claim of live service verification. If evidence contradicts the
selected contract, revise this roadmap before changing behavior or narrowing scope.
Template support remains scoped to TypeSafe fields, not ordinary model prompts.

Example candidate labels and severity criteria are author data, not framework
defaults or actions. Probability values are illustrations, not expected model outcomes.

## Deferred patterns

Intent routing, confidence-gated routing, composite scoring, verification and
escalation, and hierarchical routing are outside this feature. Do not build hooks,
interfaces, configuration, or generalizations for them. Reassess their designs only
if they become actual requirements. Question batching
supports speculative fan-out at the evaluation level, but the primitives work does
not add conditional child dispatch, automatic side effects, or durable human handoff.

## TypeSafe references

- [HTTP API and primitive contracts](https://docs.typesafe.ai/api)
- [OpenRouter System One compatibility](https://openrouter.ai/docs/guides/community/typesafe-sdk)
- [Jev on OpenRouter](https://openrouter.ai/docs/guides/community/jev)
- [Confidence semantics](https://docs.typesafe.ai/confidence)
- [Pattern overview](https://docs.typesafe.ai/patterns)
- [Speculative fan-out](https://docs.typesafe.ai/patterns/fan-out)
- [Confidence-gated routing](https://docs.typesafe.ai/patterns/confidence-routing)
- [Composite scoring](https://docs.typesafe.ai/patterns/composite-scoring)
- [Intent routing](https://docs.typesafe.ai/patterns/intent-routing)
