---
date: 2026-09-12T16:31:31-07:00
researcher: Codex
git_commit: 4abac37ac43ece985dcd53a490a8feadb4b696a1
branch: main
repository: loomspan-framework
topic: "REST skills and Console integration"
tags: [research, codebase, rest-skills, console, public-api, manifests]
status: complete
last_updated: 2026-09-12
last_updated_by: Codex
---

# Research: REST skills and Console integration

**Date**: 2026-09-12T16:31:31-07:00  
**Researcher**: Codex  
**Git Commit**: `4abac37ac43ece985dcd53a490a8feadb4b696a1`  
**Branch**: `main`  
**Repository**: `loomspan-framework`

## Research question

How does the current Loomspan codebase represent, register, validate, execute, authorize, observe, and display skills, and which existing ownership boundaries are involved in implementing `ai/thoughts/tickets/loomspan-pr-5.2-rest-skills-and-console.md`?

The ticket is the binding specification for the new behavior. This document records current implementation facts, the ticket-to-code correspondence, and the tests and protocol consumers that currently enforce those facts. It does not implement the feature.

## Repository state and sources consulted

- Repository: `loomspan-framework`
- Branch: `main`
- Commit: `4abac37ac43ece985dcd53a490a8feadb4b696a1`
- Product version: `1.0.0-beta.4-SNAPSHOT` (`pom.xml:9`)
- Primary specification: `ai/thoughts/tickets/loomspan-pr-5.2-rest-skills-and-console.md`
- Design context: `ai/thoughts/framework-feature-design-lens.md`, `ai/thoughts/roadmap.md`, `ai/thoughts/phases/phase-fw1.md`, `ai/thoughts/phases/phase-fw3.md`, `ai/thoughts/beta4-ticket-readiness.md`, and `ai/thoughts/beta4-design-review.md`
- Executable sources and tests under `loomspan-spring-boot-starter`, `loomspan-observability`, `loomspan-console`, and `loomspan-console-fixtures`

The installed `loomspan-docs` skill was invoked as required. Its metadata declares `0.1.0-SNAPSHOT`, so it is not version-aligned with this `1.0.0-beta.4-SNAPSHOT` checkout. Its routing and reference material were read, but version-sensitive conclusions below are grounded in the checkout's matching `agent-skills/loomspan-docs` package and executable source. The matching documentation currently describes the implemented pre-REST world: eight supported Java API types, YAML and Java skills, and no supported SPI. REST manifests and a REST handler are not present in that documentation because the ticketed feature is not yet implemented.

## Executive summary

Loomspan currently has one shared capability registry and two capability kinds: YAML and Java. YAML manifests are loaded into `YamlSkillDefinition`, registered by `YamlSkillCapabilityRegistrar`, and executed through the model-backed engine. Java methods are discovered by `SkillMethodBeanPostProcessor`, registered in the same registry, and executed through the direct branch in `ExecutionCoordinator`. Authorization, nested tool accounting, task/evidence credit, trace finalization, and facade exception wrapping sit outside those authoring-specific adapters.

The ticket's REST skill is therefore a third registration kind but a second direct-execution source. Its application callback has to be introduced deliberately into the closed public API, while its implementation remains within the existing registry/router/coordinator lifecycle. The current code contains no conditional application bean replacement surface and no supported SPI; the two ticketed public types are a deliberate expansion rather than an extension of an existing mechanism.

The YAML loader currently assumes every manifest is model-backed and requires `model`. REST support changes both raw-field validation and the typed `YamlSkillManifest`/`YamlSkillDefinition` invariants. Registration completion is the point where Java discovery has finished, YAML definitions are available, duplicate names are detected, and allowed-child references are validated; it is also the existing startup boundary at which the ticket's exactly-one-handler rule can be evaluated.

Invocation inputs pass through public facade validation, the execution router, the coordinator, scoped authentication, and reference resolution before a capability invoker is called. Generic validation and binding include shallow-copy paths, so the new public `RestSkillInvocation` must independently make a deep immutable snapshot of maps and lists while preserving nulls and resolved Spring `Resource` handles. `SkillExecutionEvent` contains a related recursive immutable-copy implementation, but its accepted leaf types do not currently include `Resource`.

