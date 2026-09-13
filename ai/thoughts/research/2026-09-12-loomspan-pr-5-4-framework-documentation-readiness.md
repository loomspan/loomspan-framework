---
date: 2026-09-12T21:48:44-07:00
researcher: Codex
model: GPT-5
git_commit: de2fc7d1a5154407912f5dd2f35504926e33d9d2
branch: main
repository: loomspan-framework
topic: "PR 5.4 beta 4 framework documentation and gated release readiness"
tags: [research, codebase, beta4, documentation, rest-skills, public-api, release-readiness]
status: complete
last_updated: 2026-09-12
last_updated_by: Codex
---

# Research: PR 5.4 beta 4 framework documentation and gated release readiness

**Date**: 2026-09-12 21:48:44 PDT
**Researcher**: Codex
**Git Commit**: de2fc7d1a5154407912f5dd2f35504926e33d9d2
**Branch**: main
**Repository**: loomspan-framework

## Research Question

Document the current framework state relevant to
`ai/thoughts/tickets/loomspan-pr-5.4-framework-documentation-and-readiness.md`:
the beta 4 REST manifest and handler contracts, public catalog and validation
surface, observation and shutdown semantics, supported-surface coverage,
version/release machinery, Console protocol compatibility, agent-skill
documentation, and the external Sidecar gate. This research describes the
checkout as it exists and does not implement PR 5.4.

## Summary

The live checkout is at the cleanup commit after PR 5.3. The implementation
from PRs 5.1 through 5.3 is present: REST is a third skill kind, the public API
contains thirteen allowlisted types with `RestSkillHandler` as the only
supported SPI, `SkillCatalog` is an eager immutable unfiltered snapshot,
`SkillTemplate` has two advisory `validate` overloads, observers receive
available failure history without replacing the execution failure, and one
framework-owned positive shutdown budget covers admitted roots through
completion and cutoff. Focused production and test anchors establish these
behaviors.

The root README and checked-in `agent-skills/loomspan-docs` already document a
substantial part of this surface. The checked-in documentation package is
version-aligned with the POM at `1.0.0-beta.4-SNAPSHOT` and its routed REST,
catalog, invocation, observation, error, authorization, and compatibility
topics agree with executable evidence. Remaining PR 5.4 work is concentrated
in completing root README precision, adding beta compatibility/release notes,
making the cumulative supported-surface fixture use the required YAML planner,
and recording gated readiness evidence. The current supported-surface YAML
fixture explicitly uses `planning_mode: false`, and the repository contains no
beta 4 release-notes artifact or completed SC5 snapshot-integration evidence.

The release tooling itself already enforces key safety boundaries. The version
script rejects SNAPSHOT tags, manual Console Release and Maven Central Release
runs validate without publishing, and pushed non-SNAPSHOT version-matching tags
are the publication trigger. In contrast, several README release examples use
`1.0.0-beta.4-SNAPSHOT` as the tag and release version, which directly conflicts
with the script and workflows. The final release-verification gate remains
pending by ticket design: this checkout has no `v1.0.0-beta.4` tag and no durable
record that the required Sidecar HTTP/JWT/queue/container integration ran
against this framework commit.

## Detailed Findings

### Repository baseline and version alignment

- `main` and `origin/main` point to
  `de2fc7d1a5154407912f5dd2f35504926e33d9d2` (`Cleanup PR 5.3`). The immediately
  preceding feature commits are `5aa5b75` (PR 5.3), `d733c8c` (PR 5.2), and
  `558a129` (PR 5.1). The historical grounding commit `1e4eb455` predates all
  three implementations.
- The root POM declares `1.0.0-beta.4-SNAPSHOT` (`pom.xml:9`).
  `python scripts/loomspan_version.py check` reports that the POM, module POM,
  Console skill, and checked-in `loomspan-docs` skill all carry that version.
  `VERSIONED_SKILLS` explicitly names the two skill manifests
  (`scripts/loomspan_version.py:21-24`), and `check_consistency` compares them
  with the root version (`scripts/loomspan_version.py:103-121`).
- The repository copy of the documentation skill declares
  `metadata.loomspan-version: "1.0.0-beta.4-SNAPSHOT"`
  (`agent-skills/loomspan-docs/SKILL.md:6-8`). The globally installed copy read
  for this task declares `0.1.0-SNAPSHOT`; it also describes the older eight-type
  API and no supported SPI. Because it is not version-aligned, it is not evidence
  for exact beta 4 semantics. The same-checkout package under `agent-skills/`
  supplies the aligned routing and topic documents used below.
