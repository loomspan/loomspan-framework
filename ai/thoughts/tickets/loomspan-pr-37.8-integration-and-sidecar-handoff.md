# PR 37.8 — Verify PR 37 and finalize the Sidecar handoff

## Outcome

Close the locally staged series with a working Framework and Console and an accurate contract for the next Sidecar planning context.

## Requirements

- Verify the combined series through normal declaration, publication, invocation, catalog, authored resolution, provider, worker and Console entry points. Close residual PR 37 integration gaps, including stale callers, fixtures, stored identity fields and obsolete fallback paths. Preserve targets when converting identity fields and preserve raw content elsewhere.
- Run the integrated Framework and Console builds and relevant suites, including `LoomspanPublicSurfaceArchitectureTest`. Reconcile prior deferred checks and the documentation audit against the completed implementation.
- Update the sibling Sidecar document `../loomspan-sidecar/ai/thoughts/future/portable-skill-naming.md` with the actual public signatures, examples, changed observable behavior, verification evidence and downstream documentation findings. Give a fresh Sidecar context enough information to write its own tickets. Limit sibling changes to that document.

## Acceptance criteria

- [x] The final Framework and Console builds and verification pass together. The completion record accounts for earlier deferred checks and provides evidence for exact lookup, dispatch and raw trace fidelity.
- [x] Framework and Console public examples agree with the final implementation, including ambiguous single-hyphen local names. No public invocation, lookup or dispatch retries under an alternate spelling or owner.
- [x] The Sidecar future document identifies verified Framework behavior and concrete public call shapes, separates pending Sidecar work, and links to the evidence and documentation inventory. No Sidecar implementation or ticket creation is required.

## Context

Follows PR 37.7 in the local PR 37 series. This is the closing ticket. Sidecar and Cloud implementation are separate efforts. See the [portable naming proposal](../future/portable-skill-naming.md).

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Final assurance spans supported Java APIs, serialized contracts and the integrated runtime.
- **Reassessment triggers:** None.

## Pipeline notes

PR 37 is a destructive replacement across undeployed products. Remove superseded naming behavior without aliases, shims or migration instructions. Write current-state documentation. The integrated local series must be green at closure.


## Execution notes

- Selected profile is full under the developer's series-wide authorization. Breaking replacement, current-state documentation and local commits are authorized. The Sidecar handoff remains uncommitted by explicit developer instruction.
- Baseline Framework HEAD was `e7b051e`, with clean tracked/staged work. The supplied untracked Framework proposal, this ticket, research and plans were preserved. Sidecar baseline was `4f4d68307d6000255d8fd48e224143c5561b98f0`, with only its existing untracked future handoff. Cloud baseline was `49b83f36d50fa543d4f8121ad0891d12abc95827`, with a clean worktree.
- The production delta is the internal `PreparedCandidateVariables.unknownSelector` lookup. It selects `namespaces.get(namespace)` exactly. Application API signatures, Spring wiring, public allowlists, serialized schemas and the coordinated unreleased version remain as established by PR 37.1–37.6. No compatibility shim is required or introduced.
- The checkout's matching `loomspan-docs` router and source-backed Java/API authoring topics were consulted. Published naming guidance is aligned. The worked variable declaration was documentation drift and is corrected with its expected resolved tree and actual invocation. Its README already points to the correct file and integration test. No further published contract or coverage change was needed.

## Completion record

### Integration corrections and content fidelity

The known baseline reproduction ran two existing tests and failed both. The worked declaration used invalid `read_record`, and the dynamic-selector test expected uppercase `Sales` to validate. The worked YAML, resolved JSON name and caller now use `read-record`. Lowercase dynamic data-key resolution still succeeds, while public validation/preparation reject `Sales` and the internal selector does not retry a different spelling. The added helper assertions distinguish the removed case-insensitive branch.

The first complete Maven run reached 2,569 tests and identified 12 failures and 140 errors in 35 previously unselected test classes. Their old canonical identity fixtures were corrected from their declarations and call sites. This includes application annotation metadata, executor diagnostics/lifecycle, advisor evidence, coordinator routing, mission retention, namespace dispatch/settings, quota attribution and definition copies. The executor integration references select `infra:configured`, `infra:plain`, `infra:secured` and `billing:one`, with resolved `caller:leaf`/`support:root` facts. Mission and concurrent fixtures use valid locals and colon-qualified runtime references. The reflected `deterministicTarget` fixture declares `name = "deterministic-target"` explicitly.

Identity edits preserve opaque task IDs, model aliases, configuration, data, diagnostic text and raw provider exchanges. For example, `default-model`, the diagnostic message `default-denied`, frame IDs named `default-parent`, and the historical raw tool response name `default-coordinate` retain their own meanings. The browser fixtures now expect resolved `default:test-entry`, `default:root-skill` and `default:summarize#step-2` labels. Their raw response content and inert script/link payloads are preserved.

