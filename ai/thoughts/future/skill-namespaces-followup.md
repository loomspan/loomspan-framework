# Skill Namespaces — Post-implementation Follow-up

## Scope and timing

All new namespace resource measurement is deferred until after namespace implementation. It is not a prerequisite or acceptance gate for PR 27 implementation tickets. Ordinary correctness tests for resource ownership, cleanup, and concurrency remain part of implementation; they do not require a new resource-measurement program.

Keep independent generation-owned runtimes and provider registries during implementation. Shared startup settings mean equal configuration values, not shared runtime objects. Ten namespaces with three configured connections can produce thirty connection-runtime entries, including unused connections in Java-only namespaces; actual memory, pools, sockets, and threads depend on provider behavior. Invocation snapshots lease existing generations rather than constructing clients. Prepared candidates and queued/running work retaining old generations can increase resource retention.

## Resource measurement before considering optimizations

Keep the current ownership model as the baseline. This task is documented for later planning; no benchmark execution, optimization, or follow-up ticket creation is authorized by this handoff. Deployment scale targets and acceptable thresholds remain inputs to this later work, not unresolved namespace implementation decisions.

1. Establish representative namespace counts, connections per namespace, provider mix, and publication concurrency for the intended deployments. Set acceptable startup/preparation time and memory, thread, and transport-resource thresholds before judging the results. These inputs and thresholds have not yet been selected.
2. Measure a baseline and representative scales using the same settings and controlled workloads. Cover Java-only startup namespaces both without configured providers and with unused configured connections; API-populated namespaces; concurrent preparation for the same and different namespaces; and publication churn while queued or running work retains older generations.
3. Record startup and preparation time, peak and retained memory, thread counts, actual pools/sockets where observable, and resource retention after work releases ownership. Distinguish registry/client object counts from actual transport resources. Include failed/abandoned candidates, stale publication rejection, removal, retirement, and shutdown to verify cleanup under existing physical-work ownership rules.
4. Produce reproducible workload/configuration details, measurements with provider-specific limitations, comparison against the agreed thresholds, and a recommendation. Retain independent ownership if it meets the requirements. If it does not, identify the measured bottleneck before proposing a separate optimization; any required optimization must be completed before claiming support for the affected scale.

The [Provider Resource Sharing](provider-resource-sharing.md) investigation records possible later options, not committed work. Consider those options only after the measurement task demonstrates a need. No sharing cache, additional resource reference counting, lazy client creation, speculative sharing interfaces, or permanent benchmark/reporting subsystem is selected. Keep construction and closing centralized in the existing runtime/registry paths so any justified later work can address ownership deliberately. Splitting settings from resources alone would not eliminate the extra lifetime management required for safe sharing.

