# PR 13 Configurable Provider Request Timeout Implementation Plan

## Overview

Add the supported connection transport property `loomspan.connections.<name>.request-timeout` for OpenAI (including OpenRouter), Anthropic, and Gemini. It bounds each physical HTTP call through complete response consumption, while the mission deadline continues to bound the entire execution. This is pipeline mode, selected profile `full`, confirmed by the developer. Implementation belongs to Step 4; this artifact makes no production changes.

Baseline: `main`, `e35ab4488e5cac5e1cc6fef5f0f0797b5deb703d`, version `1.0.0-beta.8-SNAPSHOT`. Tracked files were clean; the developer ticket and pipeline research are untracked artifacts to preserve.

## Current State Analysis

- `LoomspanProperties.java:128` validates connections, but `ConnectionProperties` at line 441 has no timeout. Strict startup binding rejects the new property today.
- `ExecutionConfigurationParser.java:27` separately allowlists authored connection keys; binding and initialization validation at line 122 reuse the startup properties.
- `SpringAiProviderIntegration.java:134`, `:158`, and `:170` construct the actual OpenAI, Anthropic and Google clients. All disable native retries. OpenRouter's interceptor at line 213 consumes and inspects the original response body under the transport deadline.
- Resolved Spring AI 2.0.0 OpenAI/Anthropic options default to 60 seconds. Their Stainless transport maps request duration to OkHttp call timeout and inherited read/write limits; connect remains 60 seconds. Google GenAI 1.58.0 uses optional integer milliseconds for OkHttp call timeout and leaves it unset on omission.
- All three actual call-timeout boundaries are integer milliseconds, zero means unlimited, and the maximum is `Integer.MAX_VALUE`. See research's precise representation section and resolved OkHttp 4.12.0 `Util.checkDuration`.
- `ExecutionRuntime.java:56` explicitly copies each connection. `SkillGenerationManager.java:112`, `:128`, `:148`, `:163` already support skill-only retention, both credential preparations, client-free validation and captured generation settings.
- `ProviderAttemptCallAdvisor.java:65` owns attempts, quota, unchanged-request retries and interruptible backoff. `MissionLifecycle.java:25` owns logical cutoff and physical cleanup. These mechanisms are reused, not redesigned.

## Desired End State

The property works through startup and existing `ExecutionConfiguration` publication. Local HTTP tests prove configured success and whole-call expiry for delayed headers and active body chunks. Old admitted roots, delayed work, descendants and retries retain old settings/clients after publication; new roots use new settings; skill-only publication retains the active value.

### Precise authored duration contract

- Store a nullable `Duration` with no initializer. Omission preserves driver defaults: OpenAI/Anthropic 60 seconds; Gemini unset call limit; Ollama unchanged.
- Accept positive **exact whole milliseconds** from `1ms` through `2147483647ms`, inclusive (24 days, 20 hours, 31 minutes, 23.647 seconds). Spring duration syntax remains available, including exact ISO-8601 durations. This is one common representable range across supported clients.
- Reject zero, negative, malformed, fractional-millisecond, sub-millisecond and above-maximum durations. Fractional values above 1ms are also rejected; silent precision loss is unnecessary for a newly introduced contract. Validate by `Duration` comparison against bounds and `getNano() % 1_000_000 == 0` before any `toMillis()` conversion, so huge values cannot overflow first.
- Reject any explicit timeout on Ollama with its full property path and unsupported-driver explanation. Do not set a substitute read timeout.
- Known semantic validation errors include `loomspan.connections.<name>.request-timeout` and a safe explanation; malformed binding reports the same path without echoing the authored value. `SkillReloader.validate` exposes the safe field error without resolving credentials. Preserve the existing host-map `prepare` security wrapper: it intentionally gives a generic failure for non-credential exceptions rather than leaking a cause or supplied secrets. Applications obtain actionable candidate errors from validation; preparation still rejects atomically.

### Key discoveries and design choices

Reuse nullable binding, strict parser, explicit snapshot copy, existing SDK builders, generation ownership and Framework retries. A single host-authored connection budget is understandable and cannot become model-controlled request content. Transport ownership makes this a framework responsibility even for capable models; no skill-level forwarding or model workaround solves an inaccessible SDK deadline. No dead or obsolete production abstraction was identified that needs removal within this scope.

## What We're NOT Doing

