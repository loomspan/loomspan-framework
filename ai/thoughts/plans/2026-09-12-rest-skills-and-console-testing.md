# REST Skills and Console Integration Testing Plan

## Change Summary

- Add the first narrowly supported Loomspan SPI: one application `RestSkillHandler` receiving immutable `RestSkillInvocation` values.
- Add strict `rest: true` YAML declarations, conditional handler cardinality, shared registration, direct root/nested execution, authentication, authorization, lifecycle accounting, and visible failure semantics.
- Add a distinct REST registered-skill source across Java observability, canonical fixtures, Go Console validation, MCP tools, TypeScript contracts, React list/detail, and trace rendering under the existing exact beta 4 version policy.
- Update README and version-aligned Loomspan authoring/API guidance using the focused tests below as behavioral evidence.

## Impacted Areas

- Public API values and allowlists: `ai.loomspan.api`, `ApplicationApiValueTest`, `LoomspanPublicSurfaceArchitectureTest`.
- Manifest syntax/invariants: `YamlSkillManifest`, `YamlSkillCatalog`, `YamlSkillDefinition`, their tests and YAML fixtures.
- Startup/registration: `CapabilityKind`, `CapabilityRegistry` Javadoc, `YamlSkillCapabilityRegistrar`, `LoomspanAutoConfiguration`, registrar/auto-configuration tests.
- Runtime: REST invoker, `ExecutionCoordinator`, `DefaultSkillTemplate`, `ScopedAuthentication`, `DefaultCapabilityInvoker`, direct/nested/security/lifecycle tests.
- Java diagnostics: `DefaultRegisteredSkillCatalog`, `RegisteredSkillEntry`, observability DTO mapper/records/controllers, fixture corpus tests.
- Console protocol: Go observability DTO/service/browser API, MCP contracts/renderers/goldens, TypeScript discriminated unions/API fixtures, React catalog/detail/trace views, browser/e2e tests.
- Compatibility/versioning: existing application-client and trace-analysis marker tests plus coordinated build metadata.
- Documentation: root README and checkout `agent-skills/loomspan-docs` skill-authoring/java-api routing, focused topics, and coverage tables.

## Risk Assessment

- **High — validation ambiguity**: `rest` omission must remain model-backed, while every declared non-true value fails. Forbidden-field presence must be detected even when Jackson would normalize it to null/default.
- **High — accidental public expansion**: only the two ticketed `ai.loomspan.api` types may join the closed surface; internal registrars, DTOs, auto-configuration, and bean seams must remain unsupported.
- **High — mutable or unsafe handoff**: generic/loosely typed input paths are shallow today. Direct construction and runtime handoff must freeze nested maps/lists while preserving permitted nulls and resolved `Resource` identity without reading contents.
- **High — lifecycle divergence**: a new execution engine or bypass could lose authorization, scoped authentication, reference resolution, timeouts, fencing, trace finalization, or task/evidence credit. Tests must demonstrate reuse of the direct path.
- **High — exception regression**: REST exceptions intentionally propagate to facade wrapping, while Java reflection exceptions retain exception-to-text behavior. Null must fail before `String.valueOf`, but empty string must succeed.
- **High — atomic protocol mismatch**: Java may emit REST while Go/TypeScript/MCP still assume two sources. Canonical fixtures and all validators/renderers must change together.
- **Medium — handler discovery**: enumerating or instantiating handler beans when no REST manifests exist would violate the conditional rule; multiple-bean diagnostics must name deterministic bean names and zero-bean diagnostics every REST resource.
- **Medium — kind/source conflation**: REST reuses YAML declaration fields but is not YAML. Binary branches can silently misclassify it as Java or YAML.
- **Medium — concurrency/failure ordering**: nested REST failures must not earn credit or alter established parent primary-failure selection, sibling joins, cancellation, shutdown ownership, or security-context restoration.
- **Medium — diagnostic leakage**: REST detail must not expose handler bean/method, URLs, headers, credentials, or invented target metadata; trace fidelity/redaction stays current.
- **Protected compatibility paths**: all existing public `SkillTemplate` overloads/value/error behavior; Java and model-YAML manifests/invocation; exact names; YAML/Java source variants; Spring security semantics; exact-version and `development` marker handling.
- **Intentional changes/removals**: add exactly two supported SPI types; accept only the new coherent REST manifest; change internal two-kind switches/constructors atomically; remove unused manifest normalization helpers and stale registry Javadoc; no legacy DTO reader or internal shim.

