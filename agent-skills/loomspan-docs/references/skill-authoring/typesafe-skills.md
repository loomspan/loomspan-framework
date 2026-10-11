---
audience: loomspan-skill-builder
status: development
applies_to: bundled-loomspan-revision
coverage: source-verified
---

# TypeSafe evaluation skills

## Applies when

Use a TypeSafe leaf for independent Choice, Noul, or Score judgments over supplied
state. A leaf can be invoked directly through `SkillTemplate` or exposed to a normal
chat skill as an allowed child. It has fixed question names and types, optional
structured instructions, and fixed or caller-supplied criteria. Application code
owns decisions and side effects based on the returned judgments.

Both direct TypeSafe and OpenRouter accounts use the `typesafe` connection driver
and System One protocol. Changing the account route changes connection/model
configuration, without changing the leaf's questions or business input contract.
Provider availability and judgments can differ across routes and models.

## Complete embedded setup

Use the starter dependency and application setup from the matching
`docs/quickstart-java.md` in a source checkout. In an
installed documentation package, use the application's matching starter setup;
the repository quickstart is not bundled. Put the leaf YAML under a configured
`loomspan.skills.locations` resource location. This `application.yml` fragment
declares both account routes and an ordinary chat model for the parent below:

```yaml
loomspan:
  skills:
    locations: classpath:/skills/*.yaml
  connections:
    typesafeDirect:
      driver: typesafe
      api-key: ${TYPESAFE_API_KEY}
      request-timeout: 30s
    typesafeRouter:
      driver: typesafe
      base-url: https://openrouter.ai/api
      api-key: ${OPENROUTER_API_KEY}
      request-timeout: 30s
    chat:
      driver: openai
      api-key: ${OPENAI_API_KEY}
  models:
    typesafeDefault:
      connection: typesafeDirect
      provider-model: ${TYPESAFE_MODEL_ID}
    chatDefault:
      connection: chat
      provider-model: gpt-4.1
```

Set `TYPESAFE_MODEL_ID` to the exact direct System One model ID supported by your
account. To switch the alias to OpenRouter, replace only its model mapping:

```yaml
loomspan:
  models:
    typesafeDefault:
      connection: typesafeRouter
      provider-model: ${OPENROUTER_TYPESAFE_MODEL_ID}
```

Set `OPENROUTER_TYPESAFE_MODEL_ID` to the route-specific System One ID supported
by that account. Keep both connection declarations above; credentials remain
connection properties. The leaf YAML and invocation input stay unchanged.
Loomspan sends that ID verbatim; it does not discover models, add a namespace,
or try fallback models. A TypeSafe leaf MUST select a `typesafe` connection;
ordinary chat YAML MUST select a chat driver.

The direct default service prefix is `https://api.typesafe.ai`. Loomspan appends
`/v1/systemone`, retaining prefix paths: OpenRouter above becomes
`https://openrouter.ai/api/v1/systemone`. Explicit prefixes MUST be absolute
HTTP(S) URLs without a query or fragment. `api-key` is required and sent as Bearer
authorization. Common static `headers` are supported, except any capitalization
of `Authorization`. Chat provider option blocks and model `thinking-levels` are
rejected. `provider-retry` uses the existing connection policy. For authored
`ExecutionConfiguration`, use `api-key-ref` and `header-refs` as described in
[model connections](model-selection-and-connections.md).

## Complete leaf and direct invocation

Save this as `skills/assessContact.yaml`. It evaluates a mixed batch with
input-supplied Choice candidates and Score levels:

```yaml
name: assessContact
description: Assess the supplied contact against caller-provided criteria.
model: typesafeDefault
input_schema:
  type: object
  properties:
    context: {type: object, additionalProperties: true}
    candidates: {type: object, additionalProperties: true}
    levels: {type: array, items: {type: string}}
  required: [context, candidates, levels]
  additionalProperties: false
typesafe:
  state: "${/input/context}"
  questions:
    department:
      type: choice
      instructions: Select the department that best fits this contact.
      criteria: "${/input/candidates}"
    repeat:
      type: noul
      instructions: Does the contact report a previous support attempt?
    severity:
      type: score
      instructions: Assess how much observed functionality is lost.
      criteria: "${/input/levels}"
```

