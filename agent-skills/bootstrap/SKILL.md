---
name: bootstrap
description: Guide the current agent harness to install explicitly requested Loomspan Agent Skills for the exact Loomspan version declared by a Maven project. Use as a one-shot instruction skill from the official Loomspan repository; ask before replacing an installed skill and never modify the project.
license: Apache-2.0
---

# Loomspan skill bootstrap

Read and follow this skill directly from its official GitHub URL for one
bootstrap run. Do not install `bootstrap` itself.

This is an instruction-only skill. It has no helper program. Never download or
execute bootstrap code, scripts, packages, or generated commands. Use only the
current agent harness's built-in project-reading, repository-access, approval,
and Agent Skill installation capabilities.

The contract is narrow: detect the project's Loomspan version and install only
the requested `loomspan` and/or `loomspan-docs` skills from the matching
Loomspan Git revision. Never edit `pom.xml`, change the framework dependency,
install additional skills, or fall back to another version.

## Detect the project version

Find the nearest `pom.xml` at or above the current project directory. Inspect
the direct dependency
`ai.loomspan:loomspan-spring-boot-starter`.

Accept only these common forms:

- a literal dependency version;
- a single `${property}` reference whose literal value is declared in the same
  POM's `<properties>` section.

Do not attempt Maven inheritance, profiles, BOM resolution, property chains,
module traversal, or effective-POM resolution. If the dependency is absent,
unversioned, unresolved, or ambiguous, do not guess. Use the harness's normal
official-repository access to inspect Loomspan release tags and the root POM on
`main`, show the available exact release sources plus the current development
snapshot, and ask the developer which exact version to use.

## Resolve the exact source

Map the selected version to exactly one Git revision:

- release `X` -> tag `vX`;
- snapshot `X-SNAPSHOT` -> `main`.

Before installation, inspect the root `pom.xml` at that revision and verify its
project version equals the detected or selected version. A release must never
use another tag or branch. A snapshot may use `main` only when `main` declares
that exact snapshot version.

The installable skill source paths are fixed:

| Requested skill | Repository path |
| --- | --- |
| `loomspan` | `loomspan-console/agent-skills/loomspan` |
| `loomspan-docs` | `agent-skills/loomspan-docs` |

Use the official repository `https://github.com/loomspan/loomspan-framework`. Before
changing any installation, verify every requested path exists at the exact
revision and its `SKILL.md` declares the selected version in
`metadata.loomspan-version`. Stop if a requested path or marker is absent or
mismatched. Do not substitute another skill or revision.

## Use the host's native skill installer

Use only the current harness's supported Agent Skill installation or update
operation. Let the harness choose its standard user, workspace, or
repository-scoped skill location. If more than one scope is available and the
developer did not choose one, state the supported choices and ask which scope
to use. Do not invent another agent's directory convention and do not manually
download or execute an installer.

Install only the explicitly requested skill paths from the verified Git
revision. Extra harness metadata that is irrelevant to the current agent may
be ignored; `SKILL.md`, its referenced documentation, and its Loomspan version
marker are the portable contract. The harness may retrieve those requested
skill assets through its native installer; it must not retrieve or execute a
bootstrap helper.

For each requested skill, inspect the destination through the harness before
installation:

1. If the skill is absent, install it.
2. If it exists, read `metadata.loomspan-version` when available and ask before
   replacing it.
3. Replace it only after explicit approval for that skill.
4. If replacement is declined, leave it unchanged and report it as skipped.

Never treat approval for one skill as approval for another. Do not detect local
modifications, merge files, preserve selected files, create backups, or perform
rollback. Never silently overwrite an installation.

Conceptually, ask:

```text
`loomspan-docs` version 1.1.0 is already installed. Replace it with the Loomspan 1.2.0 version?
```

After installation, use the harness to verify that every installed or replaced
skill reports the selected `metadata.loomspan-version`. If the harness requires
a refresh or a new session before discovery, report that requirement using the
host's terminology.

## Stop conditions

Stop without changing installed skills when:

- the framework version is unresolved and the developer has not selected one;
- the exact Git revision is unavailable or declares a different version;
- any requested skill or version marker is absent or mismatched;
- native repository inspection or skill installation fails.

Do not add compatibility ranges, alternate channels, migration, dependency
installation, project setup, or general-purpose skill management.

## Completion report

Report the detected or developer-selected Loomspan version, exact Git source,
installation scope, newly installed skills, replaced skills, and declined
skills. If nothing changed, say so.
