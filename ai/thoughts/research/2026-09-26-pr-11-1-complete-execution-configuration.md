---
date: 2026-09-26T17:30:49-07:00
researcher: Codex
git_commit: 56c750bcf8a0461dac0f0be330914b188bd60d6a
branch: main
repository: loomspan-framework
topic: "PR 11.1 complete execution configuration: current publication and generation behavior"
tags: [research, codebase, execution-configuration, generations, credentials]
status: complete
last_updated: 2026-09-26
last_updated_by: Codex
---

# Research: PR 11.1 complete execution configuration

**Date**: 2026-09-26T17:30:49-07:00  
**Researcher**: Codex  
**Git Commit**: `56c750bcf8a0461dac0f0be330914b188bd60d6a`  
**Branch**: `main`  
**Repository**: `loomspan-framework`

## Research Question

Document the current framework path for publishing skills with a complete execution configuration, including credential handling, effective settings, generation capture and resource lifetime, supported surfaces, process boundaries, tests, and the companion Sidecar consumer. This is the present state before implementing `ai/thoughts/tickets/loomspan-pr-11.1-complete-execution-configuration.md`.

## Summary

The supported `SkillReloader` overloads accept a complete set of skill documents and one `ExecutionConfiguration` authored as YAML. Today the explicit YAML accepts named connections, models, session settings, and trace persistence. Credentials in it are external Spring `Environment` property references, resolved during preparation; the record has no supported host-supplied credential payload (`src/main/java/ai/loomspan/api/ExecutionConfiguration.java:6`; `src/main/java/ai/loomspan/internal/skill/ExecutionConfigurationParser.java:24-34,56-67,124-132`). File-only startup binds ordinary `loomspan.*` properties, including direct values supplied by Spring property resolution (`src/main/java/ai/loomspan/autoconfigure/LoomspanProperties.java:28-51,439-465`).

Preparation constructs a generation-owned runtime and named provider clients. Publication switches the active generation; root handoff captures it, and ownership keeps retired resources until work releases them. Session limits, attachments, mission timeout, quotas, provider retry and trace persistence have generation-aware reads in the production path (`src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java:124-135,286-322,326-351`; `src/main/java/ai/loomspan/internal/core/LoomspanSessionRunner.java:298-327`). The current explicit configuration parser rejects all top-level `loomspan` fields outside its allowlist, including process settings (`ExecutionConfigurationParser.java:24-30,40-43,99-106,163-166`).

## Detailed Findings

### Supported declarations and technical exposure

- **Application API:** `ExecutionConfiguration` is a public record of `String yaml`; its constructor checks null and a one-million-character maximum. Record-generated `yaml()`, `toString()`, `equals()` and `hashCode()` operate on that string (`src/main/java/ai/loomspan/api/ExecutionConfiguration.java:1-12`). `SkillReloader` has `validate` and `prepare` overloads with `Collection<SkillDocument>, ExecutionConfiguration`, plus `publish`, `snapshot`, and a retirement listener (`src/main/java/ai/loomspan/api/SkillReloader.java:7-42`). `PreparedSkillUpdate` exposes generation ID, immutable candidate catalog and `close()` (`src/main/java/ai/loomspan/api/PreparedSkillUpdate.java:4-12`). These top-level types appear in the closed API allowlist (`src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java:29-53`); the README describes the supported surface (`README.md:191,198-229`).
- **Supported SPI:** `RestSkillHandler` is the sole supported Java SPI according to repository policy and the README (`README.md:191,231-254`). The generation carries its fixed handler with REST capabilities (`src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java:295-317`). No credential resolver SPI is in the allowlist.
- **Internal or accidentally exposed implementation:** `ExecutionConfigurationParser`, `ExecutionRuntime`, `SkillGenerationManager`, `NamedAiConnectionRegistry`, `SpringAiProviderIntegration`, and `DefaultSkillReloader` are public Java declarations under `ai.loomspan.internal`; the architecture test classifies internal technical visibility separately (`LoomspanPublicSurfaceArchitectureTest.java:272-276,311-353`). `LoomspanProperties` is public Spring binding machinery under `ai.loomspan.autoconfigure`, not an application extension API (`LoomspanProperties.java:28`; `README.md:191`). Internal constructors and bean wiring are technical exposure, not documented consumer contracts.
- **In-repository consumer:** Sidecar `RuntimeConfigurationService` builds `new ExecutionConfiguration(configuration.executionConfigurationYaml())` and uses the supported reloader to stage/publish a generation (`../loomspan-sidecar/src/main/java/ai/loomspan/sidecar/configuration/RuntimeConfigurationService.java:90-110,195-227,338-339`). Its execution coordinator resolves a captured `admitted.generationId()` to a durable snapshot ID before running (`../loomspan-sidecar/src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java:183-194`). Both POMs currently target `1.0.0-beta.6-SNAPSHOT` (`pom.xml:9`; `../loomspan-sidecar/pom.xml:17,42-43`). The Sidecar PR 7.1 ticket describes the planned encrypted credential storage and integration; it is historical/planned context, not present framework behavior.

