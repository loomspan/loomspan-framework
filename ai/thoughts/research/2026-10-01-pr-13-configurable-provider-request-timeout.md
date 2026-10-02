---
date: 2026-10-01T18:00:14-07:00
researcher: Codex (GPT-6)
git_commit: e35ab4488e5cac5e1cc6fef5f0f0797b5deb703d
branch: main
repository: loomspan-framework
topic: "PR 13: configurable provider request timeout"
tags: [research, codebase, providers, configuration, publication, timeouts]
status: complete
last_updated: 2026-10-01
last_updated_by: Codex
---

# Research: configurable provider request timeout

## Research question and execution context

Map the existing configuration, client construction, provider failure/retry, mission cutoff, and generation-publication paths for `ai/thoughts/tickets/loomspan-pr-13-configurable-provider-request-timeout.md`. This is Step 1 in pipeline mode, selected profile `full` (Full 5-Step Pipeline), explicitly confirmed by the developer with “confirm”. The ticket's advisory recommendation remains distinct from that selected profile.

Baseline: tracked checkout clean, branch `main`, commit `e35ab4488e5cac5e1cc6fef5f0f0797b5deb703d`; the developer-supplied ticket is untracked. No production changes or tests were made in this research step. Metadata came from `bash ai/scripts/spec_metadata.sh`, which reported 2026-10-01 18:00:14 PDT. Repository version and repository-local documentation skill both identify `1.0.0-beta.8-SNAPSHOT`.

Research checklist completed:

- [x] Configuration binding, validation, and strict candidate parsing.
- [x] Resolved transport sources, timeout units, omission defaults, and actual client propagation.
- [x] OpenRouter inspection and native retry ownership.
- [x] Captured execution copies, publication, credential resolution, and retirement.
- [x] Mission/cancellation paths and focused test inventory.
- [x] Supported surface classification and consumer documentation routing.
- [x] Downstream reproduction and pending hosting evidence.

## Summary

Connections currently have no request-timeout property. Startup binds through strict `LoomspanProperties`; candidate publication separately allowlists connection fields and binds into the same properties before calling the same initialization validator. Runtime generation snapshots explicitly copy connection fields, and each generation owns its actual provider clients.

OpenAI and Anthropic Spring AI 2.0.0 clients currently receive a 60-second request timeout from their SDK setup defaults. Both map that timeout into actual OkHttp whole-call and read/write inactivity limits while preserving a separate 60-second connect default. Google GenAI 1.58.0 supports an optional integer millisecond request timeout mapped to OkHttp callTimeout; its omission leaves call/connect/read/write timeouts unset/disabled in the current construction path. All three transports ultimately represent call timeouts as nonnegative signed integer milliseconds; zero means unlimited at that underlying boundary.

The current parser, runtime copy, and generation resource lifecycle already cover the ticket's startup/publication paths. Actual delayed-response timeout tests and downstream checks are future implementation verification, not research results.

## Configuration and validation

- `src/main/java/ai/loomspan/autoconfigure/LoomspanProperties.java:27` declares `@ConfigurationProperties(prefix = "loomspan", ignoreUnknownFields = false)`; unknown startup keys are rejected. `afterPropertiesSet` invokes connection and model validation.
- `LoomspanProperties.java:128` checks connection names, definitions, driver, applicable option blocks, required fields, headers, and provider-retry values. Failures use authored full property paths and safe descriptions. Gemini rejects `base-url`; OpenAI and Anthropic accept it. Ollama rejects credentials and nonmatching option blocks.
- `LoomspanProperties.java:441` defines `ConnectionProperties` containing driver, baseUrl, apiKey, headers, openai, gemini, and providerRetry. There is no transport timeout member. Its `toString` reports credential presence rather than credential content.
- `src/main/java/ai/loomspan/internal/skill/ExecutionConfigurationParser.java:27` maintains a separate connection-key allowlist: driver, base-url, api-key-ref, header-refs, openai, gemini, provider-retry. The new property is not currently publishable.
- `ExecutionConfigurationParser.java:122` flattens authored fields and uses Spring Boot `Binder`, then `properties.afterPropertiesSet()`. A BindException is rewritten to the binding property's name plus “has an invalid value”; other runtime failures receive generic safe messages. No authored secret value is required in an error.
- Candidate validation substitutes an unresolved-reference marker without reading secrets. Environment preparation resolves references only after syntax and complete skills/model validation; host-map preparation has no Environment fallback, rejects missing/blank/unused values, and copies the supplied map at the public reloader boundary.
- Session `mission-timeout` is separately bound. The transport timeout is not currently inferred from it or from `spring.ai.*`.

