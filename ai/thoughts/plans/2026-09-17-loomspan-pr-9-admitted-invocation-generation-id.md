# PR 9 — Admitted invocation generation ID implementation plan

## Overview

Expose the generation actually captured during handoff so application code can record its own durable snapshot association before executing a pending invocation. The handle will retain only the captured immutable ID outside its clearable execution payload. This is a supported API addition, with one abstract method and no extra extension point.

## Current State Analysis

Both handoff overloads call `DefaultSkillTemplate.prepareObject` or `prepareMap`, which capture a generation and owner lease before conversion or validation, then construct the same admitted handle (`src/main/java/ai/loomspan/internal/skillapi/DefaultSkillInvocationHandoff.java:28-64`; `src/main/java/ai/loomspan/internal/skillapi/DefaultSkillTemplate.java:114-177`). The handle holds prepared input in an `AtomicReference<Payload>` that execution, release, or cutoff clears (`DefaultSkillInvocationHandoff.java:66-108`). `AdmittedSkillInvocation` exposes only invoke and release today (`src/main/java/ai/loomspan/api/AdmittedSkillInvocation.java:12-35`). A lookup of the active catalog at handoff return or invocation time could report a newer generation than the captured one.

The captured `SkillGeneration.id()` is nonblank and is also the value in its catalog and REST invokers (`src/main/java/ai/loomspan/internal/skill/SkillGeneration.java:19-52,65`; `SkillGenerationManager.java:101-140`). The runner and nested execution binding carry that generation (`LoomspanSessionRunner.java:274-338`). Lease ownership and retirement remain in `FrameworkExecutionLifecycle` and `SkillGenerationManager`; the ID string itself needs no ownership (`FrameworkExecutionLifecycle.java:309-352`; `SkillGenerationManager.java:211-238`).

## Desired End State

`AdmittedSkillInvocation.generationId()` returns the exact nonblank captured ID from successful object and map handoffs, including after payload clearing, failure, release, retirement, and shutdown cutoff. Reads are thread safe, side effect free, and do not retain a generation lease. Existing invocation semantics remain unchanged. Public Javadoc, README, and version-aligned Java API guidance explain capture timing, process-local identity, mapping ownership, and safe correlation before invocation.

### Key Discoveries

- The handle constructor receives `PreparedInput`, so `prepared.generation().id()` is available before its payload can be cleared (`DefaultSkillInvocationHandoff.java:70-83`).
- `PublicSkillReloadIntegrationTest` already stages application-owned REST configuration by candidate ID and verifies old admissions survive publication while retirement waits for release (`src/test/java/ai/loomspan/integration/PublicSkillReloadIntegrationTest.java:35-87,242-310`).
- `SupportedSurfaceIntegrationTest` already executes a handed-off YAML root whose plan invokes a nested REST leaf; its handler records the received `RestSkillInvocation` (`src/test/java/ai/loomspan/integration/SupportedSurfaceIntegrationTest.java:42-77,105-160,255-275`).
- `ApplicationApiValueTest` checks the exact admitted interface method set; `LoomspanPublicSurfaceArchitectureTest` classifies the containing type as supported API (`src/test/java/ai/loomspan/api/ApplicationApiValueTest.java:50-66`; `src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java:28-48`).

## What We're NOT Doing

No Sidecar code, durable generation IDs, persistence or registry, historical invocation lookup, `SkillExecutionView` change, current-generation lookup, extra SPI, bean replacement contract, compatibility fallback, or change to admission, authorization, nesting, retirement, or shutdown machinery.

## Skill-Authoring Documentation Impact

**Impact**: No impact.

- **Rationale**: This accessor is for application code holding a root admission and managing application-owned snapshot correlation. It does not change manifest syntax, skill inputs, skill-author-visible execution semantics, capability visibility, trace/debugging guidance, or authoring tests. Matching executable paths are the handoff handle and public API tests above.
- **Documents to update**: None in `agent-skills/loomspan-docs/references/skill-authoring/`.
- **Supporting evidence**: `AdmittedSkillInvocation.java`, `DefaultSkillInvocationHandoff.java`, and the public handoff/reload integration fixtures. The version-aligned Java API knowledge set currently agrees with source on capture and reload behavior; the new accessor is absent in both, as expected before implementation. Drift classification: **aligned** for existing behavior.
- **Coverage table update**: Not required for the skill-authoring README; update the Java API knowledge-set coverage description only if its invocation entry needs to mention the new handle contract.
- **LLM-first usability**: Not applicable to skill-authoring topics. Keep Java API invocation and reload guidance routed, concise, and explicit about the application's mapping responsibility.

