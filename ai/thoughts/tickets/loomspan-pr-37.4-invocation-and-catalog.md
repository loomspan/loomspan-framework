# PR 37.4 — Expose explicit identities at Framework API boundaries

## Outcome

Let an embedded caller take identity components from validation or a catalog and invoke precisely that skill.

## Requirements

- Require the namespace and local name separately at every public root invocation entry point and carry that exact pair through admission and execution. Remove overloads that take an encoded target or infer a namespace. Validate names at lookup and invocation using the declaration rules.
- A catalog snapshot selects one explicit namespace. Its `skill(...)` accepts only a local name. `ValidatedSkill` and `SkillDescriptor` both expose `namespace()`, `localName()` and `qualifiedName()`. Review the closed API allowlist, run `LoomspanPublicSurfaceArchitectureTest` after production changes, and document the supported signatures in the README, Java API docs, packaged references and embedded examples.

## Acceptance criteria

- [x] A validated and a catalogued `orders:lookup` both yield `orders`, `lookup` and `orders:lookup` from the three accessors. The components can be passed directly to the public invocation API.
- [x] `snapshot("orders").skill("lookup")` selects that pair. Passing `orders:lookup` as the local argument fails validation. Tests exercise every public lookup and invocation shape, including malformed components, missing targets and equal local names under different owners.
- [x] Admission executes the exact requested pair under existing access checks. The supported surface and its examples expose only the explicit contract.

## Context

Follows PR 37.3 in the local PR 37 series. PR 37.5 owns model dispatch. Sidecar clients will be updated separately. See the [portable naming proposal](../future/portable-skill-naming.md).

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** The supported Java API and root admission contract change destructively.
- **Reassessment triggers:** None.

## Pipeline notes

PR 37 is a destructive replacement across undeployed products. Remove superseded naming behavior without aliases, shims or migration instructions. Write current-state documentation. Intermediate commits stay local and may break later consumers. Record deferred checks for PR 37.8 and verify this ticket's behavior as far as the local build allows.


## Completion record

### Execution and scope

Selected profile was full with the developer's advance approval. PR 37.1 through
PR 37.3 were committed before this ticket began. The checkout's only initial
changes were the untracked proposal, future tickets and this ticket's research and
planning artifacts. Those later inputs remain outside this implementation.
The developer authorizes breaking changes without compatibility shims and
current-state product guidance. The orchestrator owns the local commit and fresh
independent review. The later Sidecar handoff is to remain uncommitted.

The six `SkillTemplate` operations and two `SkillInvocationHandoff` operations
require namespace and local name separately. Preparation constructs one validated
`SkillIdentity`, then captures the exact entry and reachable generations through
the existing registry. Prepared validation/execution and handoff diagnostics use
captured metadata. The admitted payload no longer duplicates the caller's target
string. Authorization, object conversion order, Map/Object null rules, ownership,
observers, retained authentication and cleanup follow the existing captured flow.

`SkillDescriptor` has components `(namespace, localName, description, kind,
inputSchema, outputSchema)`. `ValidatedSkill` has components `(namespace,
localName, kind)`. Their sole constructors validate exact identity pairs and
`qualifiedName()` derives the colon display. Both `name()` accessors and encoded
constructors are removed. The closed API type allowlist needs no additional type.
`DefaultSkillCatalog.skill(localName)` validates using its selected namespace,
including combined length on a miss, and performs one exact lookup.

All direct Java root callers and public metadata consumers were updated so the
entire checkout compiles. The required acceptance fixtures also replace stale
canonical strings and invalid camelCase names with their exact portable target.
This includes shared TestSkillGenerations leaf metadata, manager/admission
fixtures, provider-free foundation declarations, supplied-leaf publication and
the `dept` annotation in the shared startup fixture. Catalog callers use local
names while independent internal registered-catalog lookups keep qualified keys.
Raw provider requests/responses, tool arguments, business data and diagnostic
payloads retain their content. Provider/worker/Console protocol conversions remain
with their owning tickets.

### Acceptance evidence

