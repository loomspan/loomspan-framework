# Beta 4 documentary design review

Current requirements live in the [roadmap](phases/beta4-rest-skills-and-sidecar-roadmap.md)
and phase documents. The [ticket-preparation handoff](beta4-ticket-readiness.md)
assigns delivery/acceptance ownership. This report retains rationale and
historical review evidence; superseded recommendations are not open decisions.

Reviewed the feature design lens, the roadmap, and all nine phase documents
in `phases/`. The initial pass was documentary; the fresh review below
independently verifies selected current-code assumptions.
No feature implementation or tickets belong to this step. The accepted
decisions below are reflected in the current phase contracts. All phases,
including FW1 and FW2, have since been source-grounded at `1e4eb455`;
see [the grounding report](beta4-code-grounding.md) and its G1–G3 decisions.

## Overall assessment

Keep the central design: one deliberately supported handler SPI, reuse of
the direct skill lifecycle, a read-only public catalog, framework-owned
validation, JWT-only inbound identity, one startup-loaded route file,
ordinary bounded workers, and transient owner-scoped execution records.
Do not reopen the agreed exclusions for reload, durable storage, retries,
multiple handlers, or token exchange. Those exclusions remove substantial
complexity without preventing the committed integration.

The remaining gaps concern completeness at existing boundaries, not a need
for more framework abstractions. The documents sometimes prescribe internal
classes, a collection implementation, exact PR splits, or a new script
before reuse has been checked. Treat those as grounding candidates rather
than product requirements. The named behaviors and protected contracts are
the constraints; implementation details must earn their place.

## Simplicity pass — after the R1 decision

Reviewed the roadmap and nine phases again against the feature lens, with
targeted source checks at unchanged `1e4eb455`. The largest remaining savings
are in internal ownership and delivery structure. There is no evidence that
meeting the requirements needs another public API, scheduler, lifecycle
protocol, or configuration abstraction. The developer accepted the
simplifications below; the phase documents now carry them as design
constraints. Existing duplication is distinguished from machinery the
documents could accidentally invite. None is implemented by this update.

### S1 — One root operation through execution and observation

**Evidence:** `LoomspanSessionRunner` duplicates session construction,
binding, exception capture, and completion in its run/call entry methods.
`DefaultSkillTemplate` then maps and delivers the observer after runner
return. Earlier FW1/FW2 wording described coordinating these lifetimes without
requiring a single internal completion boundary. That omission could invite
extra tracking; no second REST engine or observer execution path was needed
or justified. The correction is to simplify the existing operation itself.

**Accepted design:** consolidate the two runner paths around one
internal operation and make completion delivery part of that operation's
lifetime: admit → execute → finalize session → deliver available history →
release ownership. A narrow internal completion callback is sufficient as a
candidate; keep public-view mapping in the facade and invoke the callback
outside the execution binding, preserving the observer's current context.
Preserve success-observer exception propagation and original execution-failure
precedence. Do not count the session and its observer as separately admitted
root jobs or add a handoff registry. Keep one deadline and one root release.

This removes duplicated lifecycle branches and avoids solving the observer
gap with another tracking system. It does not remove mission-level tracking:
nested futures and write fences still need their existing owner. The
lock-safe cutoff work remains necessary and unimplemented.

### S2 — Keep Sidecar admission state in one place

**Evidence:** SC2 requires retained-record capacity, worker capacity, queue
count, and queue bytes to agree during admission, dequeue, rejection, and
shutdown. Sidecar code does not yet exist; duplicate schedulers or counters
are a risk in the specification, not an observed implementation defect.

**Accepted design:** use one executor work queue, one retained-record
store, and one internal owner for the atomic admission/accounting operations.
Extend ordinary executor/queue facilities only where the byte budget needs
it. Avoid a second staging queue plus executor queue, or independent semaphore
and status counters representing the same capacity. Never hold admission
locks while invoking a skill, delivering history, or serializing a response.
The agreed direct-handoff exception and exact cleanup must still be tested;
an unmodified ordinary executor alone does not implement weighted admission.

Keep pending input/authentication in the queued/running task; the retained
record holds its readable status and eventually one terminal outcome with
selected events. Publish that terminal value atomically. This avoids a
second result/history store and reduces reference-clearing branches. For
expiry, prefer one simple sweep of the bounded store over a timer/future per
record or a general cache subsystem; reads must enforce TTL exactly and
admission must reclaim expired capacity. Preserve all settled capacity rules.

