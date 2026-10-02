# PR 16 — Complete correction evidence testing plan

## Change Summary

- Ordinary schema retries replay the entire latest nonempty rejected response as assistant text, including whitespace-only text.
- Step-action correction JSON-quotes the entire original response, independently of parser coordinates.
- Candidate clipping and obsolete offset-selection state disappear; diagnostic bounds, validators, retries, tool gates, provider/resource errors remain.
- Guidance states complete candidate replay separately from bounded feedback/previews and model-quality limitations.

## Impacted Areas

`OutputSchemaCallAdvisor`, `StepActionCorrection`, their engine call sites, advisor/helper/engine tests, `ModelAttemptCallAdvisorIntegrationTest`, and the authoring traces topic/index. Supported Java API, `RestSkillHandler`, manifest syntax, quotas, and Console protocols are unchanged.

## Risk Assessment

- Assertions that inspect a tail sentinel alone could miss middle omission; compare complete strings and decode step evidence rather than matching only prefixes.
- Escaping quotes/backslashes/newlines must preserve decoded original text, including non-BMP Unicode and outer fences/whitespace.
- Request growth must reflect baseline plus latest candidate, without accumulated history. Diagnostic omission is allowed and must not be mistaken for candidate loss.
- Rejected actions must not execute; a corrected action remains subject to task/action/argument validation. Invalid final output must not publish success.
- Provider context failures must retain diagnostic meaning without additional lossy sends. Existing attempt quotas can prevent a send explicitly.
- Protected paths: configuration retry/quotas, supported API/SPI signatures, validation/execution behavior. Intentionally obsolete internal paths: candidate clipping constants, truncation notices, head/region/tail selection and its offset state. No old/new compatibility mode.

## Existing Test Coverage

- `OutputSchemaCallAdvisorTest`: roles, immutable baseline/options/context, latest-only third request, diagnostic bound and complete issue quoting, blank responses, success/exhaustion. Its clipping test and whitespace-only omission assertions must change.
- `StepActionCorrectionTest`: parser diagnostics/coordinates, data encoding and obsolete region selection.
- `StepLoopMissionExecutionEngineTest`: short/16000-character extra-closing-brace fixture, one accepted tool execution, repeated malformed exhaustion with no tool execution, final schema validation, original prompt/authoritative evidence.
- `ModelAttemptCallAdvisorIntegrationTest`: localhost real-provider timeout/error wiring, physical attempt accounting, quotas, canonical request/response facts and compliant completed-tool reuse.
- `ExecutionCoordinatorOutputSchemaIntegrationTest`, `OutputSchemaValidatorTest`, `StepActionValidatorTest`, `SpringAiProviderIntegrationTest`, `LoomspanPublicSurfaceArchitectureTest`: validation/integration/translation/public boundaries.
- Gaps: full equality beyond old cutoff, decoded step evidence under adversarial Unicode content, nonempty whitespace exact replay, multi-retry large replacement, terminal context error during complete correction, and structured equipment/comparison example.

## Bug Reproduction / Failing Test First

Type: unit, plus existing engine integration fixture.

1. Replace old advisor clipping assertions with equality to `"x".repeat(8192) + Unicode + distinct tail`; current replay is a prefix and fails equality.
2. Replace helper oversized region assertions with extraction/JSON decoding of evidence and equality to a >16000-character candidate with distinct middle/tail. Current head/region selection fails equality.
3. Strengthen the existing engine extra-brace fixture to assert the full JSON-quoted candidate for both sizes, retaining one accepted tool invocation. Current long branch fails.

Run the focused command before production changes and preserve exact failing test names/reasons in implementation verification. Do not preserve obsolete constants just to compile tests; use literal old-boundary test sizes. A red failure must prove replay loss, not a compilation error or unrelated failure.

## Tests to Add/Update

### 1) Complete schema candidate replay and bounded diagnostics

- Type/location: unit, `src/test/java/ai/loomspan/internal/outputschema/OutputSchemaCallAdvisorTest.java`.
- Suggested name: `replaysCompleteLargeUnicodeCandidateAndBoundsCorrectionOnly` (replace clipping test).
- Inputs: exact 8192 boundary, >16000 text/structured JSON with extra final brace, Unicode before/after old boundary, unique malformed tail; independently oversized diagnostics.
- Mocks: existing `RecordingChain`, real validator/augmentor.
- Proves: assistant text equals candidate exactly, baseline task/schema preserved, correction remains at most 2048 code points with required instructions, no candidate clipping notice. Content resembling role/instruction text stays assistant data and diagnostic facts retain existing quoting.
- Surface/compatibility: internal request composition and current-run diagnostics; complete replay intentionally replaces clipping.

