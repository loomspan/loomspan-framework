# Phase SC4 — Generic `RestSkillHandler`

Part of [beta 4 roadmap](beta4-rest-skills-and-sidecar-roadmap.md). Depends
on SC3; implements the FW1 SPI from the locally installed framework snapshot
during development. Framework assumptions grounded at `1e4eb455`; see
[SC4 evidence](../beta4-code-grounding.md#sc4--fixed-http-mapping-is-viable-attachments-are-not-json-conversion).
Sidecar's G1 transport decision is resolved: JSON-only in v1. The embedded
framework handler value contract is separate.

## Goal

Sidecar users declare REST skills in `/sidecar/skills/` and map them to HTTP
endpoints in `/sidecar/rest-routes.yaml` — no Java. Both paths are
configurable. Sidecar's single `RestSkillHandler` bean performs the call with
configured transport, auth, and limits. Skills and routes load at startup;
changes take effect after restart.

## Binding decisions

- Exactly one handler bean in Sidecar, always present (the framework only
  requires it when REST manifests exist; Sidecar always registers it).
- Application configuration identifies the route file:

```yaml
loomspan-sidecar:
  rest-routes-location: file:/sidecar/rest-routes.yaml
```

- `/sidecar/rest-routes.yaml` owns both named `targets` and skill-name-keyed
  `routes`. Multiple routes can share a target, and different routes can
  call different services without duplicating transport/auth settings:

```yaml
targets:
  expense-service:
    base-url: https://expenses.internal/api
    auth:
      mode: caller-passthrough      # none | static | caller-passthrough
      headers: {}                  # static mode only
    ssl-bundle: expenses-mtls       # optional reference to a Boot SSL bundle
    connect-timeout: 5s
    read-timeout: 30s
    max-response-size: 1MB
  customer-service:
    base-url: https://customers.internal/v1
    auth:
      mode: static
      headers:
        X-Service-Key: ${CUSTOMER_SERVICE_KEY}
    connect-timeout: 5s
    read-timeout: 30s
    max-response-size: 1MB
routes:
  expenseLookup:
    target: expense-service
    method: GET
    path: /expenses/{category}
  customerLookup:
    target: customer-service
    method: GET
    path: /customers/{id}
```

- `loomspan-sidecar.rest-routes-location` defaults to
  `file:/sidecar/rest-routes.yaml` and may point to another file. Sidecar
  owns parsing and validation; the file is outside the framework's default
  skill scan. Read it once at startup into immutable target and route maps. A
  missing/unreadable file, malformed document, or duplicate skill key fails
  startup with the file location and useful diagnostics. An explicit empty
  file with `targets: {}` and `routes: {}` is valid when there are no
  registered REST skills.
- Keep only this source for target and route declarations; do not also bind
  inline `loomspan-sidecar.rest-targets` or `loomspan-sidecar.rest-skills`
  maps or introduce precedence/merge behavior.
  This replaces an unreleased roadmap proposal, so no compatibility alias
  or migration shim is needed. Base URLs, auth settings, SSL bundle names,
  timeouts, and response limits live under this file's `targets`. Spring
  Boot SSL bundle definitions remain in standard Boot configuration; the
  route file references them rather than defining another TLS format.
- Explicitly resolve environment-backed `${NAME}` placeholders in parsed
  string values using Spring's existing placeholder facilities before
  validation. Missing required placeholders fail startup naming the file,
  field, and placeholder, without printing resolved secret values. Do not
  assume a separately parsed YAML file receives automatic property binding;
  no expression language or custom secret provider is introduced.
- Startup validation (using the FW2 catalog): every registered REST skill
  has a route entry; every entry names a registered REST skill and a
  defined target; `path` is a route path starting with `/`, with no scheme
  or authority (not a network-path reference beginning `//`); `method` is either
  `GET` or `POST`. All other methods, including `PUT`, `PATCH`, and `DELETE`,
  are unsupported in Sidecar v1 and fail startup.
  Unknown keys are rejected. All skill and route
  validation completes before readiness.
- Startup ordering: construct the handler from the route file and target
  configuration without depending on `SkillCatalog`. After framework skill
  registration completes, validate routes against the public catalog using
  Spring's existing startup lifecycle. A mismatch fails startup before
  readiness. This avoids a handler → catalog → registration → handler
  construction cycle without a new public API or custom lifecycle system.
  Validation checks local declarations only; it does not call remote
  endpoints to test their existence, availability, or credentials.
- Editing skills or routes on disk does not change a running instance.
  Restart loads and validates the edited files. Hot reload is deferred;
  do not add reload hooks, catalog versions, or old-version cleanup.
- Input binding: `{name}` path variables bind from same-named inputs (URL
  path-segment encoded). Path values and remaining GET query values must be
  non-null strings, numbers, or booleans; missing path values, null values,
  arrays, and objects fail visibly. Remaining POST inputs form a JSON object
  body, preserving schema-permitted nulls and nested values. No implicit
  omission, flattening, or templating language.
- Join each selected target's base URL and route path with a URI builder,
  preserving the base path: `https://expenses.internal/api` plus
  `/expenses/{category}` becomes `/api/expenses/<encoded category>` on that
  host. Normalize the joining slash and encode variable values as individual
  path segments; inputs cannot replace the target's scheme or authority or
  escape its configured base path. Ground encoding and route validation using
  existing URI/client facilities, including traversal cases.
- Request headers: `Accept: application/json, text/*`; static headers from
  config; `Authorization: Bearer <token>` in passthrough mode from
  `JwtAuthenticationToken.getToken().getTokenValue()` on
  `SecurityContextHolder`; passthrough with no JWT → throw (skill failure).
  Inputs never become headers.
- Response: any 2xx with no body, including `204`, returns an empty string
  without requiring a content type. A nonempty 2xx body must have JSON or
  `text/*` content type and becomes the result string. Non-2xx, timeout,
  connection failure, body over `max-response-size`, or unsupported content
  type on a nonempty body → throw with a bounded
  message (status code, content type, target name; never the response body
  in the exception message). No retries; redirects not followed.
- SC2 preserves `SkillException` messages in the failure representation;
  no data or exception-message sanitization is added. The handler's deliberately
  constructed HTTP error messages above do not imply arbitrary exception
  messages are sanitized.
- Client: Spring `RestClient` per target, built with the target's SSL bundle
  and timeouts.
- Enforce the response cap while reading the response stream, before full
  materialization; avoid default error-body buffering for bounded failures.
  Verify per-target timeout/SSL/redirect behavior against pinned dependencies.
- Sidecar v1 accepts JSON input and transports only JSON-compatible values
  through its fixed GET/POST binder. It does not transport Spring `Resource`
  handles, files, or streams. If reference resolution produces such a value,
  fail visibly before the outbound call; do not serialize a handle's fields
  or contents accidentally. No multipart/base64 conversion or upload API.
  This input-transport decision leaves the agreed JSON/text response handling
  unchanged and does not restrict application-written framework handlers.

## Acceptance criteria

- [ ] Path/query binding covers missing/null/array/object values, Unicode,
  reserved characters, dot segments, encoded traversal, and attempted
  authority replacement. Invalid values make no outbound call; accepted
  values preserve the configured base path. POST preserves permitted nulls
  and nested JSON, including an empty remaining body object.
- [ ] Positive response-size and timeout settings, valid HTTP(S) targets,
  auth-mode/header combinations, and referenced SSL bundles are validated
  locally before readiness. Duplicate target keys are rejected as well as
  duplicate route keys. These are checks of the existing fixed file contract.
- [ ] Redirect responses are not followed (including across hosts); test
  response-cap equality/excess while streaming, connect/read timeouts and
  per-target SSL isolation. Nonempty missing Content-Type fails; JSON/text
  media-type and charset handling are specified and exercised with the
  pinned client. Error responses do not trigger unbounded body buffering.
- [ ] Only `GET` and `POST` routes are accepted. Any other method, including
  `PUT`, `PATCH`, and `DELETE`, fails startup as unsupported in Sidecar v1.
- [ ] A resolved Resource or other non-JSON transport value fails before an
  outbound call; ordinary JSON inputs retain the fixed binding behavior.
- [ ] Missing mapping, unknown target, unknown skill, bad method, or a route
  containing a scheme or authority → startup failure naming the route file
  and key. A leading `/` is required and does not replace the base path.
- [ ] A Spring application-context test starts with REST skills, the single
  handler, and the public catalog without a circular dependency, and proves
  route validation sees completed skill registration. Missing routes, routes
  naming non-REST skills, and other mapping mismatches fail startup before
  readiness. No remote endpoint is called during startup validation.
- [ ] Default and overridden `rest-routes-location` load the expected file;
  unreadable/malformed files, duplicate keys, and unknown fields fail
  startup. Empty `targets` and `routes` with no REST skills are valid.
- [ ] Multiple targets and shared-target routes work from this single file.
  Environment placeholders resolve before validation; an unresolved required
  placeholder fails startup without exposing resolved secrets.
- [ ] Changing a skill definition and route on disk leaves the running
  catalog and routing unchanged; restarting picks up both edits. Invalid
  edits fail the next startup before readiness.
- [ ] `GET` with path var and extra inputs → correct URL with encoded
  segment and query; `POST` → JSON body; verified against a local stub
  server (WireMock or MockWebServer per repo choice).
- [ ] Base URLs with a path prefix and with/without trailing slash preserve
  that prefix when joined to routes; substituted values stay encoded path
  segments. Bodyless 2xx responses, including `204` without `Content-Type`,
  return an empty string; nonempty unsupported content types fail.
- [ ] Static headers sent; passthrough sends the caller's bearer token;
  passthrough without JWT fails the skill (defensive handler test; inbound
  routes require JWT). Outbound `none`/`static` modes remain supported
  independently of JWT-only inbound authentication.
- [ ] Non-2xx, timeout, oversize, wrong content type → `FAILED` execution
  with `SKILL_FAILURE` and no response body leakage.
- [ ] mTLS: stub with client-auth required succeeds with bundle, fails
  without.
- [ ] End-to-end: JWT caller → Sidecar `POST` → YAML planner → REST leaf →
  stub host endpoint receives and verifies the same JWT and establishes the
  original identity and roles. A token expiring during queueing/execution
  is rejected on callback and produces a visible skill failure.

## Ticket boundary

The generic REST handler unit in the [planning handoff](../beta4-ticket-readiness.md)
owns file/startup validation, fixed binding, all auth modes, transport/response
bounds, TLS and callback proof together. Do not merge a handler skeleton
whose security or transport requirements depend on a later hardening ticket.

