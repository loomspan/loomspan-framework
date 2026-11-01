---
audience: loomspan-application-developer
status: development
applies_to: bundled-loomspan-revision
coverage: source-verified
---

# REST Handler SPI

`RestSkillHandler` is Loomspan's sole supported SPI:

```java
@Bean
RestSkillHandler restSkills() {
    return invocation -> client.fetch(
            (String) invocation.input().get("accountId"));
}
```

If REST manifests exist, the application MUST expose exactly one handler bean. That handler serves every REST declaration and selects behavior by `invocation.skillName()` when needed. Per-skill handlers, framework-owned HTTP clients, routing manifest fields, retries, response mapping, and token exchange are not supported by this contract.

`RestSkillInvocation(String skillName, Map<String,Object> input, String generationId)` rejects null components and blank generation IDs. Loomspan validates and reference-resolves input before construction, then recursively copies and makes map/list containers unmodifiable. Null values and non-container leaf identity are preserved, including Spring `Resource` handles; the bytes behind a Resource are outside the immutability promise. `skillName()` is the canonical qualified target identity, so `tax-lookup` and `orders-lookup` are distinct even with the same local declaration. `generationId()` identifies the captured executing target generation: an orders entry calling tax receives the tax ID in the REST invocation, while the admitted root ID remains orders-scoped. The preparation-time invoker captures both values; replacement/removal and business input cannot reroute retained work. The fixed handler can use it to select configuration staged before publication; see [skill-reload.md](skill-reload.md).

The captured caller `Authentication` is available through `SecurityContextHolder` on the actual handler thread and the previous context is restored. Authentication, trace/session identifiers, and descriptions are not invocation components.

The handler is called once. An empty string is a successful result; null fails with `handler returned null`. `AccessDeniedException` remains unwrapped, an existing `SkillException` remains intact at the facade, and other runtime failures become a safe `SkillException` with their cause. Java method and serialization failures also propagate; neither Java nor REST uses an exception-to-successful-text adapter. JVM `Error` is not caught.

REST uses the existing public observation shape and direct mission/tool trace records without model attempts or a trace kind field. Console's registered-skill catalog supplies the distinct `REST` label and manifest location/text.

## Evidence

- `ApplicationApiValueTest` and `LoomspanPublicSurfaceArchitectureTest` protect shape and boundaries.
- `NamespaceExecutionAuthorityIntegrationTest` verifies distinct qualified REST identities, retained target IDs, caller scope, parallel isolation and captured settings.
- `SkillGenerationManagerTest` and REST startup tests protect fixed handler selection and generation assembly.
- `DefaultSkillTemplateTest` and `ExecutionCoordinatorTest` protect facade and lifecycle failures.

## Namespace publication boundary

Complete publications require namespace, supplied documents and ExecutionConfiguration.
Startup namespaces are Private; foreign outgoing references require a Public target.
Fixed Java methods remain assigned to their original namespace across replacement
and explicit recreation; withdrawal preserves beans and retained work. There is no
activation API or configuration inheritance. See [publication](../java-api/skill-reload.md)
for complete signatures, sequencing and retirement. This slice implements preparation
and installation, retained admission and captured cross-namespace execution.
Serialized observation and Console integration remain PR 27.5.

Evidence: `SkillReloaderTest`, `SkillDependencyValidatorTest`, `NamespaceStartupIntegrationTest`.
