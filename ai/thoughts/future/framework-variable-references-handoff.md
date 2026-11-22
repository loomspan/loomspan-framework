# Framework ticket handoff — application variables at startup and preparation

**Ready to draft the Framework ticket.** The agreed contract covers entry-point source selection, marker syntax and scope, one resolution across the effective candidate, Spring profile precedence, literal string delivery, generation capture, and the Framework, Sidecar, and Cloud responsibilities described below. Carry these decisions into the ticket as requirements and the worked examples as acceptance criteria. Implementation is pending.

For a follow-up review, report a new decision only if you can identify a concrete contradiction, an uncovered case that needs an owner’s choice, or evidence that changes an agreed assumption. Differences between current code and the agreed behavior are implementation work for the ticket. The ticket writer can specify diagnostic path notation during drafting. The remaining product question belongs to Cloud: whether tenant Vertex AI connections must supply explicit credentials. That review can proceed in parallel.

Use the repository's ticket conventions. Framework, Sidecar, Cloud, and their consumers are still in development and have no deployments. Coordinate breaking changes across repositories. Retire superseded syntax, fixtures, and executor credential plumbing directly. Compatibility shims and a transition period are unnecessary.

## Resolution contract

Look up each distinct referenced name once per startup or preparation. Reuse the returned string at every occurrence across the candidate, including provider settings, namespace executor settings, and skill documents. Each subsequent preparation performs fresh lookups. Choose the source at the entry point and give the resolver access only to that source.

| Entry point | Lookup contract |
| --- | --- |
| Standalone Spring Boot startup, including file-based Sidecar | Resolve from the host's Spring `Environment` using its configured property-source precedence, including system environment variables. Cover startup configuration and startup skill documents even when the host never calls the reload API. |
| Any no-map `SkillReloader.prepare` overload | Use the same Spring lookup contract as startup. |
| Any explicit-map `prepare` overload, including versioned preparation | Copy the supplied map and use it exclusively for that candidate. A null map is invalid. An empty map selects explicit-map mode. A pack cannot select a lookup mode or request fallback. |

Spring mode uses the effective string returned by the host's normal `Environment.getProperty(name)` lookup. For example, if Spring resolves `report.label` from `${region}` to `west`, consumers receive `west`. In explicit-map mode, a `report.label` entry containing `${region}` delivers those exact characters even when Spring defines `region`. Loomspan carries the returned string through binding without another expansion in either mode.

**Settled decision.** Each startup or preparation performs one resolution across the entire effective candidate. Connections, namespace executor settings, and executor-backed skill documents share the same lookup results and used-name accounting. A single entry satisfies every use of its name, including references found only in a skill document. Fail for a missing or blank required value. In explicit-map mode, check for unused entries only after accounting for references across all three sections. Keep lookup local to the startup or preparation even when several namespaces prepare concurrently. Record source filename and authored path for diagnostics where applicable. Construct resolution errors and logs from paths and reasons without including supplied values. Preserve the actual content delivered to consumers.

## Where an author can use a reference

Using a variable is the author's choice at startup and in published packs. Every supported setting can carry a literal accepted by its schema, including a provider API key, header value, or Gemini service-account JSON string. Only an explicit marker triggers lookup or contributes a name to the required map. Field type and provider validation apply in either case. Carry this choice through Cloud and Sidecar authoring, including migration of Sidecar's existing managed credential restrictions.

**Settled scope decision. Resolve every explicit marker throughout the agreed sections, then validate the result.** The three section boundaries below determine where traversal starts. Within each subtree, visit mapping keys, mapping values, and sequence elements at every depth. The examples illustrate uses of this rule. Adding a field, provider, or executor requires no eligibility decision. Carry this rule into ticket drafting, implementation, and review. Revisit the scope only if the product owner explicitly changes it.

| Authored location | What it configures |
| --- | --- |
| `loomspan.connections.<connection>` in execution configuration | Provider settings, including API keys, header values, and Gemini service-account JSON. |
| `loomspan.executor-config.<namespace>` in execution configuration | Namespace settings for installed executors, including future executor-specific nested maps and sequences. Preserve the existing target-namespace check. |
| Descendants of the top-level `config` map of an executor-backed YAML skill | Settings for that individual operation, delivered as `skillConfig()`. A route, queue, report template, or dataset can vary by installation while the same skill ZIP is reused. |

