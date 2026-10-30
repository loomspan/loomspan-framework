# Optional Implementation-Specific Java Skill Configuration

## Status and scope

This feature remains wanted. It is deferred for discussion until the current [namespace design](skill-namespaces.md) is complete; it is not rejected and is not part of the namespace implementation scope.

The proposal below is preserved from the original handoff. Implementation-specific published configuration would be a new framework feature: existing Java skills are Spring-managed annotated methods, and the framework currently has no configurable Java declaration binding or configuration-injection contract. No implementation API, lifecycle, or packaging is selected here.

Work is limited to generic framework capabilities. Cloud and DataSkill examples are motivating context only. The accepted namespace design assigns Java methods through `@SkillMethod(namespace = "...")`, defaulting to `default`; this registration decision does not implement the configuration proposal below.

Destructive API changes are permitted without compatibility shims. The repository's closed supported API rules, documentation requirements, and supported-surface tests still apply. See the namespace document's identity findings and source links for the shared investigation context.

## Proposal for later discussion

### Direction and motivation

The owner supported this boundary: the framework knows how to invoke a JavaSkill without needing to parse or understand its data definition. The proposal generalizes this into optional configuration whose domain meaning belongs to the Java implementation.

DataSkill is the motivating example, not a new native framework skill language. It would be a JavaSkill implementation that interprets an app's declarative data definition. Other JavaSkills and hosting applications should be able to use the same generic configuration capability.

Research existing facilities first. The source discussion explicitly leaves open whether a framework change is necessary at all.

### DataSkill use case

The accepted V1 data-operation direction is named operations backed by explicit query definitions. Runtime arguments supply parameter values without changing the published query structure. Parsing queries from method names and accepting arbitrary runtime query definitions were not selected for V1.

The candidate definition describes tables, fields, data types, constraints, named operations, and descriptive guidance. Declarative business validation is intended to run deterministically in DataSkill, without an LLM call. A rule may supply one custom `message` containing both its failure explanation and correction guidance for the companion; separate message and hint fields were not selected.

These details explain the configuration's purpose. The framework should not interpret query operators, business rules, schema constraints, or validation messages. The exact schema/query language, helper set, transaction semantics, and database integration remain separate design work. Supabase is Cloud's intended initial backend, not a requirement for the framework configuration mechanism.

YAML was proposed for a consistent authoring experience. YAML storage, file naming, inline layout, and packaging are not finalized requirements.

### Proposed shape, not a selected contract

An assistant proposed an explicit configuration field in the JavaSkill declaration, rather than arbitrary unknown declaration fields:

```yaml
# Illustrative only; not checked against existing declaration syntax.
name: recipe-data
type: java
implementation: DataSkill
config:
  schema:
    # Implementation-owned data definition.
  operations:
    # Implementation-owned named operations.
```

The keys, implementation identifier, and Java binding API have not been selected. Referenced artifacts remain an alternative. Loading and preserving structured configuration for delivery to the implementation is a proposal; no particular Java representation or initialization/invocation signature is agreed.

The same Java implementation might serve multiple differently configured declarations, including declarations in different namespaces. Instance creation, configuration binding, mutable state, and isolation remain open.

### Configuration, call arguments, and trusted context

The source discussion recommends distinguishing:

| Information | Role |
|---|---|
| Implementation configuration | Published settings and definitions interpreted by the JavaSkill. |
| Invocation arguments | Per-call values, such as a named operation and its parameters. |
| Trusted runtime context | Host-established caller identity and authorized app/environment context. |

This is a recommended separation, not an adopted context API. Optional configuration alone does not establish access enforcement. Companion-authored definitions and arguments must not become authority to impersonate another caller or select an unauthorized app.

Cloud establishes the authenticated account, authorized app/environment, and membership for each application request. DataSkill enforces the selected sharing strategy on every operation, including related records. Cloud currently requires every application table definition to include `ownerId`, assigned from trusted account context. These are DataSkill/Cloud responsibilities, not framework schema rules.

Cloud-specific strategy JavaSkills were discussed as a possible implementation technique, but their cooperation with DataSkill and prevention of bypass are unresolved. The location and distribution of DataSkill, shared database code, and strategy implementations are also undecided. Initially building DataSkill in Cloud and later extracting a reusable library was an assistant suggestion, not an accepted packaging decision.

### Open research questions

- What configuration or extension information do current JavaSkill declarations already support?
- How are Java implementations registered and bound to declarations in the framework and sidecar?
- Should configuration be inline, referenced, or represented another way?
- What representation exposes configuration, and is it available at initialization, invocation, or both?
- Can the implementation validate its configuration, when would this happen, and how would author-facing failures be reported?
- How are updates handled when multiple declarations use one implementation with different configuration?
- How are instances and mutable state scoped to prevent one configured use from affecting another?
- How does the host supply trusted context separately from invocation arguments and published configuration?
- Which configuration is declaration-specific, namespace-specific, or host-level?

### Approaches not selected

- A framework-native DataSkill schema/query parser.
- A framework policy contract built around Cloud data-access strategies.
- Hardcoding Share Nothing, Owner Edit / Everyone Read, or Share Everything in framework core.

