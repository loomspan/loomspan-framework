# PR 23 — Share output-validation policy across execution paths

## Outcome

Reduce duplicated retry and outcome policy around final-output validation so
future validation changes can be applied consistently to ordinary model
execution and planning-parent synthesis. Preserve all existing observable
behavior; this is a focused internal refactor, not a redesign of execution or
validation semantics.

## Context

This follow-up is based on [GitHub PR 23](https://github.com/loomspan/loomspan-framework/pull/23).
That PR's contents and status were unavailable during ticket authoring; the
pipeline must establish its relationship to the implementation checkout.
The title and filename number is the proposed identifier assigned by the local
ticket-numbering rule, not evidence of a newly created GitHub PR.

Ordinary execution validates assistant text through schema, evidence, and
linter advisors. Planning execution first validates action envelopes, then
validates the extracted finalResponse payload within the final-step loop.
Schema and evidence evaluation already use shared validators. The overlapping
code is in attempt accounting, retry/exhaustion decisions, outcome construction,
feedback formatting, and the small regex linter check.

The two integrations have intentional differences worth retaining: ordinary
execution uses nested advisor retry loops; planning uses a final-step loop with
separate validator counters. Ordinary linter exhaustion records an exhausted
outcome and returns the response for skills without output bindings, while
planning rejects finalization. With output bindings, ordinary execution also
rejects an exhausted linter result before returning assembled output.

Completed PR 22 (local commit 35cded1, "Assemble skill outputs through declared
bindings") is an implementation prerequisite for this expanded scope.
OutputBindingComposition and OutputBindingProjection already share source
selection, schema projection, model-override rejection, assembly, and complete
schema validation. Preserve those shared authorities rather than reimplementing
their algorithms.

PR 22 adds overlapping policy integration: planning's
validateContributionFailure repeats schema retry/status and outcome accounting;
ordinary fully bound completion uses validateBoundPolicies, while planning
uses requireCompleteOutputPolicies and its final-validation helpers. Both
paths compose, check, and record fully bound completion, but their diagnostics
are not identical. Planning also rechecks the complete assembled schema after
composition; repeated checks within one path are distinct from duplicated
implementations and are not automatically redundant.

## Requirements

- Share reusable validation-policy mechanics between the two integrations,
  rather than creating another schema or evidence validator. Attempt/status
  calculation, outcome construction, issue formatting, and regex checking are
  extraction candidates; the pipeline chooses the cohesive boundaries.
- Include output-binding contribution-failure accounting and fully bound
  completion's schema/evidence/linter policy integration in that extraction.
  Reuse the existing binding composition and projection authorities. Preserve
  current per-path recording and diagnostic differences; do not remove repeated
  validation calls merely because they call the same validator.
- Keep ordinary advisor execution and planning-step execution separate. Each
  integration retains ownership of candidate extraction, validation timing,
  model requests, and its execution lifecycle. Do not replace them with one
  universal retry loop.
- Preserve validation ordering, retry budgets and attempt numbering, exhaustion
  behavior, correction-message content and composition, exception behavior,
  outcome recording, and trace/diagnostic content, including existing explicit
  resource limits. Preserve prompt, candidate, and evidence fidelity.
- Preserve ordinary linter exhaustion returning its response without output
  bindings, ordinary bound-output exhaustion failing completion, and planning
  exhaustion failing finalization. Do not normalize those behaviors as part of
  this cleanup.
- Fully bound outputs must continue to complete without a final model request
  or semantic correction attempt. Invalid immutable bound sources, schema,
  evidence, or linting must fail without provider fallback. Mixed outputs must
  retain their invocation-local source snapshot across corrections, reject
  model-supplied bound destinations, and validate evidence and linting against
  complete assembled output. Preserve projected model guidance, planning step
  accounting, assembled-result provenance, and assembly event timing/count.
- Planning action envelopes and tool-call steps must not acquire the parent's
  final-output checks or final-output guidance. Final-output correction must
  remain within the final step without reexecuting accepted plan tasks.
- Forwarding parents using output_from must retain exact child-result
  forwarding, producer-owned validation, and no parent final model request or
  parent final-output validation.
- Keep changes internal. Do not add supported API, SPI, configuration keys, or
  alter supported contracts, lifecycle, concurrency, or transport behavior.

## Acceptance criteria

- [ ] Both integrations reuse a cohesive implementation of overlapping
  validation policy; remaining path-specific logic reflects execution or
  diagnostic responsibilities rather than a second implementation of that
  shared policy.
- [ ] Focused regression evidence establishes unchanged success, correction,
  and exhaustion behavior for schema, evidence, and linting, including retry
  ordering, budgets, attempt numbering, exceptions, and recorded outcomes.
- [ ] The ordinary/planning linter exhaustion difference remains explicit and
  covered, and correction prompts and diagnostics retain their existing
  content and fidelity.
- [ ] Binding contribution failures use shared retry/outcome policy while
  retaining current budgets, diagnostic content, and per-path recording.
- [ ] Fully bound ordinary and planning outputs retain no-final-model-call
  completion and immediate failure without correction/fallback for immutable
  invalid output. Mixed-output corrections retain the same source snapshot,
  reject bound-destination overrides, and check the complete assembled output.
- [ ] Bound-output linter exhaustion still fails ordinary completion;
  projected guidance, step accounting, provenance, and assembly events remain
  unchanged. Existing composition/projection algorithms remain shared.
- [ ] Planning still validates the extracted final payload only at completion;
  corrections do not rerun plan tasks, and output_from behavior remains intact.
- [ ] Supported API, SPI, configuration, lifecycle, concurrency, and transport
  contracts remain unchanged, with the required public-surface architecture
  check passing after production-type changes.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** medium
- **Rationale:** Behavior-preserving intent is settled, but the shared abstraction
  boundary remains open and nested versus final-step retries require careful
  discovery and design to avoid changing semantics.
- **Reassessment triggers:** A current-checkout assessment that establishes a
  bounded, mechanically clear extraction preserving every requirement may
  support Fast-Track 2-Step Pipeline — Implementation & Review. Any proposed
  observable behavior change falls outside this ticket.
