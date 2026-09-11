---
date: 2026-09-10T20:49:34-07:00
researcher: GPT-5
git_commit: 14e98df239bb8002069d9432b4f00e99d102d955
branch: main
repository: loomspan-framework
topic: "PR 1 — Make provider failures actionable in logs and traces"
tags: [research, codebase, provider-failures, retry, traces, console]
status: complete
last_updated: 2026-09-10
last_updated_by: GPT-5
---

# Research: PR 1 — Make provider failures actionable in logs and traces

**Date**: 2026-09-10T20:49:34-07:00
**Researcher**: GPT-5
**Git Commit**: 14e98df239bb8002069d9432b4f00e99d102d955
**Branch**: main
**Repository**: loomspan-framework

## Research Question

Document the current framework paths relevant to `ai/thoughts/tickets/loomspan-pr-1-actionable-provider-failures.md`: provider-failure translation, retry classification, terminal logging, failed-attempt tracing, terminal-failure linkage, Console consumption, configuration diagnostics, compatibility surfaces, tests, fixtures, and author-facing documentation.

## Summary

Loomspan already has one framework-owned physical-attempt boundary. `ProviderAttemptCallAdvisor` translates each thrown provider call, applies the connection retry policy, records one `MODEL_ATTEMPT_FAILED` record with model/connection identity and bounded diagnostics, and registers only the final non-retried failure for later canonical terminal linkage (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/chat/ProviderAttemptCallAdvisor.java:61-87`). It does not currently log a provider failure at WARN or any other level. The public `SkillTemplate` facade later wraps the runtime exception in a safe `SkillException`, and its observer is called only after success (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skillapi/DefaultSkillTemplate.java:91-130`).

Provider translation is centralized in `SpringAiProviderIntegration`. It recognizes its own normalized OpenRouter/Ollama exceptions, Google `ApiException`, Anthropic `AnthropicServiceException`, Spring `RestClientResponseException`, selected transport causes, timeout causes, cancellation/interruption, and TLS failures (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/springai/SpringAiProviderIntegration.java:233-283`). HTTP status mapping makes 408, 429, 500, 502, 503, and 504 transient; 401/403/402 receive authentication/authorization/payment categories; other non-5xx responses are permanent `INVALID_REQUEST`; any 5xx receives `SERVER_ERROR` (`SpringAiProviderIntegration.java:334-345`). The pinned OpenAI SDK's `OpenAIServiceException` hierarchy is not matched in this translation loop, so OpenAI HTTP failures currently fall through to `UNKNOWN` unless a recognized cause appears in their chain. The existing tests cover OpenAI read timeouts but do not cover OpenAI HTTP status exceptions.

The canonical trace already carries the affected framework model, connection, driver, and provider model on every model-attempt record through `ModelExecutionIdentity` and `ModelTraceContext` (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/ModelExecutionIdentity.java:10-38`; `ModelTraceContext.java:73-91`). Failed-attempt data contains a bounded Java stack followed by provider diagnostics (`DefaultExecutionTraceRecorder.java:59-70`). Console preserves attempt identity, normalized retry facts, status/type/code, and a content reference to that diagnostic array, while its UI can load and render the entire ordered array as inert text (`loomspan-console/internal/traceanalysis/attempts.go:67-159`; `loomspan-console/web/src/observability/TraceAttemptDiagnostics.tsx:33-81,153-175`). The ordinary Console attempt projection does not carry the attempt record's framework-model/connection/driver/provider-model metadata, and its terminal failure panel shows the linked attempt/retry IDs but does not directly load the linked attempt diagnostic (`loomspan-console/internal/traceanalysis/dto.go:265-285`; `loomspan-console/web/src/observability/TraceFailureFocus.tsx:10-32`).

## Detailed Findings

### 1. Configuration and model identity

