# PR 21 — Bind child skill inputs without model reproduction of evidence

## Outcome

Let a planning skill's author declare how its input and its completed children's
results populate another child's input. Framework must transfer those values
directly, without asking the planning model to serialize them again into tool-call
arguments. The receiving skill still receives complete evidence under its normal
input contract. Models author only the unbound arguments that require their input.

PR 21 is the developer-assigned ticket identifier. This file does not assert that
a GitHub pull request has been created. Ticket creation does not start the pipeline.

## Background and evidence

The equipment-service reference suite has completed eight Java model comparisons
on the frozen `post-pr20-20261004` baseline. Sol 6.1 / medium remains the empirical
reference, not the oracle. A focused offline analysis found two distinct issues:

- Authoritative source transfer depends on model reproduction. Luna added the
  dispatch contact's name to an unnamed warranty contact when `resolveEquipment`
  called `planResolution`; the mutation propagated into comparison. DeepSeek Flash
  changed contact fields at the earlier assessment boundary. Schema validity alone
  did not establish equality with the source.
- Non-Flash GLM's priority call to `planResolution` contained 24,992 characters and
  omitted one final brace. Correction repeated the identical malformed action.
  MiMo's priority calls to `compareOptions` contained 30,719 and 30,648 characters,
  both missing a final brace despite parser feedback. Most content was existing
  source evidence and an accepted assessment. All four responses reported `stop`.
  A brace-addition experiment parsed them only in memory; no runtime result or
  original evidence was repaired. These observations do not establish size as the
  cause or distinguish model generation from provider responsibility.

Business timing errors in completed comparisons already existed in `compareOptions`
despite intact relevant source facts. Both parent forwarding and final synthesis
preserved them. This feature addresses transfer fidelity and unnecessary generated
arguments; it does not establish better reasoning or universal model reliability.

The local supporting material is in the sibling `loomspan-sidecar-test-suite` repo:

- `evidence/focused-failure-analysis-20261004/trace-review.json`: actual inputs,
  outputs, request IDs, trace sequences and checksum verification.
- `docs/examples/planResolution.bindings.proposed.yaml`: a complete illustrative
  parent skill retaining its input schema, with hypothetical binding syntax.
- `docs/model-reference.md` and `docs/post-pr20-baseline.md`: reference and baseline
  boundaries. Those baseline artifacts and original captures must remain unchanged.

This ticket carries the required intent independently of those local files.

## Agreed authoring and model-facing design

Bindings belong to a child declaration under the parent's `allowed_skills`.
The skill author chooses the sources. Framework executes the mapping. The model
does not invent references, choose implicit mappings by matching names, or learn
a reference language to use this feature.

The following is the agreed shape of the authoring concept. The spelling and path
encoding below are illustrative; the pipeline may refine them to fit the existing
manifest conventions while retaining the semantics and documenting one unambiguous
supported syntax. This is not a request to implement an expression language.

```yaml
name: planResolution
planning_mode: true

# Existing full input_schema remains in place. It describes the parent's input:
# caseId, assetId, context.equipmentAssessment, context.assetContext,
# context.serviceHistory, context.referenceEvidence, context.serviceTerms,
# and context.operatingNeeds.

allowed_skills:
  - name: compareOptions
    required: true
    max_tasks: 1
    input_bindings:
      caseId:
        from: input
        path: caseId
      assetId:
        from: input
        path: assetId
      context.equipmentAssessment:
        from: input
        path: context.equipmentAssessment
      context.assetContext:
        from: input
        path: context.assetContext
      context.serviceHistory:
        from: input
        path: context.serviceHistory
      context.referenceEvidence:
        from: input
        path: context.referenceEvidence
      context.serviceTerms:
        from: input
        path: context.serviceTerms
      context.operatingNeeds:
        from: input
        path: context.operatingNeeds
      context.entitlements:
        from: child_result
        skill: entitlements
        path: data
      context.entitlementDetermination:
        from: child_result
        skill: entitlements
        path: determination
      context.serviceResources:
        from: child_result
        skill: serviceResources
        path: data
      context.continuityOptions:
        from: child_result
        skill: continuityOptions
        path: data
      context.quotes:
        from: child_result
        skill: quoteOptions
        path: quotes

output_from:
  skill: compareOptions
```

This is a child-declaration excerpt, not a standalone deployable skill: the real
parent also declares the four producers. Each producer accepts `caseId` and
`assetId`, which can likewise be bound from parent input.

