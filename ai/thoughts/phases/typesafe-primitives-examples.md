# TypeSafe primitive authoring examples

Status: Review corrections accepted, including TypeSafe fidelity and the name-collision exception; walkthroughs are not implemented or executed tests.
Updated: 2026-10-10.

These examples exercise the proposed contract in the
[TypeSafe primitives roadmap](typesafe-primitives-roadmap.md). JSON request sketches
are useful design inputs even though the proposed skill declaration uses YAML.
Keep the roadmap authoritative for shared semantics and the Phase 0 decisions.

The examples use `${/input/...}` as the working substitution syntax. The alternative
`#{...}` offers no needed additional capability. This is path-based substitution,
not an expression language. `$${` emits literal `${`; inserted input is never rescanned.

Each example shows the proposed `typesafe` block and its input requirements.
A complete registered skill also declares `name`, `description`, `model`, and
`input_schema`. Model aliases resolve through configuration rather than the template.
All cases are intended to work through both direct and child skill invocation.
The agreed skill result contains named answers directly, with no outer `answers`
wrapper. Preserve complete answer objects even for one question. For example,
the Choice example below exposes `/application/choice`, and the Score examples
expose `/bug_severity/score`. The full provider response, including model and usage
metadata, belongs in canonical execution diagnostics rather than the skill result.
They should also work through either direct TypeSafe or OpenRouter System One
connections, selected through model configuration. See the roadmap's connection
options for endpoints and selected configuration decisions. Changing routes must not
require edits to these state and question templates; actual answer values may vary.

The agreed transport is Apache HttpClient 5 with automatic retries and redirects
disabled, owned by the existing connection registry. Loomspan owns each attempt
and retries using the existing policy. Reuse chat's existing mission timeout,
cancellation, cleanup, and late-write rules. Map `request-timeout` to documented
Apache transport behavior without adding a per-attempt total deadline mechanism.
Neither TypeSafe route has an additional Loomspan-imposed response-body byte ceiling.
Preserve complete answers, including valid responses larger than 1 MiB; provider/model
limits, normal parser constraints, available memory, and timeouts still apply.
Provider-error diagnostic capture is bounded at 1 MiB with explicit truncation
metadata; it must not reject or truncate otherwise valid results. Accounting follows
chat's existing boundaries and reuses its usage services, counters, quotas, and write
gates. HTTP errors record failed attempts and diagnostics without separate error-body
usage accounting; late responses cannot bypass the execution write cutoff.
Score criteria accepts one through ten levels, each a string, object, array, or
null. Preserve entries and order; TypeSafe decides acceptance on the wire, and
provider errors follow normal failure handling. Follow TypeSafe's shapes, with the accepted
case-insensitive name-collision restriction retained for Loomspan compatibility.

These are examples of request data and existing skill composition, not additional
framework features. They require no business-rule engine, automatic routing,
ticket creation, fallback policy, schema compatibility proof, or output validator.
Path/error cases define the small TypeSafe substitution mechanism only. Cover
shared semantics once in tests; this document does not require every example to be
run across every route, entry point, and failure combination.

## Whole state substitution with two Noul questions

Input contract: a required string named `question`.

```yaml
typesafe:
  state: "${/input/question}"
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

Example input:

```json
{
  "question": "I have asked three times now. Can I please just talk to a real person?"
}
```

The resolved `state` is exactly the supplied string. Both question definitions
remain unchanged and are evaluated in the same request. The output schema can
describe the fixed answer names `is_human_escalation` and `is_repeat_contact`, each
with a Noul answer. No separate confidence field is introduced for Noul.

The whole-value expression retains the input's type; the input schema is what
requires a string in this example. Missing input fails before inference. Quoted
`"true"` and `"false"` are string keys in the YAML declaration.

The repeat-contact question judges evidence in the supplied message. It does not
verify contact history that the caller has not supplied. Providing actual history
in structured state would support a different, better-informed question.

## Embedded exception text with fixed Score criteria

Input contract: a required string named `exception`.

```yaml
typesafe:
  state: "Our app has thrown the following exception: ${/input/exception}"
  questions:
    bug_severity:
      type: score
      instructions: How severe is the reported issue?
      criteria:
        - Functionality remains available with no reported user impact.
        - A feature is degraded, but affected users have a workaround.
        - A critical operation is blocked and affected users have no workaround.