- Existing release tags stop at `v1.0.0-beta.3`. There is no beta 4 release tag.

### Contract classification under the framework design lens

#### Application API

The executable public-surface allowlist contains exactly thirteen top-level
types in `ai.loomspan.api`: `SkillTemplate`, `SkillCatalog`, `SkillDescriptor`,
`SkillKind`, `SkillExecutionView`, `SkillExecutionEvent`, `SkillMethod`,
`SkillParam`, `RestSkillHandler`, `RestSkillInvocation`, `SkillException`,
`SkillInputValidationException`, and `SkillInputValidationIssue`
(`loomspan-spring-boot-starter/src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java:29-42,285-288`).
The same architecture test recursively rejects `internal` or `autoconfigure`
types in supported signatures (`.../LoomspanPublicSurfaceArchitectureTest.java:339-365`).

The beta 4 additions divide into distinct compatibility shapes:

- `RestSkillHandler` and `RestSkillInvocation` are new supported API types and
  the former is the one supported extension contract
  (`.../api/RestSkillHandler.java:5-7`,
  `.../api/RestSkillInvocation.java:11-17`).
- `SkillCatalog`, `SkillDescriptor`, and `SkillKind` are additive public types
  (`.../api/SkillCatalog.java:11-15`, `.../api/SkillDescriptor.java:6`,
  `.../api/SkillKind.java:4-7`).
- Two abstract methods were added to the already-supported `SkillTemplate`
  interface, so application implementations/fakes compiled against an earlier
  interface do not inherit a default implementation
  (`.../api/SkillTemplate.java:11-17`).
- The observer overload signatures did not change, but their behavior expanded
  from success-only delivery to available post-session failure delivery. That
  is a behavioral compatibility change rather than a new type or method.

#### Supported SPI

`RestSkillHandler` is a functional interface whose entire public operation is
`String handle(RestSkillInvocation invocation)`
(`.../api/RestSkillHandler.java:4-7`). It is the sole deliberately supported
SPI. No `ai.loomspan.spi` package exists, and the architecture test separately
classifies public internal types as implementation detail
(`.../architecture/LoomspanPublicSurfaceArchitectureTest.java:315-326`).

The application supplies the handler as an ordinary Spring bean. Registration
looks up all handler beans only when at least one REST definition exists and
requires exactly one; zero names all REST resources, more than one names the
beans, and no REST declarations skip the cardinality check
(`.../internal/skill/YamlSkillCapabilityRegistrar.java:39-44,69-84`). The
framework auto-configuration does not declare a default handler bean. A search
of all four auto-configuration classes finds no `@ConditionalOnMissingBean` at
all; `SkillCatalog`, `SkillTemplate`, the lifecycle, router, registries, and
other runtime components are framework infrastructure beans, not documented
replacement points (`.../autoconfigure/LoomspanAutoConfiguration.java:149-214,279-293,363-450`).

#### Configuration and manifest contracts

`YamlSkillCatalog` owns the REST declaration contract. Raw-tree validation
accepts `rest` only when present as Boolean `true`; false, null, string, number,
array, and object values fail with guidance to omit the key for non-REST YAML
(`.../internal/skill/YamlSkillCatalog.java:255-305,307-324`). The forbidden
presence set is exactly `model`, `prompt`, `thinking_level`, `allowed_skills`,
`planning_mode`, `concurrency`, `max_steps`, `linter`, `output_schema`, and
`output_schema_max_retries` (`.../internal/skill/YamlSkillCatalog.java:51-56`).
Because the raw tree uses `has`, null and empty declarations are rejected by
presence before data binding. `name` and `description` retain the normal
required/nonblank checks (`.../internal/skill/YamlSkillCatalog.java:176-193`).

A REST definition has no execution configuration and an omitted input schema
uses the existing generic-object contract
(`.../internal/skill/YamlSkillDefinition.java:23-47,94-108`). Registration adds
the exact public name, YAML role policy, `REST_SKILL` kind, no configured model
descriptor, the exact resolved tool input schema, and a handler-delegating
invoker (`.../internal/skill/YamlSkillCapabilityRegistrar.java:45-62`). The
same registry completion pass detects cross-kind duplicates and resolves exact
`allowed_skills` children (`.../internal/skill/YamlSkillCapabilityRegistrar.java:63-66`).

`loomspan.shutdown.timeout` is a user-visible configuration contract. Its
binding default is 30 seconds and its setter rejects null, zero, and negative
durations (`.../autoconfigure/LoomspanProperties.java:287-305`). Additional
Spring configuration metadata publishes type, default, and the shared-budget
description (`.../src/main/resources/META-INF/additional-spring-configuration-metadata.json:3-8`).