Focused existing tests: `LoomspanPropertiesTest`, `ExecutionConfigurationParserTest`, `PublicSkillReloadIntegrationTest`, and `SkillGenerationManagerTest`. Their current configuration fixtures contain no new timeout property.

## Actual provider construction and source findings

`src/main/java/ai/loomspan/internal/springai/SpringAiProviderIntegration.java:121` constructs `ProviderConnectionRuntime` for each driver, stores framework retry policy and exact attempt ownership, and attaches its exception translator.

### OpenAI and OpenRouter

`SpringAiProviderIntegration.java:134` builds OpenAiChatOptions with API key and `maxRetries(0)`, optionally service-root base URL, organization/project headers and static headers. It builds the actual OpenAiChatModel from those options. No timeout is set by Framework.

Resolved source chain, inspected directly from local source JARs:

- `org.springframework.ai:spring-ai-openai:2.0.0`, `AbstractOpenAiOptions.java`: default timeout is `Duration.ofSeconds(60)`, selected when constructor timeout is null; builder exposes `timeout(Duration)`.
- `OpenAiChatModel` supplies configured options to `OpenAiSetup`; `OpenAiSetup.java:159` and `:170` pass the resolved duration to SDK/client builders.
- `SpringAiOpenAiHttpClient.java:440` creates Stainless Timeout with its request duration set. `:537` maps connect, read, write and request durations to OkHttp connectTimeout/readTimeout/writeTimeout/callTimeout. Per-call overrides follow the same mapping at `:178`.
- `com.openai:openai-java-core:4.39.1`, `Timeout.kt:26`: connect defaults to one minute independently; `:35` and `:44`: read and write default to request(); `:56`: raw SDK request default is ten minutes, but Spring AI overrides it to 60 seconds in this path.

Thus changing the connection-level options timeout reaches the actual HTTP call and also changes inherited read/write inactivity limits. It does not change the separate connect default. Timeout is a transport/client setting, not completion request JSON.

OpenRouter is the OpenAI driver with compatibility profile OPENROUTER, not a separate transport. `SpringAiProviderIntegration.java:152` installs a response interceptor on that same actual client. `inspectOpenRouter` at `:213` reads the complete original response body (bounded to 1 MiB + 1 byte), rejects oversized captures and error completions, and reconstitutes successful JSON body bytes. This original body consumption occurs under OkHttp's call deadline. Timeout coverage therefore includes inspection/body reading, including provider whitespace keepalives.

### Anthropic

`SpringAiProviderIntegration.java:158` builds AnthropicChatOptions with API key, `maxRetries(0)`, headers and optional base URL; no Framework timeout override.

Resolved `org.springframework.ai:spring-ai-anthropic:2.0.0` source:

- `AnthropicSetup.java:302` chooses the configured duration or its 60-second default and passes it to both official client options and the Spring AI HTTP transport.
- `SpringAiAnthropicHttpClient.java:492` creates a Stainless request timeout from Duration; `:599` maps phases to actual OkHttp timeouts.
- `com.anthropic:anthropic-java-core:2.40.1`, `Timeout.kt` uses the same connect=one-minute, read/write=request fallback as OpenAI.

Its builder propagation has the same whole-call/inactivity distinction and millisecond transport boundary.

### Gemini (API key and Vertex AI)

