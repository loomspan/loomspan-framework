# PR 35.2 — Accept literal provider settings and retire credential URIs

## Outcome

Align provider configuration around the actual setting being authored. This Framework change owns provider schema, conversion and adapter behavior. PR 35.3 wires in published markers and removes the superseded published reference fields in the same series. PR 35.4 handles Spring precedence and literal placeholder delivery.

## Requirements and acceptance criteria

- [ ] A reference-free published pack prepares with an explicit empty map. At startup and in published configuration, accept literal `api-key`, `headers` and `gemini.credentials-json` where applicable. Header names and values reach provider validation. Preserve normal conversion, constraints and authentication-mode validation.
- [ ] Exercise provider API-key authentication and explicit Vertex AI service-account JSON in their respective modes. The Gemini adapter consumes JSON contents, including multiline contents. Preserve Gemini API-key mode and existing client construction when explicit Vertex credentials are absent. Tenant-specific policy belongs to Cloud.
- [ ] Remove explicit credential loading by URI, including startup `gemini.credentials-uri`, published `gemini.credentials-ref`, and the credential resource-loading path. Show retired syntax cannot cause a file or network resource read. Migrate affected examples, documentation and fixtures. Verify the closed Java surface with `LoomspanPublicSurfaceArchitectureTest`.

## Pipeline notes

All Loomspan products are in development with no QA or production deployments. Destructively replace contracts superseded by this ticket. Breaking other Loomspan projects that still use those contracts is acceptable until their own updates land. Do not add compatibility shims or retain legacy behavior to keep those projects working. Remove retired URI contracts without aliases or migration machinery.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** The change retires a serialized configuration contract and an adapter credential-loading path.
- **Reassessment triggers:** Reassess if the current checkout already implements the production contract and only localized fixture or documentation work remains.
