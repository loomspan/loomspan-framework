---
date: 2026-10-02T14:48:22-07:00
researcher: Codex
model: Unknown
git_commit: 4313a7fcdbad33ef358b6ee4d068ce610f3dee51
branch: main
repository: loomspan-framework
topic: "PR 16 — Preserve complete evidence during model correction"
tags: [research, codebase, output-schema, step-action, correction]
status: complete
last_updated: 2026-10-02
last_updated_by: Codex
---

# Research: complete correction evidence

**Date:** 2026-10-02 14:48:22 PDT
**Researcher:** Codex
**Git commit:** `4313a7fcdbad33ef358b6ee4d068ce610f3dee51`
**Branch:** `main`
**Repository:** `loomspan-framework`

## Research question and execution context

Map both corrective request paths, their validation/execution boundaries, retry accounting, resource failures, and existing offline verification for `ai/thoughts/tickets/loomspan-pr-16-preserve-complete-correction-evidence.md`.

The user accepted the Full 5-Step Pipeline with "proceed". This is Step 1 in pipeline mode (`PROFILE: full`). The initial checkout has no tracked modifications; the ticket is an existing untracked file. No production edits, test runs, provider calls, or neighboring evidence modifications were performed in this stage. The ticket explicitly permits removal of the internal candidate-size guarantee while preserving retry/feedback bounds and excludes paid-provider calls.

## Research checklist

- [x] Read ticket and research/shared protocols.
- [x] Trace output-schema request construction and validators.
- [x] Trace step-action correction, action gates, and retry allowances.
- [x] Inventory request/resource limits and provider propagation.
- [x] Inventory tests, authoring documentation, public contracts, and historical evidence.
- [x] Gather metadata using `bash ai/scripts/spec_metadata.sh` and write durable findings.

## Summary

The checkout matches the ticket's observed snapshot. `OutputSchemaCallAdvisor` sends at most 8192 Unicode code points of the rejected candidate as an assistant message; `StepActionCorrection` sends at most two 4096 UTF-16-character regions with omission markers in a JSON string within the user message. Both retry paths retain the original task and constraints and replace the rejection state rather than append prior failures.

Validation remains separate from replay. Ordinary schema validation throws when configured retries exhaust; planning validates the action and final payload before tool execution or successful final output. Provider attempts propagate terminal exceptions and preserve request text; repository quotas count provider attempts and returned usage rather than estimating a per-request context window. The bundled authoring docs explicitly describe the current bounded replay behavior and are aligned with this snapshot.

## Detailed findings

### Ordinary output-schema correction

- `src/main/java/ai/loomspan/internal/outputschema/OutputSchemaCallAdvisor.java:33` declares four issues in hints/outcomes, a 2048-code-point correction-text bound, and an 8192-code-point candidate bound.
- `adviseCall` at line 85 skips planning-tagged requests. It augments the original prompt once, keeps `baselineRequest`, and validates each downstream response with `OutputSchemaValidator`. A retry rebuilds from baseline at line 157 and obtains a fresh downstream advisor chain.
- `buildRetryPrompt` at line 242 copies all original messages and options, adds candidate replay as `AssistantMessage`, then appends one `UserMessage` containing the current correction. It does not move the candidate into framework instructions. Blank/whitespace-only candidates currently omit the synthetic assistant message.
- `candidateReplay` near line 416 counts Unicode code points, retains the prefix, and records whether truncation occurred. `correctionTail` near line 367 announces truncation and requests one complete corrected JSON object, preservation of visible valid values, and reuse of completed tool data.
- `correctionMessage` at line 338 chooses parse/schema headings, renders complete JSON-quoted diagnostic facts, and reduces whole issue bullets until the 2048-code-point bound is satisfied. Diagnostic truncation is distinct from candidate replay.
- At most one initial response plus `maxRetries` corrections are validated. Exhaustion at line 120 records an exhausted outcome and throws `LoomspanOutputSchemaValidationException` with the full latest candidate and validation issues. A valid response is recorded and returned.
- `DefaultSkillAdvisorResolver.java:102` constructs this internal advisor from normalized skill configuration. `SpringAiModelInteraction.java:63` tags planning calls so ordinary schema correction is bypassed there.
- The no-tools correction text is guidance rather than an execution-time ban on a subsequent provider tool request. `ModelAttemptCallAdvisorIntegrationTest#outputSchemaRetryReusesCompletedToolResultWhenCorrectionReturnsJsonDirectly` covers compliant reuse of completed tool results.