### Configuration, credentials, and validation

- **Configuration and manifest contracts:** The strict parser accepts a single root `loomspan` mapping. Publishable fields are `connections`, `models`, `session`, and `execution-trace.persistence`; connection fields include driver, base URL, OpenAI/Gemini options, provider retry, `api-key-ref`, `header-refs`, and `gemini.credentials-ref`. Unknown fields are rejected (`ExecutionConfigurationParser.java:24-30,39-101,163-166`). Model aliases have connection, provider model and thinking levels. Session fields include maximum depth, mission timeout, quotas, and attachment max size (`ExecutionConfigurationParser.java:27-30,75-101`).
- `parse(..., resolveReferences=false)` substitutes a sentinel for references and validates authored syntax and model/skill relationships without reading secrets; `parse(..., true)` resolves each external property and rejects null or blank values with a field-path error. The authored record retains only reference names (`ExecutionConfigurationParser.java:56-67,102-132`; `SkillGenerationManager.java:124-135,138-150`). The focused parser test asserts the reference-resolution phase, defaults, non-disclosure in authored YAML, process-field rejection, direct-secret rejection and duplicate-key rejection (`src/test/java/ai/loomspan/internal/skill/ExecutionConfigurationParserTest.java:14-82`).
- `LoomspanProperties` holds startup `connections`, `models`, `session`, `executionTrace`, `shutdown`, `skills` and `observability` (`LoomspanProperties.java:33-111`). Named connection validation covers driver-specific credential modes and headers (`LoomspanProperties.java:175-248`). `ConnectionProperties.toString()` states whether credentials and headers are configured without printing their values (`LoomspanProperties.java:439-476`). The parser builds a fresh `LoomspanProperties` from only flattened candidate fields and its class defaults (`ExecutionConfigurationParser.java:102-106`); the explicit path does not merge candidate provider fields with deployment properties. Skill-only preparation reads the current active runtime properties and policy (`SkillGenerationManager.java:110-121,153-172`).
- Current default values live in the binding classes: max depth 32, mission timeout 60 seconds, attachment size 20 MB, six quota defaults, provider retry defaults, and trace persistence `ONERROR` (`LoomspanProperties.java:317-386,501-523`; `src/main/java/ai/loomspan/autoconfigure/ExecutionTraceProperties.java:11-20`). The framework-specific configuration metadata enumerates connection, retry, model, session, shutdown and observability properties (`src/main/resources/META-INF/additional-spring-configuration-metadata.json`).
- **Process boundary:** shutdown timeout, skill file locations and observability service enablement/authentication/retention are startup properties. Their uses include lifecycle construction, resource discovery, and web route registration (`LoomspanAutoConfiguration.java:179-210,417-428`; `LoomspanProperties.java:297-315,389-435`; `src/main/java/ai/loomspan/internal/observability/web/ObservabilityRouteRegistrar.java:69-154`). Per-execution trace persistence is inside explicit publication (`ExecutionConfigurationParser.java:99-106`).

### Preparation, provider resources, and execution capture

