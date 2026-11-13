# PR 30.1.1 — Select and integrate the dependency baseline

## Outcome

Select the exact Boot/Spring AI/SDK/NetworkNT combination. Resolve dependency alignment and mechanical Framework API changes. Record which known adapter fixes the selected artifacts contain or still require. Preserve the existing stream-closure repair.

## Scope and sequencing

**Dependencies:** None.

**Expected build state:** Aim for compilation and startup. Explicitly identified adapter-transition failures may remain for the following tickets.

PRs 30.1.2–30.1.5 restore the provider transition; PRs 30.2.2–30.3.4 restore schema consumers by their owned boundaries; PR 30.4.3 reruns all blocked integrated checks.

Own only platform selection and mechanical compatibility. Provider behavior belongs to PRs 30.1.2–30.1.5; the canonical validator and declaration migration belong to PR 30.2.1. Confirm NetworkNT compatibility without prematurely replacing all consumers.

Execute in the agreed numerical order unless working on an independent prerequisite. Dependencies describe technical prerequisites, not permission to duplicate another ticket's implementation.

## Shared migration constraints

Exactly three chat integrations remain: shared OpenAI Responses for OpenAI, Meta,
OpenRouter and Ollama; native Anthropic; native Gemini. TypeSafe remains a separate
native evaluator. Retain Spring AI ChatClient/tool-loop ownership. No second adapter,
transport, agent loop, capability registry, conversation database, protocol selector,
automatic fallback or invented public API is authorized.

Canonical inputs, prompts, outputs, tool arguments, traces and diagnostics retain
content fidelity. Do not classify sensitivity, scan secrets, mask or sanitize content.
Enforce authentication, authorization and explicit access boundaries. Existing size
limits and explicit truncation are resource controls. Preserve only the existing
ExecutionJournalProjector field-name redaction exception for the derived journal and
SkillTemplate observer; do not broaden it to canonical traces, Console or invoke results.

The supported Java API is the closed allowlist of public top-level ai.loomspan.api
types in LoomspanPublicSurfaceArchitectureTest. Internal public types are implementation
details; autoconfigure signatures are not an SPI. User-visible keys/behavior require
deliberate migration. Do not expose internal/autoconfigure types in public signatures.
Run LoomspanPublicSurfaceArchitectureTest after production type changes; record it as
blocked if intentional intermediate breakage prevents execution and rerun on restoration.

The accepted standard profile is Draft 2020-12 with NetworkNT Jackson 3 (3.0.9 is the
verified starting candidate, not an irrevocable final pin). Complete contracts have
object roots; property trees support ordinary types, explicit nullable type arrays,
scalar enums/descriptions, homogeneous nested arrays, boolean openness, standard formats
and minimum/maximum/minLength/maxLength/minItems/maxItems. Ordinary inputs additionally
support typed maps and unconstrained {} leaves. Native output/planning typed maps,
general unions/composition, references, defaults, arbitrary patterns and other unapproved
features remain excluded. Internally generated false property schemas preserve binding
ownership. Exact names, standard omission/null/openness and no coercion apply. No
automatic all-required conversion, null insertion, closure, wrapping or map reshaping.
Native strict tools are deferred; retain non-strict schemas, local pre-dispatch validation
and authorization. Retain standard local checks even when a provider ignores assertions.

Deferred Java declaration exploration (Optional wrappers, polymorphic inputs,
array-shaped DTOs and delegating creators) is excluded. Do not restore removed probes
or invent their restrictions. The retained map-key, JsonUnwrapped and UUID corrections
remain owned by PR 30.2.2. Native generated attachments are excluded.

No live provider calls, model downloads, external issue/PR submissions or test-suite
implementation are authorized by this ticket. All verification here is deterministic
or local build/startup. The neighboring suite owns later deployed/live acceptance;
missing live evidence is not permission to weaken Framework requirements.

## Acceptance criteria

- [x] The selected reproducible platform resolves with recorded effective Boot, Framework, Micrometer, AI, SDK and Jackson versions; no unsupported assumption that changing only the AI property is sufficient.
- [x] Compilation/startup/regressions distinguish actual platform acceptance from isolated historical probes; each adapter fix has an exact artifact identity or an explicit remaining blocker.
- [x] Every applicable mandatory case in the detailed contract below has executable evidence, or an explicitly identified downstream integration owner and blocked-check record consistent with this ticket's build allowance; no in-scope required behavior is silently deferred.
- [x] Owned fixtures and callers migrate with the behavior; preservation controls and the public-surface check are run when buildable, and all failures/blocked checks are reported accurately.

**Verification emphasis:** Effective dependency tree, compilation, startup, existing regressions and public-surface architecture test. Distinguish platform failures from known pending migration behavior.

## Historical platform evidence and pending fixes

