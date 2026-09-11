# Actionable Provider Failures Implementation Plan

## Overview

Make provider configuration and request failures understandable at the framework-owned attempt boundary. Loomspan will translate the missing OpenAI SDK HTTP exception family, produce one conservative framework-authored explanation using the already-normalized failure facts and model identity, emit that explanation at WARN only when the provider failure is terminal, and store the same text in the existing `MODEL_ATTEMPT_FAILED` diagnostics. Console will follow the existing canonical `ERROR_RECORDED` to final-attempt link and expose those attempt diagnostics in failure details; it will not classify provider errors itself.

## Current State Analysis

`ProviderAttemptCallAdvisor` already owns each physical provider send, translation, retry decision, failed-attempt trace emission, and the final failure registration, but it has no logger (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/chat/ProviderAttemptCallAdvisor.java:61-87`). The translator recognizes Google, Anthropic, Spring REST, and transport failures, while OpenAI Java 4.39.1 `OpenAIServiceException` values fall through to `UNKNOWN` (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/springai/SpringAiProviderIntegration.java:233-282`; `pom.xml:75-76`).

The existing attempt record already contains framework model, connection, driver, provider model, normalized failure metadata, a bounded stack, and provider diagnostics (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/ModelTraceContext.java:65-79`; `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/DefaultExecutionTraceRecorder.java:59-70`). Console preserves the final attempt link on the canonical failure, but its failure panel does not load the linked attempt content (`loomspan-console/internal/traceanalysis/failures.go:131-149`; `loomspan-console/web/src/observability/TraceFailureFocus.tsx:10-32`).

Connection construction deliberately discards unsafe exception details, yet its generic message does not identify the configuration property to inspect (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/autoconfigure/NamedAiConnectionRegistry.java:37-47`).

## Desired End State

A runtime provider failure has one normalized framework interpretation, one actionable message derived from that interpretation plus `ModelExecutionIdentity`, and one existing failed-attempt record. Every failed attempt retains stack-first diagnostics followed by framework guidance and available provider evidence. Only a failed attempt that will escape (`DO_NOT_RETRY` or `ATTEMPTS_EXHAUSTED`) emits the message at WARN, so recovered attempts do not warn and exhaustion does not warn once per retry.

The message names the framework model, connection, driver, and provider model and points to relevant property names without disclosing configured values or asserting an ambiguous cause. Authentication points to the driver-appropriate credential configuration; authorization/access failures also point to model access; connectivity points to the named connection's `base-url` and network reachability; invalid request/404 points to both endpoint and provider-model settings without claiming that the model is nonexistent. Provider bodies/type/code/summary remain evidence, not a second classification source.

For terminal failures, Console resolves the already-validated final provider attempt and presents its existing diagnostic payload in the failure panel. The same guidance text visible in WARN is therefore visible beside the original attempt evidence without copying it to a new event or reclassifying it in Go/TypeScript. Malformed client-construction failures continue to fail startup with a safe message naming `loomspan.connections.<name>.base-url` and no unsafe cause.

### Key Discoveries

- HTTP status classification already preserves 408/429/500/502/503/504 as transient and makes 401/403/404 permanent; the OpenAI translation must reuse this exact function to avoid retry drift (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/springai/SpringAiProviderIntegration.java:334-345`).
- Retry tests already protect one failed-attempt record per physical attempt, stable retry identity, final exhaustion, and no canonical error for recovery (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/chat/ModelAttemptCallAdvisorIntegrationTest.java:335-411,488-573`).
- The OpenAI SDK exposes status, headers, body, error type, and error code through `OpenAIServiceException`; its `Retry-After` header can use the existing parser. This was verified against the pinned 4.39.1 source archive.
- `TraceAttemptDiagnostics` already performs bounded contiguous content reads and renders every descriptor as inert text, so the new guidance remains part of the existing diagnostic system (`loomspan-console/web/src/observability/TraceAttemptDiagnostics.tsx:33-81,94-175`).
- `failureGraph.validateTerminalAttemptLink` already resolves the authoritative final attempt and is the correct place to retain its payload reference for the derived failure projection (`loomspan-console/internal/traceanalysis/failures.go:131-149`).

## What We're NOT Doing