An application service injects `ai.loomspan.api.SkillTemplate` and invokes:

```java
String answers = skills.invoke("assessContact", Map.of(
        "context", Map.of("message", "Again: checkout failed. Browsing works."),
        "candidates", Map.of("billing", "Charges", "technical", "Checkout failures"),
        "levels", List.of("All functions available", "A critical operation is blocked")));
```

The String is the provider's entire object-valued `answers`, serialized as JSON,
with named answers directly at the root. A binding selects `/department/choice`,
not `/answers/department/choice`. The following is an illustrative provider
fixture, not a predicted or live judgment:

```json
{
  "department": {"type":"choice","choice":"technical","confidence":0.8,
    "probabilities":{"billing":0.1,"technical":0.9}},
  "repeat": {"type":"noul","noul":0.9},
  "severity": {"type":"score","score":0.75,"confidence":0.6,
    "probabilities":{"0":0.25,"1":0.75},
    "legend":{"0":"All functions available","1":"A critical operation is blocked"}}
}
```

## Declaration and request checks

Presence of an object-valued `typesafe` block selects `SkillKind.TYPESAFE`.
`name`, `description`, `model`, and an object `input_schema` are required. Existing
`rbac_roles` are permitted and enforced. Attachment nodes are prohibited.
Remove all of `rest`, `prompt`, `thinking_level`, `planning_mode`, `concurrency`,
`max_steps`, `allowed_skills`, `linter`, `output_schema`,
`output_schema_max_retries`, `output_from`, and `output_bindings` from the leaf;
their presence fails even when null, false, or empty.

The block contains only `state` and a nonempty object `questions`. State is a
string, object, or array. Question IDs are literal object keys; each question
contains only literal `type` and optional `instructions`/`criteria`. Instructions
may be a string, object, array, or null. No inter-question dependency is supported.

| Primitive | Criteria | Provider answer description |
| --- | --- | --- |
| `choice` | Required object of 1–255 candidate keys; descriptions are string, object, array, or null | Selected `choice`, distribution `confidence`, and candidate `probabilities` |
| `noul` | Optional/null object containing only quoted `"true"`/`"false"` keys; empty/partial forms and null descriptions are allowed when instructions are supplied | `noul`, the probability of yes; no separate confidence field |
| `score` | Required ordered array of 1–10 descriptions, each string, object, array, or null | `score`, distribution `confidence`, indexed `probabilities`, and provider `legend` |

Provider probability/confidence values are described on a 0–1 scale. Choice and
Score confidence describes the distribution, not the probability that a judgment
is factually correct, and is not recomputed from the
selected candidate. Score uses zero-based levels, with an expected score range
from 0 through level count minus one; its result need not be an integer. These
describe provider outputs and do not install Loomspan answer checks.

Noul without instructions, or with null instructions, requires criteria describing
at least one outcome. Choice and Score do not require instructions. Fixed criteria
may contain templated descriptions; a whole-value reference can supply all
criteria at invocation. Score singleton/null levels pass local validation and are
sent unchanged; this does not promise provider acceptance of disputed forms.
Provider rejection follows normal failure handling.

Sibling question IDs and keys in one Choice criteria object MUST NOT collide under
locale-independent case-insensitive comparison. `Billing` and `billing` collide,
but names retain their exact spelling. This is a Loomspan authoring constraint,
not sensitivity classification. It does not apply to arbitrary state/description
objects or returned answers.