The Console is an atomic cross-language protocol consumer. Java observability snapshots, Go DTO validation/service mapping, MCP schema/goldens, TypeScript discriminated unions, React source labels/detail rendering, and committed Java-generated fixtures all currently encode exactly YAML or Java. REST is ticketed to reuse the manifest source path and raw YAML shape, so every layer must recognize `REST` as distinct from `YAML` while retaining those manifest fields.

## Current public surface and compatibility boundary

`ai.loomspan.api` is deliberately closed:

- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/api/package-info.java:1-9` describes the package as the supported public API.
- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java:29-37` names the exact eight supported types.
- The same test has exact allowlists at lines 278-305, asserts that no SPI package exists at lines 327-330, and recursively prevents public API signatures from exposing `internal` or `autoconfigure` types starting at line 334.
- `README.md:141-170` documents YAML/Java invocation and the eight supported types, and explicitly says Loomspan exposes no supported SPI or bean override contract.

The ticket makes `RestSkillHandler` and `RestSkillInvocation` supported application-facing types. Under the repository rules, that means both must be top-level types in `ai.loomspan.api`, appear deliberately in the architecture allowlists, and be documented in the README. Public signatures cannot expose types from `ai.loomspan.internal` or `ai.loomspan.autoconfigure`.

No production source under the starter currently uses `@ConditionalOnMissingBean`. Consequently, defining the REST handler as an ordinary optional application bean does not build on an existing bean-replacement convention. The handler is the single named SPI authorized by the ticket; internal registrars, registries, and auto-configuration types remain implementation details.

### Existing immutable public snapshot precedent

`SkillExecutionEvent` is a public record whose constructor recursively copies map, list, and array values (`SkillExecutionEvent.java:20-69`). It preserves null scalar values and accepts strings, numbers, booleans, and characters; unsupported leaf types fail. This is the nearest supported-surface precedent for detached immutable run values. The ticket's REST invocation has a different accepted leaf set because reference resolution may produce Spring `Resource` handles. The new record's own constructor is the final ownership boundary regardless of upstream normalization.

## Manifest loading and validation

### Current representation

`YamlSkillManifest` tracks which optional execution fields were declared via its `Field` enum (`YamlSkillManifest.java:28-53`). The tracked set covers model, thinking level, prompt, schemas, planning mode, concurrency, max steps, allowed skills, linter, and structured-output retry configuration. It has no `rest` property. Setters mark fields as declared, while `rbac_roles` is not tracked because it is valid across authoring styles.

The class also contains private `normalizeStringListMap` and `normalizeStringList` helpers near lines 670-686. Repository-wide reference search finds no callers outside their definitions, matching the ticket's cleanup note.

`YamlSkillDefinition` stores manifest, source metadata, validated schemas, evidence, linter data, and a nullable `SkillExecutionConfiguration` (`YamlSkillDefinition.java:14-20`). Its compact constructor nevertheless rejects a null execution configuration and describes that invariant as LLM-backed (`YamlSkillDefinition.java:25-49`). `requireExecutionConfiguration()` repeats that assertion at lines 133-139. The record already has the storage shape needed to distinguish a non-model-backed definition, but its invariants currently prohibit it.

### Current loader sequence

`YamlSkillCatalog` owns deterministic resource discovery and parsing:

1. It finds YAML resources under the configured locations and sorts/deduplicates them (`YamlSkillCatalog.java:94-155`).
2. `readManifest` reads the resource into a raw Jackson object before typed binding (`YamlSkillCatalog.java:236-284`). It extracts name/description for diagnostics, rejects mapping-form `allowed_skills` by raw shape at lines 256-260, and performs raw concurrency/allowed-skill checks before converting to `YamlSkillManifest`.
3. `loadDefinition` validates required metadata and concurrency and currently requires `model` for every manifest at lines 158-180.
4. It validates schemas, evidence, and linter configuration, resolves model/execution configuration at lines 186-200, and constructs `YamlSkillDefinition`.

Invalid-manifest construction near lines 980-1006 includes the resource and, when known, skill name and field. That diagnostic path is the current source of field-specific startup errors.

### Ticketed manifest matrix mapped to the loader

