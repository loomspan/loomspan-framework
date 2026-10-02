# PR 13 Configurable Provider Request Timeout Testing Plan

## Change Summary

Pipeline Step 3, following Step 2 in the same context, selected profile `full` confirmed by the developer. This plan specifies tests; no test code or production implementation is changed here.

Add optional exact whole-millisecond connection deadlines for OpenAI/OpenRouter, Anthropic and Gemini. Preserve omission defaults, credential validation, Framework retry/cancellation ownership, mission deadline and captured generations. Explicit Ollama timeout is rejected. See the companion implementation plan for the fixed `1..2147483647ms` range and compatibility decisions.

## Impacted Areas

`LoomspanProperties`, `ExecutionConfigurationParser`, `ExecutionRuntime`, `SpringAiProviderIntegration`; actual HTTP protocol fixtures, attempt advisor/trace/quota integration, public reload and generation/lifecycle tests; README and connections/reload references.

## Risk Assessment

- Binding alone can pass while actual transport ignores the value; body activity can conceal a mistaken read-only limit.
- Fractional milliseconds can be truncated; sub-resolution values become unlimited; huge Duration conversion can overflow.
- Explicit timeout must reach both Gemini modes without enabling retries or changing auth/connect behavior.
- OpenRouter inspection must consume under the deadline while retaining JSON/error/diagnostic behavior.
- Publication copies can omit the setting; delayed work/retries can accidentally read the newest generation.
- Mission/caller interruption must not trigger timeout retries, late success or early disposal of physically used resources.
- Protected surfaces: existing Java API/SPI signatures, additive settings with old omission behavior, candidate credential rules, mission/captured generation promises. No obsolete supported path is removed; changing an internal helper does not need an alias. No Console wire/schema/history tests or marker changes are needed.
- Host-map `prepare` intentionally redacts non-credential errors; safe actionable property-path evidence comes from `validate`, while prepare errors must remain secret-free and rejection atomic.

## Existing Test Coverage

- `src/test/java/ai/loomspan/autoconfigure/LoomspanPropertiesTest.java`: driver applicability, strict settings and validation.
- `src/test/java/ai/loomspan/internal/skill/ExecutionConfigurationParserTest.java`: candidate fields and credential binding.
- `src/test/java/ai/loomspan/internal/springai/SpringAiProviderIntegrationTest.java`: typed/timeouts classification, interrupt/cancellation distinction, native retry settings, Google local endpoint override, OpenRouter bounded capture.
- `src/test/java/ai/loomspan/internal/autoconfigure/ConnectionProtocolTest.java`: real wire paths and request options.
- `src/test/java/ai/loomspan/internal/chat/ModelAttemptCallAdvisorIntegrationTest.java`: physical counts/quota, unchanged-request retries, simulated OpenAI timeout traces, backoff interruption and actual OpenRouter error-completion retries.
- `src/test/java/ai/loomspan/integration/PublicSkillReloadIntegrationTest.java`: both credential paths, pending roots, published retry/connection changes and skill-only updates.
- `SkillGenerationManagerTest`, `SkillGenerationExecutionIntegrationTest`, `MissionLifecycleTest`, `JavaSkillMissionCutoffTest`, `StepLoopMissionExecutionEngineTest`: captured definitions, admission, cutoff/late-write/physical cleanup invariants.
- Gap: no supported timeout property, actual configured deadline matrix, or captured timeout behavior yet. Existing simulated failures are regression evidence but do not prove transport propagation.

## Bug Reproduction / Failing Test First

- Type: startup/parser boundary test.
- Locations: `LoomspanPropertiesTest` and `ExecutionConfigurationParserTest`.
- Arrange a valid OpenAI connection with dummy local credentials/reference, configured model alias and `request-timeout: 240s`; act through Spring binding and candidate parsing/validation; assert acceptance and Duration 240 seconds with zero endpoint calls.
- Expected pre-fix failure: strict binding or authored allowlist rejects `request-timeout`. Record the exact command/result before production changes. A red test may initially use the public validation outcome or binder without relying on a nonexistent getter, so red is a behavior failure rather than just uncompilable source.
- After the field exists, add a short actual HTTP red regression before adapter propagation if practical: response delayed beyond the authored short budget currently succeeds under the 60-second SDK default; the fixed behavior expires. Keep the server delay bounded so the red run lasts seconds.

## Fixtures and Scheduling Rules