## Existing Test Coverage

- `ApplicationApiValueTest` covers immutable public records and exception shapes, but has no REST invocation value.
- `LoomspanPublicSurfaceArchitectureTest` exactly allowlists eight types and asserts no SPI; it will deliberately change to ten types and one named SPI contract.
- `YamlSkillCatalogTests` and `YamlSkillDefinitionTest` cover raw/typed validation, declaration tracking, schemas, required model, and model-only invariants; they have no REST matrix.
- `YamlSkillCapabilityRegistrarTests` cover eager discovery, exact shared names, Java/YAML collisions, and child validation; they have no handler or third-kind cases.
- `DefaultSkillTemplateTest`, `ExecutionCoordinatorTest`, `JavaSkillAuthenticationScopeIntegrationTests`, and `DefaultCapabilityInvoker`-adjacent coverage protect facade failures, direct lifecycle, security context, nesting, and credit boundaries for existing kinds.
- `SupportedSurfaceIntegrationTest` already owns the local model stub + YAML planner + Java leaf public fixture; it should be extended instead of duplicated.
- `DefaultRegisteredSkillCatalogTest`, `ObservabilityDtoMapperTest`, and `ConsoleRestFixtureCorpusTest` enforce the Java producer and canonical fixture corpus for YAML/Java only.
- `loomspan-console/internal/observability/dto_test.go` and `service_test.go` cover wire presence/inapplicable fields and canonical fixtures; `internal/mcpadapter/skills_test.go` plus goldens cover MCP output.
- `SkillCatalog.test.tsx` and `SkillDetail.test.tsx` cover YAML and Java rendering; current TypeScript unions exclude REST. Existing e2e/API fixtures can exercise a third variant and direct trace rendering.
- `applicationclient/client_test.go` and `traceanalysis/processor_test.go` already cover exact resolved versions and `development`; these are regression guards, not a place to add a schema counter.

## Authoring Claims Requiring Evidence

The matching checkout documentation and executable behavior are **aligned (pre-change)** on YAML/Java-only authoring and no supported SPI. The change must establish and then document these new claims:

| Authoring/API claim | Executable evidence |
| --- | --- |
| Only literal `rest: true` declares REST; invalid values say to omit it | `YamlSkillCatalogTests` raw-value matrix and diagnostic assertions |
| REST's allowed/forbidden field matrix is exact, including null/empty presence | dynamic `YamlSkillCatalogTests` over every forbidden field plus valid minimal/schema/RBAC fixtures |
| Non-REST YAML still requires model | existing and explicit regression `YamlSkillCatalogTests` |
| REST manifests require exactly one application handler only when present | `YamlSkillCapabilityRegistrarTests` and `LoomspanAutoConfigurationTests` |
| REST shares exact names, allowed-child validation, input contracts, and YAML role policy | registrar, collision, visibility/access, and supported-surface tests |
| Handler input is validated/resolved and recursively immutable while nulls/Resource handles survive | `ApplicationApiValueTest`, resolver/runtime integration, and `SupportedSurfaceIntegrationTest` |
| Authentication is scoped to the actual handler thread and restored | generalized security integration tests including concurrent success/failure |
| REST is direct, earns credit only on success, and has no model attempts | coordinator/nested-tool/trace tests |
| Null fails, empty succeeds, REST runtime exceptions differ from Java adapter exceptions | invoker/facade/coordinator regression tests |
| Console displays REST with manifest path/text and no handler internals | Java/Go DTO tests, canonical fixture, MCP goldens, React component/e2e tests |
| REST protocol semantics require matching beta 4 artifacts; no separate counter/legacy reader | existing version-marker tests plus build/version verification |

## Bug Reproduction / Failing Test First

