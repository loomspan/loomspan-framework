# Configured Java Skill Executors

## Status and scope

This feature is in design discussion. The agreed direction below records the discussion through 2026-10-31; it is not implemented and does not belong to the completed namespace implementation scope. The executor takes one invocation object and supports the same return types and result conversion as SkillMethod. Public annotation, invocation-record, and recoverable-exception names are settled; remaining integration details are described below. Executor lifecycle hooks are excluded.

Implementation-specific published configuration would be a new framework feature: existing Java skills are Spring-managed annotated methods, and the framework currently has no configurable Java declaration binding or configuration-injection contract. This document contains the current enhancement scope; historical proposal syntax is not part of its contract.

Work is limited to generic framework capabilities. Cloud and DataSkill examples are motivating context only. The accepted namespace design assigns Java methods through `@SkillMethod(namespace = "...")`, defaulting to `default`; this registration decision does not implement the configuration proposal below.

Framework is in development, with no customers or deployments. Breaking API, SPI, and manifest changes are explicitly permitted; no backwards compatibility or shims are required. Work may span multiple tickets and commits with broken intermediate builds or applications until the relevant effort is complete. The repository's closed supported API rules, documentation requirements, and supported-surface tests apply to the final design. Use the existing namespace implementation as the integration baseline.

## Delivery boundary

This enhancement adds configured executors, unifies SkillMethod and SkillExecutor rules and implementation wherever possible, standardizes output and failure handling, and removes REST-specific support. It includes replacing standard tool calling's broad exception-to-text behavior with designated recoverable-failure feedback. Planner recovery improvements belong to [Planner Failure Recovery](planner-failure-recovery.md), a separate follow-up; they are not prerequisites for completing this enhancement.

Do not add step-planner correction loops, planning-skill inability outcomes, broader replanning, partial-progress protocols, or new nested/parallel recovery machinery here. The current orchestration integration below defines the complete first-delivery behavior.

## Agreed direction

- Use the name `SkillExecutor`, with an `executor:` reference in each YAML skill, instead of SkillInterface/SkillImplementation. An annotated Java method supplies reusable execution behavior; many uniquely named YAML skills can use it with different operation configuration and input schemas. Annotation fields and the public invocation type `SkillExecutorInvocation` are settled below.
- Both executors and YAML skills have namespace ownership. Use existing namespace visibility, not a separate executor visibility flag: a skill may reference an executor in its own namespace or in a Public namespace, but not in another Private namespace.
- Executors are reference-only registrations. YAML skills provide their callable surface, including description, input contract, roles, and skill configuration. An executor is not itself offered as a tool or callable through `SkillTemplate.invoke()`. Existing `@SkillMethod` remains the mechanism for ordinary directly callable Java methods. Integrate executor dependencies into existing validation and captured execution without adding a direct executor invocation route.
- Verify executor existence and namespace access at startup and in the API update workflow. Fit checks into existing validation, preparation, and admission boundaries rather than assuming `publish()` reruns dependency validation.
- Place application executor configuration under `loomspan.executor-config.<namespace>.<relative-path>`. This supersedes the earlier `loomspan.interfaces` suggestion. The executor declares the optional relative configuration reference so skill authors do not manage infrastructure paths. Reference syntax, omission, and missing-path rules are defined below.
- Each YAML skill may contain multiple nested parameters under `config:`. This is operation configuration interpreted by the executor, separate from invocation input and the executor's application configuration. Framework must preserve its content without interpreting its domain language.
- A database executor may accept a database selector in the skill's `config:` and resolve it against application executor configuration. This can support multiple databases with one executor; separate host bindings are not required merely for that example.
- Database access rules belong to the executor, not Framework. Namespace visibility controls access to the executor registration; it does not define database permissions. Trusted caller/namespace context must not be inferred from business arguments.
- Replace and remove REST skill functionality as part of the Framework feature. The required outcome is the ability to call out to the calling application or another microservice through an executor; preserving the existing REST API, manifest syntax, or exact behavior is not a requirement. Consumer adaptation is a separate effort and does not gate REST removal. No production removal is performed by this design-document edit.
- Reuse the existing namespace publication, immutable admission snapshot, ownership, and retirement machinery. Do not introduce a parallel configuration-generation lifecycle.
- SkillExecutor is invocation-only and should behave as much like a function as possible. Do not add executor preparation, configuration-validation, or cleanup hooks. Executor-specific configuration errors are reported during execution; Framework retains responsibility for its own binding and validation rules.
- Reuse and extend `RestSkillInvocation`'s defensive copying: recursively copy maps/lists and make them unmodifiable, preserving nulls and non-container leaf values. This refines the earlier universal immutability requirement: arbitrary leaf objects and the content behind resource handles are not promised to be deeply immutable. Do not add Kryo or general object-serialization copying. Executors may perform application-owned I/O.
- Use one invocation-object parameter and the same supported return types as SkillMethod; there is no String-only restriction. Include the captured executor-configuration generation ID so application beans can manage their own resources. The public type and its five accessors are settled below; additional metadata requires a demonstrated need.
- Pass only the executing skill's input/configuration, the referenced executor configuration subtree, and necessary trusted metadata. Do not expose full application configuration, internal generation/runtime object graphs, or unrelated resources. Spring collaborators own resource setup/teardown, including responses to configuration-generation changes.

