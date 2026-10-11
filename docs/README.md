# Loomspan documentation

Start with the [project overview](../README.md). Loomspan documentation is AI first:
an assistant uses this map to select version-matched instructions, perform a task,
and verify its result with the developer. The same Markdown remains readable and
usable directly by humans.

## Adoption journey

Read the overview and architecture for assessment, then load only the instructions
needed for the selected task. Establish the runtime version before relying on
version-sensitive contracts. Main describes the current development revision;
for a release, use its exact tag. Record the source commit for snapshot evaluations.

| Stage | Assistant's next action | Evidence of completion |
| --- | --- | --- |
| Evaluate | Read [architecture](architecture.md) and [setup](setup.md); relate the tradeoffs to the application's task and runtime boundary. | An integration choice and stated assumptions. |
| Select and install guidance | Follow [loomspan-install](../agent-skills/loomspan-install/SKILL.md) for the chosen exact runtime. | Installed names, component versions, source revisions and scope, or a specific host/source limitation. |
| Run a first skill | Follow the [Java quickstart](quickstart-java.md), or the exact Sidecar revision's setup guidance. | A successful invocation and observed output; compilation alone is insufficient. |
| Inspect execution | For embedded Java, use the quickstart's [servlet bridge](quickstart-java.md#continue-with-console), then [Console setup](console-setup.md). | Console connects to the selected target; the assistant discovers its MCP tools and retrieves execution evidence. |
| Develop the application | Route through [loomspan](../agent-skills/loomspan/SKILL.md), loading the relevant authoring/API references or Console playbook. | Application behavior is checked against its contracts and runtime evidence. |

Guidance installation, runtime invocation, and MCP connectivity are separate
results. Report each as completed, unavailable, or unverified from actual evidence.
An assistant host without skill installation or local MCP access can still consult
matching Markdown; it must report which execution or inspection steps it cannot
perform. The developer can complete those steps using the same documents.

| Decision or setup task | Document |
| --- | --- |
| Understand the design, responsibilities, and tradeoffs | [Architecture](architecture.md) |
| Choose embedded Java or Sidecar and install assistant guidance | [Setup](setup.md) |
| Run a complete embedded Java example | [Java quickstart](quickstart-java.md) |
| Enable observability and connect Console MCP | [Console setup](console-setup.md) |
| Plan a version change | [Upgrades](upgrades.md) |

## Ongoing application development

The versioned Agent Skills carry workflows and focused references for ongoing
work. They are readable Markdown; an installed AI assistant is not required to
consult them.

- [loomspan](../agent-skills/loomspan/SKILL.md) routes a task to the relevant specialist.
- [loomspan-docs](../agent-skills/loomspan-docs/SKILL.md) loads the
  [skill-authoring](../agent-skills/loomspan-docs/references/skill-authoring/README.md)
  or [Java API](../agent-skills/loomspan-docs/references/java-api/README.md) knowledge set.
- [loomspan-console](../loomspan-console/agent-skills/loomspan-console/SKILL.md)
  guides runtime investigation through Console MCP.
- [loomspan-install](../agent-skills/loomspan-install/SKILL.md) performs version-aware
  guidance installation through the assistant host's supported mechanisms.

Use documentation from the revision matching your runtime. Main describes the
current development revision, not every published version.

## Documentation placement

Docs explain the system and the choices around it. Skills guide ongoing work and
carry the task-specific knowledge required to perform it. Installation and upgrade
workflows may use skills while their background and decisions live here. Each
contract should have one authoritative home; link to it rather than copy it.

Application development and framework development are different audiences.
Repository implementation, packaging, and release procedures have a separate
[maintainer entrypoint](maintainers/README.md). Use that entrypoint for framework
changes; application Agent Skills do not carry the contributor workflow.
