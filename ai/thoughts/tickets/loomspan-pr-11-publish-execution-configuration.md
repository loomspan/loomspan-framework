# PR 11 — Publish execution configuration together with skills

## Outcome

Let a framework host prepare and publish one complete version of skills and the
framework settings governing their execution. New root executions use that
version; already-admitted executions retain their original version throughout
their lifetime. This enables the Sidecar Console to publish a changed prompt,
model selection and execution quota together without restarting its host or
mixing settings from different publications.

## Requirements

- Extend the supported application-facing preparation/publication contract to
  include named connections, model aliases, provider options and retries, all
  session execution settings (timeouts, depth, quotas and attachment limits),
  and execution-trace persistence alongside the complete skill set. Reuse the
  existing generation lifecycle and configuration semantics rather than adding
  a competing configuration authority. The exact public API shape belongs to
  planning; Sidecar must not depend on internal or autoconfigure Java types.
- Validate skills against the candidate's model aliases and connections, not
  the active catalog. Prepare and freeze the complete candidate and its required
  provider resources before activation. Invalid or failed preparation must not
  change the active version. Validation/preparation must not execute skills or
  require a billable model request.
- Publication switches the complete execution version atomically. Capture it
  at framework root admission/handoff. Work that has not yet crossed that
  boundary uses the version active when it does; a captured invocation retains
  its version even if physical execution begins after a subsequent publication.
  Child skills, parallel branches, provider retries and later calls within that
  root all use the same captured settings and resources.
- Keep superseded generation resources usable until captured invocations and
  their physical work release them. Release abandoned preparations and retired
  resources without leaking clients or prematurely closing resources still in
  use. Preserve the existing framework shutdown budget and lifecycle semantics.
- Use `loomspan.execution-trace.persistence` consistently for application YAML
  and the equivalent publishable setting. Preserve the existing policy meanings
  and default; this is a namespace correction, not a change in trace retention
  semantics. Trace persistence may differ between simultaneous generations.
- Keep process-wide settings outside execution publications: observability API
  enablement/authentication and service retention settings, shutdown configuration,
  and Spring/server infrastructure. Skill locations remain a source-loading
  concern; explicitly supplied published documents are the complete skill set.
  Reject unsupported process settings in an execution candidate rather than
  accepting inert fields or changing global state during publication.
- Preserve ordinary embedded Spring configuration through `application.yml` as
  the startup configuration path. Explicit publications must freeze a complete
  effective execution configuration, with defaults applied consistently; they
  must not silently acquire changes from mutable global properties afterward.
- Published credential values are external references. Resolve the required
  references during preparation and retain the resolved connection resources
  for that generation. Missing references fail preparation. Do not expose
  resolved credentials through validation, catalogs or diagnostics. This does
  not add a secret store, credential-editing UI or live secret-rotation service;
  external credential revocation can still cause provider calls to fail.
- Preserve useful execution/generation correlation so the host can associate
  work with its durable publication. Republishing an earlier complete candidate
  creates a new active generation; it does not mutate existing executions.

## Acceptance criteria

- [ ] A host using only the supported public API can prepare and publish skills,
  connections/models, session limits and tracing policy as one validated version.
  A new model alias and a skill referencing it can be introduced together.
- [ ] Across a publication, overlapping roots use their respective complete
  versions, including delayed child work, retries, limits and tracing. Root
  handoff is the documented selection boundary; there is no mixed-version window.
- [ ] Invalid configuration, missing credential references, failed preparation
  and abandoned candidates leave active work/configuration intact and release
  their unused resources. Validation/preparation performs no model invocation.
- [ ] Old resources survive until their captured physical work finishes, then
  retire; shutdown still follows the existing single framework budget.
- [ ] Process settings are excluded explicitly, embedded YAML startup remains
  supported, and defaults are consistent between startup and publication.
  All in-repository examples and tests use the nested tracing key.
- [ ] Credentials stay out of public diagnostics and authored publication
  output. Replacing a credential reference in a later publication does not
  replace an already-captured connection's credentials.
- [ ] Republishing earlier content produces a distinct generation and preserves
  execution correlation. Documentation explains the supported contract,
  configuration boundary and consumer impact of intentional changes.

## Context and sequencing

Companion: [Sidecar PR 7](../../../../loomspan-sidecar/ai/thoughts/tickets/loomspan-pr-7-publish-framework-configuration.md).
Implement and verify the framework contract first; Sidecar then integrates it
through its existing draft, validation, publication and generation-resource
lifecycle. Sidecar integration must be verified against the resulting installed
framework artifact before the combined feature is considered complete.

At ticket creation, `SkillReloader` prepares skill documents, provider connections
are wired from startup properties, and `ExecutionTraceProperties` binds the
top-level `execution-trace` key. These are source hints, not prescribed internal
decomposition. The separate Go Loomspan Console is not the authoring UI in scope.

Apply the [framework feature design lens](../framework-feature-design-lens.md).
Keep the public surface small and the implementation coherent; classify affected
contracts and update their supported-surface checks and documentation. This
ticket deliberately authorizes planning a public framework capability for
Sidecar rather than an internal bean override or additional SPI.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Changes supported APIs, configuration contracts, concurrent
  execution lifetime, provider resource ownership and cross-repository behavior.
- **Reassessment triggers:** Broader security or compatibility impacts must be
  surfaced during planning; the known scope already requires the full profile.

## Pipeline notes

- Moving `execution-trace.persistence` to `loomspan.execution-trace.persistence`
  is an intentional pre-1.0 configuration break. Use one canonical key, without
  a legacy alias; document the required consumer update and update in-repository
  references atomically. The incidents demo is a known external consumer that
  needs the YAML adjustment when adopting the updated framework.
- Superseded internal paths should be replaced coherently. This is not blanket
  authorization to break unrelated supported Java APIs or manifest behavior;
  assess those contracts under the framework design lens.
