---
audience: loomspan-skill-builder
status: development
applies_to: bundled-loomspan-revision
coverage: source-verified
---

# Evidence Contracts

## Applicability

The shared [standard schema compiler](standard-schemas.md) validates annotation
shape and placement and preserves expression text. Catalog registration compiles
expressions and checks direct-child references separately from structural schema
validation. Planning and final-output validation apply the truth sets below.

Use an `x-loomspan-evidence` annotation on an immediate root `output_schema` property when that output claim MUST be supported by successful direct child execution. Evidence enforces supportability; it does not prove factual correctness or data lineage, and it is not a workflow, ordering, dependency, authorization, or output-value condition language.

```yaml
allowed_skills:
  - name: classify-incident
  - name: investigate-network
  - name: investigate-app
output_schema:
  type: object
  properties:
    severity:
      type: string
      x-loomspan-evidence: classify-incident
    likelyCause:
      type: string
      x-loomspan-evidence: classify-incident and (investigate-network or investigate-app)
  required: [severity, likelyCause]
  additionalProperties: false
```

`x-loomspan-evidence` is Loomspan orchestration metadata. It does not become a candidate JSON field or a provider schema keyword.

## Placement and shape

| Location | Supported |
| --- | --- |
| Immediate child of root `output_schema.properties` | Yes |
| Root `output_schema` node | No |
| Nested object property | No |
| Array `items` or a property below an item | No |

Each annotation MUST be one nonblank YAML string. Null, Boolean, numeric, list, and object values are rejected without coercion. A root property without `x-loomspan-evidence` has no inferred evidence requirement, regardless of its name, description, requiredness, siblings, or the declaring skill's children. Java skills do not declare `output_schema`; evidence declarations belong to a model-backed YAML parent. Exact direct child names may identify Java, executor-backed YAML, or model-backed YAML skills.

Claim property names are exact: `Result`, `result`, ` result` and `result ` are
independent names. Lookup never trims whitespace or folds case. An absent optional
claim needs no execution credit; a present claim, including a permitted null,
requires its expression to be satisfied. Required absence fails structural
validation before evidence evaluation. Ordinary data properties named `evidence`
or `x-loomspan-evidence` do not become annotations.

Neither top-level `evidence_contract` nor schema-node `evidence` is accepted; there is no alias or merge behavior.

## Expression language

The value is a Boolean expression over exact direct names from the declaring skill's `allowed_skills`:

```text
expression     := or_expression EOF
or_expression := and_expression (OR and_expression)*
and_expression := primary (AND primary)*
primary        := SKILL_NAME | "(" expression ")"
```

- `and` binds more tightly than `or`; parentheses override precedence.
- Operators are case-insensitive whole tokens and render canonically in lowercase.
- Leaves use a local name in the containing skill's owner or an explicit `namespace:local-name`. Both components follow the [exact identity grammar](mental-model.md#canonical-namespace-identity). Each compiled identity MUST match a direct `allowed_skills` entry. Original expression text and 1-based token columns are preserved.
- Whitespace around and between tokens is insignificant.
- Bare `and` and `or` are operators in every casing. Qualified `orders:and` and `orders:or` are skill references. In `orders`, `orders:and or lookup` is a disjunction of exactly `orders:and` and `orders:lookup`. Hyphenated local names such as `android-check` remain identifiers.
- Negation, `&&`, `||`, quoted names, implicit operators, predicates, and output-value conditions are unsupported. Only `and` and `or` are reserved operator tokens.

## Truth sets

| Validation point | Names that satisfy references | Properties evaluated |
| --- | --- | --- |
| Plan validation | Nonblank, case-sensitive `PlanTask.capabilityName()` values | Every evidence-annotated root property, including optional properties |
| Final output validation | Direct child names recorded only after successful completion in the current YAML mission | Evidence-annotated top-level properties present in the schema-valid candidate |

