# Setup and integration choices

## Choose the runtime boundary

For **embedded Java**, use a Java 21+ application with the Loomspan Spring Boot
starter. This checkout targets Spring Boot 4.1 and Spring AI 2. Maven builds need
3.9+; the framework checkout includes a wrapper. Follow the
[Java quickstart](quickstart-java.md).

For **Sidecar**, use the separate
[Loomspan Sidecar repository](https://github.com/loomspan/loomspan-sidecar) and its
version-matched setup guidance. Applications call its HTTP API; an SDK is not a
prerequisite. Determine the exact Sidecar target first, then use its POM dependency
to select framework guidance. Framework and Sidecar version numbers are independent.
Do not assume an SDK or language-specific Agent Skill exists without checking its
owner's published sources.

## Configure model access

Model-backed YAML skills select a named model alias. The alias selects a named
connection and a provider model ID. Supported chat drivers are `openai`, `anthropic`,
`gemini`, and `ollama`. Supply credentials and endpoint configuration for the
chosen service; Loomspan does not inherit `spring.ai.*` settings.

Native TypeSafe Choice, Noul, and Score leaves use the separate `typesafe` driver
with direct TypeSafe or OpenRouter System One access. Use the
[TypeSafe reference](../agent-skills/loomspan-docs/references/skill-authoring/typesafe-skills.md)
for their declarations and connections; chat driver settings do not configure them.

The quickstart shows one chat connection. For additional providers, gateways, retry
policies, and timeouts, use the authoritative
[connection reference](../agent-skills/loomspan-docs/references/skill-authoring/model-selection-and-connections.md).
Compatibility with an OpenAI-style endpoint is feature-specific; do not assume
that tools, media, structured output, reasoning options, and usage reporting all
behave identically.

## Equip your development assistant

Start with the assessment prompt in the [README](../README.md). Once an integration
and exact runtime version are chosen, ask your assistant to follow
[loomspan-install](../agent-skills/loomspan-install/SKILL.md).

The installer selects the `loomspan` router, `loomspan-docs` references, and
`loomspan-console` guidance. Sidecar customers also need Sidecar authoring guidance;
SDK guidance, when available, follows the actual SDK dependency. The installer
checks exact sources and compatibility evidence before using the host's skill
manager. It defaults to supported project scope and reports missing host capabilities.
It does not change dependencies or runtime configuration.

Release `X` selects source tag `vX`. A snapshot uses main only if its root POM
matches the exact selected snapshot. A matching version does not guarantee all
newer skill folders exist in an older tag. Follow the installer's checks rather
than substituting latest guidance for a missing package.

After installation, requests can be as direct as:

```text
Use loomspan-docs to help me design this application's skill tree.
Use loomspan-docs to integrate invocation and error handling in my Java service.
```

Runtime investigation also requires a configured Console MCP connection. Follow
[Console setup](console-setup.md); installing its skill alone does not connect it.

For an assistant-led integration, use this request after choosing the runtime:

```text
Use the selected exact Loomspan runtime version and matching documentation.
Follow loomspan-install to install the complete guidance set in supported project
scope, reporting installed versions, source revisions and any host limitations.
Then follow the matching runtime's quickstart to implement and run one skill.
Report the observed invocation result separately from installation status.
For embedded Java runtime inspection, follow the servlet bridge and Console setup,
verify the installed MCP tools, and inspect the resulting execution. If local MCP
access is unavailable, explain the browser inspection steps and the evidence gap.
```