Knowable literal constraints and expression syntax are checked at registration.
After ordinary input validation/normalization, resolution and complete request
checks run once before any provider reservation or send. Missing paths, invalid
resolved criteria, and non-JSON-compatible input, including non-finite numbers,
fail locally. `SkillTemplate.validate` remains a root input/access pre-check;
it does not execute native substitution or call the provider.

## Scoped substitution

Only string nodes in state, instructions, and criteria support `${/input...}`.
Object keys and question types are never templated. `${/input}` selects the whole
validated input. JSON Pointer escapes `~0` and `~1` select tilde and slash keys.
Canonical array indexes are supported here; `01` is invalid for an array but
remains a literal object key. This native template traversal is separate from
the object-only pointers in declared parent bindings.

A string containing exactly one expression becomes a detached copy of the selected
JSON value, retaining its type and null. Embedded expressions permit only strings,
finite numbers, or Booleans; null/objects/arrays fail rather than being stringified.
Leading/trailing whitespace counts as literal content. `$${` emits literal `${`.
Inserted input is never rescanned, and YAML folding/newlines are retained as parsed.
`${ENV_VAR}`, malformed expressions, and invalid tilde escapes fail registration.
The first `}` closes an expression; nested `${` openers fail registration.
A key containing `}` cannot be addressed directly in an expression: select its
containing object as a whole value instead. Escaped `$${` openers remain literal
even when the following text would otherwise be malformed.
This is scoped path substitution, not configuration expansion or an expression
engine.

## Generated metadata and result fidelity

The captured declaration generates one descriptive output schema for tools,
public catalogs, Console/MCP, bindings, and forwarding parents. Its closed root
requires declared question names, while each answer object permits extensions
and declares a singleton `type` enum. Fixed Choice supplies choice/probability
keys; fixed Score supplies indexed probability keys. Whole-input criteria leave
these maps open and do not create invocation-specific catalog schemas. Numeric
ranges are prose descriptions, and Score legend remains broadly open.

The schema is metadata, not native response validation. Loomspan requires only
readable JSON with an object-valued `answers`. Empty `{}`, absent declared answers,
additional answers/fields, unexpected types, out-of-range values, and provider
extensions pass through. No answer repair, confidence recalculation, coercion,
membership checks, or legend reconstruction occurs. Unreadable JSON or missing,
null, string, or array `answers` fails extraction without automatic provider retry.
The complete provider envelope belongs in diagnostics; only `answers` becomes
the business result.

## Complete parent composition

Save this separately as `skills/assessReportedContact.yaml`, using the leaf and
connections above:

```yaml
name: assessReportedContact
description: Orchestrate a complete contact assessment.
model: chatDefault
planning_mode: true
max_steps: 1
prompt: Have assessContact assess this contact using the supplied criteria.
input_schema:
  type: object
  properties:
    context: {type: object, additionalProperties: true}
    candidates: {type: object, additionalProperties: true}
    levels: {type: array, items: {type: string}}
  required: [context, candidates, levels]
  additionalProperties: false
allowed_skills:
  - name: assessContact
    required: true
    max_tasks: 1
    input_bindings:
      /context: {from: input, path: /context}
      /candidates: {from: input, path: /candidates}
      /levels: {from: input, path: /levels}
output_from: {skill: assessContact}
```

Invoke `assessReportedContact` with the same input as the direct example. Planning
remains a chat interaction. The closed, wholly bound child input permits existing
Framework assigned dispatch. The native child evaluates using its own alias, and
the parent returns its retained String unchanged after all accepted work succeeds.
There is no parent synthesis, repair, or additional native answer validation.
Empty/extended answers retain those semantics. Failure/cancellation propagates
through existing mission behavior without a substitute result.

[Input bindings](input-bindings.md) enforce exact transferred subtrees; ordinary
direct inputs retain existing normalization. Parent `output_bindings` consume
the generated schema under existing partial registration checks and validate
selected values against destination contracts at runtime. Fixed disjoint Choice
enums or Score number-to-integer bindings can fail registration; dynamic criteria
remain runtime checks. Source lookup is exact and never silently omits a missing
probability key. [Output contracts](output-contracts.md) owns forwarding and
assembly rules.