Planned, started, failed, cancelled, merely visible, wrong-case, or blank capability names do not satisfy final validation. Repeated successful calls remain one set member. An unsupported optional property may be removed during output retry; a required property ultimately fails when its expression remains unsatisfied. Evidence retry receives completed tool exchanges and the latest raw provider candidate, with executable callbacks removed. A returned tool selection fails before dispatch; it MUST NOT execute further work. See [ordinary final-output correction](output-contracts.md#ordinary-final-output-correction) for replay, binding snapshots, budgets and exhaustion.

Evidence correction can still make additional physical model attempts and consume model-call and usage quotas. See [traces-and-debugging.md](traces-and-debugging.md) for retry identity and accounting semantics.

`classify-incident and (investigate-network or investigate-app)` succeeds with classification plus either investigator. It does not require both investigators. Runtime prompts and diagnostics retain the canonical expression and structured all/any gaps.

## Direct-child and nested boundaries

Java children satisfy the same exact-name success rules as YAML children. Authorization denials and failed or cancelled calls never earn evidence credit.

A reference MUST name a direct child of the declaring skill and MUST NOT name a grandchild or internal probe. During a nested YAML mission, Loomspan isolates the child's successful-skill set from its parent. Child internals never bubble upward; after a nested child succeeds, only that child's public direct name is credited at the parent boundary.

## Evidence delivery versus supportability

The `x-loomspan-evidence` annotation evaluates a set of successful direct-child names; repeated calls contribute one set member. It does not inspect returned values or retrieve task results. Separately, planning missions deliver complete task-keyed direct returns to later execution units and final synthesis, including a nested child's complete final return while keeping that child's private intermediates isolated. See [complete task evidence](planning-concurrency.md#complete-task-evidence) for grouped snapshots, explicit child arguments and reference limitations.

Schema validity checks output structure, successful-child annotations check execution supportability, and complete result delivery preserves available data. None proves factual correctness, source accuracy or quote fidelity. Applications needing those guarantees must validate the returned claims against their sources.

## Authoring procedure

1. Identify immediate root output properties needing supportability enforcement.
2. List the declaring skill's direct children (same-owner Private or accessible foreign Public targets) in `allowed_skills`.
3. Put the smallest sufficient expression in each property's `x-loomspan-evidence` annotation.
4. Use `and` for all-required children and `or` for genuine substitutes.
5. Verify exact casing and direct-child scope.
6. Confirm every annotated property is coverable during planning, including optional properties.
7. Test missing conjuncts, each OR alternative, failure/non-credit, optional omission, required failure, and nested isolation.
8. Keep workflow sequencing and multi-intent branch policy in prompts and planning design.

Catalog diagnostics identify the complete `output_schema...x-loomspan-evidence` path. Parser and reference failures include a 1-based expression column. Invalid component spelling fails validation before direct-child lookup.

## Known limits

Evidence expressions are monotonic and name-only. They do not inspect tool output values, prove correctness, choose which optional properties the model emits, enforce ordering, express negation, or replace RBAC and visibility checks.

Evidence also does not impose unconditional generated-plan task cardinality. Use [planning-task-constraints.md](planning-task-constraints.md) for explicit per-child minimums and maximums; those constraints do not change evidence truth sets.

## Implementation and test anchors

- `OrdinaryFinalOutputRepairIntegrationTest` exercises real evidence/schema/linter advisors separately and together, canonical dispatch and actual three-adapter repair serialization; `OrdinaryFinalOutputRepairLifecycleTest` covers invocation isolation and real accounting. Catalog expression extraction and exact claim lookup are covered by the evidence catalog and contract tests.

- `StandardSchemaCompiler` and `StandardSchemaCompilerTest` validate current annotation placement and scalar shape. `YamlSkillCatalog` registers canonical schemas; its expression extraction uses the existing Boolean parser and exact claim keys.
- `EvidenceExpressionParser.java` (`src/main/java/ai/loomspan/internal/runtime/evidence/EvidenceExpressionParser.java`) defines parsing, while `EvidenceExpression.java` (`src/main/java/ai/loomspan/internal/runtime/evidence/EvidenceExpression.java`) defines canonical rendering, evaluation, and structured unsatisfied requirements.
- `EvidenceCoverageValidator.java` (`src/main/java/ai/loomspan/internal/runtime/evidence/EvidenceCoverageValidator.java`) defines plan and final truth sets.
- `YamlSkillEvidencePropertyCatalogTest.java` (`src/test/java/ai/loomspan/internal/skill/YamlSkillEvidencePropertyCatalogTest.java`) and `YamlSkillEvidenceExpressionCatalogAdditionalTest.java` (`src/test/java/ai/loomspan/internal/skill/YamlSkillEvidenceExpressionCatalogAdditionalTest.java`) protect placement, scalar shape, exact identity, and diagnostics.
- `OutputSchemaCallAdvisorTest.java` (`src/test/java/ai/loomspan/internal/outputschema/OutputSchemaCallAdvisorTest.java`) and `StepPromptBuilderTest.java` (`src/test/java/ai/loomspan/internal/runtime/step/StepPromptBuilderTest.java`) protect metadata non-leakage.
- `NestedSuccessfulSkillBoundaryTest.java` (`src/test/java/ai/loomspan/internal/runtime/tool/NestedSuccessfulSkillBoundaryTest.java`) protects nested boundary credit.

Context/column evidence: `EvidenceExpressionParserTest#contextualLeavesPreserveGrammarTruthAndOriginalColumnsWithoutContextLeakage` and `NamespaceIdentityFoundationTest#evidenceErrorsRetainOriginalTokenColumns`. Runtime truth sets contain the compiled qualified identities.

`EvidenceExpressionParserTest#qualifiedOperatorNamesRemainLeavesInOwningNamespace` verifies qualified operator leaves, exact ordered references, canonical text and truth sets.