- No public provider exception, public API/SPI, replaceable Spring bean contract, configuration switch, or invocation-contract change.
- No startup network probe and no relocation of client construction merely to create a trace.
- No provider-specific classifier in Console, duplicate failure event, or duplicate terminality rule.
- No change to retry status mapping, attempt ownership, retry counts/backoff, or response-only usage accounting.
- No secret scanner, scrubber, redaction framework, or provider-content parser beyond the typed OpenAI exception data already exposed by the SDK.
- No historical trace reader, fallback, compatibility shim, or support for mismatched Console versions.
- No claim that all authentication failures mean a missing key, that connectivity proves a bad URL, or that HTTP 404 proves a missing model.
- No cleanup of the currently unused `baseUrlAlreadyEndsWithV1` helper; it is unrelated dead code and removing it would expand this diagnostic ticket.

## Skill-Authoring Documentation Impact

**Impact**: Affected

- **Rationale**: Skill authors and application developers need to know where model/connection failures appear, which identities and property names they contain, how retry-terminal WARN behavior differs from attempt-level trace evidence, and how to reach the linked attempt evidence in Console.
- **Documents to update**: `README.md`; `agent-skills/loomspan-docs/references/skill-authoring/model-selection-and-connections.md`; `agent-skills/loomspan-docs/references/skill-authoring/traces-and-debugging.md`.
- **Supporting evidence**: `SpringAiProviderIntegrationTest`, `ModelAttemptCallAdvisorIntegrationTest`, `SensitiveConnectionDataRedactionTest`, `ConsoleTraceFixtureCorpusTest`, Go fixture/adapter tests, and `TraceExplorer.test.tsx` will establish the documented translation, warning cardinality, configuration names, trace ordering, linkage, and Console access path.
- **Coverage table update**: Not required. The README already routes model/connection diagnosis and trace debugging to the two affected source-verified topics; this change fills behavior within those existing topic boundaries without changing their coverage classification or routing.
- **LLM-first usability**: Add compact failure-category/property guidance and an exact debugging sequence. Keep framework-enforced facts, conservative interpretation, and sensitive-content limitations distinct; link between the two topics instead of duplicating provider/retry background.
- **Drift classification**: The existing model/connection/retry and stack-first trace claims are **aligned** with executable behavior. Normal terminal WARN guidance and direct failure-panel access are currently undocumented and absent in code; this is an intended new behavior, not evidence of an existing promise. The installed router reports `0.1.0-SNAPSHOT` while the checkout is `1.0.0-beta.3-SNAPSHOT`, so version-sensitive decisions use checked-out source/tests/docs; the routed checked-in topic contents match the installed copies inspected during research.

## Contract and Compatibility Impact

| Surface | Impact and supporting evidence | Planned compatibility treatment |
| --- | --- | --- |
| Application API | `SkillTemplate` still returns/wraps failures through existing `SkillException`; the closed allowlist remains unchanged (`LoomspanPublicSurfaceArchitectureTest.java:20-39`). | Preserve; no signature or behavioral invocation-contract change. |
| Supported SPI | None; provider translation/advisor/Console machinery is internal and infrastructure beans are not supported replacement points (`LoomspanPublicSurfaceArchitectureTest.java:48-98`; `LoomspanAiAutoConfiguration.java:24-78`). | Preserve absence of SPI. |
| Configuration and manifest contracts | Existing `loomspan.connections.<name>` and `loomspan.models.<name>.provider-model` shapes/defaults are unchanged; only diagnostics name those properties (`README.md:93-130`; `LoomspanProperties.java:106-271,396-517`). | Preserve accepted configuration and defaults. |
| Persisted or serialized contracts | No application-owned durable provider-diagnostic format is introduced. | No change. |
| Ephemeral diagnostic formats | The existing attempt diagnostic array gains a framework guidance descriptor; the derived Console failure projection gains the linked attempt content reference. Java, Go, MCP/browser DTOs, fixtures, and UI move together. | Additive current-version change; keep exact writer/reader/projector coherence and existing bounds/order. No old-format fallback. |
| Internal or accidentally exposed implementation | Internal translator, failure details/guidance, advisor, startup registry message, Console indexes/DTOs, and UI are updated in place. | Atomic update with no shim or parallel path. |

