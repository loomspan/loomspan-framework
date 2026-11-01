# PR 28.5 — Preserve explicit Java failure meaning through orchestration and diagnostics

## Outcome

Both Java execution forms fail truthfully. Only designated recoverable failures become failed-call feedback in standard tool calling; direct callers receive failures and explicit step planning still terminates. Canonical records own classification and failure identity, and Console consumes those facts.

## Requirements

- Use PR 28.1's single recoverability policy throughout invocation, orchestration, and recording. Identify and test actual reflection/proxy, worker-future, and Spring AI callback wrappers; normalize transparent infrastructure wrappers where practical. Do not classify through arbitrary cause searches, class/message heuristics, HTTP statuses, or separate per-consumer policies. An application wrapper around a recoverable cause is a new terminal failure.
- Integrate through Spring AI's existing tool-exception-processing hook. Replace broad default runtime-exception-to-feedback conversion, retaining the current tool manager and canonical tool-selection enforcement. Do not grow a terminal-exception blacklist, replace the manager, or add an outcome wrapper, retry policy, or recovery engine.
- Direct invocation propagates recoverable and terminal failures through existing reporting boundaries with classification, message, and cause preserved; identical exception-object propagation is not required. Standard tool calling supplies feedback only for designated recoverable failures: failed tool-call identity, explicit failed/correction-permitted meaning, and the exact application message. Do not substitute a cause message or stack trace. Step planning terminates on all tool failures, including recoverable ones; no correction loop, new inability outcome, or nested escalation protocol is included.
- Failures traverse existing skill/tool failure recording before outer standard feedback. Feedback never becomes successful output, successful-skill credit, or a source for successful-result bindings, even if the enclosing mission later succeeds. Standard-mode model correction, alternate tool selection, or changed approach uses normal metered paths: failed attempts consume budgets, no counters reset/refund, and the parent timeout continues. Step-slot semantics remain unchanged.
- Authorization, quota, timeout, cancellation, and shutdown controls stay terminal. Preserve `MissionWorkExecutor` primary-cancellation precedence and existing lifecycle ownership; recoverability cannot override a controlling terminal outcome.
- Record explicit recoverable/terminal classification at the existing canonical failure boundary using the shared policy. Preserve exact recoverable messages, exception/cause diagnostics, and separate contextual summaries. Transparent propagation of the same exception, including recognized infrastructure wrappers, reuses identity. Application wrappers receive distinct failure identities even when their cause was recorded; preserve recorded-cause linkage without replacing the earlier failure. Provider-attempt correlation must not conflate failure identities. Do not add a causal-graph subsystem or second failure journal.
- Update current canonical writers/readers, observer projections, Java/Go trace processing, Console contracts/rendering, and fixtures together for this failure contract. Consumers read classification; they must not infer it from exception names, timing, adjacency, or eventual mission success. Recoverable classification does not claim that correction occurred. Assess the exact-version Console compatibility marker; no historical readers, migrations, adapters, or cross-version guarantees.
- Preserve bounded capture and explicit truncation, diagnostic content, and the existing `ExecutionJournalProjector` field-name redaction exception only in its derived-journal/observer scope. Do not extend it to canonical records, feedback, or results or introduce other sensitivity classification/content rewriting. Update error/observation guidance for the changed behavior.

## Acceptance criteria

- [ ] Shared tests exercise SkillMethod and SkillExecutor through direct invocation, actual standard tool-manager processing, and step termination: designated recoverable failure, ordinary terminal failure, transparent infrastructure wrappers, application terminal wrappers, and exact message/cause preservation.
- [ ] Real standard tool processing feeds back only designated recoverable failures after failed recording, identifies the failed call, and preserves canonical selection enforcement. Terminal security/execution controls propagate and primary cancellation remains authoritative.
- [ ] Failed attempts cannot earn successful-result credit or satisfy successful-result bindings. Standard continuation/reattempts consume existing tool/model/provider/skill/usage budgets with no reset/refund and no parent deadline restart; step planning still terminates without a correction loop.
- [ ] Same-exception/transparent-wrapper propagation shares failure identity; application wrappers have distinct identities and recorded-cause linkage. A terminal wrapper around an already-recorded recoverable exception stays visible as terminal. Provider-attempt correlation remains intact without deduplication by arbitrary cause chain.
- [ ] Canonical records, applicable projections, and Console preserve explicit classification, exact recoverable message, and diagnostics without inferring continuation. Current Java/Go fixture parity and affected UI/contracts pass; the existing narrow journal exception and truncation controls remain scoped correctly.
- [ ] Tests and documentation reject obsolete exception-to-successful-text/default broad feedback behavior; `LoomspanPublicSurfaceArchitectureTest` and relevant Console verification cover production changes.

## Context and sequencing

Depends on [PR 28.4](loomspan-pr-28.4-shared-declarative-authorization.md), including shared invocation and generation integration. Owns finding 3's orchestration disposition and finding 4's canonical meaning/identity in [the accepted design](../phases/configured-java-skill-executors.md). Read the [feature design lens](../framework-feature-design-lens.md). [Planner Failure Recovery](../phases/planner-failure-recovery.md) is a separate effort and not an implementation requirement here.

Source recheck: `SpringAiChatClientAssembler` builds the existing default manager behind `CanonicalToolCallingManager`; `DefaultCapabilityInvoker` already records failed tool outcomes. `LoomspanSession.recordFailure` currently deduplicates by walking arbitrary causes, while provider correlation also walks causes for a different purpose. Replace the identity conflation, preserving correlation. Keeping disposition and canonical failure recording in one ticket makes the policy observable end to end without temporary classifiers.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Cross-cutting failure semantics, terminal controls, diagnostic identity, and Java/Go/Console contracts require full assurance.
- **Reassessment triggers:** Concrete wrapper/control behavior conflicting with the accepted policy; additional recovery machinery requires a separate product decision, not implicit expansion.

## Pipeline notes

- Breaking current diagnostic contracts and old exception conversion behavior is intentional. There are no deployed Framework/Sidecar consumers; update current readers/fixtures directly and introduce no compatibility paths. Intermediate failures across PR 28 are allowed; final cross-component coherence is required.
- This ticket implements standard-mode designated feedback only. It does not implement the follow-up's step recovery loops, partial-progress protocols, replanning, or new recovery quotas.