The binding key identifies a destination in the child's input. `from: input`
means the validated input of this parent invocation, not the root input or a
global context. `path` identifies a location within that source; source and
destination names need not match. `from: child_result` selects the accepted result
of the named direct child in this parent invocation. Result paths address the
decoded result value, not a trace record or a guessed transport envelope.

`compareOptions` retains its complete receiving input schema. The calling model
sees a separate effective argument contract containing only unbound inputs. It
can supply optional `context.candidateReasoning` if the receiving schema allows
that extension, while Framework supplies the bound evidence. When every required
input is bound and no optional contribution is desired, `{}` is a valid model
argument object. Existing step-action envelope fields retain their usual meaning.

Do not show the model a schema requiring all the evidence fields and then merely
instruct it not to supply them. Derive the effective contract structurally and
use it consistently wherever the runtime presents or validates this child call,
including planning descriptions, execution actions and correction feedback.

## Required behavior

### Exact values and two input contracts

- Support parent-input bindings and direct-child-result bindings to named nested
  object fields. A selected source may be a scalar, null, object or array; preserve
  the complete decoded value, array order, optional-field presence and numeric
  values. The initial feature does not require array-element destinations,
  wildcard selection, interpolation, transformations or arbitrary expressions.
- Keep the receiving input schema authoritative. Framework combines permitted
  model-authored arguments with bound values, then validates the complete input
  before invoking the child. Do not weaken required fields, closed objects,
  types or other receiving constraints to accommodate references or omitted
  model-facing fields. A partial object containing unbound fields must work even
  when other required siblings are bound; empty/all-bound objects must work too.
- Reject a model attempt to populate or overwrite a bound destination, including
  an equal-value copy. Reject conflicting ancestor/descendant assignments; permit
  ordinary ancestor containers holding nonconflicting unbound siblings. Never
  silently choose the model's version or silently discard a conflicting value.
- A missing source field differs from an explicit null. Missing required binding
  sources fail closed; explicit null transfers unchanged and remains subject to
  the receiving contract. Do not omit a failed binding merely because its target
  field is optional. Do not stringify objects, guess values, or recursively parse
  ordinary source strings as JSON. Decode serialized skill-result envelopes at
  the established result boundary without rewriting the accepted result.
- Source results remain immutable from the binding operation's perspective.
  Child invocation or input assembly must not mutate parent input or another
  consumer's view of the accepted source value.

### Scope, dependencies and failures

- Select results only from this parent invocation and its accepted direct-child
  work. A skill name is not a session-global result key. Repeated or concurrent
  invocations of the same parent must not mix evidence.
- The initial feature requires an unambiguous producer: exactly one accepted
  successful result for the named child. Do not pick the first/latest result,
  use a rejected correction candidate, or substitute partial/failed work. Multiple
  producer task instances are not a supported implicit aggregation mechanism.
- A result binding is a real data dependency. Expose that constraint to planning
  and enforce it before execution. Consumers cannot overlap their producers or
  execute before the required successful result exists. Detect unknown producers,
  self/cyclic dependencies, ambiguity and incompatible plans rather than invoking
  a child with incomplete input. Preserve independent-read concurrency and the
  existing joined execution-unit semantics; bindings must not globally serialize
  unrelated tasks. The pipeline chooses the internal dependency representation.
- Validate declarations at configuration/plan validation when enough information
  is available, and perform runtime checks for values and results only knowable
  during execution. Detect duplicate/overlapping destinations and invalid paths.
  Do not pretend an undeclared producer output schema proves compatibility; use
  runtime value validation when source schema information is unavailable.
- Diagnostics must distinguish an invalid declaration, an unavailable/ambiguous
  source, a prohibited model override and an assembled-input contract failure.
  Use existing correction/error lifecycle conventions without inventing source
  content or prompting the model to reconstruct missing bound evidence.
- Binding does not grant permission. Preserve verified caller identity, existing
  child authorization, invocation limits, session/execution isolation and active
  configuration-generation consistency. A model cannot select a foreign source
  by supplying a case identifier, result identifier or apparent reference.

### Compatibility, observability and documentation

- This is opt-in manifest functionality. Skills without bindings keep existing
  behavior and input contracts. Existing `ref://` file/attachment handling and
  PR 20 `output_from` semantics remain unchanged. Do not implement model-written
  reference files or planner-generated bindings as a substitute.
- Support the normal model, Java and REST child invocation paths and their
  accepted results, without introducing a new public extension SPI. Preserve
  the existing closed public API policy. No intentional compatibility break is
  authorized by this ticket.
