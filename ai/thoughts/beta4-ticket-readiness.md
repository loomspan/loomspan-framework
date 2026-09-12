# Beta 4 ticket preparation

This completes the planning pass requested before ticket creation. It assigns
ownership and coherent delivery boundaries, not PR numbers, tickets, source
changes, or detailed implementation/test plans. No pipeline has been started.

## Authority and settled intent

The [roadmap](phases/beta4-rest-skills-and-sidecar-roadmap.md) owns product
scope, exclusions, defaults, and release order. The nine linked phase documents
own their detailed requirements and acceptance criteria. This document owns
ticket grouping and acceptance ownership; it replaces their earlier illustrative
PR splits. The [review](beta4-design-review.md) records accepted D/G/R/S
decisions and why they were made. The [grounding report](beta4-code-grounding.md)
is evidence about `1e4eb455`, not proof of future behavior or a second design
authority. Earlier superseded proposals in those reports are historical.

Requirements and accepted constraints travel into each future ticket so it is
self-contained. Internal source suggestions remain open to refinement in the
pipeline when the simpler implementation preserves those constraints. Do not
copy the reports wholesale, create another decision ledger, or require a new
approval pass for ordinary implementation choices. A concrete contradiction
or newly discovered unmet requirement is grounds to ask the developer.

The accepted destination is one root completion path through observation;
independent shutdown listeners and a later framework-owned bounded wait; one
Sidecar admission owner/queue/store; REST through the direct skill lifecycle;
one eager public catalog snapshot; startup-only routes; and the first usable
execution API delivered with JWT authentication. Reuse existing declaration
metadata and integration fixtures. No alternate engine, observer registry,
temporary production auth path, legacy reader, or second timer is planned.

## Coherent ticket boundaries

Names below are planning labels, not reserved ticket or PR identifiers.
Each unit includes its own required tests, documentation, and cleanup before
it lands. A later hardening ticket cannot make an incomplete contract safe.

| Unit | Owning phase(s) | Complete outcome and dependency |
| --- | --- | --- |
| Framework lifecycle | FW1 | Single internal root operation, admission gate, `loomspan.shutdown.timeout`, lock-safe cutoff, bounded executor cleanup, and lifetime through existing success observation. No REST dependency. Preserve current observer behavior; failure-history expansion is FW2. |
| REST skills and Console | FW1 + FW3 | After framework lifecycle: manifest/registration/SPI/direct execution, immutable handoff, root/nested authorization and success credit, failure semantics, and all required Java/Go/TypeScript/fixture/rendering changes together. Record the compatibility-marker rationale here. No unsupported third kind between merges. |
| External caller API | FW2 | After REST: eager public catalog, both `validate` overloads, and failure observation within the same root operation. Add the new observer and pre-check/shutdown proof here, without another execution or tracking path. |
| Framework documentation and readiness | FW4 | After framework features: complete human/agent guidance, compatibility notes, cumulative public-surface coverage, and release preparation. Final release checks wait for Sidecar snapshot integration; this is not permission to tag early. |
| Sidecar scaffold | SC1 | After the framework development snapshot is ready: buildable application/repository conventions, public-API-only guard, mount loading, health, CI and dependency pin. See bootstrap prerequisites below. |
| Authenticated execution API | SC2 + SC3 | After scaffold: JWT verification and ownership, catalog/execution endpoints, bounded admission/store, diagnostics and context propagation delivered together. Include the independent Sidecar shutdown gate and accounting cleanup here; SC5 proves it in the packaged application. |
| Generic REST handler | SC4 | After authenticated API: one route file and handler, all fixed binding/auth modes, startup correspondence validation, bounded response handling, TLS/timeouts, and host callback verification. Avoid merging a handler skeleton without its security/transport bounds. |
| Sidecar packaging and release | SC5 | After handler: image, probes, full lifecycle/resource integration, runnable quick start, documentation, and release workflow. Prove integration against the snapshot, release framework first, pin and verify the released dependency, then release Sidecar. |

FW3 remains a useful acceptance checklist; there is no need to create a
separate ticket if its entire outcome lands with REST. FW4 likewise verifies
coverage added with FW1/FW2 rather than commissioning a duplicate harness.
Source research may justify splitting a unit, but each resulting merge must
remain coherent and preserve the accepted atomic contract/API/auth boundaries.

## Acceptance ownership

All current phase checkboxes are assigned below. The counts are an inventory
at this planning pass, not new criterion IDs or a replacement specification.
The linked phase checklist remains the requirement text.

