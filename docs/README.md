# Loomspan documentation

Start with the [project overview](../README.md). These documents help application
developers evaluate Loomspan, choose an integration, and set up their environment.
They can be read directly or by an AI assistant.

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
Repository implementation, packaging, and release procedures do not belong in the
application getting-started path and are outside this documentation map.
