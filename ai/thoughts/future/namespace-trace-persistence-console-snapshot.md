# Expose the active trace persistence policy for each namespace

## Motivation

A shared engine can run applications with different trace persistence policies. Loomspan Cloud keeps the owner's saved selection and uses the Framework Console instance snapshot to observe what is running. After Go restarts or loses a publication response, it should be able to read the effective policy for each application directly from a fresh snapshot.

## Requested Console contract

Add `tracePersistencePolicy` to each entry in `namespaces` returned by `GET /_loomspan/observability/v1/instance`. Return the effective policy (`NEVER`, `ONERROR`, or `ALWAYS`) captured by the same active generation that supplies the entry's `generationId` and `publicationVersion`. This pairing must hold even if a publication takes effect while the snapshot is being built. The policy is available before the first invocation.

Preserve the existing top-level `tracePersistencePolicy` and its current behavior for existing Console clients. Cloud can compare the namespace policy with the owner's saved selection. It continues to use JVM and generation identity to confirm a particular restart, since successive generations may have the same policy.

## Acceptance check

Install two namespaces on the same engine, one using `ONERROR` and the other using `ALWAYS`. Before invoking either application, read one authenticated instance snapshot. Verify that each namespace reports its own policy alongside the correct generation ID and publication version.
