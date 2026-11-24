# PR 37.3 — Resolve authored references in their owning namespace

## Outcome

Make a reference in an authored skill select exactly one target, independent of the other applications installed.

## Requirements

- Resolve a bare local name in the containing skill's namespace and an explicit `namespace:local-name` in the specified namespace. Validate each component and the pair limit from PR 37.1. Apply existing visibility and authorization checks. End lookup on a miss.
- Apply this rule to allowed children, executor references, bindings, output forwarding and evidence expressions. Each field selects only its corresponding callable or executor index. Bare `and` and `or` in evidence expressions are operators.
- Convert authored dependency fields in definitions, examples, packaged skills and fixtures by interpreting their original target before rewriting. Preserve unrelated prose and data. Update the authoring references and examples for each field.

## Acceptance criteria

- [x] In `orders`, bare `warehouse-lookup` selects `orders:warehouse-lookup` and explicit `warehouse:lookup` selects the foreign callable. Removing the local target makes the bare reference fail even with the foreign target present. Converted examples preserve their intended target.
- [x] Resolution, exact grammar, lookup failure and access checks hold across all listed fields. A child and executor field with the same pair can select distinct registrations.
- [x] A parser test in `orders` shows `orders:and or lookup` is a disjunction of exactly `orders:and` and `orders:lookup`. Qualified operator names also work for `or`.

## Context

Follows PR 37.2 in the local PR 37 series. PR 37.4 owns public lookup and invocation. PR 37.5 owns model dispatch. See the [portable naming proposal](../future/portable-skill-naming.md).

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Authored expressions and authorization-sensitive resolution span several runtime consumers.
- **Reassessment triggers:** None.

## Pipeline notes

PR 37 is a destructive replacement across undeployed products. Remove superseded naming behavior without aliases, shims or migration instructions. Write current-state documentation. Intermediate commits stay local and may break later consumers. Record deferred checks for PR 37.8 and verify this ticket's behavior as far as the local build allows.


## Completion record

Step 4 completed on 2026-11-23 under the Full 5-Step Pipeline. Independent Step 5 review remains the pipeline completion gate. The developer authorized intentional breaking replacement without shims and current-state consumer documentation. The orchestrator owns the local commit after review.

### Implementation and acceptance evidence

The existing `SkillIdentity.resolve` and exact callable/executor maps provide owner-relative resolution. Production changes remove the unreachable case-insensitive evidence suggestion scan in `YamlSkillCatalog.compileEvidenceContract`. Invalid identity spelling fails before membership lookup. No public type/signature, Spring extension surface, serialized schema or provider rewrite was added.