- **Type**: unit
- **Location**: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/skill/YamlSkillCatalogTests.java`
- **Proposed name**: `loadsRestSkillWithoutModelAndRejectsModelExecutionFieldsByPresence`
- **Arrange/Act/Assert outline**: create a minimal resource with `name`, `description`, and `rest: true`; assert it loads with no execution configuration, generic input contract eligibility, and REST classification. Parameterize companion resources that add each forbidden field with representative nonempty, empty, and null values and assert resource/skill/field diagnostics.
- **Expected failure (pre-fix)**: the loader rejects the valid resource because `rest` is not represented and `model` is required; it cannot enforce REST's declaration-presence matrix or construct a non-model definition.

This first red test proves the lowest-cost executable gap before wiring Spring beans or the Console. Add the public-type compile/shape tests in the same first slice; they will initially fail to compile until the ticketed types exist.

## Tests to Add/Update

### 1. Public REST SPI shape and immutable invocation

- **Type**: unit / architecture
- **Location**: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/api/ApplicationApiValueTest.java`; `.../architecture/LoomspanPublicSurfaceArchitectureTest.java`
- **What it proves**: exact functional method and record components; JDK-only signatures; only ten allowlisted API types; no new public internal/autoconfigure exposure; null component rejection; recursive outer/nested map/list copy; returned containers reject mutation; source mutations do not alter snapshots; null values and resolved `Resource` leaf identity survive; direct construction works.
- **Fixtures/data**: mutable linked maps/lists with nested combinations, null entries, and a test `Resource`.
- **Mocks**: none.
- **Affected surface**: Supported SPI / Application API boundary.
- **Compatibility expectation**: protected exact new SPI plus preserved existing closed API; no other extension point.

### 2. REST manifest raw-value and field-presence matrix

- **Type**: unit, parameterized/dynamic
- **Location**: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/skill/YamlSkillCatalogTests.java`; manifest test resources.
- **What it proves**: only boolean true is accepted; false/null/string/number/list/map fail with resource, skill, `rest`, and omit guidance; name/description remain required; `input_schema` and `rbac_roles` are optional/valid; every ticketed forbidden field fails by presence including null/empty; no schema uses generic object; explicit schema uses current validator.
- **Fixtures/data**: minimal valid REST YAML; a table of invalid raw REST values; a table over `model`, `prompt`, `thinking_level`, `allowed_skills`, `planning_mode`, `concurrency`, `max_steps`, `linter`, `output_schema`, `output_schema_max_retries` with type-appropriate values including null/empty.
- **Mocks**: resource resolver only as current tests use it.
- **Affected surface**: Configuration or manifest behavior.
- **Compatibility expectation**: intentional REST extension; existing non-REST paths protected.

### 3. Definition invariants and model resolution isolation

- **Type**: unit
- **Location**: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/skill/YamlSkillDefinitionTest.java`; `DefaultSkillChatModelResolverTests.java` if resolution assertions belong there.
- **What it proves**: REST permits only null execution configuration; model definitions still require one; `requireExecutionConfiguration()` rejects REST use; REST loading never invokes model resolution; declared-field state survives defensive manifest copies.
- **Fixtures/data**: REST and model manifests/definitions.
- **Mocks**: counting/failing model resolver for the REST case.
- **Affected surface**: Configuration or manifest behavior / Internal implementation.
- **Compatibility expectation**: protected model YAML behavior; coherent internal invariant change with no compatibility overload.

### 4. Conditional handler cardinality and registration metadata

- **Type**: unit / Spring context integration
- **Location**: `YamlSkillCapabilityRegistrarTests.java`; `LoomspanAutoConfigurationTests.java`.
- **What it proves**: zero handlers lists every REST resource; multiple handlers list deterministic bean names; exactly one registers all REST manifests; no REST manifests skip bean validation/resolution even with zero or multiple/lazy/failing handlers; completion is eager/idempotent; REST metadata has kind, none descriptor, YAML roles/path, contract/tool schema, and real invoker.
- **Fixtures/data**: zero/one/multiple REST manifests and handler bean definitions, including lazy handler that records construction.
- **Mocks**: current registry/catalog/input resolver fakes plus Spring `ApplicationContextRunner` where appropriate.
- **Affected surface**: Configuration or manifest behavior / Supported SPI / Internal implementation.
- **Compatibility expectation**: intentional conditional rule authorized by Pipeline notes; no handler rule without REST manifests.

### 5. Shared exact-name collisions and child references

- **Type**: unit / startup integration
- **Location**: `YamlSkillCapabilityRegistrarTests.java`; `InMemoryCapabilityRegistryTest.java` only if generic collision reporting needs strengthening.
- **What it proves**: REST/YAML, REST/Java, and REST/REST duplicates fail and show both declaration locations; exact case remains distinct; YAML `allowed_skills` can resolve REST after registration; unknown children still fail after complete discovery.
- **Fixtures/data**: manifest resource paths, Java bean/method metadata, exact/case-variant names.
- **Mocks**: existing registrar fakes.
- **Affected surface**: Configuration or manifest behavior.
- **Compatibility expectation**: protected shared exact-name contract extended to REST.

