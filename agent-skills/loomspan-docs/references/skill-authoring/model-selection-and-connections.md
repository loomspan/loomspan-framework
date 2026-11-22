---
audience: loomspan-skill-builder
status: development
applies_to: bundled-loomspan-revision
coverage: initial-source-verified
---

# Model Selection and Named AI Connections

## Applicability

Use this document when an LLM-backed YAML skill needs a model, when an application must route models to endpoints/accounts, or when diagnosing model resolution. Annotation-defined Java skills execute without framework model configuration.

## Authoring model

The author-facing chain is:

`skill model` → `loomspan.models` framework alias → `loomspan.connections` connection → built-in driver → provider model ID

- An LLM-backed skill MUST set `model` to a configured framework model alias.
- A model alias MUST set `connection` and `provider-model`.
- The connection MUST exist and MUST choose exactly one built-in driver: `openai`, `anthropic`, or `gemini`. Executor packs have separate captured configuration; see [TypeSafe pack leaves](typesafe-skills.md).
- Connection names and framework model names are application-owned and case-sensitive map keys.
- Multiple connections MAY use the same driver. Use separate names when endpoints, accounts, credentials, headers, or protocol options differ.
- `default-model` has no special fallback behavior; it is an ordinary alias.

```yaml
loomspan:
  connections:
    local-primary:
      driver: openai
      base-url: http://127.0.0.1:11434/v1
      api-key: ollama
    local-secondary:
      driver: openai
      base-url: http://127.0.0.1:11435/v1
      api-key: ollama
  models:
    summarizer:
      connection: local-primary
      provider-model: qwen3:8b
    planner:
      connection: local-secondary
      provider-model: granite4:latest
```

The YAML skill declares `model: summarizer` or `model: planner`; it does not declare the driver or endpoint.

The resolved per-skill execution configuration and its named connection clients belong to the executing target namespace generation retained by the root snapshot. Cross-namespace children select their own captured models, provider credentials, request timeouts and retries; they do not inherit entry provider settings. Session quotas still use the entry generation; see [execution limits](execution-limits.md). Nested and parallel work, including provider retries, uses those captured clients after another generation is published. New roots use the new generation. Superseded clients close only after captured physical work finishes. A skill and a previously unknown model alias can therefore be validated and published together through `SkillReloader.validate(namespace, documents, configuration)` and `prepare(namespace, documents, configuration)`; hosts with their own credential store may use the map overload. See the [Java publication contract](../java-api/skill-reload.md).

The connection table below applies to embedded `application.yml` and published `ExecutionConfiguration`. Published candidates accept literal settings and whole-node `{ $var: name }` markers under each connection, including header names and values. For example, `api-key: { $var: provider.key }` uses the ordinary provider field. Each distinct name resolves once across providers, namespace executor data and executor-backed skill config before clients are constructed. Normal preparation uses `Environment.getProperty(name)`. An explicit `variableValues` map is copied, matches exact case, requires every used name and rejects unused entries. A candidate without markers accepts an empty map. Validation checks structure and decidable constraints without fetching values.

Published authored `${...}` strings and returned variable strings remain literal after that single lookup. Multiline JSON remains one string. Normal conversion and authentication validation apply after substitution. Candidate YAML may include only `loomspan.connections`, `loomspan.models`, `loomspan.session`, `loomspan.executor-config` and `loomspan.execution-trace.persistence`. Every publication supplies complete configuration and omitted scalars use built-in defaults. Startup provider settings use ordinary Spring binding. The shared marker semantics described here apply to supplied published candidates. See [publication](../java-api/skill-reload.md).

## Connection rules

| Driver | Required mode | Optional connection settings |
| --- | --- | --- |
| `openai` | `api-key` | `base-url`, `request-timeout`, static `headers`, `openai.organization-id`, `openai.project-id` |
| `anthropic` | `api-key` | `base-url`, `request-timeout`, static `headers` |
| `gemini` | Exactly one of API-key mode or Vertex AI mode | `request-timeout` in either mode; API-key mode uses `api-key`; Vertex mode uses `gemini.vertex-ai: true`, `project-id`, `location`, and optional `credentials-json` containing service-account JSON |