### S3 — Avoid an authentication transition between SC2 and SC3

**Evidence:** SC2 previously specified test-only authentication until SC3, repeating
that transitional arrangement in ownership and PR planning. SC3 then wires
the real JWT identity. These are phase boundaries, not a requirement for two
authentication implementations.

**Accepted delivery:** deliver the first usable execution API together
with JWT authentication. Keep SC2/SC3 as responsibility checklists and group
their initial implementation into one coherent delivery unit. Use ordinary test JWTs
and mocks for focused tests; do not create a temporary production auth path,
second owner type, or migration adapter. The current wording does not itself
authorize a production authentication bypass.

This changes implementation grouping, not JWT-only policy, claims, roles,
ownership, or callback verification. The roadmap and both phases now reflect
this grouping; their later focused hardening can still land incrementally.

### S4 — Reuse declaration locations and make one catalog snapshot

**Evidence:** `SkillSource` is already just `(sourcePath, beanName, method)`;
`CapabilityMetadata.kind` owns the execution kind. A REST manifest has the
same declaration-location shape as model-backed YAML. FW3's former wording
could imply enriching `SkillSource` unnecessarily.

Corrected FW1/FW3 and the roadmap to reuse existing locations and project
REST from the capability kind. Required Java/Go/TypeScript DTO validation
and Console rendering still change together; this does not alias REST to YAML.

For FW2, use the existing eager registration-completion pattern used by
`DefaultRegisteredSkillCatalog`, followed by one immutable public snapshot.
No lazy readiness state, refresh mechanism, or retained old snapshot is needed.
Keep the handler independent of that catalog and validate route correspondence
after registration as already specified. Those two startup steps avoid a real
construction cycle; merging them into the handler constructor is not simpler.

### S5 — Reuse proof, not parallel integration harnesses

**Evidence:** FW1, FW2, and FW4 all require public-surface proof, and the
existing `integration/SupportedSurfaceIntegrationTest` already has a local
model stub, YAML parent, Java leaf, and public observer.

Clarified FW4 to extend that fixture as features land and verify cumulative
coverage. Do not build a new harness for each phase. Sidecar still needs its
own application-level proof; framework tests cannot establish servlet/JWT,
route-file, queue, or container wiring. Within Sidecar, reuse a small common
application fixture for those scenarios, with focused tests at each owning
boundary rather than running every negative input through the entire image.
Packaging tests prove packaging and the quick start, not every binder rule.

### Complexity worth retaining

- Request bytes, queued bytes, retained count, and completion TTL control
  different resources. Removing one does not preserve all agreed limits.
- `validate` and execution-time checks serve admission and actual execution
  respectively. Share implementation; do not remove either boundary or add
  a public prepared-request token merely to avoid repeated validation.
- Public catalog and operator catalog expose different information. Reuse
  registration metadata without merging their contracts or exposing internals.
- Deep container copying is required at the public invocation constructor.
  `SkillExecutionEvent`'s copier rejects Resource handles, so it cannot simply
  be reused unchanged. Share only genuinely common internal traversal logic;
  do not invent a configurable copying framework or route inputs through a
  diagnostic event to reuse its constructor.
- JWT-only authentication, three key-source options, the custom Sidecar
  property prefix, and fixed GET/POST binding remain settled. Use standard
  security/client facilities beneath them; this pass does not replace the
  property contract, remove security checks, or add strategy/plugin layers.
- Independent shutdown listeners remove component coupling, but a bounded
  cutoff and keeping required resources alive remain actual requirements.

### Outcome and limits

S1's single internal completion path, S2's single admission owner, S3's
coherent authenticated API delivery, and S4/S5's metadata/catalog/test reuse
are accepted and reflected in the docs. None requires a new public contract
or compatibility shim; changes
to the supported observer's behavior remain the already-approved FW2 change.
Initial SC2/SC3 work is grouped; no review decision remains. No settled
product policy is reopened.

Updated the review, roadmap, grounding report, and affected phase contracts
and acceptance criteria. These are design changes, not claims of completed
features. The simplicity pass re-read source and checked document
links/structure; this acceptance update checked the revised documents only.
Neither reran tests, implemented
Sidecar, or proved the proposed internal lifetime/accounting designs. The
219-test result in the earlier review remains that earlier run's evidence.

## Fresh review — 2026-09-12