| Criterion | Executable evidence |
| --- | --- |
| Ambiguous local and explicit foreign callable targets, exact missing-local failure | `SkillDependencyValidatorTest#ambiguousBareChildrenKeepTheirOwnerAndMissingLocalTargetsEndLookup` publishes `warehouse:lookup` and `orders:warehouse-lookup`, checks distinct forwarded schemas and capture, omits the local declaration and verifies the bare miss while the explicit foreign reference remains valid. |
| Every authored field selects its exact identity | `NamespaceIdentityFoundationTest#localAndQualifiedFieldsSelectDistinctIdentities` covers allowed children, executor, input producer, output producer, forwarding and evidence. `bindingsForwardingAndEvidenceCannotMatchForeignOnlyChildren` exercises exact direct membership, including the same input-binding validator used by dependency admission. |
| Exact grammar and pair limit | Foundation malformed-component matrix, six-field 49/50-character local boundaries under `order-support`, explicit foreign owner limits, source labels, field paths and original evidence columns. Evidence whitespace between tokens remains grammar whitespace. |
| Separate callable/executor indexes and no wrong-role fallback | `ExecutorDependencyResolutionTest#ambiguousExecutorNamesSelectExactOwnersAndCallableRoles` checks distinct captured owner configuration/generation, a callable and executor sharing `orders:warehouse-lookup`, a foreign-only executor miss and a callable-only target rejected as an executor. Existing role-separation tests reject executor-only callable children. |
| Access checks on selected targets | Full `NamespaceAccessGuardTest`, `SkillRoleEvaluatorTest`, `DefaultAccessGuardTest` and executor dependency suites pass, retaining Private/Public, direct-child, callback, role conjunction and captured policy checks. |
| Qualified operator names | `EvidenceExpressionParserTest#qualifiedOperatorNamesRemainLeavesInOwningNamespace` asserts exact two-leaf disjunction for `orders:and or lookup`, conjunction for `orders:or and lookup`, qualified alternatives, canonical text, truth sets and original columns. Whole-token operator casing/precedence remains covered. |
| Source and result fidelity | `NamespaceIdentityFoundationTest#sourcePromptSchemaAndRawEvidenceRetainAuthoredContent` checks exact UTF-8 source bytes, prompt, description, schema data and raw evidence text against distinct prepared identities. `ChildInputBindingAssemblerTest#foreignResultCannotSatisfyAnOwnerLocalProducer` rejects the foreign result and records the selected local producer in provenance. Existing immutable config, exact claim/property, null, detached value, mission and task isolation tests pass. |
| Published example | `DeclaredChildInputBindingsIntegrationTest#completeAuthoringExampleLoadsAsOneValidatedGeneration` loads all three actual YAML blocks from the packaged input-binding topic through startup and checks their contracts. |

The final acceptance gate passed **443 tests**, zero failures/errors/skips. It includes the complete `YamlSkillCatalogTests` and `ExecutorDeclarationTests` classes with **201 passing tests**, reconciling all 20 deferred PR 37.1 cases. Required/min/max children, malformed structured references, evidence scalars, wrong-case identities and expression columns are verified in the complete classes. Full reload validation, exact management spelling and the shared authorization fixture consumers are included.

A final additional run passed **23 tests**, zero failures/errors/skips, after the last wrong-role executor assertion and exact result-provenance check. Production source passed `LoomspanPublicSurfaceArchitectureTest`. The resource diff audit inspected 39 YAML/YML files and found zero changed lines outside dependency fields and evidence identifiers. README retains LF. `git diff --check` passed.

### Target conversion inventory

Pre-series `c271b80` is the source of original qualified target meaning. The PR 37.1 declaration changes establish the current valid local spelling. Each converted fixture field follows its declaration or canonical typed target.

| Original field family | Target derivation and current representation |
| --- | --- |
| `executor: infrastructure-sql` | Original pair `(infrastructure, sql)` becomes `infrastructure:sql`. The red test observed `default:infrastructure-sql` before conversion, establishing the actual ambiguity. Immutable `Nil`, `List`, `Upper`, `upper`, `literal.dot`, configuration and transform data remain exact. |
| Default-owned evidence children such as `default-invoiceParser` and `default-expenseLookup` | Original default owner and corresponding portable local declarations produce `default:invoice-parser` and `default:expense-lookup` in allowed fields, compiled expectations and evidence identifier tokens. `vendorName`, `isDuplicate`, property casing, schemas and other data retain their meaning and bytes. |
| Required/min/max fixture children | Original `default-requiredChild`, `default-boundedChild`, `default-optionalChild` map to explicit default-owned portable local names. Invalid-reference fixtures now contain a malformed colon reference that reaches the same syntax boundary. |
| Namespace graph, executor, binding, plan and access fixtures | Known canonical identity arguments use a colon, with local declaration components updated where strict grammar requires it. Bare `child`/`run` retains the containing owner. Generation IDs, labels, configuration paths, operation names, business input and role data remain independent. |
| Connected integration YAML | Converted only structured authored fields and manifest-building helper reference arguments. Original provider response payloads, tool arguments and public root call strings remain assigned to the protocol/API tickets below. The original target, including foreign `tax`, `rates`, `shared`, `dept`, `dep` and executor owners, is explicit in the converted field. |
| README and authoring examples | `utility-echo` derives from the shown utility-owned Java method and becomes `utility:echo`. `default-finish`/binding producers use explicit default pairs. Foreign executor examples use `infrastructure:http`/`infrastructure:sql`. Existing legitimate local hyphens remain local. |

