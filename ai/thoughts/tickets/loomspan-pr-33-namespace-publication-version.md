# PR 33 — Expose each namespace’s active publication version to Console clients and operators

## Outcome

An embedding host can label a complete namespace publication (skill documents and runtime configuration) with an optional `publicationVersion`. Loomspan Cloud can ask an engine which publication it is serving after a lost publish acknowledgment, even before the first invocation. An operator can see the same answer for every installed namespace in the Console UI.

## Requirements

- Accept the label when preparing the update and preserve the supplied string exactly. The host owns its meaning, including whether equal labels identify equal content across engines. Framework continues to assign and use its process-local `generationId`. Publication ordering, stale-update checks and removal continue to use their existing semantics.
- Bind the label to the immutable candidate generation. Its prepared snapshot may describe it immediately. The active snapshot exposes its generation ID and label together only upon successful publication. Each replacement supplies its own optional label.
- Preserve existing preparation calls, credential-resolution choices, visibility defaults and `publish(preparedUpdate)`. Prefer the smallest addition to the supported Java API. `PreparedSkillUpdate.snapshot()` and `SkillReloader.snapshot(namespace)` already expose candidate and active `SkillCatalog` metadata, respectively, including the namespace and generation ID of an empty catalog.
- Through the existing authenticated observation path, let a programmatic Console client discover every installed namespace on the selected instance and read its active `generationId` and optional `publicationVersion`. Show each namespace and its publication version in the browser UI, with a clear indication when the host omitted it. Both surfaces must work on a fresh connection and on refresh or reconnect.

## Acceptance criteria

- [ ] Two independent Framework instances, East and West, publish the same empty namespace with label `P7`. Before either executes a skill, a Console client reads the namespace and `P7` alongside each instance’s own active generation ID. An operator selecting either target sees the namespace and `P7` in the UI.
- [ ] East prepares `P8`. Its candidate snapshot identifies the new generation and label. East’s active Java snapshot, the Console read and the refreshed UI still describe `P7`. After successful publication on East, a newly connected client can read East’s new generation ID and `P8` even if the host missed the acknowledgment. Reopening the UI shows `P8` on East and `P7` on West.
- [ ] Abandoning a prepared update or attempting a failed publication, including a stale candidate rejected after another candidate wins, leaves the active ID and label together on the winning generation.
- [ ] Existing callers can prepare and publish through their existing signatures with the same visibility and credential behavior. When such a caller replaces a labeled generation, clients see the replacement’s generation ID and an absent version. The UI still lists the namespace and makes the absence clear.
- [ ] After an engine restarts and the host reinstalls the namespace with `P7`, a fresh read and the UI show `P7` before execution. The read identifies the new process’s active generation. Reinstallation supplies the label.
- [ ] The README and relevant Java reload and Console observation guidance explain how to supply, discover and view the label, who defines it, and what omission means. Current Java and Go protocol fixtures and Console reader and UI coverage agree on labeled, unlabeled and empty namespaces.

## Context

`SkillGenerationManager` owns the active namespace map. Its current observation projection flattens registered skill catalogs, so an empty namespace contributes no skill rows. Read namespace metadata from the active generations themselves. Reuse the simplest existing observation and Console presentation paths that can carry this information, keeping each ID and label from one generation. The Console Instance Overview already supports target selection context and refresh.

This feature requires current-state discovery only. Publication history, Framework-managed restart persistence, content hashing, event replay, Cloud routing and Sidecar adoption are outside scope.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** The change extends a supported Java API and a cross-language observation contract. Correctness depends on the same publication boundary being reflected in Java, programmatic Console reads and the browser.
- **Reassessment triggers:** none.

## Pipeline notes

Framework is in development with no deployments. Update current producers, readers, schemas and fixtures together. Destructive changes to the affected wire shapes are acceptable. Keep the Java-to-Go compatibility version unchanged and omit compatibility shims, migration paths and legacy readers.