- `DefaultSkillReloader` implements the public validation, preparation, publication, snapshot and candidate-close operations (`src/main/java/ai/loomspan/internal/skill/DefaultSkillReloader.java:20-183`). `SkillGenerationManager.prepare(documents, configuration)` checks the candidate with unresolved reference placeholders, resolves the references, and then prepares the catalog (`SkillGenerationManager.java:124-135`). `prepareCatalog` builds capabilities and a runtime; candidate construction closes a newly constructed runtime if construction throws (`SkillGenerationManager.java:286-322`).
- The Spring bean factory supplies a runtime factory from effective properties and trace policy. It constructs a `NamedAiConnectionRegistry`, which creates a provider runtime per connection and destroys already built clients on failure (`src/main/java/ai/loomspan/autoconfigure/LoomspanAutoConfiguration.java:172-188`; `src/main/java/ai/loomspan/internal/autoconfigure/NamedAiConnectionRegistry.java:13-36,74-104`). `SpringAiProviderIntegration` reads effective API keys, headers, provider options and Gemini credentials resource when creating clients (`src/main/java/ai/loomspan/internal/springai/SpringAiProviderIntegration.java:128-168,480-489`). `ExecutionRuntime` copies session, connection and model properties, keeps the registry and trace policy, and closes the registry once (`src/main/java/ai/loomspan/internal/skill/ExecutionRuntime.java:13-31,33-107`).
- Publication swaps the active generation and marks the prior published generation superseded. `capture()` increments its owner count, and retirement waits for zero owners before closing runtime resources and calling listeners (`SkillGenerationManager.java:326-351,381-405,407-438`). `DefaultSkillInvocationHandoff` captures before object conversion and input validation and exposes the selected process-local generation ID on the admitted handle (`src/main/java/ai/loomspan/internal/skillapi/DefaultSkillInvocationHandoff.java:30-88`). Root session creation reads depth, quotas and trace policy from its captured generation (`LoomspanSessionRunner.java:298-327`). Mission work carries `ExecutionBinding` to its execution thread and reads the generation timeout (`src/main/java/ai/loomspan/internal/runtime/MissionWorkExecutor.java:28-48,70-79`). Attachment limits, usage quotas and step-loop timeout also consult captured binding where present (`src/main/java/ai/loomspan/internal/runtime/attachment/DefaultMissionInputMaterializer.java:36-40`; `src/main/java/ai/loomspan/internal/runtime/usage/DefaultSessionUsageService.java:32-37`; `src/main/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngine.java:95-101`). Connection retry policy is carried by `ProviderConnectionRuntime` and used for each physical attempt (`src/main/java/ai/loomspan/internal/provider/ProviderConnectionRuntime.java:8-17`; `src/main/java/ai/loomspan/internal/chat/ProviderAttemptCallAdvisor.java:59-80`).
- The generation ID and configured limits are emitted in current-run trace metadata (`src/main/java/ai/loomspan/internal/runtime/trace/DefaultExecutionTraceHandle.java:406-409`). That is an **ephemeral diagnostic format** under the design lens; no code reviewed here indicates a new durable interchange contract. Sidecar's durable configuration snapshot mapping is a separate **persisted or serialized contract** owned by Sidecar (`../loomspan-sidecar/src/main/java/ai/loomspan/sidecar/storage/ConfigurationSnapshotStore.java:10-14`; `../loomspan-sidecar/src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java:183-194`). The Go observability Console consumes generation and trace data, but the present ticket does not describe an application-adapter REST/SSE, acquisition, problem or consumed NDJSON change; no changed protocol fixture is established at this research stage.

### Focused verification and documentation