### Planning step-action correction

- `src/main/java/ai/loomspan/internal/runtime/step/StepActionCorrection.java:10` declares the 8192-character bound, 1024-character parser-reason bound, and head/region lengths of 4096. `Failure` stores a reason and optionally an original-response character offset.
- `parsingFailure` at line 17 records the original parser message, available coordinates, and a nearby fragment. Offsets are mapped only when parsed/original candidates match; fenced/trimmed candidates use the tail fallback. Coordinate mapping is used only to select candidate replay regions.
- `evidence` at line 57 adds the rejected response as a Jackson JSON string and the diagnostic as a separate JSON string. `correctionRequest` at line 47 explicitly says these are data rather than instructions and requests the appropriate CALL_TOOL or FINAL_RESPONSE envelope for the same task, tool identity, and arguments.
- `replay` at line 64 sends short candidates unchanged. Oversized candidates use the beginning plus a mapped region or tail and explicit omission text; the whole original text is not replayed.
- `StepLoopMissionExecutionEngine.java:793` initializes rejection state for the step. Each loop rebuilds its system prompt, assignment/final constraints, user objective/input, completed task results, execution summary, and attachments from existing context. The current evidence is appended only when feedback exists (`:815-819`). On failure, the state is overwritten with the latest model response (`:845-848`, `:892-899`). Rejected history is not appended.
- `MAX_INVALID_ACTION_RETRIES` at line 88 is one. Parse and ordinary action-validation failures allow two total responses. Final linter/schema/evidence failures have their separate existing allowances and do not consume this ordinary invalid-action counter.
- `parseStepAction` at line 1050 handles blank/null replies, strips fences/outer whitespace for parsing, and retains the original response for correction evidence. The existing final-only convenience accepts a bare object payload when it lacks action/envelope fields. This is pre-existing parsing behavior, separate from candidate replay.
- `StepActionValidator.validateAssigned/validateFinal` is called before execution (`:858-859`). Final payload validators can exhaust before output publication. Only validated actions enter the switch at line 906. `executeToolAction` invokes the bound capability at line 1025, after action acceptance. Capability invocation retains its existing authorization/input/execution boundary.
- Parse rejection records contain a 500-character preview (`:835`); replay and diagnostic record previews are separate mechanisms. This ticket is about actual requests, not redesigning trace previews.

### Resource and provider failure paths

- `SpringAiModelInteraction.java:37-73` copies the composed system/user text into Spring AI requests and attachments without a candidate-size transformation; call failures propagate except for unwrapping tool-execution causes.
- `ProviderAttemptCallAdvisor.java:64-95` materializes request messages, reserves quota before each actual send, records request text, and invokes downstream with the same request. On failure it records provider classification and original throwable, then either retries the same request or rethrows the failure. There is no truncated/summarized fallback here.
- `SpringAiProviderIntegration.java:240` owns provider exception translation. HTTP client failures are classified using status/provider information; `:392` assigns other client failures to INVALID_REQUEST. Provider context-limit text can remain in the throwable/diagnostic evidence without a new framework context-window estimator.
- `ProviderRetryDecider.java:9` retries only TRANSIENT failures, bounded by configured provider attempts. Its physical retries are distinct from semantic correction allowances.
- `DefaultSessionUsageService.java:59-69` enforces model-call and usage-unit quotas after returned responses; `:72-93` enforces provider-attempt reservation before sending and throws `LoomspanQuotaExceededException` rather than modifying the request.
- Searches of production configuration/runtime found attachment byte limits, trace/content capture controls, response usage quotas, and provider-attempt quotas, but no framework-configured input-token/context-window or full model-request byte limit. `SpringAiChatOptionsContributor`'s 8192 value is an unrelated thinking budget, not correction truncation. Jackson/parser limits are validation/serialization constraints, not evidence excerpt policies.

### Existing deterministic verification

