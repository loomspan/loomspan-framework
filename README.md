# Loomspan Framework

Loomspan brings AI reasoning into applications through reusable **skills**. A skill
can reason with a language model, call a Java service, or delegate an operation
to an application-owned REST integration. Compose those capabilities into a
hierarchy so a model can break down a task while your application controls which
operations it can use.

For example, an invoice workflow could extract invoice details, look up existing
expenses through your application, and explain whether an invoice looks like a
duplicate.

## Why Loomspan?

- **Combine reasoning with application code.** Use model-backed YAML skills,
  annotated Java methods, and REST leaves in the same skill tree.
- **Let the model plan within explicit boundaries.** Declare allowed child
  skills, validate inputs and structured outputs, and apply authorization policies.
- **Bound execution.** Configure mission deadlines, nesting limits, and usage
  quotas, and choose which models each reasoning skill uses.
- **Understand what happened.** Inspect executions, model interactions, failures,
  and usage through Loomspan Console and its MCP tools.

Loomspan uses a Hierarchical Task Network (HTN) approach with model-driven
planning. See [architecture and tradeoffs](docs/architecture.md) for when that
approach fits and what it does not guarantee.

## Work with your AI assistant

Loomspan is developed through AI with human guidance and is designed for the same
collaboration in your application: your assistant uses version-matched Loomspan
Agent Skills to help integrate and author capabilities, and Console MCP tools to
investigate runtime behavior. You can also read and use the documentation directly.

There are two kinds of “skill” here: **application skills** execute inside
Loomspan; **Agent Skills** teach your development assistant how to work with it.

Start by pasting this into your assistant:

```text
Read https://github.com/loomspan/loomspan-framework/blob/main/README.md
and follow its architecture and setup documentation links. Explain what
Loomspan would add to my application, the tradeoffs, and whether embedded
Java or Sidecar fits my needs. Review the linked Loomspan Agent Skill
installation guidance and explain how to obtain guidance matching my
chosen runtime version. Distinguish documented capabilities from assumptions.
Start with an assessment before making changes to my project.
```

When you are ready to integrate, ask your assistant to follow
[loomspan-install](agent-skills/loomspan-install/SKILL.md). It selects guidance for
your runtime version; reading the current README does not establish compatibility
with an older release. Installing guidance and connecting Console MCP are separate
setup steps; see [setup and integration choices](docs/setup.md).

## From customer report to engineering triage

Turn an unstructured support request into an assessment of impact, possible causes,
and concrete investigation steps. Start with one model-backed YAML skill:

```yaml
name: triageSupportRequest
description: Turn a customer issue into an actionable engineering triage brief.
model: assistant
prompt: >
  Assess customer impact and urgency, distinguish reported facts from hypotheses,
  and propose the next investigation steps. Identify missing information.
  Do not claim to have checked systems or taken action.
input_schema:
  type: object
  properties:
    customerMessage: { type: string }
  required: [customerMessage]
  additionalProperties: false
```

In an embedded Java application, invoke it through an injected `SkillTemplate`:

```java
String triage = skills.invoke("triageSupportRequest", Map.of("customerMessage", """
        Since this morning's deployment, checkout times out after payment.
        Three customers say they were charged but received no order confirmation.
        Retrying sometimes creates two orders. Browsing and cart updates still work.
        """));
```

This illustrates the declaration and call; the `assistant` model alias still
needs configuration. The [complete Java quickstart](docs/quickstart-java.md)
provides the dependency, configuration, file locations, and runnable application.
The brief gives an engineer a starting point: reported payment/order mismatches,
a possible connection to the deployment, and checks needed to establish the cause.
To investigate beyond the supplied message, expose order lookups or deployment
history as Java or REST skills and let a planning parent call those allowed
capabilities. See the [skill-tree guide](agent-skills/loomspan-docs/references/skill-authoring/mental-model.md).

When one direct child supplies the complete answer, an explicit model planner
can use `output_from: {skill: finish}` with a required, unique `finish` task. It
still completes all accepted work, then returns that child's existing text
unchanged without a parent synthesis request. See [output contracts](agent-skills/loomspan-docs/references/skill-authoring/output-contracts.md)
for syntax, schema ownership, and model/Java/REST examples.

A planning parent can declare `input_bindings` under each allowed child to copy
validated parent input or one accepted direct-child result into the child's input.
The model supplies only unbound arguments; Framework preserves selected values
and validates the unchanged complete receiving contract. See [declared child input bindings](agent-skills/loomspan-docs/references/skill-authoring/input-bindings.md)
for complete schemas, object pointers, dependencies and diagnostics.

## Choose your integration

- **Embedded Java:** add the Spring Boot starter and invoke skills in your
  application. This revision targets Java 21+, Spring Boot 4.1, and Spring AI 2;
  building with Maven requires 3.9+.
- **[Sidecar](https://github.com/loomspan/loomspan-sidecar):** use the separate
  Loomspan HTTP service from applications in other languages or when you want a
  separate runtime. Its repository covers setup, versions, and integration guidance.

Model-backed skills need a named connection using the OpenAI, Anthropic, Gemini,
or Ollama driver. Java and REST leaves can execute without a framework model call.

Loomspan is **in beta**. Supported Java APIs and documented configuration can
change between betas, including breaking changes. Pin an exact version and review
[upgrade guidance](docs/upgrades.md). The first production release will be `1.0.0`.

## Explore further

| Goal | Start here |
| --- | --- |
| Understand the architecture and adoption tradeoffs | [Architecture](docs/architecture.md) |
| Set up a runtime and your assistant | [Setup](docs/setup.md) |
| Run your first embedded skill | [Java quickstart](docs/quickstart-java.md) |
| Connect Console for AI-assisted debugging | [Console setup](docs/console-setup.md) |
| Design, develop, and troubleshoot application skills | [Skill-authoring knowledge set](agent-skills/loomspan-docs/references/skill-authoring/README.md) |
| Integrate invocation, validation, and runtime updates | [Java API knowledge set](agent-skills/loomspan-docs/references/java-api/README.md) |
| Find docs or understand their scope | [Documentation map](docs/README.md) |

The supported Java API is the closed allowlist in `ai.loomspan.api`, summarized
in the [public API reference](agent-skills/loomspan-docs/references/java-api/compatibility-and-boundaries.md).
The catalog descriptor is `SkillDescriptor(name, description, kind, inputSchema,
outputSchema)`; nullable `outputSchema` describes effective output shape, including
forwarding chains, without adding parent or Java/REST output validation. This
revision uses only the five-argument constructor. `RestSkillHandler` is the sole supported Java SPI. Types under `internal` and
`autoconfigure` are not application extension APIs; Loomspan does not expose an
internal bean-replacement contract.