Ollama transport changes, separate connect/read/write keys, skill/model timeout overrides, provider retry/cancellation redesign, a new Java API/SPI or bean override contract, response JSON/schema changes, Console protocol changes, Sidecar source edits, paid provider calls, or claiming final-synthesis success from a timeout test.

## Skill-Authoring Documentation Impact

**Impact: Affected.** Connection authors need timeout selection, driver defaults, precision/range, mission/retry distinction and publication retention.

- **Documents to update:** `README.md`; `agent-skills/loomspan-docs/references/skill-authoring/model-selection-and-connections.md`; its index `README.md`; `agent-skills/loomspan-docs/references/java-api/skill-reload.md`.
- **Supporting evidence:** properties/parser boundary tests, actual provider HTTP tests, public reload tests and mission/attempt tests named in the testing plan.
- **Coverage table update:** required; extend model selection/connections coverage with provider call budgets and supported precision/defaults. Update Java publication coverage wording if it explicitly enumerates settings.
- **LLM-first usability:** keep precise connection semantics in the connections topic and publication ownership in the reload topic, linked together. Use a compact driver/default table, explicit enforced limits and partial examples. Name stable source/test anchors, not volatile line numbers in distributable guidance.
- **Drift classification:** aligned at baseline; docs and code both omit the unsupported property, agree on no `spring.ai.*` inheritance, disabled native retries and captured clients. Adding the new property requires atomic documentation updates rather than treating absence as existing drift. The local docs skill and checkout both identify beta.8-SNAPSHOT.

## Contract and Compatibility Impact

| Surface | Impact and supporting evidence | Planned compatibility treatment |
| --- | --- | --- |
| Application API | Existing allowlisted `ExecutionConfiguration`, `SkillReloader`, `PreparedSkillUpdate` and invocation APIs used unchanged | Preserve signatures and publication guarantees; no new type |
| Supported SPI | `RestSkillHandler` remains sole SPI; no handler behavior change | Preserve; add no replacement seam |
| Configuration and manifest contracts | Add connection key to startup and authored candidates; ticket defines defaults, range and unsupported Ollama boundary | Additive setting; omission and all existing credential/driver/session behavior preserved; no migration |
| Persisted or serialized contracts | Authored candidate YAML accepts new field; no durable runtime/trace schema or response JSON change | Preserve existing reference serialization and credential rules; no historical readers |
| Ephemeral diagnostic formats | Genuine timeout category, physical attempt evidence and cutoff ownership remain authoritative | Verify current-run classification, counts, primary failure and late-write fencing; no trace schema change |
| Internal or accidentally exposed implementation | Binding accessors, SDK builder/helper and explicit runtime copy change | Update atomically; public modifiers on internal/binding types do not require shims |

- **Supported-contract evidence:** ticket requirements, AGENTS.md, `LoomspanPublicSurfaceArchitectureTest`, README connection/publication guidance and public reload tests.
- **Intentional compatibility changes:** none for existing valid configurations. Explicit use of a previously unsupported key gains validation semantics.
- **In-repository consumers to update:** properties/parser tests, provider HTTP and attempt tests, publication tests, runtime-copy evidence and consumer docs. Existing fixtures without timeout remain valid and protect omission.
- **Public-surface delta:** none; internal/binding accessors only, no new Spring extension point or supported signatures.
- **Shim decision: No shim.** No protected signature or prior valid property is removed; preserving an internal no-argument timeout helper solely for compatibility is unnecessary. Update its test callers coherently if its signature changes.
- **Java-to-Go boundary coordination: Not required.** No application-adapter REST/SSE, acquisition, problem, NDJSON or compatibility marker changes. Sidecar consumes the updated Framework configuration behavior through its dependency; downstream verification remains required.
- **Pipeline notes alignment: No notes.** The scope and Ollama/Sidecar exclusions are explicit requirements; no intentional break is inferred.

## Implementation Approach

Prefer the existing SDK timeout facilities over a new asynchronous wrapper, watchdog or client factory. Setting OpenAI/Anthropic `optionsBuilder.timeout(duration)` reaches the actual client, raises inherited read/write inactivity limits with the call budget and leaves connect unchanged. Gemini sets `HttpOptions.timeout(Math.toIntExact(duration.toMillis()))` together with `attempts(1)` before either authentication branch. Omitted values must not call the timeout setter. No retry or mission code should require production changes; test evidence determines whether an SDK limitation requires the ticket's scope escalation.

## Phase 1: Configuration Contract and Captured Settings