Use existing MockWebServer dependency and adapter-specific valid JSON. Keep auth fake/local and forbid external provider/OAuth requests. Suggested budgets: 1s short, 3s long; header/body completion around 200ms for success and 2s for short-budget failure. Tighten only if measured suite runtime warrants it, preserving generous separation. Assert category/result/request count primarily; elapsed checks are coarse upper bounds (e.g. <5s for 1s budget), never near-exact milliseconds.

Use request-arrival/dispatcher latches to coordinate publication, cutoff and cancellation. For body expiry, send headers promptly and a legal JSON body with leading whitespace/chunks at 50–100ms intervals through >2s, with the final valid JSON later. `setChunkedBody` plus `throttleBody`, or a bounded local handler if necessary, must actually flush multiple chunks; intervals stay comfortably below the 1s inactivity limit. Provide a complementary within-budget chunked case. Capture only safe test bodies, no real credentials. All futures, endpoints, clients and executors have bounded teardown; release blocked server handlers in finally.

Google endpoint overrides are SDK global test state. Reset both API-key and Vertex URL defaults in finally, serialize all tests using those overrides or keep them in one nonparallel class. Do not add production endpoint override support. Vertex uses generated local credentials with an unexpired cached access token or local token URI, verified to make no external auth request.

## Tests to Add/Update

### 1) requestTimeoutBindsAndValidatesAcrossSupportedDrivers

- Type: unit/startup binding integration.
- Location: `src/test/java/ai/loomspan/autoconfigure/LoomspanPropertiesTest.java`.
- Proves OpenAI, Anthropic, Gemini API-key/Vertex accept `1ms`, `240s`, exact ISO duration and `2147483647ms`; omission is null. Verify driver enum/binding path and existing credential requirements.
- Fixtures: normal per-driver properties, local fake keys and valid Vertex project/location.
- Mocks: none for binding; no clients constructed.
- Surface: Configuration behavior, protected additive contract.

### 2) rejectsInvalidRequestTimeoutWithSafeFullPath

- Type: unit/startup boundary integration.
- Location: same properties test and parser test.
- Proves zero, negative, `999999ns`, `1500000ns`, malformed text, `2147483648ms` and huge ISO Duration fail safely. Positive exact `1000000ns` is 1ms and valid if Spring syntax accepts ns; use programmatic Duration for precision variants not expressible by Binder.
- Assert semantic errors contain `loomspan.connections.named.request-timeout` and safe range/precision rationale; malformed startup binding retains the path and does not cause application-visible secret disclosure. Candidate validation messages do not echo malformed sentinel input.
- Include explicit Ollama valid duration and omission; the former reports unsupported driver, the latter remains valid.
- Mocks: none.
- Surface: Configuration behavior, protected safe validation and unchanged omission.

### 3) candidateTimeoutValidatesPreparesAndRejectsAtomically

- Type: parser/public integration.
- Locations: `ExecutionConfigurationParserTest.java`, `PublicSkillReloadIntegrationTest.java`.
- Proves allowlist accepts the key; validation neither resolves missing credential refs nor constructs/calls providers; Environment and supplied-map preparation accept the value and make zero requests. Invalid timeout validation includes safe full path; both preparation paths reject, active generation ID/behavior remain unchanged, and host-map exception/cause contains no supplied secret sentinel.
- Fixtures: complete candidate YAML, unchanged complete skills, local server, environment or map fake references; no direct authored credentials.
- Mocks: none at public boundary.
- Surface: Application API and Configuration behavior, existing publication/credential guarantees protected.

### 4) actualProviderHonorsHeaderAndCompleteBodyDeadline

- Type: adapter HTTP integration; parameterized matrix.
- Location: new `src/test/java/ai/loomspan/internal/springai/ProviderRequestTimeoutIntegrationTest.java` (or focused methods in existing provider test if cohesive).
- Matrix: OpenAI standard, OpenRouter, Anthropic, Gemini API-key, Gemini Vertex.
- For each row prove valid delayed-header response completes within long budget; headers delayed beyond short budget expire; prompt headers with continuously active chunk/whitespace body still expire beyond the total budget; chunked complete body within long budget succeeds. Failures translate to TRANSIENT/TIMEOUT, with exactly one request per direct adapter call.
- Inspect recorded request JSON to ensure no timeout transport parameter appears and reasoning/model/options/credentials remain normal. For OpenRouter assert successful body preservation; keep existing `finish_reason:error`, oversized capture and closure tests running.
- Fixtures: MockWebServer/local handler, protocol-valid JSON, fake local credentials/options and local-only Vertex auth.
- Mocks: no chat model or HTTP client mocks; use actual `SpringAiProviderIntegration#create`.
- Surface: Configuration behavior and Internal adapter; protected new whole-call semantics/current diagnostic coherence.

