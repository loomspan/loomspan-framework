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

`RestSkillInvocation(String skillName, Map<String,Object> input)` rejects null components. Loomspan validates and reference-resolves input before construction, then recursively copies and makes map/list containers unmodifiable. Null values and non-container leaf identity are preserved, including Spring `Resource` handles; the bytes behind a Resource are outside the immutability promise. Direct application construction has the same snapshot behavior.

The captured caller `Authentication` is available through `SecurityContextHolder` on the actual handler thread and the previous context is restored. Authentication, trace/session identifiers, and descriptions are not invocation components.

The handler is called once. An empty string is a successful result; null fails with `handler returned null`. `AccessDeniedException` remains unwrapped, an existing `SkillException` remains intact at the facade, and other runtime failures become a safe `SkillException` with their cause. Unlike annotation-defined Java skills, REST failures do not use the Java exception-to-text adapter. JVM `Error` is not caught.

REST uses the existing public observation shape and direct mission/tool trace records without model attempts or a trace kind field. Console's registered-skill catalog supplies the distinct `REST` label and manifest location/text.

## Evidence

- `ApplicationApiValueTest` and `LoomspanPublicSurfaceArchitectureTest` protect shape and boundaries.
- `YamlSkillCapabilityRegistrarTests` protects conditional bean selection and handoff.
- `DefaultSkillTemplateTest` and `ExecutionCoordinatorTest` protect facade and lifecycle failures.

