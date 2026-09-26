# PR 11 Execution Configuration Publication Testing Plan

## Change Summary

The existing skill-only generation becomes a complete immutable execution version. Public candidate validation and preparation accept authored settings alongside documents, create generation-owned provider resources, and atomically publish them. Root capture fixes the version for descendants and physical work. Startup YAML moves trace persistence to `loomspan.execution-trace.persistence`.

## Impacted Areas

- Supported API: `SkillReloader`, `PreparedSkillUpdate`, new `ExecutionConfiguration`, closed public allowlist.
- Candidate parsing and defaults: `LoomspanProperties`, trace binding, connection/model/session validation, `YamlSkillCatalog`.
- Ownership: `DefaultSkillReloader`, `SkillGenerationManager`, `SkillGeneration`, `NamedAiConnectionRegistry`, provider construction and shutdown.
- Runtime: handoff, runner/session/binding, model resolver and retry, mission engines, quotas, attachments, trace persistence.
- Documentation and samples: README and routed `loomspan-docs` authoring/API topics.

## Risk Assessment

The chief failure is a mixed generation: skills from one publication with model, quota, trace or client from another. Next are client leaks/double closes, premature retirement while physical work continues, and secret disclosure during failed preparation. Startup defaults and explicit publication must match. The old top-level trace key is intentionally removed; all other supported `SkillReloader` overloads and `RestSkillHandler` behavior remain protected. Internal Spring beans and autoconfigure signatures are not supported Java API (AGENTS.md and architecture allowlist); tests may change with coherent rewiring. Framework has no durable serialized publication format; generation ID in current-run traces remains coherent.

Authoring claims needing evidence: candidate aliases validate skills together; retry/limits/trace are captured per root; validation avoids model requests; credential references resolve at preparation; old clients retire after physical work; startup and explicit defaults match. Current `model-selection-and-connections.md` is aligned with current code and will become documentation drift after implementation unless changed. The docs coverage table must reflect newly tested publication and limit guidance.

## Existing Test Coverage

- `PublicSkillReloadIntegrationTest`: public two-stage publication, pending handoff, generation correlation, shutdown and stale candidates; extend for execution settings.
- `SkillReloaderTest`, `SkillGenerationManagerTest`, `SkillGenerationExecutionIntegrationTest`: manager ownership, retirement, validation and execution overlap; extend rather than create duplicate fixtures.
- `LoomspanPropertiesTest`, `LoomspanSessionPropertiesTest`, `NamedAiConnectionRegistryTests`, `ConnectionProtocolTest`: strict configuration, defaults, provider construction and retries.
- `SensitiveConnectionDataRedactionTest`, `LoomspanPublicSurfaceArchitectureTest`: secret-safe diagnostics and closed API.
- Trace, usage, attachment and shutdown tests identified by `rg` during implementation should be reused for their own behavior; these currently do not test concurrent published policies.

## Bug Reproduction / Failing Test First

- **Type:** public integration.
- **Location:** `src/test/java/ai/loomspan/integration/PublicSkillReloadIntegrationTest.java`.
- **Arrange/Act/Assert:** Start with no alias `candidate-model`; supply a complete skill document using it and a candidate configuration defining its connection and alias. Call the new public `validate(documents, configuration)` and `prepare(documents, configuration)`. Assert valid feedback and a prepared catalog containing the skill, then publish and admit a root by public handoff. On the current code the overload and value type do not compile; if validation is mechanically added while keeping the fixed startup catalog, validation rejects the new alias. This proves the essential missing capability before internal rewiring.

## Tests to Add/Update

### 1) Candidate schema, defaults and startup parity

