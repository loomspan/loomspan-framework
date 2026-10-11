# PR 26 — Separate OpenRouter response acceptance from diagnostic capture

## Outcome

Accept valid OpenRouter chat responses larger than 1 MiB without rejecting them
because of Loomspan's diagnostic capture limit. The same valid response should
not fail solely because an OpenAI model is accessed through Loomspan's OpenRouter
compatibility profile rather than directly through OpenAI.

Separate the amount of failure evidence retained from the amount of response
data accepted. This simplifies the behavioral contract even though detecting
OpenRouter errors embedded in successful HTTP responses still requires some
OpenRouter-specific code.

## Requirements

- Remove the successful-response rejection caused by `DIAGNOSTIC_LIMIT_BYTES`
  in the OpenRouter chat inspection path. Do not replace it with another arbitrary
  response-size ceiling or introduce new response-limit configuration.
- Preserve complete successful response data, including answer content, tool
  arguments, and metadata needed by existing consumers. Do not truncate a response
  to make it acceptable or return a partial result as success.
- Preserve detection of OpenRouter errors embedded in HTTP-success responses,
  including `choices` entries with `finish_reason: "error"`. Preserve existing
  failure categories, retry eligibility, and malformed-response failure behavior.
  Error detection must not be limited to the first 1 MiB of the response.
- Retain the existing 1 MiB provider-error diagnostic capture bound and explicit
  `truncated` and `captureLimitBytes` metadata. This bound governs retained failure
  evidence, not successful-response acceptance. Preserve diagnostic content within
  that bound; follow AGENTS.md's fidelity requirements.
- Keep the change narrow. Avoid a broader provider redesign, new transport or
  policy subsystems, changes to other providers' behavior, or a generalized
  compatibility-profile abstraction. Remove buffering or helpers made unnecessary
  by the correction when doing so preserves the required inspection and cleanup.
- Preserve response-resource cleanup on success and failure. Do not remove
  embedded-error handling merely to remove the size restriction.

## Acceptance criteria

- [x] A valid chat response larger than 1 MiB is accepted through both ordinary
  OpenAI configuration and the OpenRouter compatibility profile, with the complete
  result and relevant response data preserved. The diagnostic threshold no longer
  determines success, including at the threshold boundary.
- [x] OpenRouter embedded errors remain failures with their existing classification
  and retry behavior, including when the error appears beyond the former 1 MiB
  inspection boundary. Malformed responses do not become successful results.
- [x] Provider-error diagnostic evidence remains bounded at 1 MiB, with truthful
  truncation and capture-limit metadata. Truncating that evidence does not alter
  successful results or prevent recognition of the provider error.
- [x] Response resources are released on success and failure, and the correction
  adds no new response-size setting, replacement byte ceiling, provider subsystem,
  or changes to other providers' behavior.

## Context

`SpringAiProviderIntegration` enables an interceptor when the `OPENAI` driver's
`openai.compatibility-profile` is `openrouter`. Its `inspectOpenRouter` method reads
at most `DIAGNOSTIC_LIMIT_BYTES + 1` bytes and rejects oversized successful HTTP
responses before examining the JSON for embedded errors. Ordinary direct OpenAI
connections do not receive that interceptor. The limit is applied inside Java's
provider integration; it is not a Java–Go transport constraint.

Captured provider-error diagnostics flow into `MODEL_ATTEMPT_FAILED` trace records
through `ProviderAttemptCallAdvisor` and `DefaultExecutionTraceRecorder`. Retaining
a bounded diagnostic copy is useful independently of accepting the full response.

