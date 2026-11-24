---
audience: loomspan-application-developer
status: development
applies_to: bundled-loomspan-revision
coverage: source-verified
---

# Complete namespace publication

Inject the framework-owned `SkillReloader`. Every `validate` and `prepare` takes
one namespace, the **complete** supplied `Collection<SkillDocument>`, and a
required `ExecutionConfiguration`. There are no configured-file, document-only,
patch, or implicit configuration-inheritance overloads. Files are a startup input;
hosts load any later documents themselves.

```java
ExecutionConfiguration configuration = new ExecutionConfiguration("loomspan: {}");
SkillValidationResult feedback = reloader.validate(
        "department", documents, configuration, NamespaceVisibility.PUBLIC);
if (feedback.valid()) {
    try (PreparedSkillUpdate update = reloader.prepare(
            "department", documents, configuration, NamespaceVisibility.PUBLIC)) {
        configurationStore.stage(update.generationId(), resources);
        reloader.publish(update);
    }
}
SkillCatalog current = reloader.snapshot("department");
reloader.remove("department", current.generationId());
```

`NamespaceVisibility.PRIVATE` is the default on **every** publication, including
replacement of a Public generation. Pass `PUBLIC` explicitly on each Public
replacement. Visibility changes are complete publications and allocate fresh IDs.
Namespaces use their exact supplied spelling and the declaration component grammar.
Each skill pair satisfies `namespace.length() + 2 + localName.length() <= 64`.
Catalogs and validation feedback contain only the target's supplied declarations
and assigned fixed Java methods; catalog lookup accepts a local name and never searches another namespace.
Empty supplied declarations are valid when ordinary validation permits them, and
an empty published namespace still has its namespace and generation ID.

`SkillDocument.sourceName` is an exact diagnostic label. Given a complete valid
executor-backed definition in `yaml` and its executor registered in `orders`,
both labels below succeed without treating them as paths.

```java
ExecutionConfiguration empty = new ExecutionConfiguration("loomspan: {}");
for (String label : List.of("Orders import #42", "Orders/lookup.yaml")) {
    var documents = List.of(new SkillDocument(label, yaml));
    SkillValidationResult feedback = reloader.validate("orders", documents, empty);
    if (feedback.valid()) {
        try (PreparedSkillUpdate candidate = reloader.prepare("orders", documents, empty)) {
            candidate.snapshot();
        }
    }
}
```

The supplied namespace and declared name undergo exact identity validation.
Hosts loading definitions from files or archives check the actual paths before
constructing documents. Framework startup performs that check for its configured
locations. Under `classpath:/skills/**/*.yaml`, both `Orders/lookup.yaml` and
`orders/lookup.YAML` fail with their relative path and offending component.
For literal `classpath:/skills/Orders/lookup.yaml`, only `lookup.yaml` is relative.
Nested startup files still belong to `default`.

## Publication labels and current discovery

Supply the host's label with either labeled preparation overload. Visibility is explicit.

```java
try (PreparedSkillUpdate update = reloader.prepare(
        "department", "P7", documents, configuration, NamespaceVisibility.PUBLIC)) {
    SkillCatalog candidate = update.snapshot();
    candidate.publicationVersion(); // Optional.of("P7")
    reloader.publish(update);
}
SkillCatalog active = reloader.snapshot("department");
```

`prepare(namespace, publicationVersion, documents, configuration, visibility)` uses
normal `Environment.getProperty(name)` for variables. Add `variableValues` as the last argument to use a copied
explicit map with exact name matching. Null omits the label. Every supplied
string remains exact, including empty strings, whitespace and line breaks. The host
defines its meaning and whether equal labels identify equal complete content across engines.

The immutable candidate snapshot identifies its generation and label before publication.
The active snapshot changes both together only on successful publication. Abandonment,
failure and stale rejection leave the winning generation's pair intact. Every replacement
supplies its own optional label. Existing preparation methods omit it, so an unlabeled
replacement clears the prior label. Startup generations omit it. Reinstallation after
restart supplies it again and gets a new process-local ID. `generationId` continues to
control publication, stale checks, removal and retirement.

