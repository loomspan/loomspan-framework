---
name: loomspan
description: Orient developers in the Loomspan ecosystem and route requests to available framework, Console, Sidecar, SDK or installation guidance.
license: Apache-2.0
metadata:
  loomspan-component: framework
  loomspan-version: "1.0.0-beta.6-SNAPSHOT"
---

# Loomspan orientation

Loomspan runs skills either inside a Java application or in the separate Sidecar
HTTP service. Choose the relevant available sibling skill for the user's task;
specialists can also be invoked directly. Load only the guidance needed.

| Request | Specialist and responsibility |
| --- | --- |
| Skill semantics, YAML design or embedded Java API | `loomspan-docs`, matching the selected framework version |
| Runtime status, executions, traces, failures or usage evidence | `loomspan-console`, using its read-only Console tools |
| Sidecar server setup, REST routes, drafts, validation, publication or direct execution API | `loomspan-sidecar-authoring`, matching the target Sidecar version |
| SDK language setup, authentication integration, callbacks, request context, lifecycle or application changes | Available `loomspan-sdk-<language>`, matching the application's SDK dependency |
| Install or update guidance | `loomspan-install`, only when installation/update is requested |

If a specialist is missing or version alignment is unknown, explain the gap and
the relevant installation option. Do not automatically install it, load every
specialist or invent its semantics. SDK skills are future independently owned
components; do not imply that a named language skill already exists.

Direct API customers need no SDK. Sidecar guidance owns server configuration;
SDK guidance owns application integration. Application execution requests go to
Sidecar; callbacks go into the application. Management tokens author configuration,
execution JWTs authenticate execution, and application endpoints independently
authenticate callbacks and authorize data. Routing grants no additional authority.
