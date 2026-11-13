# PR 30 implementation blockers

This register carries unresolved dependency requirements across the PR 30 ticket
sequence. On November 13, 2026, the developer authorized tracking the Responses adapter
defects PR30-B01/B02 here and skipping their failing correctness cases by default so subsequent
tickets can proceed. Assertions and fixtures remain in the codebase. A passing
default build with these skips does not establish fidelity for the blocked cases.
The earlier pipeline results remain historical evidence; this decision does not
claim an independent review or erase the original failures.

Tested dependency: `org.springframework.ai:spring-ai-openai:2.1.0-RC2`, Spring Boot
`4.2.0-RC2`, Java 21. No acceptable fixed artifact was established during PR 30.1.2.
Keep one shared Responses implementation; this deferral does not authorize copied
adapters, SDK singleton mutation, HTTP-body rewrites, fallback or weaker schemas.

## PR30-B01 — Responses schema decimal precision (SAI-06)

**Status:** Open; four parameter cases skipped by default.

The adapter rounds fractional numeric bounds and enum members when decoding output
and tool schemas for SDK serialization. For example, `1.0000000000000001` becomes
`1.0`, and `0.12345678901234567890123456789` becomes `0.12345678901234568`.
Positive and negative long-exponent cases also lose numeric value.

- **Upstream boundary:** `ResponsesRequestBuilder.java:80` uses the SDK singleton
  mapper; output-schema decoding at `:228-229` and tool-schema decoding at `:262`
  lose precision. The isolated private Jackson 2 `USE_BIG_DECIMAL_FOR_FLOATS`
  candidate is reproduction/fix evidence, not a deployed override.
- **Retained test:** [SharedResponsesProtocolIntegrationTest.outputAndToolSchemaNumbersRemainExact](../../../src/test/java/ai/loomspan/internal/springai/SharedResponsesProtocolIntegrationTest.java).
  Four fractional/exponent invocations are skipped; large-integer and exact `0.5`
  controls remain active. Assertions compare exact numeric values in both
  `text.format.schema` and `tools[].parameters`, retaining strictness checks.
