# PR 30 implementation blockers

## Current ownership and historical records

The completed PR 30 migration roadmap, schema research, ticket index, coverage,
and implementation tickets have been retired from the working tree.
This register remains the downstream authority for the open B03–B05 requirements,
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

The developer explicitly authorized completing PR 30.4.3 while B03–B05 remain
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

## PR30-B03 — Anthropic tool root-schema loss (SAI-03)

**Status:** Open; full root-fidelity correctness case skipped by default under the
explicit developer decision of November 13, 2026 recorded in the PR 30.1.3 ticket.
The complete assertion runs with `loomspan.pr30.blockers=true`. This deferral
does not close the defect or establish schema fidelity. Independent nested-schema
and canonical controls remain active.

Tested artifact: `org.springframework.ai:spring-ai-anthropic:2.1.0-RC2`, JAR SHA-256
`9067b55f3febc01cc2a16650d870f71911bc4fa414efd309f46147216008815e`.
`AnthropicChatModel.toAnthropicTool` constructs `InputSchema` from `properties` and
`required`, losing authored root entries. The new approved-profile Framework capture
preserves nested constraints and typed-map value schemas, but drops root `$schema`,
`description` and `additionalProperties:false`.

- **Retained correctness test:** `AnthropicProtocolIntegrationTest#toolInputSchemaPreservesApprovedRootAndNestedConstraints`.
  It retains complete tree equality behind the explicit blocker assumption.
  `toolNestedConstraintsAndCanonicalSchemaRemainUnchanged` independently runs
  nested/required equality, non-strict tools, canonical callback schema preservation
  and exactly one loopback request by default.
- **Reproduction:** `mvn -B '-Dtest=AnthropicProtocolIntegrationTest#toolInputSchemaPreservesApprovedRootAndNestedConstraints' '-Dloomspan.pr30.blockers=true' test`.
- **Strategy:** Keep the official adapter and tool loop. No verified fixed shipped
  artifact was established in PR 30.1.3 research; upstream main still had the lossy
  mapper. Native output options do not repair this tool conversion. No copied model
  class, singleton mutation, body rewrite, fallback or weakened contract is permitted.
- **Ownership:** PR 30.1.3 owns this unresolved adapter requirement; PR 30.4.3
  reconciles it with the integrated matrix. It is not deferred to schema consumers.
- **Closure:** Consume an acceptable upstream correction/release, record exact
  coordinates/commit/checksum, and pass this complete wire comparison plus native
  output/effort/cap, legacy and preservation controls. A merged upstream PR alone is
  insufficient. Remove the assumption after the fix. PR 30.1.3 may complete its
  current iteration under the explicit deferral after default verification and
  fresh independent review; this is distinct from B03 closure and full fidelity.

See `ai/thoughts/plans/2026-11-13-pr-30.1.3-anthropic-results.md` for commands and
actual verification. Live provider calls and external submissions remain unauthorized.


### PR30-B03 verification under default deferral

On November 13, 2026, default focused contributor/protocol/architecture verification
ran 23 cases with zero failures/errors and exactly one B03 skip. The opt-in full-root
method ran one case and failed the complete schema equality assertion as expected,
with zero errors/skips. The independent nested/canonical tool control stays active.
These results
permit iteration completion under the explicit developer decision and do not
establish root-schema fidelity or close this blocker. Exact commands and logs are
recorded in the PR 30.1.3 results artifact linked above.

## PR30-B04 — Gemini raw output and tool schema narrowing (SAI-04/05)

**Status:** Open; four full correctness cases are skipped by default under the
explicit developer approval of November 13, 2026. Full assertions remain available
with `loomspan.pr30.blockers=true`. This authorizes current iteration completion
after sufficient default verification and fresh independent review, not blocker
closure or a claim of raw-schema fidelity. Positive native/default/cap controls
remain active.

Tested artifact: `org.springframework.ai:spring-ai-google-genai:2.1.0-RC2`, JAR
SHA-256 `c4217785fb658c76d20a85ca7e322338485ee437b7c9195585b277de01ded307`.
SDK: `com.google.genai:google-genai:1.73.0`, JAR SHA-256
`9709e5fd1277e758c8f4b0165473e752d0071ea22ef821556a8f18b3e435d9f0`.

