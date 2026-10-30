# Loomspan Framework

Loomspan brings AI reasoning into applications through reusable **skills**. A skill
can reason with a language model, evaluate TypeSafe primitives, call a Java service, or delegate an operation
to an application-owned REST integration. Compose those capabilities into a
hierarchy so a model can break down a task while your application controls which
operations it can use.

For example, an invoice workflow could extract invoice details, look up existing
expenses through your application, and explain whether an invoice looks like a
duplicate.

## Why Loomspan?

- **Combine reasoning with application code.** Use model-backed YAML skills,
  TypeSafe Choice/Noul/Score evaluations, annotated Java methods, and REST leaves in the same skill tree.
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
setup steps; see [setup and integration choices](docs/setup.md). The
[adoption journey](docs/README.md#adoption-journey) gives your assistant the next
action and observable completion checks from assessment through runtime inspection.

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
String triage = skills.invoke("default-triageSupportRequest", Map.of("customerMessage", """
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
can use `output_from: {skill: default-finish}` with a required, unique `finish` task. It
still completes all accepted work, then returns that child's existing text
unchanged without a parent synthesis request. See [output contracts](agent-skills/loomspan-docs/references/skill-authoring/output-contracts.md)
for syntax, schema ownership, and model/Java/REST examples.

A model skill can declare top-level `output_bindings` to assemble exact values
from its validated input and successful accepted direct children into its object
output contract. Fully bound outputs skip final synthesis; mixed outputs ask the
model for unbound reasoning fields and validate the complete assembled object.
All accepted child work must succeed. `output_from` and `output_bindings` are
mutually exclusive. See [output assembly](agent-skills/loomspan-docs/references/skill-authoring/output-contracts.md#declared-output-assembly)
for complete schemas, source ownership and failure semantics.

A planning parent can declare `input_bindings` under each allowed child to copy
validated parent input or one accepted direct-child result into the child's input.
The model supplies only unbound arguments; Framework preserves selected values
and validates the unchanged complete receiving contract. When a closed object's
possible root fields are all wholly bound, or its explicit closed contract has no
fields, Framework constructs the accepted assigned call without a parent dispatch
model request. Optional unbound fields, open/unknown contracts, unsupported shapes
and nested binding destinations retain model dispatch. Planning and a model-backed
child's own reasoning still run; authorization and task/tool limits still apply.
See [declared child input bindings](agent-skills/loomspan-docs/references/skill-authoring/input-bindings.md)
for complete schemas, object pointers, dependencies and diagnostics.

## Skill identities and catalogs

Declarations keep local names such as `search`; every reference is qualified,
including `default-search`. Java `@SkillMethod(namespace = "Tax", name = "search", description = "Search")`
registers `tax-search`; the namespace defaults to `default`. Only namespace case
normalizes; local-name case remains exact. Components start with an ASCII letter
or underscore and contain ASCII letters, digits or underscores. Namespace length
is at most 32 and total identity length at most 64 including its single hyphen.
No shorthand, aliases, fallback search, trimming or character replacement is supported.

Inject `SkillReloader` and acquire `SkillCatalog catalog = reloader.snapshot("default")`.
There is no injected startup catalog or no-argument acquisition. `namespace()` and
`generationId()` identify the catalog's owner and opaque generation, even when empty.
Descriptors and validation feedback carry qualified names. Lookup rejects malformed
references and returns empty for valid unknown or foreign identities. Retained
catalogs are immutable, sorted, unfiltered metadata with no resource lease or
execution reservation. Pass `descriptor.name()` directly to invocation/validation
or handoff; the catalog does not authorize or pin that later admission.

A prepared update's ID and snapshot identify its candidate, not its base.
Validation allocates no generation and errors leave candidate metadata incomplete.
Changing a namespace name means creating another namespace, updating references
explicitly, then removing the old namespace; no rename or inheritance is provided.

This sequential implementation establishes identity/catalog foundations in PR 27.1.
Active registry/startup/management integration follows in PR 27.2; root admission,
model dispatch and observation consumers follow in PRs 27.3-27.5. Their integration
checks remain outstanding in this slice.

## Choose your integration

TypeSafe Choice, Noul, and Score can be registered as leaf skills and invoked
through the same `SkillTemplate` or composed as children of chat skills. The
`typesafe` driver supports direct TypeSafe and OpenRouter System One accounts.
Leaves validate the resolved request and return the entire named `answers`
object as JSON text. Their generated output schema is descriptive metadata;
answers are preserved without validation or repair. See the [complete TypeSafe
setup, route switching, fixed/dynamic mixed batches, direct invocation, selected
answer bindings and forwarding parents](agent-skills/loomspan-docs/references/skill-authoring/typesafe-skills.md).
Model IDs are sent verbatim and credentials belong to connections. Existing
Console catalog/details and MCP expose `TYPESAFE` source identity, original YAML,
source locations and generated schemas; native request/response/failure content
uses the existing JSON/text trace views. Capture availability and distribution
confidence do not establish judgment correctness or live parity between routes.

- **Embedded Java:** add the Spring Boot starter and invoke skills in your
  application. This revision targets Java 21+, Spring Boot 4.1, and Spring AI 2;
  building with Maven requires 3.9+.
- **[Sidecar](https://github.com/loomspan/loomspan-sidecar):** use the separate
  Loomspan HTTP service from applications in other languages or when you want a
  separate runtime. Its repository covers setup, versions, and integration guidance.

Model-backed skills need a named connection using the OpenAI, Anthropic, Gemini,
or Ollama driver. Native TypeSafe leaves require the `typesafe` driver. Java and REST leaves can execute without a framework model call.

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
outputSchema)`; `SkillKind` includes `YAML`, `JAVA`, `REST`, and `TYPESAFE`.
Nullable `outputSchema` describes effective output shape, including
forwarding chains and native descriptive schemas, without adding parent,
TypeSafe, or Java/REST output validation. This
revision uses only the five-argument constructor. `RestSkillHandler` is the sole supported Java SPI. Types under `internal` and
`autoconfigure` are not application extension APIs; Loomspan does not expose an
internal bean-replacement contract.