#### Persisted or serialized contracts

The public `SkillExecutionView` is not a new retrieval or durable-history API;
it is a completed current-version view passed to the caller's observer. Canonical
traces continue to include the framework-owned `consoleCompatibilityVersion`
(`.../internal/runtime/trace/DefaultExecutionTraceHandle.java:306`). The
framework loads that marker from filtered release metadata
(`.../internal/release/LoomspanReleaseVersion.java:12-34`).

REST expands the existing application-adapter serialization semantically:
registered-skill summaries/details now carry source `REST` with a YAML resource
path/text. The Java producer maps REST and model-backed YAML definitions through
the YAML-source branch (`.../internal/runtime/observation/catalog/DefaultRegisteredSkillCatalog.java:30-46`).
Protected consumers include the Go application client/validators, TypeScript
unions (`loomspan-console/web/src/api/contracts.ts:123-137`), skill list/detail
components, and trace import/acquisition readers. Executable cross-language
fixtures are generated by `ConsoleRestFixtureCorpusTest`, including
`loomspan-console-fixtures/application-rest/skill-rest-detail.json`
(`.../internal/observability/web/ConsoleRestFixtureCorpusTest.java:157-166`).
Console component tests assert the distinct label and unchanged manifest
(`loomspan-console/web/src/observability/SkillCatalog.test.tsx:38-46`,
`.../SkillDetail.test.tsx:112-121`).

The compatibility treatment is exact project-version matching rather than a
separate schema counter. Missing or unequal resolved markers are rejected by
the Console application client and trace processor; dual `development` markers
are the only best-effort development case. The checked-in java-api compatibility
topic records that semantic rationale
(`agent-skills/loomspan-docs/references/java-api/compatibility-and-boundaries.md:99-105`).

#### Ephemeral diagnostic formats

The observability REST/SSE catalog and the public observer event values expose
current-execution diagnostics. `SkillExecutionEvent` recursively copies only
JSON-like scalar/map/list/array detail values, normalizes blank optional fields,
and rejects unsupported objects (`.../api/SkillExecutionEvent.java:11-68`).
Neither those event details nor REST inputs, handler results, exceptions, and
history receive a new comprehensive sanitization guarantee. The checked-in
observation topic explicitly tells applications to apply their own access,
redaction, retention, and export policy
(`agent-skills/loomspan-docs/references/java-api/observation-and-errors.md:51-67`).

#### Internal or accidentally exposed implementation

`DefaultSkillTemplate`, `DefaultSkillCatalog`, `YamlSkillCatalog`,
`YamlSkillCapabilityRegistrar`, `ExecutionCoordinator`,
`FrameworkExecutionLifecycle`, `MissionLifecycle`, and the public
auto-configuration types are technically accessible implementation or Spring
integration machinery. The architecture allowlists classify them separately;
none appears in a public API signature. `LoomspanAutoConfiguration` creates
the supported facade/catalog beans but their concrete implementations and
collaborators remain under `ai.loomspan.internal`
(`.../autoconfigure/LoomspanAutoConfiguration.java:208-214,279-293`).

### REST invocation flow

The current root and nested REST path is:

```text
YAML resource
  -> raw REST field/presence validation
  -> immutable YamlSkillDefinition without model execution configuration
  -> completed shared registry with REST_SKILL metadata and one handler invoker
  -> root SkillTemplate or an allowed child binding
  -> input contract validation
  -> root/nested authorization recheck
  -> ref:// argument resolution
  -> scoped caller SecurityContext on the actual execution thread
  -> RestSkillInvocation deep container snapshot
  -> application RestSkillHandler
  -> direct mission success/failure and ordinary trace/observer completion
```

`CapabilityExecutionRouter` rechecks access and input validation before the
coordinator (`.../internal/core/CapabilityExecutionRouter.java:39-68`). For
Java and REST kinds, `ExecutionCoordinator` follows the same direct mission
branch, resolves the effective caller, opens `ScopedAuthentication`, resolves
references, invokes the registered invoker, and does not create a model
interaction (`.../internal/core/ExecutionCoordinator.java:128-150`). This
establishes the execution-time recheck independently of public pre-validation.

`RestSkillInvocation` rejects null record components, recursively copies map
and list containers, preserves null and non-container leaf identity, and makes
containers unmodifiable (`.../api/RestSkillInvocation.java:11-43`). A resolved
Spring `Resource` is therefore passed as the same leaf object; its backing
contents are outside the container immutability claim. The record has only
`skillName` and `input`: identity, session/trace identifiers, description, URL,
headers, credentials, and routing metadata are not SPI arguments.

