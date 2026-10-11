# PR 27.5 — Complete the supported TypeSafe authoring and inspection experience

## Outcome

Authors can configure, discover, invoke, compose, and investigate TypeSafe skills
using complete supported documentation and the existing Console/MCP surfaces.

## Context and dependencies

Phase 4 of [PR 27.1](loomspan-pr-27.1-typesafe-primitives.md), following
[PR 27.4](loomspan-pr-27.4-typesafe-registered-execution.md) and its prerequisites.
The [roadmap](../phases/typesafe-primitives-roadmap.md) is the agreed contract;
[examples](../phases/typesafe-primitives-examples.md) are design inputs to turn into
verified authoring examples. Sol 6.1 is the intended coding agent. PR 27.3 already
delivers native trace rendering, and PR 27.4 delivers kind/source/catalog consumers.
This ticket completes guidance and verifies the joined experience rather than
deferring prerequisite wire compatibility or redesigning those surfaces.

## Requirements

- Update README, versioned authoring guidance, and relevant consumer documentation
  for the implemented declaration and configuration. Provide complete direct TypeSafe
  and OpenRouter setup and switching an alias's connection without changing skill
  questions/input contract; send configured model IDs verbatim and explain that
  route-specific IDs may also need changing. Keep credentials in connection configuration.
- Provide complete direct and nested examples covering Choice, Noul, Score, mixed
  batches, fixed/dynamic criteria, bindings, and a normal parent forwarding a TypeSafe
  child. Use the supported schema subset, valid names/aliases, and actual implemented
  invocation surfaces; illustrative candidate labels and business actions are not defaults.
- Explain exact template scope, typed substitution versus scalar interpolation,
  escaping, null/missing data, single-pass resolution, fixed question names/types,
  collision rules, and registration versus invocation errors. Explain primitive
  optional fields, 1–255 Choice candidates, and 1–10 Score levels including nulls;
  local Score acceptance does not guarantee provider acceptance of disputed forms.
- Document named complete answers as JSON text, no `answers` wrapper, descriptive
  schemas rather than answer validation, dynamic-schema limits, binding paths and
  existing receiver checks, unchanged-text forwarding, and provider metadata in
  diagnostics. Distinguish distribution confidence from correctness and Noul probability.
- Explain both routes' shared retry ownership, concrete implemented Apache timeout
  settings/defaults, cancellation limits, response acceptance independent of capture,
  exact/unavailable usage, response-based model-call accounting, and billing limitations.
  Report truncated/disabled capture honestly. Do not imply exactly-once inference,
  identical judgments, unlimited responses, or verified live parity from fixtures.
- Confirm TypeSafe identity and captured evaluations remain usable in existing catalog
  and trace-detail views and MCP. Reuse existing JSON/text rendering and source locations;
  no authoring UI, evaluation dashboard, probability visualization, or metadata API.
- Preserve scope: no routing, confidence gates, composite scoring, automated actions,
  generalized template/schema systems, stronger bindings, new quotas, or new SPI.
  Apply canonical content fidelity and retain only the existing derived-journal exception.

## Acceptance criteria

- [ ] Published guidance provides valid complete configuration and declaration examples
  for both routes and all three primitives, with direct invocation, child bindings,
  mixed/dynamic criteria, and parent forwarding using supported public surfaces.
- [ ] Examples agree with implemented parsing/configuration and representative
  deterministic integration evidence; changing connection route preserves skill content.
  Documentation clearly distinguishes illustrative judgments, fixtures, and any live checks.
- [ ] Authors can predict substitution/errors, primitive optionality/cardinality,
  complete-answer return shape, descriptive schema limits, binding rejection timing,
  and unchanged-text forwarding from the guidance without inferring new behavior.
- [ ] Console/MCP discovery and details expose the current kind/source, original YAML,
  locations, and schema; trace inspection shows native request/response/failure evidence
  with honest capture state through the already-delivered consumers.
- [ ] Operational guidance matches actual retry/accounting/timeout/cancellation and
  response-size behavior, preserves fidelity, and makes no unsupported API or deferred
  feature promises. Any production-type changes pass `LoomspanPublicSurfaceArchitectureTest`.

## Execution profile

- **Recommended:** Fast-Track 2-Step Pipeline — Implementation & Review
- **Confidence:** medium
- **Rationale:** This final phase documents and verifies settled, already-delivered
  behavior using existing views. Its intended scope changes no external or lifecycle
  contract, but independent review should check example and consumer consistency.
- **Reassessment triggers:** New serialized/API behavior, missing prerequisite consumer
  integration, authorization/lifecycle fixes, or material product uncertainty requires
  Full 5-Step Pipeline reassessment rather than broadening this completion ticket silently.