```

For input `{"exception": "Timeout while processing checkout"}`, the resolved state
is `Our app has thrown the following exception: Timeout while processing checkout`.
Criteria describe severity only; they do not imply ticket creation or dispatch.

Multiline exception text, quotes, and newlines remain intact. An object supplied as
`exception` fails the declared string input contract; objects also cannot be
implicitly embedded into strings under the proposed interpolation rules.

The three fixed levels allow a derived Score range of 0 through 2, with fractional
scores possible. The answer includes the distribution, legend, and confidence.
This establishes template behavior, not a predetermined model judgment.

An exception alone may not establish actual user impact. This example exercises
substitution; it does not promise a reliable severity judgment from incomplete data.
Authors can supply additional context using the structured state already supported.

## Caller supplied Score criteria array

The question name and primitive remain fixed while the caller supplies levels:

```yaml
name: assessBugSeverity
description: Assess an exception against caller-supplied severity levels.
model: typesafeDefault

input_schema:
  type: object
  properties:
    exception:
      type: string
    severityLevels:
      type: array
      items:
        type: string
  required: [exception, severityLevels]
  additionalProperties: false

typesafe:
  state: "Our app has thrown the following exception: ${/input/exception}"
  questions:
    bug_severity:
      type: score
      instructions: How severe is the reported issue?
      criteria: "${/input/severityLevels}"
```

Example input:

```json
{
  "exception": "Timeout while processing checkout",
  "severityLevels": [
    "No observed impact on functionality",
    "Functionality is degraded, but a workaround exists",
    "A critical operation is blocked with no workaround"
  ]
}
```

The resolved `criteria` value is the supplied array, not its JSON representation
inside a string. The quoted YAML expression does not force a string result.
The input schema here intentionally accepts string descriptions; it does not
define the full set of structured descriptions TypeSafe can accept.

For this invocation, the valid levels are 0, 1, and 2. Five supplied descriptions
would instead produce levels 0 through 4. The catalog can describe the stable
Score answer fields, but must not promise three particular levels for every call.
Loomspan validates the resolved criteria before sending, then trusts TypeSafe's answer. Downstream
consumers must not assume a fixed range across different supplied level counts.

A string instead of an array or non-string items fails the declared input contract.
An empty array or more than ten items fails resolved-request validation before HTTP.
The one-level case passes local request validation and is sent unchanged. Null
levels are permitted by the Score request contract, but this example's explicitly
declared string-item input contract still rejects them.
The existing YAML input schema does not support `minItems` or `maxItems`; these
limits belong to the primitive contract. See the
[TypeSafe Score documentation](https://docs.typesafe.ai/primitives/score).

Dynamic criteria are included. The catalog describes score as a number and explains
its input-dependent range in prose. For this invocation the expected provider score
range is [0,2], with `0`, `1`, and `2` probability/legend
keys. No invocation-specific output schema or answer validation is generated.
The roadmap defines catalog schema construction and minimal response handling.

## Linux top snapshot with fixed Choice candidates

Input contract: a required string named `top`.

```yaml
typesafe:
  state: "Here are the results from the 'top' command on my Linux system: ${/input/top}"
  questions:
    application:
      type: choice
      instructions: What subsystem should I troubleshoot first?
      criteria:
        system: Seeing high I/O wait or high system CPU.
        database: High processor time on PostgreSQL or MySQL.
        application: High processor time on Tomcat.