### Changes Required

1. `src/main/java/ai/loomspan/autoconfigure/LoomspanProperties.java`: nullable `requestTimeout`, accessor pair and safe validation during `validateConnections`. Separate unsupported Ollama validation from duration range/precision errors; apply it to explicit values only. Add timeout to safe diagnostic representation if useful without adding secrets.
2. `src/main/java/ai/loomspan/internal/skill/ExecutionConfigurationParser.java`: allowlist `request-timeout`; reuse existing Binder and validated properties. Preserve strict unknown keys and credential-reference-only authored input.
3. `src/main/java/ai/loomspan/internal/skill/ExecutionRuntime.java`: `connection.setRequestTimeout(original.getRequestTimeout())` in the explicit copy. Duration is immutable; no mutable transport state is shared.
4. Boundary tests: startup/context binding and parser validation for exact units/range, omission, Ollama, malformed values and safe paths; runtime snapshots and skill-only retention.

### Automated Success Criteria

- [x] Boundary-focused tests pass with all supported drivers and both Gemini modes.
- [x] Validation/preparation make no local endpoint calls; invalid candidates leave generation ID unchanged.
- [x] Runtime copies detach from subsequent mutation of startup properties.
- [x] `./mvnw.cmd --batch-mode --no-transfer-progress '-Dtest=LoomspanPropertiesTest,ExecutionConfigurationParserTest,SkillGenerationManagerTest,LoomspanPublicSurfaceArchitectureTest' test` passes.

## Phase 2: Actual Provider Propagation and Lifecycle Evidence

### Changes Required

1. `src/main/java/ai/loomspan/internal/springai/SpringAiProviderIntegration.java`: conditional timeout on OpenAI/Anthropic options; Gemini `HttpOptions` carries optional timeout and one SDK attempt before both auth paths. Keep OpenRouter interceptor and all headers/credentials/options intact.
2. Extend actual adapter tests, preferably a focused `ProviderRequestTimeoutIntegrationTest` beside `SpringAiProviderIntegrationTest`. Call the production adapter `chatModel().call(Prompt)` against local valid OpenAI, Anthropic and Google responses. Cover OpenAI standard/OpenRouter and Gemini API-key/Vertex modes. Existing Google default-base-URL override is test-only and must reset in finally and run without concurrent overriding tests.
3. Use a generated local Vertex service account and cached token or local token endpoint for real Vertex HTTP calls. Do not contact Google OAuth or add a production Gemini base-url property. If SDK test credential construction needs an internal fixture seam, keep it internal and bounded.
4. Extend `ModelAttemptCallAdvisorIntegrationTest` with real timeout-then-success/exhaustion cases, unchanged bodies, exact counts/quota/trace categories and disabled native retries.
5. Extend public reload integration with delayed admitted old root vs new root budgets, repeated skill-only updates, both credential paths and captured descendant/retry behavior. Use different budgets and controlled responses so behavior, rather than just a getter, proves client capture.
6. Add actual-provider mission-cutoff and caller-interruption cases with provider budget longer than mission budget. Observe logical failure, no later retries/work and no late success, then release/await physical work before asserting retirement/cleanup.

### Automated Success Criteria

- [x] Actual adapters succeed within a configured budget and expire beyond it for both header delays and active response-body delivery.
- [x] Per-attempt timeout, mission deadline, caller interruption and cancellation remain distinguishable.
- [x] New/old generation and skill-only behavior pass without changing public signatures or lifecycle logic.
- [x] Run the test-plan adapter/lifecycle command, then architecture test after production changes.

## Phase 3: Consumer Guidance and Downstream Verification Record

### Changes Required

Update the listed README and routed references in the same change. Document whole-call vs connect/inactivity/mission, complete body consumption, OpenRouter keepalives, inherited OpenAI/Anthropic read/write limits, supported exact precision/range, per-driver omission, Ollama rejection and no server/proxy override promise. Show `request-timeout: 240s` under an OpenRouter connection and `mission-timeout: 600s` under session; label it a partial fragment requiring model aliases and skills. Explain that all calls/work/backoff consume the mission and multiple full budgets are not guaranteed. Authored publication examples use credential references and both preparation paths; preserve the host-map failure redaction caveat.

