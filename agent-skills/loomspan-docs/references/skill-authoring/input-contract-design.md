---
audience: loomspan-skill-builder
status: development
applies_to: bundled-loomspan-revision
coverage: design-guidance
---

# Designing Useful Input Contracts

Use this guidance when designing or reviewing inputs that an application or a
parent model must supply to a skill. It applies across declaration styles; consult
[reflected Java contracts](input-contracts.md), [REST manifests](rest-skills.md)
and version-matched schema support before choosing syntax. This is design advice,
not an additional schema language or a promise of measured model accuracy.

## Model the Receiving Responsibility

Start with the information the receiver actually needs to complete its job.
Declare stable field names, types, nested record shapes and requiredness instead
of hiding essential data inside an unconstrained map. An explicit contract makes
the expected handoff discoverable and lets validation turn structural omissions
into actionable errors. It can reduce ambiguity for a model; it does not guarantee
that the model supplies correct values or completes the task.

Make a field required when its absence makes this input incomplete. Keep genuinely
optional data optional, including fields unavailable on legitimate branches.
Describe meaning, units, accepted representation and source/derived distinctions
concisely. Descriptions complement constraints; prose does not become an enforced
rule. Check actual generated requests before relying on a version to expose every
piece of schema metadata to the model.

## Choose Object Boundaries Deliberately

- Use named records and typed array items for stable structure. An array of
  arbitrary objects cannot enforce mandatory metadata on each record.
- Keep arbitrary-key maps open when extensions are part of the contract. Close
  stable records when unexpected fields would be misleading or invalid. A parent
  object's openness need not imply openness of every nested object.
- Separate authoritative source data from derived interpretation. When a receiver
  needs an unchanged source record, explain that absent optional fields remain
  absent; an illustrative shape is not a request to invent defaults or blank values.
- Specify whether a receiver expects an entire result envelope, its data value,
  or a selected projection. Choose the smallest representation that preserves
  the information needed for the receiver's responsibility and audit requirements.
  Avoid duplicating large unrelated payloads across every boundary.

## Verify the Right Guarantees

Exercise representative valid inputs, legitimate optional branches and targeted
invalid inputs: missing nested fields, wrong item types and prohibited additions.
Then inspect a real handoff and correction request to confirm both the information
the model sees and the validation that occurs before execution. More fields are
not automatically better: consider prompt size, copying effort and repair burden.

Distinguish three questions: is the input structurally valid; is it faithful to
its source; and is the resulting decision sound? A required typed collection can
still omit original members, repeat members, or contain rewritten values.
Structural validation alone does not establish provenance, cross-result equality,
authorization or business truth. Use deterministic application checks or supported
[declared child input bindings](input-bindings.md) when exact source transfer is required; do not assume a
declared dependency automatically binds or copies task results.

Treat contract tightening as a caller-facing change. Check existing producers,
replay fixtures and valid failure branches before activation. Keep validation
evidence separate from model-quality comparisons, and scope accuracy conclusions
to the models and scenarios actually tested.