The handler owns routing and remote-call mechanics. Registration invokes it
once with the exact skill name; empty text returns successfully and null raises
`REST skill '<name>' handler returned null`
(`.../internal/skill/YamlSkillCapabilityRegistrar.java:87-92`). Existing
`SkillException` and `AccessDeniedException` instances cross the facade
unchanged; other runtime exceptions become a safe `SkillException` with the
original cause. REST bypasses the Java method adapter's exception-to-text
behavior. The focused test protects empty/null and both failure forms
(`.../internal/skill/YamlSkillCapabilityRegistrarTests.java:163-187`).

The shared direct-child completion boundary grants task/required-child/evidence
credit only after successful completion. `SuccessfulSkillCompletionBoundaryTest`
contains the REST-specific success/failure coverage; the authoring topic names
that test as its executable anchor
(`agent-skills/loomspan-docs/references/skill-authoring/rest-skills.md:46-52`).

### Catalog and advisory root validation

`DefaultSkillCatalog` calls `completeRegistration` before reading the registry,
builds a `TreeMap` by exact name, translates each internal kind to public
`SkillKind`, copies the exact existing `CapabilityToolDescriptor.inputSchema`
string, and freezes both its lookup map and list
(`.../internal/skillapi/DefaultSkillCatalog.java:20-46`). It performs no
authorization filtering. The public descriptor contains only name,
description, kind, and input schema (`.../api/SkillDescriptor.java:6-18`).
`skill(name)` is exact and returns an empty `Optional` for absence.

Both `validate` overloads reuse invocation preparation and then evaluate the
root role policy against the calling thread's authentication without entering
`LoomspanSessionRunner` (`.../internal/skillapi/DefaultSkillTemplate.java:77-87,113-172`).
The Object overload rejects null before Jackson conversion; the Map overload
can normalize null to `{}` only when the effective contract is generic or
allows empty input (`.../internal/skillapi/DefaultSkillTemplate.java:113-157,227-239`).
Unknown skill, conversion, validation, and authorization exception mapping
matches invocation preparation.

Validation creates no session, trace, execution binding, observer call, model
or skill execution, and reserves neither a root-admission slot nor future child
authorization. `FrameworkShutdownIntegrationTest#validationDoesNotReserveAdmissionForLaterInvocation`
demonstrates that successful validation does not prevent a later shutdown
rejection (`.../internal/core/FrameworkShutdownIntegrationTest.java:107-138`).
Focused validation tests cover no execution/binding change, matching failures,
distinct null rules, and unknown names
(`.../internal/skillapi/DefaultSkillTemplateTest.java:135-223`).

### Observer completion and failure precedence

Input preparation happens before root admission, so invalid input and unknown or
conversion failures cannot produce a session callback. After admission,
`LoomspanSessionRunner` creates the session, executes/finalizes it inside the
execution binding, restores that binding, then invokes the completion callback.
On failure, a completion failure is suppressed behind the original execution
failure; on success, a completion runtime failure is wrapped internally only so
the facade can rethrow the original observer exception unchanged. Root ownership
is released in the surrounding `finally`
(`.../internal/core/LoomspanSessionRunner.java:215-278`).

`DefaultSkillTemplate` maps the finalized session into `SkillExecutionView` and
calls the optional observer in that one completion callback
(`.../internal/skillapi/DefaultSkillTemplate.java:176-195`). This yields at-most-once
synchronous delivery after binding restoration and before root release. History
is only delivered when finalization and mapping made it available; no registry
or retrieval API is introduced.

Focused tests protect each observable branch:

- failure history arrives once while the original failure remains the facade
  cause (`.../internal/skillapi/DefaultSkillTemplateTest.java:106-132`);
- a failure observer or mapper failure is suppressed and does not mask the
  execution failure (`.../internal/skillapi/DefaultSkillTemplateTest.java:226-264`);
- a success observer exception propagates unchanged
  (`.../internal/skillapi/DefaultSkillTemplateTest.java:415-433`);
- invalid input invokes no observer
  (`.../internal/skillapi/DefaultSkillTemplateTest.java:437-455`);
- lifecycle tests place success/failure completion after binding restoration
  and before release (`.../internal/core/FrameworkExecutionLifecycleTest.java:100-160`).

### Framework shutdown behavior