Identify these sections from the authored document. The connection and namespace names that select the sections, and skill fields alongside `config` such as `executor`, roles, schemas, transforms, and prompts, are outside them. Inside a section, a field named `prompt`, for example, receives the same traversal as its neighbors. Models, session policy, and invocation inputs are outside these sections.

### Variable reference syntax

Anywhere within an agreed section, a YAML mapping with the single, case-sensitive key `$var` denotes a reference. Flow and block mappings have the same meaning. The name must parse as a nonblank YAML string with no leading or trailing whitespace. Reject invalid names at the authored path. In explicit-map mode, match the name exactly, including case. In Spring mode, pass the name as written to the host's normal lookup.

| Authored node | Interpretation |
| --- | --- |
| `{ $var: app.region }` | Resolve the entire setting from the name `app.region`. |
| `{ $var: "123" }` | Look up the string name `123`. An unquoted numeric value fails the name check. |
| `{ $var: null }` or `{ $var: " app.region" }` | Invalid reference. Staged validation reports the path before lookup. |
| `{ $var: app.region, label: primary }` | Ordinary mapping. Its string children are literals. Continue walking its keys and values for nested markers. |
| `'${region}'` or `'prefix-${region}'` | Ordinary string. Loomspan performs no interpolation. |

The exact one-key shape is reserved throughout the agreed sections. This version provides no escape for passing that shape as a literal object. The marker accepts one name and provides no defaults or inline expressions.