- **Evidence of supported contracts**: The public-surface architecture allowlist, README configuration documentation, exact-version trace policy, and ticket requirements. Technical visibility of internal records/classes is not support evidence.
- **Intentional compatibility changes**: The current-version ephemeral failure projection and diagnostic payload gain actionable information. No supported application/configuration contract breaks.
- **In-repository consumers to update**: Java translator/advisor/registry/tests; trace fixture generator and committed corpus; Go attempt/failure indexes, browser and MCP projections/tests; TypeScript contracts/components/tests; root and skill-authoring documentation.
- **Public-surface delta**: None—no added/removed allowlisted type, public API signature, supported constructor, or Spring extension point.
- **Shim decision**: **No shim.** The affected Java/Go/TypeScript structures are internal/current-version diagnostic machinery, and an atomic additive update is sufficient.
- **Java-to-Go boundary coordination**: **Required.** The Java `MODEL_ATTEMPT_FAILED` writer, terminal attempt link, committed NDJSON fixture, Go derived failure index/content reference, browser/MCP DTOs, UI reader, and tests must ship together.
- **Compatibility-marker decision**: Keep `consoleCompatibilityVersion` at the repository release string. The diagnostic descriptor is accepted by the existing generic payload reader, older valid descriptors remain readable, and the new derived field is additive/optional; this is not a contradictory schema or semantic reinterpretation requiring a new release string. Continue exact unequal-release rejection tests and do not add legacy readers.
- **Pipeline notes alignment**: **No notes.** The ticket explicitly authorizes a coherent current diagnostic representation change and requires the marker assessment above; it authorizes no supported compatibility break.

## Implementation Approach

Extend the existing owners rather than introduce a diagnostic subsystem. A small internal guidance formatter will consume only normalized `ProviderFailureDetails` plus `ModelExecutionIdentity`, produce conservative text, and produce a bounded text diagnostic. The advisor computes it once per failed physical attempt, records it with the existing attempt, and passes the exact text to a WARN only in the existing terminal branch. Provider-specific translation remains in `SpringAiProviderIntegration`, retry decisions remain in `ProviderRetryDecider`, and terminal linkage remains in `LoomspanSession`/Console's validated link.

Store the final attempt payload identifier in Console's derived failure index when validating the explicit terminal link. Query projection converts it to the same opaque content reference used by attempt queries. Browser and MCP return that reference; the browser failure panel reuses `TraceAttemptDiagnostics`, whose display label will recognize the framework guidance kind but will not interpret its text.

## Phase 1: Normalize and Explain Provider Failures at the Attempt Boundary

### Overview

Fill the OpenAI translation gap, centralize conservative guidance, emit terminal WARN once, preserve every failed attempt's evidence, and make startup construction errors identify the relevant property name safely.

### Changes Required

#### 1. OpenAI SDK HTTP translation and evidence
**Files**: `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/springai/SpringAiProviderIntegration.java`; `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/springai/SpringAiProviderIntegrationTest.java`

**Changes**:

- Recognize `com.openai.errors.OpenAIServiceException` in the existing bounded cause walk before generic transport matching.
- Feed `statusCode`, first `Retry-After`, bounded `body`, and truncation through the existing `httpFailure` mapping. Retain optional SDK `type` and `code`, and a bounded provider explanation when available, without interpreting 404 beyond `INVALID_REQUEST`.
- Preserve 401/403 permanence and 429/503 transience; cover both permanent and retryable statuses so recognition cannot silently change retry semantics.
- Keep the one-MiB provider diagnostic capture limit and add the ticket-required focused TODO at the raw provider diagnostic capture/emission boundary for future scrubbing. Do not implement scrubbing.

#### 2. Framework-owned actionable guidance and terminal logging
**Files**: `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/provider/ProviderFailureGuidance.java` (new internal type); `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/chat/ProviderAttemptCallAdvisor.java`; `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/provider/ProviderFailureGuidanceTest.java` (new); `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/chat/ModelAttemptCallAdvisorIntegrationTest.java`

**Changes**:

- Format one bounded, safe explanation from normalized classification/category/status and `ModelExecutionIdentity`. Always name the framework model, connection, driver, provider model, and literal property paths; never include configured values.
- Use category-specific but non-speculative wording: rejected credentials/access versus missing credentials; unreachable/timed-out connection versus proven bad URL; ambiguous request/404 versus proven missing model. Include both `loomspan.connections.<name>.base-url` and `loomspan.models.<framework-model>.provider-model` where endpoint/model/access ambiguity exists.
- Represent the same text as `LOOMSPAN_PROVIDER_GUIDANCE` with `text/plain; charset=utf-8`, a fixed bound, and explicit truncation fields. Preserve diagnostic order as `JAVA_STACK_TRACE`, framework guidance, then provider evidence.
- Add the guidance to every existing failed-attempt record, including retryable/recovered attempts. Log the exact guidance at WARN only after the retry decision is known and only in the existing non-`RETRY` branch; do not log the throwable/body at WARN.
- Test no WARN for a recovered retry, exactly one WARN for attempts exhausted, exactly one immediate WARN for a permanent authentication failure, identity/property wording, ambiguity, trace ordering, and unchanged attempt/error cardinality/linkage.