## Fixed criteria and selected-answer bindings

Save this complete alternative leaf as `skills/assessFixedContact.yaml`. These
candidate labels and levels are illustrative author data, not framework defaults
or automatic business actions. It uses the same connections as the dynamic leaf:

```yaml
name: assessFixedContact
description: Evaluate a contact using fixed author-supplied criteria.
model: typesafeDefault
input_schema:
  type: object
  properties:
    context: {type: object, additionalProperties: true}
  required: [context]
  additionalProperties: false
typesafe:
  state: "${/input/context}"
  questions:
    department:
      type: choice
      criteria:
        billing: Charges
        technical: Checkout failures
    repeat:
      type: noul
      criteria:
        "true": The contact reports a previous support attempt.
        "false": The contact reports no previous support attempt.
    severity:
      type: score
      instructions: Assess how much observed functionality is lost.
      criteria: [All functions available, A critical operation is blocked]
```

Invoke directly with `skills.invoke("assessFixedContact", Map.of("context",
Map.of("message", "Again: checkout failed. Browsing works.")))`. Each primitive
is independently usable by retaining just its question in either complete leaf;
single-question results still have that question's name at the root.

Save this separately as `skills/summarizeContact.yaml`. It binds input to the
fixed leaf and assembles selected values into an ordinary parent output contract:

```yaml
name: summarizeContact
description: Extract selected contact judgments without a synthesis call.
model: chatDefault
planning_mode: true
max_steps: 1
prompt: Have assessFixedContact assess this contact and extract its judgments.
input_schema:
  type: object
  properties:
    context: {type: object, additionalProperties: true}
  required: [context]
  additionalProperties: false
allowed_skills:
  - name: assessFixedContact
    required: true
    max_tasks: 1
    input_bindings:
      /context: {from: input, path: /context}
output_schema:
  type: object
  properties:
    department: {type: string, enum: [billing, technical]}
    repeatProbability: {type: number}
    severity: {type: number}
    technicalProbability: {type: number}
  required: [department, repeatProbability, severity, technicalProbability]
  additionalProperties: false
output_bindings:
  /department: {from: child_result, skill: assessFixedContact, path: /department/choice}
  /repeatProbability: {from: child_result, skill: assessFixedContact, path: /repeat/noul}
  /severity: {from: child_result, skill: assessFixedContact, path: /severity/score}
  /technicalProbability: {from: child_result, skill: assessFixedContact, path: /department/probabilities/technical}
```

Invoke `summarizeContact` with the same `context` input as the fixed direct leaf.
This parent validates the selected values against its receiving output schema;
the leaf's descriptive schema does not validate the complete native answer.
Changing the destination enum to disjoint values fails registration. A missing
`technical` probability or an actual selected value outside the receiving enum
fails assembly at invocation. Dynamic candidates cannot establish fixed enum or
probability-key guarantees at registration. Bindings do not impose correctness,
probability ranges, confidence gates, routing, or actions.

## Transport, usage, and observation

Apache HttpClient 5.6.1 owns the shared native transport, with automatic retries
and redirects disabled. Connection `request-timeout` sets response/socket
inactivity behavior, rather than an elapsed whole-call deadline. Continuing body
chunks can keep a response alive longer than that duration. With timeout omitted,
the pinned Apache defaults are a three-minute connect timeout and three-minute
connection-lease timeout, no explicit response timeout, and effective three-minute
socket inactivity from `SocketConfig.DEFAULT`. A configured value sets response
and connection socket timeout, leaving connect/lease at three minutes.
The mission deadline remains authoritative across sending, reading, and backoff.
Cancellation aborts the native request; late writes follow existing lifecycle gates.
Cancellation cannot retract an inference already accepted remotely or guarantee
that the provider stops work or billing. A retry may repeat remote inference;
there is no exactly-once inference guarantee.

