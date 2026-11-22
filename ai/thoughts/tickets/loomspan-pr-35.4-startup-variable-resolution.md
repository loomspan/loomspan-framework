# PR 35.4 — Resolve effective Spring settings and configured skills together

## Outcome

Standalone Spring hosts and file-based Sidecar receive the same variable semantics on boot, even if they never call reload. Use the 35.1 grammar and 35.2 provider schema. Coordinate shared binding and fixtures with 35.3.

## Requirements and acceptance criteria

- [ ] Retain authored YAML nodes and locations until Spring profile, import and property-source precedence select the effective settings. Preserve field-level connection inheritance and the existing winning-section replacement behavior for executor configuration. Only effective markers participate. Other Spring properties retain their normal binding behavior.
- [ ] One startup-local resolution spans effective connections, namespace executor settings and configured executor-backed skills. Use normal `Environment.getProperty(name)`, including system environment sources. Resolve before typed connection validation/provider construction and before capturing executor and skill settings. Stale, inactive or overridden markers trigger zero lookups. Missing/blank effective variables fail with locations and reasons without supplied values.
- [ ] Boot with a base OpenAI connection containing driver, an API-key marker for missing `default.provider.key`, and an X-Region header referencing `app.region`. An active profile overrides only the key with local-demo-key. With app.region=west and a configured skill referencing it, observe the inherited driver, local key, and west in both consumers from one lookup. Include a missing inactive-profile marker. Disable the override and observe failure for the base key. Exercise an imported executor section replacement independently.
- [ ] Authored `${region}` remains literal inside each supported subtree. A marker naming a host property `report.label=${region}` delivers Spring's effective west. Verify the strings at consumers after binding, including a returned string containing placeholder characters that must survive without a second expansion. Preserve native conversion and validation at typed boundaries, marker keys, mixed headers and multiline JSON.
- [ ] Migrate startup examples relying on implicit provider placeholder expansion to explicit markers. Run `LoomspanPublicSurfaceArchitectureTest`. With 35.3 integrated, record the Framework revision/artifact, shared fixture location, test names/results and downstream Sidecar and Cloud handoffs. Cloud owns Go fixture parity, ZIP selection, exact-map transport over mTLS, private versioned installation, restart/recovery and evaluation of tenant Vertex identity before closing its finding.

## Pipeline notes

All Loomspan products are in development with no QA or production deployments. Destructively replace contracts superseded by this ticket. Breaking other Loomspan projects that still use those contracts is acceptable until their own updates land. Do not add compatibility shims or retain legacy behavior to keep those projects working. Changing the meaning of authored provider placeholder strings is intentional.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Spring source precedence, early binding and generation capture require end-to-end verification.
- **Reassessment triggers:** Reassess if the current checkout already implements the production contract and only localized fixture or documentation work remains.