### Executor annotation

Use `@SkillExecutor` with `name`, `namespace`, and `config` elements:

```java
@SkillExecutor(name = "sql", namespace = "infrastructure", config = "databases")
```

- `name` defaults to the Java method name when omitted, following SkillMethod naming conventions.
- `namespace` defaults to `default`, following SkillMethod namespace conventions.
- `config` is optional and declares the dot-separated path relative to `loomspan.executor-config.<executor-namespace>`. Omission supplies the empty immutable executor-configuration map under the binding rules below.

No executor description element is needed: YAML skills own the callable descriptions. Reuse existing Java discovery and identity handling. Declare `String config() default "";`. Omitted `config` and an explicitly empty string mean no configuration reference. Apply the path-validation rules below; do not introduce fallback lookup.

### Mutually exclusive Java method declarations

Reject a Java method annotated with both `@SkillMethod` and `@SkillExecutor` during registration. Do not choose annotation precedence or register the same method in both roles. The annotations define different input contracts, and executor registration must not accidentally expose a directly callable skill.

A Spring bean may contain separate methods of both kinds and share implementation through ordinary Java calls. Reuse existing canonical method and annotation discovery to enforce the rule consistently.

### Invocation data accessors

The public invocation type is the Java record `SkillExecutorInvocation`, with the five agreed components `input`, `skillConfig`, `executorConfig`, `skillName`, and `executorConfigGenerationId`. Its public constructor enforces required fields and performs the agreed defensive copying. Framework and application unit tests use the same constructor; no separate builder, factory API, or test-only helper is needed. Executors remain annotated Spring bean methods accepting this record, not interface implementations.

It exposes three settled data accessors, each returning `Map<String, Object>`:

- `input()` supplies validated, reference-resolved invocation arguments.
- `skillConfig()` supplies the YAML skill's operation configuration.
- `executorConfig()` supplies the referenced application executor configuration subtree.

Keep these values separate, without Framework merging or precedence. Recursively copy maps and lists into unmodifiable containers, preserving nulls and non-container leaf values under the agreed copying rules. Omitted configuration supplies an empty immutable map.

Executors own domain-specific extraction, interpretation, and validation, and may convert these maps into application-specific types using ordinary Java libraries. No Framework typed configuration binding system, type registry, or conversion annotations are selected. Any additional metadata requires a demonstrated need; the invocation type name is settled.

### Invocation metadata

Include `skillName()` and `executorConfigGenerationId()` as settled metadata accessors. `skillName()` supplies the canonical qualified identity of the executing YAML skill, including for child invocation; it is not the root skill identity or caller authentication. `executorConfigGenerationId()` supplies the captured executor namespace generation ID under the rules below.

Both values come from Framework's captured execution state, not business input. Additional metadata requires a concrete use case. Caller authentication uses the existing scoped Spring Security context during executor invocation; no authentication or token accessor is added to the invocation object. Preserve caller propagation and context restoration for root and child execution through Framework-managed execution. Application-owned background work owns its own context propagation.

### Shared Java results

Both SkillMethod and SkillExecutor use one shared result-conversion path and the same supported Java return types. Either may successfully return `null`. Framework normalizes that result to the empty string at the shared Java invocation boundary, before downstream result handling. Do not classify a null return as an execution failure or serialize it as the literal text `null`. Null and an explicitly returned empty string represent the same successful no-content result.

Return non-null strings unchanged, including empty strings and whitespace. Do not JSON-serialize, quote, trim, or otherwise transform the returned text. Serialize other non-null Java return values as JSON through shared conversion machinery. Serialization failures remain execution failures under the shared failure contract.

