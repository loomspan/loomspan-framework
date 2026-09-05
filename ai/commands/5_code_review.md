---
description: Review a change independently, fix actionable findings in pipeline mode, and validate the final result
---

# Code Review

You are tasked with performing a rigorous code review from a **fresh context**. Review the resulting code independently for correctness, safety, compatibility, maintainability, and adequate verification. If a ticket, implementation plan, or testing plan exists, also validate conformance to those requirements, but do not let the plan limit the defects or risks you look for. In pipeline mode, fix actionable findings only after completing the independent review, then review and fix again until you believe the work is clean.

## Shared Protocols

- Follow `ai/commands/shared/automation-protocol.md` for execution modes, the escalation contract, and the Step Report you must end with.
- Follow `ai/commands/shared/loomspan-docs-protocol.md` for the evidence hierarchy, `loomspan-docs` skill usage, and drift classification. Establish actual behavior from the reviewed diff, matching checked-out production source, callers, focused tests, fixtures, samples, configuration, and verification results before consulting authoring guidance.

## Core review principles

1. **Review the code, not the story.** Reconstruct the change from Git and the repository. Do not trust implementation summaries, plan checkmarks, or claimed test results without verification.
2. **Inspect independently before comparing with the plan.** First ask whether the code is correct. Only afterward ask whether it matches the ticket and plan. A plan can be incomplete or wrong.
3. **Findings come first.** The primary output is actionable defects and regressions, ordered by severity. Plan conformance and successful checks are supporting information.
4. **Use concrete evidence.** Every finding must identify the affected code, the triggering condition or execution path, and the resulting impact.
5. **Be skeptical, not speculative.** Verify candidate findings against surrounding code, callers, tests, framework behavior, and existing safeguards before reporting them.
6. **Review by risk.** Select review lenses based on the actual diff. Do not force irrelevant database, UI, or security-framework checklists onto unrelated changes.
7. **Security is always considered.** Do not limit security review to concerns mentioned in the plan; omissions in requirements can themselves be findings.
8. **Passing tests do not prove correctness.** Evaluate whether tests exercise the changed behavior, failure modes, compatibility boundaries, and meaningful assertions.
9. **Finish the independent review before editing.** Do not fix issues as you discover them; first complete the defect, verification, conformance, and documentation-impact review against the unchanged candidate. In pipeline mode, apply fixes only after that initial review is complete. In standalone mode, modify the implementation only when the user explicitly asks you to fix findings.

## Inputs

The command may be invoked with any combination of:

- ticket or requirements document;
- implementation plan;
- testing plan;
- the review number to use in the output filename, provided by the pipeline (for example, `1` for the first review and `2` for the next fresh review);
- branch, commit, pull request, or comparison base;
- working-tree changes.

Read supplied tickets, requirements, plans, and testing plans **completely** before broader investigation. If no plan exists, perform a general code review from the available requirements and diff. A plan is helpful context, not a prerequisite.

## Review process

### Step 1: Establish the exact review scope

Assume no prior conversational context. Determine exactly what changed before evaluating it.

1. Read repository instructions such as `AGENTS.md` and any directly supplied ticket, plan, or testing-plan files.
2. Inspect repository and working-tree state:

   ```bash
   git status --short
   git branch --show-current
   git diff --stat
   git diff
   git diff --cached --stat
   git diff --cached
   ```

3. Include untracked implementation files in the review by locating and reading them; `git diff` does not show their contents. Exclude prior pipeline artifacts under `ai/thoughts/reviews/` from the implementation review scope and do not read their contents.
4. If reviewing committed branch work, determine the correct comparison base from the user-provided base, upstream, or repository default branch. Use the merge base and inspect both the summary and full diff. Do not assume the base branch is named `main`.
5. Distinguish:
   - committed branch changes;
   - staged changes;
   - unstaged changes;
   - untracked files;
   - unrelated pre-existing working-tree changes.
6. Produce a concise internal inventory of changed production code, tests, configuration, documentation, migrations, dependencies, generated files, and public interfaces.

Do not review only the diff hunks. Read enough surrounding code and connected callers/callees to understand the changed behavior.

### Step 2: Reconstruct intended and actual behavior

