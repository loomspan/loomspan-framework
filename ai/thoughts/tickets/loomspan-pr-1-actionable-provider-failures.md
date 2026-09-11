# PR 1 — Make provider failures actionable in logs and traces

## Outcome

When a skill cannot run because its AI provider configuration is wrong, developers should receive an understandable explanation in the application's normal console logging and in the execution trace/Console. They should be able to identify the affected connection or model and the setting to investigate without enabling DEBUG logging or understanding Loomspan internals.

## Context

A user of `C:/opendev/code/loomspan-travel-demo` forgot to configure their API key and received only `Assessment 32eb02a4-d6a8-4626-99af-8cafdc22a02e failed (SkillException)` in the application console. The demo supplies `${OPENAI_API_KEY:not-configured}`, allowing a nonblank placeholder through startup validation, and logs exception details only at DEBUG. Truly blank required keys already have configuration validation; this request does not require moving runtime failures to startup.

Initial source inspection also suggested a gap in translation of OpenAI SDK HTTP exceptions. This is a research lead, not a prescribed patch. The feature belongs in the framework because applications should not each need provider-specific exception interpretation. Changing only the demo's logging is insufficient.

The scope includes rejected credentials, bad provider URLs, and specified provider models that do not exist or cannot be accessed. PR 1 is the developer-assigned proposed number for aligning local tickets with GitHub; it does not assert that GitHub PR 1 exists.

## Requirements

- Follow `ai/thoughts/framework-feature-design-lens.md`. Choose the simplest complete solution and reuse the existing provider-failure translation, retry, and trace mechanisms. Avoid speculative classifiers, new public APIs or SPIs, configuration switches, and parallel diagnostic systems.
- Make terminal provider failures actionable at normal WARN logging, even when an application catches the exception and logs only its class. Identify the connection and model relevant to the failure and point developers toward the relevant configuration. Avoid repeating a terminal warning for every retried attempt.
- Cover rejected or placeholder credentials and access denial; unreachable hosts/connections; wrong endpoint paths; and nonexistent or inaccessible provider models. Malformed URLs rejected during client construction may remain startup errors, but their diagnostics must identify the relevant connection setting. Do not add startup network probes or move client construction solely to obtain a trace.
- Report what is known and preserve useful provider explanations. Do not claim that a key is missing merely because authentication failed, or infer from HTTP 404 alone that the model does not exist: an endpoint path or access restriction can produce an ambiguous failure. Identify the relevant `loomspan.connections.<name>` settings and/or `loomspan.models.<name>.provider-model` without inventing a precise root cause.
- Include the actionable explanation in the existing failed-attempt trace and make it accessible in Console's failure details. Keep classification and guidance authoritative in the framework; Console must not independently classify provider exceptions. Preserve attempt identity, ordering, diagnostic evidence, and existing terminal-failure linkage rather than creating a duplicate failure event.
- Retain existing retry policy semantics. Authentication and other recognized permanent request failures must not become transient retries. Connectivity failures can be temporary and must not be declared permanent merely because a bad URL is suspected. Any correction to previously unrecognized HTTP failures must account for its effect on retryable statuses such as 429 and 503.
- Sensitive-data scrubbing is explicitly outside this development-stage feature. Do not introduce a scrubber, redaction framework, or content-parsing machinery for that purpose. Add a targeted TODO at the provider diagnostic capture/emission boundary for future scrubbing work. Preserve existing protections and diagnostic size limits.
- No intended application API or documented configuration break. Prefer existing trace fields; if current diagnostic representations need to change, keep framework writers, Console readers/projections, and debugging consumers coherent and explicitly assess the compatibility marker during planning. No speculative compatibility shims or historical trace support.

## Acceptance criteria

- [ ] A rejected placeholder or invalid credential produces a normal application-console warning identifying the connection and credential configuration to check, without requiring DEBUG logging or demo-specific provider handling.
- [ ] Bad endpoint and unavailable/nonexistent model cases produce useful diagnostics identifying the connection/model and relevant settings. Ambiguous provider responses are not presented as a proven root cause; malformed URLs that fail at startup identify their configuration setting.
- [ ] For failures during execution, the trace and Console expose the same actionable explanation alongside the original failed attempt and its available provider evidence. No duplicate failure event or separate Console classifier is introduced.
- [ ] Permanent failures and transient failures follow the intended existing retry policy, with terminal warning behavior that does not produce one terminal warning per retry. HTTP translation changes do not silently regress rate-limit/server-error retry behavior.
- [ ] The completed change uses existing framework concepts without a new public API/SPI, configuration switch, or startup network probe; skill authors and application callers need no new invocation contract.
- [ ] No new sensitive-data scrubbing implementation is introduced; a focused future-scrubbing TODO is present and existing protections and capture bounds remain intact.
- [ ] Current diagnostic consumers remain coherent, with any compatibility-marker decision documented and no unintended application API/configuration break or speculative compatibility layer.

## Sequencing

This ticket is input to the five-step research, implementation-plan, testing-plan, implementation, and independent-review process. Preliminary exploratory production edits were backed out before this ticket was created; they are not an approved design or a dependency.
