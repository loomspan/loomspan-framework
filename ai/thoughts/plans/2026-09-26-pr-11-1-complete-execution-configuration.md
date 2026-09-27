# PR 11.1 Complete Execution Configuration Implementation Plan

## Overview

Extend the existing complete skill and execution publication path so a host can pass transient provider credentials while preparing a generation. Keep authored YAML free of secret values, preserve ordinary file startup, and make the captured generation the only source of execution settings and provider resources.

## Current State Analysis

`ExecutionConfiguration` currently contains only YAML (`src/main/java/ai/loomspan/api/ExecutionConfiguration.java:6-12`). Its strict parser recognizes credential references but resolves them only from Spring `Environment` (`src/main/java/ai/loomspan/internal/skill/ExecutionConfigurationParser.java:24-30,124-132`). `SkillGenerationManager` validates syntax and skills before resolving references and building a runtime (`src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java:124-135,286-322`). Root handoff already captures a generation before conversion or validation; physical owners delay resource retirement (`src/main/java/ai/loomspan/internal/skillapi/DefaultSkillInvocationHandoff.java:30-88`; `SkillGenerationManager.java:326-438`).

Startup properties contain execution settings plus process settings. The explicit parser allows only connections, models, session and trace persistence, creates fresh binding defaults, and rejects process fields. `ExecutionRuntime.copy` freezes session, connection and model fields, but it is an internal mutable property representation and must be audited alongside every execution read (`src/main/java/ai/loomspan/internal/skill/ExecutionRuntime.java:33-107`).

## Desired End State

A host supplies one complete authored configuration and a per-preparation set of reference values through the supported `SkillReloader` API. The values are copied at method entry, resolved only for that preparation, never merged with deployment provider properties, and never appear in public catalogs, authored YAML, trace metadata or exception text. File startup and the existing reference-only overload retain their behavior. Candidate preparation freezes effective settings and provider resources; publication and root handoff continue to select generations atomically. No provider request occurs during validation or preparation.

### Key Discoveries

- The existing `api-key-ref`, `header-refs`, and Gemini `credentials-ref` syntax already separates authored intent from secret values (`ExecutionConfigurationParser.java:26,56-67`). Reuse those names rather than allowing literal secrets in YAML.
- The supported API is a closed allowlist, and `RestSkillHandler` is the only supported SPI (`src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java:29-53`; `README.md:191-254`). A preparation overload needs no new SPI or top-level API type.
- `SpringAiProviderIntegration` constructs clients from effective connection settings during preparation and currently loads Gemini Vertex credentials through a URI (`src/main/java/ai/loomspan/internal/springai/SpringAiProviderIntegration.java:116-178,480-489`). In-memory Vertex JSON requires an explicit internal preparation path.
- Current Spring bean factories pass startup session values as fallbacks, while the relevant execution paths consult `ExecutionBinding` when present (`src/main/java/ai/loomspan/autoconfigure/LoomspanAutoConfiguration.java:156-161,296,361,428,456`; `src/main/java/ai/loomspan/internal/runtime/MissionWorkExecutor.java:28-48`). Audit all such fallbacks and prohibit their use for bound execution work.

## What We're NOT Doing

- Sidecar UI, encrypted database, rollback and export implementation; those belong to its companion PR 7.1.
- A framework vault, encrypted storage, file watcher, live environment refresh, provider-side key-validity guarantee, new bean replacement contract, or another configuration system.
- Dynamic Spring/server infrastructure, observability service authentication/retention, skill location discovery, listeners or shutdown settings.

## Skill-Authoring Documentation Impact

**Impact**: Affected.

- **Rationale**: Authors and application integrators need accurate connection credential-source, generation-capture, rotation, validation and execution-limit guidance.
- **Documents to update**: `agent-skills/loomspan-docs/references/skill-authoring/model-selection-and-connections.md`, `agent-skills/loomspan-docs/references/java-api/skill-reload.md`, the focused limit and trace topics if the execution-read audit changes their stated behavior, and `agent-skills/loomspan-docs/references/skill-authoring/README.md` coverage row if its coverage or confidence changes.
- **Supporting evidence**: parser and provider tests, generation and handoff integration tests, `ExecutionConfigurationParser`, `SkillGenerationManager`, and `SpringAiProviderIntegration`.
- **Coverage table update**: Required if expanded credential guidance changes the model-selection topic's stated coverage; record the newly covered host-supplied case.
- **LLM-first usability**: Keep model selection as the routed entry, put exact `*-ref` and credential-source rules there, link to the Java publication workflow, and distinguish enforced validation from key-rotation recommendations and external revocation limits.
- **Current drift**: Aligned for the current reference-only behavior, according to matching source and focused tests. The new guidance must be written with the new executable evidence.