Static `headers` are accepted by OpenAI and Anthropic. Header names must be valid HTTP tokens and values must be non-null scalars. Published scalar values use normal string conversion. Explicit Vertex credentials are JSON contents, including multiline JSON. When omitted, the Google client uses its normal default credential construction. Provider-specific option blocks MUST match their driver. Unknown `loomspan.*` fields are rejected at startup. Loomspan does not read, merge, or inherit `spring.ai.*` configuration.

The OpenAI and Anthropic drivers use Spring AI 2's official SDK-backed clients. A configured `base-url` is the SDK service root, not a completion-path override. OpenAI Responses appends `/responses`, so a compatible service that uses the conventional versioned route should provide a root ending in `/v1`. Anthropic appends `/v1/messages` and supplies its required version header through the official client. Put supported Anthropic beta values and other custom headers in the common `headers` map. Compatibility is a service contract, not inferred by Loomspan. The `openai` driver serves OpenAI, Meta, OpenRouter and Ollama through stateless Responses (`store: false`, without `previous_response_id`). Services that only support Chat Completions are unsupported. The local examples use `api-key: ollama`; authenticated deployments require their actual credentials. No native Ollama driver, protocol fallback or compatibility profile exists.

Configure supported Anthropic beta/custom values in `headers` and choose a complete compatible `base-url`. Completion-path overrides and separate Anthropic version/beta fields are unsupported and fail strict startup binding.

Deterministic encrypted-reasoning fixtures do not establish every provider's schema enforcement, effort semantics, routing behavior or reasoning-shape compatibility. Native Ollama controls are unavailable through this shared driver unless the Responses protocol and adapter support them; Loomspan adds no provider-specific translation.

Every connection owns a `provider-retry` policy. Defaults are enabled, three total unchanged-request attempts, 500 ms initial backoff, a 2.0 multiplier, a 5 s cap, and 0.2 jitter. Skills select only the model alias and cannot declare or override this policy. The session-wide `loomspan.session.quotas.max-provider-attempts` safeguard defaults to 192; zero disables that quota.

One Loomspan model interaction is one semantic request from mission, planning, or step execution. The single Spring AI tool loop may require multiple model turns inside that interaction. A semantic validator retry starts another semantic attempt; a provider retry repeats one unchanged model turn. Every actual downstream send is one physical provider attempt, is counted against quota once, and is owned by Loomspan because provider-native retries are disabled.

Connection diagnostic content is preserved. Environment changes require a new preparation or restart. For rotation, publish a replacement and allow captured work to finish before revoking the old key. External revocation can interrupt captured execution.

## Explicit response status

An explicit provider refusal or incomplete generation is terminal before output
correction, planning parsing or tool dispatch. Complete tool-call turns continue
normally; refusal-like prose and missing final text alone do not imply rejection.
Loomspan does not retry an unchanged refused/incomplete generation, increase its
cap or recover a complete-looking subset of tool calls. Existing transport retry
classification remains separate; `failed` is not automatically transient.

The pinned metadata mappings are Responses `incomplete` status, native `refusal`,
`LENGTH`/`CONTENT_FILTER`/`INCOMPLETE` finish reasons and incomplete output items;
Anthropic `max_tokens`, `model_context_window_exceeded`, `pause_turn` and `refusal`;
Gemini `MAX_TOKENS`, `SAFETY`, `RECITATION`, `LANGUAGE`, `BLOCKLIST`,
`PROHIBITED_CONTENT` and `SPII`. Unknown values get no additional policy.

## Provider call budgets

For chat drivers, `loomspan.connections.<name>.request-timeout` MAY bound one physical HTTP call through complete response consumption, including header wait. Headers, whitespace keepalives and continuing body chunks do not reset the whole-call deadline. It is not a connect or read-inactivity setting and cannot be overridden by a skill/model or emitted as request JSON. Existing connect behavior is preserved. The current OpenAI/Anthropic SDK transports also derive their read/write inactivity limits from this duration, so raising the budget raises those inherited limits. This setting does not override provider/server/proxy limits.

