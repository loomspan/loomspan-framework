# Observation Truncation Review

## Status

Proposed ticket.

## Outcome

Find every observation value or structure that Loomspan truncates or omits, evaluate it for removal, and make any retained limit explicit and justified.

## In scope

- Inventory truncation and omission in Java live observation and trace production, Go projections, browser APIs, MCP results, and web presentation.
- Classify permanent data loss separately from continuable paging or ranged reads, retention policy, and display-only clipping.
- Remove limits that hide execution admitted under framework execution limits.
- Update contracts, compatibility markers, fixtures, tests, and guidance for every changed behavior.

## Guardrails

- Do not treat a complete continuable representation as truncation merely because one response is bounded.
- Do not retain a truncation solely because it already exists; record the concrete safety or resource constraint for each retained limit.
- Preserve explicit completeness and limitation facts wherever a complete representation remains unavailable.

## Acceptance signals

- The repository has a complete inventory with a remove or retain decision for every observation truncation and omission.
- No observation surface silently hides admitted execution structure.
- Every retained limit has an explicit rationale and observable incompleteness signal.
- Java, Go, browser, MCP, and web tests agree on the resulting semantics.