`FrameworkExecutionLifecycle` owns a single admission gate, active-root set,
deadline, cutoff flag, and framework mission executor
(`.../internal/core/FrameworkExecutionLifecycle.java:20-50`). The owning
context's synchronous `ContextClosedEvent` listener only closes admission and
establishes the deadline; it does not wait
(`.../internal/core/FrameworkExecutionLifecycle.java:53-95`). Its maximum
Spring lifecycle phase then waits for admitted roots, shuts down the executor,
waits within the same remaining deadline, publishes cutoff if roots or executor
work remain, and invokes the lifecycle callback
(`.../internal/core/FrameworkExecutionLifecycle.java:97-159`). `destroy()` is
a non-waiting fallback for failed startup or missed/failed event delivery
(`.../internal/core/FrameworkExecutionLifecycle.java:171-187`).

Root admission happens before session construction, and one admitted root is
held until completion mapping/observation returns
(`.../internal/core/LoomspanSessionRunner.java:220-265`). Missions attached
after admission closes inherit the shared deadline; at cutoff,
`MissionLifecycle` fences writes and interrupts known framework futures. The
executor receives `shutdownNow()` when cutoff publishes
(`.../internal/core/FrameworkExecutionLifecycle.java:171-178`,
`.../internal/core/MissionLifecycle.java:336-365`). Existing mission deadlines,
quotas, depth bounds, and nested admission remain in force while an admitted
root drains.

The focused shutdown suites cover late-root rejection before session creation,
validation without admission reservation, independent close listeners in either
registration order, completion under the admitted root, shared deadlines,
uncooperative executor cutoff, and keeping a lower-phase resource alive even
when Spring's phase timeout is shorter
(`.../internal/core/FrameworkShutdownIntegrationTest.java:68-256`,
`.../internal/core/FrameworkExecutionLifecycleTest.java:43-407`). The bound is
limited to Loomspan-owned work and does not bound unrelated application hooks
or halt the JVM.

Sidecar's immediate dispatch stop, queued-work discard, and lack of a Sidecar
drain timer are requirements in the roadmap/SC5, not framework implementation
in this repository. Framework tests prove the framework lifecycle but cannot
prove host HTTP/JWT/queue/container resource ordering.

### Current human documentation coverage

The README currently includes:

- the eager, sorted, immutable, unfiltered catalog; both validation overloads;
  exact schema strings; null distinctions; no-session/no-trace/no-callback/no-
  execution/no-quota semantics; no admission reservation; no child precheck;
  and execution-time recheck (`README.md:139-176`);
- the exact thirteen supported types, named sole SPI, internal/autoconfigure
  boundary, and no implied bean replacement (`README.md:169-174`);
- observer success/failure timing, at-most-once available history, pre-session
  no-callback behavior, failure precedence, unchanged success-observer errors,
  current-version immutability/trust limitations, and facade exception families
  (`README.md:176`);
- a REST manifest example and the broad leaf/handler/input/reference/security/
  null/empty/error/Console behavior (`README.md:415-433`);
- the positive 30-second shutdown setting, root rejection, continuing nested
  work, one overall budget including observation/cleanup, prompt close listener,
  later lifecycle wait, resource lifetime, and limits of the framework-owned
  bound (`README.md:480-509`).

The current README does not yet state every PR 5.4 distinction at the same
precision as code and the checked-in topic documents:

- the REST section groups forbidden fields generically rather than listing all
  ten exact names, and it does not explicitly say forbidden null/empty presence
  is rejected (`README.md:431` versus
  `.../internal/skill/YamlSkillCatalog.java:51-56,307-323`);
- it does not spell out the handler's two-component shape, lack of default,
  one-call routing responsibility, absence of identity/session arguments,
  handler ownership of URLs/headers/credentials, schema-permitted model input,
  or existing-`SkillException`/`AccessDeniedException`/ordinary-runtime/JVM-
  `Error` distinctions;
- the catalog paragraph does not enumerate the four descriptor fields or the
  empty result for exact missing lookup;
- shutdown text does not explicitly name Sidecar's separate immediate
  dispatch/queue policy or say uncooperative work is fenced/interrupted;
- no beta 4 release/compatibility-notes file or equivalent complete section
  currently distinguishes all additive API, `SkillTemplate` source/binary,
  observer/shutdown behavioral, REST startup, and Console protocol changes.

These are documentation-coverage observations, not evidence that the underlying
runtime behavior is absent.

### Checked-in `loomspan-docs` coverage and drift classification

The version-aligned skill routes both relevant knowledge sets:

- `skill-authoring` routes REST declarations to
  `references/skill-authoring/rest-skills.md`, with authorization and input
  topics as adjacent context. Its coverage table marks the REST manifest topic
  source-verified (`agent-skills/loomspan-docs/references/skill-authoring/README.md:29-55`).