Re-read the lens, roadmap, all nine phases, and prior review/grounding against
the unchanged `1e4eb455` checkout. D1–D8 and G1–G3 product policies remain
settled. R1 below records the developer's subsequent simplification and is
resolved. No substantive review decisions remain; implementation and tickets
are not authorized by this documentation update.

### R1 — Independent shutdown listeners — resolved

The developer chose independent, prompt `ContextClosedEvent` listeners:
Sidecar closes admission/dispatch and discards queued work; the framework
closes root admission and starts its monotonic shutdown deadline. Neither
listener waits for executions or calls the other. Their relative order is
irrelevant; the framework gate catches dispatch races. Both filter to the
owning context and opt out of asynchronous event execution.

The framework performs its bounded completion wait in Spring's subsequent
lifecycle-stop stage, before resources needed by active executions stop.
Caller workers and HTTP clients remain usable through completion/cutoff and
are cleaned up afterward. Use ordinary Spring lifecycle phases/dependencies
for resource ownership; Sidecar does not know framework listener priorities.
The framework still owns one deadline including cleanup and observation,
with no Sidecar timer, new public shutdown API, or cross-repository ordering
convention. The earlier recommendation to reserve listener orders is superseded.

Implementation tests must prove both listener orders, preservation of needed
resources, and the full configured framework budget even when Spring's
separate lifecycle-phase timeout is shorter. The standard Spring stages make
this design viable; they do not prove the future application wiring or fix
the lock and observer-lifetime gaps below.

### Required follow-through, without reopening product policy

- **FW1 cutoff must be safe under lock contention.** `MissionLifecycle`
  holds ancestry locks while running write actions; `DefaultExecutionStateService`
  performs trace writes inside those actions. `beginCancellation` also records
  the primary failure under the locks. Its ordinary `lock.lock()` acquisition
  has no deadline. Merely shortening its 250ms grace cannot bound shutdown
  when trace I/O is blocked. Adapt the existing authority for deadline-safe
  signalling/fencing; test a blocked writer and its resumption after cutoff.
  This is necessary to meet G3, not a new shutdown feature or relaxed guarantee.
- **FW1/FW2 completion includes observation.** `DefaultSkillTemplate` maps
  and calls the observer after `callWithNewSession` returns. Releasing all
  shutdown ownership in the runner therefore leaves the callback outside the
  wait and can tear down its caller early. Retain ownership across facade
  mapping/callback completion within the same deadline; no second timer or
  public session handle. FW2 must prove callback cardinality and preserve
  success-observer exception behavior as well as failure precedence.
- **Spring ordering has conditions.** Close events precede lifecycle stop
  and destruction in 7.0.8; synchronous listeners work with the standard
  asynchronous multicaster. An earlier throwing listener can abort event
  delivery, however. Keep bounded destruction fallback and explicitly test
  skipped listener cleanup; do not promise control over arbitrary application
  listeners. See the fresh grounding section for versioned source links.
- **Nested failure behavior is confirmed, not a new decision.** Sequential
  tool failures propagate. Concurrent assigned failures are recorded, siblings
  in the admitted unit finish, outcomes fold, then the first ordered failure
  propagates. SC2 should classify the exception returned by the facade, not
  reconstruct a different failure from event order or give access denial
  invented precedence over another concurrent primary failure.
- **Make existing transport/security promises executable.** SC3/SC4 now
  explicitly test role-prefix wiring, key-source modes, Console coexistence,
  traversal and null/container binding, redirects, streaming size boundaries,
  and per-target client settings. These close acceptance gaps in existing
  decisions; they add no binder, auth mechanism, or sanitization policy.
- **Keep phase boundaries coherent.** FW1's PR count is illustrative, because
  shutdown and coordinated Console contract work are already assigned there.
  FW2 owns observer completion with FW1; SC5 proves actual application wiring.
  Framework release still follows snapshot integration and precedes Sidecar's
  release pin. No release/dependency cycle requires a new phase.

### Simplicity and code health

The handler, catalog, validator reuse, startup-only route file, and transient
store remain the simplest complete design described by the requirements.
No additional public job API, normalized-request DTO, history endpoint,
worker tracker, version script, or shutdown timer is justified.

Reconfirmed the previously identified unused manifest helper chain and
`objectiveFor` argument. Also found `StepLoopMissionExecutionEngine.executeToolAction`
tests two exception types and then throws the identical unwrapped exception
in either branch. Remove that redundant condition if this path is edited;
otherwise leave it as a recorded cleanup candidate, not separate feature work.

