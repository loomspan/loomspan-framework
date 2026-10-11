# PR 27.3 — Execute System One through both account routes with existing runtime controls

## Outcome

One framework-owned System One transport executes TypeSafe requests against direct
TypeSafe or OpenRouter, preserves complete answers and native evidence, and honors
existing retry, accounting, timeout, and cancellation boundaries.

## Context and dependencies

Phase 2 of [PR 27.1](loomspan-pr-27.1-typesafe-primitives.md), following
[PR 27.2](loomspan-pr-27.2-typesafe-authoring-contract.md). The
[roadmap](../phases/typesafe-primitives-roadmap.md) owns transport/accounting and
capture semantics; the [examples](../phases/typesafe-primitives-examples.md) give
response mutations. Sol 6.1 is the intended coding agent. Full registered dispatch
is PR 27.4. PR 26's separate chat response-size change is not a prerequisite.

## Requirements

- Add one internal `typesafe` driver and explicitly declared Apache HttpClient 5
  dependency. Refactor existing chat-only connection ownership directly so one registry
  owns creation, normal shutdown, and construction-failure cleanup for both concrete
  resource types. Reuse one client per connection; disable automatic retries and redirects.
  No dummy chat model, synthetic chat response, parallel lifecycle, provider SPI, or
  generic capability framework.
- Reuse connection configuration: required API key, `base-url`, `request-timeout`,
  `headers`, and `provider-retry`. Default the service prefix to
  `https://api.typesafe.ai`; OpenRouter uses `https://openrouter.ai/api`. Append
  `v1/systemone` with normal URI handling, retaining `/api`. Require absolute HTTP(S)
  without query/fragment. Permit custom headers using shared validation but reject
  `Authorization` case-insensitively; generate Bearer authentication from the selected key.
- Send `provider-model` verbatim. Reject incompatible chat options and thinking settings
  on System One models/connections; retain other drivers' validation. No discovery,
  model fallback, route enums, second driver, or credential hot reload via skill reload.
- Use the PR 27.2 immutable request in a Loomspan-owned retry loop: each iteration
  reserves/traces an attempt and sends at most once. Reuse existing budgets,
  `ProviderRetryDecider`, backoff, and failure taxonomy. System One 529 maps to
  `TRANSIENT` / `PROVIDER_OVERLOADED`; chat mappings remain unchanged. Preserve status,
  body, and retry evidence. Extraction failures are terminal, with no retry or repair.
- After HTTP success, decode JSON and require object-valued `answers`. Return its
  complete contents as JSON text, including `{}`, unknown answer names/fields, and
  unexpected values. Never use typed primitive response DTOs or answer-schema,
  membership, range, probability, consistency, or legend checks. Model/usage metadata
  is not required for result success. Preserve the envelope separately in diagnostics.
- Map usable nonnegative integral `input_tokens`/`output_tokens` and representable
  total directly to existing `ModelUsageRecord`, with `EXACT` precision, including
  explicit zero. Missing/partial/unusable counts yield existing zero placeholders
  with `UNAVAILABLE` and native usage retained. No text estimates or `HEURISTIC`.
  Account each successful HTTP response once while writable, even if extraction then
  fails. HTTP errors do not contribute error-body usage; late responses obey the write
  gate. Keep response-based `modelCalls`, quotas, and attempt units unchanged; question
  count is diagnostic only. Cost is optional metadata; totals are not a billing ledger.
- Connect transport cancellation to the existing mission lifecycle, interruption,
  shutdown, bounded cleanup, backoff interruption, and late-write cutoff. Verify actual
  client cancellation, not only future interruption. Document/test exact Apache timeout
  settings and omitted defaults for `request-timeout`; no new per-attempt total deadline
  or timeout configuration. Mission timeout remains overall execution control.
- Impose no additional response-body byte ceiling on either route. Preserve valid
  complete answers larger than 1 MiB. Bound provider-error diagnostic capture at 1 MiB
  with `truncated` and `captureLimitBytes`; capture limits never limit result acceptance.
  Retain normal parser constraints and existing explicit trace capture/size controls.
