# Code Review: PR 5.3 External Caller API — Cycle 2

## Review Scope

Independently reviewed the current implementation against:

- `ai/thoughts/tickets/loomspan-pr-5.3-external-caller-api.md`
- `ai/thoughts/plans/2026-09-12-loomspan-pr-5.3-external-caller-api.md`
- `ai/thoughts/plans/2026-09-12-loomspan-pr-5.3-external-caller-api-testing.md`
- `ai/thoughts/framework-feature-design-lens.md`
- the supported-surface rules in `AGENTS.md`

The review covered the new catalog API, preflight validation, caller-security capture,
root completion and observer behavior, auto-configuration, public-surface tests,
consumer documentation, and the directly connected runtime paths. Earlier review
artifacts were deliberately not consulted.

## Findings

### [P2] Document the intentional compatibility changes to the public API

- Location: `agent-skills/loomspan-docs/references/java-api/compatibility-and-boundaries.md:35`
- Evidence: the implementation adds two abstract methods to the supported
  `SkillTemplate` interface and expands observer delivery from success-only to
  mappable post-session failures. The existing documentation described the expanded
  surface but did not give consumers migration guidance for either change.
- Trigger: an application upgrades while maintaining a hand-written `SkillTemplate`
  implementation/fake, or an observer assumes it can only receive successful history.
- Impact: implementors can encounter source or binary incompatibility, while observer
  code can mishandle failure data or apply an inconsistent security/retention policy.
- Resolution: added an explicit compatibility section identifying the validation
  methods as an intentional pre-1.0 source- and binary-sensitive change without a
  default-method shim, and documenting the observer behavior expansion and migration
  expectations.

### [P3] Preserve overload-specific preparation order in invocation guidance

- Location: `agent-skills/loomspan-docs/references/java-api/invocation.md:80`
- Evidence: the Object overload rejects null and performs Jackson conversion before
  exact skill lookup, while the Map overload performs lookup before null normalization.
  The prior lifecycle prose presented one common order and therefore misstated which
  error wins for the Object overload.
- Trigger: an invalid Object input and an unknown skill name are supplied together.
- Impact: consumers and maintainers can write incorrect error-precedence expectations
  and tests even though the implementation preserves the ticketed behavior.
- Resolution: split the preparation flow by overload and documented session
  completion, execution-binding restoration, mapping, observer delivery, and failure
  rethrow in their actual order.

### [P3] Lock the exact public catalog signatures in the API value test

- Location: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/api/ApplicationApiValueTest.java:21`
- Evidence: the public API test previously checked record component names and catalog
  method names, but not record component types or the exact generic return types
  required by the ticket.
- Trigger: a future edit retains the same names but changes `SkillDescriptor` field
  types or erases/substitutes `List<SkillDescriptor>` or
  `Optional<SkillDescriptor>`.
- Impact: a compatibility-sensitive public signature regression could pass the
  supported API value test.
- Resolution: asserted the exact component name/type pairs, raw method return types,
  parameters, and generic return signatures.

### [P3] Prove invocation rechecks authorization after successful validation

- Location: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/integration/SupportedSurfaceIntegrationTest.java:143`
- Evidence: unit tests covered invocation authorization, and the supported-surface
  fixture covered authorized validation plus denied validation, but it did not prove
  the required TOCTOU behavior through the public API after the caller's authentication
  changed.
- Trigger: preflight validation succeeds, the security context changes, and the same
  skill is then invoked.
- Impact: a regression that incorrectly treats validation as authorization for a later
  invocation could reach the handler under stale authority.
- Resolution: after successful prechecks, the fixture swaps to an unauthorized
  authentication, asserts the exact unwrapped `AccessDeniedException`, verifies the
  denied invocation produces available post-session `ERROR` history, and proves the
  handler count did not increase for that invocation.

## Re-review Result

No unresolved P0-P3 findings remain after the fixes above. The catalog is an immutable,
deterministically ordered startup snapshot; validation remains execution-free and
callback-free; invocation rechecks role authorization in its root session; public
failure callbacks receive only completed, mappable history; observer/mapping failures
remain suppressed behind the original execution failure; root completion occurs after
binding restoration; and the closed public API allowlist contains only the thirteen
intentional application-facing types.