Repository baseline: Java 21, Boot 4.1.0 and Spring AI 2.0.0. Inspected RC2 uses
Boot 4.2.0-RC2, Framework 7.1.0-RC2 and Micrometer 1.18.0-RC2; SDKs are OpenAI 4.80.0,
Anthropic 3.4.0 and Google GenAI 1.73.0. Independent NetworkNT probes passed 20 tests
on each Boot platform (Jackson 3.1.4 versus 3.1.8); these include defect reproductions,
not full Framework compatibility. Prereleases are acceptable during development.
Prefer reproducible releases/milestones; a necessary snapshot must have its resolved
build recorded. Production prerelease policy is a later reassessment.

Track SAI-02 output-config effort replacement (supported local construction exists),
SAI-03 Anthropic tool root loss, SAI-04/05 Gemini raw input/output conversion, SAI-06
Responses decimal decoding, Gemini promptFeedback loss and Responses failed-lifecycle
exception evidence loss. Adapter tickets own fixes and request evidence, not this
ticket. Do not wait for a nonexistent upstream change where a supported native path
already solves the requirement. Do not assume a merged fix ships in the selected pin.

## Detailed accepted contract and evidence

The following bounded extracts carry the applicable decisions, exact acceptance cases
and historical code/test anchors into this ticket. Scope ownership above determines
which cross-boundary statements are implementation here versus preservation context.
Statements about earlier research runs remain historical; do not claim they were rerun.
References to future tickets in retained text mean requirements carried by this ticket
set, not permission to postpone this ticket's owned work. Older proposals do not override
accepted decisions or the explicit exclusions above.

### Contract 1

#### Phase 1 Select and verify the dependency

- **Historical completed evidence:** Inspect published Spring AI 2.1.0-RC2 source artifacts and POMs from Maven
  Central. The adapter exists in `spring-ai-openai`; no new starter is needed.
- [x] Resolve platform alignment before selecting the exact pin: RC2's upstream
  build uses Boot 4.2.0-RC2, Spring Framework 7.1.0-RC2, and Micrometer 1.18.0-RC2,
  while Loomspan pins Boot 4.1.0. Java 21 exceeds the upstream Java 17 baseline.
  Decide a coherent Boot/AI version pair and verify the effective dependency tree,
  compilation, startup, and tests. Do not assume changing only the AI property works.
- [x] Prefer a reproducible milestone or release candidate over a moving snapshot.
  Use a snapshot only if a required fix warrants it and record the exact resolved build.
- [x] Review migration changes to ordered message parts, options, metadata, usage,
  error types, tool execution, and the OpenAI SDK. Run the existing regression suite
  as an upgrade check without expanding the provider design scope.
- [x] Establish the current test baseline and update the dependency in an isolated
  implementation change. Do not combine unrelated framework redesigns with this upgrade.

Completion: a reproducible dependency selection and passing existing checks, with
any newly exposed defects documented separately from intentional API changes.

### Contract 2

#### Dependency selection is a platform decision

RC2 artifacts were successfully downloaded from Maven Central without adding a
snapshot repository. Its published OpenAI module uses `openai-java-core` 4.80.0.
The newer Spring and Micrometer versions above mean Loomspan's Boot dependency
management could select older libraries than RC2 was built against. This is a
compatibility question, not a proven failure. First produce an effective dependency
tree for the proposed pair and test it; prefer coherent platform alignment over a
collection of individual transitive overrides.

The 2.1 getting-started page still contains 2.0 version examples, and the Responses
page contains a memory-support note referring to an earlier milestone. Use the
published RC2 source and resolved dependencies as the executable evidence rather
than treating every versioned documentation paragraph as an RC2 guarantee.

## Retained technical context

Historical research establishes the stated adapter/library behavior and limitations.
Broader historical fixtures do not expand the accepted v1 profile.

#### Adapter and SDK evidence

The repository pins Java 21, Boot 4.1.0, Spring AI 2.0.0, and Jackson 3. The roadmap's
candidate is 2.1.0-RC2, not an accepted upgrade. Published module POMs/source JARs show:

| Integration | Spring AI 2.0.0 SDK dependency | Spring AI 2.1.0-RC2 SDK dependency |
| --- | --- | --- |
| OpenAI | `openai-java-core:4.39.1` | `openai-java-core:4.80.0` |
| Anthropic | `anthropic-java-core:2.40.1` | `anthropic-java-core:3.4.0` |
| Gemini | `google-genai:1.58.0` | `google-genai:1.73.0` |

These are **published dependencies**, not a newly resolved Loomspan effective tree.
Boot overrides/transitives still require Phase 1 verification. Source inspection of
2.0.0 confirms the Anthropic root-field loss and Gemini typed-schema routes already
exist there; the RC2 bump alone does not fix them.

#### Executable verification follow-up — November 10, 2026

**At the time of verification no recommendation had been accepted.** The user has
since accepted the draft/library foundation and resolved the v1 policies recorded above. The isolated
[probe and handoff](../../../docs/maintainers/probes/tbd-06/README.md) now supply
reproducible code, commands, dependency trees, test reports, exception evidence,
and captured loopback HTTP requests. This updates the earlier source-only evidence;
it does not implement the migration or establish hosted-provider enforcement.

