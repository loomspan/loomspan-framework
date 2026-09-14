# PR 5.5 — Require a positive observability route collision

## Outcome

Applications can use Spring Boot Actuator and custom Spring MVC handler-mapping
infrastructure without disabling Loomspan Console merely because the framework
does not recognize a `HandlerMapping` implementation. Console remains protected
under its reserved namespace and is disabled when an inspectable application
mapping actually overlaps that namespace.

## Requirements

- Treat `/_loomspan/observability/v1/**` as Loomspan's reserved Console
  namespace. Preserve the existing opt-in Console API-key authentication and do
  not broaden access to its routes.
- Report a collision only from a positively identified path overlap. Preserve
  overlap detection for annotated controllers, functional routes, explicit URL
  mappings, broad wildcards and other mapping forms the detector can inspect.
- Do not treat an unfamiliar or otherwise unclassifiable `HandlerMapping` type
  as a collision solely because its routes cannot be enumerated. Applications
  that deliberately claim Loomspan's documented reserved namespace remain
  unsupported even when they do so through an unclassifiable custom mapping.
- Support Spring Boot 4.1's standard
  `AdditionalHealthEndpointPathsWebMvcHandlerMapping`, including the empty
  mapping contributed when Actuator health probes use a separate management
  port. It must not disable Console unless it exposes an actual overlapping
  route that can be identified through supported mapping information.
- Keep the compatibility policy general rather than adding a class-name or
  Boot-version-specific exemption. Do not introduce a new application SPI,
  framework bean-replacement contract or configuration switch.
- Preserve current behavior for confirmed collisions: Loomspan must not
  silently take over an inspectable application route in the reserved
  namespace.

## Acceptance criteria

- [ ] Console starts and valid API-key requests succeed in a Boot 4.1
  application that also enables Actuator health/readiness on a separate
  management port.
- [ ] An empty or unrelated unclassifiable `HandlerMapping` no longer disables
  Console merely because its concrete type is unknown to the detector.
- [ ] Confirmed exact, child, dynamic and broad-wildcard overlaps with
  `/_loomspan/observability/v1/**` continue to be detected and prevent Console
  route registration.
- [ ] Console requests still require the configured API key, and application
  security coexistence guidance remains accurate.
- [ ] Focused collision-detector tests and the full starter verification pass;
  no supported Java API or extension surface is added.
- [ ] A new `1.0.0-beta.4-SNAPSHOT` install allows the Sidecar authenticated
  execution API pipeline to pass its Console-plus-separate-port integration
  test without a Sidecar workaround.

## Context

The Sidecar SC2/SC3 delivery combines JWT-protected execution routes,
separate-port Actuator readiness and optional Loomspan Console coexistence. Its
application test found that `ObservabilityRouteCollisionDetector` classifies
Boot 4.1's standard `AdditionalHealthEndpointPathsWebMvcHandlerMapping` as an
unknown mapping and therefore reports a collision even when that mapping is
empty. Loomspan disables Console, so a valid Console API-key request receives
`401` rather than reaching the registered Console route.

This is a false positive, not a health-route overlap. The travel demo did not
expose it because that application does not include Actuator or exercise
Console together with separate-port probes. A Boot-class-specific exemption
would address the immediate fixture but would retain the same compatibility
failure for future Boot or application-defined mapping implementations.

The authoritative dependent Sidecar ticket is
[authenticated execution API](../../../../loomspan-sidecar/ai/thoughts/tickets/2026-09-13-authenticated-execution-api.md),
with future lifecycle use described by the Sidecar
[SC5 phase](../../../../loomspan-sidecar/ai/thoughts/phases/phase-sc5.md).
Complete this framework ticket and install the updated beta.4 snapshot before
resuming Sidecar pipeline Step 4. Do not implement Sidecar changes, SC4 routing
or SC5 packaged lifecycle proof here.

## Execution profile

- **Recommended:** full
- **Confidence:** high
- **Rationale:** The implementation is expected to be small, but it changes a
  framework-wide operator-route collision policy at an authenticated HTTP
  boundary and requires independent compatibility and security verification.
- **Reassessment triggers:** Equivalent positive-overlap behavior may already
  have landed, or current code may reveal a standard, path-aware inspection
  mechanism that eliminates the policy change. Do not downgrade if the work
  would weaken Console authentication or alter confirmed-collision behavior.

## Pipeline notes

- Replacing the existing fail-closed treatment of an unclassifiable
  `HandlerMapping` is intentional. The reserved namespace contract, API-key
  enforcement and positive detection of inspectable overlaps remain required.
