---
audience: loomspan-skill-builder
status: development
applies_to: bundled-loomspan-revision
coverage: source-verified
---

# Evidence Contracts

## Applicability

Use an `evidence` annotation on an immediate root `output_schema` property when that output claim MUST be supported by successful direct child execution. Evidence enforces supportability; it does not prove factual correctness or data lineage, and it is not a workflow, ordering, dependency, authorization, or output-value condition language.

```yaml
allowed_skills:
  - name: classifyIncident
  - name: investigateNetwork
  - name: investigateApp
output_schema:
  type: object
  properties:
    severity:
      type: string
      evidence: classifyIncident
    likelyCause:
      type: string
      evidence: classifyIncident and (investigateNetwork or investigateApp)
  required: [severity, likelyCause]
  additionalProperties: false
```

`evidence` is Loomspan orchestration metadata. It does not become a candidate JSON field or a provider schema keyword.

## Placement and shape

| Location | Supported |
| --- | --- |
| Immediate child of root `output_schema.properties` | Yes |
| Root `output_schema` node | No |
| Nested object property | No |
| Array `items` or a property below an item | No |

Each annotation MUST be one nonblank YAML string. Null, Boolean, numeric, list, and object values are rejected without coercion. A root property without `evidence` has no inferred evidence requirement, regardless of its name, description, requiredness, siblings, or the declaring skill's children. Java skills do not declare `output_schema`; evidence declarations belong to a model-backed YAML parent. Exact direct child names may identify either Java or YAML skills.

The removed top-level `evidence_contract` field is not accepted; there is no alias, compatibility reader, or merge behavior.

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
- Skill names are case-sensitive and MUST exactly match a direct `allowed_skills` entry.
- Whitespace around and between tokens is insignificant.
- `and` and `or` are reserved in every casing. Operator substrings in names such as `androidCheck` and `orderLookup` remain identifiers.
- `not`, `&&`, `||`, quoted names, implicit operators, predicates, and output-value conditions are unsupported.

## Truth sets

| Validation point | Names that satisfy references | Properties evaluated |
| --- | --- | --- |
| Plan validation | Nonblank, case-sensitive `PlanTask.capabilityName()` values | Every evidence-annotated root property, including optional properties |
| Final output validation | Direct child names recorded only after successful completion in the current YAML mission | Evidence-annotated top-level properties present in the schema-valid candidate |

Planned, started, failed, cancelled, merely visible, wrong-case, or blank capability names do not satisfy final validation. Repeated successful calls remain one set member. An unsupported optional property may be removed during output retry; a required property ultimately fails when its expression remains unsatisfied. Evidence retry uses already completed work and MUST NOT call tools again.

Evidence correction can still make additional physical model attempts and consume model-call and usage quotas. See [traces-and-debugging.md](traces-and-debugging.md) for retry identity and accounting semantics.

`classifyIncident and (investigateNetwork or investigateApp)` succeeds with classification plus either investigator. It does not require both investigators. Runtime prompts and diagnostics retain the canonical expression and structured all/any gaps.

## Direct-child and nested boundaries

Java children satisfy the same exact-name success rules as YAML children. Authorization denials and failed or cancelled calls never earn evidence credit.

A reference MUST name a direct child of the declaring skill and MUST NOT name a grandchild or internal probe. During a nested YAML mission, Loomspan isolates the child's successful-skill set from its parent. Child internals never bubble upward; after a nested child succeeds, only that child's public direct name is credited at the parent boundary.

## Authoring procedure

1. Identify immediate root output properties needing supportability enforcement.
2. List the declaring skill's direct public children in `allowed_skills`.
3. Put the smallest sufficient expression in each property's `evidence` annotation.
4. Use `and` for all-required children and `or` for genuine substitutes.
5. Verify exact casing and direct-child scope.
6. Confirm every annotated property is coverable during planning, including optional properties.
7. Test missing conjuncts, each OR alternative, failure/non-credit, optional omission, required failure, and nested isolation.
8. Keep workflow sequencing and multi-intent branch policy in prompts and planning design.

Catalog diagnostics identify the complete `output_schema...evidence` path. Parser and reference failures include a 1-based expression column, and a uniquely identifiable casing mistake suggests the exact allowed spelling.

## Known limits

Evidence expressions are monotonic and name-only. They do not inspect tool output values, prove correctness, choose which optional properties the model emits, enforce ordering, express negation, or replace RBAC and visibility checks.

Evidence also does not impose unconditional generated-plan task cardinality. Use [planning-task-constraints.md](planning-task-constraints.md) for explicit per-child minimums and maximums; those constraints do not change evidence truth sets.

## Implementation and test anchors

- `YamlSkillCatalog.java` (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java`) validates placement, compiles expressions once, and validates exact direct children.
- `EvidenceExpressionParser.java` (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/runtime/evidence/EvidenceExpressionParser.java`) defines parsing, while `EvidenceExpression.java` (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/runtime/evidence/EvidenceExpression.java`) defines canonical rendering, evaluation, and structured unsatisfied requirements.
- `EvidenceCoverageValidator.java` (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/runtime/evidence/EvidenceCoverageValidator.java`) defines plan and final truth sets.
- `YamlSkillEvidencePropertyCatalogTest.java` (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/skill/YamlSkillEvidencePropertyCatalogTest.java`) and `YamlSkillEvidenceExpressionCatalogAdditionalTest.java` (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/skill/YamlSkillEvidenceExpressionCatalogAdditionalTest.java`) protect placement, scalar shape, exact identity, and diagnostics.
- `OutputSchemaCallAdvisorTest.java` (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/outputschema/OutputSchemaCallAdvisorTest.java`) and `StepPromptBuilderTest.java` (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/runtime/step/StepPromptBuilderTest.java`) protect metadata non-leakage.
- `NestedSuccessfulSkillBoundaryTest.java` (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/runtime/tool/NestedSuccessfulSkillBoundaryTest.java`) protects nested boundary credit.
