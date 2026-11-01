# Configured Java Skill Executors

## Status and scope

This document records the accepted feature contract and all eight accepted architecture-review findings through 2026-10-31. The enhancement is not implemented and does not belong to the completed namespace implementation scope. The executor takes one invocation object and supports the same return types and result conversion as SkillMethod. Public annotation, invocation-record, and recoverable-exception names are settled. Remaining implementation choices must satisfy the accepted architecture below; executor lifecycle hooks are excluded. This document does not authorize production implementation or ticket generation.

Published executor configuration is part of this enhancement. The pre-enhancement implementation has Spring-managed annotated Java skills but no configured executor binding or configuration-injection contract. References below to current source describe that baseline; the accepted contracts describe the required final behavior.

Work is limited to generic framework capabilities. Cloud and DataSkill examples are motivating context only. The existing namespace design assigns Java methods through `@SkillMethod(namespace = "...")`, defaulting to `default`; configurable executors are the separate enhancement specified here.

Framework is in development, with no customers or deployments. Breaking API, SPI, and manifest changes are explicitly permitted; no backwards compatibility or shims are required. Work may span multiple tickets and commits with broken intermediate builds or applications until the relevant effort is complete. The repository's closed supported API rules, documentation requirements, and supported-surface tests apply to the final design. Use the existing namespace implementation as the integration baseline.

## Delivery boundary

Optimize for the simplest complete final Framework architecture, even when implementation requires more work, substantial internal restructuring, or broken intermediate builds. Existing class boundaries and data structures are not constraints. Preserve the committed behavior and production boundaries while removing obsolete paths, duplicate authorities, and unnecessary abstractions. The accepted review findings are requirements for eventual planning and implementation, not optional suggestions or additional parallel subsystems. Their numbers identify the original review findings; sections are placed beside the relevant contract topics.

This enhancement adds configured executors, unifies SkillMethod and SkillExecutor rules and implementation wherever possible, standardizes output and failure handling, and removes REST-specific support. It includes replacing standard tool calling's broad exception-to-text behavior with designated recoverable-failure feedback. Planner recovery improvements belong to [Planner Failure Recovery](planner-failure-recovery.md), a separate follow-up; they are not prerequisites for completing this enhancement.

Do not add step-planner correction loops, planning-skill inability outcomes, broader replanning, partial-progress protocols, or new nested/parallel recovery machinery here. The current orchestration integration below defines the complete first-delivery behavior.

## Agreed direction

- Use the name `SkillExecutor`, with an `executor:` reference in each YAML skill, instead of SkillInterface/SkillImplementation. An annotated Java method supplies reusable execution behavior; many uniquely named YAML skills can use it with different operation configuration and input schemas. Annotation fields and the public invocation type `SkillExecutorInvocation` are settled below.
- Both executors and YAML skills have namespace ownership. Use existing namespace visibility, not a separate executor visibility flag: a skill may reference an executor in its own namespace or in a Public namespace, but not in another Private namespace.
- Executors are reference-only registrations. YAML skills provide their callable surface, including description, input contract, roles, and skill configuration. An executor is not itself offered as a tool or callable through `SkillTemplate.invoke()`. Existing `@SkillMethod` remains the mechanism for ordinary directly callable Java methods. Integrate executor dependencies into existing validation and captured execution without adding a direct executor invocation route.
- Verify executor existence and namespace access at startup and in the API update workflow. Fit checks into existing validation, preparation, and admission boundaries rather than assuming `publish()` reruns dependency validation.
- Place application executor configuration under `loomspan.executor-config.<namespace>.<relative-path>`. The executor declares the optional relative configuration reference so skill authors do not manage infrastructure paths. Reference syntax, omission, and missing-path rules are defined below; no `loomspan.interfaces` alias is supported.
- Each executor-backed YAML skill may contain multiple nested parameters under `config:`. This field is forbidden on other skill forms. It is operation configuration interpreted by the executor, separate from invocation input and the executor's application configuration. Framework must preserve its content without interpreting its domain language.
- A database executor may accept a database selector in the skill's `config:` and resolve it against application executor configuration. This can support multiple databases with one executor; separate host bindings are not required merely for that example.
- Database access rules belong to the executor, not Framework. Namespace visibility controls access to the executor registration; it does not define database permissions. Trusted caller/namespace context must not be inferred from business arguments.
- Replace and remove REST skill functionality as part of the Framework feature. The required outcome is the ability to call out to the calling application or another microservice through an executor; preserving the existing REST API, manifest syntax, or exact behavior is not a requirement. Consumer adaptation is a separate effort and does not gate REST removal. No production removal is performed by this design-document edit.
- Reuse the existing namespace publication, immutable admission snapshot, ownership, and retirement machinery. Do not introduce a parallel configuration-generation lifecycle.
- SkillExecutor is invocation-only, not necessarily pure or side-effect-free. Do not add executor preparation, configuration-validation, or cleanup hooks. Executor-specific configuration errors are reported during execution; Framework retains responsibility for its own binding and validation rules. Spring-managed executors may delegate to application beans and perform I/O.
- Extract and share the container-copy behavior currently found in `RestSkillInvocation`, then remove that obsolete REST API: recursively copy maps/lists and make them unmodifiable, preserving nulls and non-container leaf values. Arbitrary leaf objects and the content behind resource handles are not promised to be deeply immutable. Do not add Kryo or general object-serialization copying.
- Use one invocation-object parameter and the same supported return types as SkillMethod; there is no String-only restriction. Include the captured executor-configuration generation ID so application beans can manage their own resources. The public type and its five accessors are settled below; additional metadata requires a demonstrated need.
- Pass only the executing skill's input/configuration, the referenced executor configuration subtree, and necessary trusted metadata. Do not expose full application configuration, internal generation/runtime object graphs, or unrelated resources. Spring collaborators own resource setup/teardown, including responses to configuration-generation changes.

### Executor annotation

Use `@SkillExecutor` with `name`, `namespace`, and `config` elements:

```java
@SkillExecutor(name = "sql", namespace = "infrastructure", config = "sql")
```

- `name` defaults to the Java method name when omitted, following SkillMethod naming conventions.
- `namespace` defaults to `default`, following SkillMethod namespace conventions.
- `config` is optional and declares the dot-separated path relative to `loomspan.executor-config.<executor-namespace>`. Omission supplies the empty immutable executor-configuration map under the binding rules below.