### Changes and verification limits

Updated roadmap/review status, corrected SC4's contradictory relative-path
wording, clarified FW1's illustrative PR grouping, and added the acceptance
checks above to FW1/FW2/SC2/SC3/SC4/SC5. The grounding report now separates Spring's
verified event sequence from missing framework cutoff/completion behavior
and R1's resolved independent-listener design. Existing shutdown product
policy remains unchanged; the unnecessary listener-order contract is removed.

Fresh focused Maven verification passed **219 tests**, no failures, errors,
or skips: architecture, facade, input/ref, catalog/registration, mission
lifecycle/cutoff, and step-loop execution. These test existing code. No new
tests or feature code were written. No Sidecar application, new shutdown
implementation, image, release workflow, Console UI/e2e, or full release build
was verified in this pass. Earlier test totals below describe the earlier run.
The subsequent R1 documentation update checked document consistency and
links; it did not rerun Java tests or implement the revised shutdown design.

## Earlier decision status (product choices resolved; source grounding complete)

Source grounding is now complete for the available framework checkout;
future Sidecar integration is not yet executable. G1–G3 supersede unresolved
assumptions below with source evidence. No feature or ticket work has started.

### D1 — Console compatibility and the FW1/FW3 boundary — resolved

The developer agreed to keep loomspan-framework and loomspan-console updates
together when they affect their compatibility contract. Required producers,
consumers, and fixtures change together, including when the first change
lands in FW1. Assess trace and observability REST semantics, including new
kind values, not only JSON shape. Record the marker decision during grounding.
The roadmap, FW1, and FW3 now reflect this; phase/PR boundaries cannot split
required contract updates. This does not require unrelated presentation work
to land in the same change.

### D2 — Payload bounds — resolved after grounding

The developer chose a 1MB incoming JSON limit, enforced before parsing, and
no Sidecar byte cap on final results or selected diagnostic history. Return
produced output intact under the existing record-count/TTL/diagnostics policy.
See G2 in the grounding report. Earlier output/history cap proposals are
superseded; these controls do not promise a total-process memory bound.
### D3 — Data sanitization — resolved

The developer reaffirmed the existing decision: beta 4 adds no data
sanitization. Preserve framework SkillException messages and do not sanitize
arbitrary inputs, results, or diagnostic history. Removed SC3's contradictory
blanket promise that responses/logs contain no sensitive data, and aligned
the roadmap, FW1, SC2, and SC4. Existing framework redaction remains unchanged.
Choosing limited fields for Sidecar-created HTTP errors and not printing
resolved configuration secrets remain the specified message construction;
these do not introduce a sanitization pass or guarantee arbitrary data is safe.

### D4 — Authentication lifetime while queued — resolved

The developer accepted the simplest policy: a
verified request authorizes its accepted execution using the captured
identity and roles, including after queueing; passthrough targets independently
validate the token at callback time. Expiry does not revoke already accepted
local work. No Sidecar expiry recheck at worker handoff or during execution,
token refresh, or continuous revocation mechanism is added. Framework skill
access checks still apply using the captured authentication. The roadmap,
SC2, and SC3 now state this policy explicitly.

### D5 — GET/POST and input binding — resolved

Sidecar v1 supports only GET and POST; other methods fail startup. This
restriction does not apply to application-written framework handlers.
Path variables and GET query values must be non-null strings, numbers, or
booleans. Missing path values, nulls, arrays, and objects fail visibly.
POST bodies preserve schema-permitted nulls and nested values. Inputs must
not change the target or escape its configured base path. The developer
accepted these recommendations; the roadmap and SC4 now state them.
Ground remaining parser, header, media-type, encoding, and startup-validation
details using standard facilities. Do not introduce a generalized binder.
### D6 — History delivery and lifetime — clarification

The developer asked whether history needs another call, how long it lasts,
and whether this is the simplest design. The existing design requires no
separate history call: the same GET used to poll the execution returns the
terminal result/failure and selected events together. There is no additional
history readiness state, endpoint, store, or expiry policy.

History shares the execution record's completion TTL: 15 minutes by default
from success/failure, configurable with completed-ttl. Reads do not extend
it. Restart loses the in-memory record sooner. NEVER (the agreed default)
returns no events, ONERROR includes them for failures, and ALWAYS includes
them for both terminal outcomes. Outcome availability does not depend on
whether history is enabled. The roadmap and SC2 now make this explicit.

