# Provider Resource Sharing

## Status and scope

Separate framework investigation identified during the [namespace design](skill-namespaces.md). Resource sharing is not selected for the namespace implementation. It may be worthwhile, but requires measurements and an explicit ownership decision before implementation or tickets commit to a solution.

Choose the simplest solution that fulfills demonstrated requirements. Reuse existing framework features; destructive changes are welcome and compatibility shims are not required. Preserve the repository's supported API boundaries and tests. No Cloud implementation work is included.

## Current behavior and namespace impact

Source inspection found that `LoomspanAutoConfiguration` supplies a runtime factory that creates a new `ExecutionRuntime` and `NamedAiConnectionRegistry` for each prepared generation. The registry eagerly builds each configured provider connection. `ExecutionRuntime.close()` is single-shot and destroys its registry; the registry also protects destruction against repeated calls.

The namespace design retains this independent ownership. Every startup namespace receives the same startup settings but constructs its own provider resources. Ten startup namespaces with three configured connections therefore construct ten registries containing thirty connection-runtime entries, even if most namespaces contain only Java skills. This count does not establish the number of actual connection pools, sockets, threads, or remote connections; those depend on provider implementation and must be measured.

An invocation snapshot retains existing generations and their resources; it does not create new provider clients per invocation. Prepared candidates and older generations retained by queued/running work can increase resource counts beyond the active namespace count. Existing cleanup must continue to release rejected/abandoned candidates and retire superseded generations when actual ownership ends.

## Questions to answer with measurements

- What namespace counts, configured connections, provider mix, and publication/concurrency patterns represent the intended framework scale? Establish these inputs before defining pass/fail thresholds.
- What are startup and candidate-preparation time, retained memory, thread count, connection-pool/socket usage, and resource counts for a baseline and representative namespace counts?
- How do costs change for Java-only namespaces that still receive configured connections?
- How much additional retention occurs during repeated publications with queued or long-running invocations holding old generations?
- Do failed preparation, rejected candidates, removal, retirement, and shutdown release resources under their existing ownership guarantees?

Use controlled comparisons with the same settings and workload. Separate client object counts from measured transport resources. Record provider-specific behavior and measurement limits; do not infer live connections or costs merely from the number of client objects. Do not add a permanent reporting or benchmark subsystem unless separately justified.

No acceptable scale or resource threshold has yet been specified. The investigation must produce evidence, explicit requirements/thresholds, and a recommendation. If independent ownership is too expensive for the required scale, optimization becomes a prerequisite for use at that scale, even though it remains a separate implementation effort.

## Options to evaluate

| Option | Benefit | Cost or limitation |
|---|---|---|
| Retain independent ownership | Simplest lifecycle; each generation closes its own resources | Duplicate construction for identical configurations |
| Share only the known-identical startup registry | Removes startup duplication without general configuration matching | Requires shared ownership; later publications can still duplicate resources |
| Share resources across equivalent configurations | Potentially reduces duplication across publications and namespaces | Requires exact equivalence semantics, concurrent acquisition/retirement, and configuration/credential-change handling |
| Separate generation settings from shareable provider resources | Establishes an ownership boundary if sharing is justified | Separation alone does not make sharing safe or eliminate ownership bookkeeping |

These are alternatives for investigation, not adopted features. Do not add speculative interfaces, resource caches, reference-counting hooks, or lazy client construction to the namespace work.

## Ownership considerations if sharing is selected

The current chain is invocation ownership -> generation -> independently owned provider resources. Sharing adds a resource lifetime spanning generations. Existing single-shot `close()` is insufficient to decide when the last shared owner has finished: it prevents repeated destruction but not destruction by the first retiring owner while another still needs the resource.

A candidate design could give each generation one handle to shared resources, release that handle on generation retirement/disposal, and close the underlying registry after its last owner releases it. Existing invocation leases would continue to retain generations; there need not be a second per-invocation provider lease. This is a possible design, not an implementation decision.

Keep session state, inputs, usage counters, and generation settings separate from shared provider infrastructure. Republishing one namespace must never mutate another generation's client configuration. Check current settings mutability rather than assuming defensive copies make every exposed value deeply immutable. Verify concurrent provider use and all credential/configuration distinctions relevant to resource equivalence if broader deduplication is considered.

Any selected design must address partial construction failure, candidate rejection/abandonment, concurrent retirement, namespace removal/recreation, final-owner release, and shutdown with outstanding physical work. Closing resources must not depend merely on returning a timeout response. Cleanup failures must preserve existing failure-reporting and ownership semantics.

## Deliverables before implementation

1. Measured baseline and representative-scale results, including the workload, provider setup, and limitations.
2. A decision to retain independent ownership or adopt a bounded sharing approach, justified against explicit resource requirements.
3. If sharing is warranted, an ownership/lifecycle design and focused acceptance tests for the cases above, with before/after measurements.

Keep construction and closing centralized in the existing runtime/registry paths during namespace work. Code proximity alone is not a reason to combine namespace semantics with a new resource-sharing lifetime.