| Criterion | Implemented and verified evidence |
| --- | --- |
| Reusable namespace, local name and qualified name, including orders:lookup | ApplicationApiValueTest asserts exact record shapes, constructors, removed name accessors, null/grammar/length behavior and all root signatures. PortableDeclarationIdentityIntegrationTest#validatedAndCataloguedComponentsSelectTheSameRealInvocation validates, publishes and executes both owners using returned components through real facade/handoff and Java/executor invocation. |
| Explicit namespace catalog with local-only input | DefaultSkillCatalogTest covers equal lookup locals in orders/warehouse, warehouse-lookup as a local, missing local without foreign fallback, immutable sorted unfiltered schemas, malformed/qualified inputs, exact spelling and the 49/50 combined boundary. Active and prepared public catalogs are also exercised in real Boot composition. |
| Exact root admission and existing access behavior | SkillReachableAdmissionTest exercises all eight operations, equal locals in three owners, hyphenated local/foreign ambiguity, missing-local termination, malformed components, absent namespaces without creation, combined limits, exact-owner RBAC, nested preparation, concurrency and retained work. DefaultSkillTemplateTest and SkillGenerationExecutionIntegrationTest protect input fidelity, capture-before-conversion, retained generation, observer/failure/null behavior and authentication. SkillAdmissionSnapshotTest and SkillAdmissionContractsTest protect the existing reachable association and dependency checks. |
| Supported surface and examples | LoomspanPublicSurfaceArchitectureTest passes after the final production edit. Both provider-free SupportedSurfaceIntegrationTest methods, three public reload methods, full reloader/manager/executor-generation suites and explicit whole test compilation pass. README, API JavaDoc, quickstarts and packaged Java/authoring references use the final signatures. |

### Documentation and fidelity audit

Reviewed and updated the README, `docs/quickstart-java.md`,
`docs/quickstart-executors.md`, API JavaDoc for all changed types, and the packaged
Java API invocation, catalog/validation, Java declarations, reload, compatibility
and index guides. The authoring validation workflow, input contracts, mental
model, design checklist and coverage table now distinguish explicit root
components, scoped local lookup and authored colon references. The catalog guide
also describes executor YAML's actual effective output schema, verified by the
manager/dependency/catalog suites. The guide/source comparison is aligned for
these boundaries. Earlier default-inference/case-folding/name-accessor advice was
documentation drift resolved in these topics.

Semantic searches reviewed remaining root calls and catalog inputs. The changed
Java source contains no encoded-target public overload or inferred namespace.
The README has zero CRLF sequences. Source and test diff review preserved raw
provider bodies and ordinary business/diagnostic content outside identity fields.
The remaining generated-plan examples and Console trace labels retain their
assigned PR 37.5/37.6 audit scope.

### Verification

The minimal reflection test was run before production changes and produced the
expected red result, `NoSuchMethodException` for
`SkillTemplate.validate(String, String, Object)`.

```powershell
.\mvnw.cmd test '-Dtest=ApplicationApiValueTest#rootOperationsRequireExplicitIdentityComponents' '-DfailIfNoTests=false' '-DargLine=-javaagent:C:/Users/mgiacomi/.m2/repository/org/mockito/mockito-core/5.24.0/mockito-core-5.24.0.jar'
```

The complete acceptance command passed **141 tests with zero failures, errors or
skips** on the final API/admission implementation. Log is
`target/pr374-gate3.log`.

```powershell
.\mvnw.cmd test '-Dtest=ApplicationApiValueTest,DefaultSkillCatalogTest,DefaultSkillTemplateTest,SkillGenerationExecutionIntegrationTest,SkillReachableAdmissionTest,SkillAdmissionSnapshotTest,SkillAdmissionContractsTest,SkillGenerationManagerTest,SkillReloaderTest,ExecutorGenerationExecutionTest,NamespaceIdentityFoundationTest,PortableDeclarationIdentityIntegrationTest,LoomspanPublicSurfaceArchitectureTest,SupportedSurfaceIntegrationTest#javaFoundationUsesOnlySupportedTypesAndKeepsExecutorsReferenceOnly+completeNamespaceManagementUsesOnlySupportedTypes,PublicSkillReloadIntegrationTest#preparesSuppliedYamlWithoutFilesThroughSupportedApis+publicValidationRereadsConfiguredResourcesAndKeepsSuppliedInputIsolated+retirementWaitsForPendingOldGenerationThenCleansUpAfterRelease' '-DfailIfNoTests=false' '-DargLine=-javaagent:C:/Users/mgiacomi/.m2/repository/org/mockito/mockito-core/5.24.0/mockito-core-5.24.0.jar'
```

