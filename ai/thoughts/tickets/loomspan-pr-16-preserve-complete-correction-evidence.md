# PR 16 — Preserve complete evidence during model correction

## Outcome

Give models the complete rejected response when Framework asks them to produce
a complete corrected replacement. Prioritize Framework correctness and the
information capable models need for complex tasks over implicit prompt-size
optimizations intended for smaller models. Smaller models remain usable within
their actual resource limits; Framework must not discard necessary correction
evidence by default to accommodate an assumed model capability.

## Requirements

- Remove the arbitrary 8192 candidate truncation from both output-schema
  correction and step-action correction. Preserve the entire latest rejected
  response exactly, including its tail, in the corrective model request. Do not
  substitute a larger arbitrary cutoff, prefix, excerpts, or generated summary.
- Retain the original task, applicable schema/action constraints, available
  authoritative execution evidence, and actionable validation feedback. Keep
  rejected output treated as response data rather than Framework instructions;
  preserve the existing role/encoding protections in each correction path.
- Keep retries bounded. Reuse the original correction context with the latest
  rejected candidate rather than accumulating all previous rejected candidates.
  Diagnostic feedback may remain concise and bounded; that is separate from
  preserving the candidate the model must repair.
- Preserve strict parsing, schema/action validation, authorization, execution
  boundaries, and exhaustion behavior. Rejected actions must not execute or
  publish successful output. Sending complete evidence does not make a model's
  correction automatically trustworthy or acceptable.
- If a real configured request/resource limit prevents sending complete
  evidence, surface an explicit failure identifying that limitation. Do not
  fall back to a lossy correction request. Provider-reported context-limit errors
  may establish this failure; this work does not require guessing model context
  windows, adding a tokenizer/catalog, or introducing a new public configuration
  surface. Existing explicit limits and provider failures must remain coherent.
- Preserve actual model-request and diagnostic fidelity. Do not introduce
  masking, redaction, sensitivity classification, automatic JSON repair, or a
  Framework-generated replacement for the model's response.

## Acceptance criteria

- [ ] For a rejected response longer than 8192, both correction paths send the
  entire original candidate, including content beyond the old cutoff and the
  failing tail, without altering its decoded text. Unicode content survives.
- [ ] Corrective requests retain the original task, applicable constraints,
  authoritative context, and actionable failure feedback. Candidate content
  stays in its intended data role/encoding even when it resembles instructions.
- [ ] Repeated failures retain only the latest rejected candidate, do not grow
  retry history without bound, and exhaust within the existing retry allowance.
- [ ] Valid corrections pass the existing validators; invalid corrections fail.
  Rejected tool actions have no execution side effects, and rejected final
  output cannot be published as successful merely because correction was tried.
- [ ] A resource/context-limit failure is distinguishable from successful
  correction and never causes a truncated or summarized fallback request.
- [ ] Deterministic offline verification establishes these behaviors without
  paid-provider calls. Source-derived equipment-workflow cases provide useful
  large structured-output examples; acceptance does not require a live model
  to preserve citations or prove a particular model's reasoning quality.

## Context and scope

Observed on Framework snapshot `4313a7fcdbad33ef358b6ee4d068ce610f3dee51`
through embedded Java and Python/Sidecar in the separate
`loomspan-sidecar-test-suite` workspace. Its comparison fault appends one extra
closing brace to an otherwise complete reviewed response. The rejected
candidates are approximately 14–15K Unicode code points, but independent
provider journals show only 8192 code points of candidate text in corrective
requests. The full original canonical input remains available. This is an actual
runtime request transformation, not merely trace truncation.

Known source hints, to be verified against the implementation checkout:
`OutputSchemaCallAdvisor` retains a candidate prefix;
`StepActionCorrection` retains a head and selected region/tail. Both currently
apply an internal 8192 candidate bound using different counting conventions.
The output-schema retry already rebuilds from the original prompt and latest
failure rather than retaining every failed attempt.

Two initial scoped model corrections completed their workflows but rewrote child
assessments and comparison references. A revised application prompt preserved
the original child assessments exactly; Java still reduced outer citations from
24 to 13, while Sidecar retained all 20 references but changed their order.
Both citation lists were fully visible even in the truncated candidate. Therefore
truncation is an observed information-loss defect, not an established sole cause
of citation changes, and complete candidate preservation is not a guaranteed
model-quality remedy. Missing references and harmless reordering require distinct
business acceptance treatment outside this Framework ticket.

Local retained evidence is in the neighboring suite's
`evidence/full-correction-live-20261002-130627/`, with independent review in
`evidence/review-full-correction-live-20261002-130627/` and findings in
`docs/correction-context-finding.md`. These ignored captures do not follow Git;
the ticket's intent and acceptance criteria do not depend on their availability.
Preserve existing captures, actual traces, journals, and durable records if that
workspace is used. Its provider access is disabled and the prior scoped paid
calls are exhausted; this ticket authorizes no additional paid calls.

Scope is the fidelity of correction requests in these two paths. General model
context management, automatic repair, chunked/patch correction protocols, new
model-selection rules, trace-retention redesign, and changes to the equipment
suite's business acceptance policy are excluded. Implementation mechanisms and
test locations are left to the pipeline; no new public extension SPI is intended.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** The outcome is settled, but this changes runtime model requests
  across schema and step-action correction. Resource-limit behavior, validation,
  and tool-execution guarantees require investigation and independent review.
- **Reassessment triggers:** Current-checkout evidence that materially changes
  correction consumers, request-limit enforcement, or supported configuration
  contracts; discoveries must preserve the complete-evidence outcome.

## Pipeline notes

- Removing the previous internal candidate-size guarantee is intentional.
  Existing tests that expect 8192 truncation must not preserve that behavior as
  a compatibility requirement. Retry and feedback bounds remain in scope to
  preserve. This does not authorize unrelated API/configuration breaks.
