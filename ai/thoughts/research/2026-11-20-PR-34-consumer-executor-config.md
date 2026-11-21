---
date: 2026-11-20T20:20:02-08:00
researcher: Codex
git_commit: cd1c15237cb31770853dfb78a7399f1a4594dbd0
branch: main
repository: loomspan-framework
topic: "PR 34: namespace-owned consumer configuration for shared executors"
tags: [research, codebase, executors, namespaces, publication, typesafe]
status: complete
last_updated: 2026-11-20
last_updated_by: Codex
---

# Research: Namespace-owned consumer configuration for shared executors

**Date:** 2026-11-20T20:20:02-08:00  
**Researcher:** Codex (model identity: Unknown)  
**Framework:** `cd1c15237cb31770853dfb78a7399f1a4594dbd0`, branch `main`  
**Extensions:** `a6de3966e771a27683d9f6d8f4f91ba0006c3844`

## Research Question

Map the current Framework and sibling Decisions implementation relevant to `ai/thoughts/tickets/loomspan-pr-34-consumer-executor-config.md`: independent owner/consumer/leaf configuration, reachable generation capture, complete namespace publication, immutable invocation data, and TypeSafe endpoint/key/retry policy.

The ticket is the governing scope. The developer selected the Full 5-Step Pipeline. Its Pipeline notes authorize coordinated breaking changes, direct consumer updates, unchanged existing artifact versions and v1 package, and no compatibility shims. Existing Framework prior-ticket artifact deletions and Extensions `extensions/decisions/CHANGELOG.md` deletion are unrelated work to preserve.

## Summary

The current v1 invocation exposes business input, leaf configuration and selected executor-owner configuration. It has no consumer configuration accessor. Each captured namespace generation already stores immutable executor configuration, and root admission retains both every reachable YAML leaf generation and its executor-owner generation. Binding currently selects only the owner subtree and owner generation ID.

Namespace publication already parses the existing `loomspan.executor-config.<namespace>` layout, prohibits foreign namespace entries, replaces the target generation completely, and uses empty configuration when omitted. Selection currently has mandatory owner dot-path semantics. Framework does not interpret domain keys or merge configuration maps.

The Decisions pack registers `default-typesafe` with `config = "typesafe"`. TypeSafe currently reads key and URL only from owner configuration; leaf `max_attempts` is already supported. Its real HTTP and installed-JAR consumer tests provide existing evidence for destination, exact credentials, retries, publication and retained work. One packaged TypeSafe guide statement about leaf config is stale, as detailed below.

## Research checklist

- [x] Read ticket, both repository policies, research/automation/documentation protocols and design lens.
- [x] Trace startup YAML ingestion, namespace publication and immutable configuration storage.
- [x] Trace dependency binding, reachable capture and actual invocation construction.
- [x] Inventory supported API, internal interfaces/constructors/beans, diagnostics and consumers.
- [x] Inspect TypeSafe request/settings/transport, tests, examples and build ownership.
- [x] Route version-aligned packaged documentation and classify drift.
- [x] Gather repository metadata and write this artifact.

## Detailed Findings

### Configuration ingestion and namespace ownership

- `src/main/java/ai/loomspan/internal/autoconfigure/ExecutorYamlPropertySourceLoader.java:17` implements the YAML PropertySourceLoader integration. It decodes source YAML before normal Spring scalar flattening and transports `loomspan.executor-config` as one raw value, retaining nested nulls, exact keys and scalar/container distinctions. Other properties use Boot's loader.
- `src/main/java/ai/loomspan/internal/autoconfigure/ExecutorConfigurationBinding.java:11` converts that raw value to `ExecutorConfiguration`. `src/main/java/ai/loomspan/autoconfigure/LoomspanProperties.java:32` stores it as startup binding machinery.
- `src/main/java/ai/loomspan/internal/skill/ExecutorConfiguration.java:16` requires a map of namespace maps, normalizes namespace identifiers, rejects duplicate normalized namespaces, requires textual top-level data keys and recursively copies containers. Arbitrary nested values remain domain data.
- `ExecutorConfiguration.java:35` enforces the publication target, rejecting any foreign entry, including empty maps. `src/main/java/ai/loomspan/internal/skill/ExecutionConfigurationParser.java:54` uses this same parser for publication; omission becomes `ExecutorConfiguration.empty()`.
- `src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java:95` initializes Private startup generations for `default` and fixed Java/executor assignments. Startup files belong to `default`; supplying config for another namespace does not itself install arbitrary nondefault YAML skills. Foreign shared executors require an explicit Public publication before caller publication. Startup may contain multiple namespace maps, but later publication supplies its complete target configuration rather than inheriting that startup map.
- `SkillGenerationManager.java:162` prepares complete candidate documents/configuration against a captured metadata view. Its `check` at line 190 passes only the candidate namespace's map to dependency validation. `prepareCatalog` at line 240 stores that namespace's map in the resulting generation.
- `src/main/java/ai/loomspan/internal/skill/SkillGeneration.java:75` makes an immutable container copy of the namespace configuration; line 117 exposes it internally. Replacement receives the newly supplied map. There is no separate consumer configuration root in the current configuration model.