- **Boundaries:** `GoogleGenAiToolCallingManager.resolveToolDefinitions` converts
  canonical tools into OpenAPI and uppercases types. `GoogleGenAiChatModel`
  subsequently calls `parameters(jsonToSchema(...))`; output calls
  `responseJsonSchema(jsonToSchema(...))`. Both tool conversion stages must be
  bypassed by a suitable correction. SDK raw setters alone do not integrate them.
- **Actual Framework evidence:** `GeminiProtocolIntegrationTest` keeps complete
  assertions for nullable output, typed-map/unconstrained input, nullable input
  and generated reserved-false property. RC2 nullable output fails before HTTP
  because the SDK Type cannot deserialize an array. The three tool requests use
  narrowed `parameters`, with no raw `parametersJsonSchema`; map value constraints
  and root openness disappear. Canonical callback schemas stay unchanged and no
  tool callbacks execute for the deterministic plain-text responses.
- **Reproduction:** `mvn -B '-Dtest=GeminiProtocolIntegrationTest' '-Dloomspan.pr30.blockers=true' test`.
  Full assertions remain intact behind the approved assumption. Exact runs and results are in
  `ai/thoughts/plans/2026-11-13-pr-30.1.4-gemini-results.md`.
- **Options:** The independent exact `gemini-3.8-flash` mapping uses native
  LOW/MEDIUM/HIGH without numeric budget or cap inflation. Request-local schemas
  must copy effective target defaults: a fresh typed schema-only builder replaces
  them in RC2. Positive controls retain MEDIUM, 1024/4096/2048 caps, temperature,
  stops and no schema leakage. This does not close schema fidelity.
- **Strategy:** Research established no acceptable fixed shipped artifact. Keep
  official Spring AI model/tool-loop ownership; no copied research model,
  singleton mutation, body rewrite, fixed extraBody substitute or fallback.
- **Owner:** PR 30.1.4 retains the unresolved adapter requirement. 30.2.2/30.2.3
  own schema generation/validation/binding; 30.3.1/30.3.2 own ordinary/planner
  consumer schema/default copying; 30.4.3 reconciles integrated checks.
- **Integrated B04 consumer:** Under the November 14 final-ticket continuation
  approval, only the `GEMINI` parameter of
  `ProviderResponseStatusIntegrationTest#incompletePlanningResponseNeverReachesJsonParser`
  also uses the blocker assumption. Its complete status/parser/usage assertions
  remain unchanged and run with `loomspan.pr30.blockers=true`. The nullable plan
  schema fails inside the same SDK conversion before HTTP, so this case cannot
  reach its intended incomplete-response boundary. OpenAI/Anthropic parameters
  and available Gemini status controls remain active. Count this one integrated
  invocation separately from the seven B03–B05 protocol invocations. Remove
  this assumption and pass the complete case when B04 is corrected.
- **Closure:** Verify an acceptable upstream artifact/commit, record its identity,
  pass complete raw output/tool assertions and native/options/preservation checks.
  A merged change or SDK-only success does not prove the adapter ships the fix.
  Remove the guards after correction. Iteration completion under the explicit
  deferral is separate from closure of these owned requirements.

Verification before the B04 deferral on November 13, 2026: 101 focused native-option/architecture/provider
controls passed with zero failures/errors/skips. The full suite reported failures/errors
only in the four active B04 correctness methods; no unrelated failure was observed.
The retained Gemini probe command passed four historical defect/direct-SDK methods,
which does not establish Framework raw-schema fidelity. Exact commands and logs
are in the linked results document.

### PR30-B04 verification under explicit default deferral

After the approval, focused contributor/protocol/architecture verification
passed 31 cases with zero failures/errors and exactly four B04 skips. Opt-in
`mvn -B '-Dtest=GeminiProtocolIntegrationTest' '-Dloomspan.pr30.blockers=true' test`
ran eight cases with three failures, one error and zero skips, preserving the
original defect assertions. Positive options and preservation controls remain active.
Current iteration may proceed to independent review; these results do not close
B04 or establish raw-schema fidelity. Exact logs are in the results artifact.

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