## Contract and Compatibility Impact

| Surface | Impact and supporting evidence | Planned compatibility treatment |
| --- | --- | --- |
| Application API | Add a `SkillReloader.prepare(Collection<SkillDocument>, ExecutionConfiguration, Map<String,String>)` overload; the existing `ExecutionConfiguration(String)` and reloader overloads are allowlisted (`LoomspanPublicSurfaceArchitectureTest.java:29-53`). | Preserve existing calls and add one deliberate supported operation; document it and test signatures. |
| Supported SPI | `RestSkillHandler` remains the sole SPI (`README.md:231-254`). | No new SPI or bean override contract. |
| Configuration and manifest contracts | Existing strict reference YAML and file properties are documented (`README.md:140-262`; `ExecutionConfigurationParser.java:24-106`). Add a reference form for in-memory Gemini Vertex JSON if needed. | Preserve file and existing reference behavior; reject literal secrets and process fields; document explicit map mode as complete with no environment fallback. |
| Persisted or serialized contracts | Framework does not own a durable configuration store; Sidecar owns snapshot correlation (`../loomspan-sidecar/src/main/java/ai/loomspan/sidecar/storage/ConfigurationSnapshotStore.java:10-14`). | No framework persistence format change. Sidecar will own encrypted retained credentials and its migration/reset decision. |
| Ephemeral diagnostic formats | Generation ID and limits appear in current-run trace metadata (`src/main/java/ai/loomspan/internal/runtime/trace/DefaultExecutionTraceHandle.java:406-409`). | Keep current writer/reader coherence and useful correlation; assert no secrets in traces or failures. |
| Internal or accidentally exposed implementation | Parser, properties, provider integration, runtime, manager and bean fallbacks change (`ExecutionRuntime.java:33-107`; `LoomspanAutoConfiguration.java:172-210`). | Update atomically without internal compatibility shims. |

- **Evidence of supported contracts**: API architecture allowlist, README, ticket outcome and Sidecar's supported `SkillReloader` use (`../loomspan-sidecar/src/main/java/ai/loomspan/sidecar/configuration/RuntimeConfigurationService.java:195-227`).
- **Intentional compatibility changes**: The new map overload is additive; strict rejection of direct-secret YAML remains. No existing supported behavior is intentionally removed.
- **In-repository consumers to update**: Framework tests, README, authoring skill; Sidecar integration tests and implementation in its companion work.
- **Public-surface delta**: One `SkillReloader` overload using JDK `Map`; no new top-level type, constructor, or Spring extension point. The architecture allowlist must intentionally include the method.
- **Shim decision**: **No shim.** Existing overloads are existing supported operations, not compatibility machinery; internal code can change atomically.
- **Java-to-Go boundary coordination**: **Not required.** The intended change does not alter application-adapter REST/SSE, acquisition, problem, or consumed NDJSON fields. Reassess if the execution-read audit finds a protocol change.
- **Pipeline notes alignment**: **No notes.** The ticket's development constraint authorizes removal of obsolete internal paths; this plan proposes no broader supported break.

## Implementation Approach

Choose a transient reference-value map on `prepare`, not an `ExecutionConfiguration` record component or credential resolver SPI. A record component would generate `toString`, equality and an accessor over secret values, violating ordinary readback requirements. The overload accepts the same YAML reference names and requires **every** referenced value to be supplied by the map, with no Spring `Environment` fallback; the existing overload remains the environment-reference path. Copy and validate the map immediately, never log or interpolate values into errors, and treat an unused/missing/null/blank entry as a candidate error. Keep `validate(documents, configuration)` advisory and secret-free; preparation revalidates and checks credential availability. Sidecar freezes its draft and decrypts the exact values at preparation, then passes the map once.

For Vertex service-account content, add `gemini.credentials-json-ref` to explicit candidate syntax (mutually exclusive with `credentials-ref`) and an internal effective credential representation that the provider factory can consume from memory during preparation. Keep ordinary `credentials-uri` file configuration and the existing reference-to-URI path. Validate JSON locally through the SDK during preparation without a provider call; avoid printing parser/SDK input in errors. If no Vertex credential is specified, preserve current application-default-credential behavior but resolve it as part of provider-client preparation.

### Design tradeoffs