### 5) timeoutOmissionAndTransportPhasesRemainUnchanged

- Type: focused construction/source-backed transport assertions plus existing integration regression.
- Location: provider timeout test / existing `SpringAiProviderIntegrationTest`.
- Verify constructed OpenAI/Anthropic actual transport call/read/write=60000ms and connect=60000ms on omission; configured 240000ms call/read/write with unchanged connect. Gemini omitted call=0/unset and connect/read/write unchanged; configured integer call timeout reaches both mode clients. Assert defaults without minute-long requests, using actual built-client accessors/internal reflection when needed; options-only assertion is supplementary, not sufficient transport proof.
- Native retry counts remain OpenAI/Anthropic 0 and Google one attempt; retryable local failures result in exactly one direct send. Ollama unchanged existing wire behavior, no new deadline claim.
- Surface: Configuration default behavior protected; internal access is only test evidence.

### 6) providerTimeoutRetriesAreCountedAndReuseTheRequest

- Type: advisor plus real HTTP integration.
- Location: `src/test/java/ai/loomspan/internal/chat/ModelAttemptCallAdvisorIntegrationTest.java`.
- First physical call times out at short connection budget; second returns valid JSON. Assert two server requests, byte-identical request JSON, one semantic interaction, two attempt quota charges/trace outcomes and TIMEOUT on first attempt. No recovered terminal warning. Exhaustion case reaches exactly maxAttempts with terminal TIMEOUT and no native extra sends. Include quota cap below maxAttempts to prove unchanged safeguard. Existing backoff interruption tests remain passing.
- Fixtures: deterministic queue/dispatcher and jitter=0, short/no backoff; real OpenAI or OpenRouter adapter and existing advisor/trace test harness.
- Mocks: only unrelated observation dependencies as existing tests; no model timeout fake for new evidence.
- Surface: Configuration retry contract and Ephemeral diagnostics, protected counts/current-run coherence.

### 7) publishedTimeoutUsesCapturedClientsAndSurvivesSkillOnlyUpdate

- Type: supported public API integration plus narrow snapshot test.
- Locations: `PublicSkillReloadIntegrationTest.java`, `SkillGenerationManagerTest.java`; add a focused runtime-copy test if existing manager fixture cannot demonstrate detached properties.
- A has long budget, B short budget; same named connection/model and controlled delay between budgets. Admit A root, publish B before it invokes: A succeeds, new B root times out. Repeat a skill-only prepare/publish and prove B still times out, including after changing mutable startup properties; snapshot Duration does not change after mutating source object.
- Test both credential paths with same property. Validate and prepare send zero requests; publish requires no reads/calls. Assert generation IDs and resource ownership as existing tests.
- Add descendant/retry case: admit A, publish B, A enters Java-root/child model work; first A attempt expires, then retry succeeds within A budget at A endpoint while B uses B endpoint/budget. Coordinate an additional in-flight publication if needed to prove retry reads captured state rather than registry current state. Children execute under same captured ID; bound endpoint delays and retries separately to make semantics unambiguous.
- Fixtures: public `SkillInvocationHandoff`, `SkillTemplate`, reloader; minimal Java root forwarding to model child (or existing nested fixture), fake credentials, deterministic local servers, trace/retirement observer.
- Mocks: no provider fake for timeout behavior.
- Surface: Application API and Configuration behavior, captured clients/settings and skill-only retention protected.

### 8) shorterMissionAndCallerCancellationFenceLongProviderCall

- Type: full embedded mission/provider integration.
- Location: public integration test or a focused `ProviderRequestTimeoutMissionIntegrationTest` alongside it, using existing cutoff harness conventions.
- Provider timeout longer than mission deadline; server confirms request started. Mission cutoff yields the existing mission-primary failure, no second provider attempt/child work despite retry-enabled policy, no late published success. Observe logical closure promptly, then release physical response and await cleanup/retirement without demanding instant socket closure beyond existing contract.
- Caller interruption case interrupts only after endpoint entry; existing interruption flag/primary failure is preserved, no TIMEOUT relabel/retry, no phantom attempt, no late result. Run translation cancellation distinction regression as well.
- Fixtures: coordinated endpoint, public invocation in managed executor, event/trace collection and retirement listener. No deadline-reset or fake-clock inference from options.
- Surface: Application API/Configuration lifecycle plus Ephemeral diagnostics, unchanged cutoff/security/cleanup guarantees protected.

