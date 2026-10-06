# PR24 Direct Dispatch — Independent Review 1

## Code Review Findings

No actionable findings.

## Findings Resolved in This Context

None. This context changed no implementation artifact; only this review document was written.

## Open Questions and Assumptions

None that materially affect correctness or confidence. Full pipeline was approved. Development-stage atomic internal changes and no compatibility shims were explicitly authorized.

## Review Scope and Reconstruction

Reviewed the current unstaged implementation against HEAD on `main`; there were no staged changes. The implementation comprises 26 tracked files: six production Java types, Java tests, Console TypeScript presentation/tests, Go fixture tests, one current trace fixture, the root README and authoring guidance. Read the supplied PR24 ticket, research, implementation/testing plans and implementation receipt, but established behavior independently from production paths and tests before plan-conformance assessment. The unrelated untracked PR25 ticket was excluded and preserved. Prior review artifacts were not consulted.

`ChildInputBindingProjection` compiles eligibility alongside the projected argument contract. Raw unsupported/malformed JSON Schema vocabulary retains proof uncertainty through the resolver and projection. Closed supported object contracts qualify only when no projected root properties or required fields remain, every destination is a whole root property, and ordinary empty-argument validation succeeds. Optional unbound fields, open/generic contracts, unknown schema shapes and nested destinations remain model-dispatched. Whole bound nested values remain authoritative source data rather than model input choices.

Traced dispatch from accepted-unit preflight/admission through the assigned engine, shared validator/invocation boundary, `DefaultCapabilityInvoker`, binding assembler, receiving validator, router and coordinator. Direct actions retain exact trusted assignment, lifecycle/interruption checks, pinned generation, access checks, quotas and normal task/tool result/failure handling. Parent planning, real model-child reasoning/correction and existing completion policies remain. Binding failure cannot enter model-action repair. The scheduler, joins and outcome folding are unchanged.

Reviewed canonical step metadata, tool/provenance evidence, existing live/journal projection, current corpus and Console raw-record presentation. Framework origin is emitted by the runtime and displayed without searching for a fictitious model proposal. Content is rendered as inert text. No content classification or masking was added; the existing journal-projector exception is unchanged. Exact Console release-marker equality and missing-marker rejection remain in the unchanged processor and are covered by the Go suite.

## Verification Results

All commands below were actually run in this fresh review context. Maven used installed JDK 21 by setting process-local `JAVA_HOME=C:/hamdev/jbrsdk21` and prepending its `bin` to PATH.

- PASS — `$env:JAVA_HOME='C:/hamdev/jbrsdk21'; $env:Path="$env:JAVA_HOME/bin;$env:Path"; ./mvnw.cmd test` from repository root: BUILD SUCCESS, 1,476 tests, zero failures/errors, two existing skips in `Pr18RecordedHandoffDiagnosticTest`; duration 3:47. Includes all changed test classes, 104 engine tests, binding proof/assembly/failure and facade integrations, 16 Console corpus tests, security/lifecycle/usage/generation suites and eight `LoomspanPublicSurfaceArchitectureTest` tests.
- PASS — `go test ./...` from `loomspan-console`: all packages passed (some cached).
- PASS — `go run ./internal/buildtool verify` from `loomspan-console`: TypeScript checking, 46 browser files/525 tests with coverage, production build and all Go packages passed. Existing npm audit output and large-chunk warning did not fail verification; dependencies were not changed by this ticket.
- PASS — `git diff --check` from repository root: no whitespace errors.

## Requirements and Plan Conformance