## Requirements and Plan Conformance

All acceptance criteria and planned test areas are implemented. No partial or missing
requirement was found. The review added only documentation and test coverage within the
approved ticket scope; it did not change production behavior.

The new `SkillCatalog`, `SkillDescriptor`, and `SkillKind` types are deliberate additive
Application API. The two new abstract `SkillTemplate.validate` methods are an
intentional pre-1.0 compatibility break authorized by the ticket, with no shim. The
observer expansion is an intentional behavioral compatibility change. No supported SPI,
internal bean-replacement contract, or public signature exposing `internal` or
`autoconfigure` types was introduced.

## Documentation Assessment

### Skill-Authoring Documentation

- Assessment: No impact.
- Rationale: the change exposes catalog discovery, application-side validation, caller
  security propagation, and completed root observation. It does not change skill
  manifests, models, planning, evidence, authoring semantics, or source-verification
  rules.
- Document reviewed:
  `agent-skills/loomspan-docs/references/skill-authoring/source-verification.md`.
- Drift classification: aligned.

### Java API Documentation

- Assessment: Impact.
- Documents reviewed: `agent-skills/loomspan-docs/references/java-api/README.md`,
  `compatibility-and-boundaries.md`, `catalog-and-validation.md`, `invocation.md`,
  `observation-and-errors.md`, and `java-skills.md`.
- Resolution: corrected compatibility and overload-order gaps during this review.
- Coverage: public catalog and validation API, invocation flow, security context,
  failure observation, supported boundaries, and migration guidance are current.
- LLM-first review: pass; the updated docs state the exact signatures, constraints,
  error order, and compatibility consequences without requiring source inference.
- Drift classification: aligned.

## Verification Results

- PASS — `mvn -pl loomspan-spring-boot-starter '-Dtest=ApplicationApiValueTest,DefaultSkillCatalogTest,DefaultSkillTemplateTest,JavaSkillAuthorizationIntegrationTests,DefaultAccessGuardTest,LoomspanAutoConfigurationTests' test` — 52 tests.
- PASS — `mvn -pl loomspan-spring-boot-starter '-Dtest=LoomspanSessionRunnerTest,FrameworkExecutionLifecycleTest,FrameworkShutdownIntegrationTest,SkillExecutionViewMapperTest' test` — 46 tests.
- PASS — `mvn -pl loomspan-spring-boot-starter '-Dtest=LoomspanPublicSurfaceArchitectureTest,SupportedSurfaceIntegrationTest' test` — 9 tests before the review-authored assertion expansion.
- FAIL — `mvn -pl loomspan-spring-boot-starter -Dtest=ApplicationApiValueTest,DefaultSkillCatalogTest,DefaultSkillTemplateTest,JavaSkillAuthorizationIntegrationTests,DefaultAccessGuardTest,LoomspanAutoConfigurationTests test` — PowerShell parsed the unquoted comma-separated selector and Maven did not start; attributable to command quoting, not the implementation.
- FAIL — `mvn -pl loomspan-spring-boot-starter '-Dtest=ApplicationApiValueTest,SupportedSurfaceIntegrationTest' test` — the first review-authored assertion incorrectly expected no view for an invocation-time authorization failure. The failure confirmed that authorization is rechecked after session creation and completed failure history is available; the assertion was corrected.
- PASS — `mvn -pl loomspan-spring-boot-starter '-Dtest=ApplicationApiValueTest,SupportedSurfaceIntegrationTest' test` — 10 tests after correction.
- PASS — `mvn clean verify` — 1,134 tests, 0 failures, 0 errors, 0 skipped; reactor build successful.
- PASS — `git diff --check` — no whitespace errors; Git emitted only existing line-ending normalization warnings.

## Residual Risks

None identified within the reviewed scope. Optional developer checks: none.

## Disposition

Candidate clean; fresh review required. Finding counts: P0=0, P1=0, P2=1 resolved,
P3=3 resolved.