Focused evidence: `ExecutorConfigurationTests`, `ExecutorConfigurationStartupTests`, `ExecutionConfigurationParserTest` and `ExecutorConfigurationManagerTests#validationAndBothPreparationParsePathsUsePublicationTargetWithoutApplicationCalls`. These cover raw startup fidelity, publication parsing, target enforcement, empty omission and no application validation calls. This step inspected their source; it did not run tests.

### Reference resolution and selected configuration

- `src/main/java/ai/loomspan/internal/skill/SkillDependencyValidator.java:141` builds a `NamespaceView` containing visibility, fixed executor registrations, immutable configuration and generation ID.
- `SkillDependencyValidator.java:155` resolves local executor references relative to `definition.identity().namespace()`, checks namespace presence and requires Public visibility for foreign references. Callable and executor indexes remain separate.
- `SkillDependencyValidator.java:165` resolves an executor for each reached YAML leaf. Current `ExecutorBinding` contains registration, selected owner map and owner generation ID only. It receives no consumer map.
- `SkillDependencyValidator.java:176` owns exact dot-path selection. An empty annotation selector produces an empty map. Whitespace padding, blank paths and empty dot segments fail. Segments match exact case-sensitive map keys. Missing, null, scalar, list and non-map intermediate values currently fail for owner selection; an explicitly empty selected map succeeds. Dotted keys inside the selected subtree remain ordinary data.
- `SkillDependencyValidator.java:70` validates every executor registration assigned to a candidate namespace, even when it has no callable leaf. `checkGraph` at line 100 also checks reached executor references. Application executor methods are not invoked by these checks.

`ExecutorDependencyResolutionTest:57` protects exact path behavior and `:82` protects local/qualified reference roles and Public/Private boundaries. Later methods protect retained bindings, candidate overlay return edges, owner-only reachability, capture concurrency and generation-specific authorization policy.

### Capture, publication and actual execution

- `src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java:325` captures one coherent active generation per needed namespace under `generationMonitor`. It traverses declared callable children and additionally retains the owner of each reached executor. It does not traverse unrelated skills merely because their namespace contains an executor.
- Every reached leaf's own generation is already included by callable traversal. Thus a support parent reaching a billing leaf captures support, billing and its executor owner as appropriate; the current binding does not use billing's executor data when the executor belongs elsewhere.
- `SkillGenerationManager.java:366` validates immutable admission metadata outside the registry lock. `SkillDependencyValidator.java:77` rebuilds reached bindings from those retained generations. Preparation-time foreign bindings are not pinned into the candidate as live runtime dependencies.
- `src/main/java/ai/loomspan/internal/skill/SkillAdmissionSnapshot.java:8` stores the authoritative captured generation association and leaf-name-to-executor binding map. `ExecutorBinding` at line 10 recursively freezes its current selected configuration map. Its `generationId` is the executor owner's ID.
- `src/main/java/ai/loomspan/internal/core/ExecutionCoordinator.java:149` records executor identity and owner config generation ID in the existing frame. At line 190 it invokes the captured registration with resolved/transformed business input, `definition.config()`, captured owner map, qualified leaf name and owner generation ID. There is no live configuration lookup in this route.
- `src/main/java/ai/loomspan/internal/core/ExecutorInvocations.java:20` finds the private invocation constructor using a method handle. Its `create` at line 32 is the single internal bridge used by production and many Framework tests.
- Publication atomically replaces only its target. Existing captures hold leases on their selected generations; replacement/removal is prospective, and retirement waits for retained ownership. The owner generation ID is independent of the leaf's namespace generation ID.

