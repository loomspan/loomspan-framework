# PR 27.2 — Define TypeSafe declarations and structured request contracts

## Outcome

TypeSafe declarations have one deterministic contract for registration-time checks,
input substitution, resolved-request validation, and descriptive output schemas.
Choice, Noul, Score, mixed batches, and dynamic criteria share this contract.

## Context and dependencies

Phase 1 of [PR 27.1](loomspan-pr-27.1-typesafe-primitives.md); no implementation
prerequisite. The [roadmap](../phases/typesafe-primitives-roadmap.md), especially
Phase 0 decisions, owns the exact contract. The [examples](../phases/typesafe-primitives-examples.md)
provide acceptance walkthroughs, not executed tests. Sol 6.1 is the intended coding
agent. Provider execution is PR 27.3; callable registration integration is PR 27.4.

## Requirements

- Add the TypeSafe leaf declaration through existing strict manifest parsing.
  An object-valued `typesafe` block selects it; require `name`, `description`,
  `model`, and object-root `input_schema`, and permit `rbac_roles`. Reject attachment
  schema nodes. Inside the block allow only `state` and nonempty `questions`;
  questions have literal `type`, optional `instructions`, and appropriate `criteria`.
- Reject declarations of `rest`, `prompt`, `thinking_level`, `planning_mode`,
  `concurrency`, `max_steps`, `allowed_skills`, `linter`, `output_schema`,
  `output_schema_max_retries`, `output_from`, and `output_bindings`, even when
  false, null, or empty. Preserve existing unknown-field and duplicate-key rules.
- Implement the roadmap's exact template grammar only in string values under state,
  instructions, and criteria. `${/input}` and JSON Pointer descendants support
  typed deep-copy whole-value replacement; embedded expressions accept only strings,
  finite numbers, and booleans. Use existing JSON codec number representation.
  Null/object/array interpolation fails. `$${` emits literal `${`; inserted data
  is never rescanned. Keys remain literal, and question maps/types stay fixed.
- Preserve parsed YAML text, Unicode, whitespace, explicit nulls, omission, and
  numeric values. Use JSON-library pointer support, the specified `~1`/`~0` escaping,
  exact object lookup, empty object keys, and canonical array indices. Malformed
  expressions fail registration; missing paths or invalid traversal fail invocation.
  No defaults, functions, environment/configuration expansion, or input-schema proof.
- Validate literal constraints at registration and the complete request after
  resolution against accepted normalized inputs, before a provider attempt. Reject
  non-JSON-compatible values, including non-finite numbers. Retain ordinary input
  coercion versus exact author-bound subtree validation; no second request coercion.
  Produce one immutable resolved request reusable for retries, without generation mutation.
- Follow primitive shapes: state is string/object/array; instructions may be omitted,
  null, string/object/array. Choice criteria has 1–255 entries with string/object/array/null
  descriptions. Score has 1–10 ordered levels of those same types, preserving nulls.
  Noul criteria may be omitted/null or an object with independently optional `true`
  and `false` keys; absent/null instructions require criteria describing an outcome.
  Do not impose universal instructions, two-level minima, or replacement descriptions.
- Reject sibling question IDs and same-question Choice keys that collide under
  `toLowerCase(Locale.ROOT)`, reporting both names and location. Check fixed names
  at registration and input-supplied candidates before HTTP. Never rewrite names or
  apply this restriction to arbitrary state/description objects or provider responses.
- Derive one generation-local schema using existing vocabulary. Root properties are
  declared questions, all required, with no additional root properties. Answers have
  singleton `type` enums and allow additional fields. Noul requires `type,noul`;
  Choice requires `type,choice,confidence,probabilities`; Score requires
  `type,score,confidence,probabilities,legend`. Use the roadmap's exact schema table:
  fixed Choice enums/probability keys, fixed Score numeric index keys, open dynamic
  maps, broad legend objects, and range descriptions rather than new bound keywords.
  These schemas describe results and feed existing consumers; they never validate answers.

## Acceptance criteria

- [ ] Valid leaf declarations and mixed batches parse, while incompatible fields,
  unknown fields, invalid shapes, attachments, and case collisions fail at the
  specified boundary, including null/false/empty incompatible declarations.
- [ ] The exact-resolution walkthroughs preserve structured values, escapes, Unicode,
  whitespace, nulls, and inserted literal markers; malformed syntax fails registration,
  missing optional values and invalid runtime traversal fail before provider execution.
- [ ] Fixed and dynamic Choice/Noul/Score criteria obey the same shape rules, including
  singleton Choice/Score, null Score levels, partial Noul criteria, and the Noul
  instructions/outcome requirement. No coercion, fabricated criteria, or renumbering occurs.
- [ ] Non-finite/non-JSON inputs fail; equal accepted normalized inputs resolve equally
  while ordinary versus author-bound input validation retains its existing distinction.
- [ ] Generated schemas match the mixed-batch example and fixed/dynamic schema table,
  remain unchanged across concurrent different criteria, and use existing schema
  representations suitable for current catalog/tool/binding consumers.
- [ ] Focused contract checks cover shared semantics without credentials; no generic
  template/schema infrastructure, static path proof, answer validator, or shared
  binding redesign is introduced. Production-type changes pass
  `LoomspanPublicSurfaceArchitectureTest`.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** New manifest and serialized request contracts require design and
  verification across parsing, normalization, schemas, and generation ownership.
- **Reassessment triggers:** A need for richer shared schemas or binding algorithms
  must first be checked against the selected bounded contract, not silently added.

## Pipeline notes

- Feature-required obsolete internal contracts may be replaced directly under the
  roadmap's accepted compatibility policy. Do not add shims, duplicate rules, legacy
  formats, or unrelated refactors; update affected consumers in the same change.
