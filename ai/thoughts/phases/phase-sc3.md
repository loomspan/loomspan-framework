# Phase SC3 — Inbound authentication

Part of [beta 4 roadmap](beta4-rest-skills-and-sidecar-roadmap.md). Delivered
with the initial SC2 execution API after SC1; ownership uses their shared
internal issuer/subject value from the outset. Framework assumptions grounded at `1e4eb455`; see
[SC3 evidence](../beta4-code-grounding.md#sc3--identity-propagation-aligns-configuration-requires-explicit-wiring).
JWT application wiring still needs Sidecar integration tests.

## Goal

Every Sidecar execution/catalog route is authenticated by a verified bearer
JWT, producing a Spring `Authentication` whose authorities drive `rbac_roles`
and whose identity owns executions. The host application or its trusted
identity provider supplies user identity and roles. Sidecar verifies that
assertion; it does not maintain user-role assignments. The same JWT is the
credential that `caller-passthrough` REST targets (SC4) forward back to the
host application, where it is verified to establish the caller's security
context again. Service callers use JWTs representing service identities.

## Binding decisions

- JWT is the only inbound authentication mechanism in the initial release.
  Requests without a valid bearer JWT receive `401`. Plain API keys and
  API-key-based identity delegation are deferred; no key configuration,
  alternate filter, or combined-authentication mode is introduced.
- Use Spring Security OAuth2 resource-server JWT verification. Configure a
  trusted issuer and audience; verification keys come from issuer discovery,
  a configured `jwk-set-uri`, or a public key. All modes validate the expected
  issuer, signature, audience, and token lifetime, and require nonblank `iss`
  and `sub` plus an expiration. Explicit key configuration does not bypass
  issuer validation. Verify the explicit binding below in SC3 context tests.
  Roles come from a configured claim (default `roles`) with a configured
  prefix; clock skew is configurable. Resulting `Authentication` is
  `JwtAuthenticationToken`.
- Sidecar's custom JWT property prefix is not Boot resource-server binding.
  Explicitly connect it to the standard decoder and validators for every
  key-source mode; do not assume custom issuer/audience keys activate them.
- Execution ownership uses one small internal immutable value with explicit
  `issuer` and `subject` fields, shared by SC2 and SC3. Compare both fields
  directly, not a delimiter-joined string or the token itself. No auth-kind
  discriminator is needed with JWT-only authentication. Require the claims
  before accepting requests; a renewed JWT with the same issuer and subject
  owns the same executions. Different issuer or subject yields `404` on
  execution reads. This adds no framework public API.
- The primary integration forwards an existing suitable access token. A
  host backend using sessions may obtain or issue a signed JWT from its
  authenticated identity and roles. Browser cookies and OIDC ID tokens are
  not assumed to be suitable API credentials. The token must be accepted by
  Sidecar and each passthrough target, including their audience checks.
- Sidecar never refreshes, exchanges, or mints tokens. Queue time consumes
  token lifetime too. Accepted executions retain the identity and roles
  verified at admission; later token expiry does not revoke local work.
  Sidecar does not recheck expiry at worker handoff or during execution.
  Framework skill access checks still apply to the captured authentication.
  Passthrough targets independently validate the forwarded token;
  a token expiring before a callback is rejected by the
  target and surfaces as a REST skill failure. Sidecar does not inject
  credentials into model-visible inputs. It performs no data sanitization
  of arbitrary inputs, results, exception messages, or diagnostic history;
  these are not guaranteed free of sensitive data.
- Roles map to Spring role authorities so framework `rbac_roles` and Java
  `@RolesAllowed` semantics apply unchanged.
- Keep the token converter's configured prefix aligned with Spring's
  `GrantedAuthorityDefaults`, which the framework evaluator already reads.
  A custom claim-converter prefix alone does not change framework checks.
- No sessions, no CSRF (stateless API), `Cache-Control: no-store` on all
  responses.
- Actuator health on the management port is exempt; everything else under
  `/v1/**` is authenticated.
- When Console observability is enabled, permit its reserved namespace
  through application security to the framework's own API-key filter, as
  documented in README. JWT-only execution authentication does not replace
  Console's independent operator authentication. Do not replace internal beans.
- mTLS is transport-only: documented `server.ssl.client-auth` setup; no X.509
  identity mapping.

## Configuration (draft)

```yaml
loomspan-sidecar:
  auth:
    jwt:
      issuer-uri: https://host-app.example.com
      audience: loomspan-sidecar
      roles-claim: roles
      role-prefix: ROLE_
```

## Acceptance criteria

- [ ] Context tests cover discovery, explicit JWKS, and public-key wiring;
  invalid/ambiguous key-source configuration fails startup. Custom role
  claim/prefix settings agree with framework authorization at pre-check and
  execution. Configured clock skew is exercised at expiry boundaries.
- [ ] JWT execution security and opt-in Console API-key security coexist;
  the management health exemption does not expose other management routes.
  Stateless responses carry the agreed `Cache-Control: no-store` header.
- [ ] No bearer credential or an invalid signature, wrong issuer/audience,
  expired token, or missing required identity/lifetime claims → `401`.
  Explicit JWKS/public-key modes enforce issuer validation too.
- [ ] Valid JWT → catalog `200`; `POST` to a skill requiring a role the
  token lacks → `403`; with the role → `202`. Roles originate in the
  verified token, with no Sidecar user-role assignment table.
- [ ] A different issuer or subject cannot read an execution; a renewed JWT
  with the same issuer and subject can. Ownership compares explicit fields.
- [ ] Inside a test `RestSkillHandler`, a JWT caller's token value is
  readable from `SecurityContextHolder`, including after queueing and nested
  invocation. SC4 proves the callback host verifies the same token and
  establishes the user's identity and roles.
- [ ] Ordinary failure envelopes omit stack traces, as specified in SC2.
  Framework `SkillException` messages are preserved without sanitization;
  selected diagnostic history follows SC2's mode. There is no blanket
  sensitive-data-free guarantee for responses or logs.

## Ticket boundary

Deliver with SC2 as the authenticated execution API unit in the
[planning handoff](../beta4-ticket-readiness.md). These phases identify
responsibilities, not separate authentication stages. Reuse standard JWT
facilities and the common application fixture. Ordinary test JWTs/mocks
support focused tests without a temporary production authentication path.