No executor description element is needed: YAML skills own the callable descriptions. Reuse existing Java discovery and identity handling. Declare `String config() default "";`. Omitted `config` and an explicitly empty string mean no configuration reference. Apply the path-validation rules below; do not introduce fallback lookup.

### Mutually exclusive Java method declarations

Reject a Java method annotated with both `@SkillMethod` and `@SkillExecutor` during registration. Do not choose annotation precedence or register the same method in both roles. The annotations define different input contracts, and executor registration must not accidentally expose a directly callable skill.

A Spring bean may contain separate methods of both kinds and share implementation through ordinary Java calls. Reuse existing canonical method and annotation discovery to enforce the rule consistently.

### Accepted review finding 7: shared Java discovery and proxy invocation

Use one Java method discovery process and one shared proxy-invocation mechanism, with two explicit declaration roles. Restructure the existing `SkillMethodBeanPostProcessor` responsibilities where doing so leaves simpler ownership; its current combination of discovery, interface-contract resolution, schema generation, argument binding, invocation, and result conversion is not a class boundary to preserve. Do not add an independent executor scanner/postprocessor that duplicates proxy, bridge-method, security, or contract-resolution behavior.

The selected discovery flow is: discover relevant annotated methods; resolve each canonical method and its declaration contract; validate its declaration role; register it in the callable-skill or reference-only executor index. Establish the role before registration. Detect conflicting SkillMethod/SkillExecutor declarations across implementation methods and their mapped interface contracts, not just annotations physically present on one reflected method. For example, an interface declaring SkillMethod and its implementation declaring SkillExecutor must fail clearly rather than being registered in both roles or resolved by annotation precedence. A bean may still contain separate methods of both roles.

Share bridge-method canonicalization, compatible interface-contract resolution, naming and namespace conventions, proxyability checks, supported declarative security-policy resolution and enforcement verification, runtime proxy method selection, and Spring-managed bean invocation. Preserve applicable advice by invoking the proxy rather than the unwrapped target. Interface contract compatibility must account for the relevant declaration metadata; incompatible contracts must produce actionable diagnostics rather than depend on scan order. Existing alias/bridge deduplication and explicitly named overload behavior must not diverge between the two roles.

Only the following responsibilities require role-specific handling:

| Responsibility | SkillMethod | SkillExecutor |
|---|---|---|
| Declaration validation | Existing Java parameter contracts | Exactly one SkillExecutorInvocation parameter and the configuration-path declaration |
| Callable input schema | Derived from Java parameter metadata | Owned by the referencing YAML skill; do not expose the invocation record as a tool schema |
| Argument construction | Bind declared typed parameters | Construct SkillExecutorInvocation from validated/resolved input and captured configuration/metadata |
| Registration destination | Callable-skill index | Reference-only executor index |

After argument construction, use the same proxy invocation, result conversion, and failure propagation established by finding 3. The role distinction must not create separate authentication propagation, authorization engines, observations, execution controls, or return-type support. SkillMethod's typed resource materialization remains an argument-binding concern; executor invocation retains the accepted map/resource-leaf semantics.

Separate discovery-time responsibilities from invocation-time responsibilities where that makes the implementation simpler. A small shared discovery component and shared Java invoker may replace the current mixed postprocessor; exact class names and decomposition remain implementation details. This is consolidation of existing Java machinery, not authorization for a generic annotation-plugin framework, a new discovery lifecycle, a public extension point, or compatibility wrappers around the old internals.

Use shared proxy/discovery test coverage for both roles, with focused tests for their necessary differences. Include JDK and CGLIB proxies, interface-declared annotations, generic bridge methods, equivalent and conflicting interface contracts, role conflicts split across interface/implementation declarations, explicit overload names, duplicate identities within each role, allowed same-name identities across roles, mixed-role beans, method-security advice, and rejection of unsafe proxy invocation. Verify executor-only beans are discovered and executor registrations never become callable tools or receive reflected invocation-record schemas.

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

### Accepted review finding 2: shared authorization composition and evaluation

Use one shared declarative authorization evaluation across root validation, planner tool filtering, and execution. The current independent root-policy evaluation in `DefaultSkillTemplate` and the checks reached through `AccessGuard` must converge on the same composition and evaluation rules. Do not add executor checks independently to each boundary. Existing class boundaries may be restructured to achieve the simplest final architecture; preserving an internal decomposition is not a requirement.

A callable skill's effective declarative authorization is a conjunction of its applicable existing policies. SkillMethod uses its Java declarative policy. An executor-backed YAML skill requires both its YAML policy and the referenced executor's Java declarative policy, evaluated against the same resolved caller. Reuse the existing role evaluator, role-prefix handling, and role hierarchy for each constituent policy. For example, YAML requiring `SALES` and an executor requiring `DATABASE_WRITE` requires both policies to pass. Flattening those roles into one alternative-role list would change the contract. An unrestricted or PermitAll constituent does not override a restrictive constituent; DenyAll denies the conjunction.

Keep these responsibilities explicit while consolidating their shared implementation:

- Callable authority: existing captured entry-target and declared-child rules determine whether this source may call this target. Retention of an executor namespace is not additional callable authority, as specified in accepted finding 1.
- Declarative caller authorization: one shared evaluator checks all applicable policies for the callable skill. Root validation, planner filtering, and execution use this same policy composition and caller semantics. Filtering omits denied skills; validation/execution report denial through their existing boundaries. Execution rechecks authorization even if discovery previously permitted the tool.
- Invocation advice: the shared Java invocation path calls the Spring-managed proxy so applicable method-security advice executes with the actual arguments and scoped caller authentication. Declarative checks do not replace advice or authorize invoking an unwrapped target bean.

Root validation and planner discovery remain metadata-only. They do not execute the executor, run argument-dependent method-security advice, or promise that every possible Spring advice decision has been predicted. Preserve the existing supported declarative annotation resolution and enforcement verification for `@RolesAllowed`, `@PermitAll`, and `@DenyAll`. Preserve authentication capture, explicit-caller/session-fallback semantics where applicable, worker-thread propagation, and context restoration. This consolidation adds no authentication accessor or general context bag.

Resolve the executor's policy from the executor registration selected through finding 1's captured generation view. Do not flatten or permanently copy a foreign executor policy into the caller generation, and do not consult live registrations during retained execution. The internal conjunction representation is an implementation detail; it does not require an executor authorization service, a new policy language, a general-purpose Boolean policy engine, or another supported SPI.