- `ExecutionConfigurationParserTest` protects strict candidate parsing and external reference resolution (`src/test/java/ai/loomspan/internal/skill/ExecutionConfigurationParserTest.java:38-82`). `SkillReloaderTest` covers preparation/publish ownership, stale candidates, distinct IDs, shutdown and overlapping operations (`src/test/java/ai/loomspan/internal/skill/SkillReloaderTest.java:60-160,260-391`). `SkillGenerationManagerTest` covers provider resource retirement, complete candidate validation, and multiple coexisting generations (`src/test/java/ai/loomspan/internal/skill/SkillGenerationManagerTest.java:41-68,69-118,322-446`). `SkillGenerationExecutionIntegrationTest` covers handoff capture before conversion/validation and retained generation selection after activation (`src/test/java/ai/loomspan/internal/skillapi/SkillGenerationExecutionIntegrationTest.java:53-141`). `LoomspanPublicSurfaceArchitectureTest` enforces the closed public API and signature boundary (`src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java:297-353`). Sidecar has current runtime configuration integration tests using `api-key-ref` and snapshot correlation (`../loomspan-sidecar/src/test/java/ai/loomspan/sidecar/configuration/RuntimeConfigurationIntegrationTest.java:44-116`; `../loomspan-sidecar/src/test/java/ai/loomspan/sidecar/execution/AuthenticatedExecutionApiIntegrationTest.java:345-409`).
- The README documents the reference-only explicit candidate, dynamic generation lifecycle, process boundary and rotation limitations (`README.md:140-262`). The bundled `loomspan-docs` skill declares version `1.0.0-beta.6-SNAPSHOT`, matching this checkout's POM (`agent-skills/loomspan-docs/SKILL.md`; `pom.xml:9`). Its model-selection and Java reload topics describe the same reference-only candidate and captured-generation behavior (`agent-skills/loomspan-docs/references/skill-authoring/model-selection-and-connections.md:12-54`; `agent-skills/loomspan-docs/references/java-api/skill-reload.md:10-25`). **Drift classification: aligned** for these compared current behaviors. Skill-authoring impact exists because model connection selection, credentials, limits and traces affect authoring guidance; the topic index explicitly routes model selection, limits and validation (`agent-skills/loomspan-docs/references/skill-authoring/README.md`).

## Code References

- `src/main/java/ai/loomspan/api/ExecutionConfiguration.java:6-12` — authored candidate record.
- `src/main/java/ai/loomspan/api/SkillReloader.java:7-42` — application publication API.
- `src/main/java/ai/loomspan/internal/skill/ExecutionConfigurationParser.java:24-132` — strict authored shape and reference resolution.
- `src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java:110-172,286-405` — preparation and generation ownership.
- `src/main/java/ai/loomspan/internal/skill/ExecutionRuntime.java:20-107` — effective settings copy and provider lifetime.
- `src/main/java/ai/loomspan/autoconfigure/LoomspanAutoConfiguration.java:172-210` — startup wiring and runtime factory.
- `src/main/java/ai/loomspan/internal/core/LoomspanSessionRunner.java:298-327` — captured root settings.
- `src/main/java/ai/loomspan/internal/skillapi/DefaultSkillInvocationHandoff.java:30-88` — atomic root capture and correlation.

## Architecture Documentation

The current path has one configuration publication flow: authored candidate → strict parser and skill validation → reference resolution → generation-owned provider runtime → one-shot publication → handoff capture → release and retirement. File startup populates the same `LoomspanProperties` model, while explicit publication creates that model from candidate YAML and defaults. Root settings and clients are selected from the captured generation. Process facilities remain Spring beans configured at startup. The architecture test's allowlist, not Java visibility, defines supported Application API and Supported SPI.

## Historical Context (from ai/thoughts/)

- `ai/thoughts/tickets/loomspan-pr-11-publish-execution-configuration.md` describes the implemented predecessor's reference-only credential scope and publication lifecycle.
- `ai/thoughts/tickets/loomspan-pr-11.1-complete-execution-configuration.md` requests host-supplied credentials and exhaustive execution configuration publication.
- `ai/thoughts/framework-feature-design-lens.md` supplies the six contract categories used above and the current pre-1.0 compatibility posture.
- `../loomspan-sidecar/ai/thoughts/tickets/loomspan-pr-7.1-complete-execution-configuration.md` describes the companion planned encrypted credential source and framework snapshot sequencing.

## Related Research

No prior research document matching this execution-configuration topic was found under `ai/thoughts/research/` in this checkout.

## Open Questions

- Planning needs to decide the supported shape for dynamically supplied credential material, including Gemini Vertex credentials and sensitive headers, within the existing complete candidate API.
- Planning needs to inventory every production read of startup execution properties and classify whether the existing generation-aware binding covers it or an additional capture path is needed.
- Planning needs to specify the exact Sidecar snapshot integration and evidence sequence required by the companion ticket and the framework release flow.