Record the actual build identity and controlled embedded-host result in the implementation verification artifact. A public-API `ApplicationContextRunner` invocation with the real OpenRouter adapter supplies controlled embedded-host evidence; distinguish it from running the retained external reproduction application. Inspect available downstream release/dependency metadata without modifying Sidecar source or retained configs. If no updated Sidecar artifact containing the changed Framework is available, mark equivalent hosted configuration/publication and delayed-response tests **pending** on the Framework release/build and Sidecar dependency/release update. Do not treat old beta.7/beta.2 artifacts as verification of this change. Any host-specific rejection becomes an evidence-backed follow-up. Full paid-model workflow/final synthesis remains a separate unrun check.

### Automated Success Criteria

- [x] Consumer docs explain every new enforced behavior with focused source/test evidence, and routed coverage is current.
- [x] `./mvnw.cmd --batch-mode --no-transfer-progress clean verify` passes (includes ordinary Java tests and configured checks).
- [x] `git diff --check` passes; independent Step 5 review remains required.
- [x] Downstream result identifies exact Framework artifact and Sidecar dependency, explicitly separating passed embedded evidence from pending hosted checks.

## Testing Strategy

See `2026-10-01-pr-13-configurable-provider-request-timeout-testing.md` for exact scenarios and commands. Tests start with a failing binding/parser acceptance test before production changes; actual adapters then supply meaningful deadline evidence. No minute-long default wait or credentials are required. Existing retry/cutoff tests remain regression evidence; add real HTTP integration for the new budget interactions rather than duplicating every lifecycle test.

## Performance Considerations

No new per-call scheduler or buffering is required. OpenRouter's existing bounded capture remains unchanged. Raising a call budget permits longer physical work but does not raise quotas, mission limits or client retirement lifetime beyond existing captured physical ownership. HTTP tests use short budgets and bounded teardown.

## Migration Notes

Existing configurations require no migration. Consumers opt in on each supported connection. Publishing a replacement changes new roots; captured trees retain their clients and budgets. Sidecar requires a dependency/release consuming the updated Framework. No Console compatibility marker changes.

## References

- Ticket: `ai/thoughts/tickets/loomspan-pr-13-configurable-provider-request-timeout.md`.
- Research: `ai/thoughts/research/2026-10-01-pr-13-configurable-provider-request-timeout.md` (resolved SDK source chain and test anchors).
- Design lens: `ai/thoughts/framework-feature-design-lens.md`; AGENTS.md.
- Existing patterns: `PublicSkillReloadIntegrationTest#pendingRootKeepsItsConnectionAcrossPublication`, `#pendingRootKeepsProviderRetryPolicyAfterPublication`; `SpringAiProviderIntegrationTest#googleDirectInvocationMakesOneHttpAttemptOnRetryableFailure`; `ModelAttemptCallAdvisorIntegrationTest#recordsTranslatedOpenAiReadTimeoutWithAttemptLocalStackBeforeSuccessfulRetry`.

## Step 4 implementation and verification record

Implemented the nullable connection Duration, safe pre-conversion range/precision validation, strict candidate key, explicit captured-runtime copy and conditional real SDK client timeout propagation. No production retry, mission, publication ownership, response-inspection or authentication logic changed. No supported Java signature, public type allowlist or Spring extension point changed; no compatibility shim is needed. Tests use actual adapters and fake local credentials rather than replacement Framework beans.

Behavior-first evidence: `./mvnw.cmd --batch-mode --no-transfer-progress '-Dtest=ExecutionConfigurationParserTest#acceptsRequestTimeoutOnSupportedConnection' test` FAILED before production edits (1 error: `loomspan.connections.primary.request-timeout is not publishable`). The same method accepts 240 seconds after the change. Initial test iterations corrected fixture issues only: Vertex boundary binding must avoid client/ADC construction, Google success responses require `modelVersion`, and AssertJ throwable signatures differ from string assertions. No SDK limitation or scope mismatch was found.

Autonomous fixture choices: adapter success budget 4 seconds and expiry budget 1 second; delayed headers 150 ms / 1800 ms; chunked bodies prepend 1000 legal whitespace bytes and throttle 50 bytes every 75 ms, keeping inactivity well below the expiry budget. Vertex uses generated RSA service-account JSON and a local token endpoint, with the recorded model Authorization verified as the local token; no Google OAuth request occurs. SDK global endpoint overrides reset in `finally`; the repository suite runs nonparallel. The nested capture fixture uses actual model-to-child tool invocation and a child timeout/retry; this expresses the planned descendant invariant without adding a Java forwarding seam.

