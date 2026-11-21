# Namespace-owned consumer executor configuration Testing Plan

## Change Summary

- Add the supported immutable `consumerExecutorConfig()` handoff, selected independently from the executing leaf's captured namespace using the executor's existing selector.
- Preserve owner map selection, leaf config, input, owner-generation identity, authorization and complete target-only publication.
- Update Decisions TypeSafe policy: owner endpoint, consumer key when present, owner key on omission, reject consumer URL/unknown keys, keep leaf retries.
- Update Framework and pack guidance, including correcting existing TypeSafe leaf-retry documentation drift.

Controlling artifacts: `ai/thoughts/tickets/loomspan-pr-34-consumer-executor-config.md` and `2026-11-20-PR-34-consumer-executor-config.md` in this directory. Pipeline notes authorize coordinated direct updates without shims or version changes. This artifact designs tests; no test execution is claimed by planning.

## Impacted Areas

- Framework `SkillDependencyValidator`, `SkillAdmissionSnapshot.ExecutorBinding`, private invocation construction bridge, `ExecutionCoordinator`, supported v1 invocation and API expectations.
- Existing namespace configuration parser/loader and generation capture/publication are regression boundaries, not new mechanisms.
- Sibling `extensions/decisions` settings/transport/request validation, invocation test helpers and the installed-JAR `examples/support-triage` harness/publication tests.
- Framework README, routed executor/TypeSafe/Java/reload guidance and coverage indexes; pack executor/transport/recipe and tested-build documentation.

## Risk Assessment

- Choosing the root/parent namespace instead of the leaf namespace; leaking another consumer's map or an unselected subtree; automatically merging with input, leaf or owner data.
- Treating malformed present consumer data as omission; weakening mandatory owner lookup or exact selector grammar while adding optional lookup.
- Binding live configuration after admission, freezing preparation-time foreign data, changing owner ID meaning, or failing to retain the leaf generation through handoff/publication.
- Falling back from a present invalid consumer key, accepting a consumer URL, requiring an unused owner key, or selecting credentials again during retry.
- Shallow copying nested containers or losing null/non-string nested keys, literal dots/case, scalar types and identity-bearing leaf values.
- Existing visibility, composed YAML/Java authorization, proxy advice, lifecycle, transforms and diagnostics must retain current behavior. Avoid timing sleeps; use retained handoffs and bounded latches for concurrency.
- Supported API and documented configuration are protected. Internal constructors/helpers are updated directly, with no requirement that old signatures coexist. No SPI, persisted protocol, new diagnostic field or Java-to-Go boundary change is planned.

## Existing Test Coverage

- `ExecutorDependencyResolutionTest`: exact owner paths, malformed owner nodes, Public/Private role resolution, captured bindings, candidate overlay, reachability and capture races. Missing: optional consumer selection and separate leaf-owned maps.
- `ExecutorConfigurationTests`, `ExecutorConfigurationStartupTests`, `ExecutionConfigurationParserTest`, `ExecutorConfigurationManagerTests`: raw YAML fidelity, complete publication and foreign-entry rejection. Reuse these guarantees and extend only meaningful consumer-facing gaps.
- `SkillExecutorTest`, `ApplicationApiValueTest`: copied map/list containers, null/nested keys and Resource identity; exact invocation methods. Extend these fixtures with the fourth business map rather than invent another factory contract.
- `ExecutorGenerationExecutionIntegrationTest`: startup versus publication, actual public handoff, owner replacement/removal/recreation and retained auth/config/input. Missing: consumer-only publication and parent namespace differing from leaf.
- `NamespaceExecutionAuthorityIntegrationTest`, `SharedDeclarativeAuthorizationIntegrationTest`, `ExecutorApplicationIntegrationTest`, `ExecutorInputTransformIntegrationTest`, `ExecutorInvocationLifecycleIntegrationTest`, `ExecutorDiagnosticsIntegrationTest`: authority, actual advice, root/child execution, input separation, live lifecycle and current diagnostics.
- Pack `TypeSafeSettingsTest`, `TypeSafeTransportTest`, `TypeSafeRequestTest`: existing owner policy, verbatim headers, URL grammar, retries and HTTP capture. Missing: consumer policy.
- Example `PublicationConsumerTest`: owner rotation during actual retry; `TypeSafeConsumerTest`/`CompositionConsumerTest`: installed pack discovery, native/transformed and parent execution. Missing: foreign leaf-owned consumer rotation.