The ticket defines `rest: true` as the only REST declaration. `rest: false` and omission remain ordinary model-backed manifests. For `rest: true`, only `name`, `description`, `rest`, `rbac_roles`, `input_schema`, and `output_schema` are allowed; model, prompt, planning, child-skill, concurrency, linter, and retry fields are forbidden. A model-backed manifest continues to require `model` and follows existing validation.

This matrix intersects both raw parsing and typed validation. Unknown-field and malformed-value diagnostics are currently established during raw binding, whereas field declaration tracking determines whether a forbidden field was present even when its value is null or default-like. `YamlSkillDefinition` must then represent REST definitions without an execution configuration, while its model-only accessor remains the assertion boundary used by model execution and model registration.

`SkillInputContractResolver.resolveYamlCapability` returns an explicit schema contract when `input_schema` is present and `SkillInputContract.genericObject()` otherwise (`SkillInputContractResolver.java:39-49`). That same manifest-owned input contract is the current location for REST schema validation semantics.

## Registration, naming, and startup lifecycle

### Shared registry

`InMemoryCapabilityRegistry` stores exact capability names in one map (`InMemoryCapabilityRegistry.java:13-48`). Duplicate registration throws `CapabilityCollisionException` and reports both the existing and incoming metadata identifiers. The registry does not branch on kind, so name uniqueness already spans YAML and Java and can also span REST.

`CapabilityKind` currently contains `YAML_SKILL` and `JAVA_SKILL` only. `CapabilityMetadata` keeps kind separate from `SkillSource`, authorization policy, input contract, execution descriptor, and invoker. `SkillSource` is a record of `sourcePath`, `beanName`, and `method`; it is declaration-location metadata, not a kind discriminator. REST manifests can therefore use the same source-path representation while retaining a distinct kind.

`CapabilityRegistry.java:5-8` has stale Javadoc describing a public registry of YAML-authored capabilities and claiming non-YAML registrations must be rejected. The implementation already registers Java skills, and the type is internal. This is the stale Javadoc identified by the ticket.

### Existing producers

Java discovery registers public `@Skill` methods from `SkillMethodBeanPostProcessor` with:

- registration id `beanName#method`,
- `CapabilityKind.JAVA_SKILL`,
- reflected input contract,
- annotation-derived authorization,
- an invocation adapter,
- bean/method `SkillSource` (`SkillMethodBeanPostProcessor.java:202-218`).

YAML registration occurs in `YamlSkillCapabilityRegistrar.completeRegistration()` (`YamlSkillCapabilityRegistrar.java:8-50`). It first completes Java discovery, then registers every YAML definition with a model execution descriptor, YAML roles, a placeholder model-required invoker, YAML kind, tool schema, input contract, and source path. After registration it validates all `allowed_skills` references against the shared registry.

This method is an eager startup completion boundary: all authoring sources are reconciled before the registered-skill catalog is snapshotted or runtime execution begins. The ticket's handler-count invariant depends on the set of loaded REST manifests, so it belongs to this same completed-registration lifecycle rather than per invocation.

`LoomspanAutoConfiguration` currently creates the shared registry, Java post-processor, YAML catalog, registrar, input services, access guard, execution router, coordinator, and public `SkillTemplate` (`LoomspanAutoConfiguration.java:74-275`, `342-429`). The registrar bean currently receives the registry, post-processor, catalog, and input-contract resolver. Application REST handler discovery has to enter this composition without creating a supported bean-replacement surface for the internal components.

## Execution and authorization lifecycle

### Public invocation and validation

`DefaultSkillTemplate` resolves a capability, validates input before opening a session, captures the caller's Spring Security authentication, and starts the session (`DefaultSkillTemplate.java:91-142`). It preserves `AccessDeniedException` and Loomspan `SkillException` instances, but wraps other runtime exceptions with `Skill '<name>' execution failed.`. `executeValidated` converts a successful result with `String.valueOf(result)` at lines 145-150, which means a null invoker result would currently become the string `"null"`. The REST invoker must enforce the ticket's `handler returned null` failure before that conversion; an empty string already remains a successful empty result.

