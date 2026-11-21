## Code Review Findings

No actionable findings.

This is independent review 3, running in pipeline mode as Step 5 of the Full
5-Step Pipeline. No earlier review artifacts were read. The complete independent
defect review preceded the final requirements/conformance mapping. No production,
test, configuration, documentation, ticket or plan changes were made in this
context; this review document is the only implementation-visible addition.

## Findings Resolved in This Context

None.

## Open Questions and Assumptions

None affecting acceptance. The ticket explicitly authorizes coordinated direct
updates at unchanged artifact versions and in the existing v1 package, without
compatibility shims. Hosted TypeSafe access/quality and the optional Console
profile are outside this ticket's local qualification scope.

## Review Scope and Independent Analysis

- Framework: `main`, HEAD `cd1c15237cb31770853dfb78a7399f1a4594dbd0`.
  The one commit ahead of `origin/main` is unrelated ticket cleanup. Reviewed the
  current ticket's complete unstaged diff against HEAD, connected production
  paths, tests and documentation; there are no staged changes. Untracked supplied
  research/plans were read as requirements context. Prior review files were
  excluded. Earlier cleanup/deletions were preserved.
- Extensions: `codex/scaffold-extensions`, HEAD
  `a6de3966e771a27683d9f6d8f4f91ba0006c3844`, matching its upstream. Reviewed the
  Decisions production, test, example and documentation unstaged diff. There are
  no staged changes or untracked implementation files. The pre-existing
  `extensions/decisions/CHANGELOG.md` deletion is unrelated and preserved.
- Traced raw namespace data through `ExecutorConfiguration`, publication parsing,
  immutable `SkillGeneration`, coherent reachable capture, dependency validation,
  `SkillAdmissionSnapshot.ExecutorBinding`, `ExecutionCoordinator`, the private
  method-handle bridge and the final supported invocation.
- Optional consumer selection uses the executing definition's namespace and the
  same exact selector as the owner. Only missing keys are optional; malformed
  present intermediate/final nodes fail. Owner selection still runs first and
  remains required. Same-namespace and selector-free cases produce empty
  consumer maps. Both maps use the existing container-copy semantics.
- Capture already retains each reachable leaf and executor owner. Binding is
  rebuilt from the captured immutable generations outside the publication lock;
  no live lookup, extra generation authority, lease or preparation-time foreign
  runtime binding was introduced. Consumer-only replacement changes neither the
  owner's ID nor another consumer's data. Retained handoffs retain both maps.
- Traced actual pack execution from `TypeSafeExecutor` through request validation,
  settings capture and the HTTP transport. Allowed-field validation moved into
  settings as one responsibility. Only consumer key presence selects a consumer
  credential; omission falls back. Selected-key validation preserves exact text,
  invalid present keys fail without fallback, and the unselected owner key is not
  required. Consumer destinations/unknown keys fail before HTTP. Endpoint/key
  capture remains outside the retry loop, and native request bytes, leaf retry
  bounds, cancellation and deadlines retain their existing paths.
- Considered authorization/visibility, namespace publication authority, retained
  caller security, concurrent publication, container/value fidelity, lifecycle,
  failure diagnostics and resource cost. No new prompt/data rewriting, public
  SPI, bean replacement contract, serialized protocol, compatibility marker or
  diagnostic payload was introduced. Existing journal projection is untouched.

## Verification Results

Commands were run in this fresh context. Maven output was retained in ignored
`.tmp-review3-*.log` files under the Framework working directory. Commands below
show the exact Maven invocation arguments, omitting only shell log redirection.

Framework working directory: `C:/opendev/code/loomspan-framework`.