## Bug Reproduction / Failing Test First

This is an additive feature with a demonstrated missing path, not a pure refactor. First add a Framework test in `src/test/java/ai/loomspan/internal/skill/ExecutorDependencyResolutionTest.java` named along the lines of `foreignLeafSelectsIndependentOwnerAndConsumerSubtrees`. Publish Public `default-run` with selector `a.b` and owner `{gateway: owner}`; publish billing leaf referencing it with `{a: {b: {key: billing}}, sibling: ignored}`. Assert separate owner and consumer maps in its admitted binding.

Before adding the field/accessor this test is expected to fail compilation because the consumer binding/accessor does not exist; record that honestly as the pre-feature failure. After the minimal carrier/API compiles but before selection is implemented, run it again and require a value assertion failure (empty consumer map versus billing map). Then complete resolution and show green. Do not count a dependency/environment failure as red evidence and do not mutate the working tree back to an old revision to manufacture a red result. If implementing selection atomically makes the intermediate run impractical, preserve the initial compile-red result and establish missing selection from the existing owner-only resolver source in the implementation report.

Also add the pack's settings case for owner URL without owner key plus valid consumer key. Once updated signatures compile, it fails the old owner-only key policy and passes only with consumer selection. No provider network is needed for either red test.

## Tests to Add/Update

### 1) Exact optional consumer selection and owner regression

- **Type/location:** unit/component, `src/test/java/ai/loomspan/internal/skill/ExecutorDependencyResolutionTest.java`.
- **What it proves:** foreign leaves select the same exact dot path in owner and consumer, expose only selected subtrees, and keep independent maps. Table cases: missing first/final key yields empty; selected `{}` yields empty; no selector yields empty; same namespace yields empty consumer with owner data still selected. Case mismatch and a literal dotted root key do not match a dot path; they yield optional absence for consumer while owner mismatch still fails. Literal dots/case inside a selected map remain data.
- **Malformed cases:** consumer explicit null/scalar/list at final or intermediate segment fails metadata validation/preparation and yields an actionable namespace/path/executor failure; owner missing/null/scalar/list remains rejected. Invalid selector whitespace/empty segments remains rejected. Same-namespace consumer suppression must not bypass owner errors.
- **Fixtures/data:** existing manager/publish/admit helpers; nested `a.b` YAML with distinguishing owner/billing/support values, empty structures and unrelated sibling data.
- **Mocks:** only existing discovery/input test seams; never call an application executor for metadata checks. Assert no application calls during preparation/validation.
- **Affected surface:** Configuration/manifest behavior and Internal implementation.
- **Compatibility expectation:** preserve owner strictness and visibility; intentional binding constructor update without legacy signature.

### 2) Supported invocation map fidelity and surface

- **Type/location:** unit, existing `src/test/java/ai/loomspan/api/SkillExecutorTest.java`, `ApplicationApiValueTest.java`, and `src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java`.
- **What it proves:** exact invocation method set gains only `consumerExecutorConfig`; returns `Map<String,Object>` without internal/autoconfigure signature types; final class/no public constructor remain. Add consumer data to existing recursive defensive-copy matrix. Mutating source maps/lists after construction changes none of the four maps; returned nested maps/lists reject mutation. Preserve null values/null and non-string nested keys, exact case/dots, empty containers and scalar numeric kinds; Resources/arrays/application object leaves retain identity under existing rules. Distinguishing same-name entries in each map prove no merge or aliasing of mutable source containers. Recursive cycles remain rejected.
- **Fixtures/data:** existing mutable linked maps/lists, null-capable containers and Resource/object identity fixtures. Do not use `Map.of` for null fixtures.
- **Mocks:** existing diagnostics/lifecycle suppliers only; use updated internal factory directly, not a public construction shim.
- **Affected surface:** Application API.
- **Compatibility expectation:** preserve all existing methods/meanings; add the one deliberately supported method and retain closed type allowlist.

### 3) Direct and parent-mediated leaf ownership