| Driver | Omitted `request-timeout` | Explicit value |
| --- | --- | --- |
| OpenAI, Meta, OpenRouter and Ollama through `openai` | 60-second whole-call limit | Configured whole-call budget |
| Anthropic | 60-second whole-call limit | Configured whole-call budget |
| Gemini API key and Vertex AI | Client-call limit unset | Configured whole-call budget |

An authored value MUST be an exact whole-millisecond duration from `1ms` through `2147483647ms` inclusive (24 days, 20 hours, 31 minutes, 23.647 seconds). Spring duration units and exact ISO-8601 syntax are accepted. Zero, negatives, malformed text, sub-millisecond or fractional-millisecond durations, and larger values fail safely with the full property path; values are never clamped or converted to unlimited.

Partial startup fragment; supply model aliases and skills separately:

```yaml
loomspan:
  connections:
    reasoning:
      driver: openai
      base-url: https://openrouter.ai/api/v1
      api-key: ${OPENROUTER_API_KEY}
      request-timeout: 240s
  session:
    mission-timeout: 600s
```

The mission deadline remains authoritative over all calls, other work and retry backoff. Each permitted physical attempt receives its connection budget, but multiple full budgets are not promised inside the mission. Genuine provider timeouts use the existing Framework retry policy; cancellation, interruption and mission cutoff retain their existing failure ownership and late-write fences. Native retries remain disabled.

For `ExecutionConfiguration`, use `api-key: { $var: OPENROUTER_API_KEY }`. Both variable source modes accept it. Validation performs zero lookups or provider calls. New roots receive the published clients/budget. Previously admitted roots, delayed descendants and retries keep the captured generation; each complete publication uses built-in defaults for omitted settings. Use `validate` before preparation for structured configuration feedback. See [publication](../java-api/skill-reload.md).

## Thinking levels

The following thinking rules apply to Framework chat models.

A model alias MAY declare `thinking-levels`. A skill's `thinking_level` MUST be one of those configured values. If the alias has no configured levels, the skill MUST omit `thinking_level`. Thinking options are adapted to the selected driver at request time; the connection remains reusable across model aliases.

For the exact Anthropic provider model `claude-sonnet-5-5`, `low`, `medium`
and `high` select native output effort and adaptive thinking. No explicit thinking
budget is sent and the generation cap is unchanged. Omitted thinking remains
omitted. Other Anthropic model IDs retain the existing explicit-budget mapping:
low 1024/4096, medium 4096/8192, high 8192/16384 (thinking budget/max tokens).
This exact-model mapping does not infer support for other model IDs or establish
hosted availability. Unsupported levels fail locally.

Known Anthropic adapter limitation in the pinned Spring AI `2.1.0-RC2`:
ordinary tool schemas lose root entries such as `additionalProperties`, although
nested constraints and typed-map schemas survive. Full tool-schema fidelity is
unresolved. Native raw output configuration can preserve format schemas and
existing effort when copied; ordinary output attachment and bounded correction have
loopback integration coverage. Those checks do not certify every mission/planner
path or hosted schema enforcement.

For the exact Gemini provider model `gemini-3.8-flash`, `low`, `medium` and
`high` select native `LOW`, `MEDIUM` and `HIGH`. No numeric thinking budget is
sent and the generation cap is unchanged. Omitted thinking remains omitted;
unsupported levels fail locally. Other Gemini model IDs retain numeric budgets
1024, 4096 and 8192 for low, medium and high. This exact-model mapping does not
infer hosted availability or support for other model IDs.

Changing serializable per-request schemas preserves native thinking and generation
caps. Ordinary initial/correction and
tool-continuation wiring have loopback coverage through the same adapter. At this adapter boundary, request-local
typed options must copy the effective target defaults before replacing the schema;
a fresh schema-only builder replaces those defaults, losing native thinking and
the configured cap. Fresh effective-target defaults also prevent carrying an old
model's numeric budget into a native-level target.

## Configuration and diagnosis

1. Create a named entry under `loomspan.connections` for each endpoint/account.
2. Set base URLs, credentials, headers, and provider-specific protocol settings on that connection.
3. Set each `loomspan.models.<name>.connection` to the named connection and `provider-model` to the provider model ID.
4. If two aliases use different endpoints or accounts for the same driver, point them at different named connections.

