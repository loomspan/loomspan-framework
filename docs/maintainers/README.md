# Framework maintenance

This is the entrypoint for assistants and humans changing the Loomspan repository.
Read the root [AGENTS.md](../../AGENTS.md) and any guidance in the affected subtree
before editing. Application integration is routed through the
[application documentation map](../README.md); the application Agent Skills do not
define the contributor workflow.

## Select the work

| Task | Instructions and executable authority | Verification |
| --- | --- | --- |
| Java implementation or supported API | Root guidance, `src/`, and root `pom.xml`. Only the architecture test's allowlisted `ai.loomspan.api` types are supported application API; `RestSkillHandler` is the sole supported Java SPI. | Run focused tests and `LoomspanPublicSurfaceArchitectureTest` after production-type changes. Run supported-surface tests for API changes. |
| Console implementation or packaging | [Console guidance](../../loomspan-console/AGENTS.md) and [build instructions](../../loomspan-console/README.md). Console is an independent Go module with an embedded React application. | Use the affected subtree's checks; Java `mvn test` does not build or verify Console. |
| Documentation or Agent Skill references | [Documentation placement](../README.md#documentation-placement), matching source and named tests. Keep one authoritative contract and link to it. | Check local links, heading targets, examples, version consistency and any affected skill validation. |
| Versioning and publication | [Release procedure](releases.md), the version script, and the two release workflows. | Verify the exact release commit and both publication outcomes. |

Documentation is AI first and human readable. Give assistants task entrypoints,
exact prerequisites, source/version boundaries and observable completion criteria.
Load only relevant references. Distinguish verified behavior from a recommendation
or an unresolved claim, and preserve diagnostic fidelity under repository policy.

## Implementation investigations

For shutdown, begin with the application contract in
[execution limits](../../agent-skills/loomspan-docs/references/skill-authoring/execution-limits.md)
and follow its current implementation and test anchors. Internal lifecycle ordering
belongs in source and focused tests. A claim about Sidecar packaging must be
verified in the matching Sidecar source and tests independently.