The existing provider-retry policy repeats the same captured resolved request.
Both routes use this same Framework retry owner and mission-wide provider-attempt
budget; Apache adds no retries. See [connection retry policy](model-selection-and-connections.md)
for settings, defaults and backoff.
HTTP 529 is transient overload; invalid envelope extraction is terminal.
Successful responses have no additional Loomspan body byte ceiling, including
valid answers larger than 1 MiB on either route. Parser constraints, available
memory, provider limits, and timeouts still apply. HTTP error evidence alone is
bounded at 1 MiB with explicit truncation metadata.
Response acceptance is independent of diagnostic capture: disabling or truncating
capture does not impose an answer-size limit or change extraction. Inspect the
returned capture state before claiming that request or envelope content is complete.

Successful HTTP responses are accounted once while writable, even when answers
extraction subsequently fails. Nonnegative JSON integer `usage.input_tokens` and
`usage.output_tokens` with a representable integer sum yield `EXACT`, including
zero/zero. Missing, partial, string, decimal-token (including `1.0`), negative, or overflowing counts yield
`UNAVAILABLE` with zero accounting placeholders and retained native metadata.
TypeSafe never uses text-estimated `HEURISTIC` usage. `modelCalls` follows existing
recorded-response accounting; one batch does not multiply by question count.
Error-body usage is not ingested, and these counters are not a provider billing
ledger.

Console/MCP catalog `source: TYPESAFE` is separate from public `SkillKind`.
Details show original YAML, generated schema, and the captured source label;
supplied opaque labels remain verbatim and have no Java bean/method fields.
Native request/response content uses `payloadType: evaluation` with request,
envelope or malformed `rawBody`, endpoint, requested model, and available served
model. Existing JSON/content views read inline and chunked evidence without chat
messages. Canonical content follows explicit capture controls without sensitivity
rewriting; the existing derived-journal/observer exception remains confined to
that projection. Read [traces](traces-and-debugging.md) for access and capture state.

Declarations, connections, schema, and clients belong to the root's captured
generation. Reload affects new roots; active children/retries retain old resources.
Concurrent calls resolve private criteria without changing catalog metadata.

## Evidence and limits

Focused `TypeSafeDeclarationTests` anchors protect the authoring edges:
`strictNativeManifestParsingRejectsDuplicateAndUnknownFields` covers strict YAML;
`parsedYamlScalarFidelityAndResolvedCopiesRemainExact` covers parsed text and
detached request capture; `optionalPrimitiveFieldsPreserveOmissionNullAndPartialCriteria`
covers optional primitive forms; `exactGrammarAndCollisionScopeRemainBounded`
covers expression/pointer limits and name collisions; and
`descriptiveSchemasStayFixedAcrossDynamicInvocationsAndDefensiveCopies` covers the
complete descriptive schema table. These checks exercise
`TypeSafeDeclaration#tokens`, `#resolve`, `#validate`, and `#deriveSchema`.

`TypeSafeSkillIntegrationTest#acceptedNormalizedAndExactBoundInputsProduceEqualNativeRequests`
proves ordinary normalization versus exact bound transfer through public invocation.
`TypeSafeSkillIntegrationTest#concurrentDynamicScoreLevelsStayPrivateAndMetadataRemainsStable`
proves private Score levels/results and stable catalog metadata during overlapping
local calls. Existing Choice/reload and retry fixtures protect generation capture
and reuse of one resolved request.

`TypeSafeSkillIntegrationTest#nativeTerminalHttpErrorsDoNotRetryOrAccountErrorUsage`,
`#disabledNativeRetryMakesOneSend`, and
`#nativeRetryExhaustionStopsAtConfiguredAttempts` protect physical send/attempt
cardinality, failure taxonomy, unchanged requests and zero error-body accounting
on both prefixes. `#nativeUsagePrecisionAndResponseAccountingRemainExactOrUnavailable`
protects exact/zero and unusable native usage through public invocation of a mixed
batch. `#oversizedNativeErrorRetainsBoundedTruthfulEvidenceOnBothRoutes` protects
bounded UTF-8 error evidence and explicit truncation separately from large results.

