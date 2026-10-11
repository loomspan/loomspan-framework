# PR 27.1 — Deliver TypeSafe primitives as callable Loomspan skills

## Outcome

Authors can register Choice, Noul, and Score evaluations as TypeSafe leaf skills,
invoke them through `SkillTemplate`, and compose them as children of normal skills.
Both direct TypeSafe accounts and OpenRouter accounts use System One with the same
authoring contract. This is the parent feature and acceptance ticket; implementation
belongs to the four linked delivery tickets, not a second implementation of this scope.

## Requirements

- Follow the accepted [TypeSafe roadmap](../phases/typesafe-primitives-roadmap.md),
  which owns the contract, and its [acceptance walkthroughs](../phases/typesafe-primitives-examples.md).
  The current request authorizes ticket creation only. Implementation requires a
  subsequent instruction; historical authorization notices in the roadmap do not
  block this ticket conversion.
- Support fixed question names/types, independent mixed batches, and fixed or
  input-supplied criteria. Resolve structured input references once after existing
  input validation, then validate the request before any provider attempt.
- Return the entire provider `answers` object as JSON text using the existing
  String return contract. Retain complete named answers, including empty or extended
  objects. Perform only minimal envelope extraction, never answer validation or repair.
- Use one internal `typesafe` driver and Apache HttpClient 5 transport for both
  routes. Connect existing lifecycle, authorization, retries, quotas, usage,
  cancellation, captured generations, bindings, and observation rather than
  creating replacement subsystems.
- Publish the generated descriptive schema consistently to tools, catalogs,
  bindings, forwarding parents, and Console. Expose `SkillKind.TYPESAFE` and the
  separate Console/MCP `source: TYPESAFE` contract with all affected consumers.
- Preserve canonical inputs, requests, results, and diagnostics under existing
  explicit capture controls. Retain the existing derived-journal projector exception
  only in that journal; do not introduce content classification or rewriting.
- Keep implementation internal. `RestSkillHandler` remains the sole supported Java
  SPI. No typed facade, public envelope, generic provider framework, general template
  engine, schema expansion, stronger binding proofs, new accounting policy, model
  discovery, specialized Console dashboard, or deferred routing/scoring patterns.
- Deliver the simplest complete design for current requirements. Remove replaced
  paths and update affected consumers within the same delivery; no temporary bridges
  or cleanup deferred to make an earlier phase coherent.

## Delivery and dependencies

| Ticket | Review boundary | Responsibility |
| --- | --- | --- |
| [PR 27.2](loomspan-pr-27.2-typesafe-authoring-contract.md) | Phase 1 | New declaration, substitution, request checks, and descriptive schemas |
| [PR 27.3](loomspan-pr-27.3-typesafe-provider-execution.md) | Phase 2 | New System One transport and extraction; existing controls and native trace integration |
| [PR 27.4](loomspan-pr-27.4-typesafe-registered-execution.md) | Phase 3 | Existing registration, direct/child dispatch, bindings, forwarding, and catalog consumers |
| [PR 27.5](loomspan-pr-27.5-typesafe-authoring-guidance.md) | Phase 4 | Supported authoring guidance and complete Console/MCP experience verification |

Integration acceptance proceeds 27.2 → 27.3 → 27.4 → 27.5. Transport fixtures
may be drafted after the request contract is stable. Native trace consumers ship
in 27.3 when traces first appear; kind/source consumers ship in 27.4 when skills
are published. PR 27.5 is not permission to defer these consumers. The separate
OpenRouter chat response-size work referenced as PR 26 is not a dependency.
Sol 6.1 is the intended coding agent for the implementation tickets.

## Acceptance criteria

- [ ] All four delivery tickets satisfy their scoped criteria and the accepted
  roadmap contract, with shared rules having one owner and replaced paths removed.
- [ ] Representative direct and child invocations support all three primitives,
  mixed batches, and dynamic criteria through both route configurations without
  changing skill questions or business input contracts to switch routes.
- [ ] Complete answers pass through; failures, retries, usage precision, cancellation,
  and late writes follow the agreed boundaries. Both routes accept valid responses
  larger than 1 MiB while error capture remains independently bounded.
- [ ] Catalogs, parent bindings/forwarding, Console, and MCP use the same generated
  contract and can read current kind, source, and native trace data when published.
- [ ] Author documentation includes complete setup and direct/nested examples,
  honest dynamic-schema and accounting limits, and no promises of identical judgments.
- [ ] Focused deterministic checks cover shared contracts once and differing route
  or entry-point behavior specifically, without an exhaustive Cartesian product.
  Live-provider evidence, if available, is distinguished from fixtures.
- [ ] Production-type changes pass `LoomspanPublicSurfaceArchitectureTest`; affected
  consumers, README, supported-surface checks, and existing behavior stay coherent.
  Review confirms the stated exclusions and canonical-data fidelity.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** The feature changes external protocols, supported kind identity,
  serialized contracts, and cross-cutting lifecycle integration. This parent tracks
  acceptance; execute implementation through its delivery tickets.
- **Reassessment triggers:** A proposed split must retain complete consumers and
  controls at each boundary; new product semantics require contract resolution.

## Pipeline notes

- The roadmap explicitly permits breaking replacement of obsolete code/contracts
  required by this feature. Do not preserve them with compatibility shims, legacy
  readers, dual formats, or fallback paths. Update affected consumers together and
  deliberately maintain the API allowlist, README, and supported-surface tests.
  This does not authorize unrelated redesign or deletion of user data/history.