This deliberately changes SkillMethod: strings are no longer JSON-quoted, and null no longer becomes the literal text `null`. SkillExecutor is not restricted to a String return. Update affected tests and documentation together; do not retain alternate conversion behavior or compatibility paths.

Thrown exceptions remain execution failures, with causes preserved through existing Framework failure reporting. Both SkillMethod and SkillExecutor use the shared recoverable-exception contract below; do not convert exceptions into successful result text.

These decisions do not change configuration omission or explicit-null configuration rules. Exact exception wrapping and integration with existing authorization and cancellation behavior remain implementation details to verify; preserving a cause does not promise that the identical exception object reaches every caller.

### Authorization composition

Enforce both the executing YAML skill's access rules and any applicable Spring method-security advice on the executor method. The caller must satisfy both; neither overrides or bypasses the other. Preserve Spring-managed proxy invocation and existing caller authentication propagation. A shared executor does not grant access that either boundary denies. Reuse existing authorization mechanisms rather than introducing a separate executor authorization engine.

Planner tool discovery must evaluate both the YAML skill's RBAC policy and the referenced executor's supported declarative RBAC policy against the same caller. If either denies access, omit the skill from that caller's planner tool options. Enforce both again at execution; tool filtering does not replace authorization. Keep the two policies conjunctive rather than flattening their role lists into one alternative-role list.

Reuse the existing Java access-policy resolution for `@RolesAllowed`, `@PermitAll`, and `@DenyAll`. Arbitrary Spring method-security advice, including argument-dependent expressions, still runs at invocation; the discovery guarantee does not imply pre-evaluating all such advice or executing application code during tool discovery. Management/catalog visibility is a separate surface and is not changed by this planner-discovery decision.

### Direct execution leaf declarations

Executor-backed YAML skills are direct-execution leaves. They declare their executor, operation configuration, input contract, description, identity, and access rules. Reject model, prompt, planning, and child-skill declarations, including explicitly null or empty declarations. Do not combine executor invocation with model execution or planning in the same skill. A separate planning skill may call executor-backed skills through the existing mechanisms.

Neither SkillMethod nor SkillExecutor uses Framework output-schema validation. Java code owns constructing its result; Framework owns the shared result conversion. Reject `output_schema` and `output_schema_max_retries` on executor-backed YAML skills, including explicitly null or empty declarations. Do not introduce Java output-validation retries or a separate executor output-schema contract.

### Configuration omission and binding rules

The YAML `config:` field is allowed only on executor-backed skills. Reject its declaration on other skill forms, including null or empty declarations, rather than silently accepting configuration with no consumer. This restriction concerns skill YAML, not the namespace-scoped application `executor-config` section.

- When the YAML skill omits `config:` or supplies `config: {}`, supply an empty immutable map.
- An explicitly supplied `config:` must be a map. Reject explicit null (including a bare `config:`), scalars, and lists during Framework validation. Preserve null values inside configuration maps.
- An explicitly referenced executor-configuration subtree must likewise be a map: an empty map is valid, but null, scalar, and list values fail Framework binding validation. Preserve null values inside that subtree.
- When the executor declares no configuration reference, supply an empty immutable map for executor configuration.
- When an executor explicitly declares a reference but the path is missing, fail Framework binding validation. Do not silently supply empty configuration or fall back to another source.
- When referenced configuration exists but has executor-specific problems, the executor reports the error during invocation; Framework does not interpret domain settings.
- Always supply `executorConfigGenerationId()` as the captured executor namespace's generation ID, including when no configuration reference is declared. It is not null or a separate configuration-only revision.

These rules support either configuration source, both, or neither, without merging, precedence, or inheritance. Explicit null configuration is not equivalent to omission; this structural rule is separate from null executor results, which normalize to empty strings.

### Namespace-scoped configuration publication

Keep the same `loomspan.executor-config.<namespace>.<relative-path>` YAML structure at startup and in `ExecutionConfiguration` supplied to the update API. Startup configuration may contain multiple namespaces. A publication may contain executor configuration only for its target namespace; reject foreign namespace entries rather than ignoring them or updating another namespace.

Publication remains complete replacement. Omitting `executor-config` supplies no executor configuration for the new generation; do not inherit previous or startup values. An explicit executor configuration reference whose path is consequently missing fails the agreed binding validation. Configuration belongs to the executor namespace, including when a YAML skill in another namespace references that executor.

### Configuration reference syntax

Use a simple dot-separated path relative to `loomspan.executor-config.<executor-namespace>`. Each segment selects a map key; for example, `databases.inventory` selects the `inventory` map inside `databases` in that namespace's executor configuration.

