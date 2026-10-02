# PR 13 — Configure provider request timeouts through supported connection settings

## Outcome

Allow applications to set `loomspan.connections.<name>.request-timeout` for OpenAI-compatible (including OpenRouter), Anthropic, and Gemini connections. Long non-streaming model requests must be able to finish within a configured provider-call budget instead of an inaccessible SDK default. Keep the overall mission deadline authoritative.

PR 13 is a proposed identifier supplied by the developer, not an existing published PR. This ticket authorizes a future implementation pipeline; ticket creation does not implement or publish the change.

## Confirmed findings and reproduction

Investigation on 2026-10-01 inspected Framework checkout `e35ab4488e5cac5e1cc6fef5f0f0797b5deb703d` (`1.0.0-beta.8-SNAPSHOT`), its provider integration and configuration contracts, and resolved dependency sources. The retained reproduction uses Framework `1.0.0-beta.7` in embedded Java and Sidecar `v1.0.0-beta.2`.

Reproduction workspace: `C:\opendev\code\loomspan-sidecar-test-suite`. Read `docs/implementation-status.md`, `docs/implementation-handoff.md`, `config/java.yaml`, and `config/sidecar.yaml`. Both configurations use OpenRouter model `openai/gpt-6.1-sol`, medium reasoning, non-streaming Chat Completions through the real Framework provider client, a `600s` mission timeout, and one provider attempt.

- `evidence/live-20261001-122349/` records failures around 60 seconds while reading response headers. The recording proxy buffered responses in this run, so this run alone does not establish the cause.
- The proxy was changed to forward headers and body chunks promptly, preserving upstream whitespace keepalives and unchanged request/response JSON. `evidence/live-20261001-123114/` still records failures around 60 seconds in both paths. Inspection of the retained Framework-generated NDJSON traces confirms `java.io.InterruptedIOException: timeout`, `okhttp3.internal.connection.RealCall.timeoutExit`, and chunked response-body reads. Receiving headers/body bytes did not remove the whole-call deadline. Each evidence directory retains independent provider journals, terminal results, and actual Framework traces.
- Spring AI 2.0.0 `AbstractOpenAiOptions.DEFAULT_TIMEOUT` is 60 seconds. `OpenAiChatModel` passes this through `OpenAiSetup` to `SpringAiOpenAiHttpClient`, which configures OkHttp's call timeout. Framework `SpringAiProviderIntegration.openAi()` leaves it unset and disables provider-native retries. The OpenRouter profile uses this same client plus its response-inspection interceptor.
- Anthropic's Spring AI 2.0.0 setup also defaults to a 60-second whole-call timeout. Framework leaves it unset. This is a source finding, not a reproduced Anthropic failure.
- Gemini's resolved Google GenAI 1.58.0 client supports a request/call timeout. Framework only sets its retry policy. The SDK's default client disables connect/read/write timeouts and leaves the call timeout unset. This is a source finding, not a reproduced Gemini failure.
- Ollama uses `SimpleClientHttpRequestFactory` with system-default connect/read timeouts. This transport does not supply the same whole-call timeout control.
- `LoomspanProperties.ConnectionProperties` exposes no request-timeout setting. The connection documentation rejects unknown `loomspan.*` fields and does not inherit `spring.ai.*`. `ExecutionConfigurationParser` also has a strict connection-field allowlist. `ExecutionRuntime` explicitly copies connection settings for captured generations.

The earlier dependent-evidence truncation issue was repaired in beta.7. This is a separate configuration gap. Neither retained capture verifies final synthesis or complete workflow success. No live-model calls were repeated for this investigation. Do not copy credentials, authorization headers, or raw sensitive diagnostics into this ticket or follow-up reports.

## Agreed requirements and proposed configuration contract

These are requirements for the change, not claims about existing supported behavior.

1. **One connection setting.** Add `loomspan.connections.<name>.request-timeout` as a positive duration for OpenAI, Anthropic, and Gemini, including OpenRouter and both supported Gemini authentication modes. It is a connection transport setting, not a skill/model override or request JSON parameter. Do not introduce independent connect/read/write settings in this ticket.

2. **Whole-call semantics.** The duration bounds one physical provider HTTP call through consumption of its complete response body, including time spent waiting for headers and model output. Headers, whitespace, and successive body chunks do not restart or satisfy that deadline. It is not merely a connection-establishment or read-inactivity limit. Preserve existing connect-timeout behavior. With the present OpenAI/Anthropic SDKs, read/write inactivity limits derive from the request duration; document that relationship and ensure an inherited 60-second read limit does not defeat a longer configured call budget. Do not claim this setting overrides provider/server/proxy limits.

3. **Defaults and validation.** Omission preserves each driver's existing behavior: OpenAI and Anthropic retain 60 seconds; Gemini retains its unset client-call timeout; Ollama remains unchanged. Do not impose a new universal default. Accept positive durations that the supported client can represent without overflow or conversion to an unlimited timeout. Reject malformed, zero, negative, sub-resolution, or out-of-range values with the full property path and a safe explanation; document the supported precision/range. Do not silently clamp or interpret zero as unlimited. Existing configurations require no migration.

4. **Ollama boundary.** Supporting this setting for Ollama is outside scope. Explicitly configuring it on an Ollama connection must fail validation with a clear unsupported-driver message; omission remains valid. Do not substitute a read timeout for a whole-call timeout or redesign Ollama's transport in this change.