- `java-api` routes supported-boundary, catalog/pre-check, invocation,
  REST-handler, and observation/error tasks to separate documents. Its index
  lists all thirteen API types and marks catalog and SPI coverage source-verified
  (`agent-skills/loomspan-docs/references/java-api/README.md:20-88`).

The routed checked-in documents cover the exact forbidden-field matrix,
generic input default, exact-name registration, roles, direct execution,
success credit, one-handler rule, immutable nested containers, permitted nulls
and Resource limitation, scoped security context, handler ownership, result and
failure semantics, catalog shape, both validation null rules, advisory limits,
observer lifecycle/error precedence, supported-surface compatibility, and the
project-version Console marker. Their source anchors name the focused tests and
production paths inspected here.

Drift classification:

| Compared surface | Classification | Evidence |
| --- | --- | --- |
| Checked-in `agent-skills/loomspan-docs` REST, catalog, validation, invocation, observation/error, authorization, and compatibility topics versus live source/tests | **aligned** | The package is version-marked `1.0.0-beta.4-SNAPSHOT`; routed statements match the production/test paths above. |
| Root README API/catalog/observer core statements versus live source/tests | **aligned** | `README.md:139-176` matches the public API, catalog, validation, and completion paths. |
| Root README REST declaration matrix and handler boundary versus live source/tests | **documentation drift** | The broad description is correct, but exact forbidden fields/presence and several handler-boundary distinctions are omitted from `README.md:431-433`. |
| Root README shutdown core semantics versus live source/tests | **documentation drift** | The shared budget and lifecycle stages agree at `README.md:505-507`, but cutoff/interruption and the separate Sidecar policy are not fully stated. |
| Root README release commands versus version script/workflows | **documentation drift** | `README.md:211-266` repeatedly uses a SNAPSHOT release/tag while `create_tag` rejects it and both publication workflows require non-SNAPSHOT version-matching tags. |
| Globally installed `loomspan-docs` copy versus this checkout | **unresolved** | Installed metadata is `0.1.0-SNAPSHOT`; the checkout and its bundled skill are `1.0.0-beta.4-SNAPSHOT`. The installed copy was excluded from exact beta 4 claims because of the version mismatch. |

No comparison produced a `possible framework defect` classification.

### Current cumulative supported-surface coverage

`SupportedSurfaceIntegrationTest` uses a real application context and a local
OpenAI-compatible `MockWebServer`. The model response calls both
`supportedJavaLeaf` and `supportedRestLeaf`, and the final response consumes
both results (`.../integration/SupportedSurfaceIntegrationTest.java:42-76,164-181`).
The test obtains only public `SkillTemplate` and `SkillCatalog`, asserts all
three public kinds and missing lookup, validates YAML/Java/REST roots, invokes
the YAML root with a public observer, directly invokes the REST leaf, verifies
immutable handoff and scoped authentication, observes failure history while the
failure remains thrown, and exercises public authorization behavior
(`.../integration/SupportedSurfaceIntegrationTest.java:88-160`). Its application
fixture supplies one `RestSkillHandler` and one `@SkillMethod` Java leaf
(`.../integration/SupportedSurfaceIntegrationTest.java:187-222`).

The REST fixture is a typed, role-restricted YAML declaration
(`.../src/test/resources/skills/integration/supported-rest-leaf.yml:1-14`). The
parent fixture is model-backed but explicitly declares `planning_mode: false`
(`.../src/test/resources/skills/integration/supported-surface-skill.yml:1-7`).
Consequently, current cumulative coverage demonstrates direct LLM execution
calling Java and REST leaves, not the ticket's required YAML planner path.

Owner tests retain more exact feature proof than the cumulative fixture:

- `YamlSkillCatalogTests#rejectsInvalidRestValuesAndForbiddenFieldsByPresence`
  covers every invalid `rest` form and null/empty presence of every forbidden
  field (`.../internal/skill/YamlSkillCatalogTests.java:60-105`).
- `YamlSkillCapabilityRegistrarTests` covers collisions, handler cardinality,
  no-model registration, invocation, empty/null results, and propagated
  failures (`.../internal/skill/YamlSkillCapabilityRegistrarTests.java:95-187`).
- `ApplicationApiValueTest` covers the exact public shapes and recursive REST
  invocation immutability, nulls, and Resource identity
  (`.../api/ApplicationApiValueTest.java:18-106`).
