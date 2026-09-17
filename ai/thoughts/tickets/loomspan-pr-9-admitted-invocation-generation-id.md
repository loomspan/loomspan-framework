# PR 9 — Expose the captured generation on admitted invocations

## Outcome

Allow applications to correlate an admitted execution with the configuration it actually uses, before execution starts and without relying on a completed observer callback.

Loomspan Sidecar maps process-local framework generation IDs to application-owned durable snapshot IDs. An invocation can capture generation A, remain pending while B is published, and complete after B becomes current. Reading the current catalog would associate that invocation with the wrong snapshot. The admitted handle must expose its own captured identity.

## Requirements

- Add the supported public accessor `String generationId()` to `ai.loomspan.api.AdmittedSkillInvocation`.
- Return the exact nonblank generation ID captured during preparation for the successful handoff, for both object-input and map-input overloads. Capture occurs before conversion and validation; the value need not identify the generation current when handoff returns. Preserve that timing.
- Use the same identity as the captured generation's catalog and `RestSkillInvocation.generationId()`, including nested REST work in that invocation tree.
- The ID must remain unchanged throughout the handle's lifetime: before, during, and after execution, execution failure, release, and framework shutdown cutoff, regardless of later publications.
- Concurrent and repeated reads must be safe and have no side effects. Reading the ID must not execute, claim, release, or extend the admission. Retaining or reading it must not acquire generation ownership, delay retirement, or extend shutdown.
- Keep the implementation minimal by exposing the already-captured identity. Store the immutable ID independently of the cleared execution payload; do not add a registry, lifecycle mechanism, current-generation lookup, or fabricated fallback.
- Preserve existing authorization, nesting, single-use execution, admission ownership, release, retirement, and shutdown behavior.
- Document the accessor and correlation usage in the public API documentation, README, and reload/handoff guidance. Explain that IDs are process-local, not durable snapshot identifiers, and that reading an ID does not guarantee application-owned mapping availability after ownership ends, including cutoff.
- Correlation examples must record the application snapshot association before invocation, handle a missing mapping explicitly, and release abandoned admissions if lookup or recording fails. A `try/finally` calling `release()` can cover correlation and invocation because release is harmless after execution claims the admission.

## Acceptance criteria

- [ ] Both public handoff overloads return A's nonblank captured ID; publication of B before execution does not change it.
- [ ] The old handle executes A's captured definitions, while a new handle captured after publication returns B's ID and executes B's definitions. REST handlers, including nested REST work, receive the same ID as their admitted handle.
- [ ] Repeated and concurrent reads remain stable across execution, failure, release, and cutoff and do not consume or mutate an admission or alter execution/release outcomes.
- [ ] A retained completed or released handle still exposes its original ID after its superseded generation retires. Reading the ID adds no ownership and does not delay retirement or shutdown.
- [ ] Existing authorization, nesting, admission, single-use, release, retirement, and shutdown contracts remain intact, with focused tests and supported-public-surface integration coverage establishing these guarantees.
- [ ] Public documentation explains capture timing, lifetime stability, process-local identity, and application-owned correlation and mapping retention. Examples release abandoned admissions and handle missing mappings explicitly.
- [ ] The supported-surface architecture checks pass, affected framework implementations and test doubles are updated directly, and no additional SPI, registry, persistence contract, or compatibility fallback is introduced.

## Scope and constraints

This is a framework enhancement only. No Sidecar changes, release, durable identifier, historical invocation, persistence, or Sidecar-specific types or storage concepts are included. No change to `SkillExecutionView` is requested. The application owns durable snapshot mapping, stages the mapping before publishing or admitting traffic for a generation, and controls its retention policy.

Reuse existing handoff, reload, and lifecycle fixtures for verification rather than introducing duplicative scaffolding. Favor the simplest implementation that meets every requirement.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** The implementation direction is settled and small, but this change adds a supported public API contract. The execution-profile protocol makes that a full-pipeline trigger; permission to break compatibility does not remove it.
- **Reassessment triggers:** Unexpected changes to capture timing, ownership, or lifecycle machinery require reassessing scope and implementation direction; none are intended by this ticket.

## Pipeline notes

- The project is in development. Breaking changes needed for this enhancement are explicitly authorized. Update affected implementations and test doubles directly; do not preserve old signatures through compatibility shims, default methods, aliases, or fabricated values. Document the new abstract-method requirement for handwritten implementations. This does not create a supported bean-replacement SPI.
- PR 9 is the developer-assigned proposed identifier for this ticket, not a claim that GitHub PR 9 exists.