5. **Actual client propagation.** Apply the value to the client that performs the real provider call, not just a bound options object. Preserve OpenRouter's response inspection, error-completion rejection, bounded diagnostics, and response JSON behavior. Preserve existing credentials, model/reasoning selection, and disabled SDK-level retries.

6. **Mission, cancellation, and retry ownership.** A configured provider timeout does not extend or reset a mission deadline. Cancellation and interruption retain their existing behavior and must not be relabeled as a retryable provider timeout. A genuine provider timeout follows the existing classification and Framework retry policy. Each permitted physical attempt gets its connection's call budget; all attempts and backoff still consume the same mission budget and quotas. A `240s` setting does not promise multiple full attempts within a `600s` mission. Preserve existing logical cutoff and physical-resource cleanup semantics.

7. **Existing publication API.** Support the same setting in startup configuration and in `ExecutionConfiguration` through `SkillReloader.validate`, `prepare`, and `publish`, including both credential-resolution overloads. Validation must not make provider calls. Invalid candidates leave the active generation unchanged. Copy the setting into captured execution configuration and retain it in skill-only updates. After publication, new roots use the new value; previously admitted roots, delayed work, descendants, and retries retain their captured value and clients. Use the existing API without adding a Java SPI or public API type. The current parser/copy/client-generation path makes this a bounded extension; if implementation reveals substantial additional API-publication work, stop and discuss that scope with the developer before expanding or dropping it.

8. **Documentation and downstream use.** Document supported drivers, omission defaults, validation, timeout distinctions, API publication, and the Ollama rejection. Show a 180–240-second provider timeout within a 600-second mission budget, explain that the mission budget includes other calls/work/retries, and update the consumer connection references. The same supported Framework property must work in embedded applications and when Framework is hosted by Sidecar.

Example to document after implementation (the property is not supported in the investigated release):

```yaml
loomspan:
  connections:
    reasoning:
      driver: openai
      base-url: https://openrouter.ai/api/v1
      api-key: ${OPENROUTER_API_KEY}
      request-timeout: 240s
      openai:
        compatibility-profile: openrouter
  session:
    mission-timeout: 600s
```

This is a connection/session fragment; existing model aliases and skills remain necessary. Published `ExecutionConfiguration` uses credential references as required by its existing contract, rather than direct credentials from this startup example.

## Acceptance criteria

- [ ] The unified property binds, validates, and reaches real OpenAI, Anthropic, and Gemini clients. Standard OpenAI and OpenRouter profiles both honor it. Omission preserves the documented per-driver behavior, and an explicit Ollama value fails clearly. Invalid values fail safely in startup and API candidates.
- [ ] Controlled local HTTP responses demonstrate success within a configured budget and timeout beyond it. Cover delayed headers and headers followed by a body that continues in chunks/whitespace before the final valid JSON. Continued body activity must not extend the whole-call deadline. Exercise the actual adapters, including OpenRouter response inspection, rather than asserting only an options getter.
- [ ] Regression tests use short, controlled durations with suitable scheduling margins and deterministic endpoint coordination. Default-value assertions do not require minute-long waits. No paid model calls or provider credentials are required for Framework tests.
- [ ] Tests establish that genuine provider timeouts retain retry classification, attempt limits/accounting, and unchanged-request behavior; native retries remain disabled. Cancellation and a mission deadline shorter than the provider budget still stop logical execution without starting later retries/work or publishing late success, consistent with existing cleanup contracts.
- [ ] Publication tests demonstrate validation/preparation behavior, preservation through configuration copies and skill-only updates, rejection without activation, and old-versus-new captured timeout behavior across publication. Nested/retried work uses the captured generation. Both credential-resolution paths accept the setting.
- [ ] Consumer documentation includes the example and all scope/default/semantic distinctions above. No internal bean replacement, new SPI, undocumented `spring.ai.*` override, or application-side timeout workaround is required. Run `LoomspanPublicSurfaceArchitectureTest` when production types change, per `AGENTS.md`.
- [ ] Record the downstream integration check: run equivalent controlled delayed-response scenarios through an embedded application and a Sidecar host containing the updated Framework, using the same property and OpenRouter profile. Verify Sidecar's configuration/publication path accepts and preserves it. Identify the Framework release and Sidecar dependency/release follow-up needed; if the updated Sidecar artifact is unavailable, record that check as pending with its dependency, not passed. Do not modify the Sidecar repository in this ticket or assume a separate Sidecar defect. Any discovered host-specific rejection becomes an evidence-backed Sidecar follow-up.

## Scope and remaining verification

The agreed scope is OpenAI/OpenRouter, Anthropic, and Gemini request timeouts plus existing configuration publication. No Ollama transport changes, separate connect/read knobs, new API surface, or general retry/cancellation redesign are authorized. Internal implementation types and configuration-binding signatures do not need compatibility shims solely because they are public; supported settings and API behavior do require compatibility consideration.

Implementation must verify precise SDK duration limits and client propagation and supply the local regression evidence. Sidecar dependency availability and host-level propagation remain downstream verification items, not confirmed defects. A later authorized real-model rerun of the retained integration scenario is still necessary to establish complete workflow/final-synthesis success; increasing a timeout alone does not establish that result.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** The agreed change affects a supported configuration contract across three provider adapters, transport deadlines, cancellation/retry interaction, captured configuration generations, and downstream hosting. Independent planning, testing, and review are warranted even though the property itself is small.
- **Reassessment triggers:** Unexpected SDK limitations, substantially greater API-publication work, or a need to alter existing lifecycle behavior require scope discussion. Ollama transport support and Sidecar source changes must not be added implicitly.