### 6. REST invoker return and exception boundary

- **Type**: unit
- **Location**: new focused REST invoker test if a named class is introduced; `DefaultSkillTemplateTest.java`.
- **What it proves**: handler is called once with correct name/input; null throws with `handler returned null` before text conversion; empty string succeeds; `AccessDeniedException` instance is unwrapped; existing `SkillException` message/instance is preserved; other runtime failures become safe `SkillException` with cause; JVM `Error` is not caught; observer remains success-only.
- **Fixtures/data**: handlers returning null/empty/text and throwing each failure category.
- **Mocks**: handler lambda and current facade/session/router fakes.
- **Affected surface**: Supported SPI / Application API.
- **Compatibility expectation**: ticketed REST behavior; existing Java exception-to-text tests remain protected unchanged.

### 7. Direct coordinator lifecycle, resolution, and traces

- **Type**: unit / integration
- **Location**: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/ExecutionCoordinatorTest.java`; focused trace tests as currently owned.
- **What it proves**: REST selects direct execution, receives reference-resolved validated input, uses mission executor, closes/finalizes frames on success/failure, enforces quotas/depth/timeouts/shutdown/fences, emits mission/tool frames under skill name, emits no model attempts and no new kind field, and retains bounded/redacted failure diagnostics.
- **Fixtures/data**: explicit attachment/reference input, resource handle, runtime failure with sensitive message, cancellation/timeout controls.
- **Mocks**: current deterministic session, clock, executor, trace, and model fakes; model fake should fail if called.
- **Affected surface**: Internal implementation / Ephemeral diagnostics.
- **Compatibility expectation**: current-run diagnostic coherence and protected lifecycle behavior.

### 8. Scoped authentication and restoration

- **Type**: integration
- **Location**: `JavaSkillAuthenticationScopeIntegrationTests.java` generalized/extended, or a neighboring REST-specific integration test sharing its fixture.
- **What it proves**: root and nested handler reads captured caller authentication on the actual worker thread; role denial prevents handler entry; exact prior worker security context is restored after success and failure; simultaneous callers remain isolated.
- **Fixtures/data**: authorized/unauthorized authentications and barriers for concurrent handlers.
- **Mocks**: application handler beans; no internal bean replacement.
- **Affected surface**: Supported SPI / Configuration or manifest behavior.
- **Compatibility expectation**: protected Spring Security scope and YAML role semantics.

### 9. Nested task/evidence credit and parent failure behavior

- **Type**: unit / integration
- **Location**: existing `DefaultCapabilityInvoker` and `ExecutionCoordinatorTest` nested/concurrency cases.
- **What it proves**: successful REST child earns task, required-child, and evidence credit exactly once; denial, cancellation, null, and failure earn none; parent resumes/fails under existing sequential and concurrent join rules; primary failure is not reconstructed from event order and access denial gains no new precedence.
- **Fixtures/data**: plans/evidence expressions targeting REST, controlled concurrent sibling outcomes.
- **Mocks**: existing planner/tool/session fakes and latches.
- **Affected surface**: Internal implementation / Ephemeral diagnostics.
- **Compatibility expectation**: protected lifecycle and current-run ordering semantics.

### 10. Supported-surface end-to-end composition

- **Type**: integration
- **Location**: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/integration/SupportedSurfaceIntegrationTest.java` plus its current resources/stub.
- **What it proves**: application supplies public handler bean; public `SkillTemplate` invokes a REST root and an LLM-backed YAML planner invokes REST and Java leaves; handler sees validated/resolved immutable input and caller context; role denial and authorized success behave correctly; observation uses public Java-shaped values; no internal type is needed by the application fixture.
- **Fixtures/data**: extend the existing local model stub, planner manifest, REST manifest, REST handler, and Java leaf.
- **Mocks**: local protocol-compatible model server already owned by the test; no parallel harness.
- **Affected surface**: Application API / Supported SPI / Configuration or manifest behavior.
- **Compatibility expectation**: protected supported-surface composition with intentional SPI addition.

### 11. Java registered-skill producer and REST location invariants