Use the existing public observer for final history, including failure; its
callback must not mask the original execution failure. Verify callback
cardinality and success-observer exception behavior during grounding. No
additional diagnostic model or cross-version interchange is required.
Selected events may contain business input data; releasing the original
invocation payload/security context is not sanitization of retained history.
### D7 — SPI description removed; release tooling to ground

The developer agreed to remove RestSkillInvocation.description. The roadmap
and FW1 now carry only skillName and immutable input in that SPI value.
Manifest and catalog descriptions remain useful and unchanged.

A version script means the SC1-proposed build/release helper that checks or
updates coordinated version numbers across files, analogous to the helper
named in the framework documents. It is not part of skill execution.
Recommend using Maven/release facilities unless grounding establishes a
concrete need for a separate Sidecar helper. Determine the need during grounding; do not create an extra product decision or remove required release checks.
PR grouping likewise remains a grounding/planning choice, subject to D1's
accepted framework/Console contract coordination.
### D8 — Asynchronous API, status, defaults, and shutdown — resolved

The developer chose the simpler asynchronous flow without wait. Accepted
POSTs return 202 with an id/Location, and clients poll GET for status and
outcome. Removed the optional wait parameter, max-wait setting, synchronous
completion path, and their acceptance criteria from the roadmap and SC2.
Client disconnect does not cancel accepted work.

POST requires a JSON object holding the input map. Missing, null, malformed,
or non-object bodies are 400; an empty object receives normal skill validation.
The developer also agreed completed records need no invocation inputs or
security context. They retain owner identity, status/timestamps, outcome,
and selected history; worker-held references are released on worker exit.

The developer agreed GET returns 200 when it successfully reads a known, owned execution,
including one whose status is FAILED. An execution failure is represented in
the record. A 500 means Sidecar could not handle the HTTP request itself;
unknown/expired/foreign records remain 404. The roadmap and SC2 now state this distinction.

The agreed defaults are max-retained: 1000, completed-ttl: 15m,
max-concurrent: 32, max-queued: 128, max-queued-input-size: 64MB, and
diagnostics: NEVER. These existing values are confirmed; a separate 1MB incoming JSON limit is also agreed. Final results and selected history have no Sidecar byte cap. G2 records the resolved payload policy.
The revised shutdown decision is immediate Sidecar admission/dispatch stop, 503 for new executions, and queued-work discard. There is no Sidecar drain timer. Already-dispatched work is governed by framework mission timeouts and loomspan.shutdown.timeout, default 30s. See G3 for lifecycle follow-through.
## Documentary corrections from the initial pass

- The initial pass identified the active review, existing phase files, draft
  defaults, and all-phase grounding gate. The subsequent decisions settled
  defaults and source-grounded FW1/FW2; fresh-recheck limits are recorded above.
- Roadmap includes FW1's forbidden `output_schema_max_retries` field and
  distinguishes schema-permitted input from a closed declared-key contract.
- FW1 exception acceptance preserves the explicit `AccessDeniedException`
  exception to wrapping; public-surface lists use named additions rather
  than unverified total type counts.
- FW2 distinguishes shared input preparation and policy evaluation from
  the intentionally different access-check lifecycle of validate/invoke.
- FW4 distinguishes additive API types from actual compatibility breaks.
- SC1 uses the separate management port already required by SC3/SC5.
- SC3 reuses the ownership value already introduced in SC2.
- SC4 accepts the required leading slash while rejecting a route scheme
  or authority; its former “absolute path” rejection contradicted examples.

## Grounding checklist and follow-through

The source pass and focused verification are recorded in
[beta4-code-grounding.md](beta4-code-grounding.md). Its findings cover existing input/schema
and attachment semantics; deep immutability; root/nested access and failure
classification; observer capture and exception precedence; timeout and
interruption behavior; catalog registration ordering; kind projections and
Console protocol consumers; existing diagnostic bounds and redaction; and
public API allowlists and protected consumers. Review Sidecar requirements
against those findings before fixing internal designs or ticket boundaries.
No Java API or SPI additions beyond the deliberate named additions should
emerge accidentally from grounding. Assess documented configuration and
manifest impacts; use no compatibility shim without a protected consumer
and a reason atomic updates are inappropriate.
