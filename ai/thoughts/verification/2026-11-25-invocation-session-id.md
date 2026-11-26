# Invocation session ID implementation and verification

Date 2026-11-25. Pipeline Step 4, Full 5-Step Pipeline, approved by the developer with “proceed”.

## Implementation

All four `SkillTemplate.invoke` overloads and both admitted invocation overloads return `SkillInvocationResult(text, sessionId)`. The result validates non-null text and nonblank root identity. Existing producer conversion and exact text are preserved.

`SkillException` has final nullable session metadata and a direct `getSessionId()` getter. The facade creates immutable invocation metadata snapshots, preserves designated built-in subtypes and authored messages, retains the original failure as cause, copies suppressed evidence, and selects diagnosis from the current completed session. Concurrent reused application exceptions retain their source metadata. `SkillAccessDeniedException` carries post-session identity and extends Spring's `AccessDeniedException`.

The root runner announces the UUID before session collaborator initialization and passes that same value into the session. The facade captures identity before execution, optional mapping, observers or ownership release. Public view mapping remains conditional on an observer. The obsolete internal `CompletionPhaseFailure` wrapper was removed. Existing JVM `Error` propagation remains the fatal-error boundary defined by the implementation plan.

Nested root execution checks the existing nesting precondition before allocating identity, inside the claimed-root cleanup region. This was needed after integration evidence showed the binding scope had previously performed that rejection after session construction. Rejected nested invocations now have the planned null identity, retain the original rejection message and release their captured ownership.

The two new top-level API types were deliberately added to the closed allowlist. Existing public-root callers, current README/quickstarts and routed Java API/skill-authoring guidance were updated together. The supported inventory contains 32 types. Source and focused tests establish the updated documentation claims, with drift classification **aligned**.

## Acceptance evidence

| Acceptance criterion | Executable evidence |
| --- | --- |
| Exact result text and root String ID from every successful entry shape | `ApplicationApiValueTest#rootInvocationReturnsTextAndSessionIdentity`, `#rootOperationsRequireExplicitIdentityComponents`, `#handoffAndAdmissionHandleExposeOnlyTheSupportedSingleUseShape`, `DefaultSkillTemplateTest#invocationIdentityMatchesRootWithoutPublicMapping`, and `SupportedSurfaceIntegrationTest` |
| Direct failure getter, nullable pre-session ID, Spring denial catchability | Facade subtype matrix in `#invocationIdentityMatchesRootWithoutPublicMapping`, `#initializationFailureStillExposesAllocatedIdentity`, `#ownershipReleaseFailureStillExposesRootIdentity`, `#finalizationFailureStillExposesIdentityWhenRequestedViewIsUnavailable`, `#preSessionFailureCannotReuseAnotherInvocationsIdentity`, lifecycle/admission integration assertions, and `JavaSkillIntegrationTest#publicRootsEnforceRolesAndDenyAllAndRestoreCallerContext` |
| Observer-independent availability and mapping-failure identity | Mapper zero-interaction assertions in `#invocationIdentityMatchesRootWithoutPublicMapping`, `#mappingFailureAfterSuccessStillExposesRootIdentity`, existing execution-failure suppression tests, and `ExplicitJavaFailureHandlingIntegrationTest` |
| Caller, canonical trace and view agreement across retention policies | `DefaultSkillTemplateTest#callerIdentityMatchesCanonicalTraceAcrossRetentionPolicies` exercises 24 direct/admitted, observer/no-observer, success/failure and persistence-policy combinations. Retained canonical record envelopes and `TRACE_STARTED` data match the caller ID |
| Reused exceptions retain source metadata and current diagnosis selection | `ExecutorOutputSchemaIntegrationTest#publicFailureSnapshotsIgnoreReusedOrApplicationSuppliedDiagnosis` checks distinct concurrent caller IDs against each delivered view and unchanged source metadata |
| Authoritative allocation before initialization | `LoomspanSessionRunnerTest#identityNotificationPrecedesSessionInitialization` and facade initialization fault injection |
| Supported API and existing behavior | `LoomspanPublicSurfaceArchitectureTest`, exact API constructor/value assertions and the complete Maven suite |