- **Type/location:** Framework integration, extend `src/test/java/ai/loomspan/integration/ExecutorGenerationExecutionIntegrationTest.java` or a focused adjacent class included in run commands.
- **What it proves:** actual executor invocation receives owner URL, billing key and leaf retry/operation config independently. Two billing leaves share billing configuration; another namespace receives a distinct key. A support parent reaching a billing leaf receives billing, even when support has a deliberately different executor subtree. Direct and parent invocation yield identical leaf maps. Input is forwarded explicitly and remains unchanged by config selection; retain a Resource identity in a non-transformed direct path. Same-namespace execution still gets owner data and empty consumer data.
- **Fixtures/data:** actual annotated recording executor with existing context runner, public owner publication, Public billing callable namespace for support access, deterministic assigned-child/forwarding parent or existing planner fixture. Configure all parent models explicitly when required; do not rely on startup inheritance.
- **Mocks:** model HTTP/response fixture only if parent route needs it; never mock the Framework executor handoff.
- **Affected surface:** Application API and Configuration/manifest behavior.
- **Compatibility expectation:** preserve owner-relative references, role separation, explicit input and root/child behavior.

### 4) Consumer publication, retained capture and access boundaries

- **Type/location:** Framework integration, same execution test class; component candidate-overlay tests in `ExecutorDependencyResolutionTest` where appropriate.
- **What it proves:** capture a public `SkillInvocationHandoff` before billing publication. Publish billing alone with complete unchanged leaf documents and new key. Fresh direct/parent calls use new billing settings; old handoff uses original billing and owner settings. Assert owner `executorConfigGenerationId()` is unchanged across billing-only rotation and another consumer stays unchanged. Omit the billing entry in a complete subsequent publication and observe empty consumer map on fresh invocation, retained map on old handoff. Capture before owner replacement too and prove both old maps remain paired. Existing owner removal/recreation behavior remains.
- **Authority:** validation and both preparation overload paths reject billing configuration containing any default/other entry, even `{}`; no active target changes after rejection. Foreign Private executor is denied; support cannot invoke Private billing; YAML AND Java declarative roles and invocation-time proxy advice remain enforced with the same caller. Reuse existing auth matrices rather than duplicate them all; ensure at least one new consumer-map test uses a secured executor and asserts no call on denial.
- **Candidate view:** preparation can see consumer data from its candidate overlay, including a transitive return path; prepared consumer publication does not freeze an obsolete foreign owner at runtime. Fresh admission rebinds using captured current generations. Existing coherent-capture race test remains in the focused suite.
- **Fixtures/data:** deterministic handoffs, generation snapshots, distinct keys/owner values and complete target YAML; latches only for explicit concurrency tests, always bounded/released in `finally`.
- **Mocks:** existing app context/discovery seams; no live lookup substitute or fabricated snapshot used as execution proof.
- **Affected surface:** Application API, Configuration/manifest behavior, Internal lifecycle, Ephemeral diagnostics for the existing owner ID assertion.
- **Compatibility expectation:** protect target authority, retained generation semantics and current owner-ID meaning; no new trace format.

### 5) TypeSafe selected settings policy

- **Type/location:** unit, sibling `extensions/decisions/src/test/java/ai/loomspan/decisions/internal/typesafe/TypeSafeSettingsTest.java` and request tests if validation moves.
- **What it proves:** owner URL plus consumer key works without owner key; consumer key beats owner key; absent consumer key/empty map uses owner key; both absent fail at owner key path. Present null/blank/non-string/non-header-safe consumer keys fail at consumer path without fallback. A valid consumer key does not require validating an unselected owner key. Preserve exact selected credential text and absence-only owner URL default/prefix behavior. Consumer `base_url` fails at its exact path for any value including null, blank and owner-equal URL; unknown consumer keys fail. Existing owner/leaf unknown fields and URL-invalid matrix remain.
- **Fixtures/data:** explicit maps with `containsKey` distinctions, synthetic local keys, URL prefixes and existing invalid-value matrices.
- **Mocks:** none. Capture old settings then mutate source maps to prove selected key and endpoint remain immutable settings values.
- **Affected surface:** Pack Configuration behavior; internal collaborators are not an SPI.
- **Compatibility expectation:** preserve owner-only accepted behavior and validation rules for selected key/owner URL; directly update all helper signatures.

### 6) TypeSafe actual HTTP selection and retries

- **Type/location:** pack integration, sibling `TypeSafeTransportTest` plus existing support helper.
- **What it proves:** MockWebServer observes the owner destination/path and exact consumer Authorization header. On a retryable failure, both attempts use the originally captured endpoint/key and identical request bytes. Concurrent invocations use independent keys through the same shared transport. A rejected consumer URL/key causes zero HTTP requests. Leaf `max_attempts` 1, 2 and default 3 control sends; out-of-range/null/noninteger/unknown leaf config still fail. Existing lifecycle/deadline/response and diagnostic tests remain.
- **Fixtures/data:** existing native request/response fixtures and local endpoints, retry-after zero, bounded latches for source-map changes/retry timing if needed.
- **Mocks:** final invocation is mocked only for the pack's transport unit seam; explicitly supply both config maps and live lifecycle/diagnostics. Real HTTP client remains.
- **Affected surface:** Configuration behavior and existing native external protocol.
- **Compatibility expectation:** protect current destination/retry/body/lifecycle behavior and owner-only transport cases.