The extra YAML-policy constituent is the necessary difference between the two Java declaration forms: the YAML skill is a separately authored callable boundary. That difference does not justify separate role evaluators, caller resolution rules, or execution authorization mechanisms. Management/catalog visibility remains a separate surface and is unchanged by this decision.

Verification must cover both policies permitting, either policy denying, role alternatives within each policy versus conjunction between policies, PermitAll/DenyAll, configured role prefixes/hierarchies, and consistent outcomes across root validation, root/child execution, and planner filtering. Exercise retained cross-namespace execution, real Spring proxy advice (including invocation-only argument-dependent advice), and authentication restoration. Ensure denied execution cannot be enabled merely by retaining the executor namespace or by having passed tool filtering earlier.

### Direct execution leaf declarations

Executor-backed YAML skills are direct-execution leaves. They declare their executor, operation configuration, input contract, description, identity, and access rules. Reject model, prompt, planning, and child-skill declarations, including explicitly null or empty declarations. Do not combine executor invocation with model execution or planning in the same skill. A separate planning skill may call executor-backed skills through the existing mechanisms.

Neither SkillMethod nor SkillExecutor uses Framework output-schema validation. Java code owns constructing its result; Framework owns the shared result conversion. Reject `output_schema` and `output_schema_max_retries` on executor-backed YAML skills, including explicitly null or empty declarations. Do not introduce Java output-validation retries or a separate executor output-schema contract.

### Accepted review finding 8: authoritative manifest validation and complete REST removal

Use one manifest-processing path that produces a validated declaration, with executor leaves completely replacing the obsolete REST declaration form. Consolidate interpretation at the authoring boundary rather than copying REST-specific raw/typed validation branches into separate executor machinery. Existing mutable manifest classes, declaration-presence bookkeeping, and later definition checks may be restructured when that leaves simpler ownership and fewer repeated rules.

The selected flow within the existing manifest machinery is: read authored YAML while preserving field presence; determine and validate the declaration form; validate its applicable fields; construct the immutable definition used downstream. Structural authoring rules have one authoritative owner. Raw parsing may reject malformed types or coercion, but later layers must not maintain independent versions of the same form-specific restrictions. Keep strict unsupported-field handling and actionable source/field diagnostics.

Presence is independent of value. An executor leaf declaring `prompt: null` still violates the leaf contract. Omitted `config` supplies an empty map, while explicit `config: null` fails. `config` on a non-executor form fails even when empty or null. Executor declarations cannot combine with TypeSafe, model, planning, child-skill, linter, output-forwarding/binding, or output-schema declarations under the accepted direct-leaf rules. Use one shared set of form-specific rules to enforce these distinctions, including explicitly empty/null forbidden fields; do not silently choose precedence among conflicting forms.

Carry the validated declaration form explicitly into the internal definition. Remove the current equivalence between "YAML definition without model execution configuration" and REST. Executor-backed definitions carry their executor reference and immutable skill configuration, while model-backed and TypeSafe definitions retain their required execution settings. Downstream code consumes those validated facts instead of repeatedly inspecting nullable fields to rediscover the declaration form. Internal constructors may protect invariants without becoming a second authoring validator. This direction does not require a generic manifest-schema engine, a large type hierarchy, or a new public declaration API; choose the smallest internal representation that makes the existing forms and their invariants clear. Declaration form remains distinct from execution classification as established in finding 5.

Reuse existing YAML input-schema validation and invocation reference resolution for executor leaves. Use finding 6's shared container-copy semantics for operation configuration rather than serialization round-trips. Structural manifest validation does not execute the executor or interpret its domain configuration. Cross-namespace executor resolution and configuration-path binding remain owned by finding 1's shared dependency machinery; centralizing authoring validation does not move those checks into another resolver.

Remove REST as both declaration form and execution mechanism: delete its manifest field/support, handler discovery and handler-count checks, invocation wiring, obsolete public API types, and execution-kind branches. Old `rest:` declarations fail normal unsupported-field validation. Do not retain an alias, converter, compatibility handler, alternate runtime path, or migration reader. Update current tests, samples, packaged authoring guidance, supported-surface allowlists, and Console contracts/fixtures to the final design. Consumer-specific Sidecar adaptation remains separate and does not gate this removal.

Carry useful REST test scenarios forward as executor scenarios, including root/child invocation, reference inputs, authorization, observations, cancellation, and retained work. Old REST handler semantics are not requirements to preserve. Replace tests that assert obsolete behavior rather than keeping obsolete production paths to satisfy them.

Verification must cover valid executor leaves, conflicting declaration forms, forbidden fields supplied with ordinary/null/empty values, omitted versus invalid configuration, unsupported `rest` declarations, preserved immutable configuration content, and correct validated definitions reaching generation/routing/catalog consumers. Ensure normal model-backed and TypeSafe declarations retain their intended rules. Audit raw parsing, typed binding, manifest copying, and definition construction for duplicated authoring checks or implicit REST assumptions; consolidate or remove them as part of the change rather than adding another executor-specific layer.

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

### Accepted review finding 6: immutable configuration within namespace generations

Treat executor configuration as immutable generation-owned data with one shared set of structural rules. Extend the existing generation system rather than introducing a configuration registry, a separate configuration revision, provider-client lifecycle ownership, or another configuration subsystem. Prefer restructuring existing parsing/copy boundaries over maintaining multiple interpretations of executor-owned data.

The selected flow is: startup or publication configuration enters shared structural validation and container copying; the resulting configuration belongs to its namespace generation; finding 1's resolver selects the executor subtree from the admitted executor generation; invocation receives that subtree under the existing defensive-copy contract. Startup and publication use the same internal representation, structural rules, and path resolution after their ingress-specific ownership checks. Startup may supply multiple namespaces; a publication is complete replacement for its target namespace and rejects foreign entries. Omission does not inherit startup or prior-generation values.

Store the authoritative executor configuration under the existing generation owner. Metadata-only preparation must be able to inspect executor registration/configuration without accessing live provider resources. Do not add an independently mutable copy inside the provider runtime, copy foreign configuration into caller generations, or reconstruct configuration from current application settings during retained execution. Detached validation views and defensively copied invocation containers do not constitute additional configuration authorities or lifecycles.

Use one shared container-copy implementation for the accepted map/list semantics across skill configuration, executor configuration, and invocation construction. Recursively copy maps/lists into unmodifiable containers, preserving null leaves and non-container leaf values as already agreed. Do not use JSON round-tripping, scalar flattening, or generic object serialization merely to copy executor-owned data. Preserve required defensive copying at public construction boundaries; the exact reuse of already-immutable internal values is an implementation detail that must not weaken those boundaries.

