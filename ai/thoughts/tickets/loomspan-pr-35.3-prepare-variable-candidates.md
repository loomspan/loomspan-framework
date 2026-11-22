# PR 35.3 — Resolve and capture one complete prepared candidate

## Outcome

After 35.1 and 35.2, every SkillReloader overload can validate and prepare a namespace whose providers, namespace executor settings and executor-backed skills share application variables. Preserve the existing Framework rules for staged validation, preparation and publication throughout.

## Requirements and acceptance criteria

- [ ] Staged validate needs no values or provider client. Check authored structure, marker shape/name, allowed sections, target namespace, declarations, dependencies and decidable literal constraints. Defer only checks that require resolved values, including effective key names. Prepare independently validates the actual candidate supplied to that call.
- [ ] Every no-map overload uses the host's normal `Environment.getProperty(name)`. Every explicit-map overload, including versioned preparation, copies the supplied map, matches exact case, rejects null and accepts empty for reference-free candidates. Packs cannot select a source. Fail on missing/blank required values and on unused entries after counting all three sections. A skill-only name counts. No host fallback. Concurrent preparations of two namespaces may use the same name with different values while Spring has a third.
- [ ] Resolve a distinct name once per candidate, freshly on each preparation, before constructing providers, binding executor configuration or capturing skill definitions. If Spring returns west for `report.label=${region}`, the consumer gets west. An explicit entry containing `${region}` reaches that consumer unchanged, including through provider binding. Preserve YAML-looking multiline strings. Exercise both valid and invalid typed values at the owning validator. Keep executor domain checks at their existing stage.
- [ ] Use the 35.1 worked fixture with demo-key, west, https://records.example.test and /v2/records/{recordId}. Observe the provider key and headers and an actual executor's namespace and skill settings. Verify mixed literal/referenced headers, key and value substitution, collision diagnostics, referenced Vertex JSON and literal equivalents. Resolution errors and logs give authored paths/reasons without supplied values.
- [ ] Validate A, edit a skill in B to require a new variable, then prepare B without it. The old generation serves through failure. Retry with the value and publish B's edited skill. Publish checks ownership, lifecycle and target freshness, performs no lookup, and activates exactly the prepared data. Rotating a variable and preparing the same documents/version yields fresh settings on new work while admitted work keeps its captured values.
- [ ] For a foreign executor, observe owner settings in `executorConfig()` and skill-owning namespace settings in `consumerExecutorConfig()` and `skillConfig()`, each resolved from the correct namespace's source. Executors receive ordinary settings and request inputs. No new public resolver, SPI or invocation parameter. Run `LoomspanPublicSurfaceArchitectureTest`.
- [ ] Retire published `api-key-ref`, `header-refs`, and `gemini.credentials-json-ref` in favor of whole-node markers in `api-key`, `headers`, and `gemini.credentials-json`. Update current examples, tests, API parameter names and documentation to use variable terminology such as `variableValues`.

## Pipeline notes

All Loomspan products are in development with no QA or production deployments. Destructively replace contracts superseded by this ticket. Breaking other Loomspan projects that still use those contracts is acceptable until their own updates land. Do not add compatibility shims or retain legacy behavior to keep those projects working. Remove superseded syntax without aliases, fallback or a transition period.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Candidate isolation, staged validation and retained generations span multiple lifecycle boundaries.
- **Reassessment triggers:** Reassess if the current checkout already implements the production contract and only localized fixture or documentation work remains.
