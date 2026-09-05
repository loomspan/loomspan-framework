# Loomspan Documentation and Source Evidence Protocol

Shared methodology for using the `loomspan-docs` skill and the skill-authoring knowledge base during research, planning, testing, implementation, and review. Step commands reference this document instead of repeating it.

## Evidence Hierarchy

1. **Executable evidence is authoritative.** Establish current behavior from the matching checked-out production source, focused tests, fixtures, samples, configuration, and wiring — and, when reviewing, the actual diff and verification results.
2. **The `loomspan-docs` skill is a supplement and router.** Invoke it after the executable-evidence inventory to identify intended author-facing semantics, route the relevant version-aligned topics, and detect documentation drift. It never replaces source investigation and never overrides contradictory executable evidence.
3. **Version alignment.** If the installed skill and the checkout may represent different revisions, disclose the mismatch and do not use the skill for version-sensitive claims, expectations, or decisions.

## Drift Classification

Whenever documented semantics and executable evidence are compared, record the outcome explicitly as one of:

- **aligned** — documentation and executable behavior agree;
- **documentation drift** — the code is correct and the documentation is stale or wrong;
- **possible framework defect** — the documentation states the intent and the code appears not to honor it;
- **unresolved** — the comparison could not be completed with confidence.

Never silently choose one side. Carry material discrepancies into the step's artifact, and in pipeline mode escalate any `possible framework defect` or `unresolved` classification that affects the work's scope or semantics.

## Skill-Authoring Knowledge Base

- Location: `agent-skills/loomspan-docs/references/skill-authoring/`. Its `README.md` owns the topic routing, the coverage table, and the `LLM-First Authoring Standard`; `source-verification.md` defines how claims are grounded.
- A change has skill-authoring impact when it changes anything a Loomspan skill author needs to know: manifest syntax or validation, defaults, mappings, execution or planning semantics, evidence, input/output contracts, capability visibility or RBAC, attachments or virtual files, model selection, limits or quotas, traces, debugging, or testing guidance. Do not infer impact (or its absence) from changed directory names alone; trace the behavior to its author-facing effects.
- When impact exists, update the relevant topic documents in the same change as the behavior, support every exact behavioral claim with the implemented production path and focused tests/fixtures/samples, distinguish enforced behavior from recommendations and known limitations, and update the README coverage table when a topic's coverage or confidence changes.
- Apply the `LLM-First Authoring Standard`: preserve progressive disclosure and routing, keep topic guidance self-contained and precise, and do not add narrative or duplicated prose that does not improve retrieval, interpretation, or task execution.
- An omitted impact assessment is not equivalent to "No impact"; record the rationale and evidence either way.