1. Read the changed files fully when practical. For large files, read every changed region with sufficient surrounding context and inspect all relevant types and methods it depends on.
2. Trace important execution paths across configuration binding, construction/wiring, runtime calls, persistence, serialization, external integrations, and cleanup as applicable.
3. Locate existing tests and nearby implementation patterns.
4. Identify:
   - the behavior that existed before;
   - the behavior introduced by the change;
   - public or operational contracts affected;
   - trust boundaries and failure boundaries;
   - assumptions the implementation relies on.
5. Use the ticket and plans to understand intent, but record discrepancies between those documents and executable behavior rather than silently choosing one.
6. For Loomspan framework changes, read the canonical policy in `ai/thoughts/framework-feature-design-lens.md`. Inventory exposure and evidence, then determine whether each affected surface is deliberately supported Application API or SPI, user-visible configuration or persisted behavior, current-run diagnostics, or internal implementation before evaluating compatibility. Read the ticket's optional `Pipeline notes`: a clearly covered intentional exception is verified for containment rather than reported merely because it is incompatible; broader or materially different impact remains a finding or developer question.
7. When a change affects the Loomspan Console application-adapter REST/SSE, acquisition, problem, or consumed NDJSON boundary, verify the declared Java-to-Go boundary-coordination scope against observable semantics, executable fixtures, and exact release-string rejection behavior.

### Step 3: Perform an independent defect review

Perform this step **before** plan-conformance validation. Select all applicable review lenses.

#### Functional correctness

- Verify normal behavior, boundary conditions, invalid inputs, empty/null states, partial state, ordering, retries, idempotency, and recovery paths.
- Trace every changed branch to its externally visible result.
- Look for behavior that succeeds locally but fails when composed with callers or downstream consumers.
- Check whether error handling preserves the original failure, produces actionable diagnostics, and avoids inconsistent state.

#### API and compatibility

- First separate technical exposure and existing behavior from evidence of a deliberately supported contract. Documentation, an explicit API/SPI allowlist, explicit ticket requirements, and verified consumer usage can establish protection.
- A public modifier, interface, constructor, Spring bean, `@ConditionalOnMissingBean`, existing test, fixture, or previous implementation does not by itself establish a supported contract.
- For deliberately protected Application API, Supported SPI, Configuration and manifest contracts, and Persisted or serialized contracts, check source, binary, configuration, serialization, schema, stored-data, CLI, and extension-point compatibility as applicable.
- Verify defaults and any explicitly required migration behavior. For a pre-1.0 break clearly authorized by the ticket or `Pipeline notes`, verify atomic repository updates rather than assuming mixed old/new behavior is required.
- Identify silent behavior changes, ambiguous precedence, renamed fields, altered validation order, and changed exception contracts.
- Do not report an intentional break clearly supported by the ticket or `Pipeline notes` merely because it removes obsolete Internal or accidentally exposed implementation. Do report regressions of deliberately supported behavior and breaks that lack clear intent, evidence about whether the surface is supported, or impact assessment.

#### Security and privacy

- Identify trust boundaries and attacker-controlled inputs.
- Check authorization, authentication, injection, path handling, deserialization, request forgery, unsafe redirects, and privilege changes where relevant.
- Check secrets, tokens, credentials, headers, personal data, and sensitive payloads for exposure through logs, traces, metrics, exceptions, object rendering, or persistence.
- Verify secure defaults and ensure new configuration cannot bypass established controls.
- Do not report generic security concerns without a concrete path through the changed code.

#### Concurrency and lifecycle

- Check shared mutable state, publication, thread safety, races, deadlocks, blocking work, cancellation, and timeout behavior.
- Verify clients, executors, streams, files, connections, and other resources are created at the correct scope and closed appropriately.
- Look for per-request construction of expensive reusable objects and unsafe reuse of request-scoped objects.
- Consider application startup, shutdown, refresh, and failure during initialization.

#### Persistence and migrations

- When applicable, verify forward and rollback safety, transaction boundaries, data preservation, constraints, indexes, backfills, ordering, and compatibility during rolling deployment.
- Check behavior with existing production data, not only empty schemas.

#### External services and distributed behavior

- Check timeouts, retries, rate limits, duplicate effects, partial responses, malformed responses, protocol compatibility, fallback behavior, and error translation.
- Verify endpoint/account/tenant routing cannot cross boundaries.
- Check whether retry or fallback behavior can multiply cost or side effects.

#### Performance and resource bounds

- Look for unbounded collections, recursion, payloads, retries, concurrency, cardinality, or work amplification.
- Check hot paths for avoidable repeated construction, network calls, parsing, reflection, or full scans.
- Require a concrete workload or scale trigger before reporting performance findings.