The framework needs this because it owns provider-client construction and captured generation lifetime; Sidecar cannot safely replace beans or mutate environment state. A separate resolver SPI would expand the supported extension surface and risk late mutable reads. Passing literal YAML secrets would make normal authored readback/export unsafe. The map overload adds one public method and no retained secret-bearing API object. Internal effective properties still temporarily contain values while clients are built, so they must remain generation-owned, copied, redacted, and absent from catalogs and traces. External provider revocation remains outside the framework's control.

## Phase 1: Public preparation and strict credential resolution

### Overview

Add the supported host-supplied credential path while retaining the existing file and environment paths.

### Changes Required

#### 1. Reloader API and coordinator

**Files**: `src/main/java/ai/loomspan/api/SkillReloader.java`, `src/main/java/ai/loomspan/internal/skill/DefaultSkillReloader.java`, `src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java`.

**Changes**: Add and implement the map overload. Snapshot the input map before validation or other work; use the same base-ID, one-shot, close-on-failure, shutdown and retirement machinery as existing preparation. Ensure failure wrappers never include secret values.

```java
PreparedSkillUpdate prepare(Collection<SkillDocument> documents,
        ExecutionConfiguration configuration, Map<String, String> credentialValues);
```

#### 2. Parser and provider credential form

**Files**: `src/main/java/ai/loomspan/internal/skill/ExecutionConfigurationParser.java`, `src/main/java/ai/loomspan/autoconfigure/LoomspanProperties.java`, `src/main/java/ai/loomspan/internal/springai/SpringAiProviderIntegration.java`.

**Changes**: Resolve authored references through one explicitly selected source. In map mode, require map coverage and forbid environment fallback. Retain the old environment mode. Add local validation and in-memory construction for `gemini.credentials-json-ref`; preserve URI references and file startup. Reject mutually exclusive Vertex sources, direct-secret YAML, unknown and process keys, and invalid secret values without disclosing them.

### Success Criteria

#### Automated Verification

- [x] Parser tests demonstrate map-only resolution, missing/unused/blank rejection, no fallback, exact defaults, process-field rejection and sanitized errors.
- [x] API architecture test accepts only the intended overload and no new SPI.
- [x] Focused provider test prepares in-memory Gemini credentials without a provider request.

---

## Phase 2: Complete generation capture and lifecycle

### Overview

Close remaining execution-time startup-property gaps and prove generation ownership for credentials and settings.

### Changes Required

#### 1. Effective runtime and all execution reads

**Files**: `src/main/java/ai/loomspan/internal/skill/ExecutionRuntime.java`, `src/main/java/ai/loomspan/autoconfigure/LoomspanAutoConfiguration.java`, and every execution consumer found by the `LoomspanProperties`/`ExecutionBinding` read audit.

**Changes**: Inventory every production read of connection, model, retry, session, attachment, quota, timeout, trace and execution-scoped options. Route bound execution through its captured generation; permit startup fallbacks only for process setup and unbound startup diagnostics. Freeze all defaults and resources before candidate return. Keep skill-only preparation's copy-from-active behavior. Remove dead mutable-global execution paths instead of duplicating modes.

#### 2. Lifetime and failure behavior

**Files**: `SkillGenerationManager.java`, `ExecutionRuntime.java`, `NamedAiConnectionRegistry.java`, and focused lifecycle tests.

**Changes**: Ensure partial client construction closes completed resources; discarded candidates close; published generations retain clients until physical owners release; the existing framework shutdown budget remains the only shutdown clock. No validation or preparation path sends a model request.

### Success Criteria

#### Automated Verification

- [x] Overlap, delayed start, nested, parallel, retry and later provider-call tests select the admitted generation's full settings and credentials through the one captured generation object. The evidence is compositional: `PublicSkillReloadIntegrationTest.publicHostMapPublishesReplacementKeysWithoutDeploymentProperties` asserts distinct A/B authorization headers on delayed and new roots; `pendingRootKeepsProviderRetryPolicyAfterPublication` asserts old retry policy after B; `SkillGenerationManagerTest.hostCredentialsAndExecutionSettingsRemainWithCapturedGeneration` asserts A/B credential, depth, and quota values and resource retirement; `LoomspanSessionPropertiesTest.capturedGenerationKeepsTracePolicyAndDepthAcrossPublication` asserts bound trace/depth/quota selection; `SkillGenerationExecutionIntegrationTest` protects handoff identity; `MissionExecutionEngineTest` and `StepLoopMissionExecutionEngineTest` assert exact binding transfer to workers, while `FrameworkExecutionLifecycleTest.nestedPhysicalWorkRetainsRootOwnershipAfterLogicalCompletionAndCutoff` protects physical descendant lifetime. A combined test through every path would repeat these same capture/transfer boundaries without adding a new selector.
- [x] Invalid or abandoned candidates leave active state and resources intact; old clients close only after physical completion.
- [x] File-only and equivalent explicit configurations give equivalent effective execution defaults and settings.