Review of OpenRouter's [limits documentation](https://openrouter.ai/docs/api_reference/limits)
and [chat API reference](https://openrouter.ai/docs/api/api-reference/chat/create-a-chat-completion)
did not establish a universal 1 MiB response limit. Do not present Loomspan's local
restriction as a documented OpenRouter service policy. The restriction is already
present in initial commit `8c464eae`; available history does not establish its
original rationale.

Provider-specific behavior should address a documented protocol difference or a
demonstrated integration need. Embedded-error detection meets that criterion;
the route-specific successful-response byte restriction has no established
justification. Acceptance remains subject to ordinary parsing, model/provider
constraints, and existing timeout/cancellation behavior; this ticket does not
promise unlimited resources.

This is a standalone correction identified during TypeSafe planning. It does not
implement TypeSafe, settle its outstanding design decisions, or depend on the
TypeSafe feature. No TypeSafe implementation or broader provider migration is
required first. PR 26 is the proposed identifier requested for this ticket, not
an assertion that a GitHub pull request already exists.

## Execution profile

- **Recommended:** Fast-Track 2-Step Pipeline — Implementation & Review
- **Confidence:** medium
- **Rationale:** The desired behavior and implementation boundary are settled:
  correct one provider inspector while preserving existing error handling and
  diagnostic contracts. Targeted investigation, focused verification, and
  independent review should provide sufficient assurance. This does not change
  the external wire protocol or serialized trace format.
- **Reassessment triggers:** Broader transport or lifecycle changes, changes to
  serialized diagnostics or supported API contracts, or evidence that preserving
  full-response inspection requires material design beyond this bounded correction.

## Pipeline notes

- Allowing valid OpenRouter responses that previously failed solely for exceeding
  the diagnostic capture limit is intentional. Do not preserve that rejection
  through a compatibility shim, a replacement size limit, or a new configuration
  toggle.

## Execution notes

- The developer approved **Fast-Track 2-Step Pipeline — Implementation & Review**
  with “proceed” after Step 0. Step 4 is ticket-led; no research or plan artifacts
  are required. Independent Step 5 review remains required.
- Initial checkout had no tracked edits. This untracked ticket is in scope;
  unrelated untracked `ai/thoughts/phases/` content is preserved.
- Surface assessment: the active inspector and removed duplicate Spring response
  inspector/replay wrapper are internal implementation. Only the OkHttp inspector
  is wired into production; the duplicate Spring path had only test callers.
  No Application API, Supported SPI, configuration keys/defaults, serialized
  diagnostic format, or Java–Go boundary changes are needed. No shim is required
  for internal helper removal or the intentionally removed successful-response
  rejection. Other provider behavior remains unchanged.
- Complete successful HTTP bodies are read for existing embedded-error inspection
  and replayed intact to the SDK. This retains the existing buffering mechanism
  without imposing a replacement byte ceiling. Original response streams close
  before inspection/replay on success and failure, including read failures.
  Existing parsing and timeout/cancellation constraints still apply.
- Embedded-error type/category/retry classification remains unchanged. Error
  detection reads beyond the former diagnostic boundary. Diagnostic text remains
  bounded at 1 MiB and truthfully marks truncation; malformed error completions
  also retain bounded evidence. Malformed JSON retains the active inspector's
  existing failure behavior. The stale diagnostic-scrubbing TODO was removed to
  align this touched capture path with AGENTS.md fidelity policy.
- Skill-authoring impact: **Affected** because connection response limits and
  debugging guidance matter to authors. The version-aligned repository
  `loomspan-docs` model-selection topic and coverage entry clarify acceptance
  versus diagnostic capture, with focused executable anchors. Drift assessment:
  **aligned** for existing explicit profile/error handling; the previously
  unspecified response-acceptance distinction is now documented against this
  correction. No broader documentation or protocol redesign is necessary.
- Verification fixtures cover both ordinary OpenAI and OpenRouter at exactly
  1 MiB minus 1, 1 MiB, 1 MiB plus 1, and 2 MiB, comparing complete answer/tool
  arguments and response metadata. Error fixtures verify exact bounded diagnostic
  prefix, truthful metadata, classification, cleanup, and errors after 1 MiB.
  The existing advisor retry/trace recovery test now places the embedded error
  after 1 MiB.

- Step 4 verification: `mvn -q "-Dtest=SpringAiProviderIntegrationTest,LoomspanPublicSurfaceArchitectureTest,ConnectionProtocolTest,ProviderRequestTimeoutIntegrationTest,ModelAttemptCallAdvisorIntegrationTest" test`
  passed architecture (8), protocol (10), timeout (15), and advisor (14) tests.
  Initial test compilation errors and an incorrect lowercase SDK finish-reason
  expectation were corrected; final `mvn -q "-Dtest=SpringAiProviderIntegrationTest" test`
  passed all 12 provider tests. Together these establish all acceptance criteria.
  `git diff --check` passed. The full repository test suite was not run because
  these focused checks cover the bounded change and affected integration paths.
  No optional manual checks are required. Independent Step 5 review remains.
