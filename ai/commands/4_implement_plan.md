---
description: Implement a completed plan or a ticket-led fast/direct change with verification
---

# Implement Plan

You are tasked with implementing either a completed technical plan from
`ai/thoughts/plans/` or, when explicitly selected by `0_run_pipeline.md`, a
ticket-led fast-track or direct change.

## Shared Protocols

- Follow `ai/commands/shared/automation-protocol.md` for execution modes, the escalation contract, and the Step Report you must end with.
- Follow `ai/commands/shared/loomspan-docs-protocol.md` for the evidence hierarchy, `loomspan-docs` skill usage, and drift classification. Use the governing-artifact mismatch process below when a drift classification materially affects implementation scope or semantics.

## Getting Started

Before editing in either plan-led or ticket-led execution:

- Read repository instructions and inspect Git status and the relevant diff,
  including staged and untracked changes. Existing changes belong to the
  developer unless clearly identified as this ticket's work.
- Preserve unrelated changes, including overlapping hunks. If ownership cannot
  be established safely, ask a focused question or escalate in pipeline mode.
  Keep unrelated cleanup out of scope and never use destructive Git commands
  to recreate a clean state.
- For framework work, read the canonical policy in
  `ai/thoughts/framework-feature-design-lens.md`. When no plan supplies the
  assessment, directly classify affected surfaces and assess compatibility
  and Java-to-Go boundary-coordination constraints. Follow the repository's
  closed API allowlist; a generic SPI category does not establish a Loomspan SPI.

When given a plan path:
- Read the plan completely and check for any existing checkmarks (- [x])
- Read the original ticket and all files mentioned in the plan
- If a testing plan exists for this work (for example, `ai/thoughts/plans/*-testing.md`), read it completely and follow it during implementation
- For Loomspan framework work, read and follow the plan's `Contract and Compatibility Impact` section before editing code
- When the plan affects the Loomspan Console application-adapter REST/SSE, acquisition, problem, or consumed NDJSON boundary, treat the plan's Java-to-Go boundary-coordination scope as an implementation constraint
- Read the plan's `Skill-Authoring Documentation Impact` section. If it is missing (for example, in an older plan), perform the assessment described below before implementation
- When skill-authoring impact is `Affected`, first trace and test the affected executable behavior, then follow `ai/commands/shared/loomspan-docs-protocol.md` before editing the knowledge base
- **Read files fully when practical** - avoid partial reads unless a file is very large; if you must read in chunks, capture sufficient surrounding context
- Think deeply about how the pieces fit together
- Create a todo list to track your progress
- Start implementing if you understand what needs to be done

When the orchestrator explicitly invokes **fast-track** or **direct** pipeline
execution without plan artifacts:

- Read the ticket, repository instructions, working-tree state, and directly
  relevant files completely.
- Perform bounded, targeted reconnaissance sufficient to validate the ticket's
  assumptions and locate the implementation and tests. Do not create research,
  implementation-plan, or testing-plan artifacts merely to recreate skipped
  stages.
- Create an internal working checklist mapping every acceptance criterion to
  implementation and proportionate verification.
- Apply the shared profile eligibility and reassessment rules as evidence
  develops. A mismatch returns `STATUS: needs-developer` with the proposed
  upgrade; the orchestrator changes the route before affected work resumes.
- Preserve material context in the ticket's `Execution notes` under the shared
  handoff rules; the internal checklist and Step Report do not replace that
  durable handoff. Under the direct profile, state clearly that no independent
  review is part of the selected route.

If neither a plan nor an explicit ticket-led profile is provided, ask for a
plan. Do not infer fast-track or direct mode yourself.

## Implementation Philosophy

Plans and ticket-led checklists guide the implementation, but reality can be
messy. Your job is to:
- Follow the plan or ticket's intent while adapting to what you find
- Implement each phase fully before moving to the next
- Verify your work makes sense in the broader codebase context
- Update plan checkboxes when a plan exists; otherwise track the internal
  ticket-led checklist without creating a substitute artifact

For framework work, follow the governing plan or ticket decisions about which affected surfaces are deliberately supported and which are internal, including Application API, Supported SPI, configuration and manifest behavior, persisted or serialized behavior, and ephemeral diagnostics. A public modifier, interface, constructor, Spring bean, `@ConditionalOnMissingBean`, existing test, or previous implementation does not by itself establish supported behavior. Do not add unplanned overloads, aliases, fallbacks, adapters, deprecated paths, legacy readers, duplicate interfaces, compatibility constructors, bridge types, or dual behavior.

When an intentional break is clearly required by the ticket or `Pipeline notes`, remove obsolete paths completely and atomically update every in-repository caller, test, sample, fixture, configuration, manifest, and documentation reference identified by the plan or targeted reconnaissance. If implementation discovers documented behavior whose compatibility treatment the governing artifact did not settle, a verified protected consumer, or a need for compatibility machinery, treat that as a mismatch and use the process below instead of silently adding a shim.

