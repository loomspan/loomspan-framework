# PR 34 — Supply namespace-owned configuration to shared skill executors

## Outcome

A host can register one executor in a public namespace and let other namespaces use it. Each consuming namespace can configure that executor once for all its YAML skills. For example, billing can publish a new TypeSafe API key for all its decision skills while the executor owner supplies the gateway URL. A skill author can still choose a retry limit for a particular decision.

## Configuration and invocation contract

Use the existing YAML structure and the executor registration's existing config selector. TypeSafe declares `config = "typesafe"`. Given a public executor in `default`, the following startup configuration supplies two independently owned maps:

```yaml
loomspan:
  executor-config:
    default:
      typesafe:
        base_url: https://typesafe-gateway.example.com
    billing:
      typesafe:
        api_key: example-billing-key-v1
```

A billing skill can declare:

```yaml
executor: default-typesafe
config:
  max_attempts: 2
```

| Accessor on `ai.loomspan.api.extensions.v1.SkillExecutorInvocation` | Value for this invocation |
| --- | --- |
| `executorConfig()` | The owner's selected map, containing `base_url` |
| `consumerExecutorConfig()` (new) | The billing namespace's selected map, containing `api_key` |
| `skillConfig()` | The leaf's declared map, containing `max_attempts: 2` |

The consumer is the namespace that owns the YAML leaf declaring the executor reference. If a support workflow invokes the billing leaf, billing supplies the consumer map. The same rule applies when the leaf is invoked directly. Use the same config path in both namespaces, including existing dot-path semantics. Preserve existing visibility, authorization, required owner binding and invocation input semantics.

Return an immutable `Map<String, Object>` with the same value fidelity and container immutability as the existing configuration maps. Return an empty map for a same-namespace execution, an executor with no config selector, or a missing consumer entry. An explicitly empty consumer entry also yields an empty map. Supply only the selected subtree. Each executor decides which consumer settings it accepts and when to use them. The framework supplies the maps independently, with no automatic merge, additional configuration root, or framework-wide override allowlist.

## Namespace publication

Startup configuration and namespace publication must both supply consumer settings. For example, with the complete billing skill documents in `billingDocuments` and the key as billing's only centralized executor setting:

```java
var billingConfig = new ExecutionConfiguration("""
    loomspan:
      executor-config:
        billing:
          typesafe:
            api_key: example-billing-key-v2
    """);
reloader.publish(reloader.prepare("billing", billingDocuments, billingConfig));
```

Publication replaces the complete target namespace generation. Omitting the consumer entry in a subsequent billing publication clears it for fresh calls. Billing has authority over its own section only. Capture the consumer map with the leaf generation and preserve the existing owner-generation meaning of `executorConfigGenerationId()`.

## TypeSafe extension

Update `loomspan-decisions` in the sibling repository `../loomspan-extensions/extensions/decisions` as part of this work. For the shared TypeSafe executor, take the endpoint from owner settings (including TypeSafe's existing default URL), accept a consumer-supplied API key, and use the owner's key when the consumer omits it. Validate the selected key under existing TypeSafe rules. Reject a consumer-supplied URL with an error identifying the disallowed setting. Keep `max_attempts` in leaf configuration. These choices belong to TypeSafe. Other executors can define their own policies. The example above must work even when the owner supplies no key.

## Acceptance criteria

- [x] Direct and parent-mediated execution deliver the three maps shown above. Two billing leaves share the billing settings, and another consuming namespace receives its own settings. Ordinary mission input and existing namespace access controls retain their behavior.
- [x] The supported accessor returns immutable, independently scoped data with existing key, null and nested container fidelity. Same-namespace, absent, empty and selector-free cases yield an empty consumer map. Existing owner config selection still works, including for same-namespace calls.
- [x] Publishing billing alone rotates the key for newly captured calls. A handoff captured before publication still uses its original billing key and owner configuration. Removing billing's entry yields an empty map on fresh calls. Billing cannot publish settings into the owner namespace, and rotating billing leaves other namespaces' configurations and the owner generation ID intact.
- [x] The updated decisions pack runs against the changed framework. Verify the example through the actual TypeSafe executor and transport with a local endpoint, including destination, selected credential, rejected consumer URL, leaf retries and existing owner-only usage.
- [x] The framework README and decisions pack documentation explain the YAML layout, lookup, publication behavior and TypeSafe policy. Update the supported-surface expectations for the v1 invocation and run `LoomspanPublicSurfaceArchitectureTest` after changing production types.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** The work changes a supported extension contract across two repositories and adds namespace-generation capture to the invocation handoff.
- **Reassessment triggers:** Reassess if checkout inspection shows that the contract and cross-repository behavior are already implemented and only documentation or verification remains.

## Pipeline notes

Both projects are in development with no deployments. Coordinated breaking changes are authorized within this scope. Update affected consumers directly. Keep all existing versions, including the v1 package and artifact versions, and omit compatibility shims. Verify the decisions pack against the locally updated framework at its existing version.

## Execution notes

The developer approved the Full 5-Step Pipeline on 2026-11-20. Prior-ticket artifact deletions in Framework and the pre-existing decisions CHANGELOG.md deletion in Extensions are outside PR 34.
