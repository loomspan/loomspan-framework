# PR 30 implementation blockers

## Current ownership and historical records

The completed PR 30 migration roadmap, schema research, ticket index, coverage,
and implementation tickets have been retired from the working tree.
This register remains the downstream authority for the open B05 requirement,
approved skips, and closure conditions; the dated verification sections below are
historical records, not claims of fresh verification or future ticket work.
Spring AI contribution planning now lives in the private
[contributions roadmap](https://github.com/loomspan/spring-ai-contributions/blob/main/ai/thoughts/roadmaps/spring-ai-blockers.md).

The original migration decisions, tickets, results and independent review are
preserved in local Git commit `7eb8ac489dd72a45175e3b9fadd02e7bcd01cc30`.
Historical paths in this register refer to that commit; it has not been verified
as published remotely. Retrieve a document without restoring it to the working tree:

```powershell
git show 7eb8ac489dd72a45175e3b9fadd02e7bcd01cc30:ai/thoughts/phases/spring-ai-responses-migration.md
git show 7eb8ac489dd72a45175e3b9fadd02e7bcd01cc30:ai/thoughts/plans/2026-11-14-PR-30.4.3-integrated-migration-readiness-results.md
git show 7eb8ac489dd72a45175e3b9fadd02e7bcd01cc30:ai/thoughts/reviews/2026-11-14-PR-30.4.3-integrated-migration-readiness-review-1.md
```

### Later deployed and live acceptance

The neighboring [test-suite migration acceptance roadmap](../../../../loomspan-sidecar-test-suite/MIGRATION-ACCEPTANCE-ROADMAP.md)
retains the agreed implementation sequence, both-host provider-free/business/TypeSafe
regressions, selected Java-host provider matrix, Sidecar tool-chain cases, coherent
artifact identities, protected fixtures/manifests, and explicit missing/failed cells.
It also retains the earlier OpenRouter comparison scope question, request/token/cost
limits, deadlines, access/pricing preflight, and separate paid-run authorization.
No deployed/live success is established by this cleanup or the completed Framework work.

Framework continues to own deterministic real factory/advisor/tool-path captures,
schema/options fidelity, ordered replay, explicit response status and accounting,
side-effect prevention, Console checks, and supported-public-surface verification.
Native effort, unchanged total generation caps, request-local defaults, legacy behavior,
and unsupported-option rejection must remain covered on initial, continuation, planner,
and model-override paths. Use the suite's manifest as the selected fixture authority.
An isolated SDK probe or merged upstream PR cannot replace integrated acceptance;
missing adapter fidelity blocks the affected acceptance cell until corrected.

Retained protocol tests remain in this repository. Only blocker-related isolated
probes and controls are maintained in the neighboring [contributions workspace](../../../../spring-ai-contributions/probes/README.md),
verified on Liberica 17.0.19. Obsolete probes, historical captures, and the temporary
archive were removed at the developer's request. See the [relocation results](../../../../spring-ai-contributions/ai/thoughts/results/2026-11-14-probe-relocation.md).
No blocker guards or correctness assertions were removed by the documentation cleanup.

## Final-ticket continuation approval — November 14, 2026

The developer explicitly authorized completing PR 30.4.3 while B05 remains
open, testing all available Framework behavior and moving forward with the
Framework. The developer will work with the Spring AI team through upstream pull
requests; this work is not a prerequisite for the current ticket's completion.
This extends the existing default-skip deferrals to the final ticket's current
iteration. Keep the complete correctness assertions and opt-in reproductions,
and report blocked integrated cases and skips explicitly. Required default
Framework/Console verification and fresh independent review must still pass.
Ticket completion is distinct from blocker closure and unqualified migration
acceptance; the closure conditions below remain outstanding. No new defect,
weaker assertion, copied adapter, body rewrite, fallback or live call is approved.

## PR30-B05 — Gemini response status evidence discarded by the adapter

**Status:** Open; default-skip deferral explicitly approved by the developer on
November 13, 2026. Full assertions remain available with
`loomspan.pr30.blockers=true`; iteration completion requires verification and
independent review.

This approval covers only required Gemini status evidence discarded by the pinned
`org.springframework.ai:spring-ai-google-genai:2.1.0-RC2` adapter: native
`promptFeedback` and candidate finish/safety details. A blocked prompt can have
no candidates, so its explicit block reason cannot be recovered from candidate
finishReason. Do not infer rejection from empty output or prose.

Keep complete correctness assertions available with `loomspan.pr30.blockers=true`
and skip only these blocked cases by default. Verify all available Framework
status handling, evidence, accounting and preservation controls. Default green
checks do not establish the missing evidence or close B05. Preserve official
adapter/tool-loop ownership; no copied adapter, second transport, body rewrite,
fallback or weaker evidence requirement is authorized.

**Ownership:** PR 30.1.5 retains this unresolved adapter requirement; PR 30.4.3
must reconcile it and rerun integrated acceptance. Current iteration completion
requires sufficient default verification and fresh independent review.

**Closure:** Consume an identifiable acceptable upstream correction retaining
original prompt feedback and candidate finish/safety details. Record artifact
identity, remove the skip guards and pass complete real-pipeline evidence
assertions, including blocked prompts without candidates, plus status, usage,
normal tool continuation and preservation controls. A source-only correction or
merged upstream PR does not establish shipped adapter fidelity.

Research and the bounded correction are recorded in
`ai/thoughts/research/2026-11-13-pr-30.1.5-explicit-status-and-accounting.md` and
`ai/thoughts/plans/2026-11-13-pr-30.1.5-explicit-status.md`.

**Retained complete correctness cases:**
`ProviderResponseStatusIntegrationTest#geminiBlockedPromptRetainsFeedbackAndRejectsWithoutCandidates`
and `#geminiCandidateRetainsNativeFinishAndSafetyDetails`. Only these methods use
the B05 assumption; available candidate-status rejection, zero tool dispatch,
schema/linter/evidence/planning bypass, usage/outcome and cancellation controls
run by default across the three adapters.

```powershell
mvn -B '-Dtest=ProviderResponseStatusIntegrationTest#geminiBlockedPromptRetainsFeedbackAndRejectsWithoutCandidates+geminiCandidateRetainsNativeFinishAndSafetyDetails' '-Dloomspan.pr30.blockers=true' test
```

### PR30-B05 focused verification under explicit default deferral

Default `mvn -B '-Dtest=ProviderResponseStatusIntegrationTest' test` ran 33 cases
with zero failures/errors and exactly two B05 skips on November 13, 2026. The
opt-in two-method command above ran two cases with two expected assertion
failures, zero errors and zero skips. The empty-candidate prompt loses feedback
and is accepted; candidate SAFETY rejection works but original finishMessage and
safetyRatings are lost. Exact supplied usage is counted once for both fixtures.
These runs preserve the full requirements and do not close B05. Exact logs are
`target/pr30-status-tests-final.log` and `target/pr30-status-b05-optin.log`;
verification history is in
`ai/thoughts/plans/2026-11-13-pr-30.1.5-explicit-status-results.md`.

Final PR30.1.5 default Java verification passed with the authorized blocker
skips, including the public-surface check and corrected shared corpus. Final full
Console Go tests passed. These are Step 4 results; fresh independent review remains the iteration
completion gate. B05 remains open.