| Requirement | Independently checked evidence |
| --- | --- |
| R1 conservative per-binding proof | Resolver/projection source and proof tests distinguish closed zero-input from unspecified, fully versus partially bound properties, optional input, open/typed extension channels, unsupported raw vocabulary and nested presence-sensitive fallback. No name/model allowlist or fabricated values exists. |
| R2 shared controls and retained reasoning | Engine direct test and nine Java/REST/model facade combinations preserve accepted planning, complete received values/results, noneligible dispatch and model-child output correction. Both paths join the same assigned invocation method and normal router. |
| R3 binding failures and provenance | Facade missing-source case makes only the planner request and no child side effect; assembler, isolation and binding trace tests cover missing/ambiguous/incompatible sources, exact values and provenance. Invocation selects current owning input and accepted prior-unit results. |
| R4 lifecycle/security/scheduling | Direct validation-boundary cancellation/interruption tests prevent side effects; facade revoked-authority and approval-denial scenarios fail normally. Direct/mixed group, timeout, cutoff, admission, quota and generation suites pass. No new side-effect retry or scheduler change exists. |
| R5 honest evidence/hooks/accounting | Trusted step origin/reason, no direct model/proposal frame, normal tool lifecycle and real child calls are asserted. Facade observer delivery remains once on success/failure. Current Java/Go corpus and browser tests accept missing direct model frames and display emitted dispatch facts. Request-specific hooks only apply to actual requests. |
| R6 documentation and API | Routed guidance covers automatic rule, fully bound producer, optional/nested consumer counterexample, child reasoning, independent authorization and accounting. Complete three-file example loads. Supported API/SPI allowlists are unchanged and architecture checks pass. |
| Mixed call-count demonstration | Facade matrix asserts one planner, zero eligible-producer parent requests, one consumer parent dispatch, two actual model-producer reasoning/correction requests and one model-consumer request when applicable, with exact result/value assertions. Evidence integration sourceA request counts decrease by one while evidence assertions hold. |

Partial or missing criteria: none. Safe deviations: nested destinations conservatively retain model dispatch; deterministic validated-event cancellation replaces the proposed latch; immutable captured capability ownership and existing generation tests establish pinning without adding a new reload experiment. These are adequately supported by source and executable evidence. Historical failing-test receipts were not treated as fresh verification; current behavioral checks were rerun as part of the full suite.

Compatibility review: production signature changes are internal. Supported `SkillTemplate`/observer and sole `RestSkillHandler` SPI signatures remain unchanged. Manifest/configuration semantics were assessed; automatic removal of eligible parent requests, hooks and usage is the ticket's intentional behavior change. Existing current-run step metadata is extended with no new canonical enum or REST/SSE DTO, historical reader or marker-policy change. Java/Go/TypeScript current consumers move together. No shim, new public surface or bean replacement SPI was introduced.

## Skill-Authoring Documentation Impact

- **Assessment:** Affected.
- **Rationale:** Automatic dispatch, conservative proof exclusions, child reasoning, provider-hook/accounting changes and step diagnostics affect authors.
- **Documents reviewed:** root `README.md`; `agent-skills/loomspan-docs/SKILL.md`; `references/skill-authoring/README.md`, `source-verification.md`, `input-bindings.md`, `planning-concurrency.md`, `execution-limits.md`, and `traces-and-debugging.md`, all from this checkout. The router's version matches `1.0.0-beta.8-SNAPSHOT`.
- **Evidence checked:** projection/resolver/validator, engine, invoker/assembler/router/coordinator/lifecycle, provider accounting boundaries, facade/trace/observer and current Console tests/fixtures.
- **Coverage table:** Current; bindings, planning and debugging routes cover the new semantics.
- **LLM-first usability:** Pass; routed rule and decision table are self-contained, complete examples are executable, and adjacent accounting/debugging guidance is linked.
- **Drift classification:** Aligned. No material documentation/source conflict remains in the reviewed additions.

## Residual Risks and Optional Developer Checks

No optional developer checks are required. Paid providers, sibling reference-suite runs and race-mode expansion were not run; the ticket explicitly permits deterministic verification and does not require those checks. Tests establish call reduction and retained controls, not timing, dollar or live-model reliability improvement. Existing best-effort interruption does not guarantee cancellation of external application side effects.

## Disposition

**Approve** — zero actionable findings at any priority; fresh verification is sufficient. `REVIEW_RESULT: clean`.