- **Type/location:** unit and Spring context, `src/test/java/ai/loomspan/autoconfigure/LoomspanPropertiesTest.java`, `LoomspanSessionPropertiesTest.java`, and a new internal candidate parser test.
- **Proves:** Every supported connection/model/session/retry/attachment/trace setting is accepted and frozen. Omitted settings produce the same effective values as startup. Unknown keys, process-only `shutdown`, `skills.locations`, `observability`, direct secrets, duplicate keys, invalid alias/driver/options/retry/ranges and wrong types fail with path-specific secret-safe messages. Only the nested trace key binds; old top-level key fails strict binding or is demonstrably absent under normal Spring configuration.
- **Fixtures/mocks:** In-memory YAML strings, Spring `ApplicationContextRunner` or existing binding fixture; no provider requests.
- **Surface/expectation:** Configuration and manifest behavior; protect defaults and deliberate trace-key removal.

### 2) Complete candidate and public boundary

- **Type/location:** integration, `PublicSkillReloadIntegrationTest.java`; architecture, `LoomspanPublicSurfaceArchitectureTest.java`.
- **Proves:** New alias and dependent skill validate/prepare/publish together. Existing skill-only overloads still work and copy currently active execution settings after an explicit publication. Empty document collection is a complete replacement. Validation has no generation/client/model side effect, and preparation does not send a model request. Public signatures use allowlisted types only; no new SPI or bean-replacement extension point appears.
- **Fixtures/mocks:** Local fake provider factory/counting model or in-process mock HTTP server; no billable request.
- **Surface/expectation:** Application API protected path and closed surface.

### 3) Credential reference resolution and redaction

- **Type/location:** unit/integration, `SensitiveConnectionDataRedactionTest.java`, `NamedAiConnectionRegistryTests.java`, `PublicSkillReloadIntegrationTest.java`.
- **Proves:** Environment-backed `api-key-ref`, Gemini credential ref and header refs resolve during preparation, not validation; missing/blank refs fail before activation. A new reference and altered external value do not alter a captured old client. Authored candidate output, validation result, catalog, exception/cause messages, logs and trace metadata contain no resolved secret. Literal headers are rejected in explicit publications, while embedded startup YAML retains its existing static-header behavior. Failed partial construction closes prior clients.
- **Fixtures/mocks:** Stub `Environment` with distinct sentinel secrets and close-counting provider clients; capture logs and diagnostic serialization. No network provider request.
- **Surface/expectation:** Application API/security and current-run diagnostic coherence.

### 4) Atomic publication and candidate lifecycle

- **Type/location:** unit, `SkillReloaderTest.java` and `SkillGenerationManagerTest.java`.
- **Proves:** Invalid config, failed skill check, missing reference, construction failure, shutdown during prepare, foreign/closed/stale candidate, and raced publish leave active generation/settings intact. `close()` is idempotent and releases unpublished resources; publish transfers ownership; repeated publish rejection cannot close the active generation. Superseded unused generations close once, even if a host retirement listener throws or client close fails. No provider construction/close occurs under generation publication monitor.
- **Fixtures/mocks:** Close-counting clients and latches; deterministic failure injection; no timed sleeps.
- **Surface/expectation:** Protected API lifecycle and internal ownership.

### 5) Overlapping roots, descendants, retries and limits

- **Type/location:** integration, `SkillGenerationExecutionIntegrationTest.java`, `PublicSkillReloadIntegrationTest.java`, and focused `ModelAttemptCallAdvisorIntegrationTest.java` where retry counting belongs.
- **Proves:** Capture A, block before physical start, publish B, then run A: A keeps its skill/prompt/model connection/provider options/retry policy/depth/timeout/all quotas/attachment max size. B handoff uses B. A child/parallel branch and provider retry after B publication still use A. A client stays open until admitted/pending roots and registered physical descendant work finish, even after caller cancellation or timeout; then closes once. B remains usable. Avoid relying on incidental thread timing by gates/latches and recorded client identities.
- **Fixtures/mocks:** Two named fake or local protocol connections with distinguishable responses/attempt counts; latches, deterministic clock or bounded deadlines, close counters. Exercise each quota family at a boundary instead of merely asserting config getters.
- **Surface/expectation:** Application API semantics and internal lifecycle; protected `RestSkillHandler` still gets captured ID.