- Preserve diagnostic fidelity: distinguish model-supplied arguments from the
  effective child input, and make each binding's parent/source task and selected
  path identifiable in execution evidence. Retain the original accepted result
  and actual delivered values through existing canonical evidence mechanisms;
  avoid needless duplicate storage where those mechanisms already suffice.
  Do not introduce masking or content rewriting. Existing explicit resource
  limits and documented trace/journal policies continue to apply.
- Update the Framework skill-authoring documentation library with the supported
  syntax and a complete example showing parent input schema, producer children,
  consumer input contract, effective model arguments, dependency requirements,
  failure behavior and interaction with `output_from`. Clearly distinguish this
  feature from `ref://` and JSON Schema references.

## Acceptance criteria

- [ ] An author can declare bindings under `allowed_skills` for both parent-input
  and direct-child-result sources, including differently named source/destination
  fields. A documented complete example demonstrates the relationship to both
  parent and receiving schemas, without requiring the model to interpret bindings.
- [ ] A parent can pass a large nested assessment and source records into a child
  using an empty argument object or only new candidate reasoning. Captured model
  action arguments contain none of the bound evidence; the actual child receives
  the exact source values and passes its unchanged complete input contract.
- [ ] The effective model contract omits bound fields from required arguments and
  disallows overriding them. Nested partial objects, fully bound objects, allowed
  unbound extensions and mixed bound/unbound inputs behave consistently in initial
  calls and correction attempts. Unbound constraints remain enforced.
- [ ] Exact transfer preserves optional absence, explicit null where valid,
  arrays, nested structures and monetary integers. Missing paths, wrong types,
  invalid receiving values and conflicts fail before child invocation. A consumer
  cannot modify the retained source or another consumer's input through binding.
- [ ] Unknown/ambiguous producers, duplicate/overlapping destinations, cycles,
  unavailable or failed results and model override attempts produce attributable
  failures. No fallback copies evidence from model arguments or another execution.
- [ ] Result dependencies prevent premature or concurrent consumer execution
  while independent producers still run concurrently. Nested and concurrent
  parent invocations using the same skill names remain isolated; correction
  candidates cannot replace the accepted source result.
- [ ] Model, Java and REST producer/consumer paths deliver correctly typed bound
  values with unchanged authorization and invocation limits, including runtime
  validation when a producer has no declared output schema.
- [ ] Execution evidence identifies each binding's source and distinguishes raw
  model arguments from the effective delivered input. Original accepted result
  content remains inspectable under existing diagnostic policies. The caller and
  configuration generation remain attributable through the invocation.
- [ ] Existing unbound skills, `ref://` handling, ordinary input validation and
  PR 20 result forwarding retain their behavior. Binding can be combined with
  `output_from` without reintroducing parent synthesis or duplicate child-output
  validation. The public API boundary remains compliant with repository policy.
- [ ] Deterministic offline verification establishes these mechanics without
  paid provider calls. The authoring documentation accurately describes the
  implemented syntax and limits. No claim of improved live-model reliability is
  made without separately authorized evidence.

## Scope and sequencing

Implement this as a general Framework capability, not an equipment-case special
case or a model-specific workaround. The equipment scenario supplies motivation
and a representative composition, not fixed business answers to embed in prompts.
Preserve complete evidence at the receiving model; the objective is to remove
generated copying, not necessarily to reduce that model's input-token count.

No reference-store extension, automatic name-based binding inference, model-authored
binding plan, cross-session reference access, transformation DSL, multi-result
aggregation or business-reasoning correction is required for this first version.
Internal storage, schema projection, path parsing and dependency representation
are implementation choices for the pipeline, subject to the requirements above.

PR 20 is the existing output-forwarding foundation and must continue to work.
Do not alter the sibling suite's frozen prompts, schemas, evaluator, scenarios,
reference designation, replay fixtures, captures or uncommitted work. A later
explicitly selected comparison baseline can adopt the feature and evaluate it.
Paid evaluations, replay refresh, deployment, publishing and release are separate
authorizations. This ticket-writing request starts no pipeline, commit or push.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** The intent is settled, but implementation changes the authored
  manifest contract, model-facing argument schemas, input assembly, execution
  dependencies and diagnostic evidence across invocation paths. Concurrency,
  isolation and compatibility require research, planning and independent review.
- **Reassessment triggers:** Discovering that the feature requires a public API
  expansion, incompatible contract change, broader result lifetime or cross-session
  access requires explicit scope reassessment. These discoveries do not authorize
  weakening the stated boundaries or selecting a lighter route automatically.
