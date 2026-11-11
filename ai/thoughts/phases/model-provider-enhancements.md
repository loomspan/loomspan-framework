# Model Provider Enhancements

## Status and scope

Open investigations captured from the provider discussion on November 10, 2026.
These items request verification and agreement on intended behavior, not a finding
that current behavior is wrong. Prefer the simplest solution that meets demonstrated
requirements. The [Spring AI Responses migration roadmap](spring-ai-responses-migration.md)
records the subsequent direction to reuse Spring AI's Responses adapter for
OpenAI, Meta, and OpenRouter with stateless history replay. Prerelease dependencies
and destructive migration changes are acceptable during development; no legacy
Chat path or compatibility shims are wanted. Detailed reasoning-retention
semantics remain open; migration implementation has not started.

## Verify Anthropic thinking blocks

- [ ] Verify how the pinned Spring AI version and Loomspan preserve Anthropic
  thinking blocks, signatures, and opaque thinking data through a complete tool
  round trip, including mixed text/thinking/tool responses and multiple tool calls.
- [ ] Investigate the source-inspection concern: `AnthropicChatModel` represents
  thinking blocks as separate generations, while the default tool manager builds
  continuation history from the generation containing tool calls. Determine what
  is actually serialized on the next request before classifying this as a defect.
- [ ] Add focused protocol evidence for preservation, ordering, and replay. Match
  expectations to the selected model's API requirements and the exact dependency
  version; distinguish fixture verification from live verification.
- [ ] Record whether current behavior is intended and sufficient, an upstream
  limitation, or requires a targeted framework change.

Start with `SpringAiChatClientAssembler`, `CanonicalToolCallingManager`, and the
pinned Spring AI `AnthropicChatModel`, `ToolCallingAdvisor`, and
`DefaultToolCallingManager` sources.

## Audit conversation history across calls and retries

- [ ] Inventory every continuation, correction, and retry path: ordinary and
  parallel tool calls, recoverable tool failures, linter failures, output-schema
  corrections, evidence-contract corrections, provider transport/rate-limit retries,
  and any additional retry paths found during the audit. Include boundaries between
  planning, step execution, child skills, and separate model interactions.
- [ ] For each path, record the actual next request: baseline instructions,
  visible messages, assistant tool calls, tool results, correction feedback,
  assistant metadata, and provider-specific reasoning state. Identify what is
  retained, appended, rebuilt, or omitted and who owns that decision.
- [ ] Review and agree on intended conversation semantics for each boundary.
  A fresh attempt and a continuation can both be intentional; do not assume that
  preserving all history is always correct or that rebuilding a prompt is a bug.
- [ ] Verify call/result pairing, ordering, independent invocation isolation,
  behavior after partial failures, and whether any retry can repeat tool side
  effects. Distinguish execution traces from context actually sent to the model.
- [ ] Capture the agreed behavior in focused tests and documentation. Propose
  implementation changes only where observed behavior differs from that decision.

Initial inspection suggests tool loops retain conversation history, schema
corrections rebuild from a baseline plus candidate text and feedback, linter
corrections amend instructions, and provider retries resend the current request.
Treat these as starting observations to verify across the complete pipeline.

## Retention across corrections and completed interactions remains open

Required reasoning-state replay within the migrated Responses tool loop is agreed.
Evaluate that continuity separately from optional retention
across completed tasks or correction attempts. Provider reasoning items are not
interchangeable with visible thinking summaries or ordinary assistant text.
Respect each API's required replay contract before comparing optional policies.

If a retention-policy experiment is pursued, compare task correctness, recovery
from wrong assumptions, repeated tool work, latency, billed token usage, and
context growth on representative workloads. Do not assume that retaining more
reasoning improves results or that removing it is always safe. Preserve opaque
provider items unchanged whenever retained.

The [Meta compatibility spike](../../../docs/maintainers/meta-compatibility-spike.md)
contains the initial direct-provider probe. The Spring AI migration roadmap above
supersedes the initial custom-adapter and protocol-selector proposals; the spike
remains historical baseline evidence.