The authenticated engine `GET /_loomspan/observability/v1/instance` includes a
`namespaces` array with each installed namespace's current `namespace`, `generationId`
and nullable `publicationVersion`, including empty and Private namespaces before any
invocation. Console's selected-target `LOOMSPAN_get_instance` MCP tool and Instance
Overview show those facts on initial reads, refresh and reconnect. The UI marks null
as “Not supplied”. Observation uses its existing credentials independently of call
visibility. See [HTTP observation](../skill-authoring/observability-http.md).

## Complete configuration and variables

Only `loomspan.connections`, `loomspan.models`, `loomspan.session`, `loomspan.executor-config`, and
`loomspan.execution-trace.persistence` are publishable. Existing nested provider
options, request timeouts, retries, limits, quotas and attachments remain supported.
Host shutdown, startup skill locations, observability, other application settings
and unknown keys reject validation/preparation. Every omitted supported scalar uses
its built-in default; previous, startup and other namespace settings do not fill
missing values. Missing models/connections required by a declaration fail.

Connections accept literal `api-key`, `headers` and Vertex `gemini.credentials-json` contents. A whole-node `{ $var: name }` mapping supplies one string from the host. Markers participate under each connection, namespace executor data and executor-backed skill `config`, including mapping keys and sequence elements. Names are opaque, case-sensitive, nonblank strings without surrounding whitespace. A mapping with additional keys is ordinary data. Models, session settings, declarations and request inputs retain their own syntax.

`prepare(namespace, documents, configuration, variableValues)` copies the supplied map and uses only its exact entries. Missing or blank required values and unused entries fail. All three sections count toward usage, including names used only in skill config. An empty map accepts a candidate without markers. The full overload accepts visibility before the map. Without a map, every overload uses normal `Environment.getProperty(name)`. Packs cannot choose a source.

Validation checks authored structure, marker names, declarations, dependencies and every decidable literal constraint with zero lookups, provider constructions or application calls. An unresolved key defers only configuration selection that depends on it. Preparation independently validates the documents passed to that call, resolves each distinct name once and checks all effective keys, typed values and dependencies before creating clients. It sends no model request. Resolution failures identify authored source paths and fixed reasons.

Each returned string is terminal. An explicit `${region}` remains those exact characters in provider and executor settings. A host property `report.label=${region}` can yield `west` through normal Environment lookup. Returned multiline JSON and YAML-looking strings retain their contents. Native typed conversion and provider authentication validation still apply. Every new preparation resolves freshly, including one with the same documents and publication label. Publication performs zero lookups and activates the captured candidate.

`loomspan.executor-config` uses namespace-indexed maps and permits only the publication target, including empty entries. Omission supplies empty data. Nested maps/lists, null leaves, empty containers, scalar types and exact case/dotted data keys are preserved. Ordinary application keys such as `api-key-ref` remain data. The provider schema accepts ordinary setting names with literals or markers. Each generation owns immutable namespace and skill settings. Admission captures the executor owner's `executorConfig()`, the skill owner's `consumerExecutorConfig()` and its `skillConfig()` from their respective namespaces. Executor domain checks remain at invocation. See [executor configuration](../skill-authoring/executor-skills.md).

## Capture, concurrency and one-shot candidates

Before expensive preparation, Loomspan captures the target's active generation ID
or strict absence, together with one coherent immutable external metadata view.
There is no caller-supplied preparation revision or public base accessor. The
candidate receives a new process-local opaque ID that differs from its base;
allocation order need not equal publication order. IDs never encode skill identity
and are not durable restoration identifiers.

Every candidate skill and outgoing callable/executor reference is validated against that
view. Preparation retains no foreign runtime lease and stores no foreign executor configuration or policy in the caller generation. All references into the candidate namespace, including transitive return
paths, use its complete candidate declarations. Omitted old skills are unavailable.
Foreign edges require a Public target, including a path returning into the
candidate; same-namespace composition can remain Private. Existing contracts,
bindings and forwarding/schema cycle checks still apply. Incoming/unreachable
callers are not scanned.

Same-target and different-target preparations construct detached candidates
concurrently. Each owns its definitions, diagnostics, configuration, credentials,
derived contracts and runtime. Publication checks framework ownership, candidate
state, lifecycle and **only the target's captured base/absence**, then atomically
installs it. Dependency revision, deletion, visibility or recreation changes alone
never make publication stale. There is no dependency revalidation or automatic
retry at publication. Installation does not prove current dependency executability;
every fresh root admission captures and validates its declared reachable graph.
Temporary `SkillTemplate.validate` checks release their ownership immediately and
reserve no execution; `SkillInvocationHandoff` retains an admitted snapshot until
its single execution or explicit release.

