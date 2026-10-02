# Loomspan repository guidance

## Prompt and data fidelity

Framework and Console must not classify data as sensitive or attempt to mask,
redact, or sanitize prompts, inputs, outputs, tool arguments, evidence, or
diagnostic content. They cannot determine which data is sensitive or whether
altering it would change the response to a user request. Preserve content and
diagnostic fidelity; do not introduce field-name heuristics, secret scanners,
replacement values, or automatic masking. Do not recommend or require these
mechanisms in tickets, plans, tests, reviews, or documentation.

Enforce authentication, authorization, and explicit access boundaries rather
than rewriting content. Existing size limits and explicit truncation are
resource controls, not sensitivity classification or redaction.

Known, supported exception: retain the existing field-name redaction in
`ExecutionJournalProjector` for the derived execution journal, including events
delivered to the `SkillTemplate` observer callback. This exception does not
apply to canonical traces, model prompts, tool execution inputs, or the result
returned by `invoke()`, and does not guarantee comprehensive sensitivity
detection. Do not remove it on general-policy grounds or broaden it without an
explicit product decision.

## Public API and compatibility

Loomspan's supported application-facing Java API is deliberately small and closed. It consists only of the public top-level types in `ai.loomspan.api` that are allowlisted by `LoomspanPublicSurfaceArchitectureTest`.

- Treat changes to those allowlisted API types as compatibility-sensitive. Before changing or removing one, consider source, binary, and behavioral compatibility and add a compatibility shim when the project requires one.
- A Java `public` modifier does not by itself make a type supported API.
- Everything below `ai.loomspan.internal`, including `public` classes, interfaces, records, constructors, and methods, is implementation detail. It may change or disappear without a compatibility shim. Do not preserve an internal type solely for compatibility, and do not recommend internal types to library consumers.
- Types in `ai.loomspan.autoconfigure` are Spring Boot integration machinery and configuration binding types, not an application extension API. Their Java signatures do not require compatibility shims merely because Spring requires them to be public. User-visible configuration keys and documented configuration behavior are separate compatibility concerns.
- `RestSkillHandler` is Loomspan's sole supported Java SPI. Loomspan exposes no supported internal bean-replacement surface; do not create another SPI, bean override contract, or additional public API accidentally.
- New application-facing API must live in `ai.loomspan.api`, be deliberately added to the closed allowlist, be documented in the README, and have supported-surface tests. Prefer keeping a type internal unless consumers genuinely need it.
- Public API signatures must not expose types from `internal` or `autoconfigure` packages.

Run `LoomspanPublicSurfaceArchitectureTest` after changing production types. Its allowlists are the executable authority for the Java API classification; the README is the consumer-facing summary.