When things do not match the governing plan or ticket exactly, think about why and communicate clearly. The governing artifact is your guide, but your judgment matters too. Ordinary adaptation (a renamed helper, a moved file, an extra call site its intent obviously covers) is normal implementation work. Persist material decisions in the governing artifact and echo them in `DECISIONS`; routine adaptations are not mismatches.

If you encounter a genuine mismatch — the governing plan or ticket cannot be followed as written, its assumptions are wrong, scope must change, or a compatibility decision it made no longer holds:
- STOP affected work and determine why the governing artifact cannot be followed
- Propose a minimal governing-artifact update + rationale, stating clearly: what it says (Expected), the actual situation (Found), and why it matters
- Standalone: present it to the developer and get approval before proceeding
- Pipeline: set `STATUS: needs-developer` with your proposed governing-artifact update as the `RECOMMENDATION`; never adapt around a genuine mismatch silently. Profile mismatches follow the shared upgrade rule instead.

Read both the ticket requirements and its optional `Pipeline notes` before implementation. An intentional exception clearly authorized by either may proceed, but impact broader or materially different from that authorization is a mismatch and requires the developer. If neither clearly authorizes an intentional-looking compatibility break, do not assume it is authorized.

## Keep the Skill-Authoring Knowledge Base Current

The plan's documentation assessment, when present, is a starting point rather than a permanent conclusion. In ticket-led mode, perform the assessment directly. Reassess either conclusion against the behavior actually implemented and the final diff.

Apply the impact definition and knowledge-base update rules in `ai/commands/shared/loomspan-docs-protocol.md`. When impact is present, update the relevant knowledge-base documents in the same phase as the behavior change; do not defer them to an unspecified follow-up.

If actual implementation reveals skill-authoring impact that a completed plan marked `No impact`, or materially changes planned documentation scope, treat that as a mismatch. Propose the minimal governing-artifact update and rationale using the mismatch process above before proceeding.

If implementation reveals Java-to-Go boundary work beyond the coordination scope defined by the governing ticket or plan, treat that as a mismatch and apply the profile reassessment rule when running a light route. Do not ship a boundary change without its required Java or Go counterpart, protocol fixtures, semantic tests, rejection tests, and documentation.

## Verification Approach

After implementing a phase:
- Run the success criteria checks (usually `mvn test` covers everything)
- Attribute failures before fixing them. Fix ticket-caused failures; preserve
  unrelated developer changes and report pre-existing or environment failures
  with evidence. If a narrower safe check cannot establish required criteria,
  return `needs-developer` for a material decision or `failed` for an
  unrecoverable execution failure rather than claiming completion.
- Review the phase's actual changes for author-facing semantic impact and confirm the plan's documentation assessment, when present, is still correct
- When documentation changed, verify its claims against the cited tests, fixtures, samples, or production source; apply the README's LLM-first acceptance questions; and update routing and coverage when required
- Update your progress in the plan, when present, and in your todos
- Check off completed plan items only when a plan exists
- For framework work, verify the phase introduced no accidental public types, leaked internal types in public Application API or Supported SPI signatures, or new or retained `@ConditionalOnMissingBean` beans not deliberately established as Supported SPI

Before completion, review the full ticket-scoped diff and map every acceptance
criterion to sufficient verification. Do not report complete with unresolved
ticket-caused failures or required checks lacking sufficient evidence.

End with the Step Report: list exact verification commands and actual results,
label unrun checks NOT RUN, and carry optional developer checks from the
governing ticket or plan into `OPTIONAL_DEVELOPER_CHECKS`. Persist newly found
observations in that artifact before reporting them; ticket-led work uses
`Execution notes`. Optional observations do not replace required verification.
For full and fast-track, the fresh Step 5 review establishes final completion.
Direct has no independent review; state that reduced assurance explicitly.

## If You Get Stuck

When something isn't working as expected:
- First, make sure you've read and understood all the relevant code
- Consider whether the codebase has evolved since the governing artifact was written
- Present the mismatch clearly and ask for guidance (standalone) or escalate it (pipeline)

Use sub-agents or deep research steps sparingly - mainly for targeted debugging or exploring unfamiliar territory.

## Resuming Work

If the plan has existing checkmarks:
- Trust that completed work is done
- Pick up from the first unchecked item
- Verify previous work only if something seems off
- Still reassess skill-authoring documentation impact for the final combined diff before declaring the plan complete

Before final completion of framework work, repeat the public-surface and Spring-extension-point checks across the full diff, and confirm intentionally obsolete paths were removed rather than retained behind compatibility machinery when that removal is authorized by the ticket or `Pipeline notes`.

Remember: You're implementing a solution, not just checking boxes. Keep the end goal in mind and maintain forward momentum.
