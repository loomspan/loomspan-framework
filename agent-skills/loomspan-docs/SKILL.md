---
name: loomspan-docs
description: Prepare and consult version-aligned Loomspan framework documentation. Use when a developer asks to read up on a Loomspan knowledge set such as skill-authoring or java-api, or needs grounded guidance for designing, invoking, reviewing, implementing, or diagnosing Loomspan skills and application integrations. Use the separate loomspan skill for live runtime inspection.
license: Apache-2.0
metadata:
  loomspan-version: "1.0.0-beta.3-SNAPSHOT"
---

# Loomspan framework documentation

Use this skill as a versioned context loader and documentation router. The
bundled references are authoritative authoring guidance for the Loomspan
revision from which this skill was installed. They do not replace executable
framework behavior when an exact edge case requires source verification.

## Knowledge sets

| Set | Entrypoint | Preparation baseline |
| --- | --- | --- |
| `skill-authoring` | [Skill-authoring index](references/skill-authoring/README.md) | The index and [skill-tree mental model](references/skill-authoring/mental-model.md) |
| `java-api` | [Java API index](references/java-api/README.md) | The index and [compatibility and boundaries](references/java-api/compatibility-and-boundaries.md) |

Treat a knowledge-set name supplied when invoking `loomspan-docs` as an
explicit selector.
If no set is named, infer it from the request. Do not invent an undocumented
set; state which sets are available.

## Prepare context

When the developer asks to prepare, read up, get oriented, or seed context for
a knowledge set:

1. Read the selected set's entrypoint completely.
2. Read its preparation baseline completely.
3. Build a compact working map from the entrypoint's routing and coverage
   tables. Do not load every topic document.
4. Report the selected set, the baseline documents read, the major routed
   topics now available, and any material version or coverage limitation.
5. If the request is preparation-only, stop before design or implementation
   and wait for the developer's task.

## Consult the documentation

For a concrete question or task:

1. Read the selected set's entrypoint if it has not been read in the current
   task.
2. Follow its routing table and read only the topic documents relevant to the
   request. Read each selected document completely.
3. Distinguish enforced behavior, recommended design, optional choices, known
   limitations, and undocumented areas. Missing coverage is not proof that a
   feature is supported or unsupported.
4. Re-read the relevant reference before making exact claims if earlier
   context may have been compacted.
5. Cite bundled document paths and named implementation or test anchors when
   they materially support the answer.

When a question requires source verification, follow
[the source-verification protocol](references/skill-authoring/source-verification.md).
If the Loomspan source is not present locally, use the official Loomspan GitHub
tag matching the developer's dependency when authorized and available. Do not
silently substitute the default branch, another release, or a fork. If matching
source cannot be inspected, explain the remaining uncertainty.

## Preserve version and product boundaries

- Prefer a skill installed from the Git tag matching the application's
  Loomspan Maven dependency. If the versions may differ, disclose that before
  relying on version-sensitive behavior.
- The bundled `skill-authoring` set covers authoring semantics and design. The
  `java-api` set documents the closed supported application-facing Java
  surface. Neither set creates a Loomspan SPI or bean-replacement contract.
- Use the separately installed `loomspan` skill and configured Loomspan
  Console MCP connection for live runtime status, traces, failures, or usage.
- Treat repository paths and implementation anchors in the references as
  source-investigation hints. Do not assume an installed consumer project
  contains the Loomspan framework source tree.
