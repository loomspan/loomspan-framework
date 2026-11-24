# Expose the active trace persistence policy for each namespace

## Outcome

An authenticated caller of `GET /_loomspan/observability/v1/instance` can tell which trace persistence policy each installed namespace will use before its first invocation. The Framework owns the accuracy of the snapshot. Cloud reconciliation and restart detection are outside this ticket.

## Acceptance criteria

- [x] With two namespaces installed on one engine, one using `ONERROR` and the other `ALWAYS`, a single authenticated snapshot taken before either is invoked reports each namespace's effective `tracePersistencePolicy`, `generationId`, and `publicationVersion`. Every namespace row includes the policy, including `NEVER` when effective and when the publication version is null.
- [x] A prepared replacement becomes visible only on publication. If publication overlaps snapshot construction, the affected row contains either the complete prior generation's values or the complete replacement generation's values. Its generation ID, publication version, and policy all come from one generation.
- [x] The top-level `tracePersistencePolicy` continues to report the active `default` namespace's policy, with the existing `ONERROR` fallback when its generation or runtime is absent. This holds even when another installed namespace uses `ALWAYS`.
- [x] The change stays within the existing Framework Console endpoint contract and requires no extension to the supported application-facing Java API. The Go–Java compatibility version number retains its current value.

## Pipeline notes

The project is in active development with no deployments. Destructive changes to internal representations are welcome. Omit compatibility shims. Any documentation should describe the resulting contract directly, with no references to the change, prior behavior, or migration. This ticket will be implemented without a GitHub PR.

## Execution profile

- **Recommended** — Full 5-Step Pipeline
- **Confidence** — high
- **Rationale** — The endpoint's serialized response contract changes, and the generation pairing must hold during concurrent publication.
- **Reassessment triggers** — None identified.

## Execution notes

- Selected profile: Full 5-Step Pipeline, confirmed by the developer.
- Starting checkout: `main` at `b6a8959`. Preserve the existing untracked ticket and the working-tree deletion of its predecessor under `ai/thoughts/future/`.