`SpringAiProviderIntegration.java:170` starts both authentication modes with the same `Client.builder().httpOptions(oneAttemptGoogleHttpOptions())`. API-key mode sets apiKey; Vertex mode sets vertexAI/project/location and optionally loads explicit credentials from URI or JSON. `:203` sets SDK attempts(1), and GoogleGenAiChatModel gets a zero-retry Spring template.

Resolved `com.google.genai:google-genai:1.58.0` source was obtained with `mvn -q dependency:get '-Dartifact=com.google.genai:google-genai:1.58.0:jar:sources' '-Dtransitive=false'` (dependency source only, no provider requests):

- `types/HttpOptions.java:50` declares timeout as `Optional<Integer>` in milliseconds; builder timeout(Integer) at `:186`.
- `ApiClient.java:114`/`:264` build defaults for API-key/Vertex mode, and `:119`/`:273` merge custom options in both modes.
- `ApiClient.java:288` disables connect/read/write timeouts by default; `:293` applies configured timeout through `builder.callTimeout(Duration.ofMillis(timeout))`.
- `HttpApiClient.java:85` and `:130` apply per-request HttpOptions timeout the same way for synchronous/asynchronous calls. This does not mean Framework currently uses asynchronous or streaming provider calls.

No timeout is currently supplied; omission preserves the SDK's empty optional call limit. Existing `SpringAiProviderIntegrationTest#googleDirectInvocationMakesOneHttpAttemptOnRetryableFailure` sets `Client.setDefaultBaseUrls` to a local MockWebServer and restores it in finally. That enables actual Gemini adapter HTTP tests without introducing a supported production base-url field. Global endpoint overrides require isolation in tests. Existing Vertex credential test uses generated local RSA service-account JSON and builds a client without a model request.

### Precise representation boundary

Resolved `com.squareup.okhttp3:okhttp:4.12.0`, `OkHttpClient.kt:936` converts Duration to milliseconds. `internal/Util.kt:276` requires nonnegative values and milliseconds <= Integer.MAX_VALUE. Underlying zero is unlimited. Converting a Duration to milliseconds floors fractional milliseconds, and Duration.toMillis can overflow before transport validation. Google independently accepts only Integer milliseconds.

The shared representable positive timeout interval is 1..2,147,483,647 milliseconds (24 days, 20 hours, 31 minutes, 23.647 seconds). Sub-millisecond positive values can otherwise become zero/unlimited via conversion; non-integral millisecond precision is not retained by the transport. The ticket requires rejecting sub-resolution/unrepresentable values rather than silently creating an unlimited deadline. The planning step must settle the supported authored precision explicitly against this boundary.

### Ollama

`SpringAiProviderIntegration.java:186` uses Spring RestClient with SimpleClientHttpRequestFactory and unchanged system-default connect/read timeouts. This does not expose an equivalent whole-call budget in the current transport. Its native retry template is disabled. No Ollama transport change is authorized by the ticket.

## Framework retry, cancellation, and mission ownership

- `SpringAiProviderIntegration.java:260` traverses bounded exception causes. SocketTimeoutException maps to transient TIMEOUT. InterruptedIOException only maps to transient TIMEOUT when cause messages indicate timeout and neither cancellation nor interruption is present. InterruptedException/CancellationException/SSLException are permanent UNKNOWN; an interrupted current thread prevents the InterruptedIOException timeout heuristic.
- Existing `SpringAiProviderIntegrationTest` protects timeout wrappers, cancellation and interruption distinctions, typed Google failures, OpenRouter error-completion inspection and bounded diagnostics. Current tests establish classification separately from long-running HTTP timeout behavior.
- `src/main/java/ai/loomspan/internal/chat/ProviderAttemptCallAdvisor.java:65` loops within maxAttempts, checks interruption, reserves provider-attempt quota, records the request, and reuses the same request through `chain.copy(this).nextCall(request)`. Each exception is translated and classified before Framework decides retry/backoff. It records failure and outcome evidence. `:125` waits interruptibly; `:144` aborts on interruption before later attempts.
- `ProviderRetryDecider` owns policy/backoff and only retries transient failures under enabled policy and attempt bounds. SDK native retries are disabled at all current adapters as described above.
- `src/main/java/ai/loomspan/internal/core/MissionLifecycle.java:25` owns admission, cancellation, hierarchical write fences and cleanup, including owning futures and the mission deadline. Logical cutoff and physical return are separate; superseded resources remain retained for physically running work.
- `MissionLifecycleTest` protects first-cancellation ownership, deadline clamping, racing publication/cutoff, future cancellation and physical-completion waiting. `JavaSkillMissionCutoffTest` protects logical closure, noncooperative late return fencing, caller interruption preservation and primary failure ownership. StepLoopMissionExecutionEngine tests cover nested/concurrent mission behavior. Actual longer-provider-budget/shorter-mission tests remain required by the ticket.