The annotation uses `String config() default "";`. Omission or an explicitly empty string means no reference and supplies an empty immutable map. Reject whitespace-only paths, leading/trailing whitespace, and empty segments such as `.database`, `database.`, or `database..primary`. Do not silently trim or repair a path.

The selected value must be a map. Its contents remain executor-defined and may include nested maps, lists, and scalars. An omitted reference supplies the agreed empty immutable map. Do not support array indexing, wildcards, expressions, or fallback lookup. Dots separate path segments, so keys containing literal dots cannot be addressed through this syntax. Reuse existing suitable path handling where possible rather than introducing an expression engine.

### Executor identity and reference resolution

Reuse existing skill identity conventions for executor registrations: local name plus namespace, namespace defaulting to `default`, omitted name defaulting to the Java method name, and existing name validation/namespace normalization. Duplicate executor identities fail registration; do not resolve by Spring bean name or fallback search. Executor registrations and callable skills are separate identity categories, so a callable skill and executor may share a qualified name without ambiguity.

Executor references follow existing skill reference resolution: `executor: sql` resolves in the declaring YAML skill's namespace, while `executor: infrastructure-sql` is explicit. No fallback search is permitted.

`default` is a namespace name, not Public visibility: the owner's earlier use of "public" when describing annotation defaults was corrected to "default". Omitting an annotation namespace uses `default`; supplying one uses that namespace. Existing namespace visibility is unchanged.

### Shared recoverable exception contract

SkillMethod and SkillExecutor use one failure contract. Application code may deliberately throw a designated recoverable exception carrying feedback for planner correction. Ordinary returns represent success; other exceptions are terminal by default. Do not infer recoverability from message text, HTTP status codes, or arbitrary application exception classes. No success/failure return wrapper is selected.

A recoverable exception still represents a failed execution attempt, not successful result data. Its feedback may be supplied to the parent planner through orchestration-owned failure handling. It does not authorize automatic retry or guarantee that an operation had no side effects. Authorization, cancellation, and execution limits remain enforced boundaries.

The designated exception is an intentional supported application API addition and must live in `ai.loomspan.api`, be added to the closed allowlist, documented, and covered by supported-surface tests. `RecoverableSkillException` is the settled public name. It extends the existing `SkillException` and exposes `RecoverableSkillException(String message)` and `RecoverableSkillException(String message, Throwable cause)`. Both constructors require a non-null, nonblank message and reject invalid messages at construction. Preserve a valid message unchanged, without trimming or rewriting it. The optional cause preserves diagnostic context and must not supply fallback planner feedback. Both SkillMethod and SkillExecutor use this same exception. Wrapping/classification rules are settled below. Direct invocation propagates recoverable and terminal exceptions to the application caller.

Replace Java exception-to-successful-text conversion and the standard tool manager's broad default conversion with the same explicit recoverability policy. Ordinary runtime exceptions must not become model correction feedback merely because the tool manager previously did so. Authorization, quota, timeout, cancellation, and shutdown failures propagate as terminal controls.

### Recoverable exception wrapping

Unwrap known invocation-infrastructure wrappers to recognize `RecoverableSkillException`. Do not search arbitrary application cause chains for a recoverable exception. An invocation wrapper around a recoverable exception preserves recoverability; an application-thrown `IllegalStateException` whose cause is a recoverable exception is terminal. This preserves the application's outer exception choice while accommodating invocation plumbing.

Identify the concrete infrastructure wrapper types encountered by the shared Java invocation and tool-calling paths during implementation and cover them with focused tests. Do not treat every exception with a cause as a transparent wrapper. Preserve the agreed terminal handling of authorization, cancellation, timeout, quota, and shutdown failures.

### Integration with current orchestration

| Route | Required behavior in this enhancement |
|---|---|
| Direct invocation | Propagate recoverable and terminal exceptions to the application caller. |
| Standard model tool calling | Supply designated recoverable failure feedback to the model; propagate terminal failures. Preserve existing freedom to correct arguments, select another available tool, or change approach within execution boundaries. |
| Explicit step planning | Preserve current mission termination on tool failure, including designated recoverable failures. Retain classification, message, and cause in failure reporting; do not introduce a correction loop. |

These are planning-mode differences, not separate Java execution contracts. Recoverable means correction is permitted, not that every mode already implements it. Nested calls follow these existing route boundaries; this enhancement does not add an escalation protocol or automatic replay of a failed child mission. Only deliberate application recoverable signals qualify for standard feedback; exact wrapper handling must preserve terminal controls.