- `DefaultSkillCatalogTest#buildsEagerUnfilteredImmutablePublicSnapshotWithExactSchemas`
  covers ordering, unfiltered scope, kinds, exact schema bytes, missing lookup,
  immutability, and registration-before-snapshot
  (`.../internal/skillapi/DefaultSkillCatalogTest.java:24-50`).
- `DefaultSkillTemplateTest` and the lifecycle suites cover the exact validation,
  observer, failure-precedence, and shutdown boundaries listed above.

### Release tooling and readiness gates

`scripts/loomspan_version.py` is the single coordinated version mechanism. Its
`set` operation requires a clean worktree, replaces the exact current version
in tracked text files, refreshes derived fixture byte lengths/checksums, and
rechecks consistency (`scripts/loomspan_version.py:187-228`). Its `tag` operation
requires a clean committed non-SNAPSHOT version and creates annotated
`v<version>` (`scripts/loomspan_version.py:231-243`). Script tests cover
historical-evidence preservation, derived fixture refresh, complete replacement,
dirty-worktree refusal, metadata drift, annotated release tags, and SNAPSHOT
rejection (`scripts/tests/test_loomspan_version.py:45-150`).

The README already lists the intended local dry-run commands: coordinated
version check, script unit tests, `clean verify`, and release-profile verify
with signing skipped (`README.md:287-301`). No current PR 5.4 artifact records
fresh results for the full four-command dry run at this commit. This research
ran only the version consistency check; historical runs in
`ai/thoughts/beta4-code-grounding.md` predate PRs 5.1-5.3 and do not establish
final beta 4 readiness.

`.github/workflows/publish.yml` and `.github/workflows/console-release.yml`
both support `workflow_dispatch`. Manual runs execute verification/package
jobs but their publish/upload jobs are gated to pushed tag events
(`.github/workflows/publish.yml:5-7,35-57`,
`.github/workflows/console-release.yml:4-6,42-53,159-163`). The Maven workflow
checks coordinated versions, script tests, full Java verification, and the
release profile before its tag-only deploy (`.github/workflows/publish.yml:25-66`).
The Console workflow validates skills/version tooling and packages/smokes the
three native targets plus the macOS disk image before its tag-only GitHub
release job (`.github/workflows/console-release.yml:16-163`).

The ticket explicitly separates local snapshot preparation from final release-
commit verification. The latter remains gated on SC5 proving actual Sidecar
pre-dispatch validation/authorization, asynchronous JWT propagation through a
YAML planner to the REST handler and callback host, observer-history selection
for `NEVER`/`ONERROR`/`ALWAYS`, and packaged shutdown/listener/resource behavior.
None of those host-level contracts can be inferred from this repository's
framework-only tests. No in-repository evidence currently records that SC5
integration, the actual framework commit installed for Sidecar, or final manual
Console Release/Maven Central Release validation. The absence is consistent
with the gate remaining pending and does not authorize tagging or publication.

## Code References

- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/api/SkillTemplate.java:11-25` — supported validation and invocation overloads.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/api/RestSkillHandler.java:4-7` — sole supported handler SPI signature.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/api/RestSkillInvocation.java:11-43` — immutable handoff construction.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java:51-56` — exact REST-forbidden field set.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java:176-203` — REST/model-backed branch and required fields.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java:307-324` — raw `rest: true` and presence validation.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skill/YamlSkillCapabilityRegistrar.java:35-92` — shared registration, exactly-one handler, and null-result boundary.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/ExecutionCoordinator.java:128-150` — direct Java/REST execution, reference resolution, and scoped authentication.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skillapi/DefaultSkillCatalog.java:20-46` — eager immutable exact-schema public catalog.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skillapi/DefaultSkillTemplate.java:77-195` — validation preparation, root role precheck, invocation, and observation.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/LoomspanSessionRunner.java:215-278` — one admitted root and completion/failure precedence.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/FrameworkExecutionLifecycle.java:53-187` — root admission, close event, shared wait, cutoff, and fallback.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/autoconfigure/LoomspanProperties.java:287-305` — positive 30-second shutdown default.
- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/integration/SupportedSurfaceIntegrationTest.java:42-222` — current combined public-surface scenario.
- `loomspan-spring-boot-starter/src/test/resources/skills/integration/supported-surface-skill.yml:1-7` — current direct, non-planning YAML fixture.
- `scripts/loomspan_version.py:103-121,187-243` — version coordination and release-tag rules.
- `.github/workflows/publish.yml:25-66` — build-only manual validation and tag-only Maven publication.
- `.github/workflows/console-release.yml:16-163` — build-only manual native release validation and tag-only GitHub release.

## Architecture Documentation