**Verified local behavior:** NetworkNT 3.0.9 resolves, compiles and passes the
representative validator fixtures on Java 21.0.2 with both Boot 4.2.0-RC2 / Jackson
3.1.8 and Boot 4.1.0 / Jackson 3.1.4. Both complete probe runs report **20 tests,
zero failures/errors/skips**, including assertions reproducing adapter defects.
Spring AI is 2.1.0-RC2; SDKs are OpenAI 4.80.0, Anthropic 3.4.0 and Google GenAI
1.73.0. Full resolved trees, including SDK Jackson 2 dependencies, are retained.
These are independent BOM-managed probes, not the framework's effective tree or
Boot startup acceptance.

Validator fixtures cover 2020-12/meta-schema checks, required/omitted/null,
standard object openness and typed maps, exact property names, precise numbers and
integer `1.0`, mixed enums, nested arrays, composition, formats, unchanged values,
diagnostics and bounded evaluation failures. Precision tests use Jackson trees
parsed with `USE_BIG_DECIMAL_FOR_FLOATS`. Retained probe snapshots include broader
historical tests; they do not define the narrowed implementation scope.

**Configuration surprises and remaining validator gaps:** meta-schema checking is
separate from compilation. Use bundled standard meta-schemas without enabling
external retrieval. JDK and optional Joni 2.2.6 pass the
representative identifier/search/Unicode fixtures; Java `\Q...\E` works only in
the JDK engine. Neither full ECMA-262 equivalence nor a regex wall-clock bound is
established. Execution limits classify exhaustion distinctly and default to unlimited;
the probe thresholds are fixtures, not proposed product restrictions.

## Reproduction anchors

Historical standalone validator/platform reproduction (Java 21 and Maven):

```powershell
./docs/maintainers/probes/tbd-06/run.ps1
./docs/maintainers/probes/tbd-06/run.ps1 -BootVersion 4.1.0
```

These probe results do not replace the complete Framework effective tree/startup gate.

## Pipeline notes

- Destructive updates are encouraged: this repository is in development with no
  deployments to support. Remove superseded implementations in place; do not add
  legacy readers, aliases, dormant paths, compatibility shims or fallback solely to
  keep intermediate commits building. Respect the supported public API boundary above.
- The developer commits between tickets without syncing. The stated intermediate
  build failures are authorized; identify each affected consumer/test and restoration
  owner. Do not mark blocked tests passed, remove assertions or skip required checks
  into success. Final integrated acceptance requires green checks.
- Preserve unrelated worktree changes and retained probes. Resource stream closure
  in SkillMethodBeanPostProcessor is already repaired, with success/failure/nested
  tests; preserve it rather than treating it as missing migration implementation.
- Source anchors and retained reports below are historical navigation/evidence, not
  fresh passing-test claims. Verify hints against the current checkout. Before claiming
  a new defect, trace callers, validation, execution and tests against the accepted
  requirement. Routine implementation choices are autonomous; broader product or
  architecture departures require a concrete evidence-backed question.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** This ticket changes or provides outstanding assurance for a cross-cutting migration involving serialized contracts, external protocols or execution lifecycle behavior. Settled intent does not eliminate integration research, test planning or independent review.
- **Reassessment triggers:** A materially changed scope or checkout should be reassessed under the shared protocol; this recommendation does not waive any required review or verification.

## Reference navigation

- [Agreed overview](../phases/migration-ticket-overview.md)
- [Coverage ledger](../phases/migration-ticket-coverage.md)
- [Migration roadmap](../phases/spring-ai-responses-migration.md)
- [Historical schema research](../phases/standard-json-schema-research.md)
- [TBD-06 reproduction and retained evidence](../../../docs/maintainers/probes/tbd-06/README.md)


## Execution notes

Step 4 selected Boot 4.2.0-RC2 / AI 2.1.0-RC2 / NetworkNT 3.0.9. Root effective versions, exact dependency paths, adapter-fix ledger, commands and per-check evidence are in `../plans/2026-11-13-pr-30.1.1-dependency-baseline.md` (Execution Record). Mechanical preservation explicitly disables RC2's new per-tool/total caps to retain Loomspan limit ownership and retains x-api-key through existing Anthropic custom headers after SDK 3.4.0 changed static auth to Bearer. No API, configuration or provider-design expansion.

Acceptance uses the authorized intermediate-build allowance: full suite ran 1,837 tests with two failures and two existing skips; final preservation checks passed 125/125 after the owned auth correction, architecture 8/8, unchanged independent NetworkNT probe 26/26. `ExecutionTraceContractTest.structuredChatCapturePreservesOrderNullsEmptyValuesAndExactContent` remains an executed failure from selected ordered-message text empty versus prior null; restoration owner **30.1.2**, integrated rerun owner **30.4.3**. Exact command/cause/rerun gates are in the plan. Checkmarks record platform-owned completion plus explicit downstream ownership, not integrated green or provider/schema migration acceptance. All other named future adapter cases retain the plan's pending acceptance ledger. Fresh Step 5 review remains required.