### 7) Installed-JAR foreign consumer qualification

- **Type/location:** end-to-end local application, sibling `extensions/decisions/examples/support-triage/src/test/java/ai/loomspan/examples/triage/PublicationConsumerTest.java` plus `ConsumerHarness`; extend `CompositionConsumerTest` if that better reuses parent fixtures.
- **What it proves:** installed changed Framework + actual installed TypeSafe executor + actual transport deliver local owner gateway and consumer headers. Publish Public default owner with URL and no key, billing with key and two qualified-reference leaves, another consumer with distinct key, and support parent pointing to Public billing. Verify direct and parent requests, leaf retry 2, then billing-only key rotation. A retained handoff captured before rotation, invoked after it, sends old billing key to old captured owner endpoint; fresh work uses new billing key and same owner ID; the other consumer stays unchanged. Omitted billing consumer settings fall back to owner key when an owner key is present; absence of both fails without a send. Consumer URL is rejected without contacting either destination. Keep an owner-only same-namespace scenario green.
- **Fixtures/data:** extend harness to publish explicit namespace/visibility with complete configuration; use public handoff API and MockWebServer requests as evidence, not only `ConsumerProbe`. Reuse parent/planner fixtures and trace capture. All headers/assertions use synthetic values. Separate semantic scenarios when that makes failure diagnosis clearer.
- **Mocks:** only local provider/planner servers; no mocked TypeSafe executor/transport or source-tree substitution for installed artifacts.
- **Affected surface:** supported Java handoff, namespace configuration/publication, pack configuration and existing external HTTP protocol.
- **Compatibility expectation:** qualify actual cross-repository consumers at unchanged versions; preserve discovery and owner-only usage.

## Authoring Claims and Evidence Mapping

| Guidance claim | Evidence required |
| --- | --- |
| Same selector, optional absent/empty/same-namespace/selector-free consumer maps; malformed present data rejected | Case group 1 |
| Independent immutable maps with exact container/value fidelity | Case groups 1–2 |
| Leaf owns consumer identity even under a foreign parent; two leaves share settings | Case groups 3 and 7 |
| Complete target-only publication, omission clears fresh calls, retained work keeps both maps, owner ID unchanged | Case groups 4 and 7; existing parser/manager tests |
| Public sharing and composed authorization/input behavior remain | Case groups 3–4 and existing authority/advice regressions |
| TypeSafe endpoint owner-only, consumer key/fallback, rejection and invocation-time validation | Case groups 5–7 |
| Leaf retry `max_attempts` 1–3/default 3 and stable captured request/settings | Case groups 5–7 and existing request tests |

Existing generic owner guidance is aligned; the packaged TypeSafe statement rejecting every leaf config key is documentation drift. Correct that statement using the already implemented retry rules. Product docs should cite stable executable anchors and local-provider limitations, with coverage tables updated after the corresponding tests pass. Do not claim hosted accuracy, quality or external access from local fixtures.

## How to Run

Prerequisites: Java 21, Maven 3.9+, and pinned JSLT `ai.loomspan.thirdparty:jslt:0.1.15-jackson3-cf30e6d` installed per Framework `docs/building-jslt.md`. Framework has the Windows Maven wrapper used below; Extensions uses its documented `mvn` commands. Use the same local Maven repository for both projects. Install JSLT only if resolution shows it missing; a dependency setup failure is not a product-test failure. Run commands in the stated working directory and quote `-Dtest` in PowerShell. If tests are split into new classes, add those exact names to focused selectors and record the actual commands used.

Framework working directory `C:/opendev/code/loomspan-framework`:

```powershell
./mvnw.cmd -B '-Dtest=ExecutorDependencyResolutionTest' test
./mvnw.cmd -B '-Dtest=SkillExecutorTest,ApplicationApiValueTest,LoomspanPublicSurfaceArchitectureTest,SupportedSurfaceIntegrationTest,ExecutorConfigurationTests,ExecutorConfigurationStartupTests,ExecutionConfigurationParserTest,ExecutorConfigurationManagerTests,ExecutorDependencyResolutionTest,ExecutorGenerationExecutionIntegrationTest,NamespaceExecutionAuthorityIntegrationTest,SharedDeclarativeAuthorizationIntegrationTest,ExecutorInputTransformIntegrationTest,ExecutorInvocationLifecycleIntegrationTest,ExecutorDiagnosticsIntegrationTest' test
./mvnw.cmd -B install
```

The final Framework install runs its full tests and publishes the current Framework artifact locally for the pack. It is required; a prior installed same-version JAR is insufficient. `LoomspanPublicSurfaceArchitectureTest` must actually run after production types change, even when included in the full build.

Extensions working directory `C:/opendev/code/loomspan-extensions`, after Framework install:

```powershell
mvn -B validate
mvn -B -f extensions/decisions/pom.xml '-Dtest=TypeSafeSettingsTest,TypeSafeRequestTest,TypeSafeTransportTest' test
mvn -B -f extensions/decisions/pom.xml install
mvn -B -f extensions/decisions/examples/support-triage/pom.xml verify
```

The pack install runs all pack tests. Example `verify` runs its independent installed-JAR suite; aggregator validation alone proves neither production executor nor example. Existing optional Console profile need not run because there is no consumed protocol change. Preserve ordinary diagnostic tests and the existing journal exception; add no sensitivity classification or content rewriting. If execution cannot complete, report exact attempted commands, setup blockers and residual acceptance gaps; do not mark unrun checks passed.

## Acceptance Criteria Mapping

| Ticket criterion | Required verification |
| --- | --- |
| Direct/parent maps, two billing leaves, another namespace, ordinary input/access | Case groups 3–4 and 7; focused authority/advice suite |
| Supported immutable scoped accessor, fidelity and empty cases, owner behavior | Case groups 1–2 plus startup/parser and supported-surface suite |
| Billing-only rotation, retained handoff, omission, target authority, isolation and owner ID | Case groups 4 and 7 |
| Actual Decisions executor/transport, destination/credential/URL rejection/retries/owner-only | Case groups 5–7 and installed-JAR example verify after current Framework/pack install |
| Framework/pack docs, supported-surface update and architecture test | Guidance evidence table, doc diff review and actual architecture test run |

## Exit Criteria

- [x] Initial red feature evidence is recorded accurately; environment failures are separate.
- [x] All implemented cases above pass post-change, with no unresolved required failure.
- [x] Exact optional-versus-malformed semantics, map fidelity and leaf-owned scope have focused assertions.
- [x] Direct/parent and retained public handoff tests exercise actual Framework execution; cross-repository example uses installed current artifacts and actual TypeSafe HTTP transport.
- [x] Consumer-only rotation and omission preserve other namespaces and owner ID; foreign publication/Private access and composed authorization remain denied as required.
- [x] Architecture and supported-surface tests pass after production changes; no extra public constructor, types, SPI or internal signature leak appears.
- [x] Existing owner-only and lifecycle/input/diagnostic behavior remains green; obsolete internal construction signatures are updated without fallback overloads.
- [x] Full Framework install, Extensions validation, Decisions install and example verify pass in dependency order, and exact results/build identities are recorded.
- [x] Authoring claims map to actual passing tests and current source, documented drift is corrected, and relevant README coverage rows are synchronized.
- [x] Versions and v1 package stay unchanged; no REST/SSE/NDJSON or marker change is introduced.
- [x] Unrelated pre-existing deletions remain untouched.

## Optional Developer Checks

None required. All acceptance criteria have local executable verification; live hosted TypeSafe access and model quality are outside this ticket.

## Step 4 results

All exit criteria are met by the implementation and verification recorded in the
companion implementation plan's Step 4 execution evidence. The initial resolver
feature test was compile-red before the carrier existed; sandbox JVM-attach failure
prevented the intermediate value-red run, so the original owner-only resolver
source supplies the explicitly permitted missing-selection evidence. Focused
Framework verification passed 96 tests; full Framework install passed 2,379 tests
with two existing skips and one recorded unrelated cold-JVM timing flake resolved
by its warm retry. Focused Decisions tests passed 69 cases, pack install passed
86 cases, and installed-JAR example verify passed 21 cases with its optional
Console test skipped. Exact commands, source bases and installed JAR hashes are
in the implementation plan and pack `docs/tested-builds.md`. No required
acceptance failure remains. Fresh Step 5 review is still required.
