# Phase FW3 — Observability and Console for REST skills

Part of [beta 4 roadmap](beta4-rest-skills-and-sidecar-roadmap.md). Depends
on FW1. Source-grounded at `1e4eb455`; see
[evidence and contract owners](../beta4-code-grounding.md#fw3--contract-changes-begin-with-fw1).

## Goal

Operators see REST skills as a third source kind everywhere they already see
YAML and Java skills: the observability REST API, its fixtures, and the
Console (Go + React). Traces of REST executions are diagnosable.

## In scope

- Observability skill list/detail DTOs carry the REST kind and its
  declaration source (YAML resource path, and that it is handled by the
  application's `RestSkillHandler`).
- Project `CapabilityMetadata.kind` as REST while reusing `SkillSource`'s
  existing YAML declaration location. `SkillSource` describes location, not
  execution kind; no new REST source subtype or duplicate kind field is needed.
- Deterministic fixtures: `skill-rest-detail.json` and any list-page fixture
  changes under `loomspan-console-fixtures/application-rest/`.
- Console: skill list and detail rendering for the REST kind; any trace
  analysis that switches on kind handles REST like Java (direct invocation,
  no model attempts).
- Assess `consoleCompatibilityVersion` for trace and observability REST
  contract changes, including semantic changes and new kind values, not only
  JSON shape. Record the bump/no-bump rationale where the first contract
  change lands, including FW1 if applicable. Same-version-only compatibility
  policy applies; no legacy readers.

## Out of scope

- Displaying handler internals (URLs, targets) — the framework does not know
  them.
- Any Sidecar-specific Console features.

## Binding decisions

- Console labels the kind "REST" and shows the YAML resource path; details
  show the manifest text as for YAML skills.
- Share internal/public kind translation where appropriate. Keep source-
  specific observability validation at its owner; it is a different projection
  from FW2's consumer catalog. REST requires Java and Go validator changes,
  TypeScript union changes, and rendering changes together with FW1.
- Trace records for REST executions use the existing direct-invocation frame
  semantics; if a frame or record carries a kind, REST is a distinct value,
  not aliased to Java or YAML.
- `ConsoleRestFixtureCorpusTest` owns `application-rest` fixtures. Use its
  existing `-Dloomspan.console.fixtures.regenerate=true` workflow; repeat
  to verify no diff. `ConsoleTraceFixtureCorpusTest` is separate and is needed
  only for changes to trace/analysis fixtures.
- The compatibility marker is currently `${project.version}`, not an
  independently incremented schema number. Use coordinated version tooling
  and the beta 4 release pin; record REST's protocol change explicitly.
  Current mission frames have no dedicated capability-kind field. Do not add
  one merely to label the catalog or infer trace kind from a current catalog.

## Acceptance criteria

- [ ] Observability `skills` and `skills/{name}` responses for a REST skill
  report the REST kind and YAML resource path.
- [ ] Fixtures regenerate deterministically and Go tests consume them.
- [ ] Console lists and details a REST skill, and shows a REST execution
  trace without error or misclassification.
- [ ] The compatibility-marker decision accounts for protocol semantics as
  well as shape, and framework/Console producers, consumers, and fixtures
  affected by a compatibility-contract change are updated together.
- [ ] Console CI (Go, React, e2e) and starter tests pass.

## Ticket boundary

FW3's requirements are included in the REST skills and Console unit from the
[planning handoff](../beta4-ticket-readiness.md). Keep this phase as its
acceptance checklist; no separate ticket is needed for already-completed
contract, fixture or UI work. Changes and proof land together with REST.

## Grounded source anchors

- `ObservabilityDtos`, `ObservabilityDtoMapper`, `DefaultRegisteredSkillCatalog`
  and `RegisteredSkillEntry` for how YAML vs Java is currently distinguished.
- Current mission frames carry no dedicated capability kind. The marker
  comes from filtered Maven release metadata; Console validates it exactly.
- `loomspan-console/AGENTS.md` for Console conventions, build, and test
  commands.

## Pipeline notes for the tickets

- If a `consoleCompatibilityVersion` bump is needed, it is expected; update
  all current consumers together, no backward compatibility.