Use existing accounting and deadlines for standard-mode continuation. Every model call and tool reattempt uses normal metered paths; failures consume budgets without refund/reset, and the parent timeout does not restart. Add no recovery-specific quota. Step-slot semantics remain unchanged. The follow-up owns extending recovery within step tasks.

### Recoverable failure feedback

Where current orchestration supports continuation (standard model tool calling), the parent planner receives the identity of the failed tool call, an explicit indication that the attempt failed and correction is permitted, and the application-supplied recoverable exception message unchanged. Both Java execution forms use this feedback contract. Exact transport representation remains an implementation detail; step-planner feedback-driven correction belongs to the follow-up.

The failed attempt remains recorded as a failure. Its feedback must not be recorded as successful output or used as a successful result in downstream input bindings. Preserve the exception and its cause in diagnostics; planner correction feedback uses the deliberately supplied application message rather than automatically substituting a cause message or stack trace.

### Public catalog classification

Classify executor-backed YAML skills as `SkillKind.JAVA`, alongside directly callable SkillMethod methods. Their shared implementation kind is Java execution. List the YAML-defined callable skill in the public catalog; do not list reference-only executor registrations as independently callable skills. Diagnostics may identify the executor binding without adding a separate public skill category.

REST removal is part of this enhancement. Remove `SkillKind.REST` along with obsolete REST API, manifest support, and execution wiring. Update public-surface tests, catalog consumers, tests, samples, and documentation to the final classification. No deprecated enum value, alias, or compatibility mapping is required. Consumer migration remains a separate effort and does not gate removal.

### Fixed executor registration lifecycle

SkillExecutor follows the existing SkillMethod registration lifecycle. Fixed executor registrations appear in every generation of their assigned namespace. Namespace replacement retains those registrations while replacing generation-owned configuration with the newly supplied values; no configuration inheritance is introduced.

Removing a namespace withdraws its executors from new lookup and admission while already retained executions finish against their captured generations. Explicit namespace recreation includes its assigned fixed executors again with the new generation's configuration. Removal does not destroy Spring beans. Bean state, resource ownership, and application shutdown remain application/Spring responsibilities; annotation changes require redeployment.

Reuse the existing fixed-Java registration and namespace lifecycle owners. Do not create a separate executor activation or bean lifecycle. Preserve proxy/advice behavior and pass captured configuration in each invocation rather than mutating shared bean configuration between concurrent calls.

### Shared Java execution rules and implementation

Maximize shared rules and code paths between `@SkillMethod` and SkillExecutor. Prefer one coherent Java execution implementation, including discovery, proxy invocation, authentication, authorization, execution controls, observation, result conversion, and failure handling wherever their requirements permit. Existing differences are not automatically requirements to preserve; deliberate changes to SkillMethod are allowed under this feature's compatibility posture.

Every proposed difference in rules or code paths between SkillMethod and SkillExecutor must be explicitly called out in the planning document. For each difference, identify the concrete requirement, explain why a shared rule or implementation is insufficient, and describe the simplest alternative and technical-debt impact. Do not introduce silent divergence as an implementation detail.

One shared output and failure policy for SkillMethod and SkillExecutor is required. The designated recoverable-exception approach is settled above. Shared result conversion is settled: null becomes empty text, strings remain unchanged, and other values are serialized as JSON. Both Java execution forms support the same return types and use the same conversion implementation.

### Generality and implementation simplicity

SkillExecutor must extend the mechanisms already used to discover and invoke `@SkillMethod` methods. RBAC, input validation, namespace validation, and the direct execution lifecycle already exist; reuse and extend their current owners rather than implementing executor-specific copies. Preserve Spring-managed invocation and applicable advice, caller authentication, validation boundaries, and the existing execution/observation controls. Reuse does not mean blindly adopting Java result serialization or exception-to-text semantics where the agreed REST replacement goal requires different behavior; resolve those differences explicitly within the shared mechanisms.

No new subsystem, lifecycle, or execution path may be introduced implicitly as an implementation detail. If an existing mechanism cannot meet a committed requirement, identify the concrete gap, explain why extension is insufficient, and discuss the simplest alternatives with the owner. Any proposed new subsystem, lifecycle, or execution path requires explicit agreement before implementation. Record that decision and its tradeoffs in the design; difficulty extending existing code is a reason for discussion, not automatic authorization for parallel machinery.