`SkillInputValidator` handles the capability's resolved input contract. A generic contract returns an unmodifiable outer map but does not recursively copy it (`SkillInputValidator.java:20-37`). Explicit-schema validation recursively normalizes constrained objects and arrays, but unconstrained `additionalProperties` and arrays without item schemas contain shallow-copy paths (`SkillInputValidator.java:68-193`). Attachments accept resolved Spring `Resource` values (`SkillInputValidator.java:310-336`). These facts make the REST invocation constructor's independent deep snapshot observable and necessary, especially for generic and loosely structured schemas.

### Router and coordinator

`CapabilityExecutionRouter` checks authorization, validates input again, and calls the coordinator inside the current execution binding (`CapabilityExecutionRouter.java:39-68`).

`ExecutionCoordinator` loads a `YamlSkillDefinition` only for `YAML_SKILL`, constructs the mission context and binding, opens the mission frame, and uses the direct input-trace sanitizer whenever there is no YAML definition (`ExecutionCoordinator.java:77-145`). The current direct branch tests only `JAVA_SKILL`; it resolves authorization, runs through the shared `MissionWorkExecutor`, opens `ScopedAuthentication`, resolves references, and invokes the capability. YAML continues into the model execution engine at lines 146-165.

This is the existing two-engine split the ticket preserves: model-backed YAML versus direct callbacks. Adding REST to the direct-kind predicate keeps mission frames, authorization, input resolution, failure recording, cleanup, and finalization on the existing path. No third execution engine is implied by the current architecture or ticket.

`RefResolver` recursively traverses generic maps, lists, and arrays and resolves reference leaves (`RefResolver.java:18-98`). Explicit contracts traverse schema-shaped values and resolve runtime-reference-capable and attachment nodes. This occurs before the capability invoker, so a REST handler receives resolved input. `BoundCapability` then makes only a shallow outer-map copy before invoking (`BoundCapability.java:33-38`); the public invocation record remains the deep immutable handoff boundary.

### Authentication scope and exception semantics

`ScopedAuthentication` saves the exact previous Spring Security context, installs a fresh context containing the captured caller authentication, and restores the previous context on close (`ScopedAuthentication.java:19-25`). Existing integration tests cover nested success and denial plus simultaneous workers (`JavaSkillAuthenticationScopeIntegrationTests.java:25-71`). Because REST joins the same direct branch, its handler runs under the same captured context and cleanup mechanism.

The current Java reflection adapter catches runtime failures, rethrows security and argument failures, and transforms other exceptions into logged text (`SkillMethodBeanPostProcessor.java:474-520`). The ticket explicitly gives REST different semantics: the framework invokes the handler directly, its runtime exception propagates through the coordinator, public `SkillTemplate` applies its existing wrapping rule, and Java-to-Java nested invocation sees the original runtime exception. REST must therefore not reuse the Java reflection adapter's return-shaping behavior.

The coordinator records a canonical failure before finalization and uses `TraceFailureMetadata` to expose only exception type and a fixed safe message (`ExecutionCoordinator.java:167-193`, `321-368`; `TraceFailureMetadata.java:9-27`). This is the existing no-sensitive-message trace boundary that applies to direct failures.

## Nested tools, task credit, evidence, and traces

`DefaultCapabilityInvoker` is the shared nested-capability adapter. It opens a `TOOL_INVOCATION` frame under the capability name, calls the execution router, and only after successful return records task completion or successful-skill evidence (`DefaultCapabilityInvoker.java:57-128`). Its failure path records tool/failure metadata and rethrows without granting success credit (`DefaultCapabilityInvoker.java:135-195`). Because capability visibility and binding are registry-driven, a registered REST kind inherits this lifecycle without a REST-specific tool-accounting implementation.

`ExecutionCoordinator` supplies the nested mission frame and guarantees finalization/cleanup for both direct and model branches. Current trace frames and the public execution event projection do not include a capability-kind field; `ExecutionJournalProjector` projects root mission and skill-execution frames based on frame type, not authoring source. The ticket's REST-specific observability change therefore concerns registered-skill catalog metadata, not a new runtime trace discriminator.

## Registered-skill observability and Console protocol

### Java producer

`DefaultRegisteredSkillCatalog` forces registrar completion, takes a sorted registry snapshot, and maps each capability into `RegisteredSkillEntry` (`DefaultRegisteredSkillCatalog.java:23-75`). YAML entries fetch the definition and emit source path plus raw YAML. Every non-YAML entry is currently labeled `JAVA` and populated from bean/method source fields. A third kind would be misclassified by this binary branch until it is explicit.

