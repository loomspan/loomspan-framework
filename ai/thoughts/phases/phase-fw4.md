# Phase FW4 — Documentation, agent-skills, and release readiness

Part of [beta 4 roadmap](beta4-rest-skills-and-sidecar-roadmap.md). Depends
on FW1–FW3. Establishes framework release readiness; tagging waits until
Sidecar integration against the local snapshot is verified. Re-run affected
checks after integration-driven framework changes and complete release
validation on the final commit. Source-grounded at `1e4eb455`; see
[documentation/release evidence](../beta4-code-grounding.md#fw4--concrete-documentation-and-release-anchors-exist).

## Goal

Every beta 4 framework feature is documented for humans and for coding
agents, proven through the public surface only, and the release process runs
clean.

## In scope

- README: REST skills section (manifest contract, leaf rules, handler
  requirement, security posture: handler owns URLs/credentials, model sees
  inputs permitted by the schema), SPI section (`RestSkillHandler`,
  `RestSkillInvocation`, exactly-one-bean rule, `SecurityContext` contract,
  failure semantics), catalog view and `validate` sections, updated
  supported-surface list with the named additions from FW1 and FW2.
- Document the FW2 observer contract for successful and failed executions,
  including no callback for rejection before session creation and preserving
  the execution exception when a failure observer throws.
- Document `loomspan.shutdown.timeout: 30s`, framework-owned shutdown bounds,
  and how ordinary mission timeouts continue during shutdown. Sidecar owns
  immediate admission/dispatch stop, with no separate drain timer.
  Explain independent, prompt shutdown-event listeners and the later
  framework-owned lifecycle wait, keeping needed resources alive until
  completion/cutoff without shared listener priorities.
- `agent-skills/loomspan-docs`: skill-authoring reference for REST skills;
  java-api references for the SPI, catalog, and `validate`; routing updates
  in the knowledge-set README. `metadata.loomspan-version` remains coupled
  through `scripts/loomspan_version.py`.
- Extend the existing `SupportedSurfaceIntegrationTest` fixture: local
  OpenAI-compatible stub → YAML planner → REST leaf via a test handler → also
  a Java leaf; catalog and `validate` asserted through public API only;
  observing only `SkillExecutionView`.
  FW1/FW2 add their feature assertions to this fixture as they land; FW4
  verifies the combined coverage rather than building another harness for
  the same scenario. Sidecar integration remains separate because it tests
  actual HTTP/JWT/application wiring absent from this framework fixture.
- Release notes / beta compatibility note distinguishing additive public
  types from intentional source, binary, or behavioral breaks, including
  the `SkillTemplate` method addition and any Console protocol change.
- Correct README's example release tag commands: they currently use the
  development SNAPSHOT string, while the publish workflow rejects SNAPSHOT
  release tags. Use the actual release version in release examples.
- Release dry run per README: version check, script unit tests, `clean
  verify`, release-profile verify; manual Console Release and Maven Central
  Release validation runs.

## Out of scope

- Sidecar documentation (Sidecar repo).

## Acceptance criteria

- [ ] README documents every new manifest field, API type, method, and
  behavior with no reference to internal types.
- [ ] `loomspan-docs` references exist for REST skill authoring and the new
  Java API, and the bootstrap/version coupling still passes
  `loomspan_version.py check`.
- [ ] Supported-surface test exercises REST leaf, catalog, and `validate`
  through `ai.loomspan.api` only.
- [ ] Public-surface coverage proves observer history is delivered on
  execution failure without masking the execution exception.
- [ ] Release dry-run commands pass locally; manual GitHub validation
  workflows pass on the release commit.

## Ticket boundary

The framework documentation/readiness unit in the
[planning handoff](../beta4-ticket-readiness.md) owns complete guidance and
cumulative coverage. README/API proof required by FW1/FW2 lands with those
features. Prepare release checks here, then perform final release-commit
verification after Sidecar snapshot integration and before framework tagging.

## Grounded source anchors

- `SupportedSurfaceIntegrationTest` and its local OpenAI-compatible stub.
- `agent-skills/loomspan-docs/references/` structure and how knowledge sets
  are routed.
- `loomspan_version.py` checks root/module POM and loomspan/loomspan-docs
  skill versions; its set command updates tracked version occurrences.