```

Resolution concatenates the literal state prefix with the exact supplied `top`
string, preserving multiline output. Criteria keys are fixed, so the derived
selected-value enum is `system`, `database`, or `application`, and the distribution
uses those keys. This describes TypeSafe's contract; Loomspan passes answers through
without independently checking the selected value or distribution.

The selected label is returned data. Loomspan does not troubleshoot the subsystem,
route work, or add an automatic fallback candidate. Candidate design is the author's
responsibility; no framework behavior depends on these particular labels.

## Structured state, dynamic Choice, and mixed batch

For the earlier examples, the resolved HTTP envelope is exactly
`{"model": "jev-latest", "state": <resolved state>, "questions": <declared questions>}`,
with only the indicated substitutions applied. Their catalog roots require exactly
their declared question names. Apply the roadmap's Noul shape to both contact
answers, fixed Score(N=3) to exception severity, dynamic Score to caller-supplied
levels, and fixed Choice(system/database/application) to the Linux snapshot.
These schemas describe expected results and participate in existing parent output-
binding registration checks; they do not validate TypeSafe responses. The response
handling matrix below defines extraction behavior; semantic judgments are illustrative.

For dynamic Score, the exact catalog field schemas (inside the required Score
answer fields) are:

```json
{
  "score": {"type": "number", "description": "Score from 0 through the supplied level count minus one."},
  "probabilities": {
    "type": "object", "additionalProperties": true,
    "description": "Maps supplied level indices to probabilities from 0 to 1."
  },
  "legend": {
    "type": "object", "additionalProperties": true,
    "description": "Provider-returned level descriptions."
  }
}
```

Contiguity and matching probability/legend keys belong to TypeSafe's answer contract.
Loomspan validates the request's level count before sending, but does not validate
the returned maps. The catalog fragment describes expected shapes across all calls.

This complete declaration uses the existing YAML input-schema subset. An open
candidate object allows arbitrary keys; resolved-request validation constrains its
values, cardinality, and case-insensitive key uniqueness. This does not promise
arbitrary JSON Schema support. A candidate map containing both `Billing` and
`billing` fails before HTTP; either name alone is valid and retains its spelling.

```yaml
name: assessContact
description: Evaluate a support contact against caller-supplied departments.
model: typesafeDefault
input_schema:
  type: object
  properties:
    context:
      type: object
      additionalProperties: true
    candidates:
      type: object
      additionalProperties: true
    focus:
      type: string
    low:
      type: string
    high:
      type: string
  required: [context, candidates, focus, low, high]
  additionalProperties: false
typesafe:
  state: "${/input/context}"
  questions:
    department:
      type: choice
      instructions:
        task: "Select the department for ${/input/focus}."
        firstMessage: "${/input/context/messages/0/text}"
      criteria: "${/input/candidates}"
    repeat:
      type: noul
      instructions: Does the contact report a previous support attempt?
    severity:
      type: score
      instructions: How much observed functionality is lost?
      criteria:
        - "${/input/low}"
        - "${/input/high}"
