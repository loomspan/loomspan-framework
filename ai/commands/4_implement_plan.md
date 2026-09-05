---
description: Implement technical plans from ai/thoughts/plans with verification
---

# Implement Plan

You are tasked with implementing a completed technical plan from `ai/thoughts/plans/`. These plans contain phases with specific changes and success criteria.

## Shared Protocols

- Follow `ai/commands/shared/automation-protocol.md` for execution modes, the escalation contract, and the Step Report you must end with.
- Follow `ai/commands/shared/loomspan-docs-protocol.md` for the evidence hierarchy, `loomspan-docs` skill usage, and drift classification. Use the plan-mismatch process below when a drift classification materially affects implementation scope or semantics.

## Getting Started

When given a plan path:
- Read the plan completely and check for any existing checkmarks (- [x])
- Read the original ticket and all files mentioned in the plan
- If a testing plan exists for this work (for example, `ai/thoughts/plans/*-testing.md`), read it completely and follow it during implementation
- For Loomspan framework work, read and follow the plan's `Contract and Compatibility Impact` section and the canonical policy in `ai/thoughts/framework-feature-design-lens.md` before editing code
- When the plan affects the Loomspan Console application-adapter REST/SSE, acquisition, problem, or consumed NDJSON boundary, treat the plan's Java-to-Go boundary-coordination scope as an implementation constraint
- Read the plan's `Skill-Authoring Documentation Impact` section. If it is missing (for example, in an older plan), perform the assessment described below before implementation
- When skill-authoring impact is `Affected`, first trace and test the affected executable behavior, then follow `ai/commands/shared/loomspan-docs-protocol.md` before editing the knowledge base
- **Read files fully when practical** - avoid partial reads unless a file is very large; if you must read in chunks, capture sufficient surrounding context
- Think deeply about how the pieces fit together
- Create a todo list to track your progress
- Start implementing if you understand what needs to be done

If no plan path provided, ask for one.

## Implementation Philosophy

Plans are carefully designed, but reality can be messy. Your job is to:
- Follow the plan's intent while adapting to what you find
- Implement each phase fully before moving to the next
- Verify your work makes sense in the broader codebase context
- Update checkboxes in the plan as you complete sections

For framework work, follow the plan's decisions about which affected surfaces are deliberately supported and which are internal, including Application API, Supported SPI, configuration and manifest behavior, persisted or serialized behavior, and ephemeral diagnostics. A public modifier, interface, constructor, Spring bean, `@ConditionalOnMissingBean`, existing test, or previous implementation does not by itself establish supported behavior. Do not add unplanned overloads, aliases, fallbacks, adapters, deprecated paths, legacy readers, duplicate interfaces, compatibility constructors, bridge types, or dual behavior.

When an intentional break is clearly required by the ticket or `Pipeline notes`, remove obsolete paths completely and atomically update every in-repository caller, test, sample, fixture, configuration, manifest, and documentation reference identified by the plan. If implementation discovers documented behavior whose compatibility treatment the plan did not settle, a verified protected consumer, or a need for compatibility machinery, treat that as a plan mismatch and use the mismatch process below instead of silently adding a shim.

When things don't match the plan exactly, think about why and communicate clearly. The plan is your guide, but your judgment matters too. Ordinary adaptation (a renamed helper, a moved file, an extra call site the plan's intent obviously covers) is normal implementation work — record it in `DECISIONS`, not as a mismatch.

If you encounter a genuine mismatch — the plan cannot be followed as written, its assumptions are wrong, scope must change, or a compatibility decision it made no longer holds:
- STOP and think deeply about why the plan can't be followed
- Propose a minimal plan update + rationale, stating clearly: what the plan says (Expected), the actual situation (Found), and why it matters
- Standalone: present it to the developer and get approval before proceeding
- Pipeline: set `STATUS: needs-developer` with your proposed plan update as the `RECOMMENDATION`; never adapt around a genuine mismatch silently

Read both the ticket requirements and its optional `Pipeline notes` before implementation. An intentional exception clearly authorized by either may proceed, but impact broader or materially different from that authorization is a mismatch and requires the developer. If neither clearly authorizes an intentional-looking compatibility break, do not assume it is authorized.

## Keep the Skill-Authoring Knowledge Base Current

The plan's documentation assessment is a starting point, not a permanent conclusion. Reassess it against the behavior actually implemented and the final diff.

Apply the impact definition and knowledge-base update rules in `ai/commands/shared/loomspan-docs-protocol.md`. When impact is present, update the relevant knowledge-base documents in the same phase as the behavior change; do not defer them to an unspecified follow-up.

If actual implementation reveals skill-authoring impact that the completed plan marked `No impact`, or materially changes the planned documentation scope, treat that as a plan mismatch. Propose the minimal plan update and rationale using the mismatch process above before proceeding.

If implementation reveals Java-to-Go boundary work beyond the coordination scope defined by the plan, treat that as a plan mismatch. Do not ship a boundary change without its required Java or Go counterpart, protocol fixtures, semantic tests, rejection tests, and documentation.

## Verification Approach

After implementing a phase:
- Run the success criteria checks (usually `mvn test` covers everything)
- Fix any issues before proceeding
- Review the phase's actual changes for author-facing semantic impact and confirm the plan's documentation assessment is still correct
- When documentation changed, verify its claims against the cited tests, fixtures, samples, or production source; apply the README's LLM-first acceptance questions; and update routing and coverage when required
- Update your progress in both the plan and your todos
- Check off completed items in the plan file itself by updating the checkboxes in the plan
- For framework work, verify the phase introduced no accidental public types, leaked internal types in public Application API or Supported SPI signatures, or new or retained `@ConditionalOnMissingBean` beans not deliberately established as Supported SPI

When all phases are complete, end with the Step Report: list the exact verification commands run with their results in `VERIFICATION` and copy any optional developer checks from the plan into `OPTIONAL_DEVELOPER_CHECKS`. If implementation reveals a useful non-automatable observation that the plan missed, add it to the plan before reporting it so the fresh reviewer receives it through the artifact handoff. These checks do not block completion. Your executable verification results are working feedback — the fresh-context review (`5_code_review.md`) is the ground truth for "done".

## If You Get Stuck

When something isn't working as expected:
- First, make sure you've read and understood all the relevant code
- Consider if the codebase has evolved since the plan was written
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