Developers decide which configuration belongs in application-level executor configuration and which belongs in the individual skill's `config:`. Support both sources without assigning domain-specific meanings to either: application configuration is not restricted to connections, and skill configuration is not restricted to SQL or operation selectors. Those are examples, not Framework rules. Omission follows the agreed empty-map rules above.

Use the [framework feature design lens](../framework-feature-design-lens.md) when evaluating this feature. Choose the simplest complete design that meets the committed goals and preserves production boundaries. Generality should come from preserving executor-owned data and behavior, not from speculative hooks, registries, binding layers, or lifecycle subsystems. Identify substantial subsystem work and discuss simpler alternatives with the owner before selecting it. Prioritize the simplest final Framework architecture over smaller patches or continuously working intermediate commits. Remove obsolete REST paths rather than maintaining coexistence, adapters, or compatibility machinery for existing consumers.

The settled data contract delivers the two configuration sources separately through the map-valued accessors above, alongside invocation input and only the trusted context actually needed. Let executor code decide how to interpret or combine them. Do not introduce Framework merging, override precedence, interpolation, or domain validation merely to support both sources.

Cost assessment from the current source investigation: carrying structured configuration through a generation fits existing ownership; cross-namespace executor references require extending dependency validation, reachable admission capture, and execution resolution because today's traversal follows `allowedSkills`. Namespace isolation and retained execution are essential correctness, not optional follow-up work. A new executor resource factory lifecycle, generic configuration schema system, or host binding registry would add separate concepts and requires demonstrated need; none is selected. Exact implementation effort remains to be assessed during design.

### Illustrative authoring shape

This is proposed syntax, not a supported manifest or a finalized API. Namespace ownership of supplied skill documents continues to come from publication.

```yaml
# Application configuration
loomspan:
  executor-config:
    infrastructure:
      sql:
        databases:
          inventory:
            # Executor-defined connection settings
```

An executor named `sql` in namespace `infrastructure` would reference the relative configuration path `sql`. A skill published into `sales` could use it when `infrastructure` is Public:

```yaml
name: createCar
description: Create a car
executor: infrastructure-sql
config:
  database: inventory
  sql: |
    insert into car (name, model, year)
    values (:/car/name, :/car/model, :/car/year)
input_schema:
  type: object
  properties:
    car:
      type: object
      properties:
        name: { type: string }
        model: { type: string }
        year: { type: integer }
      required: [name, model, year]
  required: [car]
```

SQL syntax, parameter binding, connection selection, and database authorization belong to the executor. The example does not establish Framework SQL support or a required executor configuration schema.

## Existing publication machinery: source-verified baseline

Read before selecting new implementation machinery:

- `src/main/java/ai/loomspan/internal/skill/DefaultSkillReloader.java`: `prepare` accepts complete documents and `ExecutionConfiguration` for one namespace, captures a preparation view, and returns a detached one-shot candidate. `publish` checks candidate ownership/state and lifecycle, then delegates installation. Closing an unpublished candidate disposes it.
- `src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java`: `capturePreparation` captures the target base ID (or absence) and a coherent view of active namespace metadata. Preparation checks candidate dependencies against that view and builds its runtime. `publish` checks only the target base, then installs atomically; changes to foreign dependencies do not themselves reject publication.
- The same manager's `capture` traverses the root's reachable `allowedSkills`, selects one active generation per reachable namespace under the registry monitor, and acquires ownership. `validateAdmission` checks the retained snapshot outside that monitor. Executor references are not currently represented in this traversal; their integration is necessary design work, not a reason to create another snapshot system.
- `src/main/java/ai/loomspan/internal/skill/SkillAdmissionSnapshot.java` and `SkillGeneration.java`: execution resolves through captured immutable namespace associations and generation-owned capabilities/definitions/runtime. Superseded generations remain alive while ownership exists; retirement closes generation resources and dispatches existing retirement notifications.
- `src/main/java/ai/loomspan/internal/skill/ExecutionConfigurationParser.java`: publishable top-level sections currently are `connections`, `models`, `session`, and `execution-trace` (with only supported nested settings accepted). `executor-config` is not supported today and requires an explicit parser/configuration extension.
- [Complete namespace publication](../../../agent-skills/loomspan-docs/references/java-api/skill-reload.md) describes complete replacement, defaulting without prior/startup configuration inheritance, namespace visibility, retained admission, and retirement limitations.

Apply these existing semantics to executor dependencies and configuration. Do not assume a caller publication permanently pins a foreign executor generation: the existing model captures the current reachable graph at admission. Exact executor dependency representation and configuration storage remain to be designed. Do not add public generation fields merely because internal capture involves more than one namespace.