`RegisteredSkillEntry` currently contains `registeredName`, `source`, `sourcePath`, `beanName`, `method`, and `yaml` (`RegisteredSkillEntry.java:6-40`). Its location validator accepts only `YAML` with source path/YAML text or `JAVA` with bean/method. `ObservabilityDtoMapper` and `ObservabilityDtos` preserve and revalidate that shape. Under the ticket, REST uses the manifest location/text fields but has source `REST`; this makes source kind and declaration location independent in the producer contract.

### Go adapter and MCP

The Console Go DTOs mirror the Java fields (`loomspan-console/internal/observability/dto.go:23-40`). Raw decoding validation currently treats Java specially and all other sources as YAML-shaped at lines 130-148. Service validation then explicitly accepts only `YAML` and `JAVA` and enforces their location requirements (`loomspan-console/internal/observability/service.go:281-314`). Both layers must recognize REST as a manifest-backed source.

The MCP skills adapter prints bean/method for Java and source path/YAML for the other branch (`loomspan-console/internal/mcpadapter/skills.go:120-155`). Its committed tool schema/golden at `loomspan-console/internal/mcpadapter/testdata/tools-list-response.json` currently enumerates only YAML and Java variants. REST changes the typed contract and the corresponding generated/golden response.

### TypeScript and React

`loomspan-console/web/src/api/contracts.ts:124-136` defines skill summary/detail as YAML-or-Java discriminated unions. `SkillCatalog.tsx:67-68` labels Java or YAML and chooses bean/method versus source path. `SkillDetail.tsx:66-83` has the same binary label and displays manifest text only when source is exactly YAML. REST requires a third union member with manifest-backed location and text, plus explicit REST labels in both screens.

Current component tests cover YAML and Java examples (`SkillCatalog.test.tsx:37-44`; `SkillDetail.test.tsx:62-123`). Browser/API tests also contain literal YAML skill fixtures. These are current consumer assertions rather than independent protocol authorities.

### Canonical fixtures

`ConsoleRestFixtureCorpusTest` is the source of truth for committed Java-to-Console REST fixtures. It generates a temporary corpus, and with `-Dloomspan.console.fixtures.regenerate=true` replaces committed fixtures, then checks the exact file list and byte identity (`ConsoleRestFixtureCorpusTest.java:40-73`). Its fixture map at lines 137-172 currently contains a skill list, a YAML detail, and a Java detail. The committed `application-rest/skills-page.json` contains CheckDns (YAML) and LookupDns (Java); `skill-detail.json` is the YAML detail.

The ticket adds a REST catalog entry and `application-rest/skill-rest-detail.json`. Because Go DTO/service tests consume the Java-generated corpus and TypeScript fixtures mirror the same wire shape, fixture regeneration, Go validation, UI contracts, and MCP goldens form one atomic protocol update. The Console repository guidance confirms this cross-language atomicity and specifies `go test ./...` and `go run ./internal/buildtool verify` as its normal verification commands.

## Same-version compatibility marker

The root and starter Maven projects use `1.0.0-beta.4-SNAPSHOT`. The starter filters `META-INF/loomspan-release.properties`, and `LoomspanReleaseVersion` loads the resulting console compatibility version. `DefaultExecutionTraceHandle` writes that marker into canonical traces (`DefaultExecutionTraceHandle.java:306`).

The Console application client parses the marker and requires exact string equality with its expected version (`loomspan-console/internal/applicationclient/client.go:200-233`). The trace processor similarly requires a present, nonblank marker and exact equality (`loomspan-console/internal/traceview/processor.go:73-76`, `137-140`, `501-514`). Missing, non-string, or unequal markers are already rejected. The ticket extends the current beta.4 protocol and explicitly does not require an old Console to read the expanded payload; it does not introduce a compatibility shim or migration reader.

## Design-lens classification

Using `ai/thoughts/framework-feature-design-lens.md`, the affected surfaces classify as follows:

| Surface | Classification | Current or ticket evidence |
|---|---|---|
| `SkillTemplate`, existing API values/errors | Application API | Existing closed allowlist and README contract |
| `RestSkillHandler`, `RestSkillInvocation` | Supported SPI | Explicitly authorized by the ticket and must join the closed allowlist |
| `rest` manifest flag, field matrix, handler-count startup rule, `rbac_roles` behavior | Configuration and manifest contracts | User-authored YAML and deterministic startup validation |
| Registry, registrar, coordinator, resolvers, auto-configuration | Internal or accidentally exposed implementation | Packages under `ai.loomspan.internal`/`autoconfigure`; no supported bean override surface |
| Registered-skill Java DTO, Go DTO/service, TypeScript union, fixtures | Protected application-adapter protocol consumers | Same-version Java/Console wire contract; must change atomically |
| Registered-skill and canonical trace JSON/NDJSON | Ephemeral diagnostic formats | Console diagnostic transport; exact compatible same-version artifacts are the narrow portable case |
| Existing trace artifacts bearing an exact version marker | External durable objects only within the existing narrow same-version promise | Readers reject missing or unequal markers; no cross-version promise |

No new cross-version persisted format is introduced by the ticket.

## Test ownership and verification map

The existing test suite has clear owners for the ticketed behavior:

- `LoomspanPublicSurfaceArchitectureTest`: exact public type/package/signature allowlists and no accidental internal exposure.
- `YamlSkillCatalogTests`: raw and typed manifest parsing, unknown fields, required model, schema loading, declaration-specific validation, and resource-rich diagnostics.
- `YamlSkillCapabilityRegistrarTests`: startup completion, shared registration, duplicate collision, and allowed-child validation.
- `DefaultSkillTemplateTest`: pre-session validation, authentication capture, facade preservation/wrapping rules, error behavior, and observer lifecycle.
- `JavaSkillAuthenticationScopeIntegrationTests`: nested and concurrent security-context scoping.
- `ExecutionCoordinatorTest` and focused nested-success/failure tests: direct lifecycle, mission/tool frames, failure/finalization behavior, and credit boundaries.
- `SupportedSurfaceIntegrationTest`: public API plus YAML-parent/Java-leaf end-to-end proof; the existing integration harness already exercises public invocation, nesting, and observation.
- `ConsoleRestFixtureCorpusTest`: canonical Java producer payloads and committed fixture identity.
- Go observability DTO/service tests and MCP adapter goldens: wire decoding, invariant validation, and tool schema/output.
- React API/component tests and browser tests: discriminated contracts, REST labels/location, and manifest rendering.

The repository and Console guidance identify the relevant verification commands as the normal Maven test suite (including the architecture test), canonical fixture regeneration followed by a clean fixture check, `go test ./...`, and `go run ./internal/buildtool verify`. Windows race testing is environment-dependent per the Console guidance and is not the primary buildtool verification path.

## Code references

- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java:29-37` — exact supported API type set.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/api/SkillExecutionEvent.java:20-69` — existing recursive immutable public snapshot behavior.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skill/YamlSkillManifest.java:28-53` — declared manifest execution-field inventory.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java:158-209` — model-backed definition construction.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java:236-284` — raw manifest validation and binding.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skill/YamlSkillCapabilityRegistrar.java:8-50` — completed discovery and shared registration boundary.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/ExecutionCoordinator.java:77-193` — direct/model execution split, scoped invocation, failure capture, and cleanup.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/runtime/tool/DefaultCapabilityInvoker.java:57-195` — nested tool frames and success/failure credit.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/runtime/observation/catalog/DefaultRegisteredSkillCatalog.java:23-75` — registered-skill snapshot mapping.
- `loomspan-console/internal/observability/service.go:281-314` — Go YAML/Java source invariant checks.
- `loomspan-console/web/src/api/contracts.ts:124-136` — browser YAML/Java discriminated contracts.
- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/observability/web/ConsoleRestFixtureCorpusTest.java:40-73` — canonical fixture generation and byte-for-byte verification.

## Architecture documentation

`AGENTS.md` and `ai/thoughts/framework-feature-design-lens.md` establish the public/internal classification used above. The matching checkout documentation under `agent-skills/loomspan-docs` establishes the current YAML/Java authoring and invocation model. The Console-local `AGENTS.md` establishes that Java producer, Go adapter, TypeScript UI, and fixtures move atomically.

