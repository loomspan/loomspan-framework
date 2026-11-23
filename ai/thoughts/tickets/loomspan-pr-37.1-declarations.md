# PR 37.1 — Register callable skills and executors by portable identity

## Outcome

Give every declaration an owning namespace and a local name. Use that ordered pair throughout registration so two applications can declare the same local name.

## Requirements

- For both components, validate the entire supplied string against `[a-z][a-z0-9]*(?:-[a-z0-9]+)*`, with 1–64 ASCII characters. Reject either component containing `anthropic` or `claude`. Require `namespace.length + 2 + localName.length <= 64` for callable skills and reference-only executors alike.
- Startup YAML belongs to `default` regardless of subdirectory. Supplied YAML belongs to the namespace passed to the reloader. Both Java annotations take their declared namespace, defaulting to `default` when omitted. For both annotations, use an explicit nonempty `name` verbatim, otherwise use the Java method name verbatim.
- Keep separate callable and executor indexes. Enforce uniqueness in each namespace across YAML and Java callables, and independently among executor registrations. Update declaration examples, annotation documentation, fixtures and packaged authoring guidance.

## Acceptance criteria

- [x] Both declaration paths for YAML and both Java annotations assign the specified owner and reject invalid spellings without normalization. Java startup errors identify the offending method and explain how to provide a valid annotation name or rename the method.
- [x] `lookup-3pl` is valid. Initial digits, case changes, whitespace, invalid separators and reserved substrings fail. In `order-support` a 49-character valid local name succeeds and a 50-character one fails for either registration kind.
- [x] Duplicate callable or duplicate executor pairs fail within their respective index. Distinct owners and one callable plus one executor sharing a pair succeed. Declaration examples match these rules.

## Context

First ticket in the local PR 37 series. PR 37.2 owns path casing, PR 37.3 owns authored resolution and PR 37.4 owns public invocation and catalog access. See the [portable naming proposal](../future/portable-skill-naming.md).

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Registration changes startup, publication and the identities shared by downstream consumers.
- **Reassessment triggers:** None.

## Pipeline notes

PR 37 is a destructive replacement across undeployed products. Remove superseded naming behavior without aliases, shims or migration instructions. Write current-state documentation. Intermediate commits stay local and may break later consumers. Record deferred checks for PR 37.8 and verify this ticket's behavior as far as the local build allows.

## Completion record

Step 4 implemented the portable declaration grammar, exact namespace validation, two-separator pair limit, colon-qualified identity codec and actionable annotation diagnostics. Existing configured/supplied owners and separate callable/executor indexes provide the required registration behavior. The codec change is the registration prerequisite described in the implementation plan. The supported API allowlist and annotation signatures remain closed.

### Verification

The minimal red command was run before production edits and failed in both selected methods as expected. Hyphenated ownership was rejected and namespace case was accepted.

```powershell
.\mvnw.cmd test '-Dtest=SkillIdentityTest#acceptsPortableHyphenatedPair+rejectsNamespaceCaseChanges' '-DfailIfNoTests=false'
```

The declaration acceptance command passed 136 tests with zero failures or errors. It compiles production and every test source and includes `LoomspanPublicSurfaceArchitectureTest`, public annotation tests, actual startup/public-reloader validation and preparation, 49/50 boundaries, independent indexes, proxy discovery and executor configuration fidelity.

```powershell
.\mvnw.cmd test '-Dtest=SkillIdentityTest,SkillMethodBeanPostProcessorTest,PortableDeclarationIdentityIntegrationTest,ExecutorConfigurationTests,ExecutorConfigurationManagerTests,ExecutorConfigurationStartupTests,SkillMethodTest,SkillExecutorTest,LoomspanPublicSurfaceArchitectureTest,SkillMethodTargetDiscoveryIntegrationTests,SharedJavaDiscoveryInvocationTest,YamlSkillCatalogTests#acceptsProviderPortablePublicSkillNames+rejectsNonPortablePublicSkillNames+keepsMissingAndBlankNamesOnRequiredFieldPath+suppliedDocumentsValidateIdentityAndReuseManifestRules' '-DfailIfNoTests=false' '-DargLine=-javaagent:C:/Users/mgiacomi/.m2/repository/org/mockito/mockito-core/5.24.0/mockito-core-5.24.0.jar'
```

The final diagnostic punctuation edit was followed by a passing 30-test processor/architecture run (`target/pr371-final-diagnostics.log`).