- **Type**: unit / web integration
- **Location**: `DefaultRegisteredSkillCatalogTest.java`; `ObservabilityDtoMapperTest.java`; relevant observability endpoint tests.
- **What it proves**: REST is never aliased to Java/YAML; summary/detail use `REST`, source path, and YAML text only; Java remains bean/method-only; YAML remains manifest-backed; unknown kinds and inapplicable fields fail; list/detail sorting, pagination, and lookup remain stable; no handler internal or target field appears.
- **Fixtures/data**: one entry of each kind and malformed variants.
- **Mocks**: registry/catalog/registrar/path resolver as current tests use them.
- **Affected surface**: Ephemeral diagnostics.
- **Compatibility expectation**: current-version producer coherence.

### 12. Canonical Java-to-Console REST fixtures

- **Type**: fixture integration/golden
- **Location**: `ConsoleRestFixtureCorpusTest.java`; `loomspan-console-fixtures/application-rest/skills-page.json`; new `skill-rest-detail.json`.
- **What it proves**: canonical page contains YAML/REST/Java variants; REST detail has exact source path/text and no handler fields; declared fixture set and bytes are deterministic; repeated regeneration is a no-op.
- **Fixtures/data**: Java producer-generated corpus only.
- **Mocks**: canonical fixture builder already in the test.
- **Affected surface**: Ephemeral diagnostics / Java-to-Go application-adapter boundary.
- **Compatibility expectation**: atomic same-version protocol update; no historical fixtures.

### 13. Go DTO/service/browser validation

- **Type**: unit / integration
- **Location**: `loomspan-console/internal/observability/dto_test.go`; `service_test.go`; affected `internal/browserapi` tests.
- **What it proves**: raw and service validation accept REST only with path and (for detail) YAML; reject missing/inapplicable/empty fields and unknown sources; canonical REST fixtures decode and flow to the browser API with target scope/cursor integrity.
- **Fixtures/data**: canonical Java corpus plus malformed JSON tables.
- **Mocks**: existing HTTP target fixture servers.
- **Affected surface**: Ephemeral diagnostics / Java-to-Go boundary.
- **Compatibility expectation**: current-version coherence; no legacy decoder.

### 14. MCP schemas and rendering

- **Type**: unit / golden
- **Location**: `loomspan-console/internal/mcpadapter/skills_test.go`; `contracts_test.go`; `testdata/tools-list-response.json`, `skills-list.json`, `skill-detail.json`.
- **What it proves**: REST is included in schemas/enums and text output; list/detail show label/path/YAML while omitting bean/method/unknown targets; YAML/Java output remains correct; untrusted text remains data.
- **Fixtures/data**: REST summary/detail and updated goldens.
- **Mocks**: existing MCP service fake.
- **Affected surface**: Ephemeral diagnostics.
- **Compatibility expectation**: current-version tool coherence.

### 15. TypeScript unions and React list/detail/trace UI

- **Type**: unit / component / e2e
- **Location**: `loomspan-console/web/src/api/contracts.ts`; `SkillCatalog.test.tsx`; `SkillDetail.test.tsx`; affected API fixture tests and `web/e2e` spec.
- **What it proves**: compile-time REST discriminant requires path/YAML and excludes bean/method; catalog shows `REST` and path; detail shows unchanged manifest text; Java/YAML still render; a REST direct trace opens without error/misclassification or model-attempt assumptions; handler implementation/URL text is absent.
- **Fixtures/data**: REST list/detail fixture and representative direct trace already shaped like Java execution.
- **Mocks**: existing observability provider/router/API fixture helpers.
- **Affected surface**: Ephemeral diagnostics.
- **Compatibility expectation**: current-version UI coherence.

### 16. Exact compatibility-marker regression

- **Type**: unit / build verification
- **Location**: existing `loomspan-console/internal/applicationclient/client_test.go`; `internal/traceanalysis/processor_test.go`; Maven/Console version verification.
- **What it proves**: resolved exact match succeeds; missing, blank, non-string, and unequal markers fail; both `development` markers permit ordinary complete validation; no independent schema counter, range, fallback, or snapshot-to-snapshot promise appears; Maven-filtered and Console versions remain coordinated at beta 4.
- **Fixtures/data**: existing release/development/malformed marker tables.
- **Mocks**: current HTTP and trace fixtures.
- **Affected surface**: Persisted or serialized behavior / Ephemeral diagnostics.
- **Compatibility expectation**: protected exact-version path; ticket-authorized semantic expansion without legacy support.

### 17. Documentation evidence and routing review

