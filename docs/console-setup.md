# Console and MCP setup

Use a Console package matching the framework's coordinated version. Follow the
[Console runtime package guide](../loomspan-console/release/README.md) to start it.
This setup applies to a running servlet application; the command-line quickstart
does not expose HTTP endpoints until you apply its
[servlet continuation](quickstart-java.md#continue-with-console).

For a release, obtain the package for your operating system from the matching
framework release tag's assets and verify its checksum as described in the runtime
package guide. For a snapshot source evaluation, build Console from the same
framework commit using the [Console build instructions](../loomspan-console/README.md#canonical-production-commands).
A matching snapshot version string alone does not identify the same source build.

## Enable the target observability API

Servlet applications can expose the read-only operator API under
`/_loomspan/observability/v1/**`. It is disabled by default and is not exposed
through Actuator or CORS. Use HTTPS whenever the listener is reachable beyond a
trusted local boundary, generate at least 32 random bytes, encode them as
unpadded base64url, and keep the resulting key in external configuration:

```yaml
loomspan:
  observability:
    enabled: true
    auth:
      api-key: ${LOOMSPAN_OBSERVABILITY_API_KEY}
    completion-grace-ttl: 15m
    trace-catalog-metadata-ttl: 24h
```

The key must be 32–512 printable, non-whitespace ASCII characters and is loaded
at startup; rotate it by restarting the application.

Applications using Spring Security must let the reserved namespace reach
Loomspan's filter while retaining their normal rules elsewhere. Loomspan does not
create or reorder the application's `SecurityFilterChain`:

```java
@Bean
SecurityFilterChain applicationSecurity(HttpSecurity http) throws Exception {
    return http.authorizeHttpRequests(requests -> requests
            .requestMatchers("/_loomspan/observability/v1/**").permitAll()
            .anyRequest().authenticated())
        .build();
}
```

The `permitAll` rule does not make the API unauthenticated; the Loomspan key is
still mandatory. A generic proxy or host-security `401`/`403` occurs before the
adapter and is distinct from the adapter's `LOOMSPAN_API_KEY_REJECTED` problem.
The normal servlet context path applies to every route. If startup detects an
invalid configuration or an overlapping application mapping, it logs a
configuration diagnostic and leaves the entire optional adapter, observation, and
completion grace behavior disabled.

TLS and any host/proxy rejection remain application infrastructure
responsibilities. A server-to-server Console client on the same listener needs
no CORS configuration. Rotating the key prevents later requests but does not
retroactively revoke a transfer that was already authenticated and admitted.

## Connect the assistant

Start Console, open its printed loopback pairing URL, and configure the target
application address and its observability API key. In the paired browser, enable
MCP under Settings. Configure your assistant host with the Console loopback MCP
endpoint and bearer credential through its protected configuration mechanism.
The MCP credential is separate from the target application key.

Install the version-matched `loomspan-console` Agent Skill through
[loomspan-install](../agent-skills/loomspan-install/SKILL.md). An assistant host
must be able to reach the local loopback listener. Installing the skill alone
cannot enable live inspection.

For ongoing investigation, use the
[Console skill](../loomspan-console/agent-skills/loomspan-console/SKILL.md) and
[trace debugging reference](../agent-skills/loomspan-docs/references/skill-authoring/traces-and-debugging.md).
For direct adapter diagnosis, see the
[observability HTTP reference](../agent-skills/loomspan-docs/references/skill-authoring/observability-http.md).

## Verify and inspect

1. In the paired browser, select **Connect** for the intended application address
   and key. Check its connection and compatibility result. Include any servlet
   context path in the target address.
2. Ask your assistant to discover its installed Console MCP tools and call
   `LOOMSPAN_get_runtime`. Verify that the intended target is selected and inspect
   authentication and compatibility facts. A browser connection alone does not
   prove the assistant's MCP connection works.
3. Ask it to follow `loomspan-console` to find the recent `triageSupportRequest`
   execution and inspect its finalized trace. Use returned `traceId` values;
   distinguish the observed outcome, captured content and usage from an inference
   about the quality of the triage brief. For your own application, select the
   relevant skill and execution instead.

```text
Use loomspan-console to verify the selected target and inspect the recent
triageSupportRequest execution. Discover the installed tools, check runtime
status, then use the matching playbook to retrieve the finalized trace.
Report its outcome, traceId, recorded model interaction and available usage.
Distinguish recorded facts from your interpretation and identify missing evidence.
```

If MCP tools are missing, check the assistant host's connection and refresh
requirements. If that host cannot reach the local listener, use Console's browser
trace views and report that assistant evidence retrieval is unavailable. If the
artifact has expired or the application restarted, rerun the example and reconnect
within its completion grace; absence of an artifact does not establish execution
failure. Use the Console skill's playbooks for precise runtime and trace errors.