A target change after capture rejects the candidate as stale. A change before
capture becomes its base, regardless of a prior catalog read. Competing candidates
for a continuously present target have one winner. A null base means absent,
never wildcard: an absence candidate can publish if that target is absent again.
A candidate based on a withdrawn incarnation remains stale after recreation.

Close abandoned candidates. Failed construction, stale publication, or shutdown
rejection disposes only that candidate. Foreign objects are rejected without
closing them. Already published candidates cannot publish again; closing/rejecting
one does not close its installed generation. Shutdown checks occur before and
after construction and at publication; slow construction is not forcibly interrupted.

## Startup, fixed Java and removal

Startup builds Private `default` from configured declarations plus its assigned
Java methods, and a separate Private generation for each other Java assignment,
containing only those methods. All use the same startup settings with separate
runtimes. Same-namespace local or qualified startup composition works directly. Supplied authored local references resolve against the validation/preparation owner before dependency checks; publishing the same document in another owner changes those local targets without rewriting source bytes. Foreign
Private startup references fail; hosts publish Public shared capabilities before
publishing their callers. Managed hosts can start without local files and own
readiness and ordering. Java direct entry is available immediately after startup;
there is no activation publication or per-namespace bean lifecycle.

Fixed Java registrations are included in every generation of their assigned
namespace. Supplied documents cannot override, omit, relocate, copy or configure
them. Java bean state remains host-owned. Annotation changes need redeployment.
An empty supplied collection removes previous supplied declarations while retaining
fixed methods; it does not merge prior supplied content.

`remove(namespace, expectedGenerationId)` rejects a missing/wrong active ID,
withdraws atomically and does not wait for retained work to drain. Existing owners
can finish; new lookup fails without implicit recreation. Explicit recreation
requires complete inputs, includes assigned fixed Java methods and allocates a fresh
ID. Removal does not destroy beans or cascade into dependent publications.
Public-to-Private changes are likewise prospective. Restart rebuilds Private startup
state; runtime publications/removals are not restored. Hosts repeat their managed
sequence and stage resources using the new process IDs.

Ordinary cycles can use real valid staged publications: Public `a` already contains
the contract called by `b`, then Public `b -> a`, then replacement Public `a -> b`.
All intermediate versions must be callable and valid; unresolved stubs and invalid
schema/forwarding cycles are unsupported.

## Managed hosting boundaries

An embedded startup caller and its fixed Java children in `default` can execute
without a publication. For file-free managed hosting, publish the Java target's
namespace with empty supplied documents, complete configuration and explicit
`PUBLIC`, then publish the caller's complete documents/configuration through the
same API. Namespace names are host choices. A Java-only target can use
`new ExecutionConfiguration("loomspan: {}")`; the model-backed caller supplies its
own complete connections/models, including explicit variable markers. Pass
in-memory `SkillDocument` values to `prepare` rather than configuring startup files.
See [Java declarations](java-skills.md) and [model configuration](../skill-authoring/model-selection-and-connections.md)
for the corresponding authored contracts.
A department can publish supplied declarations into its assigned Java namespace;
fixed methods remain and omitted supplied declarations disappear. On restart,
repeat the managed sequence from Private startup before advertising readiness.