Focused evidence: `ExecutorGenerationExecutionIntegrationTest:25` compares startup and publication values; `:48` retains real handoffs across owner replacement/removal/recreation while preserving ordinary input, a Resource handle, leaf configuration, authentication and generation IDs; `:110` covers fresh visibility after preparation; `:164` covers retained secured executor calls. `NamespaceExecutionAuthorityIntegrationTest`, `SharedDeclarativeAuthorizationIntegrationTest`, `ExecutorApplicationIntegrationTest` and `ExecutorInputTransformIntegrationTest` cover real namespace authority, actual proxy advice, root/parent calls and business-input separation.

### Invocation API and container fidelity

`src/main/java/ai/loomspan/api/extensions/v1/SkillExecutorInvocation.java:17` is final and has no public constructor. Its current eight public methods are `input`, `skillConfig`, `executorConfig`, `skillName`, `executorConfigGenerationId`, `diagnostics`, `isCancelled` and `remainingTime`. Business maps and metadata are captured; lifecycle accessors remain live reads of the mission and ancestors.

`src/main/java/ai/loomspan/internal/util/ImmutableContainers.java:16` recursively copies map/list containers into unmodifiable containers. It retains nulls, map keys and non-container leaf identities, including Resources, arrays and arbitrary application values. Nested non-string/null keys are retained. Recursive container cycles fail. This is container immutability, not deep freezing of arbitrary objects or Resource bytes.

`src/test/java/ai/loomspan/api/ApplicationApiValueTest.java:132` explicitly asserts the complete invocation method-name set. `SkillExecutorTest:33` exercises every existing map with null keys/values, nested containers and identity-bearing leaves. `ApplicationApiValueTest:91` adds nested non-string key and Resource behavior. Changing the private constructor also affects the internal bridge and its many test call sites; it is not a public construction contract.

### TypeSafe pack policy and transport

All following paths are relative to sibling `../loomspan-extensions/extensions/decisions/`:

- `src/main/java/ai/loomspan/decisions/autoconfigure/DecisionsAutoConfiguration.java:11` creates the executor and one shared transport, with transport destruction calling `close`. These are wiring details, not bean-replacement contracts.
- `src/main/java/ai/loomspan/decisions/internal/typesafe/TypeSafeExecutor.java:14` declares `@SkillExecutor(name = "typesafe", config = "typesafe")` in default namespace. `execute` prepares request bytes using owner config and invocation input, then calls the shared transport with the invocation.
- `src/main/java/ai/loomspan/decisions/internal/typesafe/TypeSafeRequest.java:27` allows only `api_key` and `base_url` in the owner map, validates/copies native input and serializes it once. It does not select credentials. Unknown owner keys fail before send.
- `TypeSafeRequest.java:15` accepts only leaf `max_attempts`, an integer Java numeric kind from 1 through 3, default 3. Unknown leaf keys fail. Request preparation and transport are separate collaborators; retries use the same prepared byte array.
- `src/main/java/ai/loomspan/decisions/internal/typesafe/TypeSafeSettings.java:8` currently captures only owner settings. It requires a nonblank string API key, used verbatim, and permits only printable ASCII header characters (32 through 126). The URL defaults to `https://api.typesafe.ai` only when absent. Explicit null/blank/non-string settings fail.
- URL validation requires absolute HTTP(S), server authority with host, no user info/query/fragment, valid optional port and path. Trailing slashes are stripped and `/v1/systemone` is appended while retaining a path prefix. There is no environment lookup, credential interpolation or Framework connection resolution.
- `src/main/java/ai/loomspan/decisions/internal/typesafe/TypeSafeTransport.java:69` captures settings once before its retry loop; line 109 constructs a request to that endpoint and sends `Authorization: Bearer <captured key>`. It reads leaf attempts separately. Redirect handling and automatic Apache retries are disabled; the transport owns bounded retry, cancellation/deadline checks and diagnostics.

Verified repository-local consumers and fixtures:

- `src/test/java/.../TypeSafeSettingsTest.java` covers exact credentials, default/prefixed URLs, missing/invalid keys, URL rejection, non-string settings and snapshot immutability.
- `src/test/java/.../TypeSafeTransportTest.java` covers real local HTTP path/header, capture once across retries, bounded leaf attempts and independent concurrent settings. Transport test support mocks the final supported invocation and supplies owner maps; it does not use a public invocation constructor.
- `src/test/resources/typesafe/` owns synthetic native request/response fixtures.
- `examples/support-triage/src/test/java/ai/loomspan/examples/triage/ConsumerHarness.java:25` starts the actual Boot application and installed pack, uses MockWebServer for planner/provider and supplies raw startup YAML. Its current publication helper targets only `default`.
- `examples/support-triage/src/test/java/ai/loomspan/examples/triage/PublicationConsumerTest.java:15` publishes distinct same-namespace endpoints/keys during a blocked request, verifies new requests reach the new destination/key, and releases old retries using their old destination/key and unchanged bytes. It covers owner rotation, not foreign consumer-only rotation.
- `TypeSafeConsumerTest`, `CompositionConsumerTest`, `LifecycleConsumerTest`, `AccountingConsumerTest` and `DiagnosticFaultConsumerTest` exercise installed artifact discovery, direct/native/transformed input, parent composition, live lifecycle and diagnostics. `ConsoleConsumerTest` is opt-in and consumes ordinary traces.

### Surface inventory and compatibility classification

| Category | Current evidence and affected surface |
| --- | --- |
| Application API | The closed allowlist in `LoomspanPublicSurfaceArchitectureTest:37` includes exactly the v1 invocation and diagnostics extension types alongside top-level `ai.loomspan.api` types. The requested accessor extends this deliberately supported handoff. `SkillReloader`, `ExecutionConfiguration` and `SkillExecutor` are supported top-level API; annotation selector documentation currently names owner configuration only. |
| Supported SPI | Repository policy and architecture tests explicitly establish none. Annotated executor declarations do not create bean replacement or internal construction contracts. |
| Configuration and manifest contracts | Existing YAML namespace maps, exact dot-path selector, complete publication, leaf `config`, identity/visibility/RBAC, and pack API-key/URL/retry rules are documented contracts and demonstrated by focused tests. |
| Persisted or serialized contracts | This ticket introduces no declared durable configuration format beyond existing YAML and no serialized invocation protocol. Native TypeSafe request/response wire shapes remain pack-owned and separate from Java invocation state. |
| Ephemeral diagnostic formats | Existing executor frames expose executor identity and owner `executorConfigGenerationId`; diagnostics use existing bounded records/content. No consumer map or second generation ID is currently emitted. |
| Internal or accidentally exposed implementation | `ExecutorBinding`, `SkillAdmissionSnapshot`, `SkillGeneration`, manager/validator, `ExecutorInvocations`, configuration parser/loader, `ImmutableContainers`, internal registration/invoker and auto-configuration classes/beans. Their public modifiers and tests demonstrate exposure/behavior, not supported API or SPI status. |

Interfaces/constructors are inventoried separately from API promises: the supported invocation has a private constructor and is consumed as a concrete final class; internal construction uses `ExecutorInvocations`; `ExecutorBinding` is an internal record constructor. `SkillExecutorRegistration` carries a Java function invoker internally. Neither is a consumer replacement point. Framework auto-configuration wires manager and coordinator (`LoomspanAutoConfiguration:155`, `:445`); pack beans are ordinary fixed construction methods without `@ConditionalOnMissingBean` customization contracts.

The ticket explicitly permits source/binary/behavioral changes within its scope, keeps v1/artifact versions and requires direct coordinated updates without shims. Existing method signatures returning `Map<String,Object>` expose no internal or auto-configuration types. Adding a map accessor has a Java supported-surface delta but does not by itself create REST/SSE or NDJSON fields.

Protected Console consumers presently read executor identity, YAML provenance, owner generation IDs and existing diagnostics: Framework `ExecutorDiagnosticsIntegrationTest`, Java fixture corpus tests, Console callable-form provenance and diagnostic record consumers. The ticket does not require new diagnostic fields or a changed protocol marker. Source evidence identifies no necessity to serialize consumer configuration to meet the requested Java handoff behavior. A later plan changing REST/SSE/NDJSON would need its own coordinated protocol assessment.

## Documentation comparison and authoring impact

Used the repository-local `agent-skills/loomspan-docs/SKILL.md` as router after executable-path inventory. Its declared `1.0.0-beta.10-SNAPSHOT` matches Framework and the Decisions dependency. Relevant routed topics were `references/skill-authoring/executor-skills.md`, `typesafe-skills.md`, `source-verification.md`, and Java API `java-skills.md`/`skill-reload.md`, with their indexes. This is the matching checked-out skill, not a separately installed release.