The beta 4 implementation reuses one shared capability identity/registry and
one root/mission lifecycle rather than introducing REST-specific planning,
session, observation, or catalog infrastructure. REST differs at the declaration
and invoker boundary: the YAML catalog creates a leaf definition with no model
configuration, registry completion binds all REST names to the one application
handler, and the coordinator dispatches the kind through the existing direct
Java/REST branch. Input validation, exact-name lookup, role evaluation,
reference resolution, mission deadlines, quotas, success credit, tracing,
observation, and shutdown ownership remain shared.

The application-facing architecture has two deliberately different catalog
views. `SkillCatalog` is the supported, eager, immutable, unfiltered application
snapshot containing only public metadata and exact tool schemas. The
observability registered-skill catalog is internal adapter machinery that also
contains declaration locations/text for trusted operator diagnostics. Injection
of the public catalog does not grant authorization or a replacement contract.

Root validation and root invocation also remain deliberately distinct.
`validate` performs current lookup/input/root authorization without session or
admission side effects. `invoke` repeats input/access enforcement, crosses root
admission, and performs nested authorization as work executes. This separation
allows an asynchronous host to reject known-bad requests before dispatch without
turning validation into a reservation token.

Root ownership spans finalization and public observer delivery. That makes the
same shutdown budget cover execution, trace completion, mapping, success or
available-failure observation, cutoff, and executor cleanup. The owning context
close event closes admission promptly; Spring's later maximum-phase lifecycle
stop performs bounded waiting, allowing lower-phase resources to remain alive.
Host-specific dispatch/queue shutdown remains outside this framework owner.

## Historical Context (from ai/thoughts/)

- `ai/thoughts/phases/beta4-rest-skills-and-sidecar-roadmap.md` is the current
  cross-repository product map. It assigns REST contracts to the framework,
  handler routing/JWT/queue behavior to Sidecar, exact project-version Console
  compatibility, framework-first release order, and the SC5 integration gate.
- `ai/thoughts/phases/phase-fw4.md` defines this documentation/readiness phase:
  complete README and agent guidance, cumulative public-surface proof, beta
  compatibility notes, release-command correction, local dry runs, and final
  verification only after Sidecar integration.
- `ai/thoughts/beta4-ticket-readiness.md` assigns feature correctness to PRs
  5.1-5.3 and cumulative documentation/readiness to PR 5.4. It explicitly says
  framework tests do not establish Sidecar servlet/JWT/queue/container wiring.
- `ai/thoughts/beta4-design-review.md` records the accepted one-root completion
  path, independent prompt close listeners, framework lifecycle wait, public
  catalog/validation shape, observer precedence, and project-version marker.
- `ai/thoughts/beta4-code-grounding.md` describes the pre-feature state at
  `1e4eb455`. Its test runs and absence findings are historical only; the live
  checkout now contains PRs 5.1-5.3.
- `ai/thoughts/tickets/loomspan-pr-5.1-framework-lifecycle.md` owns the framework
  shutdown/admission/root-lifetime implementation now visible in
  `FrameworkExecutionLifecycle` and `LoomspanSessionRunner`.
- `ai/thoughts/tickets/loomspan-pr-5.2-rest-skills-and-console.md` owns the REST
  manifest/SPI/direct execution and coordinated Console changes now present.
- `ai/thoughts/tickets/loomspan-pr-5.3-external-caller-api.md` owns the catalog,
  both validation overloads, and failure observer behavior now present.

## Related Research

There were no existing files under `ai/thoughts/research/` in this checkout.
The historical design, grounding, readiness, phase, and feature-ticket files
listed above are the related context.

## Open Questions

These are pipeline-planning questions, not unresolved product decisions:

1. The ticket requires beta 4 compatibility/release notes, but the repository
   has no existing changelog or release-notes location. Planning must choose the
   durable artifact location and connect it from the consumer-facing README.
2. The cumulative fixture must become a YAML planner while continuing to reuse
   `SupportedSurfaceIntegrationTest`; its current scripted provider exchange is
   direct tool calling under `planning_mode: false`. Planning must map the
   existing fixture to the already-tested planner protocol without creating a
   second harness.
3. The ticket requires recording the actual installed snapshot commit and later
   SC5/final-workflow evidence. Planning must choose the in-repository readiness
   record that can explicitly show preparation complete while the external gate
   and final release-commit verification remain pending.
4. The globally installed `loomspan-docs` copy is stale relative to this checkout.
   This does not block implementation using the checked-in package, but exact
   post-change verification must continue to use the repository copy or a newly
   installed package bearing the matching version.
