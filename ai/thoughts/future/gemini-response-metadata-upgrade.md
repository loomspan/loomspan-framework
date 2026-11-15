# Gemini response metadata after a Spring AI upgrade

## Scope

Future dependency-upgrade follow-up, not a Loomspan implementation blocker.
An upstream Spring AI bug report has been submitted. Revisit this work when a
release containing the relevant fix is available; no workaround is planned now.

## Issue

The tested `org.springframework.ai:spring-ai-google-genai:2.1.0-RC2` adapter
omits Gemini response-level `promptFeedback` and candidate `finishMessage` and
`safetyRatings`. A blocked prompt may contain no candidates, so a candidate
finish reason cannot communicate its `promptFeedback.blockReason`.

Loomspan already rejects recognized Gemini candidate finish reasons, including
`SAFETY`, through `ProviderResponseStatusClassifier`. It does not currently inspect
response-level prompt feedback. With no generations, that classifier returns no
rejection. Empty output or refusal-like prose is not evidence of a provider block.

`ProviderAttemptCallAdvisor.responsePayload` copies response and generation
metadata entries into traces. Fields exposed through those metadata maps may
therefore be retained automatically after upgrading, but that does not by itself
make the status classifier consume them.

## Check the upstream fix

1. Identify the released Spring AI version and associated Google GenAI SDK version.
   Verify the release includes both response-level prompt feedback and candidate
   finish/safety details, rather than relying on an issue closure or merged PR.
2. Inspect the adapter's actual metadata keys, value types, and handling of blocked
   responses with no candidates. Determine whether it returns metadata, creates a
   recognized rejection status, or throws an exception.
3. Upgrade the dependency and capture real factory/advisor/tool-path behavior with
   local HTTP fixtures. Check that metadata is retained in the canonical trace,
   including nested native fields, and that usage is counted exactly once.

## Adjust Loomspan only where necessary

- If the adapter supplies a rejection signal already handled by Loomspan, verify
  the existing path works without a production change.
- If it only adds response-level prompt feedback, extend
  `src/main/java/ai/loomspan/internal/provider/ProviderResponseStatusClassifier.java`
  to read the documented block reason before iterating over generations. Reject
  explicitly blocked responses even when there are no candidates. Do not infer
  rejection from missing output, prose, or unknown/unspecified values.
- If native fields appear outside the metadata maps currently copied by
  `src/main/java/ai/loomspan/internal/chat/ProviderAttemptCallAdvisor.java`, adapt
  trace extraction to the actual upstream API while preserving supplied content.
- If upstream throws for blocked prompts, verify rejection, diagnostic evidence,
  retry behavior, and supplied usage accounting through that exception path.

Keep the official Spring AI adapter and tool loop. An upgrade may be sufficient;
the final upstream API and behavior determine whether a small integration change
is needed.

## Acceptance fixtures and regression checks

Add focused cases to
`src/test/java/ai/loomspan/internal/springai/ProviderResponseStatusIntegrationTest.java`
when evaluating the fixed dependency:

- A response with no candidates and `promptFeedback.blockReason = SAFETY`, an
  explicit `blockReasonMessage`, and usage: retain the feedback, reject the
  response, and count supplied usage once.
- A candidate with `finishReason = SAFETY`, a distinct `finishMessage`, nested
  `safetyRatings`, and partial text: retain all evidence and reject the candidate.
- Rejection occurs before tool dispatch, schema/linter/evidence correction, or
  planning JSON parsing. No unchanged retry or duplicate tool effect occurs.
- Ordinary successful responses, recognized finish-reason rejection, unknown or
  absent status values, missing/zero usage, cancellation, and normal multi-round
  tool continuation retain their existing behavior.

Run the response-status integration class and related trace, usage, retry, and
provider controls affected by any change. Run `LoomspanPublicSurfaceArchitectureTest`
if production types change. Record the exact dependency versions and results in
this document; local fixtures do not establish live-provider acceptance.