Use finding 1's shared dependency resolver for annotation path validation and subtree binding. The dot-separated path selects a map in the executor namespace's captured configuration; missing or non-map explicit references fail binding, while omission supplies an empty map. Supply only that relevant subtree. Framework does not merge skill and executor configuration, assign override precedence, interpret domain keys, interpolate executor data, or apply connection credential-reference rules to it. For example, a key named `api-key-ref` inside executor configuration is ordinary executor-owned data; its spelling does not activate Framework connection-reference handling or sensitivity classification.

Startup binding fidelity is a concrete implementation verification requirement, not an assumption that adding a map property is sufficient. Verify the actual Spring startup ingestion path preserves the accepted structure and values, and compare equivalent startup and publication inputs. If the existing binding path loses distinctions, correct that ingress boundary rather than introducing different startup semantics or downstream repair/fallback rules. Keep this work focused on the executor data contract; no general configuration framework rewrite is selected merely by this requirement.

Verification must cover nested maps/lists, null leaves, empty containers, scalar value types, case-sensitive keys, and literal dotted keys within selected subtrees. Literal dotted keys remain preserved data but cannot themselves be addressed as one segment by the accepted dot-path syntax. Cover omitted configuration versus explicit null/wrong-type declarations, malformed/missing paths, target-namespace enforcement, complete replacement without inheritance, defensive copying, and coherent retained configuration after replacement/removal. Audit existing parser and runtime/manifest copy boundaries so new configuration cannot be silently dropped or transformed. No domain schemas, executor validation callbacks, resource factories, or public typed-binding API are introduced.

### Executor identity and reference resolution

Reuse existing skill identity conventions for executor registrations: local name plus namespace, namespace defaulting to `default`, omitted name defaulting to the Java method name, and existing name validation/namespace normalization. Duplicate executor identities fail registration; do not resolve by Spring bean name or fallback search. Executor registrations and callable skills are separate identity categories, so a callable skill and executor may share a qualified name without ambiguity.

Executor references follow existing skill reference resolution: `executor: sql` resolves in the declaring YAML skill's namespace, while `executor: infrastructure-sql` is explicit. No fallback search is permitted.

`default` is a namespace name, not Public visibility. Omitting an annotation namespace uses `default`; supplying one uses that namespace. Existing namespace visibility is unchanged.

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

### Accepted review finding 3: shared invocation and explicit failure policy

The selected final architecture separates three responsibilities: shared Java invocation, one narrow failure-classification policy, and the existing orchestration route's response to that classification. Prefer restructuring existing internals and deleting obsolete behavior over layering executor-specific exception handling onto the current implementation. This direction applies equally to SkillMethod and SkillExecutor.

The shared Java invocation path invokes the Spring-managed proxy, converts successful results, and propagates failures. Null becomes empty text, strings remain unchanged, and other results are JSON-serialized by the same conversion implementation. Serialization failures propagate as execution failures. The invocation path must not return error text as a successful result or decide whether a planner should continue.

Use one internal recoverability policy with the already-settled designated-exception rule. Recognize `RecoverableSkillException` through only known invocation-infrastructure wrappers. Normalize wrappers at the boundaries that introduce them where practical, retaining causes for diagnostics, and share the classification rule wherever it is needed. Do not implement repeated arbitrary cause-chain searches or separate policies for Java methods, executors, tool calling, and diagnostics. An infrastructure wrapper around `RecoverableSkillException` preserves recoverability; an application-thrown `IllegalStateException` with that exception as its cause is terminal. Wrapper transparency must be supported by the actual invocation plumbing and focused tests, not inferred merely from the presence of a cause.

The orchestration route owns the disposition:

- Direct invocation propagates failures to the application through existing Framework reporting boundaries; an identical exception object is not promised, but classification, message, and cause must be preserved.
- Standard tool calling produces explicitly failed-call feedback only for designated recoverable failures, preserving the application message unchanged. Terminal failures propagate.
- Explicit step planning continues to terminate on tool failure, including recoverable failures, preserving classification. No correction loop is added.

Integrate the explicit policy through Spring AI's existing tool-exception-processing hook. The current default conversion of runtime tool failures to feedback must be replaced, not supplemented with a growing list of terminal exceptions. Retain the existing tool manager and canonical tool-selection enforcement; this finding does not select a replacement tool manager or recovery engine. Identify and test actual reflection/proxy, worker-future, and Spring AI callback wrappers. Existing `MissionWorkExecutor` primary-cancellation precedence remains owned by execution controls; classification must not override timeout, cancellation, quota, authorization, or shutdown boundaries.

Failures must traverse the existing execution/tool failure-recording path before the outer standard-tool boundary produces recoverable feedback. Feedback is not a successful skill result, does not earn successful-skill credit, and cannot satisfy downstream successful-result bindings. Canonical recording must use the same classification policy, not independently infer recoverability; accepted finding 4 specifies recording and identity. No additional recovery state machine, outcome wrapper, public classification API, retry policy, or quota is selected.

Remove `LoomspanExceptionTransformer`, its default implementation and autoconfiguration wiring, and obsolete exception-specific bypasses whose sole purpose was avoiding broad exception-to-successful-text conversion. Preserve necessary security and execution-control behavior through the shared invocation and existing control owners. Do not preserve old transformation paths, alternative output conversion, or compatibility adapters. Replace tests that assert transformed error text with tests of the accepted contract.

Verification must cover both Java forms across direct invocation, real standard tool-manager processing, and step termination. Include null/string/object results, serialization failure, designated recoverable exceptions with exact message preservation, ordinary terminal exceptions, infrastructure-wrapped recoverable exceptions, application terminal wrappers around recoverable causes, and terminal security/execution controls. Verify failed recording and absence of successful-result credit before model feedback, and normal accounting/deadlines for standard-mode continuation. Tests only at the Java invoker boundary are insufficient because Spring AI's outer default conversion is a separate existing behavior that this change removes.

### Recoverable failure feedback

Where current orchestration supports continuation (standard model tool calling), the parent planner receives the identity of the failed tool call, an explicit indication that the attempt failed and correction is permitted, and the application-supplied recoverable exception message unchanged. Both Java execution forms use this feedback contract. Exact transport representation remains an implementation detail; step-planner feedback-driven correction belongs to the follow-up.

