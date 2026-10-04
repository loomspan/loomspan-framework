# Console and MCP setup

Use a Console package matching the framework's coordinated version. Follow the
[Console runtime package guide](../loomspan-console/release/README.md) to start it.
This setup applies to a running servlet application; the command-line quickstart
does not expose HTTP endpoints.

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