## Complete generation publication and copies

- `src/main/java/ai/loomspan/autoconfigure/LoomspanAutoConfiguration.java:184` supplies a runtime factory that creates ExecutionRuntime and a NamedAiConnectionRegistry using real SpringAiProviderIntegration. Integration/wiring classes are infrastructure, not application extension contracts.
- `src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java:128` prepares explicit configuration: parse without secrets, validate the complete document set, resolve Environment or supplied-map references, then construct catalog/runtime resources. `:148` validation parses and checks the set without constructing provider clients. `:328` creates runtime resources only during preparation; failure closes any constructed runtime.
- `SkillGenerationManager.java:112` skill-only preparation uses `currentProperties` and `currentPolicy`; `:163` reads active runtime configuration instead of mutable startup settings. Therefore explicit copy completeness controls retention of any new connection field.
- `src/main/java/ai/loomspan/internal/skill/ExecutionRuntime.java:33` snapshots properties; `:56` explicitly copies each connection into a new binding object and copies its provider options/retry policy. A future field needs an explicit entry in this copy.
- `DefaultSkillReloader.java:73` exposes existing configuration preparation; `:80` provides host-map credential resolution. `:120` publishes a frozen prepared candidate after owner/base/single-use/shutdown checks. Host-map preparation errors are generically wrapped except safe credential-reference messages, as the existing security contract requires.
- `SkillGenerationManager` leases/retains published generations and releases clients only on final ownership retirement. Root handoff, descendants and retries route through captured generation state, rather than the current global registry.

Existing public integration anchors include `PublicSkillReloadIntegrationTest#publicHostMapPublishesReplacementKeysWithoutDeploymentProperties`, `#pendingRootKeepsProviderRetryPolicyAfterPublication`, `#pendingRootKeepsItsConnectionAcrossPublication`, and `#publishesNewModelAliasAndSkillAsOneCandidate`. The connection publication test already checks skill-only update retention. `SkillGenerationExecutionIntegrationTest` checks admitted/delayed roots and nested execution bindings. These tests are reusable fixtures, not evidence that the new timeout is already present.

## Supported surface and compatibility inventory

| Surface category | Current evidence and exposure |
| --- | --- |
| Application API | Allowlisted public `ai.loomspan.api.ExecutionConfiguration`, SkillReloader, PreparedSkillUpdate, SkillValidationResult and invocation APIs already expose complete publication. Ticket asks to use these existing signatures, not add types. |
| Supported SPI | RestSkillHandler is the sole supported SPI; no timeout/provider bean replacement surface is supported or requested. |
| Configuration and manifest contracts | `loomspan.connections`, startup unknown-field rejection, candidate field allowlists, credentials references, driver defaults and mission deadline behavior are supported/documented. New request-timeout is a deliberate additive configuration contract. No skill/model override syntax is requested. |
| Persisted or serialized contracts | ExecutionConfiguration contains application-authored YAML; credential references and allowed fields are protected behavior. No persisted trace/schema or response JSON change is requested. |
| Ephemeral diagnostic formats | Existing bounded timeout/failure classifications and per-attempt diagnostics/traces remain current-run evidence. No trace schema change is requested. |
| Internal or accidentally exposed implementation | Public internal provider integration/registry/runtime/parser types and autoconfigure binding types are implementation machinery; their constructors, getters and setters are not supported Java API solely because they are public. |