- **Existing requirements:** [Roadmap decimal fidelity](spring-ai-responses-migration.md#tbd-07-selecting-provider-differences-without-a-capability-registry),
  [SAI-06 blocker register](standard-json-schema-research.md#spring-ai-implementation-blockers-and-upstream-contribution-handoff),
  and [retained precision probe](../../../docs/maintainers/probes/responses-schema-precision/README.md).
- **Closure:** Verify an acceptable upstream artifact preserves numeric values in
  both real Framework wire schemas. Remove the skip guard, run all six numeric
  cases and schema/options/replay controls, and record the artifact and results.

## PR30-B02 — Required reasoning items omitted during replay

**Status:** Open; three parameter cases skipped by default.

RC2 requires both ciphertext and an item ID for reasoning replay. Required
plaintext, signed and summary-only items are omitted during continuation;
signature data is not retained. Encrypted-reasoning success does not establish
support for these shapes.

- **Upstream boundary:** `ResponsesItemMapper.reasoningItem():403-429` requires
  ciphertext/ID; `reasoningPart():623-634` does not retain signature.
- **Retained test:** [SharedResponsesProtocolIntegrationTest.requiredNonCiphertextReasoningSurvivesContinuation](../../../src/test/java/ai/loomspan/internal/springai/SharedResponsesProtocolIntegrationTest.java).
  The `plaintext`, `signed` and `summary-only` invocations retain their original
  outbound-continuation assertions. Encrypted multi-round replay, ordering, IDs,
  commentary phase, isolation and no-duplicate-tool-effect tests remain active.
- **Existing requirements:** [OpenRouter stateless replay](spring-ai-responses-migration.md#openrouter-compatibility-and-stateless-replay)
  and the roadmap's known mapper limits and Ollama summary-only limitation.
- **Closure:** Verify required content, summary, signature, IDs and ordering
  through the shared adapter using actual outbound continuation captures. Remove
  the skip guard and rerun the three cases plus encrypted replay/isolation/tool
  controls. Record the exact fixed artifact and results.

## Reproduction and follow-through

The skips use JUnit assumptions with blocker IDs and this document's path. Set
`loomspan.pr30.blockers=true` to run the unchanged failing assertions locally:

```powershell
mvn -B '-Dtest=SharedResponsesProtocolIntegrationTest#outputAndToolSchemaNumbersRemainExact+requiredNonCiphertextReasoningSurvivesContinuation' '-Dloomspan.pr30.blockers=true' test
```

Default positive/skip verification:

```powershell
mvn -B '-Dtest=SharedResponsesProtocolIntegrationTest' test
```

Ownership remains visible across tickets: PR 30.1.2 established adapter/factory
captures; PR 30.3.1 owns ordinary schema attachment/composition; PR 30.4.3 must
reconcile these blockers and rerun integrated acceptance with the guards removed.
Subsequent tickets may continue without treating skipped requirements as passed.
No new provider calls or external submissions are authorized by this register.

The 32 setup errors from the original full-suite run are **resolved**, not open
dependency blockers: fixture credential consumers and Boot test security exclusions
were corrected, with all 43 affected/control tests passing on rerun. See the
[PR 30.1.2 results](../plans/2026-11-13-pr-30.1.2-shared-responses-results.md)
for original failures, exact commands and verification history.

## Verification after enabling default skips

On November 13, 2026, the protocol class ran 26 cases with zero failures/errors
and exactly seven blocker skips. The opt-in reproduction ran nine cases with
seven expected failures and both numeric controls passing. A full `mvn -B test`
ran 1,865 cases with zero assertion failures, one child-binding mission-timeout
error and nine skips (seven here plus two existing diagnostic skips). The complete
child-binding class then passed all 17 cases in isolation without changes to its
test or deadline. That timeout did not reproduce; the full run is not claimed green.
Exact commands and logs are recorded in the linked results artifact.

## PR30-B03 — Anthropic tool root-schema loss (SAI-03)

**Status:** Open; full root-fidelity correctness case skipped by default under the
explicit developer decision of November 13, 2026 recorded in the PR 30.1.3 ticket.
The complete assertion runs with `loomspan.pr30.blockers=true`. This later decision
extends the default-skip pattern to B03; it does not close the defect or establish
schema fidelity. Independent nested-schema and canonical controls remain active.

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
with zero errors/skips. The default full suite then passed 1,877 cases with zero
failures/errors and ten skips (B03, seven prior Responses and two existing diagnostic
cases). The independent nested/canonical tool control stays active. These results
permit iteration completion under the explicit developer decision and do not
establish root-schema fidelity or close this blocker. Exact commands and logs are
recorded in the PR 30.1.3 results artifact linked above.

## PR30-B04 — Gemini raw output and tool schema narrowing (SAI-04/05)

**Status:** Open; four full correctness cases are skipped by default under the
explicit developer approval of November 13, 2026: "Apply the same default-skip
deferral to B04 as B01–B03, keep the full assertions available with
loomspan.pr30.blockers=true, document my approval, and finish the pipeline
including independent review." This authorizes current iteration completion
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
- **Closure:** Verify an acceptable upstream artifact/commit, record its identity,
  pass complete raw output/tool assertions and native/options/preservation checks.
  A merged change or SDK-only success does not prove the adapter ships the fix.
  Remove the guards after correction. Iteration completion under the explicit
  deferral is separate from closure of these owned requirements.

Verification before the B04 deferral on November 13, 2026: 101 focused native-option/architecture/provider
controls passed with zero failures/errors/skips. Full `mvn -B test` ran 1,892
cases with 3 failures, 1 error and 10 prior skips. All failures/errors are the
four active B04 correctness methods; no unrelated failure was observed. The
retained Gemini probe command passed four historical defect/direct-SDK methods,
which does not establish Framework raw-schema fidelity. Exact commands and logs
are in the linked results document.

### PR30-B04 verification under explicit default deferral

After the quoted approval, focused contributor/protocol/architecture verification
passed 31 cases with zero failures/errors and exactly four B04 skips. Opt-in
`mvn -B '-Dtest=GeminiProtocolIntegrationTest' '-Dloomspan.pr30.blockers=true' test`
ran eight cases with three failures, one error and zero skips, preserving the
original defect assertions. The default full `mvn -B test` passed 1,892 cases
with zero failures/errors and 14 skips (four B04, eight prior B01–B03 and two
existing diagnostics). Positive options and preservation controls remain active.
Current iteration may proceed to independent review; these results do not close
B04 or establish raw-schema fidelity. Exact logs are in the results artifact.