### Acceptance mapping

| Ticket criterion | Implemented evidence |
| --- | --- |
| Unified binding/validation/client propagation/defaults/Ollama | `LoomspanPropertiesTest#requestTimeoutBindsAcrossSupportedDriversAndPreservesOmission`, `#rejectsInvalidRequestTimeoutBeforeLossyOrOverflowingConversion`, `#invalidTimeoutStartupBindingReportsItsFullPath`; `ExecutionConfigurationParserTest#acceptsRequestTimeoutOnSupportedConnection`, `#rejectsInvalidRequestTimeoutWithSafeFullPath`; `ProviderRequestTimeoutIntegrationTest#actualTransportPreservesDefaultAndConnectPhase` traverses actual constructed transport graphs for all five rows. |
| Delayed headers and active-body whole-call deadlines | `ProviderRequestTimeoutIntegrationTest#configuredBudgetAllowsDelayedHeadersAndCompleteChunkedBody`, `#wholeCallExpiresDespiteHeadersAndContinuouslyActiveBody`: actual OpenAI standard/OpenRouter, Anthropic, Gemini API key/Vertex; configured success/expiry, one send, transport value absent from JSON; existing OpenRouter diagnostics and closure regressions remain passing. |
| Short local fixtures without paid credentials | Local MockWebServer responses/auth, bounded endpoint teardown, no minute-long default requests; actual transport default assertions use client access/reflection. |
| Retry/count/quota/unchanged-request and cutoff ownership | `ModelAttemptCallAdvisorIntegrationTest#realProviderTimeoutRetriesPreserveRequestCountsClassificationAndQuota`: real recovery, exhaustion and quota cap, byte-identical retries, TIMEOUT traces and no recovered terminal error. `PublicSkillReloadIntegrationTest#longerProviderBudgetCannotExtendMissionOrCallerInterruption`: endpoint entry latch, mission/caller primary failure and interrupt flag, one request, no late success, release endpoint then await physical return/retirement. Existing interruption, cancellation translation and lifecycle tests also run. |
| Publication/copies/skill-only/captured descendants and both credentials | `PublicSkillReloadIntegrationTest#publishedTimeoutCapturesOldRootAndSurvivesRepeatedSkillOnlyUpdates`: Environment A / host-map B, zero preparation calls, admitted old root succeeds and new roots expire after repeated skill-only updates/startup mutation. `#invalidTimeoutCandidatesRejectAtomicallyWithoutRequestsOrHostSecretDisclosure`: safe validate paths, both preparations reject, unchanged ID and no host-map cause/secret disclosure. `#delayedDescendantAndItsRetryRetainCapturedProviderBudget`: old endpoint/budget held by child and byte-identical retry after publication; new child uses replacement budget. `SkillGenerationManagerTest#runtimeSnapshotDetachesRequestTimeoutFromStartupMutation` protects detached copy. |
| Consumer docs and supported surface | README and routed connections/reload/index updated for exact range/defaults, 240s/600s partial example, mission/retry/read-write/connect distinction, credential paths, captured settings and Ollama boundary. `LoomspanPublicSurfaceArchitectureTest` passes unchanged allowlists; no internal bean replacement is documented. |
| Controlled embedded and downstream host record | `PublicSkillReloadIntegrationTest#embeddedStartupOpenRouterHonorsHeaderAndActiveBodyDeadline` uses startup setting and supported `SkillTemplate` in a real embedded Spring context; publication tests cover reference-only candidates. Updated Sidecar host prerequisite is unavailable and remains PENDING as detailed below. |

Documentation drift classification after source/test comparison: **aligned**. Repository-local `loomspan-docs` metadata matches beta.8-SNAPSHOT. The connections topic is self-contained for optional/enforced semantics, scope/defaults and examples; the publication topic owns preparation/credential redaction and capture, linked from connections; stable named source/test anchors support material claims. The routing coverage row now names provider budgets. LLM-first acceptance questions (applicability, required/optional/prohibited behavior, evidence, remaining limits, next topic) are satisfied.

### Commands and results