## Implementation review checkpoints

### Configuration validation: no assumed domain checks

Framework can validate its own declaration syntax, executor resolution, and namespace visibility. It does not know that `database`, `sql`, or any other executor-owned key has a particular meaning.

The earlier suggestion that preparation verifies that a selected database exists and required executor settings are present was a possible executor-provided capability, not an agreed requirement. Even "database exists" is ambiguous: a key in supplied configuration, an externally resolved resource, or a reachable live database are different claims. No database lookup, connection probe, SQL validation, mandatory schema, or executor validation callback is selected.

Settled boundary:

- Invocation-only execution is selected; executor-specific errors are reported at execution time. Configuration schemas and executor validation callbacks are not part of this design. Revisit only for a demonstrated need and explicit agreement, preserving today's validation boundary rather than silently turning `validate()` into application execution.
- Do not design hypothetical optional-validation hooks as remaining required work. Framework reference/path binding checks still need to fit the existing preparation/admission boundaries without publication-time dependency revalidation or retaining foreign runtime resources during metadata-only preparation.

### Registration, configuration ownership, and invocation

- Invocation construction is settled: a public record with required-field validation and defensive copying. Recoverable exception naming, constructors, and wrapper-classification rules are settled above. Annotation elements (`name`, `namespace`, `config`), invocation data/metadata accessor names, registration naming, executor reference resolution, and reference-only behavior are settled.
- Configuration reference syntax, ownership, and publication shape are settled above: simple dot-separated map paths, executor namespace ownership, the same namespace-indexed layout at startup and publication, and rejection of foreign namespace entries in a publication. The annotation element is `config`, with empty string as its omission default. Omission and malformed-path rejection rules are settled above; diagnostics must consistently explain the violated rule.
- Keep the five settled invocation components; add metadata only if source review demonstrates a concrete unmet requirement and the owner agrees. The three map-valued data accessors are also settled above; the public type is the record `SkillExecutorInvocation`, with construction rules settled above. Caller authentication through the existing Spring Security context is settled above; no Authentication field or general context bag is selected.
- Fixed executor registration lifecycle is settled above: reuse SkillMethod behavior across namespace replacement, removal, and recreation, preserving proxies and per-invocation captured configuration.
- Resource management stays application/Spring-owned under the invocation-only decision. No executor resource factory or generation-specific preparation/cleanup hook is selected. Any future need must be demonstrated and discussed explicitly.
- The shared designated recoverable-exception contract is settled above; exception naming, constructors, and wrapper-classification rules are settled. Identify and test the actual invocation-infrastructure wrappers during implementation. Shared output conversion and return-type support are settled above. Current-mode integration is settled above; broader planner recovery is out of scope. Verify exception wrapping, authorization, and cancellation integration. Do not adopt exception-to-successful-text conversion or introduce automatic retries/output validation implicitly.
- Authorization composition is settled above: enforce both YAML skill access rules and applicable executor method-security advice. Verify integration through existing checks and Spring proxy invocation.
- Remaining manifest structural details include malformed paths and consistent diagnostics. Direct-execution leaf restrictions, output-schema rejection, and configuration omission, explicit null, and wrong-type behavior are settled above. Do not infer new supported combinations from this document's examples.
- Public catalog classification is settled: executor-backed skills use `SkillKind.JAVA`, executor registrations are not independently callable catalog entries, and `SkillKind.REST` is removed in this enhancement. Complete Console/diagnostic integration, packaging, and deliberately supported API additions. The current sole supported SPI remains `RestSkillHandler` until the public surface is explicitly changed, documented, and tested.

### REST removal and Framework verification

Framework completion includes executor support and removal of the obsolete REST-specific API, `SkillKind.REST`, manifest support, wiring, and execution branches. Update Framework tests, samples, documentation, and supported-surface allowlists to describe the final contract. Multiple implementation tickets and commits are allowed; intermediate builds and applications need not work, and no compatibility bridge is required.

Verify that an executor-backed skill can call the hosting/calling application or another microservice. Use a representative HTTP integration in Framework verification without requiring consumer migration first. Validate the selected executor contract and existing production boundaries, including input/reference handling, authorization, root and child execution, failure reporting, observation, cancellation, and retained work across publication/removal. Existing REST tests are evidence of useful scenarios, not a requirement for behavioral parity. SQL remains a useful second case for checking that configuration support stays general.