| Phase checklist | Count | Primary owner and follow-through |
| --- | ---: | --- |
| [FW1](phases/phase-fw1.md#acceptance-criteria) | 20 | Framework lifecycle owns the first seven root/shutdown criteria. REST skills and Console owns the remaining REST/SPI/catalog/immutability criteria. The architecture/full-starter check applies to both units. References to future `validate` and failure observation are extended by FW2; actual Sidecar listeners/resources are proved in SC5. |
| [FW2](phases/phase-fw2.md#acceptance-criteria) | 11 | External caller API owns all catalog/pre-check/observer criteria, including both null-overload rules, callback cardinality/context/exception precedence, and overlap with the existing root shutdown lifecycle. |
| [FW3](phases/phase-fw3.md#acceptance-criteria) | 5 | REST skills and Console owns all operator DTO, deterministic fixture, UI/trace rendering, compatibility-marker, and cross-language verification criteria. |
| [FW4](phases/phase-fw4.md#acceptance-criteria) | 5 | Framework documentation/readiness owns all documentation/version and cumulative public-surface checks. Feature units supply the actual feature tests. Final release-commit/workflow verification occurs after the SC5 snapshot integration gate and before framework publication. |
| [SC1](phases/phase-sc1.md#acceptance-criteria) | 6 | Sidecar scaffold owns all build, architecture, mount/model configuration, CI, and pin checks. |
| [SC2](phases/phase-sc2.md#acceptance-criteria) | 21 | Authenticated execution API owns every admission/accounting, HTTP/outcome, ownership/expiry, diagnostic mode, payload and queued-auth criterion. Its shutdown gate has local application proof here; SC5 adds packaged lifecycle/resource proof. |
| [SC3](phases/phase-sc3.md#acceptance-criteria) | 7 | Authenticated execution API owns every decoder/key mode, claim/role/prefix, namespace security, JWT identity propagation and error-envelope criterion. Use a test handler here; SC4 proves the real outbound callback. |
| [SC4](phases/phase-sc4.md#acceptance-criteria) | 16 | Generic REST handler owns every file/startup, binding, transport, response, auth, TLS, restart and callback criterion. |
| [SC5](phases/phase-sc5.md#acceptance-criteria) | 10 | Sidecar packaging/release owns all packaged lifecycle, probe, quick-start, configuration reference and release-pin/image/archive criteria, plus snapshot and released-dependency integration gates. |

Total: **101 existing checkboxes**. Cross-phase extensions above add evidence
where new components become available; they do not move the owning feature's
correctness to a later test-only ticket. The framework lifecycle ticket can
finish without the not-yet-existing public `validate` method or Sidecar, but
must already enforce admission independently and retain current observation.

Distinguish evidence in tickets and reviews:

- **Existing behavior:** source anchors and the earlier 219-test run are
  regression evidence for the current checkout only. They do not establish
  REST, the new catalog/pre-check, failure observation, or bounded shutdown.
- **New framework proof:** the relevant feature unit tests the changed
  behavior, reusing current facade, lifecycle, registrar and public-surface
  fixtures. Run architecture checks after production-type changes and the
  phase-required suites; exact commands/fixtures belong to pipeline research.
- **New Sidecar proof:** its application tests establish servlet/JWT,
  queue/store and route wiring; SC5 establishes image/resource shutdown and
  release behavior. Reuse one application fixture, with focused negative tests
  at each boundary. No external service account is needed for local stubs.

## Applying the feature design lens

The [lens](framework-feature-design-lens.md) argues for the following concrete
tradeoffs, rather than treating fewer files or tests as inherently simpler.

| Concern | Choice, classification, and impact |
| --- | --- |
| Why framework work? | The framework owns skill contracts, authorization, nesting, trace facts, and execution lifetime even with a capable model. A handler adapts existing services while a language-neutral HTTP host avoids requiring every caller to embed Java. No framework change would leave per-endpoint Java adapters and private integration plumbing. |
| Author/caller experience | Authors declare ordinary skill inputs and a REST leaf; callers keep the normal invocation contract. Route configuration remains handler-owned, an accepted local-visibility tradeoff. No implicit business-input inheritance; identity remains trusted scoped authentication. |
| Public Java API/SPI | Add only the two FW1 SPI types and the three FW2 catalog types, the two `validate` overloads, and the observer behavior already agreed. Allowlist, README and public-surface proof land with them. No public internal/autoconfigure signatures or new bean-replacement contract. Supported `SkillTemplate` changes are intentional pre-1.0 changes with no shim, as settled in FW2. |
| Manifest/configuration | REST manifest validation, handler requirement and the new framework shutdown setting are deliberate supported contracts. Existing non-REST validation stays intact. New root rejection during shutdown and failure-observer callbacks are intentional behavior changes and must be documented. Sidecar keys and its route file are its own new contracts; no aliases for superseded unimplemented proposals. |
| Diagnostic protocol | REST expands Console protocol semantics. Update current producers, consumers and fixtures atomically; retain redaction and failure fidelity. The marker derives from the coordinated project version, not a new schema counter. Record the beta 4 marker/version rationale at the first protocol change. No legacy readers, historical catalogs or cross-version compatibility fixtures. |
| State and ownership | Immutable invocation containers include nested maps/lists; Resource contents are not promised immutable. One root lifecycle and one Sidecar admission owner reuse existing responsibilities. Sidecar status/result retention is transient, bounded by the agreed count/TTL policy; it is not a durable-history format or total-heap bound. |
| Compatibility and cleanup | Internal signatures have no compatibility promise; consolidate runner duplication, remove the unused manifest helper chain, and correct the registry Javadoc with owning changes. Remove the unused router argument/redundant exception branch if editing those paths. Update affected callers/tests atomically, without unrelated cleanup tickets or adapters preserving obsolete internals. |
| Boundaries worth keeping | Separate pre-check/execution authorization, distinct payload/count limits, lock-safe cutoff, independent shutdown gates, operator/public catalog projections, and startup route correspondence solve different correctness problems. Do not remove them to reduce code size. Preserve all settled exclusions, including reload, retries, token exchange, extra handlers and output/history byte caps. |

Exact helper signatures, executor/queue composition, Spring phases, URI
builder calls, decoder composition, fixture placement and verification commands
remain pipeline research/planning work. They must earn their complexity against
existing code. Do not require a generalized abstraction or extra test harness
merely because this handoff names several responsibilities.

## Repository and bootstrap prerequisites

During this pass the developer created `C:/opendev/code/loomspan-sidecar`.
Read-only inspection confirmed a clean checkout containing `README.md` and
`LICENSE`, with origin `https://github.com/loomspan/loomspan-sidecar.git`.
There is no `AGENTS.md`, `ai` workflow, Maven scaffold or application yet.
The repository destination is therefore resolved. Workflow seeding remains
the local prerequisite for its scaffold ticket. Remote access/visibility,
publishing credentials and image registry were not verified; these are
CI/release provisioning inputs, not reasons to invent destinations or block
framework tickets. The repository was created by the developer, not this pass.

Git reported that this checkout belongs to `BUILTIN/Administrators`. Its
status/remote reads succeeded with a per-command `safe.directory` exception
for the exact user-supplied path; no global Git trust setting was changed.
Use that narrowly scoped approach or correct folder ownership when working
there, rather than trusting every repository.

Use this bootstrap handoff when proceeding to Sidecar ticket preparation:

The developer explicitly selected
`C:\opendev\code\loomspan-sidecar\ai\thoughts\tickets` for **all Sidecar
tickets**, including SC1. Do not stage or create them in the framework's
ticket directory. This records the destination; it does not create tickets now.

1. Framework feature tickets live in this repository's `ai/thoughts/tickets/`.
   Number them at creation using [write_ticket.md](../commands/write_ticket.md),
   never from the planning labels above.
2. Seed the existing Sidecar checkout with its `AGENTS.md`, the command
   suite including referenced `ai/commands/shared/` resources, the design lens,
   and the SC phase documents needed for the agreed development workflow.
   Repair relative links to the pinned framework design/evidence during that
   transfer; the Sidecar copies then become authoritative for its phases.
   Keep links from the framework roadmap rather than maintaining two editable
   Sidecar specifications. Do not copy framework tickets or completed reports
   as Sidecar implementation history.
3. Create the SC1 scaffold ticket in
   `C:\opendev\code\loomspan-sidecar\ai\thoughts\tickets` and run its pipeline
   from the Sidecar checkout. Minimal
   repository/workflow seeding is the bootstrap prerequisite, not an excuse
   to implement the application scaffold outside SC1. Its fresh context must
   carry the public-API-only rule, configuration ownership and local snapshot
   strategy. Verify remote access when needed for CI; do not pretend
   CI/publishing acceptance can pass in an unprovisioned repository.
4. Subsequent SC tickets live and run there too. Use that repository's own
   PR numbering. Record the actual framework commit used for `mvn install`
   and Sidecar CI after framework implementation; the current grounding hash
   does not contain the new APIs and must not be mistaken for a usable pin.
5. After snapshot integration, run final framework release checks and publish
   it first. Verify Sidecar against the released artifact before its own tag
   and image/archive publication. Release credentials and registry destination
   are required then; no publication or remote access is claimed now.

The bootstrap destination is explicit without creating tickets in the wrong
repository or guessing a GitHub organization. If the new
repository has no numbered tickets/PRs, follow the ticket command's lookup
rule when it is provisioned; do not reserve a guessed number now.

## Readiness result

Design intent, compatibility posture, delivery boundaries, acceptance owners,
evidence limits and repository handoff are recorded. Framework tickets can be
created next when requested. Sidecar ticket creation follows minimal workflow
seeding in its now-existing checkout. No additional broad design review or feature
implementation is a prerequisite. This pass checked document structure,
links and acceptance coverage; it did not rerun tests or release checks.