## Contract and Compatibility Impact

| Surface | Impact and supporting evidence | Planned compatibility treatment |
| --- | --- | --- |
| Application API | Add `String generationId()` to supported `AdmittedSkillInvocation`; exact method shape checked by `ApplicationApiValueTest` and type allowlisted by `LoomspanPublicSurfaceArchitectureTest`. Handwritten implementations and fakes must implement the new abstract method. | Intentional pre-1.0 source and binary sensitive addition authorized by ticket Pipeline notes; update in-repo implementation/tests directly and document recompile requirement. |
| Supported SPI | `RestSkillHandler` remains the only supported SPI; `RestSkillInvocation.generationId()` is existing metadata. | Preserve handler signature and behavior. |
| Configuration and manifest contracts | No property, YAML, validation, or author-facing rule changes. | Preserve existing contracts. |
| Persisted or serialized contracts | IDs remain process-local; no durable or wire format change. | Do not add persistence semantics. |
| Ephemeral diagnostic formats | No trace writer, reader, or projection change. | Preserve current diagnostic behavior. |
| Internal or accidentally exposed implementation | Internal admitted handle gains one final string field; no lifecycle state change. | Update atomically; do not preserve internal signatures for compatibility. |

- **Evidence of supported contracts**: Root `AGENTS.md`, closed architecture allowlist, README supported API list, public API source, and the ticket.
- **Intentional compatibility changes**: New abstract method requires handwritten `AdmittedSkillInvocation` implementations to update and recompile. The ticket explicitly authorizes this without default method, shim, alias, or fabricated fallback.
- **In-repository consumers to update**: Internal admitted implementation; exact method-shape test; focused handoff, reload, lifecycle, and supported-surface tests; API Javadoc; README; `agent-skills/loomspan-docs/references/java-api/invocation.md`, `skill-reload.md`, and `compatibility-and-boundaries.md`; adjust Java API README coverage entry if useful. Search for additional handwritten implementations before editing; current source inventory found only `DefaultAdmittedSkillInvocation`.
- **Public-surface delta**: One `String generationId()` method on an already allowlisted interface. No new type, constructor, Spring extension point, or internal type in a public signature. The architecture type allowlist remains nineteen types.
- **Shim decision**: **No shim.** The ticket explicitly authorizes updating implementations directly. A default method or fallback cannot faithfully supply the captured ID.
- **Java-to-Go boundary coordination**: **Not required.** No application-adapter REST/SSE, acquisition, problem, or consumed NDJSON format changes; `RestSkillInvocation` is the existing Java callback metadata.
- **Pipeline notes alignment**: **Aligned.** The only planned break is the ticketed abstract method; no wider compatibility change is planned.

## Implementation Approach

Use `PreparedInput.generation().id()` once in the admitted-handle constructor, assign it to a `final String` independent of `payload`, and return it directly. A final immutable field permits concurrent reads without locks and cannot claim or retain admission ownership. Keep `Payload`, lease, root, and all invoke/release paths as they are. Alternatives of querying the current catalog, retaining a generation object, or adding a registry would report the wrong identity or couple ID reads to lifecycle state.

## Phase 1: API and immutable handle identity

### Changes Required

1. **Public API** — `src/main/java/ai/loomspan/api/AdmittedSkillInvocation.java`: add abstract `String generationId()` with Javadoc specifying nonblank captured preparation identity, lifetime stability, process-local scope, side effect free reads, and no mapping-retention guarantee.
2. **Internal implementation** — `src/main/java/ai/loomspan/internal/skillapi/DefaultSkillInvocationHandoff.java`: set `private final String generationId` from `prepared.generation().id()` in the handle constructor before registering termination; implement a plain getter. Do not retain `PreparedInput` or `SkillGeneration` through that new field.
3. **Method boundary** — `src/test/java/ai/loomspan/api/ApplicationApiValueTest.java`: update exact method-set assertion for `generationId(): String`. Confirm no other public method or SPI appears.

### Success Criteria

#### Automated Verification

- [x] A pre-fix focused test fails because the accessor is absent; after implementation it compiles and passes.
- [x] `./mvnw.cmd --batch-mode --no-transfer-progress -Dtest=ApplicationApiValueTest,LoomspanPublicSurfaceArchitectureTest test` passes.

## Phase 2: Behavior and lifecycle evidence

### Changes Required