- PASS — `./mvnw.cmd --batch-mode --no-transfer-progress '-Dtest=ProviderRequestTimeoutIntegrationTest' test`: 15 tests, zero failures/errors/skips; includes actual transport phases and all adapter matrix rows.
- PASS — `./mvnw.cmd --batch-mode --no-transfer-progress '-Dtest=ModelAttemptCallAdvisorIntegrationTest#realProviderTimeoutRetriesPreserveRequestCountsClassificationAndQuota,PublicSkillReloadIntegrationTest#publishedTimeoutCapturesOldRootAndSurvivesRepeatedSkillOnlyUpdates+invalidTimeoutCandidatesRejectAtomicallyWithoutRequestsOrHostSecretDisclosure+longerProviderBudgetCannotExtendMissionOrCallerInterruption+delayedDescendantAndItsRetryRetainCapturedProviderBudget' test`: 5 tests, zero failures/errors/skips.
- PASS — `./mvnw.cmd --batch-mode --no-transfer-progress '-Dtest=LoomspanPropertiesTest,ExecutionConfigurationParserTest,SkillGenerationManagerTest,LoomspanPublicSurfaceArchitectureTest,ProviderRequestTimeoutIntegrationTest,SpringAiProviderIntegrationTest,ConnectionProtocolTest,ModelAttemptCallAdvisorIntegrationTest,PublicSkillReloadIntegrationTest,SkillGenerationExecutionIntegrationTest,MissionLifecycleTest,JavaSkillMissionCutoffTest,StepLoopMissionExecutionEngineTest' test > target/pr13-focused.log 2>&1`: 182 tests, zero failures/errors/skips. A later added startup invalid-binding test is included in full verification below; production accessor placement only was tidied after this run.
- PASS — `./mvnw.cmd --batch-mode --no-transfer-progress clean verify > "$env:TEMP/loomspan-pr13-verify.log" 2>&1`: 1234 tests, zero failures/errors/skips; BUILD SUCCESS, 2:45, completed 2026-10-01 18:33 PDT. Includes all final production/test changes and architecture checks.
- Initial full verification FAILED solely on a brittle test literal: delayed-header timeout was correctly a `SocketTimeoutException: Read timed out`, whereas the public nested fixture required lowercase `timeout` in its stack. Public timeout assertions now inspect the typed cause or the actual `InterruptedIOException` timeout cause, matching both real transport expiry races. No production change was needed. Initial full log is retained at `%TEMP%/loomspan-pr13-verify-first.log`; the corrected full run uses the command above.
- PASS — `git diff --check` at this checkpoint; repeat after final artifact updates.

### Build and downstream boundary

Framework source identity: `ai.loomspan:loomspan-spring-boot-starter:1.0.0-beta.8-SNAPSHOT`, base commit `e35ab4488e5cac5e1cc6fef5f0f0797b5deb703d` plus the uncommitted PR 13 change. The controlled embedded tests use this checkout's built classes. This is evidence for Framework's embedded startup and public publication paths; it is not a run of the retained external application or a live final synthesis.

Final verified local artifact: `target/loomspan-spring-boot-starter-1.0.0-beta.8-SNAPSHOT.jar`, SHA-256 `d7e7acea3efb4f223763be2ea13b890e2972b541aea1c9b99684903a8d899a91`. Created by the successful final clean verification; it has not been published or installed into Sidecar.

Read-only inventory: `C:/opendev/code/loomspan-sidecar/pom.xml` identifies Sidecar `1.0.0-beta.2` and `<loomspan.version>1.0.0-beta.7</loomspan.version>`. Both available local host jars, `loomspan-sidecar-1.0.0-beta.2.jar` and `loomspan-sidecar-1.0.0-beta.2-SNAPSHOT.jar`, contain `BOOT-INF/lib/loomspan-spring-boot-starter-1.0.0-beta.7.jar`, as verified from ZIP entry metadata. The retained reproduction status/handoff likewise pin beta.7 / Sidecar beta.2. None contains this change. No downstream source/configuration was edited, and no host-specific rejection was discovered.

**PENDING / NOT RUN:** equivalent Sidecar startup/publication and delayed-header/active-body scenarios require a Framework release/build containing PR 13, then a Sidecar dependency/release consuming that exact artifact. The existing beta.7 dependency cannot establish the new property. This is a dependency follow-up, not evidence of a separate Sidecar defect. Sidecar source changes remain outside this ticket.

**Optional developer check / NOT RUN:** after separately authorizing an updated release and real-model run, rerun the retained 240-second provider / 600-second mission scenario on embedded and updated Sidecar paths to assess full workflow/final synthesis. Controlled timeout success alone does not establish that business/model outcome. No paid provider calls were made for Step 4.

Independent fresh Step 5 review remains required after implementation verification.