### 2) Whitespace, empty, baseline and latest-only schema retries

- Type/location: unit, same advisor test.
- Suggested names: `preservesWhitespaceOnlyCandidateExactly`, strengthen `rebuildsThirdAttemptFromBaselineWithOnlyLatestCandidateAndCorrection` and existing exhaustion test.
- Inputs: nonempty spaces/tabs/newlines, genuinely empty response, two distinct >8192 rejected candidates and valid third or invalid terminal response; original options/context sentinels.
- Mocks: existing chain/outcome collector.
- Proves: nonempty whitespace gets exact assistant replay; empty response retains existing omission/feedback. Third request has baseline plus only the second complete candidate/current correction, options/context unchanged. Configured exhaustion has exactly initial plus N calls and full latest raw output; no passed outcome.
- Surface/compatibility: internal replay intentionally changes whitespace omission; manifest retry behavior protected.

### 3) Complete JSON-string step evidence with useful parser feedback

- Type/location: unit, `src/test/java/ai/loomspan/internal/runtime/step/StepActionCorrectionTest.java`.
- Suggested names: `largeEvidencePreservesDecodedCandidateRegardlessOfParserLocation`, `parserDiagnosticsRetainOnlyAvailableCoordinatesAndBoundReason`.
- Inputs: long distinct head/middle/tail, Unicode/quotes/backslashes/newlines/instruction-like text; missing/valid parser coordinates; fenced original and normalized parse text; long parser message.
- Mocks: existing Jackson exception/location mocks; real planning codec to decode quoted evidence.
- Proves: all original text survives regardless of offset availability or normalization; framework data-only instructions and CALL_TOOL/FINAL_RESPONSE constraints remain. Diagnostic coordinates describe parsed candidate without invented location; parser reason and overall diagnostic remain bounded.
- Surface/compatibility: internal helper and current-run diagnostics; remove offset-selection tests/state, preserve diagnostic usefulness.

### 4) Rejected action recovery, validators, and no side effects

- Type/location: engine integration, `src/test/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngineTest.java`.
- Update `syntheticTrailingBraceRecoveryReplaysShortAndLongCandidatesWithoutRejectedToolCalls`; strengthen repeated-invalid and final-schema cases.
- Inputs: short/large malformed tool envelopes, valid correction, wrong task/tool/action correction, schema-invalid final corrections, distinct large final candidates across configured final-schema retries.
- Mocks: sequence client captures each system/user request; counted `BoundCapability`; existing real validators/state/planning fixtures.
- Proves: rejected candidates do not invoke tools, valid correction invokes once, invalid correction exhausts unchanged allowances, final schema/evidence/argument checks remain effective with no successful final publication. Baseline task/input/completed results remain present. Where final-schema allowance permits a third request, only latest rejected candidate is appended; do not modify ordinary invalid-action retry allowance to create this scenario.
- Surface/compatibility: protected execution/configuration semantics and current-run evidence.

### 5) Source-derived structured equipment/comparison fixture

- Type/location: advisor/engine request-capture tests above; a small test-only builder under `src/test/java/ai/loomspan/testkit/` or JSON resource under `src/test/resources/` if reused.
- Inputs: synthetic nested `equipmentAssessment` with chronology, hypotheses, questions and citations; comparison options and ordered outer citations referencing fictional source sections. Derive domain names from neighboring fictional source pack (`P240`, `NB-P240-017`, `E17`, `MAN-2.3`), repeat realistic structured entries to exceed 14000 code points, include Unicode and a unique last citation, append one extra brace.
- Mocks: deterministic queued replacement, never a model generation.
- Proves: full rejected structured output arrives unchanged in both encodings, including child fields/middle references/tail. A valid scripted replacement passes the configured schema/action validators; no citation-quality claim about live models is inferred.
- Surface/compatibility: internal replay and supported output validation. Fixtures are self-contained synthetic test data, not retained captures; tests must not depend on neighboring files or credentials.

### 6) Provider context-limit failure during full schema correction

- Type/location: offline integration, `src/test/java/ai/loomspan/internal/chat/ModelAttemptCallAdvisorIntegrationTest.java`.
- Suggested name: `outputSchemaCorrectionPreservesFullCandidateWhenProviderRejectsContextLimit`.
- Inputs: localhost server returns initial >8192 malformed text, then OpenAI-shaped HTTP 400 invalid-request/context-limit error with a distinctive diagnostic message. Enable bounded provider retries to prove permanent failure is not retried.
- Mocks: `MockWebServer` only; use actual `SpringAiProviderIntegration`, schema advisor, provider-attempt advisor, execution binding/state/usage. Existing real-provider timeout test is the setup pattern.
- Proves: decoded correction wire message equals original candidate; exactly two requests; terminal exception preserves context-limit meaning; failed attempt classification/diagnostic visible; no third shortened/summarized fallback and no schema-passed outcome/success. Requests and trace evidence retain actual text under existing capture controls.
- Surface/compatibility: provider failure/current-run diagnostic coherence and existing quotas. No new error taxonomy/configuration required.