## Historical context from `ai/thoughts/`

- `ai/thoughts/roadmap.md:52-147` records the intended single optional handler, public API constraint, immutable invocation input, authorization, and direct execution shape.
- `ai/thoughts/phases/phase-fw1.md:133-160` records registration and direct lifecycle scope; lines 225-247 cover observability and cleanup.
- `ai/thoughts/phases/phase-fw3.md:40-69` records Console kind, source path/text, fixtures, and compatibility-marker scope.
- `ai/thoughts/beta4-ticket-readiness.md` groups REST and Console as one atomic delivery after PR 5.1.
- `ai/thoughts/beta4-design-review.md:111-139` records reuse of `SkillSource`, capability kind, and the existing supported-surface integration fixture.

## Related research

No earlier research artifact under `ai/thoughts/research/` was present in this checkout at the start of this step.

## Ticket-to-code change inventory

The binding ticket maps to these ownership areas:

1. **Supported API** — add the two public REST types; deep immutable constructor semantics belong to the invocation value; update exact architecture allowlists and README inventory.
2. **Manifest model and loader** — represent `rest`, retain declared-field information, apply the REST/model field matrix, and allow REST definitions to omit model execution configuration without weakening the model-only assertion.
3. **Startup registration** — add `REST_SKILL`, discover the optional application handler, enforce zero-or-one based on whether REST manifests exist, register REST definitions into the shared exact-name namespace, and retain manifest authorization/input/source metadata.
4. **Direct invocation** — treat REST as direct in the coordinator; resolve references and scope authentication before constructing the public invocation; call the handler without the Java reflection exception adapter; reject null before public string conversion.
5. **Shared runtime behavior** — continue to use router access checks, mission executor/finalization, nested tool invocation, and existing success/failure credit boundaries.
6. **Observability producer** — explicitly map REST as REST and retain source path/raw YAML rather than treating all non-YAML kinds as Java.
7. **Console protocol consumers** — extend Go invariants/MCP schema, TypeScript unions, catalog/detail UI, tests, and canonical fixtures in the same change.
8. **Compatibility and cleanup** — preserve exact-version marker behavior; correct registry Javadoc and remove the two unreferenced normalization helpers named by the ticket.
9. **Documentation** — update root README and the version-aligned `agent-skills/loomspan-docs` skill-authoring and Java API references so the new supported SPI, manifest syntax, lifecycle, authorization, input snapshot, return/null behavior, and exception differences are discoverable.

## Documentation impact assessment

**Skill-authoring impact: Affected.** REST is a new authoring mode expressed in the YAML manifest, with an exact field matrix, application-handler requirement, and different execution semantics. The current skill-authoring mental model says manifests are model-backed and the Java API material says there is no supported SPI, so version-aligned documentation must change with the code.

The checkout documentation and current executable source are aligned for the pre-ticket behavior. The material documentation delta is therefore the intentional addition of REST rather than correction of an already-shipped mismatch. Relevant version-aligned references include:

- `agent-skills/loomspan-docs/references/skill-authoring/README.md`
- `agent-skills/loomspan-docs/references/skill-authoring/mental-model.md`
- `agent-skills/loomspan-docs/references/skill-authoring/authorization.md`
- `agent-skills/loomspan-docs/references/skill-authoring/input-contracts.md`
- `agent-skills/loomspan-docs/references/java-api/README.md`
- `agent-skills/loomspan-docs/references/java-api/compatibility-and-boundaries.md`
- `agent-skills/loomspan-docs/references/java-api/invocation.md`
- `agent-skills/loomspan-docs/references/java-api/observation-and-errors.md`
- root `README.md`

The roadmap, phase documents, readiness review, and future tickets under `ai/thoughts/tickets/` remain useful design context for questions about how this feature fits later work. They are historical/planning inputs; executable source, tests, and the exact ticket remain authoritative for current implementation behavior.

## Open questions

No unresolved product question was found in Step 1. The ticket's Pipeline notes settle the points that would otherwise be ambiguous: the two new types are the sole supported SPI addition; handler cardinality is conditional on REST manifests; REST handler exceptions differ deliberately from Java reflection-adapter exceptions; and Console compatibility is exact-version beta.4 protocol expansion without an old-reader requirement.
