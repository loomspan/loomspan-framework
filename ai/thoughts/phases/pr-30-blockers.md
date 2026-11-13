# PR 30 implementation blockers

This register carries unresolved dependency requirements across the PR 30 ticket
sequence. On November 13, 2026, the developer authorized tracking the known adapter
defects here and skipping their failing correctness cases by default so subsequent
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
