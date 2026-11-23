# Loomspan Framework

Loomspan brings AI reasoning into applications through reusable **skills**. A skill
can reason with a language model, call an annotated
Java service, or execute a YAML-configured operation through an application-owned
Java executor. Compose those capabilities into a
hierarchy so a model can break down a task while your application controls which
operations it can use.

For example, an invoice workflow could extract invoice details, look up existing
expenses through your application, and explain whether an invoice looks like a
duplicate.

## Why Loomspan?

- **Combine reasoning with application code.** Use model-backed YAML skills,
  executor-backed operations and annotated Java methods in the same skill tree.
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

Authored skill input/output schemas use a supported subset of JSON Schema Draft
2020-12 with exact names and numeric values, explicit nullable type arrays, open
objects by default, and locally asserted standard formats. Read
[standard schema declarations](agent-skills/loomspan-docs/references/skill-authoring/standard-schemas.md)
for vocabulary, annotations and diagnostics. The same canonical contracts drive
input validation, generated Java declarations, binding and local output acceptance.
Native provider restrictions and known adapter limitations are documented in
[output contracts](agent-skills/loomspan-docs/references/skill-authoring/output-contracts.md#native-output-mapping)
and [model connections](agent-skills/loomspan-docs/references/skill-authoring/model-selection-and-connections.md).
Local validation does not establish hosted schema enforcement or model quality.

## From customer report to engineering triage

Turn an unstructured support request into an assessment of impact, possible causes,
and concrete investigation steps. Start with one model-backed YAML skill:

```yaml
name: triage-support-request
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
String triage = skills.invoke("default:triage-support-request", Map.of("customerMessage", """
        Since this morning's deployment, checkout times out after payment.
        Three customers say they were charged but received no order confirmation.
        Retrying sometimes creates two orders. Browsing and cart updates still work.
        """));
```

This illustrates the declaration and call; the `assistant` model alias still
needs configuration. The [complete Java quickstart](docs/quickstart-java.md)
provides the dependency, configuration, file locations, and runnable application.
For application-owned HTTP and SQL-shaped execution without a model provider,
run the [executor quickstart](docs/quickstart-executors.md). Resource creation and
cleanup remain application-owned; see [executor resource retirement](agent-skills/loomspan-docs/references/java-api/skill-reload.md#executor-owned-application-resources).
The brief gives an engineer a starting point: reported payment/order mismatches,
a possible connection to the deployment, and checks needed to establish the cause.
To investigate beyond the supplied message, expose order lookups or deployment
history as Java skills and let a planning parent call those allowed
capabilities. See the [skill-tree guide](agent-skills/loomspan-docs/references/skill-authoring/mental-model.md).

When one direct child supplies the complete answer, an explicit model planner
can use `output_from: {skill: default-finish}` with a required, unique `finish` task. It
still completes all accepted work, then returns that child's existing text
unchanged without a parent synthesis request. See [output contracts](agent-skills/loomspan-docs/references/skill-authoring/output-contracts.md)
for syntax, schema ownership, and model/Java/executor examples.

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

Declarations use exact namespace and local-name components. Each component has
1–64 ASCII characters matching `[a-z][a-z0-9]*(?:-[a-z0-9]+)*` and excludes the
substrings `anthropic` and `claude`. The pair must satisfy
`namespace.length() + 2 + localName.length() <= 64`. In `order-support`, a
49-character local name succeeds and a 50-character name fails for both callable
skills and reference-only executors. Qualified identities render as `namespace:local-name`.

Startup YAML belongs to `default`, including nested files. Supplied documents
belong to the validation/preparation namespace. Both Java annotations default to
`default` and use an explicit nonempty `name` verbatim, otherwise the Java method
name verbatim. `search()` can omit its name. A method `findOrder()` supplies
`name = "find-order"` or fails startup with correction guidance. Components are
validated as supplied. Callable pairs must be unique across YAML and Java, while
executor pairs have their own index. A callable and executor may share one pair.

Authored local references resolve in the containing owner and explicit colon
references select their stated owner. Missing local targets fail in that owner.
Reference comparisons use the resolved identity and retain the original source,
prompts and evidence text. Runtime identity keys use the colon-qualified pair.

Inject `SkillReloader` and acquire `SkillCatalog catalog = reloader.snapshot("default")`.
There is no injected startup catalog or no-argument acquisition. `namespace()` and
`generationId()` identify the catalog's owner and opaque generation, even when empty.
`publicationVersion()` returns its optional host-supplied publication label.
Descriptors and validation feedback carry qualified names. `SkillCatalog.skill(...)` requires qualification and rejects local or malformed
references and returns empty for valid unknown or foreign identities. Retained
catalogs are immutable, sorted, unfiltered metadata with no resource lease or
execution reservation. Pass `descriptor.name()` directly to invocation/validation
or handoff; the catalog does not authorize or pin that later admission.

Every root invocation and handoff freshly captures exactly the namespace generations
reached by the entry's declared child graph and its executor dependencies, including
optional and role-restricted branches. Retaining an executor namespace does not
traverse its unrelated skills or grant callable authority. Missing dependencies, foreign Private targets, invalid bindings, or changed
prepared forwarding contracts reject before execution. Unrelated broken skills do not
block entry. Entry RBAC is checked without eagerly authorizing children; Private permits
authorized direct host entry. Retained work keeps its captured generations and visibility
across replacement, removal, and recreation. Handoff's `generationId()` identifies only
the entry generation and has no ownership side effects.

`SkillTemplate.validate()` checks the same captured dependency policy, input, and entry
RBAC with temporary leases, then releases them. For executor leaves, entry RBAC requires
both YAML and captured Java executor policies. It creates no session, invokes no
skill/provider, and reserves no root. Later invocation can fail after dependency,
authentication, or lifecycle changes; use handoff when reserved admission is needed.
Admission leases already constructed runtimes. It is not a transaction over external
systems. Each dispatch independently enforces the executing source's declared children,
same-namespace or captured Public visibility, and target RBAC. Retaining a generation
does not authorize its other skills. A Public child cannot call back into a Private
entry namespace. Public skill-call visibility grants no configuration or observation access.

Children execute with their captured target definitions, model/connection credentials,
mission timeout and attachment limits. One session keeps the entry's quotas, depth and
trace policy; child usage consumes that entry budget. Parent timeout remains active
while a child uses its own timeout. Managed workers preserve caller identity through
standard Spring Security and restore the parent context after success or failure.
See [execution limits](agent-skills/loomspan-docs/references/skill-authoring/execution-limits.md).

A prepared update's ID and snapshot identify its candidate, not its base.
Validation allocates no generation and errors leave candidate metadata incomplete.
Changing a namespace name means creating another namespace, updating references
explicitly, then removing the old namespace; no rename or inheritance is provided.

Publish complete namespace replacements with required configuration:

```java
ExecutionConfiguration config = new ExecutionConfiguration("loomspan: {}");
try (PreparedSkillUpdate candidate = reloader.prepare(
        "department", completeDocuments, config, NamespaceVisibility.PUBLIC)) {
    reloader.publish(candidate);
}
SkillCatalog current = reloader.snapshot("department");
reloader.remove("department", current.generationId());
```

The labeled preparation overload accepts the host's `publicationVersion` next to
the namespace and requires explicit visibility. Add `variableValues` after visibility
to use a copied explicit map.

```java
try (PreparedSkillUpdate candidate = reloader.prepare(
        "department", "P7", completeDocuments, config, NamespaceVisibility.PUBLIC)) {
    candidate.snapshot().publicationVersion(); // Optional.of("P7") on the candidate
    reloader.publish(candidate);
}
SkillCatalog active = reloader.snapshot("department");
```

The host defines the label's meaning, including whether equal labels identify equal
complete content across engines. Every supplied string remains exact, including empty
and whitespace-only values. The immutable candidate snapshot describes it immediately.
Active snapshots expose the new generation ID and label together after successful
publication. Each replacement supplies its own optional label. A null label or an
existing preparation signature omits it, including when replacing a labeled generation.
`generationId` continues to control publication, stale checks, removal and retirement.
After restart, the host reinstalls the namespace and supplies its label again.

Authenticated `GET /_loomspan/observability/v1/instance` returns every installed
namespace in `namespaces`, including empty and Private namespaces, with `namespace`,
`generationId` and nullable `publicationVersion` before any invocation. Console MCP
clients use `LOOMSPAN_get_instance` for the selected target. Operators see these rows
in Instance Overview on connection, refresh, reconnect and reopening. The UI displays
“Not supplied” for omission. See [HTTP observation](agent-skills/loomspan-docs/references/skill-authoring/observability-http.md)
for authentication and the current response shape.

`NamespaceVisibility` is the supported `PRIVATE`/`PUBLIC` enum. Omitting visibility
always selects Private, including replacement. Each publication has complete supplied
declarations and execution configuration; omitted settings use built-in defaults,
with no inheritance of models, connections or previous settings. Publishable keys are
`loomspan.connections`, `loomspan.models`, `loomspan.session`, `loomspan.executor-config`, and
`loomspan.execution-trace.persistence`; host and unknown settings fail validation.
Whole-node `{ $var: name }` markers resolve once per published candidate across provider settings, namespace executor data and executor-backed skill `config`. Every no-map preparation uses normal `Environment.getProperty(name)`. Explicit `variableValues` maps use exact names, require every used value and reject unused entries. Returned strings remain terminal, including `${...}` and multiline JSON. Staged validation needs no values. Preparation independently resolves and captures its supplied documents, and publication performs zero lookups. See the [publication guide](agent-skills/loomspan-docs/references/java-api/skill-reload.md).

Startup accepts the same markers in effective connections, namespace executor settings and configured executor-backed skill `config`. Spring profile, import and property-source precedence select effective settings before lookup. Connections inherit individual fields and the winning executor section replaces lower sections. Each distinct name uses one normal Environment lookup across these consumers. Inactive and overridden markers cause zero lookups. Authored `${...}` and returned strings remain literal in these selected subtrees. Native typed conversion and provider validation run before clients are built, and startup generations capture the resolved maps. See the [connection guide](agent-skills/loomspan-docs/references/skill-authoring/model-selection-and-connections.md).

Executor leaf declarations use `executor:` and optional map-valued `config:`.
An optional `input_transform:` uses JSLT to produce the complete Java input object
after ordinary input binding, validation and reference resolution. Callers and
planners continue to use the leaf's `input_schema`.
An optional `output_schema` validates the raw result or the result of an
`output_transform` once before publication. Each leaf owns its object contract.
Schema mismatch fails the leaf after executor work, with zero validation retries.
See [executor result contracts](agent-skills/loomspan-docs/references/skill-authoring/executor-skills.md#validate-the-published-executor-result).
`rest:` is unsupported syntax. `loomspan.executor-config` contains namespace-owned
maps: startup YAML can contain multiple namespaces, while a publication accepts
only its target namespace. Omission is empty with no inheritance. Nested nulls,
empty containers, scalar types and exact case/dotted keys survive YAML loading;
executor data is uninterpreted and separate from skill config.

The v1 invocation supplies separate `executorConfig()` (owner),
`consumerExecutorConfig()` (executing YAML leaf namespace) and `skillConfig()`
(leaf) maps. Foreign executors select the same exact dot path in both namespaces.
Owner selection is required; missing consumer keys yield an empty map. Present
null, scalar or list path nodes fail. Same-namespace execution and no selector
also yield an empty consumer map. Containers are recursively copied and
unmodifiable; other leaves retain identity. There is no automatic merge.
A support parent reaching a billing leaf receives billing's settings. Complete
billing publication rotates fresh work; retained handoffs keep both captured maps.
`executorConfigGenerationId()` remains the owner's ID. See the
[shared TypeSafe example](agent-skills/loomspan-docs/references/skill-authoring/typesafe-skills.md).

Executor leaves execute as `SkillKind.JAVA` through captured namespace generations.
The executor namespace owns its fixed registrations and immutable configuration;
local references use the YAML owner and foreign references require a Public target.
Validation, planner filtering and execution require both the YAML and captured Java
executor policies against the same caller; role alternatives remain within each policy. See
[executor authoring](agent-skills/loomspan-docs/references/skill-authoring/executor-skills.md).

Preparation validates outgoing contracts against one coherent external metadata view,
with references returning to the target resolved against its candidate. Foreign
targets must be Public. Detached preparations run concurrently; publication compares
only the target's once-captured base/absence. External dependency changes do not
make it stale. Close abandoned candidates. Removal requires the expected active ID,
withdraws without drainage, and never triggers lookup-driven recreation.

Startup creates Private default and assigned Java namespaces with separate runtimes.
Fixed Java methods remain in every generation of their namespace and are immediately
available for direct entry; removal preserves beans and retained work. Restart rebuilds
Private startup state. Hosts authorize management/routing and own readiness/sequencing.
Credentials, database permissions, mutable Java state and resource consumption
still need host/application controls; namespaces do not establish complete tenant
isolation. See the [publication guide](agent-skills/loomspan-docs/references/java-api/skill-reload.md).

### Deployment patterns

An embedded application can load `default-parent` at startup with an
`allowed_skills` reference to its fixed `default-local` Java method. Both belong
to the same Private namespace and can execute immediately, without publication.
Foreign startup references cannot bypass Private visibility. For managed sharing,
start without local skill or execution-configuration files, publish the fixed
Java namespace as Public, then supply the caller's documents and configuration
through `SkillReloader`. For example, given this method on an application bean:

```java
@SkillMethod(namespace = "utility", name = "echo", description = "Echo a value")
public String echo(@SkillParam(description = "Value") String value) {
    return value;
}
```

The host can publish these complete managed generations with an injected
`SkillReloader`; `providerKey` comes from its own credential source:

```java
try (PreparedSkillUpdate utility = reloader.prepare("utility", List.of(),
        new ExecutionConfiguration("loomspan: {}"), NamespaceVisibility.PUBLIC)) {
    reloader.publish(utility); // Retains utility-echo from the application bean.
}
SkillDocument caller = new SkillDocument("assistant", """
        name: assistant
        description: Echo using the shared Java capability
        model: assistant
        allowed_skills: [{name: utility-echo}]
        """);
ExecutionConfiguration execution = new ExecutionConfiguration("""
        loomspan:
          connections:
            chat:
              driver: openai
              base-url: https://api.openai.com/v1
              api-key: { $var: provider-key }
          models:
            assistant: {connection: chat, provider-model: gpt-4.1-mini}
        """);
try (PreparedSkillUpdate department = reloader.prepare("department", List.of(caller),
        execution, Map.of("provider-key", providerKey))) {
    reloader.publish(department);
}
// An injected SkillTemplate now admits department-assistant freshly on each call.
```

The model alias and provider model are illustrative host choices. A department's
later complete publication automatically retains its own fixed Java methods;
omitted supplied declarations disappear. Sharing requires explicit `PUBLIC` on
every replacement. Restart returns to Private startup generations, without restored
runtime publications, so the host repeats the managed sequence before accepting
requests. `NamespaceStartupIntegrationTest` executes both embedded and file-free
managed callers using a deterministic provider fixture and verifies restart,
complete replacement, fixed methods, and removal/recreation.

For a cyclic dependency, first publish a real valid Public `a` without its foreign
edge, then valid Public `b -> a`, then replace Public `a` with `a -> b`. Every
intermediate publication must be valid and callable; existing forwarding/schema
cycle validation and recursion limits still apply. There is no atomic multi-namespace
bootstrap. Removal or Public-to-Private replacement affects fresh admission while
already admitted work retains its captured view. Dependent publications are not
removed automatically, and withdrawal does not drain work. Hosts own management
authorization, entry routing, readiness, drainage,
version admission and repository integration. Loomspan prescribes no publisher
role, namespace membership, reserved shared name, or app/environment mapping.

### Captured execution diagnostics

The host setting `loomspan.execution-trace.directory` selects the trace root and
defaults to `${java.io.tmpdir}/loomspan-traces`. Each completed trace has the path
`<root>/<canonical entry namespace>/<sessionId>.<traceId>.execution-trace.ndjson`.
During execution the filename has an additional `.in-progress` suffix; live
reads and journal projection use that active file. For retained traces, the
closed `TRACE_COMPLETED` append is followed by a same-directory atomic rename.
The completed name signals complete bytes, including for failed executions;
it does not imply success or continued artifact availability. Immediately
discarded traces are deleted under the active name. Grace-retained files use
the completed name and the original expiry deadline, with open artifact reader leases
deferring deletion. Trace-start `tracePath` records the initial active path;
snapshots and finalized descriptors follow successful publication. Interrupted
execution can leave an active file. Rename failure follows trace-finalization
error handling, advertises no finalized artifact and can leave active diagnostic
evidence. The filesystem must support atomic rename; there is no copy or
non-atomic fallback.
A configured value is the root itself. An execution starting in `finance` keeps
its complete trace under `finance`, including calls into other namespaces.
Namespace-published execution configuration can set trace persistence, while the
directory is host-controlled. Missing directories are created on write.
Administrators manage filesystem permissions, and existing directory permissions
are preserved. The recorded path identifies the selected file, subject to normal
persistence and retention rules. Filesystem write failures use existing error
handling without choosing another destination.

One invocation has one session, trace, canonical event sequence and completed
observer flow across all namespaces. Existing Console skill displays, lookups,
correlations and trace readers use complete qualified names, so `a-search` and
`b-search` remain distinct. Host inspection includes Private and restricted skill
metadata under existing observation authorization; runtime discovery remains
execution-permission filtered.

Trace start, live execution and finalized trace metadata retain `entryNamespace`
and `namespaceGenerationIds`, the captured namespace-to-generation map. Session
`generationId` always identifies the entry generation. A known record's
`executingNamespace`/`executingGenerationId` describe its executing owner, separately
from an executor invocation's captured configuration-owner ID. These facts remain accurate after
replacement, removal and recreation. Unknown-skill diagnostics retain unavailable
values, and routes naming model/planning operations keep their operation meaning.
Canonical prompts, inputs, outputs, tools, evidence and diagnostics are preserved;
only the existing derived journal/observer projection has its established
field-name redaction exception.

Console live connections and saved trace imports require an exact release match
for `consoleCompatibilityVersion`, derived from Maven `${project.version}` through
filtered `META-INF/loomspan-release.properties`. A different release is rejected.
Matching `development` markers permit normal validation but promise no compatibility
between development builds. There are no legacy readers, alternate protocol counters,
or historical readability guarantee.

## Choose your integration

TypeSafe Choice, Noul, and Score are available through the independently versioned
[Loomspan Decisions pack](../loomspan-extensions/extensions/decisions/README.md).
The local unpublished artifact is `ai.loomspan:loomspan-decisions:0.1.0-SNAPSHOT`.
It registers reference-only `default-typesafe`. Applications declare ordinary
`executor: typesafe` YAML leaves and pass a native request directly or build it
with JSLT. They supply resolved connection settings in the captured executor
namespace map.
These leaves execute as `JAVA`. With `output_transform` omitted, they publish
the complete provider JSON and parents can bind answers through `/answers/...`.
To publish a skill-owned JSON shape, use the
[executor output transform](agent-skills/loomspan-docs/references/skill-authoring/executor-skills.md#publish-a-transformed-executor-result).
Domain validation occurs at invocation, and no generated
output schema or early provider-domain checks are supplied. Pack requests/retries
are outside Framework provider-attempt quotas and Framework-accounted usage;
cancellation is cooperative. See the [TypeSafe pack reference](agent-skills/loomspan-docs/references/skill-authoring/typesafe-skills.md)
for a declaration, configuration and the accepted numeric/lifecycle limits.

The pack's [tested-build reference](../loomspan-extensions/extensions/decisions/docs/tested-builds.md)
describes installed-artifact checks for concurrent publication, cooperative
lifecycle, resource ownership, accounting and optional Console/MCP trace inspection.
Its [runnable recipe](../loomspan-extensions/extensions/decisions/examples/support-triage/README.md)
includes local installation and deterministic verification without real credentials.

- **Embedded Java:** add the Spring Boot starter and invoke skills in your
  application. This revision targets Java 21+, Spring Boot 4.2.0-RC2, and Spring AI 2.1.0-RC2;
  building with Maven requires 3.9+. Install the pinned JSLT artifact into the local
  Maven repository before building this checkout or a consuming project.
  [Build the JSLT dependency](docs/building-jslt.md)
- **[Sidecar](https://github.com/loomspan/loomspan-sidecar):** use the separate
  Loomspan HTTP service from applications in other languages or when you want a
  separate runtime. Its repository covers setup, versions, and integration guidance.
  Cloud hosts Framework directly; sidecar examples do not make sidecar a runtime
  dependency or select a Cloud authentication scheme.

Model-backed skills need a named connection using the `openai`, `anthropic`, or `gemini` driver.
The `openai` driver uses stateless Responses for OpenAI, Meta, OpenRouter and Ollama.
Services must support `/responses`. For local Ollama, use an explicit root such as
`http://127.0.0.1:11434/v1` and `api-key: ollama`; authenticated deployments use
their actual credentials. Java and executor leaves can execute without a Framework model call.

Loomspan is **in development**, with no deployments. Supported Java APIs and
documented configuration can change, including breaking changes. Use documentation
from the same revision as the runtime. The first production release will be `1.0.0`.

## Explore further

| Goal | Start here |
| --- | --- |
| Understand the architecture and adoption tradeoffs | [Architecture](docs/architecture.md) |
| Set up a runtime and your assistant | [Setup](docs/setup.md) |
| Run your first embedded skill | [Java quickstart](docs/quickstart-java.md) |
| Run application-owned executors without a model provider | [Executor quickstart](docs/quickstart-executors.md) |
| Connect Console for AI-assisted debugging | [Console setup](docs/console-setup.md) |
| Design, develop, and troubleshoot application skills | [Skill-authoring knowledge set](agent-skills/loomspan-docs/references/skill-authoring/README.md) |
| Integrate invocation, validation, and runtime updates | [Java API knowledge set](agent-skills/loomspan-docs/references/java-api/README.md) |
| Find docs or understand their scope | [Documentation map](docs/README.md) |

The supported Java API is the closed allowlist in `ai.loomspan.api` and the exact v1 executor types in
`ai.loomspan.api.extensions.v1`, summarized
in the [public API reference](agent-skills/loomspan-docs/references/java-api/compatibility-and-boundaries.md).
It contains 30 public top-level types, including `SkillExecutor`, the
Framework-created `SkillExecutorInvocation` class and `ExecutorDiagnostics` capability, `RecoverableSkillException`, `PlannerInabilityException`, `SkillFailureDiagnosis`, `SkillContractStage`, and `SkillContractIssue`. Java callable
methods and reference-only executors share discovery and proxy invocation. Successful
null/void results become empty text, Strings remain exact, and other results become
JSON; failures propagate. Only designated `RecoverableSkillException` failures become
failed-call feedback in standard tool calling, after failed recording and with the
exact application message. Direct invocation and explicit steps still throw;
ordinary application wrappers remain terminal. Feedback earns no successful-result
credit and continuation uses existing budgets and the original parent timeout.
Executor methods accept exactly `ai.loomspan.api.extensions.v1.SkillExecutorInvocation`.
Its six captured business/configuration accessors retain copied map/list containers
and generation identity. `isCancelled()` reads live Framework cancellation;
`remainingTime()` reads the remaining execution budget, capped by ancestors and
shutdown, with empty for no deadline and zero at expiry. Executors should also
check thread interruption and bound their own I/O; these observations do not
abort remote work. `diagnostics()` supplies bounded invocation-scoped `info`, `warn`
and `error` message/JSON capture. Supported JSON is snapshotted during the call;
concurrent calls use existing trace ordering and retained handles stop recording
when executor work ends or its lifecycle is cut off. Ordinary diagnostic failures
are contained; caller argument evaluation and JVM-wide failures are excluded.
System notices are separate and best effort; severity never changes execution.
See the [executor API contract](agent-skills/loomspan-docs/references/java-api/java-skills.md#bounded-executor-diagnostics).

Failed direct and admitted invocations expose optional terminal contract detail
through `SkillException.getFailureDiagnosis()`, including input rejection during
handoff preparation. This works with diagnostics set to `NEVER` and without an
observer. Keep the requested root name separately from the diagnosis's canonical
receiving or publishing `skillName()`.

```java
try {
    return skills.invoke(requestedSkill, input);
} catch (SkillException failure) {
    failure.getFailureDiagnosis().ifPresent(diagnosis -> {
        logger.warn("Skill {} failed at {} with {}", diagnosis.skillName(),
                diagnosis.stage(), diagnosis.message());
    });
    throw failure;
}
```

The four stages are `INPUT_SCHEMA`, `INPUT_TRANSFORM`, `OUTPUT_TRANSFORM` and
`OUTPUT_SCHEMA`. Immutable schema issues expose `path`, `code` and `message`
from the rejecting validator. Paths are JSON Pointers, with `""` for the root
and `~0`/`~1` for literal tildes/slashes. Transform failures retain authored JSLT
errors and have empty issues. Existing `SkillInputValidationException` catches
and `getIssues()` remain usable. Absence means the selected terminal cause has
no diagnosis for these four stages. Corrected attempts, suppressed failures and
losing parallel branches cannot provide it. Cancellation and causal wrappers
follow canonical completion selection. Read the
[diagnosis contract](agent-skills/loomspan-docs/references/java-api/observation-and-errors.md#terminal-contract-diagnosis).

Canonical diagnostics record classification independently from completion linkage.

A standard model-backed invocation can end unsuccessfully by calling the Framework
control `loomspan_cannot_complete` alone with a nonblank `reason`. In explicit-step
planning, the model can declare `CANNOT_COMPLETE` with a reason during assigned-task
action generation or final synthesis. Explicit-step parents propagate the failure.
A standard caller receiving it may take a permitted action that addresses the
obstacle. Work may already have completed, and replay may be unsafe.
Direct and admitted Java callers can catch `PlannerInabilityException` and read the
exact reason through `getMessage()`. Existing `RecoverableSkillException` catches
also apply. Use `RecoverableSkillException` for ordinary Java operation correction.
Console shows the selected failure's origin, the accepted signal when captured,
and the parent request's recorded tool feedback. Java-thrown inability has the
same failure kind. Use recorded evidence to identify its source.
See [trace failure inspection](agent-skills/loomspan-docs/references/skill-authoring/traces-and-debugging.md#terminal-outcome-and-failures).

YAML executor leaves retain their definitions and use captured registrations,
selected executor configuration and executor generation identity during execution.
Validation and discovery evaluate declarative metadata without running argument-dependent
advice. Actual invocation supplies real arguments to Spring advice, which can still deny
a validated candidate; caller security contexts restore after success or failure.
See [Java declarations and results](agent-skills/loomspan-docs/references/java-api/java-skills.md).

The catalog descriptor is `SkillDescriptor(name, description, kind, inputSchema,
outputSchema)`; `SkillKind` includes `YAML` and `JAVA`.
Nullable `outputSchema` describes effective output shape, including
forwarding chains. Executor YAML leaves validate their declared output schema at
publication, and forwarding parents use the accepted result. Annotation-only Java
methods have no YAML output schema. The descriptor
has a five-argument constructor. Loomspan exposes no supported Java SPI. Types under `internal` and
`autoconfigure` are not application extension APIs; Loomspan does not expose an
internal bean-replacement contract.

Catalogs classify both annotated methods and executor-backed YAML as `JAVA`;
reference-only executors are absent from callable discovery. Console diagnostics
keep execution kind separate from declaration provenance, showing authored YAML
and source labels alongside executor identity and Java implementation. See
[skill diagnostics](agent-skills/loomspan-docs/references/skill-authoring/observability-http.md#skill-classification-and-declaration).

### Executor diagnostic inspection and usage scope

Framework-accounted usage covers native Framework model calls. Executor-managed
requests/retries do not reserve the native provider-attempt budget, and authored
usage-shaped diagnostic JSON does not contribute to these totals. Zero means zero
Framework-accounted usage, not measured zero executor usage. Preserve native usage
precision: a missing report can produce a heuristic estimate when text is available;
unavailable evidence remains unavailable. These totals are not total application
request counts or billing totals.

Select the recorded frame and query `LOOMSPAN_query_trace_records` with
`filter.types` containing `EXECUTOR_DIAGNOSTIC_RECORDED` and
`EXECUTOR_DIAGNOSTIC_SYSTEM_NOTICE_RECORDED`, plus `filter.frameId` when needed.
Both selectors apply normally, including for a child `ROOT_MISSION` frame; notices
do not bypass filters. Continue the unchanged query while `hasMore` is true.
Read selected DATA descriptors inline when complete, or follow their ordinary
`contentRef` through `LOOMSPAN_read_trace_content` ranges and continuations.
LOGICAL omits physical chunk rows. Each sequence remains an individual event.

Console Records exposes **Diagnostic details** for diagnostic and system-notice records with retained message/JSON, explicit capture states and recorded reasons.
Frame boundaries supply identity/configuration generation, timing and input;
recorded executor output or ordinary child tool results remain separate facts.
An authored ERROR, retry text or arbitrary `outcome` field supplies no invocation
failure or native attempt classification. Notices have no authored severity.
Missing evidence establishes no capture cause. A complete retained notice does not
recover discarded content. Use recorded Framework outcome/failure classification.