#### 3. Safe connection-construction diagnostics
**Files**: `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/autoconfigure/NamedAiConnectionRegistry.java`; `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/autoconfigure/NamedAiConnectionRegistryTests.java`; `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/autoconfigure/SensitiveConnectionDataRedactionTest.java`

**Changes**:

- Retain eager construction and the no-cause safe wrapper, but make the generic message name `loomspan.connections.<name>.base-url` and the named connection/driver as the setting to inspect for malformed construction input.
- Preserve the existing exact Gemini credential and provider-retry validation messages.
- Extend safe-output tests to prove API key, headers, base URL value, credential URI value, and original unsafe exception message/cause stay absent while the safe property path is present.

### Success Criteria

#### Automated Verification

- [x] OpenAI 401/403/404/429/503 translation tests prove category/classification/evidence and reuse of existing retry rules.
- [x] Advisor tests prove trace guidance identity/settings, stack-guidance-provider ordering, zero WARNs for recovery, and exactly one terminal WARN for permanent/exhausted failure.
- [x] Construction tests prove the safe `base-url` property path and continued value/cause suppression.
- [x] Existing retry/attempt/linkage suites remain green.
- [x] Public API architecture remains unchanged: `LoomspanPublicSurfaceArchitectureTest`.

---

## Phase 2: Project the Linked Attempt Diagnostic into Console Failure Details

### Overview

Use the canonical terminal provider-attempt link to expose the original attempt payload from Console's failure path without duplicating or interpreting provider facts.

### Changes Required

#### 1. Derived Go failure projection
**Files**: `loomspan-console/internal/traceanalysis/failures.go`; `loomspan-console/internal/traceanalysis/model.go`; `loomspan-console/internal/traceanalysis/query_facts.go`; `loomspan-console/internal/traceanalysis/dto.go`; `loomspan-console/internal/traceanalysis/record_facts.go`; related `attempts_test.go`, `failures_test.go`, `query_facts_test.go`, and browser adapter tests

**Changes**:

- When `validateTerminalAttemptLink` resolves the explicit final attempt, retain that attempt's existing payload ID on the derived failure fact. Do not derive terminality or category from adjacency/status text.
- Encode the payload ID as an opaque `providerAttemptContentRef` in the queried `FailureSummary`; leave it absent for non-provider failures and record-local projections that lack the validated cross-record join.
- Preserve strict rejection of partial/mismatched links, retry-linked attempts, and non-final attempts.

#### 2. Browser, MCP, and UI presentation
**Files**: `loomspan-console/internal/browserapi/trace_analysis.go`; `loomspan-console/internal/browserapi/trace_analysis_test.go`; `loomspan-console/internal/mcpadapter/traces.go`; `loomspan-console/internal/mcpadapter/*trace*_test.go`; `loomspan-console/web/src/api/contracts.ts`; `loomspan-console/web/src/observability/TraceExplorer.tsx`; `loomspan-console/web/src/observability/TraceFailureFocus.tsx`; `loomspan-console/web/src/observability/TraceAttemptDiagnostics.tsx`; `loomspan-console/web/src/observability/TraceExplorer.test.tsx`

**Changes**:

- Carry the optional linked attempt content reference through browser and MCP failure DTOs as navigation evidence, not a classification.
- In the selected terminal failure panel, reuse `TraceAttemptDiagnostics` with that reference so the framework guidance and original stack/provider evidence load together. Preserve target/imported scope verification, artifact-unavailable handling, bounded range traversal, and inert text rendering.
- Label `LOOMSPAN_PROVIDER_GUIDANCE` as “Loomspan provider guidance”; retain the generic label for unknown future kinds.
- Test that selecting a linked terminal failure automatically exposes the guidance, does not load raw records, keeps non-provider failures unchanged, handles unavailable artifacts, and never uses UI wording/status to infer cause.