Startup errors include the complete property path for missing, unknown, or driver-inapplicable settings. Runtime resolution errors identify the skill, framework model, connection, driver, and provider model. Start diagnosis at the named connection, then verify its driver-specific requirements and the alias reference.

For chat drivers, terminal provider failures emit one framework-owned WARN after retry policy decides that the request will escape. The warning identifies the framework model, connection, driver, and provider model and names the settings to investigate. Authentication points to the connection credential setting without claiming it is missing. Connectivity points to network reachability and the driver's applicable connection settings—`loomspan.connections.<name>.base-url` for OpenAI and Anthropic, or Gemini's `api-key`/`gemini.vertex-ai` mode plus `gemini.project-id` and `gemini.location`—without proving that the configured endpoint is wrong. Invalid-request and HTTP 404 guidance points to those driver-applicable connection settings and `loomspan.models.<framework-model>.provider-model` because endpoint, model, and access failures can be ambiguous. A retry that later recovers emits no terminal WARN.

For chat drivers, the identical bounded guidance is recorded on every failed physical attempt, including recovered attempts, so diagnosis can continue in Console alongside the original provider evidence. Provider evidence remains untrusted diagnostic content and may contain sensitive values; Loomspan does not generally scrub it. Follow [traces-and-debugging.md](traces-and-debugging.md) for the exact failure-to-attempt inspection path.

## Source verification anchors

- Configuration and validation: `LoomspanProperties`, `LoomspanPropertiesTest`, `ExecutionConfigurationParserTest`.
- Skill model resolution: `YamlSkillCatalog`, `EffectiveSkillExecutionConfiguration`.
- Connection construction and lookup: `NamedAiConnectionRegistry`, `SpringAiProviderIntegration`, `DefaultSkillChatModelResolver`.
- Retry ownership: `LoomspanPropertiesTest`, `ProviderRetryDeciderTest`,
  `SpringAiProviderIntegrationTest`, `ConnectionProtocolTest`, and
  `ModelAttemptCallAdvisorIntegrationTest`.
- Responses protocol coverage: `SharedResponsesProtocolIntegrationTest` and
  `SpringAiProviderIntegrationTest`.
- Request options and tool-loop assembly: `SpringAiChatOptionsContributorTest` and `SpringAiChatClientAssemblerIntegrationTest`.
- Wire behavior: `ConnectionProtocolTest` and `AnthropicProtocolIntegrationTest`
  (native effort, unchanged caps, changing raw output schemas, legacy controls and
  nested tool constraints and canonical schema preservation).
- Gemini native/legacy options and cap/default isolation: `SpringAiChatOptionsContributorTest`
  and `GeminiProtocolIntegrationTest`.
- Call budgets/default transport phases: `LoomspanProperties#validateRequestTimeout`,
  `ProviderRequestTimeoutIntegrationTest` (shared Responses, Anthropic and both Gemini modes,
  delayed headers, active body deadline, configured success, actual client defaults).
- Captured timeout/publication and cutoff: `PublicSkillReloadIntegrationTest#publishedTimeoutCapturesOldRootAndSurvivesRepeatedSkillOnlyUpdates`,
  `#delayedDescendantAndItsRetryRetainCapturedProviderBudget`, `#longerProviderBudgetCannotExtendMissionOrCallerInterruption`;
  retry accounting: `ModelAttemptCallAdvisorIntegrationTest#realProviderTimeoutRetriesPreserveRequestCountsClassificationAndQuota`.
- Operational identity: `ModelExecutionIdentity`, `ExecutionTraceContractTest`, `MicrometerUsageMetricsRecorderTest`.

This topic does not fully specify provider SDK behavior or third-party compatibility guarantees. Inspect the pinned Spring AI version and the target service's protocol documentation when those details determine correctness.

Canonical tool identities pass to providers unchanged, including underscores. Model plan, step and tool selections normalize namespace case through the common parser before exact bound matching; local-name case stays exact. Malformed, oversized and unbound choices fail. Provider-specific naming checks still apply without aliases or encoding. Raw model output and authored prompts remain unchanged. Evidence: `CanonicalToolCallingManagerTest`, `SpringAiChatClientAssemblerIntegrationTest`, and `NamespaceExecutionAuthorityIntegrationTest`.