## Verification commands and outcomes

Maven 3.9.6 uses Java 21.0.2 through the existing `JAVA_HOME`. Mockito startup-agent loading recovered a Windows dynamic-attachment pipe failure. All successful behavioral suites below explicitly load the existing local Mockito jar.

1. Expected baseline failure, 1 test

   `mvn -q "-Dtest=ApplicationApiValueTest#rootInvocationReturnsTextAndSessionIdentity" test`

   The baseline method returned `String`, while the reflection assertion expected `SkillInvocationResult`.

2. PASS, all production and test callers compile

   `mvn -q -DskipTests test-compile`

3. INTERRUPTED, Mockito dynamic attachment stalled and Surefire recorded a Windows pipe access failure

   `mvn -q "-Dtest=ApplicationApiValueTest,LoomspanPublicSurfaceArchitectureTest,DefaultSkillTemplateTest,LoomspanSessionRunnerTest" test`

4. PASS, 78 tests, zero failures/errors/skips

   `mvn -q "-DargLine=-javaagent:C:/Users/mgiacomi/.m2/repository/org/mockito/mockito-core/5.24.0/mockito-core-5.24.0.jar" "-Dtest=ApplicationApiValueTest,LoomspanPublicSurfaceArchitectureTest,DefaultSkillTemplateTest,LoomspanSessionRunnerTest" test`

5. PASS, 117 tests, zero failures/errors/skips after the nested-root rejection adjustment

   `mvn -q "-DargLine=-javaagent:C:/Users/mgiacomi/.m2/repository/org/mockito/mockito-core/5.24.0/mockito-core-5.24.0.jar" "-Dtest=SupportedSurfaceIntegrationTest,ExplicitJavaFailureHandlingIntegrationTest,ExecutorOutputSchemaIntegrationTest,SkillGenerationExecutionIntegrationTest,SkillReachableAdmissionTest,FrameworkShutdownIntegrationTest,NamespaceObservationIntegrationTest,ConsoleTraceFixtureCorpusTest" test`

   The first run of this command had one failure in 117 tests, exposing nested rejection after identity allocation. The rerun passed after moving the existing precondition before allocation.

6. Initial complete-suite failure, 2582 tests, 3 assertion failures, zero errors, 3 skips

   `mvn -q "-DargLine=-javaagent:C:/Users/mgiacomi/.m2/repository/org/mockito/mockito-core/5.24.0/mockito-core-5.24.0.jar" test`

   The failures were the previous recoverable constructor count, an old public exception identity assertion in executor accounting, and an incorrect new post-session assertion against an entry-time `@DenyAll` fixture. Constructor/snapshot assertions were updated. The Java security test now separately exercises entry denial and an actual Java execution that throws `AccessDeniedException`.

7. PASS, 16 tests, zero failures/errors/skips

   `mvn -q "-DargLine=-javaagent:C:/Users/mgiacomi/.m2/repository/org/mockito/mockito-core/5.24.0/mockito-core-5.24.0.jar" "-Dtest=SkillExecutorTest,ExecutorAccountingIntegrationTest,JavaSkillIntegrationTest" test`

8. PASS final complete-suite run after those test corrections, 2582 tests, zero failures/errors, 3 existing conditional skips

   `mvn -q "-DargLine=-javaagent:C:/Users/mgiacomi/.m2/repository/org/mockito/mockito-core/5.24.0/mockito-core-5.24.0.jar" test`

   The three skips are two opt-in `Pr18RecordedHandoffDiagnosticTest` cases requiring `pr18.diagnostic` and the platform-conditional `StartupSkillPathTest#literalSymlinkKeepsItsLogicalFilename`, whose filesystem assumption could not create symbolic links. The ticket's focused behavioral checks all executed successfully.

9. PASS

   `git diff --check`

Current-source/documentation searches found zero obsolete public String-returning invocation declarations, zero text-only assignments in README/quickstarts/routed references, and zero remaining `CompletionPhaseFailure` references. Internal execution contracts retain their existing role.

## Remaining verification

Step 4 implementation and required verification are complete. Fresh Step 5 review supplies independent pipeline completion evidence.

## Optional developer checks

None.