#### Observability and operations

- Verify logs, metrics, traces, and errors identify the failing operation without leaking sensitive data.
- Check metric-tag cardinality. For Ephemeral diagnostic formats, verify current writer/reader/projector/debugging-tool coherence, usefulness, accuracy, ordering, failure visibility, security boundaries, and redaction. Historical or cross-version readability is not required unless a ticket explicitly changes the canonical policy.
- Confirm operators can distinguish multiple configured instances, tenants, models, or endpoints when the change introduces them.
- Verify failures at startup and runtime are diagnosable.

#### Maintainability and repository fit

- Check ownership boundaries, naming, duplication, extension points, and consistency with existing patterns.
- Report maintainability concerns only when they create a concrete risk of misuse, divergence, or future defects; do not report stylistic preferences as findings.
- Check dependency, build, packaging, and deployment changes for necessity and unintended scope.
- Treat unjustified overloads, aliases, fallbacks, adapters, deprecated paths, legacy readers, duplicate interfaces, compatibility constructors, bridge types, dual behavior, and retained obsolete paths as actionable maintainability risks.
- Check for accidental public types, internal types leaked through public Application API or Supported SPI signatures, and new or retained `@ConditionalOnMissingBean` beans not deliberately established as Supported SPI.

#### Documentation and user-facing semantics

- Verify configuration examples, migration notes, API documentation, samples, and troubleshooting guidance match executable behavior.
- Identify stale or missing documentation when users or operators must act differently after the change.
- Verify examples do not encourage insecure credential handling or unsupported behavior.

### Step 4: Evaluate tests and run verification

1. Map each changed behavior and primary risk to existing or new tests.
2. Review test quality:
   - Does the test fail without the implementation?
   - Does it assert externally meaningful behavior rather than implementation details?
   - Does it cover negative and compatibility paths?
   - Can mocks hide the integration defect being tested?
   - Are assertions strong enough to detect incorrect routing or state?
   - Are compatibility-path tests limited to protected surfaces, and do tests confirm intentionally obsolete behavior was removed rather than hidden behind a fallback when that removal is authorized by the ticket or `Pipeline notes`?
3. Compare implemented tests with the dedicated testing plan when present. The testing plan is expected coverage, not a ceiling.
4. Run the narrowest relevant tests first, followed by the module or repository verification appropriate to the change.
5. Record exact commands and results. Do not state that a check passed unless it was run successfully in this review context.
6. If a check cannot be run, state why and describe the residual risk. Do not convert “not run” into “pass.”
7. Distinguish failures caused by the reviewed change from unrelated environment or pre-existing failures, with evidence.

### Step 5: Validate requirements and plan conformance

After the independent review:

1. Map every ticket acceptance criterion and plan success criterion to code, tests, documentation, or other executable evidence. List useful non-automatable observations separately as optional developer checks; they are not completion gates.
2. Verify completed plan checkboxes against the repository; do not trust checkmarks by themselves.
3. Identify:
   - missing requirements;
   - partially implemented requirements;
   - deviations that introduce risk;
   - deliberate deviations that are safe and justified;
   - implementation added outside the ticket's intended scope.
   - intentional breaks authorized by the ticket or `Pipeline notes` incorrectly retained behind compatibility machinery;
   - unexplained preservation or surface growth not supported by evidence that the affected surface is deliberately supported.
4. Do not report harmless naming or mechanical differences as defects. Put non-defective deviations in the conformance summary.
5. A plan-conformant implementation can still receive blocking findings.

### Step 6: Validate Loomspan skill-authoring documentation impact

Review the actual diff for changes a Loomspan skill author needs to know about. Do not rely only on the plan's conclusion or changed directory names.

Consider changes to:

- manifest syntax and validation;
- model selection and configuration;
- defaults and compatibility behavior;
- mappings and capability visibility;
- execution and planning semantics;
- evidence contracts;
- input/output contracts;
- RBAC;
- attachments and virtual files;
- limits and quotas;
- traces, debugging, and testing guidance.

When impact exists:

