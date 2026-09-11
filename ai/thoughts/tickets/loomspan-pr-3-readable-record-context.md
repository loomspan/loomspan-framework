# PR 3 — Show readable skill and step context in trace records

## Outcome

Make the Console trace Records table understandable without inspecting frame
UUIDs or opening raw records. Users currently see event types such as
`MODEL_RESPONSE_RECEIVED` beside a UUID and report ignoring the UUID. They want
to know which skill and execution step produced each record while scanning the
table and reading an expanded request or response.

## Requirements

- Replace the Records table's `Frame` column with `Context`. Show the owning
  skill and runtime execution step as `Skill Name / Step X` when those facts
  apply. Keep the event type, sequence, duration, and existing actions visible.
- Apply readable context across the Records table, including model request and
  response rows. Show only the immediate owning skill and step or phase by
  default. Reveal the full path on context hover and keyboard focus, and keep
  the full path above expanded model request and response content. Do not add
  a display setting. A tooltip is unnecessary when the full path is identical
  to the visible label.
- Label planning and mission model calls with their skill and `Planning` or
  `Mission` when they do not belong to a numbered execution step. Do not assign
  a fictitious step or confuse execution steps with plan task positions,
  provider attempts, or retries.
- Preserve nested ownership: use the actual owning skill and its step. Include
  readable parent-skill context in the full path using the existing frame
  hierarchy. Do not infer ownership from adjacent records or completion order.
- When evidence does not establish some context, retain the known facts and
  explicitly indicate the unavailable portion. When no context is established,
  show `Context unavailable`. Never substitute a UUID or guess a skill or step.
- In the Records presentation, expose frame UUIDs only through the existing
  `Read raw record` action. Do not add shortened IDs, UUID tooltips, copy-ID
  buttons, a show-IDs toggle, or display preferences. Preserve the underlying
  identifiers and raw-record evidence for diagnostics and MCP correlation.
- Choose the simplest coherent implementation that completes these behaviors.
  Reuse existing trace facts and presentation mechanisms. Avoid speculative
  abstractions, new state models, parallel context classifiers, and unrelated
  cleanup. Preserve record ordering, selection, failure highlighting, content
  expansion, and raw-record access.
- Selecting any record within a step highlights that nearest enclosing step,
  using frame relationships rather than sequence proximity. Its Started and
  Completed rows share the darker highlight, and its related activity uses the
  lighter highlight, including when an activity row is selected. Only the
  selected row receives the selection outline. Preserve error/warning styling
  (including failed step termination) and keyboard focus visibility. Unrelated
  interleaved steps remain unhighlighted; missing ancestry must not invent a
  step association.

## Acceptance criteria

- [ ] A user can identify the owning skill and execution step directly from
  applicable Records rows; the column is named `Context`, and no frame UUID is
  displayed outside raw-record content in this view.
- [ ] Rows show the immediate skill context; hover and keyboard focus reveal
  the full path, which also appears above expanded model requests and responses.
  Existing event types, sequence, duration, and actions remain available.
- [ ] Planning, mission, nested, repeated, and interleaved invocations receive
  evidence-based context without borrowing another invocation's skill or step.
  Nested full paths include readable parent-skill context.
- [ ] Missing or incomplete context is explicit, preserves known facts, and
  never produces an invented step or UUID fallback.
- [ ] Raw records still expose exact frame identifiers; no new UUID controls,
  display settings, or configuration are introduced.
- [ ] Existing record ordering, selection, failure visibility, and content and
  raw-record interactions continue to work.
- [ ] Clicking a step boundary or related activity selects the same step group:
  both boundaries have matching darker fills, activity has a lighter fill,
  and only the clicked row has the selection outline. Nested records use the
  nearest enclosing step; interleaved steps and incomplete ancestry do not
  cause false associations, and failures remain visually distinct.

## Context and design constraints

Use [the framework feature design lens](../framework-feature-design-lens.md).
This is a Console diagnostic usability improvement; it does not change how
skill authors define skills or how application developers invoke them. No new
application API, SPI, configuration, manifest, trust, or mutability semantics
are intended.

The initial source inspection found that browser records already carry
`route`, `frameType`, and frame relationships. Runtime step-model routes contain
skill and step information, while compact frame projections omit `stepNumber`.
These are research hints, not a requirement to parse route names or expand the
protocol. Verify the authoritative context already available before choosing
the implementation, consistent with the lens's semantic-ownership principle.
Do not reconstruct runtime decisions from incidental naming or ordering when
authoritative facts already exist.

The intended affected surface is internal Console presentation over current-run
diagnostic evidence. No serialized-format, cross-component protocol, or
compatibility-marker change is expected, and no compatibility shim is needed
for replacing this internal UI column. Preserve raw diagnostic fidelity. Do
not broaden the work into UUID removal from other Console views or MCP.

The low-value UUID column is deliberately removed. No other dead code or
obsolete abstraction was established during this discussion; the pipeline
should flag source-backed findings rather than expand cleanup speculatively.

PR number 3 is explicitly assigned by the developer. This ticket authorizes
the described feature for subsequent pipeline work; ticket creation does not
start the pipeline or create a GitHub PR.