---

## Phase 3: Supported documentation and cross-repository evidence

### Overview

Document the contract, install the updated framework snapshot, and gather Sidecar integration evidence before final framework checks.

### Changes Required

#### 1. Framework documentation

**Files**: `README.md`, `agent-skills/loomspan-docs/references/skill-authoring/model-selection-and-connections.md`, `agent-skills/loomspan-docs/references/java-api/skill-reload.md`, and the knowledge-base coverage table.

**Changes**: Describe the map overload, reference names, no-fallback behavior, secret readback limits, exact execution/process boundary, file startup, environment restart behavior, rotation order, external revocation limit and Sidecar-facing use. Keep focused source/test anchors.

#### 2. Snapshot and Sidecar sequence

**Files**: Framework and companion Sidecar build/test artifacts; Sidecar implementation belongs to PR 7.1.

**Changes**: Run the framework focused and full tests, then `./mvnw.cmd -B -ntp install` in this repository. Confirm Sidecar resolves the locally installed matching `1.0.0-beta.6-SNAPSHOT`; run its supported-API integration against a complete candidate with encrypted-source supplied values and generation correlation. Obtain Sidecar's own test evidence before final framework release checks. Follow the companion ticket's later Maven Central publication and release-pin sequence; do not overwrite a release or claim final Sidecar release checks before publication.

### Success Criteria

#### Automated Verification

- [x] `./mvnw.cmd -B -ntp -Dtest=LoomspanPublicSurfaceArchitectureTest test` passes (as part of focused selection).
- [x] Focused parser, provider, generation, handoff and integration tests pass.
- [x] `./mvnw.cmd -B -ntp test` and `./mvnw.cmd -B -ntp install` pass in framework.
- [ ] Sidecar snapshot integration passes against the installed framework artifact and uses only supported API.
- [x] Authoring guidance matches focused executable evidence and the coverage table is current.

## Testing Strategy

Start with a red supported-API test for map-only provider credential preparation, then parser/provider unit tests. Use existing generation, root handoff and execution integration fixtures for overlap and resource lifetime. The companion Sidecar test is a cross-repository acceptance dependency, with its own evidence. The dedicated testing plan specifies cases and commands.

## Performance Considerations

Map copying and reference lookup are bounded preparation work. Keep provider construction and credential parsing outside publication locks and model-call paths. Do not introduce per-request secret resolution or client rebuilding.

## Migration Notes

Existing `new ExecutionConfiguration(yaml)` and reference-only preparation remain valid. Hosts choosing map mode must supply every referenced credential in that preparation; no deployment provider fallback occurs. Sidecar's encrypted store and any development-data reset are owned by its companion PR; no framework data is deleted. Environment-variable changes still require restart.

## Step 4 implementation notes

- Framework execution-read audit: root admission and `LoomspanSessionRunner` use the captured `SkillGeneration`; `MissionWorkExecutor`, `StepLoopMissionExecutionEngine`, attachment materialization, usage service, trace recorder, chat client assembly, and provider-attempt advisor read `ExecutionBinding` for bound work. Startup properties remain fallbacks for unbound startup/process paths. No Java-to-Go boundary change was found. The final code change copies the in-memory Gemini JSON value into the generation-owned `ExecutionRuntime` and builds the provider client at preparation.
- Developer sequencing decision: complete and review the framework component first, then implement Sidecar PR 7.1 against the installed framework snapshot. Sidecar host-map acceptance is a deferred companion release gate, not a Step 4 framework blocker. The companion checkout currently remains at PR 7: `RuntimeConfigurationService.prepare` calls the two-argument framework overload, and no PR 7.1 encrypted credential/UI implementation or host-map integration test is present. Its PR 7.1 ticket owns full UI/API, encrypted storage, restart, rollback, and export work. The framework snapshot was installed; existing Sidecar focused integration tests passed (28 tests), and its full suite passed (222 tests, 3 skipped). These do not supply the future PR 7.1 host-map acceptance evidence. No Sidecar production files were changed under framework PR 11.1.

## References

- Ticket: `ai/thoughts/tickets/loomspan-pr-11.1-complete-execution-configuration.md`.
- Research: `ai/thoughts/research/2026-09-26-pr-11-1-complete-execution-configuration.md`.
- Companion: `../loomspan-sidecar/ai/thoughts/tickets/loomspan-pr-7.1-complete-execution-configuration.md`.