### 6) Trace selection and generation correlation

- **Type/location:** integration, trace contract tests plus `PublicSkillReloadIntegrationTest.java`.
- **Proves:** Concurrent A/B roots record their respective generation IDs and trace persistence (`NEVER`, `ONERROR`, `ALWAYS` as applicable), including one failure and one success. No process-wide retention/auth setting changes. Current writer, trace reader and activity projector agree on captured ID; no resolved credential appears. Republishing A's authored content after B yields new ID C and old A execution remains associated with A.
- **Fixtures/mocks:** In-memory trace store/observer and controlled root gates; no external model request.
- **Surface/expectation:** Ephemeral diagnostics current-run coherence.

### 7) Startup, shutdown and documentation evidence

- **Type/location:** Spring context/integration tests, existing shutdown suite and new startup config test; repository docs search.
- **Proves:** Embedded `application.yml` starts from canonical key and produces the same effective defaults as a complete explicit candidate. Framework shutdown retains one configured budget, rejects new admission, lets captured work finish within the existing contract, and releases each owned client. Search all tests/examples/docs for obsolete top-level trace samples; manual doc comparison ensures model/validation/trace guidance matches focused tests.
- **Fixtures/mocks:** Context runner and close-counted clients; existing shutdown clock/test harness.
- **Surface/expectation:** Configuration protected path and internal resource lifecycle.

## How to Run

- Red test: `mvn -Dtest=PublicSkillReloadIntegrationTest test` after adding the first test, before implementation; record the expected compile or validation failure.
- Focused config/API: `mvn -Dtest=LoomspanPublicSurfaceArchitectureTest,LoomspanPropertiesTest,LoomspanSessionPropertiesTest,PublicSkillReloadIntegrationTest test`.
- Focused lifecycle: `mvn -Dtest=SkillReloaderTest,SkillGenerationManagerTest,SkillGenerationExecutionIntegrationTest,NamedAiConnectionRegistryTests,SensitiveConnectionDataRedactionTest test`.
- Full regression: `mvn test`.
- Install artifact for companion Sidecar integration: `mvn install -DskipTests` after tests pass. Sidecar PR 7 separately must compile and test against this installed artifact using supported API only; framework completion should record this handoff obligation, not claim Sidecar integration has passed before it runs.
- Search: `rg -n '^execution-trace:|execution-trace\.persistence' README.md src agent-skills examples` and inspect each result for canonical nesting (the full property path naturally contains the second string).

Maven requires Java 21 and Maven 3.9+ per `pom.xml`; no provider account or billable model endpoint is required. Use local protocol fakes for provider behavior.

## Exit Criteria

- [ ] First public integration test fails for the pre-fix missing capability, then passes.
- [ ] Each acceptance criterion has executable evidence: complete candidate, handoff overlap, invalid/failure isolation, abandoned/retired resources, defaults/process boundary, secret redaction, republish/correlation.
- [ ] Existing skill-only API and `RestSkillHandler` tests pass; no accidental public or autoconfigure/internal signature exposure.
- [ ] The old top-level trace key and examples are removed, without an alias; the nested key and unchanged `ONERROR` default are tested.
- [ ] New documentation claims have focused source/test support and the authoring coverage table is accurate.
- [ ] Focused suites, architecture test, full `mvn test`, and local `mvn install -DskipTests` pass; any unavailable required check is reported as such, not assumed successful.
- [ ] Companion Sidecar integration against the installed artifact is tracked as the dependent PR 7 verification. Do not claim combined feature complete until that separate verification runs.

## Optional Developer Checks

In an environment with a provisioned external credential, publish a new provider connection and observe a real request using it. This checks third-party SDK/credential provisioning, supplements the automated local protocol tests, and is not a framework pipeline completion gate.
