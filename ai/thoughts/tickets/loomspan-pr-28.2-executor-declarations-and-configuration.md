# PR 28.2 — Validate executor declarations and preserve configuration faithfully

## Outcome

Give YAML declarations and executor configuration one authoritative structural interpretation. Produce immutable, explicit executor-leaf definitions and namespace-owned configuration data for generation integration, without duplicating REST validators or flattening executor-owned content.

## Requirements

- Within existing manifest processing, preserve authored field presence, determine and validate declaration form, validate applicable fields, then construct the immutable definition. Carry declaration form explicitly downstream; absence of model execution configuration must no longer mean REST. Consolidate duplicated raw/typed/definition authoring restrictions; malformed-type checks and internal invariant protection must not become independent versions of the same form rules.
- Add executor-backed direct leaves with `executor:` reference, optional map-valued `config:`, identity, description, input schema, and access rules. Reject combinations with TypeSafe, model, prompt, planning, concurrency, child skills, linter, output forwarding/bindings, output schema, or output-schema retries under the accepted leaf rules. Forbidden field presence fails even for null or empty values. Keep model-backed and TypeSafe declaration behavior coherent; do not silently select precedence among forms.
- `config` is permitted only on executor leaves. Omission or `{}` yields an empty immutable map. Explicit null, a bare field, scalars, and lists fail. On other forms, even empty/null `config` fails. Preserve nested null values. Reuse existing YAML input-schema handling; executor domain configuration is not a Framework schema.
- Remove `rest` from supported manifest syntax; old declarations fail normal unsupported-field validation. Remove obsolete REST authoring checks and implicit REST definition assumptions. PR 28.3 removes remaining registration/runtime/API support; do not preserve the old declaration form for it.
- Support `loomspan.executor-config.<namespace>.<relative-path>` at startup and in published `ExecutionConfiguration`. Startup may supply multiple namespaces. Publication permits only its target namespace and rejects foreign entries. Publication is complete replacement: omission supplies no executor configuration and never inherits startup or prior-generation data. Establish one immutable internal representation and shared structural validation/copy rules, to be stored by PR 28.3's existing namespace generation owner.
- Preserve executor data through actual startup ingestion, publication parsing, and manifest/configuration copy boundaries: nested maps/lists, null leaves, empty containers, scalar types, case-sensitive keys, and literal dotted keys inside subtrees. Use PR 28.1's shared container copying, not JSON round-tripping or scalar flattening. Correct an ingress that loses these distinctions; do not introduce downstream repair or separate startup semantics.
- Annotation paths use simple dot-separated map keys relative to the executor namespace. Empty string means no reference; whitespace-only, leading/trailing whitespace, and empty segments fail without trimming. No array indexing, wildcard, expression, fallback lookup, or literal-dot key addressing is supported. Keep the semantic path-validation/binding owner in the shared dependency resolution delivered by PR 28.3; do not create separate startup, manifest, and invocation resolvers here.
- Skill and executor configuration remain separate, general executor-owned data. No merging, precedence, interpolation, domain validation, credential-reference interpretation, sensitivity classification, typed-binding API, validation callback, or resource factory. In particular, `api-key-ref` inside executor data is ordinary content.
- Update relevant configuration/authoring guidance and parser fixtures with this new syntax and intentional REST syntax removal. Tests belong with this ticket; PR 28.7 completes repository-wide examples and documentation.

## Acceptance criteria

- [ ] Valid executor leaves produce explicit immutable declarations; conflicting forms and forbidden ordinary/null/empty fields fail at the authoring boundary with source/field diagnostics. Existing model and TypeSafe form tests remain meaningful.
- [ ] Omitted/empty configuration succeeds, explicit null/non-map configuration fails, and `config` on non-executor forms and old `rest` syntax fail. Raw binding, copying, and definition construction contain no competing form validators or implicit REST inference.
- [ ] Equivalent real Spring startup and publication inputs preserve the accepted nested content and scalar distinctions, including case-sensitive/dotted data keys and nulls. Tests exercise ingestion rather than only a manually constructed map property.
- [ ] Target-namespace enforcement, replacement without inheritance, and defensive copying are verified. Parser output can be owned by namespace generations without a separate configuration authority.
- [ ] Configuration remains uninterpreted domain data and validation does not call application code. Updated syntax guidance and `LoomspanPublicSurfaceArchitectureTest` reflect any changed production types.

## Context and sequencing

Depends on [PR 28.1](loomspan-pr-28.1-shared-java-discovery-and-invocation.md). Owns finding 8's authoring boundary and finding 6's ingress/structural rules in [the accepted design](../phases/configured-java-skill-executors.md); PR 28.3 owns generation storage and shared reference binding. Apply the [feature design lens](../framework-feature-design-lens.md). Do not build speculative abstraction layers to separate these tickets.

Source recheck: `YamlSkillCatalog` currently repeats REST restrictions in raw and typed validation; `YamlSkillManifest` tracks presence; `YamlSkillDefinition` copies manifests and equates REST with absent execution configuration. `ExecutionConfigurationParser` currently accepts only connections, models, session, and execution-trace, and flattens supported settings into Spring binding. Extend the executor ingress without imposing that flattening on its data or rewriting unrelated configuration machinery.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Manifest/configuration contracts and startup/publication fidelity cross several production boundaries.
- **Reassessment triggers:** Concrete ingestion limitations that cannot satisfy the accepted contract without a new product decision; ordinary internal restructuring is already authorized.

## Pipeline notes

- No customers or deployments require compatibility. REST syntax removal, changed internal definitions, and temporarily broken consumers/builds until later PR 28 tickets are intentional. Add no alias, migration reader, adapter, or temporary duplicate parser. All repository consumers must be coherent by series completion.
- This ticket validates Framework structure only; SQL/HTTP/domain configuration errors remain invocation-time application errors. Planner recovery and Sidecar migration are excluded.