```powershell
.\mvnw.cmd test '-Dtest=SkillMethodBeanPostProcessorTest,LoomspanPublicSurfaceArchitectureTest' '-DfailIfNoTests=false' '-DargLine=-javaagent:C:/Users/mgiacomi/.m2/repository/org/mockito/mockito-core/5.24.0/mockito-core-5.24.0.jar'
```

The first focused run without an explicit agent was interrupted after Mockito self-attachment reported a denied Windows attach pipe. The command-local cached Mockito 5.24.0 agent resolves that environment issue. The initial focused agent run passed 97 tests. Local logs are under `target/pr371-acceptance.log` and `target/pr371-focused-agent.log`.

`git diff --check` passes. README retains LF line endings. Source and guide comparison is aligned for the declaration contract. A resource diff audit found no prompt, operation, configuration or business-data edits. Converted fixture fields are declaration names and structured references to those renamed declarations, with naming-test descriptions adjusted to their actual cases.

### Declaration documentation inventory

Reviewed and updated `README.md`, `docs/quickstart-java.md`, `docs/quickstart-executors.md`, annotation Javadocs, and the packaged guide topics `java-api/java-skills.md`, `skill-authoring/mental-model.md`, `authorization.md`, `input-contracts.md`, `output-contracts.md`, `evidence-contracts.md`, `typesafe-skills.md`, `executor-skills.md` and `planning-task-constraints.md`. The skill-authoring README routes the exact shared identity rules and cites focused declaration evidence. The packaged `SKILL.md` manifest names already satisfy the component grammar. Method names and business fields such as `repeatContactProbability` and executor `operation` data retain their authored spelling.

### Deferred checks and series ownership

The final broader run executed 201 tests with 19 failures and one error, all in the exact cases listed below. Its log is `target/pr371-residual-final.log`. The broader command deliberately exposes remaining authored-resolution work assigned to PR 37.3. Run it after converting those fields and repeat it in PR 37.8.

```powershell
.\mvnw.cmd test '-Dtest=YamlSkillCatalogTests,ExecutorDeclarationTests' '-DfailIfNoTests=false' '-DargLine=-javaagent:C:/Users/mgiacomi/.m2/repository/org/mockito/mockito-core/5.24.0/mockito-core-5.24.0.jar'
```

- `ExecutorDeclarationTests#executorLeafHasExplicitFormAndImmutableData` still expects the former foreign executor spelling `infrastructure-sql`. The source fixture now resolves it as the local pair `default:infrastructure-sql`. PR 37.3 converts the authored target to `infrastructure:sql` while preserving its original meaning.
- `ExecutorDeclarationTests#restIsUnsupportedAndExecutorReferencesAreStrict` still treats a single-hyphen local reference as malformed and expects the former executor rendering. PR 37.3 updates the authored-reference grammar cases.
- `YamlSkillCatalogTests#rejectsInvalidStructuredAllowedSkillEntries` retains old child spellings or obsolete malformed-reference expectations in dynamic cases `[3]`, `[7]` and `[9]` through `[18]`. `loadsStructuredAllowedSkillEntryWithRequiredMinimum` retains `default-requiredChild`, `default-boundedChild` and `default-optionalChild`. PR 37.3 owns their target-preserving conversion.
- The same class's `failsStartupWhenEvidenceContractContainsBlankExpression`, `loadsEvidenceContractWhenManifestDeclaresOne`, `rejectsListValuedEvidenceExpressionWithoutStringCoercion`, `rejectsWrongCaseDirectChildWithSuggestion` and `usesExactValidatedPublicNameInEvidenceExpression` retain old child/evidence spellings. PR 37.3 owns conversion, parser behavior and diagnostic expectation updates.
- `NamespaceIdentityFoundationTest` remains an integrated authored/public-catalog target for PR 37.3 and PR 37.4. `NamespaceStartupIntegrationTest` and `SupportedSurfaceIntegrationTest` need the explicit public invocation/catalog shapes under PR 37.4 and final provider dispatch under PR 37.5. They were not run as declaration acceptance checks.
- `NamespaceObservationIntegrationTest`, `ConsoleTraceFixtureCorpusTest` and consumers of the renamed `observability-test/check-dns.yaml` fixture need coordinated identity conversion under PR 37.6. They were not run here.
- PR 37.8 must rerun the complete Framework and Console build/suites, close all checks above and reconcile the later documentation audit. Provider/worker spelling, Console serialization and full-series assurance remain assigned to their explicit tickets.

No optional developer checks or unresolved product decisions were identified. The developer authorized all breaking replacements without shims and approved the selected profile. The sibling Sidecar handoff remains uncommitted when PR 37.8 updates it. This record precedes independent Step 5 certification.