- Emit native evaluation traces containing resolved request, route via connection/endpoint,
  requested/served models, attempts, response or failure evidence, and outcome, reusing
  captured declaration/generation references. Represent malformed bodies as raw text.
  Deliver affected Java/Go/TypeScript trace consumers and fixtures together, including
  Console model-detail parsing/rendering of ordinary JSON/text without fake chat messages.
  Truthfully expose disabled/truncated capture. Preserve canonical content and the
  unchanged derived-journal observer/projector exception; no new public observer API.

## Acceptance criteria

- [x] Both configuration examples construct the correct endpoint, Bearer credential,
  verbatim model ID, and shared request shape; custom headers work and missing keys,
  invalid URLs, Authorization overrides, and incompatible options are rejected.
- [x] Controlled HTTP fixtures preserve full mixed answers, empty/extended answers,
  native metadata, and unexpected answer values. Unreadable JSON or missing/null/non-object
  answers fail extraction without retries, repair, or fabricated results.
- [x] A 529-then-success case follows enabled bounded retry policy; terminal errors,
  exhausted attempts, disabled retries, redirects, and cancellation produce no hidden
  sends. Every send has a reservation/trace; existing chat classification stays unchanged.
- [x] Exact, exact-zero, partial, missing, invalid/overflow usage and extraction-failure
  accounting follow the stated precision/write boundaries without estimates, duplicate
  accounting, question multiplication, or error-body usage ingestion.
- [x] Real-client fixtures verify configured timeouts, cancellation during HTTP/backoff,
  late-write rejection, and registry cleanup including construction failure; late responses
  cannot revive cancelled success. Timeout documentation matches observed settings.
- [x] Each route preserves a valid response larger than 1 MiB; oversized error evidence
  is bounded and truthfully marked without applying that limit to successful results.
- [x] Current Console trace detail and affected wire consumers read native request,
  envelope, and malformed response evidence as soon as emitted; no chat-only assumptions,
  canonical content rewriting, or deferred consumer repair remains.
- [x] Focused shared-contract and route-specific fixtures pass, as does
  `LoomspanPublicSurfaceArchitectureTest` for production-type changes. Recheck live API
  documentation during implementation; any credentialed live checks are reported separately
  from fixture evidence and do not silently narrow the accepted Score/request contract.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** External HTTP behavior, resource ownership, cancellation, accounting,
  and serialized trace consumers require cross-cutting design and independent review.
- **Reassessment triggers:** If connection ownership and transport cannot be reviewed
  coherently, split their delivery without leaving duplicate lifecycles or incomplete
  trace consumers. Contradictory provider evidence requires revisiting the roadmap.

## Pipeline notes

- Replace obsolete chat-only internal boundaries directly; the accepted roadmap
  rejects compatibility bridges, legacy readers, and dual trace formats. Update all
  affected consumers together. Preserve supported existing chat behavior and deliberately
  maintain API allowlists/documentation/checks for any necessary public changes.

## Execution notes

2026-10-11: User approved the Full 5-Step Pipeline. Steps 1–4 completed against
baseline `4552dff89b6928089e829a58b0f24a504d99064f`; independent Step 5 review is next.
Baseline already contained native provider execution. This change corrects registry
construction ownership/cause/cleanup evidence and adds acceptance-level HTTP,
accounting and coordinated Java/Go/TS native trace assurance. Accepted provider
request/result contracts, public API/SPI, configuration, trace representation,
compatibility marker and derived-journal exception remain unchanged.

Governing evidence: research `../research/2026-10-11-PR-27.3-typesafe-provider-execution.md`,
implementation plan `../plans/2026-10-11-PR-27.3-typesafe-provider-execution.md`, and
testing plan `../plans/2026-10-11-PR-27.3-typesafe-provider-execution-testing.md`.
Plans record red tests, repaired new-fixture/obsolete-test failures and exact
verification receipts. Final `.\mvnw.cmd clean test` passed: 1,577 tests, no
failures/errors, 2 existing opt-in PR 18 diagnostics skipped. Console full Go,
typecheck, production build and 533 web tests passed. Official TypeSafe,
OpenRouter and pinned Apache docs were rechecked; credentialed provider smoke
and accuracy checks were not run and remain optional. No commit was requested.