The failed attempt remains recorded as a failure. Its feedback must not be recorded as successful output or used as a successful result in downstream input bindings. Preserve the exception and its cause in diagnostics; planner correction feedback uses the deliberately supplied application message rather than automatically substituting a cause message or stack trace.

### Accepted review finding 4: canonical failure meaning and identity

Keep the existing canonical failure-recording mechanism as the authority for diagnostic failure meaning. Use finding 3's shared classification policy; do not introduce an independent trace/Console classifier, a second failure journal, a public failure API, or a recovery-history subsystem. Existing failure-record shapes and identity bookkeeping may be restructured to produce the simplest coherent final architecture. Update current writers, readers, projections, and fixtures together rather than preserving obsolete formats with adapters.

Record whether a failure is designated recoverable or terminal explicitly at the owning failure boundary. Preserve the deliberately supplied recoverable exception message unchanged together with exception/cause diagnostics. Contextual summaries such as "Tool execution failed" are distinct from the actual exception message and must not replace it. Exact internal field names and representation remain implementation details. Recoverability describes the failure, not an orchestration action or outcome: a recoverable exception still terminates step planning and propagates to direct callers. Do not infer that correction occurred or succeeded merely because a failure is recoverable.

Failure identity must distinguish transparent propagation from a new application failure:

- Reuse a failure record when the same exception propagates to another boundary, including recognized transparent infrastructure wrappers. Reuse the same wrapper interpretation as finding 3; the existence of a cause alone does not establish transparency.
- An application-thrown wrapper is a distinct failure, even if its cause has already been recorded or happens to have the same recoverability classification. Record the new outer exception's meaning and preserve its causal relationship to the earlier failure. Do not overwrite the earlier record or silently reuse its identity.
- In particular, if a recorded `RecoverableSkillException` becomes the cause of an application-thrown `IllegalStateException("Cannot continue", cause)`, record the terminal outer failure and retain the causal relationship. The current arbitrary cause-chain deduplication must not cause the terminal failure to disappear or inherit the earlier recoverable classification.

Limit deduplication to transparent propagation rather than adding message/type heuristics. Retain the actual exception cause diagnostics; where the cause already has a recorded failure identity, preserve that relationship in the existing canonical recording model. This does not select a general-purpose causal graph subsystem. Provider-attempt correlation and failure identity are distinct concerns: a new failure may be causally associated with the same provider attempt without becoming the same failure. Preserve existing execution-control ownership of primary cancellation and terminal failure selection.

Console and observer projections consume recorded classification and diagnostic facts. They must not reconstruct recoverability from exception class names, messages, stack traces, record adjacency, or later outcomes. Standard tool feedback uses finding 3's same policy and deliberate application message; diagnostic recording does not create another rule for whether continuation is permitted. Failed attempts remain excluded from successful result data and credit even when the enclosing standard-mode mission later succeeds.

Preserve existing bounded diagnostic capture and explicit truncation controls. Preserve the documented `ExecutionJournalProjector` field-name redaction exception only in its existing derived-journal/observer scope; do not apply it to canonical failure records, model feedback, or skill results, and do not broaden its rules. This work introduces no sensitivity classification or automatic content rewriting.

Verification must cover same-exception propagation through multiple recording boundaries, known infrastructure wrappers sharing failure identity, application wrappers receiving distinct identities and retaining recorded-cause linkage, and a terminal wrapper around an already-recorded recoverable failure. Verify explicit classification and exact recoverable-message fidelity in canonical records and applicable projections, continued diagnostic cause capture, provider-attempt correlation without identity conflation, direct propagation, step termination, and standard recoverable feedback without successful-result credit. Schema changes are current diagnostic-contract changes, not a reason for historical readers or compatibility shims.

### Public catalog classification

Classify executor-backed YAML skills as `SkillKind.JAVA`, alongside directly callable SkillMethod methods. Their shared implementation kind is Java execution. List the YAML-defined callable skill in the public catalog; do not list reference-only executor registrations as independently callable skills. Diagnostics preserve the YAML declaration and identify the executor binding and Java implementation as required by accepted finding 5, without adding a separate public skill category.

REST removal is part of this enhancement. Remove `SkillKind.REST` along with obsolete REST API, manifest support, and execution wiring. Update public-surface tests, catalog consumers, tests, samples, and documentation to the final classification. No deprecated enum value, alias, or compatibility mapping is required. Consumer migration remains a separate effort and does not gate removal.

### Accepted review finding 5: execution classification and declaration provenance

Separate how a callable skill executes from where it was declared. The current assumption that `JAVA` means both Java execution and annotation-only declaration must be removed from generation validation, definition lookup, execution routing, diagnostic catalogs, and Console contracts. Preserve the accepted public `SkillKind.JAVA` classification for both Java forms; do not add an EXECUTOR kind to encode declaration provenance.

Use one execution classification to select behavior and supply public catalog classification. Preserve declaration provenance explicitly in the existing metadata model for diagnostics:

| Callable declaration | Execution kind | Relevant provenance |
|---|---|---|
| SkillMethod | JAVA | Annotated bean method |
| Executor-backed YAML | JAVA | Original YAML declaration plus referenced executor identity and Java method |
| Model-backed YAML | YAML | Original YAML declaration |
| TypeSafe YAML | TYPESAFE | Original YAML declaration |

Both Java forms use one Java execution branch, lifecycle, and shared invocation machinery. Their necessary argument-construction differences remain within that shared implementation. Retrieve declaration information according to the actual registration rather than inferring that Java execution has no YAML definition. Do not introduce another direct-execution engine, an executor-specific catalog, or parallel lifecycle merely to preserve existing kind-based assumptions.

Diagnostics describe one callable skill with its relevant provenance. Executor-backed skill details retain the original authored YAML and identify the executor binding and Java implementation location. Reference-only executors do not become independently callable catalog entries. Declaration and method locations remain diagnostic information, never alternative invocation identities. Exact internal provenance types and wire field names remain implementation details; this direction does not add a public provenance API or a new supported extension point.

Replace the existing diagnostic contract that requires bean/method-only provenance and forbids YAML/source paths for every JAVA entry. Update Framework metadata construction and validation, Go DTO/service validation, TypeScript contracts, Console rendering, fixtures, and documentation together. Rendering and validation use provenance for source details and execution kind for classification. Do not make Console infer execution behavior from the presence of YAML or preserve REST/source-format assumptions through aliases, fallback readers, or compatibility adapters. Assess the cross-component compatibility marker under the existing exact-version policy as part of this protocol change.

