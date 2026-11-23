# PR 37.2 — Validate the spelling of startup skill paths

## Outcome

Make Framework discovery report a mis-cased definition path consistently across case-sensitive and case-insensitive resource locations.

## Requirements

- For discovery patterns inspect actual directory and filename spelling beneath the fixed prefix. Catch case variants of matching candidates, including the extension. For a literal location the checked relative path is the filename. Check caller-supplied relative spelling before lookup, and actual discovery spelling when enumerating resources.
- Keep path validation at the loader. In `SkillReloader.validate(...)` and `prepare(...)`, validate the supplied identity and preserve `SkillDocument.sourceName` verbatim as a diagnostic label. Document that distinction in startup and reload examples. Cloud archive and Sidecar HTTP loading belong to their respective projects.

## Acceptance criteria

- [x] With `classpath:/skills/**/*.yaml`, both `Orders/lookup.yaml` and `orders/lookup.YAML` produce errors identifying the relative path and offending component, including on a host where the glob would normally miss the candidate.
- [x] The literal `classpath:/skills/Orders/lookup.yaml` passes the case check, while a literal filename with uppercase spelling fails. Nested lowercase definitions still belong to `default`.
- [x] Supplied YAML works with diagnostic labels `Orders import #42` and `Orders/lookup.yaml`. Startup and reload documentation illustrate the implemented behavior.

## Context

Follows PR 37.1 in the local PR 37 series. PR 37.1 owns declaration grammar. PR 37.3 owns references inside the located documents. See the [portable naming proposal](../future/portable-skill-naming.md).

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Pattern enumeration and literal lookup require discovery across supported resource types and host casing behavior.
- **Reassessment triggers:** None.

## Pipeline notes

PR 37 is a destructive replacement across undeployed products. Remove superseded naming behavior without aliases, shims or migration instructions. Write current-state documentation. Intermediate commits stay local and may break later consumers. Record deferred checks for PR 37.8 and verify this ticket's behavior as far as the local build allows.

## Completion record

### Implementation

- `YamlSkillCatalog` configures its privately supplied Spring resolver for case-insensitive candidate matching. It validates caller relative spelling before lookup and actual candidate spelling before YAML reads. Missing roots, extension selection, resource order and custom classloader delegation retain focused coverage.
- `SkillSourcePathResolver` now lives in `internal.skill` and serves loading and observation. Literals check their filename. Patterns check beneath the fixed prefix. File-backed resources obtain actual spelling with `toRealPath(NOFOLLOW_LINKS)` and URI escapes are decoded for component validation. Classpath caller strings retain literal percent characters.
- Supplied labels remain exact. Public validation/preparation tests cover both requested labels with normal Environment and explicit empty-map preparation, invalid identity feedback, and retained source labels/bytes.
- Observation test declarations `Alpha` and `FetchDns` were invalid under PR 37.1. The focused source-path regression fixture now uses valid lowercase declaration names and matching exact assertions. Its Console identity representation remains assigned to PR 37.6.
- Public API signature delta is zero. The internal source-path helper moved with its architecture classification. No Spring extension point or bean was introduced.

### Acceptance evidence

| Criterion | Executable evidence |
| --- | --- |
| Pattern candidates with uppercase directories and extensions reject before parsing | `StartupSkillPathTest#rejectsActualFilesystemSpelling`, `#validatesExplodedClasspathCandidates`, `#validatesPackagedJarEntrySpelling` and `#rejectsUppercaseExtensionOmittedByConfiguredPattern` use real filesystem, classpath/classpath* and temporary-JAR resources. Invalid candidate bodies demonstrate path rejection precedes parsing. |
| Caller spelling, literal scope and startup owner | `StartupSkillPathTest#validatesCallerRelativeSpellingBeforeLookup` asserts zero resolver calls. `#literalLocationsCheckOnlyFilename` covers the exact requested classpath literal. `#literalLookupChecksActualFilenameOnCaseInsensitiveFilesystem` passed on this Windows host. `PortableDeclarationIdentityIntegrationTest#nestedStartupYamlAlwaysBelongsToDefault` covers actual startup ownership. |
| Supplied exact labels | `PortableDeclarationIdentityIntegrationTest#suppliedLabelsRemainExactThroughValidationAndPreparation` covers both public routes and variable-source modes. `StartupSkillPathTest#suppliedDocumentSourceRetainsLabelAndBytes` and `SkillSourcePathResolverTest` protect exact metadata/bytes independently of path checks. |
| Existing discovery and identity behavior | Selected `YamlSkillCatalogTests` protect missing roots/no matches, deterministic recursive discovery and supplied feedback. The complete declaration gate and `LoomspanPublicSurfaceArchitectureTest` pass. |