The shared `ExecutorPolicyFixture` now represents its in-memory content with an explicit supplied diagnostic label. Its former nonexistent `FileSystemResource("leaf.yaml")` could not supply actual filesystem spelling after PR 37.2. Both guard/evaluator consumers pass. The existing reloader class replaced obsolete namespace-normalization assertions with exact rejection checks and the 64-character component limit. These are test fixture adaptations within the approved exact naming contract.

### Documentation sets reviewed

| Set | Result and evidence |
| --- | --- |
| README deployment/forwarding/executor fragments | Updated authored fields and qualified declaration labels against actual method owners and exact compilation. Root public call examples remain assigned to PR 37.4. |
| Packaged mental model, input bindings, output contracts and executor topics | Current local/qualified grammar, direct producer/forwarding membership, role index and misses align with the foundation/dependency/assembler suites. Complete input-binding example is runnable startup evidence. Ordinary schemas, paths and prose retain authored content. |
| Packaged evidence and authorization topics | Exact identity spelling, qualified operator leaves, original text/columns and selected-target access claims align with parser/catalog/guard suites. Removed namespace-normalization and casing-suggestion claims. |
| Packaged planning constraints/concurrency, validation workflow and TypeSafe topic | Corrected shared identity explanation and explicit foreign executor/producer examples. Concurrency, provider-pack operations, configuration and runtime protocols retain their existing responsibilities. |
| Java declaration/publication topics, Java and executor quickstarts | Inspected their dependency fields. Local `executor: http`/`sql` already selects the containing owner. Final supported root/catalog signatures belong to PR 37.4. |
| Knowledge-base routing and coverage | Shared identity coverage row includes authored-field, ambiguity, index and qualified-operator evidence. The package version matches the checkout. Compared reference semantics are aligned after the documentation edits. |

### Verification commands and actual results

Expected red reproduction before conversion, **1 failure** with expected `infrastructure:sql` and actual `default:infrastructure-sql`, in `target/pr373-red.log`.

```powershell
.\mvnw.cmd test '-Dtest=ExecutorDeclarationTests#executorLeafHasExplicitFormAndImmutableData' '-DfailIfNoTests=false' '-DargLine=-javaagent:C:/Users/mgiacomi/.m2/repository/org/mockito/mockito-core/5.24.0/mockito-core-5.24.0.jar'
```

Final acceptance, **443 pass**, in `target/pr373-combined-final.log`.

```powershell
.\mvnw.cmd test '-Dtest=SkillIdentityTest,NamespaceIdentityFoundationTest,SkillDependencyValidatorTest,ExecutorDependencyResolutionTest,SkillDependencyBindingValidationTest,NamespaceAccessGuardTest,SkillRoleEvaluatorTest,DefaultAccessGuardTest,EvidenceExpressionParserTest,EvidenceExpressionParserAdditionalTest,EvidenceContractTests,EvidenceCoverageValidatorTest,EvidenceContractAdvisorAdditionalTest,YamlSkillCatalogTests,ExecutorDeclarationTests,YamlSkillEvidenceExpressionCatalogAdditionalTest,YamlSkillEvidencePropertyCatalogTest,ChildInputBindingTest,ChildInputBindingDeclarationsTest,ChildInputBindingProjectionTest,ChildInputBindingAssemblerTest,OutputBindingCompositionTest,OutputBindingIsolationTest,PlanInputBindingDependencyValidatorTest,PlanOutputBindingProducerValidatorTest,SkillReloaderTest,DeclaredChildInputBindingsIntegrationTest#completeAuthoringExampleLoadsAsOneValidatedGeneration,LoomspanPublicSurfaceArchitectureTest' '-DfailIfNoTests=false' '-DargLine=-javaagent:C:/Users/mgiacomi/.m2/repository/org/mockito/mockito-core/5.24.0/mockito-core-5.24.0.jar'
```

