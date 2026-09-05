# MCP contract verification

Loomspan Console exposes stateless Streamable HTTP at exact `/mcp`, with MCP
`2026-07-28` and compatible `2025-11-25` negotiation. It requires an exact
loopback Host and one bearer key. The installed surface contains thirteen
read-only tools and no resources or resource templates. Ordinary MCP
`tools/list` is the sole installed-surface inventory; runtime status contains
only target connection, authentication, compatibility, runtime identity, and
live-monitoring facts. Trace reads retain exact source-byte
offsets with a 1 KiB default and a 16 MiB (16,777,216-byte) shared per-call
maximum. Ordinary complete navigation results use a 32 KiB ceiling and default
range results use 48 KiB; pagination admits only complete items. Automated
MCP-over-HTTP tests cover exact UTF-8 and worst-case base64
framing at 1, 4, and 16 MiB, concurrent clients, and explicit rejection of the
32 and 64 MiB candidates.

Checked-in tests measure complete HTTP responses, including the JSON-RPC
response envelope, structured content, and deterministic text fallback. They
enforce a 25 KiB ceiling for `tools/list`, a 32 KiB ceiling for ordinary
complete navigation results, and a 48 KiB ceiling for default range results.
The tests retain and report exact current measurements so that size drift below
those ceilings remains visible during review; this document records the stable
limits and methodology rather than duplicating those change-sensitive values.

Compact schemas name stable decision and navigation fields while full
typed-result validation enforces every concrete Go result. Execution lists are
compact; exact detail returns every complete active branch and its nullable
assignment facts. Activity contains only a closed set of selected recorded
scalars plus continuity and coverage. The trace tools include a whole-item
authoritative plan query, assignment-aware detailed frames, descriptor-first
records with integrated literal search, exact selected content reads, and the
separate raw-NDJSON escape hatch.

## Dual-output boundary and active-result measurements

Every successful tool call carries one Loomspan-authored deterministic text
item and one SDK-populated `structuredContent` value. The supported protocol
revisions provide no client capability for choosing between those result
representations. The selected Go SDK also serializes typed structured output
into text when a handler leaves `content` unset, so removing the authored
fallback would not create a structured-only result.

Trace-tool success text is the minified JSON serialization of the same result
DTO, followed by one newline. Tests decode it and compare the complete value to
structured output so omitted nullable values, `false`, and zero remain distinct.

`dual_output_budget_test.go` records exact minified MCP `2025-11-25` JSON-RPC
baselines for the committed one-item execution list, execution detail, and
activity fixtures, plus a deterministic 64-item execution page and the largest
complete-item activity prefix admitted from a 64-item input page. It
also measures the text and structured wire contributions independently. The
maximum activity shape exercises the closed named-fact projection and verifies
that arbitrary upstream properties are absent. These
exact values are drift signals rather than new response ceilings: active and
activity pages remain count-bounded at 64 complete items, while trace
navigation and range reads retain their separate byte-admission budgets.

Console owns construction of both result representations and the HTTP response.
It does not own whether a downstream connector or client selects, suppresses,
displays, externalizes, or forwards either representation. Client presentation
thresholds and model-context inclusion therefore remain outside repository
conformance and release gates. The current server behavior deliberately stays
dual-output so structured results remain complete and text-only workflows keep
their deterministic, safety-reviewed evidence.

The release also carries the byte-identical, client-neutral
`skills/loomspan/` package. Installation is a user-selected
copy or filesystem link into a local client's user/global skill location; it
does not auto-install or contain an endpoint or key. The canonical skill is
unversioned during unreleased development and does not negotiate a version with
the MCP server. Live use requires the existing protected MCP configuration.
MCP clients discover the current tools rather than checking static capability
IDs; raw artifact inspection remains an optional workflow outside ordinary
semantic inspection. Automated tests keep protocol, target, authentication,
evidence, and target-scope failures distinct.

Repository-packaged skill, authoring guidance, operator documentation, and
deterministic agent evaluations are aligned with this current contract. The
structural audit rejects retired capability IDs, twelve-tool claims, and raw
plan reconstruction as an ordinary workflow; no compatibility alias or legacy
response remains.

Automated release evidence consists of pinned official conformance scenarios
for initialization, tool listing, caching, and DNS-rebinding protection across
both protocol revisions; SDK black-box discovery, strict schema rejection,
structured/text results, domain errors, opaque continuations, and trace-ID-only
calls against the real Loomspan surface; native credential/lifecycle tests on Windows x86_64, Linux
x86_64, macOS arm64, and macOS x86_64; and browser/API tests. Official
fixture-specific scenarios for diagnostic tools, prompts, resources, sampling,
and elicitation are intentionally inapplicable and must not be made to pass by
adding production capabilities.
