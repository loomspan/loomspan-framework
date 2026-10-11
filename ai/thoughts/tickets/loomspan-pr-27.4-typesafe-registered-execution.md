# PR 27.4 — Make TypeSafe skills callable, composable, and discoverable

## Outcome

Registered TypeSafe leaves execute directly and as children within existing missions,
with consistent schemas, authorization, binding/forwarding behavior, captured
generations, and readable Console/MCP metadata.

## Context and dependencies

Phase 3 of [PR 27.1](loomspan-pr-27.1-typesafe-primitives.md), requiring
[PR 27.2](loomspan-pr-27.2-typesafe-authoring-contract.md) and
[PR 27.3](loomspan-pr-27.3-typesafe-provider-execution.md). The
[roadmap](../phases/typesafe-primitives-roadmap.md) owns the contract and binding
integration guidance; [examples](../phases/typesafe-primitives-examples.md) define
representative rejection timing and forwarding results. Sol 6.1 is the intended
coding agent. This ticket connects existing mechanisms; it adds no new binding,
forwarding, mission, or observation subsystem.

## Requirements

- Register and dispatch the leaf through captured skill generations, direct
  `SkillTemplate` invocation, and nested execution within the caller's session.
  Deliberately extend the declaration/dispatch boundaries in `ExecutionCoordinator`
  and `SkillGeneration`; reuse `MissionWorkExecutor` for mission accounting and
  cancellation coordination. A TypeSafe leaf must not enter chat planning,
  synthesis, linting, thinking defaults, output validation, or repair.
- Preserve authorization, quotas, input validation, nested tool accounting,
  provider attempts, cancellation, and write gates. Validate/resolve before attempts;
  equal accepted normalized direct/bound inputs yield equal requests, retaining
  ordinary coercion versus exact author-bound input validation. Reject chat skills
  selecting System One aliases and TypeSafe skills selecting chat aliases.
- Use the captured declaration and generation-local schema throughout each invocation.
  Reload affects new calls only. Concurrent dynamic criteria cannot mutate registered
  schemas or leak requests/results between invocations; do not cache criteria by skill name.
- Publish PR 27.2's single descriptive schema to the public catalog, tool metadata,
  and existing effective-schema map via `SkillGenerationManager.resolveOutputSchema`.
  Let `validateOutputBindings` / `checkBindingCompatibility` consume it unchanged:
  retain type/nullability/nested checks, enum intersection, integer-to-number widening,
  and open/unknown branch behavior. No binding-specific schema or static subtype proof.
- Parent output bindings retain registration compatibility checks and runtime
  destination validation. Child input bindings retain path/topology checks and actual
  receiver-input validation; do not import the parent schema comparison into them.
  Source lookup remains exact, while destination schema lookup/projection/composition
  retain existing case-insensitive behavior. Missing paths and receiver mismatches
  fail without fallback, widening, or omission. Do not extend binding array syntax.
- Normal parents may forward a TypeSafe child under existing `output_from` rules:
  `planning_mode: true`, selected direct child `required: true`, `max_tasks: 1`,
  exactly one selected task, and all accepted work completed. Preserve exclusions
  of parent `output_schema`, `output_schema_max_retries`, `linter`, and `output_bindings`.
  Inherit the child's schema and forward retained JSON text unchanged, including `{}`
  and extensions, using existing forwarding and `RESULT_FORWARDED`. No synthesis,
  repair, answer-validation gate, new wrapper, or substitute result on child failure.
- Add public `SkillKind.TYPESAFE` deliberately and update switches, decoders, README,
  allowlist/supported-surface checks, and affected consumers together. No extra
  `SkillDescriptor` fields, typed Java facade, provider-envelope API, or new SPI.
- Separately publish Console/MCP `source: TYPESAFE`, nonblank `sourcePath`, generated
  `outputSchema`, and original captured YAML in details; summaries keep their existing
  shape without YAML. Omit Java `beanName`/`method`. Reuse `YamlSkillSource` and
  `SkillSourcePathResolver` location rules unchanged, including opaque supplied
  `SkillDocument.sourceName` labels verbatim, without path normalization or synthetic URIs.
- Update the Java registered catalog/entry validation, Go summary/detail validators,
  MCP `skillSourceSchema` and tool descriptions, and affected TypeScript unions/decoders
  and UI fixtures in this delivery. Reuse working text/location renderers. Native trace
  consumers from PR 27.3 must display real direct/child execution evidence with captured
  provenance and truthful capture limits; preserve canonical content and existing journal.

## Acceptance criteria

- [ ] Direct and child calls return identical complete named-answer shapes for equivalent
  accepted input, and respect role denial, input failures, incompatible aliases, quotas,
  attempt limits, cancellation, and the existing shared-session accounting boundaries.
- [ ] Concurrent dynamic calls and a reload retain invocation-local criteria/results
  and captured generations; late responses cannot change cancelled outcomes or bypass writes.
- [ ] Catalog, tools, bindings, and forwarding parents expose the same generated schema.
  Fixed Choice disjoint enums and Score number-to-integer bindings fail registration;
  intersecting Choice enums register but incompatible actual values fail runtime validation.
  Singleton type enums and dynamic Choice receiver constraints behave as documented.
- [ ] Missing dynamic probability paths fail exact lookup; child bindings retain their
  existing checks and normalization distinction. No stronger checker, answer validator,
  or alternate schema owner is introduced.
- [ ] A focused parent-forwarding integration preserves retained child text for normal,
  empty, and extended answers, inherits schema, emits `RESULT_FORWARDED`, performs no
  final synthesis/repair, waits for accepted work, and propagates child failures.
- [ ] Console and MCP list/detail both accept discovered manifests and supplied documents,
  showing `TYPESAFE`, correct location (including an opaque label), original detail YAML,
  generated schema, no Java locations, and no detail YAML in summaries. No consumer
  waits for PR 27.5 to understand the new kind/source or emitted trace data.
- [ ] Focused representative route/entry-point integration and affected consumer checks
  pass with `LoomspanPublicSurfaceArchitectureTest`; README and supported API checks
  describe the deliberate kind addition while existing Java/REST/chat behavior is retained.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Supported kind identity, authorization, lifecycle/concurrency, and
  Java/Go/TypeScript/MCP contracts all participate in this integration boundary.
- **Reassessment triggers:** If existing schema injection appears insufficient,
  record the concrete obstacle and discuss alternatives before adding machinery or
  bypassing checks. A split must preserve complete dispatch and consumer invariants.

## Pipeline notes

- The accepted roadmap authorizes feature-required breaking replacements and the
  deliberate `TYPESAFE` kind/source addition. Update current consumers together; do
  not add compatibility shims, aliases, legacy decoding, or dual formats. Preserve
  required behavior, API discipline, and user data/history.