Every checked-in test source compiles. Log is
`target/pr374-test-compile-final.log`.

```powershell
.\mvnw.cmd test-compile '-DskipTests'
```

Supplemental verification of all direct catalog/dependency callers and the
packaged authoring example passed **8 tests with zero failures, errors or skips**.
Log is `target/pr374-catalog-callers-final.log`.

```powershell
.\mvnw.cmd test '-Dtest=SkillDependencyValidatorTest,DeclaredChildInputBindingsIntegrationTest#completeAuthoringExampleLoadsAsOneValidatedGeneration' '-DfailIfNoTests=false' '-DargLine=-javaagent:C:/Users/mgiacomi/.m2/repository/org/mockito/mockito-core/5.24.0/mockito-core-5.24.0.jar'
```

The following check passed after the final implementation and documentation edits.

```powershell
git -c core.safecrlf=false diff --check
```

Mockito's cached 5.24.0 Java agent is a command-local host workaround. No build
configuration was added for it. Earlier acceptance attempts exposed fixture
residue and an implementation helper signature error, which were corrected
before the passing final runs. No API-boundary acceptance check remains open.

### Reconciliation and remaining integrated checks

PR 37.3's deferred API/admission obligation is closed by the exact public shape,
record producers, catalog lookups, real metadata reuse and full admission tests
above. Public reload preparation, configured validation and retirement coverage
all pass through the replacement public signatures. The eight supplemental
checks keep the previously verified authored/dependency/catalog example coherent.

The following later checks were **not run** in this ticket. This records scope
and outstanding verification, not a claim that they pass or fail.

| Check | Remaining boundary and owner |
| --- | --- |
| SupportedSurfaceIntegrationTest#invokesYamlPlannerAndBothLeafKindsThroughSupportedSurface | Raw generated plans and CALL_TOOL payloads still need the PR 37.5 colon-worker/provider dispatch conversion. The public callers compile with separate components. |
| NamespaceExecutionAuthorityIntegrationTest#plannedAssignedAndAutomaticCallsUseCanonicalForeignIdentity and #parallelForeignBranchesIsolateCallerContextsOnSuccessAndFailure | Assigned worker/provider fixtures and canonical consumer assertions remain in PR 37.5, with final integrated assurance in PR 37.8. |
| PublicSkillReloadIntegrationTest#invokesSuppliedModelSkillThroughSupportedApi, #pendingRootKeepsItsConnectionAcrossPublication, #preparesStagesAndPublishesThroughSupportedBeans | Mixed model/protocol fixture paths compile. Their full model-driven behavior and remaining historical declaration/diagnostic names are verified during PR 37.5/37.8. |
| NamespaceStartupIntegrationTest#suppliedLocalDocumentsValidateAndPrepareInEachOwnerBeforePublicationWithoutFallback and #embeddedSameNamespaceStartupCompositionIsValidAndForeignPrivateStartupFails | Provider-returned function names and route assertions retain old protocol spelling for PR 37.5. The shared fixed Java owner is now valid and provider-free foundation tests pass. |
| NamespaceObservationIntegrationTest#hostRootAndEntryNamespaceOwnCrossNamespaceTrace and #capturedMapAndTargetOwnerSurviveRemovalAndRecreation, ConsoleRestFixtureCorpusTest and ConsoleTraceFixtureCorpusTest | Coordinated Java/Go/UI identity DTOs, trace labels and fixture corpus are PR 37.6, with combined verification in PR 37.8. |
| ExecutorGenerationExecutionIntegrationTest#workedVariableCorpusReachesProviderAndActualExecutor | The worked read_record declaration/call and remaining executor integration identities require integrated fixture reconciliation in PR 37.8. Provider payloads remain exact pending PR 37.5. |
| Complete Framework and Console builds and remaining compiled integration classes | PR 37.8 closes all prior deferred checks, tests and documentation inventory together. |

Optional developer checks are none.