Construction diagnostics retain the original cause and any suppressed cleanup
failures. `NamedAiConnectionRegistryTests#constructionFailureRetainsCleanupFailuresAndContinuesClosing`
protects cleanup continuation and diagnostic chains;
`#nativeConstructionOwnershipClosesAcquiredResources` and
`#nativeAllocationClosesWhenRuntimeConstructionFails` observe actual Apache pool
closure. These are framework test anchors, not application extension points.
`ConnectionDiagnosticFidelityTest#constructionCauseRemainsVerbatimAlongsideStructuredIdentity`
protects retained construction details alongside structured model identity.

`TypeSafeDeclaration`, `TypeSafeDeclarationTests`, and `YamlSkillCatalog` own
declaration/substitution/schema checks. `TypeSafeClient`, `TypeSafeExecutor`, and
`TypeSafeClientTest` protect actual HTTP route, headers, retry ownership, response
limits, native usage, and inactivity behavior. `TypeSafeSkillIntegrationTest`
exercises public direct/child dispatch, controls, forwarding, and captured
generations using deterministic local provider fixtures. `ConsoleRestFixtureCorpusTest`,
`ConsoleTraceFixtureCorpusTest`, Go observability/MCP/corpus tests and
`TraceRecords.model.test.tsx` protect coordinated diagnostic consumption.

`ConsoleTraceFixtureCorpusTest#nativeFailureCorpusPreservesRawResponseAndBoundedProviderEvidence`
and Go `TestNativeEvaluationFailureFixturesPreserveLogicalContentAndAttemptFacts`
protect `native-evaluation-malformed-response` and
`native-evaluation-provider-failure`: raw response text, retained business keys,
HTTP-success versus terminal extraction failure, failed attempts and truncated
provider evidence. Go `TestNativeEvaluationAbsentContentRemainsUnavailableReaderState`
and the focused `TraceRecords.model.test.tsx`/`TraceAttemptDiagnostics.test.tsx`
cases protect unavailable, incomplete and explicitly truncated reader evidence.
Absent content is a reader state; it does not describe a new producer setting.

`TypeSafeRegisteredInspectionIntegrationTest#discoveredAndSuppliedNativeMetadataTraverseAuthenticatedRoutes`
protects actual discovered and supplied registration through authenticated catalog
routes, original YAML, generated schema, and verbatim opaque labels.
`TypeSafeSkillIntegrationTest#publishedTypeSafeExamplesExecuteDirectAndFullyBoundForwardingParent`
loads the published connection mappings and all four complete skill declarations
on both local route prefixes. It protects fixed/dynamic mixed batches, selected
answer assembly, verbatim model IDs, complete named results, captured generation,
direct/child canonical frames, shared accounting, and forwarding without synthesis.
`#nestedNativeFailureRetainsSharedAccountingWithoutSubstitute` protects denied
children and exhaustion of the provider budget shared with parent planning.
`SkillGenerationManagerTest#nativeSchemasEnterExistingBindingCompatibilityAndCapturedCatalogs`
protects fixed disjoint-enum, singleton-type and number-to-integer registration
rejections and dynamic-schema deferral.
`DeclaredOutputBindingsPlanningIntegrationTest#nativePassthroughIsEnforcedOnlyByConsumingBindingContract`
protects receiving-enum rejection and exact missing/case-mismatched probability
paths for fixed and dynamic criteria at invocation.

These are fixture/source-backed mechanics, not evidence of live model accuracy,
identical judgments between routes, or acceptance of every locally valid criterion.
No typed public TypeSafe facade, provider extension SPI, automatic business
routing, model discovery, or specialized Console dashboard is added.