Spring infrastructure beans in LoomspanAiAutoConfiguration route generation resources and advisors; their presence is not a bean-replacement contract. The authority is `src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java` plus repository AGENTS.md. Production changes require that architecture test. Current evidence establishes an additive configuration contract with omission preserving old behavior; no Java signature compatibility shim is established as necessary. Console REST/SSE/acquisition/problem/NDJSON protocol consumers are not in the affected configuration/client path and no protocol-marker change is part of this ticket.

## Consumer documentation and drift

Applied repository-local `agent-skills/loomspan-docs/SKILL.md` as a router after executable evidence inventory. Read skill-authoring index, source-verification protocol, model-selection-and-connections topic, Java API index and skill-reload topic. Its metadata matches the checkout version.

- README.md:94 documents named connections and no `spring.ai.*` inheritance; :96 describes Framework retries; :198 describes full publication and both credential paths; :273 directs integration tests to local compatible endpoints and supported public APIs.
- `agent-skills/loomspan-docs/references/skill-authoring/model-selection-and-connections.md` owns the connection table, supported keys, client defaults/generation capture and credential paths.
- `agent-skills/loomspan-docs/references/java-api/skill-reload.md` owns validation/preparation/publication lifecycle and captured settings.
- Skill-authoring index coverage row for model selection/connections describes the current coverage.

Drift classification: **aligned** for current strict connection fields, no Spring AI inheritance, disabled native retries and complete generation publication. Request-timeout is absent both from implementation and current connection docs, matching its status as a new ticket requirement. Skill-authoring impact exists because applications/skill authors choose connection budgets and must understand their relation to mission duration and retries; connection guidance and implementation anchors will require atomic updates. No version mismatch or scope-changing documented/code contradiction was found.

## Downstream evidence and scope

Read the retained reproduction's `docs/implementation-status.md` and `docs/implementation-handoff.md` in `C:/opendev/code/loomspan-sidecar-test-suite`, and inspected the relevant checked-in connection fields in config/java.yaml and config/sidecar.yaml without extracting secrets. Both hosts use OpenRouter compatibility profile and 600-second mission budget in the ticket's retained scenario. The handoff pins released Framework beta.7 in embedded/Sidecar beta.2 and explicitly says the complete live workflow/final synthesis remains unverified. Earlier chunked-response timeout evidence remains the ticket's historical observation; this step made no new live-model requests.

The current Framework checkout is beta.8-SNAPSHOT, not an available updated Sidecar release. No downstream updated-host delayed-response verification was performed and no availability claim was inferred. Ticket acceptance requires equivalent local delayed-response scenarios through an updated embedded host and Sidecar host, including Sidecar configuration/publication retention. If an updated Sidecar artifact is absent at verification time, that check remains pending on a Sidecar dependency/release consuming the updated Framework. Sidecar source changes are out of scope. Full paid-model workflow success also remains separate future verification.

## Historical context and related research

The developer's PR 13 ticket is the retained detailed provider-source/reproduction history. A repository search found no separate timeout/provider-generation research artifact to substitute for fresh source investigation. The feature-design lens provides the exact supported-surface categories used above. Existing publication tests and docs provide executable lifecycle evidence independently of historical tickets.

## Open questions for planning and verification

1. Specify the authored supported precision at the integer-millisecond boundary (whether reject all fractional milliseconds or document safe truncation above the 1-ms minimum). The ticket prohibits sub-resolution/unlimited conversion and silent clamping; no current request-timeout precision contract exists.
2. Select deterministic local response fixtures/margins for delayed headers and continuously active body consumption across all actual adapters and both OpenAI profiles. Existing MockWebServer and Gemini endpoint-override fixtures provide the needed mechanisms; no production Gemini base-url expansion is needed.
3. Record exact downstream updated Framework and Sidecar artifact identities/availability in implementation verification; unavailable updated Sidecar remains explicitly pending. This is not a discovered Sidecar defect or authorization to edit its repository.

These are bounded planning/verification items resolvable from the approved ticket and current evidence, not developer escalations. No substantial additional publication API work or unexpected SDK inability was established during research.