```

Input:

```json
{
  "context": {
    "messages": [{"text": "Again: checkout says \"failed\".\nPlease help 🧭"}],
    "history": ["ticket-7"],
    "account": {"tier": "standard", "note": null},
    "literal": "${/input/doNotEvaluate}"
  },
  "candidates": {"billing": "Charges", "technical": {"scope": "Checkout failures"}},
  "focus": "this contact",
  "low": "Functionality remains available.",
  "high": "A critical operation is blocked."
}
```

Exact resolved HTTP request assuming configured `provider-model: jev-latest`
(JSON whitespace and object ordering are not contractual):

```json
{
  "model": "jev-latest",
  "state": {
    "messages": [{"text": "Again: checkout says \"failed\".\nPlease help 🧭"}],
    "history": ["ticket-7"],
    "account": {"tier": "standard", "note": null},
    "literal": "${/input/doNotEvaluate}"
  },
  "questions": {
    "department": {
      "type": "choice",
      "instructions": {
        "task": "Select the department for this contact.",
        "firstMessage": "Again: checkout says \"failed\".\nPlease help 🧭"
      },
      "criteria": {"billing": "Charges", "technical": {"scope": "Checkout failures"}}
    },
    "repeat": {"type": "noul", "instructions": "Does the contact report a previous support attempt?"},
    "severity": {
      "type": "score",
      "instructions": "How much observed functionality is lost?",
      "criteria": ["Functionality remains available.", "A critical operation is blocked."]
    }
  }
}
```

The three questions have no inter-question dependency. Here is its complete catalog
output schema; it is generated, not author-supplied:

```json
{
  "type": "object",
  "required": ["department", "repeat", "severity"],
  "additionalProperties": false,
  "properties": {
    "department": {
      "type": "object",
      "required": ["type", "choice", "confidence", "probabilities"],
      "additionalProperties": true,
      "properties": {
        "type": {"type": "string", "enum": ["choice"]},
        "choice": {"type": "string"},
        "confidence": {"type": "number", "description": "Distribution confidence, from 0 to 1."},
        "probabilities": {
          "type": "object", "additionalProperties": true,
          "description": "Maps supplied candidates to probabilities from 0 to 1."
        }
      }
    },
    "repeat": {
      "type": "object", "required": ["type", "noul"], "additionalProperties": true,
      "properties": {
        "type": {"type": "string", "enum": ["noul"]},
        "noul": {"type": "number", "description": "Probability of yes, from 0 to 1."}
      }
    },
    "severity": {
      "type": "object",
      "required": ["type", "score", "confidence", "probabilities", "legend"],
      "additionalProperties": true,
      "properties": {
        "type": {"type": "string", "enum": ["score"]},
        "score": {"type": "number", "description": "Score from 0 through 1 for the two declared levels."},
        "confidence": {"type": "number", "description": "Distribution confidence, from 0 to 1."},
        "probabilities": {
          "type": "object", "required": ["0", "1"], "additionalProperties": false,
          "properties": {
            "0": {"type": "number", "description": "Probability from 0 to 1."},
            "1": {"type": "number", "description": "Probability from 0 to 1."}
          }
        },
        "legend": {
          "type": "object", "additionalProperties": true,
          "description": "Provider-returned level descriptions."
        }
      }
    }
  }
}
```

For this invocation, TypeSafe's expected department choices are `billing` and
`technical`, with matching probability keys. Loomspan does not create a specialized
output schema or recheck those guarantees. The catalog stays
unchanged when another call supplies `sales` and `support`. Fixed descriptions
may be templated without changing a fixed Choice enum or Score level count.

Illustrative provider result (an extraction fixture, not a predicted judgment):

```json
{
  "department": {"type": "choice", "choice": "technical", "confidence": 0.8,
    "probabilities": {"billing": 0.1, "technical": 0.9}},
  "repeat": {"type": "noul", "noul": 0.9},
  "severity": {"type": "score", "score": 0.75, "confidence": 0.6,
    "probabilities": {"0": 0.25, "1": 0.75},
    "legend": {"0": "Functionality remains available.", "1": "A critical operation is blocked."}}
}
```

The provider envelope containing this `answers` object also supplies `model` and
`usage`; retain it in canonical diagnostics. Confidence is passed through, not
recalculated or validated by Loomspan.

## Exact resolution and error cases

Each row describes a string node in a templated field, after YAML parsing.
The source input object below is assumed accepted by the ordinary input contract.

| Node / input | Expected behavior |
| --- | --- |
| `${/input}` | Whole input object, copied once |
| `${/input/a~1b/~0key/0}` with `{"a/b":{"~key":["yes"]}}` | String `yes` |
| `${/input/a/}` with `{"a":{"":"empty-key"}}` | String `empty-key` |
| `${/input/a/01}` with `a` an array | Invalid array index before HTTP |
| Same pointer with `a` equal to `{"01":"value"}` | String `value` |
| `${/input/a/3}` with one-element array | Missing path before HTTP |
| `${/input/a~2b}` | Registration error: invalid pointer escape |
| `${/input/times}` with numeric 3 | Numeric 3; invalid as top-level state, valid nested in state |
| `times=${/input/times}` with numeric 3 | String `times=3`; other numeric spellings use the existing JSON codec, without canonicalization |
| `flag=${/input/flag}` with boolean false | String `flag=false` |
| ` ${/input/history}` with an array | Interpolation error; leading space prevents whole-value replacement |
| `$${/input/missing}` | Literal `${/input/missing}`, no lookup |
| `${/input/message}` with value `${/input/missing}` | Same literal value, no second lookup |
| `${ENV_VAR}` or `${/input/value` | Registration error, not configuration expansion |
| `${/input/optional}` when absent | Invocation error even if optional in schema |
| `${/input/value}` with null | Null retained; allowed as nested state, Choice/Noul description, Noul criteria, Score level, or instructions subject to primitive-specific requirements; rejected as top-level state |
| `value=${/input/value}` with null | Interpolation error |
| `state: >` versus `state: >-` | YAML trailing newline retained versus stripped; resolver makes no further change |

Literal criteria with an invalid shape fail registration. A whole-input criteria
reference that resolves to the same shape fails invocation before HTTP. Empty
Choice map, 256 candidates, empty Score criteria, 11 Score levels, and numeric
instructions violate the documented request shapes. Singleton Choice is allowed;
do not add a second candidate or fabricate an answer. Singleton Score criteria
such as `["present"]` and criteria containing null such as `[null, "present"]`
pass local request validation and are sent unchanged. Do not remove null entries
or renumber levels. Provider rejection follows normal failure handling; these
examples do not promise server acceptance of disputed forms.
Duplicate YAML keys follow existing manifest parsing rules.
Object keys are literal, even if they contain `${...}`; no key templating is added.
A templated `type` or entire templated `questions` map is invalid.
That restriction belongs to the initial registered-skill authoring contract, not
TypeSafe's protocol. Template escaping disambiguates authoring syntax without
changing the intended resolved content.

Optional-field walkthroughs, following TypeSafe's official SDK references:

| Declaration | Expected behavior |
| --- | --- |
| Noul with instructions and no criteria, null criteria, or `{}` criteria | Preserve the supplied form; do not insert criteria keys |
| Noul with instructions and only `criteria.true` or only `criteria.false` | Accept the partial object; do not fill in the other side |
| Noul with instructions and a null criterion description | Preserve null |
| Noul with omitted/null instructions and criteria describing an outcome | Accept the criteria-driven question without generating instructions |
| Noul with neither instructions nor an outcome description | Reject under TypeSafe's primitive-specific requirement |
| Choice/Score with otherwise valid criteria and omitted/null instructions | Preserve omission/null; do not require or generate instructions |

These are request-shape checks, not judgments about whether an author's question
or rubric is useful. Application input validation still applies first; the resolved
provider request receives no additional coercion or rewriting pass.

Question IDs `Billing` and `billing` in the same declaration fail registration,
as do those two fixed candidate keys within one Choice. Apply the existing
locale-independent lowercase comparison used for authored schema names. The same
collision in a caller-supplied candidate map fails resolved-request validation
before HTTP. Matching names across different questions are fine. These checks do
not extend to arbitrary state or description objects, and never rewrite keys.
This name-collision rule is an explicitly accepted Loomspan compatibility exception,
not a TypeSafe requirement. Keep it for consistency with existing schema and binding
behavior; no shared case-matching redesign is part of this feature.

## Response handling and binding walkthroughs

Apply each mutation independently to the valid mixed-batch envelope above:

| Mutation | Expected behavior |
| --- | --- |
| Unreadable JSON body | Fail response extraction; retain diagnostic evidence; no repair or automatic retry |
| Missing `answers`, or `answers` is null, a string, or an array | Fail response extraction; do not manufacture a result |
| `answers` is `{}` | Return `{}`; no per-question presence or count check |
| Remove `repeat`; add an undeclared answer; change type to `Noul` | Pass through the answers object; no individual answer validation |
| Department choice `sales` or probability key `Technical` | Preserve exactly as returned; no membership or case check |
| Add `provider_detail` inside an answer | Preserve field in returned answer |
| Add `id`, `provider`, or `usage.cost` to envelope | Preserve in diagnostics, exclude from business result |
| Probability string `"0.9"`, negative number, unexpected sum, or score inconsistent with probabilities | Preserve the decoded JSON values; no coercion, range, or mathematical checks |
| Remove legend or add level `2` | Preserve response contents; no reconstruction or level check |
| Structured description returned in a legend value | Preserve its structure |
| Missing model or usage with an answers object | Return answers; missing usage is `UNAVAILABLE`, never estimated from text |
| Usage reports `input_tokens: 10` and `output_tokens: 2` | Record 10 prompt units, 2 completion units, total 12, precision `EXACT` |
| Usage reports both token counts as zero | Record exact zero usage with precision `EXACT` |
| Only one token count is present, or counts cannot populate the existing accounting record | Return answers; usage is `UNAVAILABLE`; retain native metadata without inventing the missing count |
| Successful HTTP response fails answers extraction but includes usable usage, while execution remains writable | Account available usage once; preserve response and fail extraction |

One batch is one logical evaluation, regardless of question count. Existing
accounting increments `modelCalls` per recorded response; it does not independently
count logical evaluations. HTTP error responses do not add usage to model-response
counters, even if the error body reports it. Successful HTTP responses record
available usage once while writable, including when subsequent answers extraction
fails. Late responses follow the existing write gate; no background accounting or
cutoff bypass is added. These totals are not a complete provider billing ledger.

Direct invocation and a parent's input bindings produce the same resolved request
for identical accepted, normalized inputs. Substitution reads that validated input.
Ordinary inputs may be coerced; author-bound subtrees retain exact validation. For
an integer `times` field, direct input `{"times":"3"}` can normalize to
`{"times":3}`, whereas binding the string `"3"` into that field fails the receiver
contract. Binding numeric `3` succeeds and produces the same request as the normalized
direct input. Preserve this existing distinction; no binding change is required.
Reject non-JSON-compatible input values, including non-finite numbers, before
serialization even where ordinary number validation accepted them.

A parent binding from `/department/choice`
gets `technical`; no `/answers` prefix is used. A child input receiver that permits only
`sales` fails its input contract before its provider call. A binding from
`/department/probabilities/sales` fails missing-source lookup for this invocation.
No fallback or silent omission is introduced. Parent output bindings additionally
compare available schemas at registration, then validate actual selected values
against the parent's destination schema at runtime. Source value
lookup is exact; destination schema lookup, projection, and composition retain
their existing case-insensitive behavior. No shared binding change is required.

The registration checks apply to parent `output_bindings`, not automatically to
child `allowed_skills.input_bindings`:

| Parent output-binding example | Existing behavior to retain |
| --- | --- |
| Fixed Linux Choice `/application/choice` to destination enum `[billing]` | Registration failure: no overlap with `[system, database, application]` |
| Same fixed Choice to destination enum `[system]` | Registration passes this enum check; actual `database` fails runtime destination validation |
| Score `/bug_severity/score` to an integer destination | Registration failure: number-to-integer is incompatible even if a particular score could be integral |
| `/application/type` (enum `[choice]`) to enum `[score]` | Registration failure: singleton enums are disjoint |
| Mixed-batch dynamic `/department/choice` to enum `[sales]` | No fixed source enum to compare; returned `technical` fails runtime destination validation |

These are existing partial compatibility checks, not a new schema proof system or
provider-output validator. Integration must make the generated TypeSafe schema
available to them rather than routing around them.

For the dynamic Score example, a three-level call and concurrent five-level call
share the same descriptive catalog schema, with expected provider score ranges
[0,2] and [0,4].
Only their requests are validated locally; answers pass through. Their resolved
criteria and returned results must not leak across calls.
Reloaded declarations affect new invocations only, following captured-generation
semantics. These are acceptance walkthroughs, not executed tests or live results.

## Parent forwarding a TypeSafe result

A normal parent may forward the `assessBugSeverity` TypeSafe skill declared above.
The parent's `chatDefault` alias selects a configured chat model; the child retains
its `typesafeDefault` alias. This uses the existing parent forwarding contract:

```yaml
name: assessReportedBug
description: Evaluate the supplied bug and return the severity assessment.
model: chatDefault
planning_mode: true
prompt: Have assessBugSeverity evaluate the supplied exception and severity levels.
input_schema:
  type: object
  properties:
    exception:
      type: string
    severityLevels:
      type: array
      items:
        type: string
  required: [exception, severityLevels]
  additionalProperties: false
allowed_skills:
  - name: assessBugSeverity
    required: true
    max_tasks: 1
    input_bindings:
      /exception: {from: input, path: /exception}
      /severityLevels: {from: input, path: /severityLevels}
output_from:
  skill: assessBugSeverity
```

The parent inherits the child's dynamic Score output schema. Its accepted plan
must contain exactly one direct task for that child, and all accepted work must
complete before forwarding. Parent `output_schema`, `output_schema_max_retries`,
`linter`, and `output_bindings` remain prohibited with `output_from`.

A focused integration walkthrough should assert:

- Successful evaluation returns the child's retained named-answers JSON text
  unchanged, without an `answers` wrapper or provider envelope. The parent performs
  no final synthesis, repair, or additional answer validation.
- Replacing the provider's `answers` with `{}`, or adding answer names or fields,
  preserves the resulting child text through forwarding despite the descriptive
  schema. Schema inheritance does not add a response-validation gate.
- The existing `RESULT_FORWARDED` event links the parent to the selected child task;
  the full provider envelope remains in the child's canonical diagnostics, subject
  to the roadmap's capture controls.
- A child invocation failure propagates through existing execution behavior; the
  parent does not manufacture a substitute result.

Rejecting `output_from` on a TypeSafe leaf only prevents that leaf from declaring
its own forwarding workflow. This parent example requires no new forwarding
mechanism and is an acceptance walkthrough, not an executed test.
