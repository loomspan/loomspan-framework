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
Namespaces normalize case and independently enforce the 32-character limit.
Catalogs and validation feedback contain only the target's supplied declarations
and assigned fixed Java methods; qualified catalog lookup never searches elsewhere.
Empty supplied declarations are valid when ordinary validation permits them, and
an empty published namespace still has its namespace and generation ID.

## Complete configuration and credentials

Only `loomspan.connections`, `loomspan.models`, `loomspan.session`, and
`loomspan.execution-trace.persistence` are publishable. Existing nested provider
options, request timeouts, retries, limits, quotas and attachments remain supported.
Host shutdown, startup skill locations, observability, other application settings
and unknown keys reject validation/preparation. Every omitted supported scalar uses
its built-in default; previous, startup and other namespace settings do not fill
missing values. Missing models/connections required by a declaration fail.

Credentials use explicit `api-key-ref`, `header-refs`,
`gemini.credentials-ref` or `gemini.credentials-json-ref` references. Preparation
normally resolves them from Spring Environment. The complete overload
`prepare(namespace, documents, configuration, credentialValues)` copies a supplied
map and resolves every reference from it without Environment fallback. Missing,
blank or unused map entries fail. The full overload accepts visibility before the
credential map. Validation does not resolve credentials, construct provider clients,
instantiate a REST handler or invoke implementations. Preparation builds independent
clients but sends no model request. Diagnostic content is preserved.

## Capture, concurrency and one-shot candidates

Before expensive preparation, Loomspan captures the target's active generation ID
or strict absence, together with one coherent immutable external metadata view.
There is no caller-supplied preparation revision or public base accessor. The
candidate receives a new process-local opaque ID that differs from its base;
allocation order need not equal publication order. IDs never encode skill identity
and are not durable restoration identifiers.

Every candidate skill and outgoing reachable reference is validated against that
view. All references into the candidate namespace, including transitive return
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
reachable admission and cross-namespace dispatch integration follow in PRs 27.3–27.4.

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
runtimes. Same-namespace qualified startup composition works directly. Foreign
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

## Evidence

- `SkillReloaderTest`: complete replacement, base/absence, state/lifecycle, deterministic concurrent preparation/publication, disposal and retirement.
- `SkillDependencyValidatorTest`: candidate overlay, transitive contracts/visibility, captured metadata retirement and coherent capture races.
- `NamespaceStartupIntegrationTest`: Private startup, direct Java, managed no-file publication, removal/recreation and restart.
- `ExecutionConfigurationParserTest`: exact configuration/credential boundary and nested validation.
- `LoomspanPublicSurfaceArchitectureTest`: closed supported API and sole RestSkillHandler SPI.