### 9) supportedSurfaceAndDocumentationEvidence

- Type: existing architecture test and evidence review.
- Location: `src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java`; routed docs named in implementation plan.
- Run architecture test after production edits; exact allowlist unchanged, no internal signature leaks/new SPI/bean-replacement promise. Map each doc claim to tests 1–8: drivers/limits/defaults, active-body deadline, retries/mission distinctions, credentials/publication retention. No tests that merely match prose strings.
- Surface: Application API/SPI closed contract; documentation of enforced Configuration behavior.

### 10) embeddedAndSidecarControlledHostVerification

- Type: embedded integration plus downstream check/record.
- Embedded: supported public API context with updated artifact and real OpenRouter adapter, same property, delayed headers/active body and publication retention. Record Framework version/commit; this supplies controlled embedded evidence without claiming retained external application/live workflow success.
- Sidecar: use an available host artifact that actually includes this changed Framework; run equivalent same-property local endpoint startup/publication/delay cases. Record Sidecar and Framework identities. Do not modify Sidecar source or reproduction configs in this ticket.
- If artifact unavailable, explicitly PENDING on Framework release/build and Sidecar dependency/release consuming it; not PASS, and not a Sidecar defect. Retained beta.7/beta.2 fails the updated-dependency prerequisite. Paid-model final synthesis remains separately NOT RUN.
- Surface: supported host Configuration behavior, downstream verification boundary.

## How to Run

Windows PowerShell, Java 21+, Maven wrapper (repo-standard); no API keys or network provider endpoints. If environment lacks a supported JDK, report the limitation before claiming checks pass. No new dependencies are planned.

1. Pre-fix red boundary command, targeting newly named method(s) through Surefire, for example:
   `./mvnw.cmd --batch-mode --no-transfer-progress '-Dtest=ExecutionConfigurationParserTest#acceptsRequestTimeoutOnSupportedConnection' test`
2. Boundary and architecture:
   `./mvnw.cmd --batch-mode --no-transfer-progress '-Dtest=LoomspanPropertiesTest,ExecutionConfigurationParserTest,SkillGenerationManagerTest,LoomspanPublicSurfaceArchitectureTest' test`
3. Adapter, retry, publication and cutoff focused suite (include the new mission class if created):
   `./mvnw.cmd --batch-mode --no-transfer-progress '-Dtest=ProviderRequestTimeoutIntegrationTest,SpringAiProviderIntegrationTest,ConnectionProtocolTest,ModelAttemptCallAdvisorIntegrationTest,PublicSkillReloadIntegrationTest,SkillGenerationExecutionIntegrationTest,MissionLifecycleTest,JavaSkillMissionCutoffTest,StepLoopMissionExecutionEngineTest' test`
4. Full repository verification:
   `./mvnw.cmd --batch-mode --no-transfer-progress clean verify`
5. Diff whitespace:
   `git diff --check`

Use actual finalized test names in implementation reports, not hypothetical commands claimed as run. Repeat a timing test only if a new edit, failure or concern warrants it; successful focused and full checks suffice.

## Exit Criteria

- [x] A reliable behavior red test is run and recorded before fix; adapter coverage proves actual propagation afterward.
- [x] Startup and public candidate validation accept supported exact values, reject unsafe values/Ollama clearly, preserve omission and secret safety.
- [x] All actual adapters/profile/authentication rows prove delayed-header and active-body total deadlines, plus configured success; defaults/phases and one-send native retry ownership have evidence.
- [x] Genuine timeout retries preserve counts, quota, unchanged request and classification; mission/caller cutoff prevents later work and late success with existing physical cleanup.
- [x] Copies, both credential preparations, invalid-candidate atomicity, old/new roots, delayed descendants/retries and skill-only retention are verified.
- [x] Focused/full checks, architecture test and diff checks pass; no new API/SPI or compatibility shim is introduced.
- [x] Consumer guidance/coverage tables match source/tests, with no unresolved drift or unsupported SDK/provider guarantee.
- [x] Embedded controlled evidence is recorded; Sidecar check is either passed against identified updated dependency or explicitly pending with dependency/release prerequisite. No pending check is represented as passed.
- [ ] Fresh independent Step 5 review follows implementation. Optional production-model outcome observation is separate from local correctness; full workflow/final synthesis remains unverified without a separately authorized run.