- Applications configure named connections under `loomspan.connections` and model aliases under `loomspan.models`. A model entry requires `connection` and `provider-model`, and the named connection must exist (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/autoconfigure/LoomspanProperties.java:231-258`).
- OpenAI and Anthropic require nonblank `api-key`; Ollama requires nonblank `base-url`; Gemini requires exactly one of API-key or Vertex mode (`LoomspanProperties.java:182-209`). A nonblank placeholder such as `not-configured` satisfies this startup validation because validation uses `StringUtils.hasText` (`LoomspanProperties.java:261-266`).
- Each connection owns the retry configuration. Defaults are enabled, three total attempts, 500 ms initial backoff, multiplier 2.0, five-second maximum backoff, and 0.2 jitter (`LoomspanProperties.java:458-480`).
- The application-facing configuration chain and endpoint semantics are documented in the root README: model alias to connection to provider model, connection-owned retries, SDK service-root `base-url` behavior, and the OpenRouter profile (`README.md:93-130`).
- `EffectiveSkillExecutionConfiguration` is converted to `ModelExecutionIdentity`, whose four fields are framework model, connection, driver, and provider model. `metadata()` emits all four (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/ModelExecutionIdentity.java:10-38`).
- `ModelTraceContext.metadata(attempt)` merges that identity with skill, segment, retry-sequence, attempt, attempt-number, attempt-reason, and provider-attempt-number fields (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/ModelTraceContext.java:65-79,116-150`).

### 2. Connection construction and startup diagnostics

- `LoomspanAiAutoConfiguration` directly creates the internal registry, provider integration, resolver, options contributor, advisor resolver, and model interaction factory as infrastructure beans. These bean methods have no `@ConditionalOnMissingBean` annotations (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/autoconfigure/LoomspanAiAutoConfiguration.java:24-78`).
- `NamedAiConnectionRegistry` builds each configured connection eagerly. A `SafeAiConnectionConfigurationException` passes through unchanged; any other runtime failure is replaced by `IllegalStateException("Failed to construct AI connection '<name>' for driver <driver>")` without the original cause (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/autoconfigure/NamedAiConnectionRegistry.java:21-50`).
- The safe exception path currently names `loomspan.connections.<name>.gemini.credentials-uri` when a Gemini credential resource cannot be read (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/springai/SpringAiProviderIntegration.java:427-437`). The retry-ownership guard similarly names `loomspan.connections.<name>.provider-retry.enabled` (`NamedAiConnectionRegistry.java:28-34`).
- The generic construction-failure path names the connection and driver, but not `loomspan.connections.<name>.base-url` or another setting. `SensitiveConnectionDataRedactionTest` deliberately verifies that the API key, header values, base URL, and credential URI values do not appear in owned output, while safe connection and driver identity remain (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/autoconfigure/SensitiveConnectionDataRedactionTest.java:35-103`).
- `SpringAiProviderIntegration#baseUrlAlreadyEndsWithV1` parses the base URL with `URI.create`, but fresh repository search found no invocation of this method (`SpringAiProviderIntegration.java:440-445`). Current OpenAI path behavior is instead protected through the SDK-backed client test (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/autoconfigure/ConnectionProtocolTest.java:62-82`).

### 3. Provider client construction and retry ownership

- `SpringAiProviderIntegration#create` creates a `ProviderConnectionRuntime` containing the chat model, driver, exact attempt ownership marker, connection retry policy, and a method reference to the centralized translator (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/springai/SpringAiProviderIntegration.java:113-123`).
- OpenAI and Anthropic models set SDK `maxRetries(0)`; Gemini uses one HTTP attempt and a Spring retry template with zero retries; Ollama uses a one-attempt retry template (`SpringAiProviderIntegration.java:125-191`). This leaves physical retry ownership with Loomspan's advisor.
- `SpringAiChatClientAssembler` resolves the runtime by connection, creates a `ProviderAttemptCallAdvisor`, and places it into the one skill `ChatClient` advisor chain (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/springai/SpringAiChatClientAssembler.java:78-103,106-120`).
- `DefaultSkillChatModelResolver` reports missing runtime configuration with skill, connection, driver, and framework model. Its current message does not include the provider model (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/chat/DefaultSkillChatModelResolver.java:19-32`).

### 4. Provider-failure translation

- The translator walks at most 12 unique throwable causes. It recognizes:
  - internal `ProviderCallException`, returning already normalized details;
  - Google `ApiException`, capturing status/message as bounded JSON evidence;
  - Anthropic `AnthropicServiceException`, capturing bounded response body and `Retry-After`;
  - Spring `RestClientResponseException`, capturing bounded response body and `Retry-After`;
  - cancellation/interruption and TLS as permanent `UNKNOWN`;
  - socket timeouts and qualifying interrupted read timeouts as transient `TIMEOUT`;
  - connect/socket/EOF/unknown-host causes as transient `CONNECTIVITY`;
  - all other failures as `UNKNOWN` (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/springai/SpringAiProviderIntegration.java:233-332`).
- HTTP classification is exact: 408, 429, 500, 502, 503, and 504 are transient; every other status is permanent. Categories are 429 rate-limited, 401 authentication, 403 authorization, 402 payment-required, all 5xx server-error, and all remaining statuses invalid-request (`SpringAiProviderIntegration.java:334-345`). A 404 is therefore represented as a permanent invalid request rather than a proven nonexistent model.
- OpenRouter's explicit compatibility profile additionally inspects successful HTTP responses for `finish_reason: error`; it records provider error type, code, summary, and bounded body evidence, then classifies a closed list of error types as transient (`SpringAiProviderIntegration.java:194-230,405-423`). Ollama's response error handler converts non-success responses to the same internal normalized HTTP details (`SpringAiProviderIntegration.java:447-458`).
- Diagnostic capture is limited to one mebibyte and records kind, content type, text, truncation state, and capture limit (`SpringAiProviderIntegration.java:68,398-403`). There is currently no targeted future-scrubbing TODO at this capture method or the failed-attempt emission call.
- The pinned dependencies are Spring AI 2.0.0 and OpenAI Java core 4.39.1 (`pom.xml:54,75-76`; `loomspan-spring-boot-starter/pom.xml:31-34`). The 4.39.1 OpenAI client represents HTTP failures with `com.openai.errors.OpenAIServiceException` subclasses. No `com.openai.errors` type is referenced by the current translator, and fresh search found no OpenAI HTTP-status translation test. `SpringAiProviderIntegrationTest` covers only OpenAI interrupted read timeout behavior for that driver (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/springai/SpringAiProviderIntegrationTest.java:112-157`).
- Focused executable coverage exists for Google typed HTTP/transport failures, Anthropic 503 with `Retry-After`, Ollama 429, OpenRouter error completions, timeout/cancellation distinctions, capture bounds, and response closure (`SpringAiProviderIntegrationTest.java:49-110,159-245`; `ConnectionProtocolTest.java:30-59,150-175,233-252`).

### 5. Retry decisions, terminal boundary, and warning ownership today

- `ProviderRetryDecider` retries only `TRANSIENT` classifications when connection retry is enabled. It returns `ATTEMPTS_EXHAUSTED` at the maximum attempt, otherwise uses bounded jittered backoff and a larger bounded `Retry-After` when present (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/provider/ProviderRetryDecider.java:8-49`). Permanent and unknown failures return `DO_NOT_RETRY` (`ProviderRetryDecider.java:10-14`).
- On every thrown physical call, `ProviderAttemptCallAdvisor` translates first, decides retry behavior second, records usage/metrics and a failed-attempt trace third, and only then branches. A `RETRY` waits and loops; `DO_NOT_RETRY` and `ATTEMPTS_EXHAUSTED` register the failure-to-attempt link and rethrow (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/chat/ProviderAttemptCallAdvisor.java:68-87`).
- The advisor contains no logger and emits no WARN. The terminal branch at lines 81-85 is the current point that distinguishes a failure which will escape from failures that will be retried.
- Retry integration tests establish one `MODEL_ATTEMPT_FAILED` record per failed physical attempt, stable retry-sequence identity, increasing attempt/provider-attempt numbers, no `ERROR_RECORDED` for recovered failures, and `ATTEMPTS_EXHAUSTED` only on the final attempt (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/chat/ModelAttemptCallAdvisorIntegrationTest.java:335-411,488-573,716-771`).
- Because translation precedes retry classification, adding recognition of an HTTP exception changes retry behavior as well as diagnostics. Existing status mapping treats 429 and the selected 5xx statuses as transient, while 401/403/404 and other request failures are permanent.

### 6. Failed-attempt trace representation

- `ProviderFailureDetails` currently carries classification, category, optional HTTP status, optional retry delay, provider type/code, optional summary, and a list of diagnostics (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/provider/ProviderFailureDetails.java:9-27`). All of these types are in `ai.loomspan.internal`.
- `ProviderAttemptCallAdvisor#failureMetadata` writes classification, category, retry decision, delay, delay source, and optional HTTP/type/code/summary to record metadata (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/chat/ProviderAttemptCallAdvisor.java:104-116`). `ModelTraceContext` has already supplied skill, model, connection, driver, provider model, and attempt identity.
- `DefaultExecutionTraceRecorder` constructs one ordered diagnostic list: a bounded Java stack first, followed by provider diagnostics. This list is the data of the existing `MODEL_ATTEMPT_FAILED` record; no duplicate failure event is created (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/DefaultExecutionTraceRecorder.java:59-70`).
- `ModelAttemptCallAdvisorIntegrationTest` protects that ordering and exact provider diagnostic preservation (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/chat/ModelAttemptCallAdvisorIntegrationTest.java:351-405`). The recovered-provider fixture and Java/Go corpus tests protect chunked diagnostic transport and exact ranged access (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/runtime/trace/ConsoleTraceFixtureCorpusTest.java:333-375`; `loomspan-console/internal/traceanalysis/fixture_corpus_test.go:462-557`).
- `ExecutionJournalProjector` maps every failed attempt to a WARN journal entry but currently retains only attempt/retry/classification/category/decision/delay fields, not model identity, HTTP facts, summary, or diagnostic text (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/runtime/trace/ExecutionJournalProjector.java:75-90,109-123`).
- `LiveActivityProjector` also exposes failed attempts, but its detail-key allowlist omits framework model, connection, driver, provider model, HTTP status, provider error type/code, and summary. Its displayed summary is only “Provider attempt N failed” or “...retrying in N ms” (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/runtime/observation/LiveActivityProjector.java:20-45,356-365`).

### 7. Canonical terminal-failure linkage and public facade behavior

- On the terminal provider branch, `registerProviderFailure` associates the thrown failure and every cause with the final `attemptId`/`retrySequenceId` in session-local identity maps (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/LoomspanSession.java:338-355`).
- Later `recordFailure` traverses the throwable chain, reuses an existing stable failure ID when one exists, and copies the registered provider attempt identity onto the canonical `ERROR_RECORDED` metadata (`LoomspanSession.java:276-324`). `LoomspanSessionTest#linksTheCanonicalTerminalErrorToTheRegisteredFinalProviderAttempt` protects wrapped-cause linkage (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/LoomspanSessionTest.java:125-141`).
- `ExecutionCoordinator` records the canonical failure, closes frames with the same failure ID, and finalizes the trace with that ID as `terminalFailureId` (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/ExecutionCoordinator.java:128-225`). The final `TRACE_COMPLETED` terminal ID remains the authority for terminality.
- The canonical `ERROR_RECORDED` payload contains a safe context message, exception class, and its own bounded stack diagnostic; it does not copy failed-attempt provider diagnostics (`LoomspanSession.java:306-324`; `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/TraceFailureMetadata.java:15-26`).
- `DefaultSkillTemplate` preserves `AccessDeniedException` and existing `SkillException`, but wraps other runtime failures as `SkillException("Skill '<name>' execution failed.", cause)`. The optional public observer is invoked after the try/catch and therefore receives only successful invocations (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skillapi/DefaultSkillTemplate.java:91-130`).
- `SkillException` has only message and message/cause constructors (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/api/SkillException.java:3-13`). No provider-specific public exception or public diagnostic API exists.

### 8. Console ingestion and failure presentation

- The Go attempt graph consumes `MODEL_REQUEST_SENT`, `MODEL_RESPONSE_RECEIVED`, and `MODEL_ATTEMPT_FAILED` using explicit attempt/retry IDs and strict lifecycle ordering. It preserves classification, category, decision, delay, HTTP status, provider type/code, and the attempt payload ID (`loomspan-console/internal/traceanalysis/attempts.go:67-159`). It does not preserve the failed-attempt metadata's `summary` or model/connection identity in `AttemptSummary` (`loomspan-console/internal/traceanalysis/dto.go:265-285`).
- `RecordFacts` attaches the attempt result and an opaque `contentRef` for the failed-attempt diagnostic payload; exact content is read through the ordinary bounded range service (`loomspan-console/internal/traceanalysis/record_facts.go:145-158`; `loomspan-console/internal/traceanalysis/query_ranges.go:18-59`).
- The web client contract mirrors the normalized attempt fields and content reference. It does not carry summary or model/connection fields (`loomspan-console/web/src/api/contracts.ts:327-340`).
- In the record table, a failed attempt receives an “Attempt diagnostics” action. `TraceAttemptDiagnostics` loads all contiguous ranges, requires complete valid UTF-8 JSON with a `diagnostics` array, validates descriptor fields, labels unknown diagnostic kinds generically, and renders their text in `<pre>` (`loomspan-console/web/src/observability/TraceRecords.tsx:976-1047,1105-1113`; `loomspan-console/web/src/observability/TraceAttemptDiagnostics.tsx:33-81,94-175`). A new framework-defined text diagnostic kind would therefore render without a Console-side classifier.
- The failure graph independently consumes `ERROR_RECORDED`, marks terminality only from `TRACE_COMPLETED.terminalFailureId`, and validates an optional terminal provider-attempt link against a final failed attempt (`loomspan-console/internal/traceanalysis/failures.go:10-42,107-149`). This preserves the framework-emitted authority rather than reconstructing terminality from record order.
- The terminal failure panel shows the failure ID, record/frame/route, attempt ID, retry-sequence ID, and a note that it does not identify root cause. Its buttons switch among timeline, usage, and records; it does not directly select or load the linked `MODEL_ATTEMPT_FAILED` content (`loomspan-console/web/src/observability/TraceFailureFocus.tsx:10-32`). `TraceFailureDiagnostic` loads diagnostics attached to `ERROR_RECORDED`, which currently means the canonical stack rather than the provider diagnostic array (`loomspan-console/web/src/observability/TraceFailureDiagnostic.tsx:28-105`).
- The existing cross-language fixture includes a recovered failed attempt with Java stack plus provider body and proves its query/search/range behavior. There is no fixture whose terminal failure details expose an actionable framework explanation attached to the linked failed attempt (`loomspan-console-fixtures/traces/recovered-provider-attempt-diagnostic.ndjson:5-11`; `loomspan-console-fixtures/README.md:3-5`).

### 9. Diagnostic bounds and sensitivity

- Provider HTTP/error-completion bodies are bounded to one mebibyte at capture (`SpringAiProviderIntegration.java:68,194-229,245-259,398-403,447-458`). Java stack capture is also bounded to one mebibyte (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/BoundedStackTraceCapture.java:1-39`).
- Console's canonical failure parser accepts at most 16 diagnostics, at most one mebibyte per diagnostic, and at most four mebibytes aggregate for an `ERROR_RECORDED` failure (`loomspan-console/internal/traceanalysis/failures.go:45-93`). Failed-attempt diagnostics use the ordinary payload/content path and frontend validation instead of the failure-descriptor parser.
- The root README states that the authenticated artifact endpoint transfers the exact finalized diagnostic file and does not parse, rewrite, normalize, redact, compress, or fully buffer it; authenticated traces may contain application business data and paths (`README.md:522-538`). The authoring trace guide likewise treats diagnostics as potentially sensitive inert data (`agent-skills/loomspan-docs/references/skill-authoring/traces-and-debugging.md:245-250,407-415`).
- Current owned configuration diagnostics intentionally omit credential contents, header values, and base URL values (`SensitiveConnectionDataRedactionTest.java:35-103`). No general scrubber or redaction framework is present in the provider diagnostic capture path.

### 10. Contract and compatibility inventory

#### Application API

- The closed supported API remains the eight top-level types in `ai.loomspan.api`; provider classes and trace machinery are not allowlisted. `SkillTemplate` and `SkillException` are the affected existing application-facing invocation/failure boundary, but no provider-specific type appears in their signatures (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java:20-39`; `loomspan-spring-boot-starter/src/main/java/ai/loomspan/api/SkillException.java:3-13`).

#### Supported SPI

- None. `LoomspanAiAutoConfiguration` has direct infrastructure beans with no conditional replacement annotations, and the architecture test classifies public internal provider/resolver/advisor types as internal cross-package machinery (`LoomspanAiAutoConfiguration.java:24-78`; `LoomspanPublicSurfaceArchitectureTest.java:48-98`).

#### Configuration and manifest contracts

- `loomspan.connections.<name>.driver`, `.api-key`, `.base-url`, provider-specific blocks, `.provider-retry.*`, and `loomspan.models.<name>.connection`/`.provider-model` are documented configuration contracts (`README.md:93-130`; `LoomspanProperties.java:106-271,396-517`). The ticket does not request changing their accepted shape or defaults.
- YAML skill authors continue selecting a framework model alias; provider connection configuration belongs to the application. No manifest invocation contract is involved.

#### Persisted or serialized contracts

- No application-owned durable provider-diagnostic contract was found. The public observer values are current-version diagnostic values, and the canonical trace file is portable only under the exact compatibility marker described below.

#### Ephemeral diagnostic formats

- `MODEL_ATTEMPT_FAILED` metadata/data, `ERROR_RECORDED` attempt linkage, live activity DTOs, canonical NDJSON, Go attempt/failure indexes, browser DTOs, MCP projections, and frontend diagnostic rendering are same-version diagnostic formats. The root README explicitly says live/finalized trace shapes are not application APIs (`README.md:513-520`).
- Java writes `consoleCompatibilityVersion` into `TRACE_STARTED` (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/runtime/trace/DefaultExecutionTraceHandle.java:298-314`); Console requires a nonblank string marker and applies release/development compatibility checks (`loomspan-console/internal/traceanalysis/processor.go:501-514`; `processor_test.go:115-180`).
- `loomspan-console-fixtures` is the executable Java-to-Go semantic contract and is consumed by Java corpus generation/verification and Go fixture tests (`loomspan-console-fixtures/README.md:3-5,45-45,78-84`; `ConsoleTraceFixtureCorpusTest.java:333-375`; `fixture_corpus_test.go:462-557`). Any changed diagnostic representation must keep those writers/readers/projections coherent in the same change.

#### Internal or accidentally exposed implementation

- `ProviderFailureDetails`, translator/category/classification, retry policy/decision/outcome, `ProviderConnectionRuntime`, `ProviderAttemptCallAdvisor`, Spring AI integration, model identity/context, registry/resolver, trace recorder, session maps, and Console processing/UI implementation are internal. The architecture allowlist explicitly classifies their technically public Java declarations as framework-only collaboration types (`LoomspanPublicSurfaceArchitectureTest.java:68-98`).
- Fresh search found no `@ConditionalOnMissingBean` in production sources and no in-repository application use of these internal provider types outside framework wiring/tests.

### 11. Documentation comparison and drift classification

- The checked-in authoring guide describes the same model-alias/connection/provider-model chain, retry ownership, exact physical attempts, attempt-local stack-first diagnostics, terminal provider-attempt linkage, and same-version Console trace model (`agent-skills/loomspan-docs/references/skill-authoring/model-selection-and-connections.md:16-80`; `traces-and-debugging.md:250-291,387-415`). Those existing claims are **aligned** with the production paths and focused tests inventoried above.
- The current guide says connection diagnostics, traces, and metrics identify framework model, connection, and driver (`model-selection-and-connections.md:68`) and that runtime resolution errors identify skill, framework model, connection, driver, and provider model (`model-selection-and-connections.md:82`). Canonical attempt traces do contain all four model identity fields, but the missing-connection resolver message omits provider model and the journal/live/Go attempt projections omit some or all identity fields. For the requested normal-WARN/actionable-provider-failure behavior, the documentation has no current statement. Drift classification: **unresolved** for this uncovered behavior, not evidence of an existing supported behavior; planning must decide which author-facing diagnostic semantics require documentation updates after implementation.
- The installed `loomspan-docs` router metadata says `0.1.0-SNAPSHOT`, while the checkout's `agent-skills/loomspan-docs/SKILL.md` says `1.0.0-beta.3-SNAPSHOT`. The selected installed and checked-in `model-selection-and-connections.md` files have identical SHA-256 hashes, as do the selected `traces-and-debugging.md` files. Version-sensitive conclusions in this research therefore use checked-out source, tests, fixtures, and checked-in documentation; the installed skill was used only as the required routing mechanism.

## Code References

- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/chat/ProviderAttemptCallAdvisor.java:61-116` — physical attempt loop, translation, retry decision, failed-attempt recording, terminal registration, and trace metadata.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/springai/SpringAiProviderIntegration.java:113-191` — built-in provider clients and native retry disabling.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/springai/SpringAiProviderIntegration.java:233-345` — throwable traversal, typed translations, transport mapping, HTTP classification, and provider evidence.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/ModelExecutionIdentity.java:10-38` — model/connection/driver/provider-model identity.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/DefaultExecutionTraceRecorder.java:59-70` — stack-first failed-attempt diagnostic emission.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/LoomspanSession.java:276-355` — canonical failure deduplication and provider-attempt linkage.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skillapi/DefaultSkillTemplate.java:91-130` — safe facade wrapping and success-only observation.
- `loomspan-console/internal/traceanalysis/attempts.go:67-159` — Console attempt validation and normalized facts.
- `loomspan-console/internal/traceanalysis/failures.go:107-149` — authoritative terminal and provider-attempt link validation.
- `loomspan-console/web/src/observability/TraceAttemptDiagnostics.tsx:33-175` — inert ordered attempt diagnostic rendering.
- `loomspan-console/web/src/observability/TraceFailureFocus.tsx:10-32` — current terminal failure details and attempt identity display.
- `loomspan-console-fixtures/README.md:3-5,78-84` — same-version cross-language trace fixture contract.

## Architecture Documentation

The current ownership flow is:

```text
loomspan.models alias + loomspan.connections entry
  -> EffectiveSkillExecutionConfiguration
  -> ModelExecutionIdentity / ModelTraceContext
  -> ProviderConnectionRuntime selected by connection
  -> ProviderAttemptCallAdvisor
       -> provider-specific Throwable translator
       -> ProviderRetryDecider
       -> existing MODEL_ATTEMPT_FAILED record
       -> terminal failure-to-attempt registration only when not retrying
  -> canonical ERROR_RECORDED + TRACE_COMPLETED.terminalFailureId
  -> exact NDJSON artifact / live diagnostic projection
  -> Console Go validation and projections
  -> browser/MCP consumers
```

The provider translator owns provider-specific interpretation. The attempt advisor owns physical attempt ordering, retry decisions, and the terminal-vs-retry branch. `LoomspanSession` owns stable canonical failure identity and the link from the terminal error to the final provider attempt. Console validates and presents those emitted facts; it does not currently classify provider exceptions.

The relevant protected invariants are one actual send per physical attempt, one failed-attempt record per thrown send, unchanged-request retries only for transient classifications, response-only model-call/usage accounting, stable attempt ordering, no canonical error for a recovered provider attempt, final-attempt linkage for terminal provider failure, bounded diagnostic evidence, and exact same-version Java-to-Go fixture agreement.

## Historical Context (from ai/thoughts/)

- `ai/thoughts/tickets/loomspan-pr-1-actionable-provider-failures.md` is the only provider-actionability ticket or research artifact found under `ai/thoughts/`. It records the travel-demo motivating incident, required failure cases, ambiguity constraints, retry invariants, trace/Console coherence, security scope, and no-new-API/SPI constraint.
- `ai/thoughts/framework-feature-design-lens.md` classifies application API, SPI, configuration/manifest, persisted/serialized, ephemeral diagnostic, and internal surfaces; it identifies framework-emitted authoritative facts and same-release Java/Go coordination as the governing design concerns for this ticket.
- `ai/thoughts/tickets/loomspan-pr-21-console-diagnostic-logging.md` concerns the separate Go Console process's own diagnostic logging. It does not define application-side provider-failure logging and is not an implemented dependency of PR 1.

## Related Research

No prior research document about actionable provider failures was found in `ai/thoughts/research/`.

## Open Questions

The planning step must resolve these implementation-shape decisions from the ticket and current boundaries:

1. Which existing failed-attempt representation will carry the framework-authored actionable explanation: an additional bounded diagnostic in the current ordered `diagnostics` array, an existing metadata field such as `summary`, or a coordinated current-version adjustment to both. The choice determines Java fixture, Go projection, MCP/browser DTO, and UI work.
2. How Console failure details will navigate or expose the linked final `MODEL_ATTEMPT_FAILED` explanation while retaining `ERROR_RECORDED` and `TRACE_COMPLETED.terminalFailureId` as the canonical terminal authority.
3. Whether the chosen ephemeral trace/projection adjustment warrants changing `consoleCompatibilityVersion`; the current policy requires an explicit same-version marker decision and atomic fixture/consumer updates, but does not require historical readers.
4. Which construction-time exceptions from each built-in SDK can be safely mapped to the relevant `loomspan.connections.<name>.base-url` setting without exposing the configured URL value, while retaining the existing safe-output test guarantees.
5. Which author-facing statements in the root README and checked-in `loomspan-docs` topics must be updated once the exact WARN text, trace diagnostic shape, and Console access path are implemented.