Mandatory ticket-generation and implementation requirement: "After REST removal, implementation should assess whether that duplicate enum still owns any distinct behavior. If it remains only a one-to-one translation, remove it rather than expanding it to encode declaration forms." The duplicate in question is internal `CapabilityKind` relative to public `SkillKind`. Carry this assessment and conditional removal explicitly into the generated implementation ticket's scope and acceptance criteria; do not omit it as optional cleanup. If distinct behavior justifies retaining the enum, record the concrete responsibility and why the simpler single-classification representation is insufficient. Existing visibility, tests, or call sites alone do not justify retaining it. This requirement does not authorize changing the settled public kind classification.

Verification must cover both Java forms sharing execution routing, executor-backed definitions surviving generation validation and lookup, original YAML and executor implementation details remaining inspectable in Console, absence of reference-only executors from callable catalogs, and complete REST-kind removal across current contracts and fixtures. Verify model-backed and TypeSafe provenance continues to work. Record the duplicate-enum assessment and its resulting simplification in implementation evidence.

### Fixed executor registration lifecycle

SkillExecutor follows the existing SkillMethod registration lifecycle. Fixed executor registrations appear in every generation of their assigned namespace. Namespace replacement retains those registrations while replacing generation-owned configuration with the newly supplied values; no configuration inheritance is introduced.

Removing a namespace withdraws its executors from new lookup and admission while already retained executions finish against their captured generations. Explicit namespace recreation includes its assigned fixed executors again with the new generation's configuration. Removal does not destroy Spring beans. Bean state, resource ownership, and application shutdown remain application/Spring responsibilities; annotation changes require redeployment.

Reuse the existing fixed-Java registration and namespace lifecycle owners. Do not create a separate executor activation or bean lifecycle. Preserve proxy/advice behavior and pass captured configuration in each invocation rather than mutating shared bean configuration between concurrent calls.

### Shared Java execution rules and implementation

Maximize shared rules and code paths between `@SkillMethod` and SkillExecutor. Prefer one coherent Java execution implementation, including discovery, proxy invocation, authentication, authorization, execution controls, observation, result conversion, and failure handling wherever their requirements permit. Existing differences are not automatically requirements to preserve; deliberate changes to SkillMethod are allowed under this feature's compatibility posture.

Every proposed difference in rules or code paths between SkillMethod and SkillExecutor must be explicitly called out in the planning document. For each difference, identify the concrete requirement, explain why a shared rule or implementation is insufficient, and describe the simplest alternative and technical-debt impact. Do not introduce silent divergence as an implementation detail.

One shared output and failure policy for SkillMethod and SkillExecutor is required. The designated recoverable-exception approach is settled above. Shared result conversion is settled: null becomes empty text, strings remain unchanged, and other values are serialized as JSON. Both Java execution forms support the same return types and use the same conversion implementation.

### Generality and implementation simplicity

Consolidate and restructure the mechanisms already used to discover and invoke `@SkillMethod` methods as required by the accepted findings. RBAC, input validation, namespace validation, and the direct execution lifecycle already exist; share their semantic ownership rather than implementing executor-specific copies. Preserve Spring-managed invocation and applicable advice, caller authentication, validation boundaries, and execution/observation controls. Replace the old Java serialization and exception-to-text behavior with the settled shared output and failure contract; those changes are no longer open design questions.

No new subsystem, lifecycle, or parallel execution path may be introduced implicitly as an implementation detail. The consolidation and internal restructuring explicitly selected in findings 1–8 are already accepted directions; they do not require reconfirmation merely because existing classes must change or new internal helper types are useful. If those directions cannot meet a committed requirement without an additional subsystem, lifecycle, or execution path, identify the concrete gap and discuss the simplest alternatives with the owner before selecting it. Difficulty extending existing code does not authorize parallel machinery.

Developers decide which configuration belongs in application-level executor configuration and which belongs in the individual skill's `config:`. Support both sources without assigning domain-specific meanings to either: application configuration is not restricted to connections, and skill configuration is not restricted to SQL or operation selectors. Those are examples, not Framework rules. Omission follows the agreed empty-map rules above.

Use the [framework feature design lens](../framework-feature-design-lens.md) when evaluating this feature. Choose the simplest complete design that meets the committed goals and preserves production boundaries. Generality should come from preserving executor-owned data and behavior, not from speculative hooks, registries, binding layers, or lifecycle subsystems. Identify substantial subsystem work and discuss simpler alternatives with the owner before selecting it. Prioritize the simplest final Framework architecture over smaller patches or continuously working intermediate commits. Remove obsolete REST paths rather than maintaining coexistence, adapters, or compatibility machinery for existing consumers.

The settled data contract delivers the two configuration sources separately through the map-valued accessors above, alongside invocation input and only the trusted context actually needed. Let executor code decide how to interpret or combine them. Do not introduce Framework merging, override precedence, interpolation, or domain validation merely to support both sources.

Implementation effort must be assessed against the accepted final architecture: structured configuration belongs to namespace generations, and shared dependency resolution covers callable children and executor references. Namespace isolation and retained execution are essential correctness, not optional follow-up work. A resource factory lifecycle, generic configuration schema system, or host binding registry is not selected. Effort estimates must not turn those excluded mechanisms into prerequisites or preserve duplicated machinery to reduce patch size.

### Illustrative authoring shape

This example uses the accepted Framework syntax; support is not yet implemented. The SQL-specific keys and behavior are illustrative executor-owned data. Namespace ownership of supplied skill documents continues to come from publication.