1. After reconstructing and testing the changed executable behavior, follow `ai/commands/shared/loomspan-docs-protocol.md` for the knowledge-base routing, claim verification, `LLM-First Authoring Standard`, coverage table, and drift classification.
2. Verify guidance changed in the same implementation and accurately distinguishes enforced behavior, recommendations, and known limitations, with every exact behavioral claim verified against the reviewed production path and focused tests, fixtures, or samples from the matching checkout.
3. Treat missing, stale, unsupported, conflicting, or non-actionable guidance as a normal code-review finding with an appropriate severity.

If there is no skill-authoring impact, record the concrete rationale and evidence reviewed.

## Candidate-finding verification

Before reporting any finding:

1. Re-read the exact changed lines and surrounding implementation.
2. Trace the concrete trigger through callers and dependencies.
3. Search for an existing safeguard, validation, or compensating behavior.
4. Check relevant tests and framework/library semantics.
5. Confirm the issue is introduced by, worsened by, or directly relevant to the reviewed change.
6. State the observable impact without exaggeration.
7. For compatibility candidates, confirm from evidence whether the affected surface is deliberately supported or internal before deciding whether the change is a compatibility regression, an intentional change supported by the ticket or `Pipeline notes`, or an unjustified shim.

Do not report:

- purely stylistic preferences;
- hypothetical problems with no reachable trigger;
- issues already prevented by code you did not initially notice;
- broad architectural wishes unrelated to the change;
- pre-existing defects unless the change makes them materially worse or relies on them. Mention important pre-existing observations separately, not as change-blocking findings.

## Pipeline Review/Fix Behavior

In pipeline mode, one fresh context owns a complete review/fix cycle:

1. Perform the entire independent review process above before making any implementation change. Do not narrow the review to findings from a previous cycle.
2. Review only the current repository state, ticket, and plans. Do not locate or read prior review documents; use the review number supplied by the orchestrator when naming this review's artifact.
3. If there are no actionable findings of any priority and verification is sufficient, make no implementation changes and report `REVIEW_RESULT: clean`.
4. If actionable findings exist, fix all of them using the mismatch and verification discipline in `4_implement_plan.md`; do not restart or expand the original implementation plan. Keep fixes within the ticket's intended outcome and avoid unrelated scope growth.
5. If a fix requires developer intent that cannot be resolved from the ticket, `Pipeline notes`, repository, or plans, stop with `STATUS: needs-developer`, including the evidence and your recommended answer.
6. After fixing, run the relevant verification and perform another complete review of the resulting change. Continue the review/fix loop in this context until no actionable findings remain or you need the developer or encounter an unrecoverable failure.
7. Once the final internal review has no actionable findings and verification is sufficient, report `REVIEW_RESULT: fixes-applied` if you changed any code, tests, documentation, configuration, fixtures, plans, or other implementation artifact during this context. The orchestrator will launch another fresh context to validate your fixes.

Writing or updating this cycle's review document does not by itself count as a fix. A context may report `clean` when the review document is its only filesystem change.

## Finding priorities

Use these priorities consistently:

- **P0 — Critical:** Catastrophic and broadly exploitable/release-blocking issue, such as unavoidable data loss, severe security compromise, or system-wide outage.
- **P1 — High:** A likely or high-impact correctness, security, compatibility, or reliability defect that should block merge.
- **P2 — Medium:** A real defect or meaningful regression with a narrower trigger or practical workaround; normally fixed before merge.
- **P3 — Low:** A concrete low-risk defect, missing defensive behavior, or maintainability issue worth addressing. In pipeline mode it is actionable and must be fixed or escalated; only standalone review may leave it as follow-up work.

Do not inflate severity. Severity reflects impact, reachability, and likelihood—not how much code would be needed to fix it.

## Finding format

Each finding must be independently understandable and actionable:

```markdown
### [P1] Short imperative title

- **Location:** `path/to/file.ext:line`
- **Evidence:** What the changed code does and the relevant execution path.
- **Trigger:** The concrete input, configuration, state, concurrency condition, or failure mode.
- **Impact:** What becomes incorrect, insecure, incompatible, unavailable, or operationally misleading.
- **Recommendation:** The smallest appropriate correction or design constraint.
```

Keep line ranges tight. If several locations contribute to one defect, choose the most useful primary location and cite the others in the evidence.

## Output format

Write the full review to `ai/thoughts/reviews/YYYY-MM-DD-ENG-XXXX-description-review-N.md`, replacing `N` with the review number provided by the pipeline (or `1` for a standalone first review). The review document is an audit trail for the developer; later fresh review contexts do not consume it. Chat output is only the receipt. In standalone mode you may additionally show the findings inline for convenience.