- PASS — `./mvnw.cmd -B '-Dtest=SkillExecutorTest,ApplicationApiValueTest,LoomspanPublicSurfaceArchitectureTest,SupportedSurfaceIntegrationTest,ExecutorConfigurationTests,ExecutorConfigurationStartupTests,ExecutionConfigurationParserTest,ExecutorConfigurationManagerTests,ExecutorDependencyResolutionTest,ExecutorGenerationExecutionIntegrationTest,NamespaceExecutionAuthorityIntegrationTest,SharedDeclarativeAuthorizationIntegrationTest,ExecutorInputTransformIntegrationTest,ExecutorInvocationLifecycleIntegrationTest,ExecutorDiagnosticsIntegrationTest' '-DreuseForks=false' test`: 96 tests, no failures/errors/skips.
- FAIL — `./mvnw.cmd -B '-DreuseForks=false' '-DforkCount=4' '-Dsurefire.rerunFailingTestsCount=1' install`: all 2,379 tests ran, with one final failure, zero errors, two skips and one flake. The only final failure was unchanged `ExecutorInvocationLifecycleIntegrationTest#capturedInvocationObservesCallerCancellationAcrossThreadsWithoutClearingInterrupt` line 140, requiring `bean.interrupted == true`; its immediate retry also failed. The unchanged worker loop can exit after observing cancellation before worker interruption arrives. That assertion and the cancellation/interruption implementation have no ticket diff. The same class passed all seven tests in the focused run. The separate known 100 ms parent-start latch in unchanged `ConcurrentGroupedExecutionIntegrationTest` passed its immediate retry.
- PASS — `./mvnw.cmd -B '-Dtest=ExecutorInvocationLifecycleIntegrationTest#capturedInvocationObservesCallerCancellationAcrossThreadsWithoutClearingInterrupt' test`: isolated rerun passed, one test.
- PASS — `./mvnw.cmd -B '-DreuseForks=false' '-DforkCount=4' '-Dsurefire.rerunFailingTestsCount=3' install`: 2,379 tests, zero final failures/errors, two existing skips, two explicitly reported flakes. The cancellation assertion passed its third attempt; the known 100 ms nested direct-mission timeout fixture passed its retry. Current Framework artifact installation completed successfully at `2026-11-20T21:51:30-08:00`. The architecture test passed all eight cases in this full run as well as the focused run.
- PASS — `git -c core.safecrlf=false diff --check`.
- PASS — `git -C ../loomspan-extensions -c core.safecrlf=false diff --check`.

Extensions working directory: `C:/opendev/code/loomspan-extensions`, after the
successful current Framework installation, in dependency order:

- PASS — `mvn -B validate`.
- PASS — `mvn -B -f extensions/decisions/pom.xml '-Dtest=TypeSafeSettingsTest,TypeSafeRequestTest,TypeSafeTransportTest' test`: 69 tests, no failures/errors/skips.
- PASS — `mvn -B -f extensions/decisions/pom.xml install`: 86 tests, no failures/errors/skips; installed the current pack.
- PASS — `mvn -B -f extensions/decisions/examples/support-triage/pom.xml verify`: 21 tests, zero failures/errors and one optional Console skip; completed at `2026-11-20T21:53:04-08:00`. Both `PublicationConsumerTest` methods passed using the installed artifacts and actual executor/transport with local HTTP servers.

Target and installed JAR SHA-256 values were independently compared and match:

| Artifact | SHA-256 |
| --- | --- |
| Framework `1.0.0-beta.10-SNAPSHOT` | `5CA1209FF7E7A3582EB1585FFAF13EDEFBC6B381EA5E1B19276756A0AF1DCF1A` |
| Decisions `0.1.0-SNAPSHOT` | `655D482A19483860EF1F3FB555DBACFD6D9FFECA196235E093EEC3C5D4245580` |

- NOT RUN — optional Console profile, demo `exec:java`, hosted provider quality/account-access checks: no consumed Console protocol changed, and these are outside the ticket's local acceptance criteria.

## Requirements and Plan Conformance