URI-encoding cases cover lowercase Unicode filenames, outer archive paths with spaces, and literal percent characters in classpath filenames. Temporary-JAR resolvers disable URL caching only in tests so Windows cleanup can close their archives.

### Verification

Final Step 4 command, executed after the final production and test changes.

```powershell
.\mvnw.cmd test '-Dtest=StartupSkillPathTest,SkillSourcePathResolverTest,PortableDeclarationIdentityIntegrationTest,DefaultRegisteredSkillCatalogTest,SkillIdentityTest,SkillMethodBeanPostProcessorTest,ExecutorConfigurationTests,ExecutorConfigurationManagerTests,ExecutorConfigurationStartupTests,SkillMethodTest,SkillExecutorTest,SkillMethodTargetDiscoveryIntegrationTests,SharedJavaDiscoveryInvocationTest,LoomspanPublicSurfaceArchitectureTest,YamlSkillCatalogTests#loadsYamlSkillsFromClasspathSkillsPattern+loadsNoSkillsWhenConfiguredClasspathRootIsMissing+loadsNoSkillsWhenClasspathRootExistsButHasNoYamlMatches+validationReportsSuppliedEntryProblemsWithoutResolvingLabels+suppliedDocumentsValidateIdentityAndReuseManifestRules+acceptsProviderPortablePublicSkillNames+rejectsNonPortablePublicSkillNames+keepsMissingAndBlankNamesOnRequiredFieldPath' '-DfailIfNoTests=false' '-DargLine=-javaagent:C:/Users/mgiacomi/.m2/repository/org/mockito/mockito-core/5.24.0/mockito-core-5.24.0.jar'
```

PASS — 184 tests, zero failures/errors, one capability skip. Log `target/pr372-combined-final.log`. The conditional `literalSymlinkKeepsItsLogicalFilename` test skipped because this sandbox cannot create symbolic links. Actual filename casing and all required literal/pattern checks ran successfully. No required ticket check remains open.

```powershell
.\mvnw.cmd test '-Dtest=StartupSkillPathTest#rejectsUppercaseExtensionOmittedByConfiguredPattern' '-DfailIfNoTests=false' '-DargLine=-javaagent:C:/Users/mgiacomi/.m2/repository/org/mockito/mockito-core/5.24.0/mockito-core-5.24.0.jar'
```

Expected FAIL before production changes — one test failed because the baseline omitted the uppercase-extension candidate. Log `target/pr372-red.log`. The same method passes in the final combined run. Earlier focused path gates passed 57 and 59 tests with the same single capability skip, and the declaration regression passed 131 tests. The final command supersedes that intermediate evidence.

```powershell
git -c core.safecrlf=false diff --check
```

PASS after implementation and documentation updates. Java compilation is part of the Maven test command. Mockito uses the previously verified command-local agent because this Windows sandbox denies self-attachment.

### Documentation audit

Updated the root README, Java quickstart, `SkillDocument`/`SkillReloader` Javadocs, and the packaged mental-model, validation-workflow and Java skill-reload topics. Both knowledge-base coverage indexes cite focused evidence. The executor quickstart was reviewed and has no affected startup path assertion. Documentation describes the current path/label boundary with no migration narrative.

Ownership, extension selection and exact supplied labels are **aligned** with executable behavior. The nearby namespace-normalization/32-character statement in the publication topic was **documentation drift** relative to PR 37.1 and now describes exact components and the pair limit. Existing authored-reference prose and tests retain their PR 37.3/37.7 assignments.

### Deferred integration

The 20 authored-reference residual cases listed in PR 37.1 remain assigned to PR 37.3 and final PR 37.8 closure. They were not rerun because this change's selected declaration/path gates provide the required local evidence. Full Framework and Console builds remain PR 37.8 gates. The symbolic-link test runs automatically where the host grants creation capability. There are no optional developer decisions needed for this ticket.