Lead with findings. Do not lead with a summary of work performed.

```markdown
## Code Review Findings

### [P1] ...
[Finding]

### [P2] ...
[Finding]

## Findings Resolved in This Context

- [Original priority and finding, fix applied, and verification evidence; or `None`]

## Open Questions and Assumptions

- Questions that materially affect correctness or review confidence.

## Verification Results

- PASS — `<exact command>`
- FAIL — `<exact command>`: concise failure and attribution
- NOT RUN — `<command or check>`: reason and residual risk

## Requirements and Plan Conformance

- Implemented: [criteria with evidence]
- Partial: [criteria and missing portion]
- Missing: [criteria]
- Safe deviations: [non-defective differences and rationale]
- Compatibility review: [supported surfaces affected, evidence, intentional compatibility changes, relevant `Pipeline notes`, protected consumers, public API changes, and shim/no-shim assessment]

## Skill-Authoring Documentation Impact

- **Assessment:** Affected / No impact
- **Rationale:** Concrete author-facing behavior examined
- **Documents reviewed:** Paths or `None`
- **Evidence checked:** Tests, fixtures, samples, and/or source
- **Coverage table:** Current / Update required / Not applicable
- **LLM-first usability:** Pass / Needs changes / Not applicable

## Residual Risks and Optional Developer Checks

- Tests or environments unavailable during review
- Non-automatable observations the developer may optionally perform after pipeline completion
- Compatibility assumptions not executable locally

## Disposition

- **Request changes** — one or more blocking findings
- **Approve with follow-ups** — standalone review only; no blocking findings, but listed P3 or residual work remains
- **Candidate clean; fresh review required** — all findings found in this context were fixed, but this context changed the implementation
- **Approve** — no actionable findings and verification is sufficient
```

If there are no findings, say **“No actionable findings.”** Do not invent low-value comments to fill the report. Still provide verification results, residual risks, documentation impact, conformance, and disposition.

Optional developer checks do not prevent `REVIEW_RESULT: clean`. Prefer executable evidence and use this section only for observations that cannot reasonably be automated. A missing developer check must not conceal insufficient executable evidence for an acceptance criterion.

End with the Step Report from the automation protocol: `ARTIFACTS` names the review document, `VERIFICATION` repeats the exact commands run in this context, `OPTIONAL_DEVELOPER_CHECKS` carries any useful non-automatable observations from the final repository state, `SUMMARY` carries the disposition and finding counts by priority, and `REVIEW_RESULT` is `clean` or `fixes-applied` according to the rules above. Only a final fresh context returning `clean` provides the pipeline's ground-truth verification for "done".

## Final review checklist

- [ ] Exact diff scope, base, staged, unstaged, and untracked files were identified.
- [ ] Changed behavior was traced beyond isolated diff hunks.
- [ ] Independent defect review happened before plan comparison.
- [ ] Applicable correctness, compatibility, security, concurrency, lifecycle, persistence, integration, performance, observability, and documentation risks were considered.
- [ ] Evidence established how each affected framework surface should be treated before compatibility findings were evaluated, and intentional changes supported by the ticket or `Pipeline notes` were distinguished from regressions of protected contracts.
- [ ] Unjustified compatibility machinery, retained obsolete behavior, accidental public types, leaked internal signature types, and `@ConditionalOnMissingBean` extension points not deliberately established as Supported SPI were checked.
- [ ] Every reported finding has a reachable trigger, concrete impact, tight location, and supporting evidence.
- [ ] Candidate findings were checked for existing safeguards and false positives.
- [ ] Tests were assessed for quality and coverage, not merely counted.
- [ ] Commands reported as passing were actually run in this fresh review context.
- [ ] Ticket, plan, and testing-plan criteria were mapped to evidence when present.
- [ ] Skill-authoring documentation impact was assessed independently against the actual diff.
- [ ] Secrets and sensitive data were checked across logs, traces, metrics, errors, and object rendering where applicable.
- [ ] Findings are ordered by severity and the disposition matches them.
- [ ] In pipeline mode, all actionable findings were fixed or escalated, every fix was followed by another complete review, and `REVIEW_RESULT` accurately reflects whether this context changed implementation artifacts.

Remember: the goal is not to prove that the implementation followed its plan. The goal is to determine whether the change is safe and correct to merge, and to explain any reason it is not with precise, reproducible evidence.