- **aligned:** generic owner dot-path selection, immutable maps, complete namespace publication, captured owner generation identity, Public foreign references and no domain merge in Framework README (`:261` onward), executor topic, Java declaration topic and reload topic agree with current source/tests.
- **aligned:** pack `docs/executor.md` describes optional leaf `max_attempts`, owner map settings, URL default/prefix and invocation-time validation consistently with current production and focused pack tests.
- **documentation drift:** packaged `references/skill-authoring/typesafe-skills.md` under “Native input, settings and results” says to omit/empty leaf config and that every leaf key is rejected. Current `TypeSafeRequest.maxAttempts`, `TypeSafeTransportTest` and pack `docs/executor.md` support `max_attempts` 1–3. This discrepancy is within the ticket's existing retry/documentation scope; it does not create a new product decision.
- Current generic docs say “both configuration maps” or enumerate three total business maps, and TypeSafe docs describe only owner settings. Those describe the current code; they are not evidence that the requested consumer accessor exists.

The ticket has skill-authoring impact: it changes what a foreign executor leaf receives, where namespace settings live, how consumer publication affects fresh versus retained calls, and which TypeSafe keys each namespace controls. The Framework README, annotation/accessor JavaDoc, routed generic executor/Java/reload topics, TypeSafe topic and coverage index are directly relevant documentation locations. Pack README and `docs/executor.md`/`docs/transport.md` own provider-specific behavior; `docs/tested-builds.md` and recipe docs own build/run evidence and examples.

## Architecture Documentation

The existing ownership chain is raw namespace data -> immutable namespace generation -> reached admission binding -> Framework-created invocation -> executor-owned domain validation. The caller's mission input remains explicit ordinary business input; configuration is a separate captured channel. Parent entry namespace and executing leaf namespace can differ, and leaf identity is already available at binding time.

Existing publication and lease capture provide the state and lifecycle needed to describe which owner and leaf generation an invocation uses. No application executor preparation callback, config merge engine, provider override policy or internal bean-replacement surface currently exists.

## Historical Context and Related Research

- `ai/thoughts/phases/executor-packs-and-typesafe-extraction-roadmap.md` is the owning extraction roadmap referenced by Extensions `AGENTS.md` and root README. It establishes pack-owned provider behavior, application-owned callable skills, independent release ownership and coordinated development changes. Historical progress claims are not current verification evidence.
- `ai/thoughts/phases/typesafe-executor-pack-migration-review.md` and `decisions-executor-pack-research.md` are roadmap-linked historical source/rationale companions, not substitutes for the live source traced here.
- Prior PR 31–33 research/plan/ticket/review files are deleted in the current worktree by pre-existing work. They were not restored or used as current implementation evidence.
- This step made only this research artifact. No production, test, fixture, version, protocol or sibling repository files changed.

## Verification environment and build ownership

Framework version is `1.0.0-beta.10-SNAPSHOT`; Decisions version is `0.1.0-SNAPSHOT`, with Framework dependency pinned to the same current version. The pack POM is independently owned; Extensions root is an aggregator. The example is a separate installed-JAR consumer and is not proved by descriptor validation or only the pack's unit tests.

Existing documented local sequence: Framework focused/full tests and install; Extensions root `mvn -B validate`; pack `mvn -B -f extensions/decisions/pom.xml install`; example `mvn -B -f extensions/decisions/examples/support-triage/pom.xml verify`. `LoomspanPublicSurfaceArchitectureTest` is required after production-type changes. The pinned JSLT dependency prerequisite is documented in `docs/building-jslt.md` and pack build guidance.

No executable test suite was run during this research-only step. Metadata script launch was attempted using `bash ai/scripts/spec_metadata.sh` (WSL returned access denied), then Git Bash's explicit executable (MSYS failed creating a named object with access denied). Equivalent metadata was collected with PowerShell `Get-Date -Format o`, `git rev-parse HEAD`, `git branch --show-current`, and sibling Git commands. No thoughts-status researcher identity was available; this artifact identifies the researcher as Codex.

## Open Questions

No unresolved developer decision blocks planning. The ticket settles separate maps, leaf-owned consumer identity, same-selector lookup, empty cases, capture semantics and TypeSafe policy.

Planning should make explicit the handling of a present malformed consumer subtree (null/scalar/list or non-map intermediate), distinguishing optional absence from the existing mandatory owner selection rules. Current owner behavior is fully evidenced above; no consumer selection behavior exists yet. This is an implementation validation detail for the plan to resolve from the ticket's “same config path” and “existing dot-path semantics” requirements, not evidence for treating malformed data as a missing entry.