Verify the same failure classification for SkillMethod and SkillExecutor: direct callers receive exceptions, standard tool calling feeds back only designated recoverable failures, and step planning still terminates without a correction loop. Confirm failures remain failed in traces and cannot satisfy successful-result bindings. Verify terminal controls are not swallowed by the standard tool manager and that continuation uses existing quotas and deadlines.

Consumer-specific HTTP configuration, transport policies, and migration plans are outside this Framework design. They must not become Framework ticket requirements without a separately discussed general capability gap.

## Fresh context review handoff

The next task is a design consistency and implementation-gap review, not implementation or ticket generation. Read repository instructions and the [framework feature design lens](../framework-feature-design-lens.md), then compare this design with actual source and relevant tests. The preceding decisions are accepted requirements; do not reopen them without concrete contradictory evidence or an unmet requirement.

Review for contradictions, stale statements, missed integration points, accidental API expansion, duplicated machinery, and unnecessary debt. Identify the existing owners to extend or simplify. Every proposed difference between SkillMethod and SkillExecutor must be explicitly justified in the eventual planning document. Prefer a more involved implementation if it produces a simpler final Framework architecture. Intermediate broken commits are acceptable; no compatibility shims are required.

Keep three efforts distinct: this enhancement, [Planner Failure Recovery](planner-failure-recovery.md), and Sidecar's HTTP executor reimplementation. Read the follow-up only to understand scope boundaries. Sidecar notes live at `C:/opendev/code/loomspan-sidecar/ai/thoughts/phases/http-skill-executor-design-notes.md`; they are consumer context, not additional Framework requirements. Framework completion includes REST and `SkillKind.REST` removal and deliberate standard-tool-manager failure classification, but not new step recovery loops or planning-skill inability outcomes.

Useful source-review targets include Java discovery/proxy invocation and serialization; manifest parsing and validation; generation dependency capture and retained admission; fixed Java registration; authorization/discovery; catalog and internal execution-kind routing; standard tool callbacks and exception wrappers; step termination; quotas; traces/Console; public-surface allowlists; tests and consumer documentation. In particular, verify how executor-backed YAML can use public `SkillKind.JAVA` without inheriting ordinary-method assumptions in internal routing. Identify issues rather than inventing answers without evidence.

Return prioritized findings with source references, concrete consequences, and the simplest proposed resolutions. Separate genuine product decisions from routine implementation details and already-settled choices. Flag obsolete code that can be removed in scope; do not preserve internal machinery for compatibility. Discuss any required product decisions one at a time. No production implementation, tickets, or commits are authorized by this handoff.

Important clarifications to retain:

- "Function-like" means invocation-only with defensively copied inputs, not pure or side-effect-free. A Spring-managed executor can delegate to other beans, perform HTTP/database work, and rely on application-owned resources.
- Application beans own creation/reuse/teardown of connection pools and other resources, including changes driven by executor configuration generations. The captured generation ID is a useful key, not a Framework promise to manage those resources. Executor beans or collaborating resource-management beans may inject the public `SkillReloader` API and register `onGenerationRetired(...)`, matching callback IDs against `executorConfigGenerationId()`. Register listeners early, keep callbacks prompt, and arrange application-owned cleanup. Notifications may be omitted during shutdown, so application beans also own shutdown cleanup. No new lifecycle hook or invocation accessor is selected.
- REST's copy helper traverses maps and lists and preserves other leaves. Immutable scalars need no clone. Existing `ref://` resolution can yield Spring `Resource` file inputs; preserving a resource handle does not freeze or duplicate its underlying bytes. Such handles arise from relevant input values, not automatic exposure of connection pools, sessions, or runtime internals. See `RestSkillInvocation`, `DefaultRefResolver`, and `SessionLocalVirtualFileSystem` for the existing behavior.
- Configuration is general executor-owned data at either location. SQL, database selectors, connection settings, and HTTP are examples, not schema requirements or a division of responsibilities enforced by Framework.
- Avoid exposing unused object graphs or speculative metadata. The executor receives only its relevant input/configuration subtree and necessary trusted identity. No full configuration tree, admission snapshot, mutable session bag, or internal runtime object is selected.
- Reuse existing SkillMethod invocation, RBAC, input/reference validation, namespace validation, execution limits, observations, and generation retention. Any proposed new subsystem, lifecycle, or execution path must be identified, compared with simpler alternatives, and explicitly agreed before implementation.

Only design documents/tickets have been edited in this discussion; no executor production implementation has been performed. Documentation edits were checked with `git diff --check`, not production tests.