### Success Criteria

#### Automated Verification

- [x] Go trace-analysis tests prove only a valid final provider attempt contributes the opaque content reference.
- [x] Browser/MCP contract tests preserve the reference and omit it when no provider link exists.
- [x] React tests prove the failure panel renders the same guidance descriptor through existing exact-range loading for target and imported evidence.
- [x] Existing terminality, failure diagnostic, attempt query, range, and generic descriptor tests remain green.

---

## Phase 3: Coordinate Fixtures and Author-Facing Guidance

### Overview

Make the Java-to-Go corpus and documentation describe the completed same-version behavior precisely.

### Changes Required

#### 1. Cross-language semantic fixture
**Files**: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/runtime/trace/ConsoleTraceFixtureCorpusTest.java`; `loomspan-console-fixtures/traces/terminal-provider-failure-actionable.ndjson` (new generated fixture); matching expected fixture files; `loomspan-console-fixtures/README.md`; `loomspan-console/internal/traceanalysis/fixture_corpus_test.go`; relevant browser/MCP semantic fixture tests

**Changes**:

- Generate a terminal provider failure with model/connection/provider-model identity, one final attempt, stack-first guidance/provider evidence, and canonical error/final completion links.
- Assert Java output, Go ingestion/index/reference, exact content-range text, browser/MCP projection, and terminal-vs-attempt semantics.
- Keep the current exact release marker and existing unequal-marker rejection coverage; do not introduce an old/new fixture pair or fallback reader.

#### 2. Documentation
**Files**: `README.md`; `agent-skills/loomspan-docs/references/skill-authoring/model-selection-and-connections.md`; `agent-skills/loomspan-docs/references/skill-authoring/traces-and-debugging.md`

**Changes**:

- Document normal terminal WARN behavior, named identities/property paths, category-specific conservative wording, and the distinction between retry attempt diagnostics and terminal warning cardinality.
- Document stack-guidance-provider ordering and the Console path from terminal failure to the linked attempt diagnostics.
- State that provider explanations are evidence, ambiguous responses are not proven root causes, raw diagnostics may contain sensitive data, and no general scrubbing is performed.
- Retain current routing/coverage table entries because topic scope and confidence do not change.

### Success Criteria

#### Automated Verification

- [x] Fixture regeneration followed by a clean corpus verification produces no diff.
- [x] Java and Go fixture tests agree on exact current-version semantics and content references.
- [x] Documentation claims map to the cited production paths, tests, and fixture; the skill-authoring README's LLM-first acceptance questions remain satisfied.
- [x] Full Java, Go, web, and Console build verification passes.

## Testing Strategy

### Unit Tests

- Translate typed OpenAI HTTP failures, including retryable and permanent statuses, bounded evidence, optional code/type, and `Retry-After`.
- Format safe, identity-rich, non-speculative guidance for authentication, authorization, connectivity, timeout/server, invalid request/404, rate limit, and unknown categories.
- Verify startup construction diagnostics name property paths but suppress configured values and unsafe causes.
- Validate Go final-attempt content-reference enrichment and absence on invalid/non-provider links.

### Integration Tests

- Exercise provider attempts through the advisor to prove retry cardinality, warning cardinality, exact trace order, recovery behavior, and terminal linkage.
- Exercise the Java-generated terminal fixture through Go trace analysis, browser/MCP projections, content range reads, and React failure presentation.

See `ai/thoughts/plans/2026-09-10-actionable-provider-failures-testing.md` for the failing-test sequence and full verification matrix.

## Performance Considerations

Guidance formatting is constant-time per failed attempt and bounded. Only the terminal WARN is emitted. Console loads linked attempt content only while showing a selected failure and retains the existing paged/ranged bounds; no startup or runtime network probe is added.

## Migration Notes

No application migration is required. Configuration keys, public invocation types, and retry defaults remain unchanged. Java/Go/TypeScript/fixture changes must land atomically; no compatibility shim or trace backfill is planned.

## References

- Original ticket: `ai/thoughts/tickets/loomspan-pr-1-actionable-provider-failures.md`
- Related research: `ai/thoughts/research/2026-09-10-actionable-provider-failures.md`
- Design lens: `ai/thoughts/framework-feature-design-lens.md`
- Evidence protocol: `ai/commands/shared/loomspan-docs-protocol.md`