The spelling is a Loomspan convention. CloudFormation’s long-form `Ref` provides a precedent for expressing a whole-value reference as a one-key mapping. YAML defines the mapping structure and leaves lookup semantics to the application. This lets Go and Java recognize references in the parsed tree without scanning string contents. [CloudFormation Ref](https://docs.aws.amazon.com/AWSCloudFormation/latest/TemplateReference/intrinsic-function-reference-ref.html) · [YAML specification](https://yaml.org/spec/1.2.2/)

Walk the parsed YAML tree, including sequence elements. Replace a marker with one nonblank string. Preserve all other scalar types, nested containers, and literal strings, including authored `${row.id}`. Do not interpolate substrings, reparse a supplied string as YAML, or perform another reference or Spring placeholder pass during binding. A supplied string containing braces, newlines, or YAML punctuation stays one scalar. At an existing typed binding boundary, apply that field's normal conversion and validation. Executors that require a native numeric node, for example, need an explicit conversion rule to accept a string there.

For example, this provider header uses the same marker in the key and the value:

```yaml
headers:
  ? { $var: header.name }
  : { $var: app.region }
```

With `header.name` set to `X-Region` and `app.region` set to `west`, the provider validates and receives `{X-Region: west}`. Discovery must retain both authored locations before converting YAML to a string-keyed map. If two keys resolve to the same string, fail with their authored locations before either entry can overwrite the other. Perform substitution early enough that a parser or binder expecting textual keys sees the resolved names.

The scope also governs where values may flow. A private host property substituted into a prompt could produce a perfectly valid string that is then sent to a model. Schema validation cannot detect that destination mistake. An executor may itself send its configured values to a service or model, so its configuration carries the same trust implications as authoring those values directly. The existing authorization boundaries still apply.

**Gemini decision.** Remove explicit credential loading by URI throughout Loomspan, including the startup setting `gemini.credentials-uri`, the published `gemini.credentials-ref` field, and the adapter's resource loader for credentials. A host supplying explicit Vertex AI credentials supplies service-account JSON contents. The adapter already parses those contents. Keep Gemini API-key authentication. Migrate examples and tests, including coverage that the retired syntax cannot trigger a resource read.

**Open Cloud question for the parallel Cloud review.** When a Vertex AI connection omits explicit credentials, Framework builds the Google client without supplying credentials. Verify whether the SDK can use the engine's identity in the actual Cloud deployment. The recommendation for Cloud to evaluate is to require application-supplied service-account JSON for tenant Vertex AI connections, supplied literally or by reference. Identify the simplest shared Cloud enforcement point for publish, restart, and recovery. This policy is pending Cloud review and does not block drafting the Framework ticket.

**Ticket details.** Specify diagnostic path notation for dotted keys, list indices, and markers in authored keys. Preserve YAML resource limits when walking aliases. Use the planned provider fields `api-key`, `headers`, and `gemini.credentials-json` at startup and publication. Migrate `api-key-ref`, `header-refs`, and `gemini.credentials-json-ref` to those fields respectively. A single `headers` map can mix literal and referenced values. Use variable terminology such as `variableValues` for the existing map parameter and documentation. No new public resolver or executor API is required by this design.

## Worked discovery and consumption example

These proposed pack excerpts illustrate the relevant fields. The example uses non-secret test values and an HTTP executor registered for the target namespace.

`loomspan/execution.yaml`

```yaml
loomspan:
  connections:
    primary:
      driver: openai
      api-key: { $var: provider.key }
      headers:
        X-Region: { $var: app.region }
  executor-config:
    sales:
      http:
        targets:
          records:
            base-url: { $var: records.url }
            headers:
              X-Region: { $var: app.region }
              Accept: application/json
```

Executor-backed skill document `loomspan/skills/read-record.yaml`

```yaml
name: readRecord
description: Read a record by ID
executor: http
config:
  target: records
  method: GET
  path: { $var: records.lookup-path }
  pathParams: {recordId: /recordId}
input_schema:
  type: object
  properties:
    recordId: {type: string}
  required: [recordId]
```

| Distinct name | File and authored paths |
| --- | --- |
| `provider.key` | loomspan/execution.yaml → `loomspan.connections.primary.api-key` |
| `app.region` | loomspan/execution.yaml → `loomspan.connections.primary.headers.X-Region` and `loomspan.executor-config.sales.http.targets.records.headers.X-Region` |
| `records.url` | loomspan/execution.yaml → `loomspan.executor-config.sales.http.targets.records.base-url` |
| `records.lookup-path` | loomspan/skills/read-record.yaml → `config.path` |

The host supplies exactly four entries:

```json
{
  "provider.key": "demo-key",
  "app.region": "west",
  "records.url": "https://records.example.test",
  "records.lookup-path": "/v2/records/{recordId}"
}
```

The resolved target has the literal `Accept` header and the supplied `X-Region`. The skill's captured path is `/v2/records/{recordId}`. The HTTP executor fills the ID from the request using its ordinary path mapping at invocation. As a sequence fixture for a future executor, `config.destinations: [{region: {$var: app.region}}]` adds a use at `config.destinations[0].region` and requires no fifth map entry.

## Validation, binding, and generation ownership

**Required ticket language.** Variable substitution must follow the existing Framework rules for staged validation, preparation, and publication that apply to skills and execution configuration. Implement it within those same lifecycle stages and preserve their existing guarantees for every preparation overload. Carry this requirement and the edited-pack acceptance case below into the Framework ticket.

A staged validation result describes only the pack supplied to that call. For each Cloud publish request, discover references in the ZIP selected for that request and pass its documents, execution configuration, and current application values to Framework `prepare`. Preparation checks that candidate independently of any earlier validation. Framework `publish(PreparedSkillUpdate)` checks ownership, lifecycle, and target-generation freshness, then activates the prepared candidate. Including edits or variable changes made after preparation requires a fresh preparation.

1. Framework staged `validate` works before any values are supplied. Check document form, allowed sections, reference shape and name, namespace targeting, skill declarations and dependencies, and literal constraints wherever the existing validator can decide them. Defer checks that depend on a referenced value. An unresolved marker is not a usable string or typed setting.
2. During startup or preparation, resolve before provider construction and before binding or capturing executor and per-skill configuration. Validate resolved Framework-owned settings. Preserve executor-owned domain validation timing. Sidecar's HTTP executor currently validates its target and operation on invocation. Successful preparation alone does not establish route validity or reachability.
3. Capture the resolved skill definitions and configuration in the generation. For a foreign executor, `executorConfig()` comes from the executor owner's captured settings. `consumerExecutorConfig()` and `skillConfig()` come from the appropriate skill-owning namespace. Each namespace's references must already have been resolved against its own source. Preserve existing configuration selection rules. Executors receive ordinary settings and request inputs. They need no variable store, lookup callback, or secrets map.
4. A failed preparation leaves the active generation serving. Publishing activates the prepared values without further lookup. Reinstalling the same ZIP and opaque publication version after rotation can create a fresh generation. Previously admitted work retains its original generation and values.

## Implementation evidence and coordinated tickets

### Framework startup binding findings

Verified against Framework's Spring Boot startup path on 2026-11-22. All five `ExecutorConfigurationStartupTests` passed. A temporary Spring Boot binding test also passed with a base OpenAI connection, an active profile supplying only its base URL, and `region=west`. The connection retained the base driver and API key. Spring expanded an authored API key of `${region}` to `west`, while the identical string under executor configuration reached the bound settings literally. Looking up the separate host property `report.label=${region}` through `Environment.getProperty("report.label")` returned `west`.

**Worked startup example for file-based Sidecar or a standalone Spring host.** Spring determines the effective settings first. Loomspan then resolves the markers in those settings together with the configured skills.

`application.yaml`

```yaml
loomspan:
  connections:
    primary:
      driver: openai
      api-key: { $var: default.provider.key }
      headers:
        X-Region: { $var: app.region }
```

`application-local.yaml`

```yaml
loomspan:
  connections:
    primary:
      api-key: local-demo-key
```

Assume Spring supplies `app.region=west`, `default.provider.key` is absent, and a configured executor-backed skill uses `{ $var: app.region }` in its `config`. With the `local` profile active, startup succeeds. The connection uses `local-demo-key`, inherits the `openai` driver, and sends the configured `X-Region` header with value `west`. The skill receives the same `west`. Loomspan looks up `app.region` once across both uses and performs zero lookups for the overridden `default.provider.key`. With the `local` profile inactive, the base API-key marker is effective and startup fails because `default.provider.key` is missing.

Carry these constraints into the Framework ticket:

- Retain marker structure and authored locations while reading YAML. Defer lookup until Spring has selected the effective settings. `ExecutorYamlPropertySourceLoader` sees individual documents before profile selection and already transports `loomspan.executor-config` as one raw value. The profile/import test confirms that the winning executor section replaces the lower one. Preserve the separately verified field-level overrides for connections, including fields inherited from lower-priority sources.
- Resolve effective connection settings before typed binding and `LoomspanProperties.afterPropertiesSet()`, which validates connections. Preserve normal type conversion while preventing Spring from expanding the delivered string again. Under the agreed syntax an authored provider literal such as `${region}` will now stay literal. A startup configuration that intends a lookup must migrate that field to `{ $var: region }`. Keep ordinary Spring properties outside the agreed sections on their existing binding path.
- Share one startup lookup cache across effective connections, executor settings, and the configured skills read by `YamlSkillCatalog.checkedConfigured`. Keep that cache local to startup. In an end-to-end Framework startup test, override a base connection marker with a higher-priority literal while inheriting another base field, and put a missing reference in an inactive profile. Verify zero lookups for both displaced references, one lookup for a name shared with a skill, and the final values observed by consumers. The baseline tests establish existing Spring behavior. This end-to-end case verifies the new marker implementation.

**Framework.** `SkillReloader` and `DefaultSkillReloader` already distinguish Spring and copied explicit-map preparation for provider references. `ExecutionConfigurationParser` currently recognizes specific provider fields and checks unused keys there. Move accounting to the complete candidate, including skill documents. `SkillGenerationManager` currently checks unresolved documents before reparsing the execution configuration with values. Ensure the definitions used to build the generation include the resolved per-skill data as well. Startup follows a separate binding path through `ExecutorYamlPropertySourceLoader` and `ExecutorConfigurationBinding`. Cover both paths, including literal preservation through Spring binding. `YamlSkillCatalog` reads the executor leaf's raw `config` map, `SkillDependencyValidator` selects namespace executor settings, and `SkillExecutorInvocation` already exposes the separate configuration maps. Inspect/update `ExecutionConfigurationParserTest`, `ExecutorConfigurationTests`, and `SkillReloaderTest`, and run `LoomspanPublicSurfaceArchitectureTest` after production type changes.

**Sidecar and consumers.** File mode loads settings and skills at startup. In managed database mode, `RuntimeConfigurationService` calls Framework validation, performs detached preparation, and uses explicit-map preparation for publication and restoration. `EffectiveExecutionConfiguration` currently collects selected provider references and attaches encrypted credential records to HTTP executor settings. `HttpSkillExecutor` looks up and decrypts fixed header values during requests. Ticket the managed candidate builder to discover variables across both document types and supply the exact map. Migrate fixed service headers to resolved configuration. Keep caller-token passthrough request-scoped. Update management/editor behavior, examples, consumer packs, and the Sidecar test suite to use the new contract. Sidecar work may land in a follow-up ticket.

**Cloud.** Each application owns its variable store and runs in a private namespace on a shared Java engine. For staged checks and before publish, restart, or recovery install, Go scans the selected immutable ZIP's `loomspan/execution.yaml` **and its executor-backed skill documents**. Use the same document selection as installation. Collect distinct names and all source filenames and paths, then look up current values in the destination application's store. This expands the original execution.yaml-only discovery requirement. Keep the authored representation in drafts, management reads of authored configuration, and publication ZIPs. Resolved settings belong to the prepared generation. Restart and recovery use the original ZIP and publication version with the destination application's current values. Discovery requires neither a stored manifest nor a Java call.

For installation, Go sends the ZIP, namespace, opaque Cloud-assigned `publicationVersion`, and exact current value map over mTLS. The wrapper rejects an omitted or null map and accepts an explicit empty map for a reference-free pack. It adapts the ZIP to skill documents and execution configuration, calls versioned explicit-map `prepare` with private visibility, then publishes. ZIP handling, transport, application authorization, and variable-store access belong to Cloud.

## Acceptance and return handoff

- Validate pack A successfully, then edit a skill to introduce a new reference in pack B. Submit B for Cloud publication with the new variable missing and verify preparation fails while the prior generation keeps serving. Supply the value and retry B. Verify the published skill contains B's edit and uses the value captured during that preparation.

- Exercise provider API-key and Vertex AI service-account JSON configurations in their respective modes with both literal and referenced values. A valid all-literal pack prepares with an explicit empty map. In a mixed pack, discovery and lookup count only the explicit references.

- Run the worked fixture through independent Go extraction and Java preparation. Assert every filename/path, the four distinct names, the provider settings, and the configuration observed by an actual executor. Cover nested lists, equivalent block and flow markers, multi-key maps containing `$var`, invalid reference names, exact case matching, and a variable used only by a skill.
- Use the header example to verify that Go discovers both names and Java resolves both positions. Add a literal sibling key that collides with the resolved key and verify preparation fails. Exercise a previously unknown executor field with a nested marker to guard against per-field allowlists.
- Exercise standalone startup and no-map preparation with configured Spring property sources. In startup YAML, verify that the active profile's effective setting wins and a marker in an inactive or overridden setting triggers no lookup. In explicit-map mode, prepare two namespaces sharing a JVM with the same reference name and different values while Spring defines a third. Include a foreign-executor case to verify owner and consumer settings independently.
- Accept an empty map for a reference-free pack. Fail on null, missing, blank, or unused entries as applicable. Removing a key from one application's map must fail even if the host or another namespace has it. Verify actionable authored paths, absence of supplied test values in resolution errors/logs, and continued service from the prior generation.
- Verify the `report.label` example at the consumer after binding in both modes. Repeat the reference across settings and skill documents and assert one Loomspan lookup for that name per preparation. A new preparation reads it afresh.
- Validate a reference-bearing candidate without values, then exercise valid and invalid typed settings at the owning validator. Pass a YAML-looking multiline string through to a string consumer unchanged. Confirm excluded fields do not trigger lookup.
- Exercise Sidecar file startup, managed validation, publish, and restoration. Verify fixed headers, caller passthrough, and per-skill route substitution independently.
- Rotate a value and reinstall the original ZIP and publication version. Verify fresh values on new work and original values on work already admitted. Exercise Cloud restart and recovery, including wrapper rejection of omitted/null maps and failure of an incomplete replacement.
- Return the Framework revision or artifact version, final syntax and shared fixtures, test names and results, and linked Sidecar/test-suite and Cloud follow-ups. Use the Cloud results to revisit Fable's finding before deciding whether to close it.
