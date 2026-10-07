# README and documentation audit

Date: 2026-10-04
Audience: framework owner and documentation maintainers
Status: first editorial pass; open technical questions are explicitly marked below

## Scope and placement decision

Reviewed the original root README, the empty `docs/` directory, Agent Skill entrypoints
and indexes, relevant application references, Console runtime setup, and focused
configuration/API source and tests. This is not an exhaustive technical audit of all
skill references or a verification of external release availability.

The README now helps a human evaluate Loomspan and hand off to their assistant.
Docs explain architecture, choices, setup, and upgrades. Skills retain ongoing
application-development and troubleshooting workflows and contracts. Framework
maintenance is a separate audience.

The user explicitly requested `ai/throughts`; this audit uses that spelling despite
the existing `ai/thoughts` directory. Parked material is in
[readme-maintainer-material.md](readme-maintainer-material.md), pending a keep/move/delete decision.
It is not a newly endorsed release runbook.

## Findings and disposition

| Finding | Evidence and significance | Disposition |
| --- | --- | --- |
| Reader journey was dominated by reference detail | Original README: about 7,300 words, 722 lines; invocation began at line 160 and first YAML declaration at line 471. | Replaced with a short overview, AI handoff, small example, and routes to deeper material. |
| Getting Started was not a complete first-use path | Invocation used `duplicateInvoiceChecker`; its later manifest required `invoiceParser`, which the README never defined. | Replaced with a self-contained one-skill quickstart, including application POM, entrypoint, configuration, file paths, and run command. |
| Initial configuration introduced unnecessary decisions | Two connections, two aliases, `6000s` mission timeout, and `ALWAYS` trace persistence appeared before first invocation. | Quickstart uses one connection and alias, with normal execution defaults. This changes the example, not framework defaults. |
| “No DSLs or planning languages” overstated simplicity | YAML schemas, evidence expressions, child constraints, and planning configuration are part of the authored contract. | Removed the absolute claim. Architecture explains that natural-language prompts coexist with structured configuration and application code. |
| Traditional planner comparison was unsupported marketing | JSHOP2/PANDA were described as brittle without evidence or a scoped comparison. | Removed comparative judgment. No claim that those planners are inferior; no comparative research performed. |
| Integration positioning was inconsistent | Opening was Java-only; installation later called Sidecar the primary path. | Present embedded Java and Sidecar as explicit choices. Any strategic claim that one should be the primary product path remains an owner decision. |
| “Skills” referred to two different concepts | Runtime YAML/Java/REST capabilities and assistant installation packages appeared under Defining Skills. | README now distinguishes application skills from development-assistant Agent Skills. |
| Version statement was demonstrably stale | Old text claimed framework main was beta.6-SNAPSHOT; root POM is `1.0.0-beta.8`. | Removed the time-sensitive comparison. No current Sidecar version is asserted without inspecting its exact revision. |
| Dependency example implied an available artifact | README presented a SNAPSHOT coordinate without an artifact source or local-build prerequisite. | Quickstart explicitly describes a source evaluation and local install. Published artifact availability was not verified. |
| Release engineering interrupted application authoring | Version scripts, fixture checksums, tagging, signing secrets, and publication recovery lived under Defining Skills. | Parked unchanged release instructions for owner review. Candidate for a later maintainer runbook or deletion. |
| Release artifact description may already be incomplete | Old root README referred to three archives/checksums; Console README also describes a macOS DMG and four published artifacts. | Flagged in parked material; do not treat copied release steps as revalidated. |
| Shutdown section mixed application guarantees and internal ordering | It combined admission/budget semantics with close-event gates, lifecycle-stop ordering, and Sidecar packaging-test requirements. | Retained application behavior in execution-limits reference; parked implementation ordering and external Sidecar assertion. |
| Most advanced Java material duplicated existing references | Handoff, generation capture, retirement, validation, observer failures, and REST handler behavior already have focused Java API topics. | Removed README copies and linked the existing authoritative references. Did not discard application-relevant contracts just because they are detailed. |
| Protocol details obscured the observability feature | Header encoding, download admission, branch DTOs, expiration races, and exact artifact behavior appeared in the root overview. | Setup moved to docs; direct HTTP diagnosis moved to a focused skill reference. Exact HTTP assertions were migrated, not comprehensively re-proven in this pass. |
| Skill index had an obsolete audience premise | It described a future SkillBuilder as the intended consumer and repository-local use as the interim path. | Reframed around application developers and installed assistants. Original premise parked for product discussion. |
| Contributor instructions leaked into application API guidance | Compatibility reference instructed readers to edit allowlists and run framework architecture tests. | Parked the contributor procedure; retained descriptive API boundaries and source anchors useful for application verification. |
| AI assistance was not presented as the intended workflow | README did not lead readers from evaluation to installed guidance and Console investigation. | Added an assessment prompt with a concrete repository URL; clarified that guidance installation and MCP connection are separate. |