### 7) Step correction context-limit failure and explicit quota behavior

- Type/location: engine integration in engine test; existing provider quota tests in model-attempt integration suite.
- Inputs: first malformed large tool action, then injected explicit context-limit exception on corrective invocation; counted tool and captured user/system messages. Reuse existing quota tests with provider-attempt limit.
- Mocks: extend the existing sequence-client seam locally to throw on second call (or equivalent test-only scripted client); no external service.
- Proves: complete rejected evidence composed before failure, exactly two model invocations, terminal failure/cause distinguishable, zero tool calls/no final success and no fallback. Existing provider-attempt reservation still throws explicitly when exhausted.
- Surface/compatibility: protected execution/resource boundaries/current-run failures.

## Authoring Claims Requiring Evidence

Update `traces-and-debugging.md` and its index coverage row from current **aligned clipping behavior** to the verified new behavior: exact latest candidate; assistant role or JSON-string data; original context and latest-only history; diagnostic bounds separately; existing retry/validator gates; explicit provider limitations with no lossy fallback. Named tests above establish these claims. Explain that complete replay does not guarantee model recovery, argument/citation fidelity, or sufficient provider capacity. Rejection-record previews remain bounded and are not the full corrective request.

## How to Run

Use repository Maven wrapper, Java 21+, Maven 3.9+; no credentials, provider network access, paid calls, or special profiles. Local mock server uses loopback only.

```powershell
./mvnw.cmd '-Dtest=OutputSchemaCallAdvisorTest,StepActionCorrectionTest,StepLoopMissionExecutionEngineTest' test
./mvnw.cmd '-Dtest=OutputSchemaValidatorTest,StepActionValidatorTest*,ExecutionCoordinatorOutputSchemaIntegrationTest,ModelAttemptCallAdvisorIntegrationTest,SpringAiProviderIntegrationTest,LoomspanPublicSurfaceArchitectureTest' test
git diff --check
```

The first command is the red/green replay regression command (red before implementation, green after). The second covers validators/provider translation/usage/tool reuse and mandatory architecture after production edits; it also compiles production/tests. Broaden to `./mvnw.cmd test` only when changes/failures expose wider concerns or when implementation/review judges full-suite assurance useful; do not rerun passing groups without a new reason. Record command results and any unavailable checks honestly. There is no separate configured lint/format gate in `pom.xml`.

## Exit Criteria

- [x] Replay-equality regressions compile and demonstrably fail pre-fix due to candidate loss.
- [x] Complete decoded-text fidelity covers both paths, old boundary, malformed tail, middle content, Unicode, instruction-like text, whitespace/fences and structured fixture.
- [x] Latest-only history/baseline evidence/options/context, existing feedback bounds and retry exhaustion are verified.
- [x] Validators pass valid correction and reject invalid correction; zero rejected tool effects and no invalid successful final output.
- [x] Explicit resource/context errors retain meaning, are terminal when required, and create no shortened fallback.
- [x] Focused regression and boundary commands pass, including `LoomspanPublicSurfaceArchitectureTest` after production edits.
- [x] Clipping-only helpers/constants/offset selection and obsolete test expectations are absent without compatibility fallback.
- [x] Authoring claims are supported by focused evidence; topic/index are updated and remain aligned with current executable semantics.
- [x] No neighboring captures/workspace files altered, no paid calls; independent Step 5 review receives concrete change and verification evidence.

No optional developer checks are needed for acceptance. Live model-quality/citation experiments are outside scope and are not verification gates.


## Step 4 results

All implementation exit criteria are established by exact request equality, real validators/execution gates, localhost provider wire capture, and explicit failure tests. Final selected checks passed 160 tests across focused replay (82), boundary/one corrected-identity test (48), and nested action validators (30). Use `StepActionValidatorTest*` to include its nested-only cases; the initially planned bare selector did not run those cases in the combined command. Exact executed commands, expected red failures, resolved fixture-only exploratory failures, remaining full-suite NOT RUN, and public/documentation assessments are recorded in the implementation plan's Step 4 verification receipt. Independent Step 5 review is still pending.