Full frontend verification exposed an asynchronous test readiness assumption in the expired-artifact case. Its callback could fire before the router committed the cleared query state. The test now waits for that observable location state. This changes test synchronization only. The remaining full-browser failures were old resolved-name expectations in the shared artifact navigation helper and nested-synthesis test, corrected against the current corpus.

The final fidelity audit compared all 82 canonical trace files byte-for-byte with pre-ticket HEAD. Every file is identical, including all 156 existing model request/response payloads and raw tool-call/response contents. The 165 required README/corpus LF files pass, derived artifact lengths and evaluation checksums require zero updates, and dated replay/archives are unchanged. The worked expected JSON differs only at the resolved declaration name. Its four variable names, six occurrences, labels, configuration and resolved values are intact.

### Final verification

All commands below ran successfully on the final sources. Root Maven logs live outside root `target/` so `clean` can remove its own output tree. Java sources were frozen during compilation. Console commands used native Windows permissions for the existing DACL checks and the cached pinned Go/Node/npm tools.

| Gate | Actual result and receipt |
| --- | --- |
| Complete Framework `clean verify` | **2,569 tests, zero failures/errors, three skips**, BUILD SUCCESS. Includes `LoomspanPublicSurfaceArchitectureTest` and all current Java corpus producers. Log `loomspan-console/target/pr37-tools/pr378-full-final.log`. |
| Final focused lifecycle/quota repair | 77 tests, zero failures/errors/skips. Log `loomspan-console/target/pr37-tools/pr378-residual2.log`. Final complete verification rechecks every corrected class. |
| Console canonical `verify` | All 610 frontend tests, complete Go suite, type check, authoring/replay and generated asset checks passed. Log `loomspan-console/target/pr37-tools/pr378-console-verify-final.log`. |
| Console canonical `build` | Passed its verification phases and produced the versioned native executable. The repeated frontend phase passed all 610 tests. Log `loomspan-console/target/pr37-tools/pr378-console-build.log`. |
| Full Go race suite | Passed all packages. Log `loomspan-console/target/pr37-tools/pr378-race.log`. |
| Full browser suite | All **40 workflows passed** against the fresh executable. Log `loomspan-console/target/pr37-tools/pr378-browser-all-final.log`. |
| Official MCP conformance | All five scenarios passed, with 17 scenario checks and zero failures/warnings. Run by the orchestrator as Step 4 verification. Log `loomspan-console/target/pr37-tools/pr378-mcp-conformance-final.log`. |
| Version and fidelity/metadata audit | `1.0.0-beta.10-SNAPSHOT` consistent. All 82 traces/156 model payloads unchanged, 165 LF files clean, current derived metadata and dated replay correct. Log `loomspan-console/target/pr37-tools/pr378-audit.log`. |
| Diff and handoff static checks | Diff whitespace passed. Every handoff link resolves, signatures match supported source and sibling scope is exact. |

Exact commands, from Framework root unless the Console directory is specified:

```powershell
.\mvnw.cmd clean verify '-DargLine=-javaagent:C:/Users/mgiacomi/.m2/repository/org/mockito/mockito-core/5.24.0/mockito-core-5.24.0.jar'
.\mvnw.cmd test '-Dtest=MissionLifecycleTest,SessionUsageServiceTest,FrameworkExecutionLifecycleTest,ExecutionStateServiceTest,PhysicalBranchContextTest' '-DfailIfNoTests=false' '-DargLine=-javaagent:C:/Users/mgiacomi/.m2/repository/org/mockito/mockito-core/5.24.0/mockito-core-5.24.0.jar'
python scripts/loomspan_version.py check
python loomspan-console/target/pr37-tools/pr378-audit.py
git -c core.safecrlf=false diff --check
```

From `loomspan-console`, the process-local Console setup and commands were:

```powershell
$pr37NodeBin = 'C:/opendev/code/loomspan-framework/loomspan-console/target/pr37-tools/node-24.18.0/package/bin'
$env:PATH = "$pr37NodeBin;$env:PATH"
$env:npm_config_cache = 'C:/opendev/code/loomspan-framework/loomspan-console/target/pr37-tools/npm-cache'
$env:npm_config_offline = 'true'
go run ./internal/buildtool verify
go run ./internal/buildtool build
$env:PLAYWRIGHT_BROWSERS_PATH = Join-Path $env:USERPROFILE 'AppData/Local/ms-playwright'
npm.cmd --prefix web run test:e2e
go run ./internal/buildtool mcp-conformance
$env:PATH = "C:/msys64/mingw64/bin;$env:PATH"
$env:CGO_ENABLED = '1'
go test -race ./...
```