The host authorizes management and routes external entry requests. Namespaces do
not establish complete tenant isolation: credentials, database permissions,
mutable Java state and resource consumption need host/application controls.
There is no prescribed publisher role, namespace membership, reserved shared
namespace or app/environment mapping. Cloud hosts Framework directly; sidecar
hosting examples are optional and select neither a dependency nor Cloud auth.
Drainage, version admission and repository integration
remain host work. Public call visibility grants neither configuration nor
observation access. See [observation](observation-and-errors.md#captured-namespace-identity)
for entry-versus-target generation facts.

## Retirement notifications

Register framework-wide `onGenerationRetired(listener)` before staging resources.
Superseded/removed generations retire when existing invocation and physical-work
ownership reaches zero. Retained catalogs are metadata, not resource leases.
Callbacks and resource closing run outside registry/lifecycle synchronization.
Listeners may run concurrently, must be thread-safe and should return promptly;
schedule slow application cleanup elsewhere. Listener failures are isolated. Closing
a registration prevents future selection without waiting for an already selected
callback. There is no replay, ordering, retry or durable delivery guarantee;
notifications during shutdown may be omitted. Never-published candidates get no
retirement callback. There is no historical invocation API.

### Executor-owned application resources

An executor bean or a collaborating application resource manager MAY inject
`SkillReloader` and register this existing listener early, before staging the
initial resources. Match callback IDs against the
v1 `SkillExecutorInvocation.executorConfigGenerationId()` used to select those
resources. This is the captured executor namespace generation ID even when the
annotation has no configuration path; it can differ from the YAML skill's or
entry generation. Retained invocations keep that executor generation alive
across replacement and removal.

The ID is a useful application key, not a promise that Loomspan creates,
refreshes, caches or closes pools, clients or other application resources. The
application must coordinate resource creation and retirement itself; a listener
registered after retirement has no replay. Keep the registration handle for the
application lifetime, use thread-safe bookkeeping, and dispatch slow cleanup
off the callback thread. Application beans also own shutdown cleanup because
shutdown notifications may be omitted. Do not rely on callback ordering,
delivery retries or IDs surviving a process restart.

The executor quickstart (`docs/quickstart-executors.md` in the matching framework checkout) demonstrates
ordinary Spring-owned HTTP resource cleanup without a generation-specific cache.
`ExecutorGenerationExecutionIntegrationTest` covers captured executor IDs,
retained work, retirement notifications and preserved bean ownership.

## Evidence

- `StartupSkillPathTest`: caller spelling before lookup and actual filesystem, exploded classpath, all-classpath and JAR path validation. `SkillSourcePathResolverTest` protects shared source paths and exact labels/bytes.
- `PortableDeclarationIdentityIntegrationTest#suppliedLabelsRemainExactThroughValidationAndPreparation`: both labels through public validation and both variable-source preparation routes. `SkillIdentityTest` protects exact declaration components and pair limits.

- `SkillReloaderTest`: exact immutable labels, variable source modes, concurrent ID/label pairing, complete replacement, base/absence, state/lifecycle, deterministic concurrent preparation/publication, disposal and retirement.
- `NamespacePublicationObservationIntegrationTest`: independent engines, before-invocation reads, candidate/active separation, exact labels, omission, stale rejection, removal and restart installation.
- `SkillDependencyValidatorTest`: candidate overlay, transitive contracts/visibility, captured metadata retirement and coherent capture races.
- `NamespaceStartupIntegrationTest`: actual embedded and file-free managed caller execution, explicit Public sharing, prospective visibility changes, departmental complete replacement retaining fixed Java, removal/recreation and Private restart.
- `SkillDependencyValidatorTest#transitiveReturnUsesCandidateDeclarationsContractsAndVisibility`: staged ordinary cycles with candidate return paths and unchanged forwarding-cycle rejection.
- `ExecutionConfigurationParserTest`: exact configuration/variable boundary and nested validation.
- `PreparedCandidateVariablesTest`: all overloads, exact maps, once-per-name resolution, staged selectors, candidate edits, key collisions and terminal strings.
- `ExecutorGenerationExecutionIntegrationTest`: actual worked-corpus consumers and retained owner/consumer/leaf settings across rotation.
- `LoomspanPublicSurfaceArchitectureTest`: closed supported API and absence of extension SPIs.

## Publish consumer executor settings

With the complete billing documents in `billingDocuments`, publish only billing.
Keep billing Public when foreign parents invoke its leaves; every publication
defaults to Private when visibility is omitted:

```java
var config = new ExecutionConfiguration("""
    loomspan:
      executor-config:
        billing:
          typesafe:
            api_key: example-billing-v2
    """);
try (var update = reloader.prepare("billing", billingDocuments, config, NamespaceVisibility.PUBLIC)) {
    reloader.publish(update);
}
```

Fresh billing leaf calls, including those reached through another parent, use the
replacement consumer map. Omission clears it. Foreign namespace entries are
rejected even if empty. Retained handoffs keep both original maps and
`executorConfigGenerationId()` still identifies the captured executor owner.
`ExecutorGenerationExecutionIntegrationTest` covers rotation, omission, independent
consumers and retained public handoff. See [executor configuration](../skill-authoring/executor-skills.md).