| Ticket criterion | Independently checked evidence |
| --- | --- |
| Direct/parent delivery, two billing leaves, independent consumer, input and access behavior | `ExecutorGenerationExecutionIntegrationTest#leafConsumerConfigurationIsCapturedIndependentlyAcrossParentsAndPublication`; actual local HTTP in installed `PublicationConsumerTest`; focused authority/advice/input regressions. |
| Supported immutable independent accessor, null/key/container fidelity and empty cases; strict owner behavior | `SkillExecutorTest`, `ApplicationApiValueTest`, `ExecutorDependencyResolutionTest`, startup/parser/configuration tests, architecture and supported-surface tests. The exact method-name expectation adds only `consumerExecutorConfig`. |
| Billing-only rotation, retained handoff, omission, target-only authority, consumer isolation and stable owner ID | Real Framework direct/parent/public-handoff publication integration, rejection through validation and both preparation overloads, dependency capture tests, installed publication consumer. |
| Actual TypeSafe destination/key, URL rejection, retry and owner-only behavior | Passing settings/request/real HTTP transport tests, full pack install and independent installed-JAR example verify. The owner URL with no owner key works with the consumer key. |
| Framework/pack guidance and supported surface | README, annotation/accessor JavaDoc, routed authoring/Java/reload topics, pack executor/transport/example documentation, updated exact accessor expectation and actual architecture execution. |

- Partial: none. Missing: none. No unsafe deviation or unrelated implementation expansion found.
- Safe verification adjustment: isolated JVMs address the documented reused-JVM environment issue. The full retry transparently permits warm reruns of unrelated timing-sensitive fixtures, preserving the initial failed result and final flake counts. No test assertions or lifecycle code were changed to obtain a pass.
- Compatibility review: the allowlisted final v1 Application API gains one additive `Map<String,Object>` accessor. Existing accessors retain their meaning; the owner generation ID is unchanged. Annotation configuration remains the existing documented root/selector contract, extended with independent optional consumer lookup. Internal constructors/bridge/binding and pack collaborators are directly updated under the ticket's explicit no-shim decision. No extra supported type, public constructor, leaked internal signature type, SPI or bean extension path appears. Versions and REST/SSE/NDJSON contracts remain unchanged; no Console marker change is needed.

## Skill-Authoring Documentation Impact

- **Assessment:** Affected.
- **Rationale:** Authors need leaf-owned consumer identity, exact optional/malformed selector semantics, independent maps, complete publication/retention and TypeSafe endpoint/key/retry policy.
- **Documents reviewed:** Framework `README.md`; `agent-skills/loomspan-docs/SKILL.md`; its skill-authoring index, `source-verification.md`, `executor-skills.md`, `typesafe-skills.md`; Java API index, `java-skills.md`, `skill-reload.md`; sibling Decisions README, `docs/executor.md`, `docs/transport.md`, `docs/tested-builds.md`, example README and corresponding code/test/example paths.
- **Evidence checked:** Current resolver, capture/publication path, invocation copying/construction, actual execution, TypeSafe request/settings/transport and the successful verification above. The repository-local skill and Maven dependencies identify the same Framework version.
- **Drift classification:** Aligned. The current TypeSafe topic documents the implemented `max_attempts` range/default and provider validation policy; no remaining blanket rejection of leaf configuration appears in that updated topic. New configuration/publication claims match executable paths and tests.
- **Coverage table:** Current; generic executor, Java declarations and pack TypeSafe coverage rows identify the relevant evidence and local-provider limitations.
- **LLM-first usability:** Pass. Generic selection and retention live in generic executor/reload guidance; provider precedence lives in the TypeSafe topic/pack. Complete target publication includes explicit Public visibility where foreign parents need it. Stable named anchors support claims without adding unsupported SPI guidance or hosted-quality claims.

## Residual Risks and Optional Developer Checks

- Two pre-existing timing-sensitive Framework tests required warm retries under the loaded four-fork suite. Their exact failures/passes are recorded above; this review changes neither those tests nor lifecycle behavior. All ticket-specific focused checks and cross-repository suites passed, and the complete current Framework artifact was installed successfully.
- Local synthetic provider/planner servers establish composition, destination, credentials, request/retry behavior and retained configuration. They do not establish hosted TypeSafe accuracy, latency, cost or account access.
- Optional developer checks: none required. No unresolved acceptance failure or developer decision remains.

## Disposition

**Approve** — no actionable findings of any priority; verification is sufficient.
`REVIEW_RESULT: clean`. This context made no implementation-artifact changes.