The three Maven skips are the two opt-in `Pr18RecordedHandoffDiagnosticTest` methods because `pr18.diagnostic` is absent, and `StartupSkillPathTest#literalSymlinkKeepsItsLogicalFilename` because this host cannot create symbolic links. All normal loopback protocol tests passed. MCP's caching scenario skips inapplicable `resources/read` hints. Its existing runner accepts the known Windows Node post-success libuv closing assertion. No runner success criterion changed.

The resulting starter artifact is `target/loomspan-spring-boot-starter-1.0.0-beta.10-SNAPSHOT.jar`, 1,208,798 bytes, SHA-256 `87fa70dead94b2818603476b36de0f2e0d2ad3a42ab0248639fb003ca56f638b`. No Maven install was performed. The Sidecar handoff documents a fresh install as preparation for that subsequent workstream.

Earlier attempts are retained in ignored logs. `pr378-red.log` records the two expected baseline failures. `pr378-targeted.log` records four stale diagnostic expectations, `pr378-full-first.log` the 152 stale fixture failures, and `pr378-residual1.log` the final eight fixture errors corrected before the 77-test pass. `pr378-console-verify.log` records the asynchronous readiness test failure and `pr378-browser-all.log` the old-label failures. Offline MCP preparation initially lacked cached pinned GitHub/transitive archives, which the orchestrator supplied from existing caches before the successful exact command. These have final passing dispositions above.

### Deferred-check reconciliation and exact boundary audit

| Ticket | Closing evidence |
| --- | --- |
| PR 37.1 declarations | Full identity, annotation, startup/reloader and duplicate-index tests pass. The 20 authored-reference cases first deferred there were closed in PR 37.3 and are green in the complete final YAML/executor suites. |
| PR 37.2 startup paths | Full filesystem/classpath/classpath*/JAR and supplied-label suites pass. The conditional symbolic-link result is documented above. |
| PR 37.3 authored references | Complete dependency, binding, forwarding, evidence and authorization suites pass, including local `warehouse-lookup` versus foreign `warehouse:lookup`, missing-local failure and role-specific indexes. |
| PR 37.4 public API | All eight root operation shapes, explicit metadata components, catalog local-only lookup, retained admission/authentication and every direct caller compile and pass in the complete suite. |
| PR 37.5 provider and worker | The real `NamespaceExecutionAuthorityIntegrationTest` wire/prompt/persisted-evidence cases pass, plus all manager, adapter, plan, worker, forwarding and root-to-child protocol suites. The shared worked variable/executor integration is now green. |
| PR 37.6 Console | All three current Java contract corpora, observation integrations and complete Go/frontend/race/browser/MCP checks pass together. Exact same-local owner selection and raw evidence are covered. |
| PR 37.7 documentation | Current local examples agree with the final signatures and protocol tests. The four-product inventory is carried into the Sidecar handoff with pending downstream work distinguished. |

Source review confirms `DefaultSkillTemplate.captureRoot` constructs the supplied pair, `DefaultSkillCatalog.skill` validates a local name in its selected namespace, authored fields resolve through exact owner-relative identities and the correct index, `RequestBoundToolCallingManager` dispatches only opaque offered names, and workers require the assigned colon reference. `PreparedCandidateVariables` now selects the exact namespace key. Remaining case folding is in independent domains such as HTTP header names, provider settings, evidence operator tokens and path validation. There is no public root, catalog, authored lookup, provider or worker retry under an alternate spelling or owner.

### Sidecar handoff and repository boundary

Updated only `../loomspan-sidecar/ai/thoughts/future/portable-skill-naming.md`. It contains all six facade and two handoff signatures, lifecycle semantics, validation/catalog reuse, declaration/index rules, loader scope, exact authored/provider/worker behavior, Console components, actual final evidence and concrete downstream documentation work. Every relative link resolves to an existing artifact/source. Consumer Java examples use only `ai.loomspan.api` types.

The original handoff SHA-256 was `30b48c1d2812953daf7e65d5a9b2b9c2a0863561c0faa36eaccdbeb734dbdd6c`, and the updated SHA-256 is `f534d3f8a21a3b2ea3d5696d5808636a6d96a892f4056e34c4f37cb33ca09a7d`. Sidecar still has only that existing untracked document and no staged changes or commit. Cloud retains its clean baseline. No Sidecar implementation or tickets were created.

No required deferred implementation or verification remains for Step 4. Fresh independent review is the next pipeline stage. Optional developer checks are none. Hosted provider accuracy, Sidecar/Cloud adoption and release publication retain their own workstream evidence requirements.