```yaml
# Application configuration
loomspan:
  executor-config:
    infrastructure:
      sql:
        databases:
          inventory: {}
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

The following describes the pre-enhancement source verified during review, not the desired final architecture. Use it to locate existing owners, then apply the accepted findings; recheck source if it changes before implementation:

- `src/main/java/ai/loomspan/internal/skill/DefaultSkillReloader.java`: `prepare` accepts complete documents and `ExecutionConfiguration` for one namespace, captures a preparation view, and returns a detached one-shot candidate. `publish` checks candidate ownership/state and lifecycle, then delegates installation. Closing an unpublished candidate disposes it.
- `src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java`: `capturePreparation` captures the target base ID (or absence) and a coherent view of active namespace metadata. Preparation checks candidate dependencies against that view and builds its runtime. `publish` checks only the target base, then installs atomically; changes to foreign dependencies do not themselves reject publication.
- The same manager's `capture` traverses the root's reachable `allowedSkills`, selects one active generation per reachable namespace under the registry monitor, and acquires ownership. `validateAdmission` checks the retained snapshot outside that monitor. Executor references are not currently represented in this traversal; their integration is necessary design work, not a reason to create another snapshot system.
- `src/main/java/ai/loomspan/internal/skill/SkillAdmissionSnapshot.java` and `SkillGeneration.java`: execution resolves through captured immutable namespace associations and generation-owned capabilities/definitions/runtime. Superseded generations remain alive while ownership exists; retirement closes generation resources and dispatches existing retirement notifications.
- `src/main/java/ai/loomspan/internal/skill/ExecutionConfigurationParser.java`: publishable top-level sections currently are `connections`, `models`, `session`, and `execution-trace` (with only supported nested settings accepted). `executor-config` is not supported today and requires an explicit parser/configuration extension.
- [Complete namespace publication](../../../agent-skills/loomspan-docs/references/java-api/skill-reload.md) describes complete replacement, defaulting without prior/startup configuration inheritance, namespace visibility, retained admission, and retirement limitations.

Apply these existing semantics to executor dependencies and configuration. Do not assume a caller publication permanently pins a foreign executor generation: the existing model captures the current reachable graph at admission. The architectural direction below is accepted; exact internal types and storage layout remain implementation details within that direction. Do not add public generation fields merely because internal capture involves more than one namespace.

### Accepted review finding 1: unified dependency resolution and retention

The selected final architecture has one namespace-generation system, one shared dependency-resolution policy, and one admission snapshot covering callable skills and their executor dependencies. Optimize for the simplest complete final Framework architecture, even when reaching it requires substantial restructuring. Existing class boundaries, callable-only traversal, and data structures are not constraints to preserve. Merely adding independent executor checks to preparation, admission, and invocation would leave duplicated rules and does not satisfy this direction.

Each namespace generation owns its complete callable declarations, reference-only executor registrations, and immutable executor configuration. Fixed Java registrations retain the already-settled replacement/removal/recreation behavior. SkillMethod and SkillExecutor share Java discovery and invocation machinery. Keep separate skill and executor name indexes within the existing generation ownership so the two identity categories may share a qualified name; this distinction does not authorize separate registry, activation, configuration-generation, or retirement lifecycles. Startup namespace initialization must account for namespaces containing only executor registrations.

Consolidate dependency resolution within the existing generation machinery around two explicit reference roles:

- `allowed_skills` references callable child skills and determines the declared callable graph.
- `executor` references execution behavior and its namespace-owned configuration; the executor is never a callable child.

Share identity resolution and namespace visibility rules, then apply the structural requirements of the referenced role. Executor existence and annotation configuration-path binding are Framework checks owned by this shared resolution policy. Executor-specific domain validation remains invocation-only. Do not build a general-purpose dependency graph framework, plugin resolver system, or speculative extension points to represent these two relationships. A shared resolver describes semantic ownership, not a required new public type or a mandate to keep all code in one class.

Preserve the different lifecycle boundaries while sharing their resolution rules:

1. Preparation validates against the complete candidate namespace overlaid on its captured foreign metadata view. It must not execute application code, retain foreign runtime resources, or permanently bind the caller generation to a foreign executor generation/configuration subtree.
2. Publication retains the existing target-base check and atomic installation semantics. Foreign changes after preparation do not add publication-time dependency revalidation.
3. Fresh admission selects and retains one coherent generation per required namespace using the existing capture/ownership mechanism. Follow executor references from reachable skills as well as their child references. Validate against the selected immutable generations; preserve the existing separation between registry capture and expensive validation outside the registry monitor.
4. Invocation resolves the executor registration, selected configuration subtree, and `executorConfigGenerationId()` through that admission snapshot. It must not consult the live registry or create another independently selected configuration snapshot. Do not duplicate binding rules in invocation; use the shared resolution policy or its captured result.

Retaining a namespace is not callable authority. An executor reference retains the executor namespace but does not expose the executor as a tool, add its name to `allowed_skills`, grant access to unrelated skills in that namespace, or traverse those unrelated skills' dependencies. Preserve root-reachable admission rather than validating or retaining every skill merely because its namespace was captured. Executor namespace visibility is checked as a dependency permission; callable source/target authority remains governed by the existing declared-child rules. Authorization composition is separately specified above.

For example, `sales-createCar` referencing `infrastructure-sql` admits a sales generation S and an infrastructure generation I. The skill declaration and skill configuration come from S; the executor registration and executor configuration come from I. `skillName()` remains `sales-createCar`, and `executorConfigGenerationId()` identifies I, including when the executor declares no configuration path. A later infrastructure replacement affects fresh admissions, while already-admitted work continues with I. Removal blocks fresh dependent admissions while retained work can finish. Existing ownership and retirement govern both generations; removing a namespace does not destroy its Spring beans.

Implementation verification must demonstrate same-name skill/executor coexistence without executor callability, executor-only namespace startup, same- and cross-namespace binding, private-namespace rejection, missing/non-map configuration rejection, fresh versus retained behavior across replacement/removal/recreation, and lack of authority or dependency expansion to unrelated skills. Include a foreign dependency change between preparation and publication: publication follows the existing target-base rule, fresh admission validates the newly selected graph, and previously retained admission remains coherent. These are acceptance consequences of the selected architecture, not new lifecycle or compatibility features.

## Implementation review checkpoints

### Configuration validation: no assumed domain checks

Framework can validate its own declaration syntax, executor resolution, and namespace visibility. It does not know that `database`, `sql`, or any other executor-owned key has a particular meaning.

Preparation does not verify domain claims such as whether a selected database exists or executor-specific settings are complete. No database lookup, connection probe, SQL validation, mandatory schema, or executor validation callback is selected.

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
- Authorization composition is settled in finding 2: shared declarative-policy conjunction across root validation, planner filtering, and execution, plus applicable Spring proxy advice at invocation. Verify the same caller and captured executor policy are used.
- Manifest restrictions, malformed-path rules, and configuration omission/null/type behavior are settled. Implement them through findings 6 and 8's shared owners with consistent diagnostics; exact diagnostic wording is an implementation detail. Do not infer new supported combinations from examples.
- Public catalog classification and provenance are settled in finding 5. Implement the Console/diagnostic changes and the mandatory `CapabilityKind` assessment and conditional removal. The final supported API adds `SkillExecutor`, `SkillExecutorInvocation`, and `RecoverableSkillException`, and removes `RestSkillHandler`, `RestSkillInvocation`, and `SkillKind.REST`. Update allowlists, supported-surface tests, README, repository guidance describing the old sole SPI, and packaged consumer documentation. Annotation-based registration must not introduce an internal bean-replacement contract or another public extension point.

### REST removal and Framework verification

Framework completion includes executor support and removal of the obsolete REST-specific API, `SkillKind.REST`, manifest support, wiring, and execution branches. Update Framework tests, samples, documentation, and supported-surface allowlists to describe the final contract. Multiple implementation tickets and commits are allowed; intermediate builds and applications need not work, and no compatibility bridge is required.

Verify that an executor-backed skill can call the hosting/calling application or another microservice. Use a representative HTTP integration in Framework verification without requiring consumer migration first. Validate the selected executor contract and existing production boundaries, including input/reference handling, authorization, root and child execution, failure reporting, observation, cancellation, and retained work across publication/removal. Existing REST tests are evidence of useful scenarios, not a requirement for behavioral parity. SQL remains a useful second case for checking that configuration support stays general.

Verify the same failure classification for SkillMethod and SkillExecutor: direct callers receive exceptions, standard tool calling feeds back only designated recoverable failures, and step planning still terminates without a correction loop. Confirm failures remain failed in traces and cannot satisfy successful-result bindings. Verify terminal controls are not swallowed by the standard tool manager and that continuation uses existing quotas and deadlines.

Consumer-specific HTTP configuration, transport policies, and migration plans are outside this Framework design. They must not become Framework ticket requirements without a separately discussed general capability gap.

## Design handoff and completion requirements

The source review and discussion of all eight findings are complete. Their accepted directions are recorded in this document. Read repository instructions and the [framework feature design lens](../framework-feature-design-lens.md) alongside this design. Do not reopen accepted decisions without concrete contradictory evidence or an unmet requirement. No tickets, production changes, or commits are authorized by this document; wait for an explicit request for that next phase.

When planning is authorized, carry the accepted architecture, removal work, and verification obligations into the generated tickets. Preserve the explicit requirement to assess `CapabilityKind` after REST removal and remove it if it remains a one-to-one translation. Every necessary difference between SkillMethod and SkillExecutor must be identified and justified. Prefer more implementation work when it yields a simpler complete final Framework architecture. Intermediate broken builds are acceptable; no compatibility shims are required.

The eight findings are review topics, not a required eight-ticket decomposition. Organize eventual tickets around cohesive changes to the accepted final architecture and explicit dependencies. Shared owners often span several findings; do not split tickets in a way that creates duplicate implementations, temporary adapters, or separate executor subsystems. Make coverage of every accepted finding and its verification obligations traceable across the resulting tickets, including removal work and cross-component completion. Recheck relevant source and repository ticket conventions before writing tickets; the design records accepted behavior and architecture, not a frozen inventory of implementation classes.

Keep three efforts distinct: this enhancement, [Planner Failure Recovery](planner-failure-recovery.md), and Sidecar's HTTP executor reimplementation. Read the follow-up only to understand scope boundaries. Sidecar notes live at `C:/opendev/code/loomspan-sidecar/ai/thoughts/phases/http-skill-executor-design-notes.md`; they are consumer context, not additional Framework requirements. Framework completion includes REST and `SkillKind.REST` removal and deliberate standard-tool-manager failure classification, but not new step recovery loops or planning-skill inability outcomes.

Completion requires coherent changes across discovery/proxies, manifests, namespace configuration and retained admission, authorization, catalog/routing, tool failure handling, step termination, quotas, traces/Console, supported API allowlists, tests, and consumer documentation. Use shared tests for both Java forms plus boundary integration tests for actual Spring proxies, standard tool-manager processing, retained foreign generations, and Console contracts. The verification paragraphs in each accepted finding are obligations for the relevant implementation work, not separate subsystems to build.

Remaining implementation details include exact internal types/decomposition, diagnostic field names and feedback transport, actual infrastructure-wrapper coverage, startup-ingestion mechanics that preserve the configuration contract, and test organization. These choices must implement the accepted semantic ownership and boundaries. If new evidence requires a genuine product decision or additional subsystem, present the concrete gap and discuss that decision one at a time; do not silently expand scope.

Important clarifications to retain:

- Application beans own creation/reuse/teardown of connection pools and other resources, including changes driven by executor configuration generations. The captured generation ID is a useful key, not a Framework promise to manage those resources. Executor beans or collaborating resource-management beans may inject the public `SkillReloader` API and register `onGenerationRetired(...)`, matching callback IDs against `executorConfigGenerationId()`. Register listeners early, keep callbacks prompt, and arrange application-owned cleanup. Notifications may be omitted during shutdown, so application beans also own shutdown cleanup. No new lifecycle hook or invocation accessor is selected.
- Existing `ref://` resolution can yield Spring `Resource` file inputs; preserving a resource handle does not freeze or duplicate its underlying bytes. Such handles arise from relevant input values, not automatic exposure of connection pools, sessions, or runtime internals. `RestSkillInvocation` is baseline evidence for the container-copy semantics to extract before its removal; `DefaultRefResolver` and `SessionLocalVirtualFileSystem` supply the existing reference behavior.
- Configuration is general executor-owned data at either location. SQL, database selectors, connection settings, and HTTP are examples, not schema requirements or a division of responsibilities enforced by Framework.
- Avoid exposing unused object graphs or speculative metadata. The executor receives only its relevant input/configuration subtree and necessary trusted identity. No full configuration tree, admission snapshot, mutable session bag, or internal runtime object is selected.
- Consolidate SkillMethod invocation, RBAC, input/reference validation, namespace validation, execution limits, observations, and generation retention according to the accepted findings. Additional subsystems or lifecycles beyond those accepted directions require the evidence and discussion specified under Generality and implementation simplicity.

This review and acceptance discussion changed only this design document; no tickets were generated and no executor production implementation was performed. The source-review baseline ran 51 tests across `LoomspanPublicSurfaceArchitectureTest`, `SkillMethodBeanPostProcessorTest`, `SkillMethodTargetDiscoveryIntegrationTests`, `SkillReachableAdmissionTest`, `CanonicalToolCallingManagerTest`, and `JavaSkillAuthenticationScopeIntegrationTests`, all passing. Those tests verify pre-enhancement behavior, not executor readiness. Subsequent documentation-only edits were checked with `git diff --check`; no production tests were rerun for those edits.