- `OutputSchemaCallAdvisorTest#boundsCandidateAndCorrectionByUnicodeCodePoint` (`:256`) deliberately asserts old prefix truncation and its user notice. Tests near `:90`, `:177`, `:216`, `:288`, and `:395` cover latest-only rebuilding, diagnostic bound, assistant-role adversarial content, blank response handling, and exhaustion.
- `StepActionCorrectionTest` currently asserts short exact replay, data encoding, mapped middle region replay, and unmappable head/tail replay. Its oversized assertions protect the old behavior, not a supported API size contract.
- `StepLoopMissionExecutionEngineTest#syntheticTrailingBraceRecoveryReplaysShortAndLongCandidatesWithoutRejectedToolCalls` (`:353`) uses an offline sequence client with an extra closing brace, 5/16000-character tool argument fixtures, captured system/user messages, and a counted tool. Its oversized branch at line 389 asserts omission and a request under 12000 characters.
- The adjacent repeated-malformed-action test (`:396`) exhausts one retry without tool execution. Existing tests also cover validation feedback, final output-schema failure/exhaustion, and complete authoritative task evidence.
- `ExecutionCoordinatorOutputSchemaIntegrationTest`, `ModelAttemptCallAdvisorIntegrationTest`, `OutputSchemaValidatorTest`, `StepActionValidatorTest`, and `LoomspanPublicSurfaceArchitectureTest` provide relevant integration/boundary checks. No test was run for this research stage.
- Build declares Java 21, Maven 3.9+, JUnit/Surefire, and version `1.0.0-beta.8-SNAPSHOT` in `pom.xml`.

## Contract/exposure inventory

| Category | Current evidence and consumers |
| --- | --- |
| Application API | The closed `ai.loomspan.api` allowlist is authoritative. Neither correction helper is allowlisted. No new application API is implied by complete replay. |
| Supported SPI | `RestSkillHandler` is the sole supported SPI. Correction helpers and advisor chains are internal wiring, with no supported consumer override contract. |
| Configuration and manifest contracts | `output_schema`, `output_schema_max_retries`, provider retry policy, and quotas have existing author-visible behavior. Replay size is currently described in authoring docs; the ticket explicitly authorizes changing the internal candidate bound while retaining retry/feedback limits. |
| Persisted or serialized contracts | No durable correction replay format is exposed as a public interchange contract. Candidate JSON-string encoding is internal request composition. |
| Ephemeral diagnostic formats | Existing advisor outcomes, request messages, action rejection previews, and failure records explain the current run. Complete replay changes message text/length, not record schemas, endpoint/SSE semantics, or Console compatibility markers. Console remains a downstream diagnostic consumer. |
| Internal or accidentally exposed implementation | Public `OutputSchemaCallAdvisor`, its constructors/constants, provider interfaces/advisors, and package-private `StepActionCorrection` are internal under AGENTS.md. Existing tests establish behavior without establishing supported API compatibility. |

No new Spring bean or conditional replacement point is involved. In-repository helper callers are the schema advisor itself, step engine, and focused tests; all located truncation-specific tests are listed above. The research found no protected Java-to-Go protocol shape requiring coordinated Console edits for replay fidelity alone.

## Documentation impact and drift

The repository-bundled `agent-skills/loomspan-docs/SKILL.md` was consulted as the documentation router after executable inventory. Its version matches `pom.xml`; the skill and source are from this checkout. Routed documents read: the skill-authoring index, source-verification, output-contracts, validation-workflow, and traces-and-debugging.

Classification: **aligned** at the researched snapshot. `traces-and-debugging.md:303` explicitly describes latest bounded assistant replay, `:340` names truncation tests, and `:371` describes step-action head/region/tail replay. The index coverage entry at `README.md:88` describes bounded step-action evidence. Complete replay changes information skill authors need for diagnosing correction and provider-limit failures, so these are authoring-impact locations for the next stages. Output-contracts already states complete initial schema guidance and separate validation/retry behavior.

## Historical context and related research

The ticket records neighboring equipment-workflow observations and retained ignored provider journals at a separate sidecar-suite workspace. This research did not inspect or alter those captures; the requirements do not depend on them. Its extra-closing-brace failure shape is already represented by the engine's synthetic offline test. No earlier dedicated correction research artifact was found by a correction/output-schema/step-action filename search of `ai/thoughts`.

## Open questions for planning

- How should the new regression fixtures represent the source-derived equipment/comparison output while staying self-contained and deterministic? Existing short/long tool-envelope fixtures cover the failure shape but not that structured domain example.
- Which existing provider integration seam best demonstrates a context-limit failure during a complete correction without paid calls? Existing exception propagation and provider attempt tests supply the seam; the new test must establish no lossy fallback and no successful output.
- The blank-candidate omission behavior is pre-existing; the ticket's exact preservation requirement warrants an explicit plan decision for whitespace-only responses as well as large nonblank candidates.

These are implementation/test-design decisions resolvable from the ticket and local evidence, not developer escalations. There is no remaining ambiguity about the required complete-evidence outcome or approved full profile.