## Remaining questions for technical or product review

1. **Release migration provenance.** API references have a “Changes in This Revision”
   section accumulating several changes without a complete version-to-version ledger.
   We should establish which releases introduced them before promising complete
   upgrade instructions. The new upgrade doc explicitly describes this limitation.
2. **Independent snapshot compatibility.** Exact version labels are useful, but two
   builds with the same SNAPSHOT version can contain different contracts. The current
   installer/Console policy was preserved; this pass does not create a stronger
   compatibility promise. Record source revisions for reproducible evaluations.
3. **Detailed observability assertions.** The relocated HTTP reference is marked
   `migrated-readme-contract`. A focused adapter audit should recheck exact header,
   race, download-limit, and timeout claims against the current implementation/tests.
4. **“Safe” exception terminology.** Several existing references describe exceptions
   as safe or discuss generic wrappers protecting supplied credentials. This can be
   misread as a comprehensive content-safety guarantee. A later contract review should
   clarify the exact behavior without introducing sensitivity classification or
   masking and without changing diagnostic fidelity through an editorial edit.
5. **Breadth of skill reference detail.** Existing topic indexes and source anchors
   remain dense. This pass removed the clearest contributor-audience instruction,
   but did not classify every internal explanation across the full knowledge set.
   Future pruning should distinguish application-observable guarantees from runtime
   implementation mechanics.
6. **Console consumer versus contributor README.** The Console root README begins
   with build tooling and packaging. The new setup document routes users to its runtime
   package guide. A separate Console README audit would apply the same audience split.
7. **Naming and strategic emphasis.** The application/Agent Skill distinction is now
   explicit. Whether to make Sidecar the default adoption path is a product decision,
   not something this editorial pass should infer.

8. **First-run diagnostic noise.** The successful quickstart emitted an
   observability-disabled warning because it is not a servlet application, plus
   Hibernate Validator warnings about `@Valid` on connection/model maps. These did
   not prevent execution. Review separately whether expected disabled behavior
   deserves a warning and whether the validation annotations need maintenance.

## Relocation map

| Original README material | Authoritative destination after this pass |
| --- | --- |
| Overview and why use Loomspan | [README](../../README.md), [architecture](../../docs/architecture.md) |
| Dependencies, connection setup, first invocation | [Java quickstart](../../docs/quickstart-java.md), [setup](../../docs/setup.md) |
| Retry, provider call budgets, provider diagnostics | Existing [connection reference](../../agent-skills/loomspan-docs/references/skill-authoring/model-selection-and-connections.md) |
| Catalog, invocation/handoff, API inventory, updates, retirement, observers | Existing [Java API knowledge set](../../agent-skills/loomspan-docs/references/java-api/README.md) |
| YAML/Java/REST declaration rules, planning, evidence, authorization | Existing [authoring knowledge set](../../agent-skills/loomspan-docs/references/skill-authoring/README.md) |
| Agent Skill installation and version selection | [Setup](../../docs/setup.md) explanation; existing [installer](../../agent-skills/loomspan-install/SKILL.md) workflow |
| Beta compatibility and upgrade decisions | [Upgrades](../../docs/upgrades.md) |
| Execution budgets and application shutdown | [Execution limits](../../agent-skills/loomspan-docs/references/skill-authoring/execution-limits.md) |
| Observability activation and host security | [Console setup](../../docs/console-setup.md) |
| Direct HTTP/trace artifact diagnostics | [Observability HTTP](../../agent-skills/loomspan-docs/references/skill-authoring/observability-http.md) |
| Repository structure, release engineering, internal shutdown ordering | [Parked maintainer material](readme-maintainer-material.md) |

No production code, public API, configuration defaults, or runtime policy changed.

## Verification completed

- Nine tests passed: `LoomspanPublicSurfaceArchitectureTest` and
  `SupportedSurfaceIntegrationTest`.
- Extracted the quickstart's exact POM, Java, and YAML code blocks into an ignored
  temporary application. Compiled and ran it with a local OpenAI-compatible HTTP
  test endpoint, checking the requested model, input message, and printed response.
  No paid provider was called; real-provider behavior and model quality remain
  outside this check.
- Version consistency and the `loomspan-docs` skill validator passed. The validator
  initially lacked PyYAML; it was rerun successfully in an isolated environment
  under ignored `target/` after installing that dependency there.
- Checked local Markdown links, heading targets, balanced code fences, and diff
  whitespace. No production types changed.

Build logs and the temporary example remain under ignored `target/`; they are not
new repository deliverables.