Final adjustments, **23 pass**, in `target/pr373-final-adjustments.log`.

```powershell
.\mvnw.cmd test '-Dtest=ExecutorDependencyResolutionTest,ChildInputBindingAssemblerTest,LoomspanPublicSurfaceArchitectureTest' '-DfailIfNoTests=false' '-DargLine=-javaagent:C:/Users/mgiacomi/.m2/repository/org/mockito/mockito-core/5.24.0/mockito-core-5.24.0.jar'
git -c core.safecrlf=false diff --check
```

The command-local Mockito agent is the existing Windows workaround. Maven configuration is unchanged. Intermediate fixture failures in `target/pr373-focused.log` and `target/pr373-combined.log` are closed by the passing final gate.

### Subsequent boundary verification

The following complete suites were **NOT RUN** in this ticket. Their authored YAML fields have target-preserving conversions, and final root/catalog/provider/Console assertions still belong to their specified ticket. These are concrete integration obligations for the series and closure in PR 37.8.

| Exact suites | Boundary and owner |
| --- | --- |
| `SkillAdmissionContractsTest`, `SkillAdmissionSnapshotTest`, `SkillReachableAdmissionTest`, `SkillGenerationManagerTest`, `ExecutorGenerationExecutionTest`, `PublicSkillReloadIntegrationTest` | Typed capture/catalog and public entry fixtures still contain prior canonical strings or inferred root lookup cases. PR 37.4 updates exact public shapes and current declaration consumers, with complete integrated verification in PR 37.8. |
| `DeclaredChildInputBindingsIntegrationTest` except its passing complete-example method, `DeclaredOutputBindingsOrdinaryIntegrationTest`, `DeclaredOutputBindingsPlanningIntegrationTest`, `BindingIsolationIntegrationTest`, `DesignatedChildResultIntegrationTest`, `ExecutorAccountingIntegrationTest`, `ExecutorApplicationIntegrationTest`, `ExecutorInputTransformIntegrationTest`, `ExecutorOutputSchemaIntegrationTest`, `ExecutorOutputTransformIntegrationTest`, `ExplicitJavaFailureHandlingIntegrationTest`, `JavaSkillIntegrationTest`, `PlannerEvidenceFlowIntegrationTest`, `NamespaceExecutionAuthorityIntegrationTest`, `SharedDeclarativeAuthorizationIntegrationTest` | Public root entry changes belong to PR 37.4. Provider function returns and worker structured actions belong to PR 37.5. Raw provider payloads are preserved here. These suites include remaining camelCase declarations/generated paths and provider canonical names to update with their consumers, closed in PR 37.8. |
| `NamespaceObservationIntegrationTest`, `ConsoleRestFixtureCorpusTest`, `DefaultRegisteredSkillCatalogTest` | Console-facing metadata, stored contracts and fixture generation belong to PR 37.6 after public entry and provider changes. Java/Go/UI final verification belongs to PR 37.8. |
| `StartupVariableResolutionIntegrationTest` and `loomspan-variable-fixtures/worked/loomspan/skills/read-record.yaml` consumers | The explicit foreign executor field is converted. Remaining invocation/registration fixtures and `read_record` declaration consumer are final integration work under PR 37.4/37.8. Configuration, `$var` markers and operation data retain authored content. |
| Complete Framework and Console builds | PR 37.8, after PR 37.4–37.7 complete their public, protocol and observation work. |

No required authored-reference check remains unresolved. No optional developer check was identified.
