# PR 11.1 — Publish every aspect of execution behavior as one configuration

## Development constraints

**Loomspan is still in development. Destructively replace superseded contracts,
code and schemas; do not add compatibility shims, legacy adapters, parallel
legacy APIs or migration machinery solely to preserve obsolete behavior.**

Apply the [framework design lens](../framework-feature-design-lens.md). Choose
the simplest complete solution, minimize technical debt, and keep implementation,
tests and documentation proportional. Document intentional consumer changes and
required development-data resets; this does not authorize deleting deployed data.

## Outcome

A host can dynamically publish every aspect of framework execution behavior,
including provider credentials, together with skills without restarting. Each
root and all its descendants use one complete captured configuration throughout
their physical lifetime. Ordinary file configuration remains the default for
embedded applications; dynamic configuration is optional.

PR 11 established execution configuration publication but restricted credentials
to external property references. Complete that capability by extending the
existing execution configuration and generation lifecycle, not by introducing a
second configuration system or a collection of independent live setters.

## Requirements

- Cover all framework settings that govern an execution, including skills,
  connections, provider credentials and options, model aliases, retry behavior,
  session timeouts, depth, quotas, attachment limits and execution tracing policy.
  These examples are not an exhaustive allowlist: execution behavior must not
  retain accidental startup-only exceptions or mutable global configuration reads.
- Preserve file-based startup configuration, including environment-variable
  credential references, as the default. File and explicit publication paths
  must produce the same effective execution semantics and defaults. An explicit
  complete candidate must not silently inherit provider fields from deployment
  files or acquire mutable defaults after preparation.
- Provide a deliberately supported public way for a host to prepare a complete
  candidate with dynamically supplied provider credentials. Sidecar must be able
  to use its own encrypted credential storage without writing deployment files,
  mutating the process Environment, replacing framework beans or importing
  internal/autoconfigure types. The exact API shape belongs to planning; a new
  credential resolver SPI is not prescribed. Keep the supported surface small.
- Resolve and capture all required effective values and provider resources during
  preparation. Invalid settings, unavailable credentials or resource preparation
  failures leave the active generation unchanged. Validation/preparation must not
  execute skills or require a billable provider request.
- Activate skills and execution configuration atomically. Framework root
  admission/handoff remains the version-selection boundary. Captured roots,
  delayed starts, nested calls, parallel branches, retries and later provider
  calls retain their version. Queued work not yet handed off captures the version
  active at handoff. Preserve host publication/execution correlation.
- Keep old resources usable until their captured physical work ends; release
  abandoned preparations and retired resources. Preserve the existing single
  framework shutdown budget. Do not rebuild the established lifecycle needlessly.
- Keep resolved credentials out of catalogs, authored configuration readback,
  ordinary diagnostics, errors and accidental object/string representations.
  Supporting host-supplied secrets does not make them ordinary exportable YAML.
  The framework does not own a secret-management UI, encrypted database or vault.
- Process infrastructure remains outside scope: listeners, storage backends,
  observability service authentication/retention, shutdown configuration and
  other Spring/server infrastructure. Distinguish these from per-execution
  tracing policy. Reject unsupported process fields in execution candidates.
- External revocation of a captured provider key may break an older execution;
  no guarantee of provider-side validity is required. Document normal rotation:
  publish the replacement, allow old work to finish, then revoke the old key.
  Environment-variable changes still require a process restart; live environment
  refresh and automatic file watching are not required.

## Acceptance criteria

- [ ] A host using only the supported public API publishes skills and the complete
  execution configuration, including replacement provider credentials, without
  restarting or changing global deployment properties.
- [ ] File-only embedded applications retain the default configuration path and
  environment references; equivalent file and dynamic configurations have
  equivalent behavior. The documented execution/process boundary accounts for
  all execution-affecting settings, with no unexplained startup-only gaps.
- [ ] Overlapping executions across a publication retain their respective complete
  settings and credentials through delayed, nested, parallel and retry work;
  new roots select the new generation at handoff and remain correlatable.
- [ ] Failed or abandoned candidates do not partially activate or leak resources;
  old resources remain usable until physical completion and shutdown retains its
  existing lifecycle guarantees. Preparation requires no model invocation.
- [ ] Secret values are absent from public readback and diagnostics; process
  settings are rejected. Documentation explains credential handling, rotation,
  environment restart behavior and intentional consumer changes.
- [ ] Supported-surface checks and framework tests verify this contract. The
  companion Sidecar integration uses the resulting installed snapshot and supplies
  its own evidence before final framework release checks.

## Context

Developer-assigned work item: **PR 11.1**. Extends
[PR 11](loomspan-pr-11-publish-execution-configuration.md); companion:
[Sidecar PR 7.1](../../../../loomspan-sidecar/ai/thoughts/tickets/loomspan-pr-7.1-complete-execution-configuration.md).
Implement the framework contract first, then install the snapshot for Sidecar
integration. Follow the companion repository's dependency/release sequencing;
Sidecar does not rebuild the framework in its normal build.

Source hint: `ai.loomspan.api.ExecutionConfiguration` already carries execution
YAML alongside skills through publication. At ticket creation its documented
contract is reference-only. Build on that work and the existing preparation and
generation machinery. Internal decomposition remains a planning decision.
This ticket deliberately authorizes planning the necessary public capability,
with allowlist, supported-surface tests and documentation updates.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Supported API, secret handling, complete execution semantics,
  concurrent generation lifetimes and cross-repository integration are affected.
- **Reassessment triggers:** Additional execution-affecting settings or public
  contract gaps must be covered in planning; they do not justify internal bypasses.