1. **Reload and REST correlation** — extend `src/test/java/ai/loomspan/integration/PublicSkillReloadIntegrationTest.java` to assert map and object handoffs return their captured candidate/catalog ID, remain A after B publication, execute A while new B work executes B, and match the handler's received ID. Keep the existing retirement fixture and assert a retained released/completed handle still reports A after retirement and repeated reads do not delay retirement.
2. **Preparation timing and authorization** — extend `src/test/java/ai/loomspan/internal/skillapi/SkillGenerationExecutionIntegrationTest.java` using its conversion-time publication fixture to assert an object handoff still reports A although B is active when handoff returns. Preserve exact validation and authorization outcomes. The existing captured-generation execution test should also compare its handle ID with the execution binding.
3. **Cleared payload and shutdown** — extend `src/test/java/ai/loomspan/internal/skillapi/DefaultSkillTemplateTest.java` and, where needed, `src/test/java/ai/loomspan/internal/core/FrameworkShutdownIntegrationTest.java` to assert stable reads after success, failure, release and cutoff, and that reads do not claim an admission or change the existing pending execution and shutdown results. Add bounded concurrent reads on the same handle to prove stable access while state changes, with no sleeps as the oracle.
4. **Nested supported surface** — extend the existing `src/test/java/ai/loomspan/integration/SupportedSurfaceIntegrationTest.java` to compare a handed-off YAML root's ID with the nested REST handler's observed `RestSkillInvocation.generationId()` before the fixture overwrites its last-invocation slot.

### Success Criteria

#### Automated Verification

- [x] Focused tests cover every ticket acceptance criterion and retain existing authorization, single-use, nesting, retirement and cutoff assertions.
- [x] `./mvnw.cmd --batch-mode --no-transfer-progress -Dtest=ApplicationApiValueTest,LoomspanPublicSurfaceArchitectureTest,DefaultSkillTemplateTest,SkillGenerationExecutionIntegrationTest,PublicSkillReloadIntegrationTest,SupportedSurfaceIntegrationTest,FrameworkShutdownIntegrationTest test` passes.

## Phase 3: Consumer documentation and full verification

### Changes Required

1. **README** — update the handoff example at `README.md:169-187` to record the application snapshot association before `invoke`, explicitly handle missing mapping, and use `try/finally { admitted.release(); }` across correlation and invocation. Explain A-to-B publication timing, immutable process-local ID, and mapping retention at `README.md:196-221`.
2. **Version-aligned Java API guidance** — update `agent-skills/loomspan-docs/references/java-api/invocation.md` handoff example and lifecycle prose; `skill-reload.md` for staging and retention under application control; `compatibility-and-boundaries.md` for the abstract method and handwritten-implementation recompile requirement. Keep code examples application-owned and explicitly fail on missing mapping. Update the Java API index coverage wording if its invocation description otherwise misses the new contract.
3. **Review for stale claims** — ensure no example suggests `reloader.snapshot().generationId()` after handoff identifies an already captured invocation or implies retaining a handle keeps an application map available after retirement/cutoff.

### Success Criteria

#### Automated Verification

- [x] Javadoc, README, and routed Java API guidance agree with verified implementation and tests; no skill-authoring topic change is needed.
- [x] `./mvnw.cmd --batch-mode --no-transfer-progress test` passes, including `LoomspanPublicSurfaceArchitectureTest`.
- [x] `git diff --check` passes; inspect diff to ensure the pre-existing unrelated `pom.xml` formatting change is preserved.

## Testing Strategy

Start with a failing accessor test, then cover both overloads, conversion-time publication, REST root/nested ID equality, lifecycle stability, and absence of extra ownership. Use existing fixtures as described in the companion testing plan. Full Maven tests and architecture checks are completion gates.

## Performance Considerations

The accessor is one immutable field read. It must not lock the generation monitor, query the catalog, allocate registry state, or perform lifecycle work.

## Migration Notes

Application handwritten implementations or fakes of `AdmittedSkillInvocation` must implement the new abstract method and recompile. IDs are process-local; applications must stage and retain their own mapping to durable snapshots before admitting traffic and according to their own retention policy. Reading a retained handle after ownership ends remains possible even when its application mapping has been removed.

## References

- Ticket: `ai/thoughts/tickets/loomspan-pr-9-admitted-invocation-generation-id.md`
- Research: `ai/thoughts/research/2026-09-17-loomspan-pr-9-admitted-invocation-generation-id.md`
- Design lens: `ai/thoughts/framework-feature-design-lens.md`
