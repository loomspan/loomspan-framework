# Phase SC1 — Sidecar repository scaffold

Part of [beta 4 roadmap](beta4-rest-skills-and-sidecar-roadmap.md). Starts
after framework implementation, before its release. Framework assumptions
grounded at `1e4eb455`; see [SC1 evidence](../beta4-code-grounding.md#sc1--platform-and-startup-assumptions).
Sidecar application verification remains future work. The developer-created
checkout is `C:/opendev/code/loomspan-sidecar`, with origin
`https://github.com/loomspan/loomspan-sidecar.git`; it currently contains only
README/license files. This phase is the seed for that repo's own `ai/thoughts`
process, transferred during workflow seeding as recorded in the handoff.

## Goal

A buildable, testable Spring Boot 4 / Java 21 application depending on the
locally installed `ai.loomspan:loomspan-spring-boot-starter:1.0.0-beta.4-SNAPSHOT`, with the guardrails that
make every later Sidecar phase safe: public-API-only enforcement,
configuration conventions, CI, and version coordination.

## In scope

- Maven project (wrapper included) with the starter dependency pinned to the
  development snapshot; Spring Boot web (servlet) and Spring Security.
- Align initially with Java 21, Boot 4.1.0, Spring Framework 7.0.8, and
  Security 7.1.0 from the framework POM/BOM. Declare Sidecar's web, JWT/JOSE,
  Actuator, and HTTP-client integration dependencies explicitly. Use Boot's
  Jackson 3 mapper (`tools.jackson`), not internal Loomspan codec beans.
- ArchUnit test forbidding any import of `ai.loomspan.internal..` and
  `ai.loomspan.autoconfigure..` from Sidecar code and tests.
- `application.yml` conventions: framework keys under `loomspan.*` only as
  documented; Sidecar keys under `loomspan-sidecar.*`; secrets via
  environment variables.
- Default mount layout: `/sidecar/skills/` for skill manifests and
  `/sidecar/rest-routes.yaml` for Sidecar's targets and route mappings. Framework skill
  scanning is restricted to the skills subdirectory; route loading is SC4.
- Default location configuration (overridable):

  ```yaml
  loomspan:
    skills:
      locations:
        - file:/sidecar/skills/**/*.yaml
        - file:/sidecar/skills/**/*.yml
  loomspan-sidecar:
    rest-routes-location: file:/sidecar/rest-routes.yaml
  ```
- Health/readiness via Actuator on a separate management port, as required
  by SC3 and SC5. SC5 verifies the packaged deployment and shutdown behavior.
- CI: build and test; a job that builds the framework at a pinned commit and
  `mvn install`s it before building Sidecar against the snapshot.
- Maven/release checks for Sidecar's own version and the recorded framework
  version pin. Do not clone the framework version script unless Sidecar
  actually acquires multiple version-coupled artifacts needing it.
- `AGENTS.md` for the Sidecar repo carrying the public-API-only rule, the
  prefix rule, and the framework-first dependency policy.
- Seed the existing `ai/commands` workflow, including its referenced shared
  resources, and `ai/thoughts` layout using the bootstrap handoff. The scaffold
  ticket then runs from the Sidecar checkout; do not clone historical
  framework tickets/reports or introduce another development pipeline.

## Binding decisions

- Sidecar never implements or overrides framework internals; if a gap is
  found, it is a framework issue.
- Sidecar hosts no Java `@SkillMethod` skills.
- Skills and routes load only at startup in the initial release. Disk edits
  require a restart. Reuse framework startup loading; no watcher, reload
  operation, or framework scanner exception for `rest-routes.yaml`.
- Ordinary beta 4 development uses the locally installed framework snapshot.
  Re-run framework `mvn install` after framework changes; record the framework
  commit used by CI. No snapshot repository is introduced. SC5 switches to
  the released starter after integration is verified and the framework is
  published.

## Acceptance criteria

- [ ] After building and installing the recorded framework commit,
  `mvn verify` passes on a clean Sidecar clone using the snapshot dependency.
- [ ] ArchUnit test fails on an internal import (proved by a deliberately
  failing example, then removed).
- [ ] A YAML skill in the mounted directory registers at startup with its
  required model/connection configuration. Mounting a planner manifest alone
  does not configure its model.
- [ ] Default scanning includes skills under `/sidecar/skills/` and excludes
  `/sidecar/rest-routes.yaml`; custom skill locations remain supported.
- [ ] CI runs on push and pull request.
- [ ] Release checks validate the Maven version and framework dependency pin.

## Ticket boundary

The Sidecar scaffold unit in the [planning handoff](../beta4-ticket-readiness.md)
owns the buildable application, dependency/architecture checks, mounts,
health and CI. Its ticket lives in
`C:\opendev\code\loomspan-sidecar\ai\thoughts\tickets`, as do all later
Sidecar tickets, after minimal local
repository/workflow seeding; the handoff records that prerequisite. No
Sidecar application implementation belongs in the framework repository.