- **Type**: documentation/static review backed by automated feature tests
- **Location**: root `README.md`; checkout `agent-skills/loomspan-docs/references/skill-authoring` and `java-api` indexes/topics.
- **What it proves**: exact field matrix, sole SPI, immutable/resolved input, Resource limitation, handler rule, scoped authentication, RBAC, return/failure, observation, and compatibility claims match executable tests; routing/coverage tables lead an LLM to focused REST guidance and no longer claim all YAML is model-backed or all SPI unsupported.
- **Fixtures/data**: named source/test anchors, minimal valid/invalid snippets.
- **Mocks**: none.
- **Affected surface**: Configuration or manifest behavior / Supported SPI documentation.
- **Compatibility expectation**: documentation aligned with the implemented current version.

## How to Run

From repository root:

- First red test: `.\mvnw.cmd -pl loomspan-spring-boot-starter -Dtest=YamlSkillCatalogTests -DfailIfNoTests=false test`
- Focused API/architecture: `.\mvnw.cmd -pl loomspan-spring-boot-starter -Dtest=ApplicationApiValueTest,LoomspanPublicSurfaceArchitectureTest -DfailIfNoTests=false test`
- Focused manifest/registration/runtime: `.\mvnw.cmd -pl loomspan-spring-boot-starter -Dtest=YamlSkillCatalogTests,YamlSkillDefinitionTest,YamlSkillCapabilityRegistrarTests,LoomspanAutoConfigurationTests,ExecutionCoordinatorTest,DefaultSkillTemplateTest,JavaSkillAuthenticationScopeIntegrationTests,SupportedSurfaceIntegrationTest -DfailIfNoTests=false test`
- Focused observability/fixture: `.\mvnw.cmd -pl loomspan-spring-boot-starter -Dtest=DefaultRegisteredSkillCatalogTest,ObservabilityDtoMapperTest,ConsoleRestFixtureCorpusTest -DfailIfNoTests=false test`
- Regenerate canonical REST fixtures after the producer is final: `.\mvnw.cmd -pl loomspan-spring-boot-starter -Dtest=ConsoleRestFixtureCorpusTest -Dloomspan.console.fixtures.regenerate=true -DfailIfNoTests=false test`
- Repeat that exact regeneration command, compare the fixture hashes or working-tree diff before/after the second run, then run the normal fixture test once more.
- Full Java reactor: `.\mvnw.cmd test`

From `loomspan-console`:

- Go tests: `go test ./...`
- Full Console verification, including declared builds, TypeScript/React tests, and configured e2e checks: `go run ./internal/buildtool verify`

No external credentials or services should be required: extend the existing local model stub and deterministic fixture infrastructure. The optional Windows race command is not a completion gate for this ticket because normal Console guidance makes it environment-dependent.

## Exit Criteria

- [ ] The minimal REST manifest test fails on pre-fix behavior for the expected unknown-REST/model-required reason, then passes after implementation.
- [x] All public value, allowlist, manifest matrix, handler cardinality, registration/collision/reference, runtime/facade, authentication, lifecycle/credit, and trace tests pass.
- [x] Direct and runtime-produced invocation values resist source and accessor mutation at every map/list depth, preserve permitted nulls and Resource handles, and reject null record components.
- [x] Every invalid `rest` value and every forbidden field including null/empty presence produces actionable resource/skill/field diagnostics; non-REST validation is unchanged.
- [x] Protected `SkillTemplate`, Java, model-YAML, exact-name, security, timeout/quota/fence, task/evidence, observation, and exception paths pass. REST-visible failures do not alter Java's exception-to-text adapter.
- [x] REST catalog list/detail and direct traces are coherent across Java, Go, MCP, TypeScript, React, and e2e, with distinct kind, manifest path/text, no model attempts, and no handler/target leakage.
- [x] Canonical REST fixtures regenerate deterministically; a second regeneration changes no bytes; trace/analysis corpora remain untouched unless their content truly changes.
- [x] Existing exact release and dual-`development` marker tests pass with no schema counter, range, legacy reader, migration, fallback, or historical fixture.
- [x] `LoomspanPublicSurfaceArchitectureTest`, the full Maven reactor, `go test ./...`, and `go run ./internal/buildtool verify` all pass.
- [x] Tests cited as evidence establish every changed skill-authoring/API claim; matching checkout documentation is updated in the same unit and its routing/coverage tables satisfy the LLM-first standard.
- [x] Obsolete internal helper methods and YAML-only registry Javadoc are removed/updated, with no compatibility overload or parallel old/new behavior.
- [x] No optional developer observation is required for completion; all ticket acceptance criteria have executable evidence.
