# Loomspan Framework

A Java 21, Spring Boot 4.1, and Spring AI 2–based agentic framework that uses LLM‑driven skills within a Hierarchical Task Network (HTN) architecture.

Loomspan while still an HTN is fundamentally different from traditional HTNs. Instead of relying on rigid, rule‑based planners, Loomspan blends classical HTN structure with LLM‑powered reasoning, allowing agents to dynamically decompose missions, select skills, and orchestrate complex workflows. 

At its core, Loomspan treats skills as the fundamental building blocks of capability. YAML manifests define model-backed skills, and Java `@SkillMethod` annotations define directly callable application skills. Both sources share one exact-name catalog, authorization checks, and execution lifecycle. Model-backed parents can call locally allowed children from either source. This creates a flexible planning system that combines LLM reasoning with explicit contracts and ordinary Spring services.


## Why Loomspan?
Most HTN planners (like JSHOP2 or PANDA) rely on static, hand‑coded methods. They’re powerful, but brittle. Loomspan takes a different approach:
- LLM‑driven decomposition
The agent decides how to break down a mission in real time.
- Skill‑based execution
Each skill is a modular, reusable capability that can call others.
- Natural‑language domain modeling
No DSLs or planning languages — skills are written in plain English.
- Spring Boot foundation
Easy integration, dependency injection, configuration, and deployment.
The result is a hybrid system that combines the structure of HTNs with the adaptability of modern LLMs.


## Beta compatibility

Loomspan is currently in beta. The supported Java API and documented
configuration keys and behavior may change between beta releases, including
breaking changes. Beta releases do not guarantee source, binary, or behavioral
compatibility with earlier betas. Release notes will identify breaking changes
and migration steps; review them before upgrading and pin an exact beta version.
The first production release will be `1.0.0`.

## Requirements

- Java 21 or newer
- Maven 3.9 or newer (the included Maven wrapper is recommended)
- For model-backed YAML skills, a named Loomspan AI connection using the Ollama, OpenAI, Anthropic, or Gemini driver

## Project Structure

The Loomspan Framework repository contains two projects:

- `loomspan-spring-boot-starter`: the core starter.
- `loomspan-console`: an independent Go module with an embedded React application. Its explicit build uses pinned Go, Node.js, and npm toolchains and is not part of the Maven reactor.

Ordinary Java development and `mvn test` do not invoke Console tooling. See
[`loomspan-console/README.md`](loomspan-console/README.md) for the Console build
and local hot-reload workflow.

## Getting Started

Add the starter to your application:

```xml
<dependency>
    <groupId>ai.loomspan</groupId>
    <artifactId>loomspan-spring-boot-starter</artifactId>
    <version>1.0.0-beta.2-SNAPSHOT</version>
</dependency>
```

Configure application-owned AI connections, skill locations, and named Loomspan model aliases in `application.yml`:

```yaml
server:
  port: 8081

loomspan:
  connections:
    ollama-main:
      driver: ollama
      base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
    openai-main:
      driver: openai
      api-key: ${OPENAI_API_KEY}
  session:
    mission-timeout: 6000s
  skills:
    locations:
      - classpath:/skills/**/*.yml
      - classpath:/skills/**/*.yaml
  models:
    granite4-tiny:
      connection: ollama-main
      provider-model: ibm/granite4:tiny-h
    default-model:
      connection: ollama-main
      provider-model: ibm/granite4:tiny-h

execution-trace:
  persistence: ALWAYS
```

Every LLM-backed YAML skill must name one of the entries under `loomspan.models`. Java skills do not require a framework model. `default-model` is an ordinary model key; it is not selected automatically.

A connection is a concrete endpoint/account and chooses a built-in `driver`; a model is a framework alias that chooses a connection and the request-level `provider-model`. Multiple connections may use the same driver. Loomspan does not merge or inherit `spring.ai.*` settings. Keep credentials in environment variables or an external secret store.

Provider retries are owned by each application connection, not by YAML skills. The default is three total attempts with 500 ms initial backoff, a 2.0 multiplier, a 5 s cap, and 0.2 jitter. Set `provider-retry.enabled: false` (or `max-attempts: 1`) for one attempt. Loomspan disables the supported Spring AI clients' own application-level retries so these limits describe actual downstream calls.

The `openai` and `anthropic` drivers use Spring AI 2's official SDK-backed clients. Their optional `base-url` is the SDK service root: OpenAI appends `/chat/completions`, so include `/v1` in the root when the service requires it; Anthropic appends `/v1/messages`. Both accept common static `headers`; use that map for Anthropic beta or other supported custom headers. OpenAI additionally supports organization/project IDs and the explicit OpenRouter compatibility profile. The former OpenAI completion-path override and Anthropic completion-path/version/beta fields are rejected rather than aliased. The `ollama` driver uses its native `/api/chat` protocol. Gemini supports either API-key mode or Vertex AI mode (`project-id` and `location`, with optional credentials resource), but not both on one connection.

Several model aliases can share one connection while choosing different provider model IDs. An OpenAI-compatible gateway is another named connection using `driver: openai`; it does not need a vendor-specific driver:

```yaml
loomspan:
  connections:
    openrouter:
      driver: openai
      base-url: https://openrouter.ai/api/v1
      api-key: ${OPENROUTER_API_KEY}
      headers:
        HTTP-Referer: ${OPENROUTER_SITE_URL}
      openai:
        compatibility-profile: openrouter
      provider-retry:
        enabled: true
        max-attempts: 3
        initial-backoff: 500ms
        multiplier: 2.0
        max-backoff: 5s
        jitter: 0.2
  models:
    fast:
      connection: openai-main
      provider-model: gpt-4o-mini
    deep:
      connection: openai-main
      provider-model: gpt-5
    routed-sonnet:
      connection: openrouter
      provider-model: anthropic/claude-sonnet-4
```

Endpoint compatibility is feature-specific: verify tools, media, structured output, reasoning fields, and usage reporting against the selected service.

By default, Loomspan discovers `classpath:/skills/**/*.yaml`. Add the `.yml` pattern, as above, when your application uses that extension.

### Invoking a skill

Inject `SkillTemplate` and invoke a Java or YAML skill with a map (or an object that can be converted to a map). The result is returned as text; use a YAML `output_schema` for model-backed structured output. Java results retain Jackson serialization.

```java
import ai.loomspan.api.SkillTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class InvoiceWorkflow {
    private final SkillTemplate skills;

    public InvoiceWorkflow(SkillTemplate skills) {
        this.skills = skills;
    }

    public String checkInvoice(String invoiceText) {
        return skills.invoke("duplicateInvoiceChecker", Map.of("payload", invoiceText));
    }
}
```

The supported starter Java API is closed to these eight types in `ai.loomspan.api`: `SkillTemplate`, `SkillExecutionView`, `SkillExecutionEvent`, `SkillMethod`, `SkillParam`, `SkillException`, `SkillInputValidationException`, and `SkillInputValidationIssue`. A Java `public` modifier does not add a type to this API: everything under `ai.loomspan.internal` is implementation detail and may change without a compatibility shim, while `ai.loomspan.autoconfigure` contains Spring-facing integration and configuration-binding machinery rather than an application extension API. Documented configuration keys and behavior remain user-facing contracts. `SkillTemplate` is injectable and easy to mock in application tests, but replacing its framework bean or implementing Loomspan internals is unsupported. There are currently no supported Loomspan-specific SPIs or bean overrides.

The installable [Java API knowledge set](agent-skills/loomspan-docs/references/java-api/README.md)
provides LLM-oriented routing and source-verified guidance for this surface.

For integration testing, configure a real or local protocol-compatible named connection and invoke the YAML skill through `SkillTemplate`. Loomspan's supported-surface integration test follows this pattern: it supplies a local OpenAI-compatible endpoint through `loomspan.connections`, invokes an LLM-backed YAML skill that calls an annotation-defined `@SkillMethod`/`@SkillParam` leaf, and observes only `SkillExecutionView` values. Tests should not replace internal resolvers, coordinators, model factories, registries, or virtual-file-system beans.

Successful observers receive a session ID and immutable, current-version `SkillExecutionEvent` values. These events are intended for trusted development and debugging, may contain application business data, and are not a durable or comprehensively sanitized trace contract. Invalid caller input raises `SkillInputValidationException`, authorization failures remain Spring Security `AccessDeniedException`, and other runtime failures crossing the facade become a safe `SkillException`.

## Defining Skills

### Bootstrap version-matched Loomspan skills

The repository publishes a one-shot `bootstrap` Agent Skill. It reads the
current Maven project's Loomspan dependency and installs only the requested
Loomspan skills from the matching Git revision. Ask the current coding agent to
read it directly from GitHub; the bootstrap itself does not need to remain
installed:

```text
Read and follow the bootstrap skill at https://github.com/loomspan/loomspan-framework/tree/main/agent-skills/bootstrap to install loomspan and loomspan-docs for this project. Do not install the bootstrap skill itself.
```

Bootstrap is a single instruction-only `SKILL.md`. It never asks the agent to
download or execute bootstrap code. Repository inspection, approval, and skill
installation are delegated to the current agent harness's native capabilities,
so the same prompt can be used with Codex, Gemini CLI or Antigravity, Windsurf,
and Devin. The harness chooses or asks for its supported user, workspace, or
repository installation scope.

Release versions resolve to the exact `v<version>` Git tag. The current
development snapshot resolves to `main` only when `main` declares that exact
snapshot version. Bootstrap asks before replacing an existing installed skill
and never edits the project or changes its framework dependency. The
version-coupled skills declare `metadata.loomspan-version`; bootstrap reports
that installed version and verifies the staged marker before placement.

Repository maintainers coordinate Maven, fixture, documentation, and Agent
Skill versions with one command:

```bash
python scripts/loomspan_version.py check
python scripts/loomspan_version.py set 1.0.0-beta.2
# Review, test, and commit the release version.
python scripts/loomspan_version.py tag 1.0.0-beta.2
```

`set` requires a clean worktree and replaces the exact current version in all
tracked text files, including the reactor POMs and version-coupled skill
metadata. It also refreshes fixture byte lengths and evaluation fixture
checksums. Historical evaluation records are preserved. It does not commit.
`tag` requires a clean, committed, non-SNAPSHOT
version and creates the annotated `v<version>` tag used by bootstrap; it does
not push. After releasing, use `set` again to begin the next development
snapshot. Git tags are the release-version catalog, so there is no separate
bootstrap version list to update.

Keep the project on the next anticipated beta version with `-SNAPSHOT` during
normal development. Remove that suffix when preparing the beta release. The
current development version is `1.0.0-beta.2-SNAPSHOT`. A complete beta cycle looks like:

```text
1.0.0-beta.2-SNAPSHOT -> 1.0.0-beta.2 -> tag v1.0.0-beta.2 -> 1.0.0-beta.3-SNAPSHOT
```

Starting from a clean development snapshot worktree, prepare the release.
If the version is already `1.0.0-beta.2`, skip the `set` command. Pushing
the tag below starts the Console release and automatic Maven Central publication:

```bash
python scripts/loomspan_version.py check
python scripts/loomspan_version.py set 1.0.0-beta.2

# Review and run the appropriate tests before committing.
git add .
git commit -m "Release 1.0.0-beta.2"

python scripts/loomspan_version.py tag 1.0.0-beta.2
git push origin main v1.0.0-beta.2
```

After releasing beta 2, start beta 3 development from a clean worktree. This
updates the working branch; the beta 2 tag continues to identify its release:

```bash
python scripts/loomspan_version.py set 1.0.0-beta.3-SNAPSHOT
git add .
git commit -m "Begin beta 3 development"
git push origin main
```

Continue incrementing the beta number for later betas. Release candidates use
`1.0.0-rc.1`, and the first production release uses `1.0.0`.

Substitute the actual release and next-development versions in these commands.
Bootstrap resolves a release from its exact `v<version>` tag. It resolves a
SNAPSHOT from `main` only when the root POM and version-coupled skill metadata
on `main` declare that exact SNAPSHOT version.

### Maven Central releases

The `release` Maven profile builds sources and Javadoc JARs, signs artifacts,
and configures Sonatype's Central Publishing plugin. Published coordinates are
`ai.loomspan:loomspan-framework-parent` (POM) and
`ai.loomspan:loomspan-spring-boot-starter` (JAR and attached documentation).

To review the release build locally without signing, uploading, or changing
the current development version, run these commands (`mvnw.cmd` on Windows):

```bash
python scripts/loomspan_version.py check
python -m unittest discover scripts/tests -v
./mvnw --batch-mode --no-transfer-progress clean verify
./mvnw --batch-mode --no-transfer-progress -Prelease -pl loomspan-spring-boot-starter -am -DskipTests -Dgpg.skip=true verify
```

Inspect the starter's binary, `-sources.jar`, and `-javadoc.jar` in
`loomspan-spring-boot-starter/target`. These checks do not exercise signing,
Central credentials, or Sonatype's server-side validation. A manual run of
the **Maven Central Release** GitHub Actions workflow performs the same
build-only checks, with no publishing secrets.

After review, the existing version script prepares and tags the committed
release. A pushed `v<version>` tag must match the root POM and must not contain
`SNAPSHOT`. The workflow checks coordinated versions, runs all Java tests
(including the public API architecture test), and verifies release packaging
before its upload job uses these GitHub Actions secrets:

| Secret | Purpose |
| --- | --- |
| `CENTRAL_TOKEN_USERNAME` | Central Portal user token username |
| `CENTRAL_TOKEN_PASSWORD` | Central Portal user token password |
| `GPG_PRIVATE_KEY` | ASCII-armored signing private key |
| `GPG_PASSPHRASE` | Signing key passphrase |

The verified Sonatype namespace must cover `ai.loomspan`. Before the first
upload, ensure the corresponding GPG public key is available on a keyserver
supported by [Sonatype's GPG requirements](https://central.sonatype.org/publish/requirements/gpg/).
The workflow imports the private key on its runner and supplies the passphrase
through an environment variable.

The upload uses `autoPublish=true` and waits up to 30 minutes for Sonatype to
report `PUBLISHED`. Publication happens automatically after validation; no
manual Publish action is needed in the [Central Portal](https://central.sonatype.com/publishing/deployments).
Maven Central search indexing may take additional time. Published versions
cannot be overwritten; fixes require a new version.

The Console release also runs on the same tag and can publish independently
of Maven. Check both workflows before announcing a release. If an upload job
fails, inspect the Portal before rerunning that job: a deployment may already
exist even if GitHub did not receive its final status. Manual workflow runs
only verify; use the original tag run when retrying an upload.

After `loomspan-docs` is installed, prepare the knowledge set that matches the
work:

```text
Use the loomspan-docs skill to prepare skill-authoring.
Use the loomspan-docs skill to prepare java-api.
```

Use `skill-authoring` to design or diagnose skill trees. Use `java-api` to work
with the closed application-facing Java surface, including invocation, Java
skills, observations, errors, and compatibility boundaries.

### YAML skills

A YAML skill declares a configured `model` and may use model execution settings. `prompt` supplies private instructions in addition to the public `description`.

The YAML `name` is the skill's single public identity and must match `^[A-Za-z_][A-Za-z0-9_]{0,63}$`: use 1-64 characters, start with an ASCII letter or underscore, and then use only ASCII letters, digits, or underscores. Names are case-sensitive and Loomspan does not trim, sanitize, normalize, truncate, or alias them. Descriptive lowerCamelCase names such as `duplicateInvoiceChecker` and `expenseLookup` are the recommended authoring style, though underscores and uppercase starts are also valid. Java annotations follow the same exact-name rules and share the namespace. Use registered names in `SkillTemplate`, `allowed_skills`, and property-level `evidence` expressions.

Duplicate Java/Java, YAML/YAML, or Java/YAML names fail startup with both declaration locations. After registration completes, every exact `allowed_skills` reference must resolve. Missing children fail startup even if an access policy would hide them.

```yaml
name: duplicateInvoiceChecker
description: >
  Checks whether a given invoice already exists in the expense system.
  First, parses the raw invoice text to extract vendor, amount, and date.
  Then, retrieves existing expenses and compares them to determine
  if the invoice is a duplicate.
model: granite4-tiny
planning_mode: true
concurrency: true
max_steps: 10
allowed_skills:
  - name: invoiceParser
    required: true
  - name: expenseLookup
    min_tasks: 1
    max_tasks: 2
output_schema:
  type: object
  properties:
    isDuplicate:
      type: boolean
      evidence: invoiceParser and expenseLookup
      description: True if a matching expense was found in the system
    vendorName:
      type: string
      evidence: invoiceParser
      description: Vendor name extracted from the invoice
    totalAmount:
      type: number
      evidence: invoiceParser
      description: Total amount extracted from the invoice
    invoiceDate:
      type: string
      evidence: invoiceParser
      description: Invoice date in ISO-8601 format (YYYY-MM-DD)
    reasoning:
      type: string
      evidence: invoiceParser and expenseLookup
      description: Brief explanation of why the invoice was or was not considered a duplicate
  required: [isDuplicate, vendorName, totalAmount, invoiceDate, reasoning]
  additionalProperties: false
output_schema_max_retries: 2
```

Important execution settings:

- `planning_mode`: enables the step-based HTN executor only when set to `true`. It is disabled by default.
- `concurrency`: valid only when `planning_mode: true` is explicitly declared. It defaults to `true` for such planners; set it to `false` to require serialized execution while retaining accepted `parallelGroup` metadata. Generated tasks may use an exact nullable `parallelGroup` matching `^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$`. Only consecutive tasks with the same non-null group form an ordered execution unit, dependencies may target only earlier units, and a full unit must fit within the remaining step budget before any member starts. For omitted or explicit `true`, the coordinator admits every member of a valid group before dispatching them concurrently, waits for every ordinary outcome, and folds results and diagnostics in task-list order so later units and final synthesis see sequential-equivalent state. Ungrouped tasks and `concurrency: false` groups execute serially. A group is an author assertion that its members are independent and safe to overlap; it records eligibility, while frame intervals establish whether work actually overlapped. Mission timeout, interruption, or partial dispatch stops later admission, uses bounded cleanup, folds available outcomes in task order, blocks admitted tasks with no outcome, marks the plan stale, and fences writes after the logical cutoff; external side effects remain the capability author's responsibility.
- `allowed_skills`: a structured sequence of direct child declarations. Every entry requires an exact registered Java or YAML `name`; scalar entries are invalid. Optional `min_tasks` and `max_tasks` are non-negative generated-plan task counts, and `required: true` makes the effective minimum at least one. Defaults are minimum `0`, unbounded maximum, and `required: false`; the effective minimum is `max(min_tasks, required ? 1 : 0)`. Task-count fields require explicit `planning_mode: true`, while an unconstrained `{name: ...}` entry is valid for direct execution. A positive-minimum child must be visible and authorized before planning begins. Loomspan counts exact case-sensitive `PlanTask.capabilityName` bindings, includes the bounds in the planning prompt, permits one corrective planning attempt, and rejects a still-invalid plan before storage or execution. These bounds do not count runtime invocations and do not alter evidence expressions or authorization.
- `max_steps`: bounds planning-loop steps. Each assigned task costs one step and final synthesis costs one step; correcting an invalid action for the same assignment does not consume another task step.
- `prompt`: optional private instructions for an LLM-backed skill.
- `thinking_level`: selects a configured thinking level for models that support it.
- `input_schema`: validates and describes the expected input. Supported types are `object`, `array`, `string`, `number`, `integer`, `boolean`, and `attachment`.
- `output_schema`: validates the model response. When present, `output_schema_max_retries` defaults to `2` and accepts values from `0` through `3`.
- `linter`: currently supports a `regex` linter with `max_retries` from `0` through `3`.
- `output_schema.properties.<name>.evidence`: attaches a nonblank Boolean expression over exact direct `allowed_skills` names to an immediate root output property. Operators `and` and `or` are case-insensitive; skill names remain case-sensitive; `and` binds more tightly than `or`, and parentheses override precedence. Plan validation checks every annotated property against planned child names; final validation checks only annotated properties present in the candidate against successfully completed direct children. Nested child internals do not leak upward. The annotation is orchestration metadata, not candidate JSON, and enforces supportability rather than factual truth or workflow order. Nested-schema annotations are unsupported.
- `rbac_roles`: requires one of the listed Spring roles. The default `ROLE_` prefix, configured `GrantedAuthorityDefaults`, and `RoleHierarchy` apply equally to YAML and Java policies; roles are not raw authority strings.

For attachment inputs, declare `type: attachment`, a `media_type` (`image`, `pdf`, `audio`, `video`, or `file`), and permitted `allowed_content_types`. Pass a Spring `Resource` or a `ref://...` virtual-file reference as the input value.

### Annotation-defined Java skills

One `@SkillMethod` on a Spring-managed bean declares one directly callable skill. The annotation owns its description and optional `name`; an omitted or empty name uses the canonical Java method name. Explicit values are validated exactly, without trimming. No companion YAML is required. Any YAML `mapping` key, including null or empty forms, is rejected with annotation-only guidance.

```java
import ai.loomspan.api.SkillMethod;
import ai.loomspan.api.SkillParam;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

@Service
public class ExpenseService {
    @SkillMethod(name = "expenseLookup", description = "Retrieve recent expenses")
    public List<Map<String, Object>> getLatestExpenses(
            @SkillParam(description = "Optional category", required = false) String category) {
        return List.of(Map.of("category", "Software", "amount", 120.00));
    }
}
```

Invoke this declaration with `skills.invoke("expenseLookup", Map.of())`, or list `{name: expenseLookup}` in a YAML parent's `allowed_skills`. A successful Java child can satisfy the parent's exact-name plan/task and evidence requirements. Access denial, cancellation, and failure do not earn successful completion credit.

The Java signature and `@SkillParam` define inputs. `Object` accepts any JSON-compatible value, `Map<String, Object>` accepts heterogeneous values, typed maps retain value constraints, and records/DTOs expose named fields. Prefer records or DTOs when a planner needs discoverable stable fields. Optional parameters must use nullable reference types; optional primitives fail startup. See [reflected Java input contracts](agent-skills/loomspan-docs/references/skill-authoring/input-contracts.md).

Distinct overloaded methods can declare distinct explicit names; same/default names collide. Discovery canonicalizes proxy/interface/bridge declarations, includes relevant lazy beans, and invokes the final Spring-managed bean. Methods must be invocable through that bean's actual proxy. Private, final, and static skill methods on a CGLIB class proxy are rejected at startup; expose an overridable method or use a plain bean. Bean names and declared method signatures are diagnostic locations, not aliases.

Java execution uses the common skill mission, session, failure, and observer lifecycle without a framework model request or synthetic plan. Java declarations have no YAML prompt, output schema, or planning configuration. See [Java skills](agent-skills/loomspan-docs/references/java-api/java-skills.md) for the supported annotation and invocation contract.

### Java and YAML authorization

Java skills support Spring JSR-250 `@RolesAllowed`, `@PermitAll`, and `@DenyAll`. Applications using any of these policies must enable actual enforcement:

```java
@Configuration
@EnableMethodSecurity(jsr250Enabled = true)
class MethodSecurityConfiguration {}
```

Use Spring's `EnableMethodSecurity` and `jakarta.annotation.security` annotations. `@RolesAllowed("FINANCE")` and YAML `rbac_roles: [FINANCE]` require `ROLE_FINANCE` by default. Configured `GrantedAuthorityDefaults` and `RoleHierarchy` apply to both. No annotation and `@PermitAll` are unrestricted; `@DenyAll` never grants. Empty YAML roles are unrestricted, while an explicitly empty Java roles declaration follows Spring's denial semantics.

Class/method/interface policy resolution follows Spring's unique annotation scanner. Missing or disabled JSR-250, unrelated proxies, and secured declarations without applicable advice fail startup. Caller authentication is propagated to the Java proxy in a fresh context and the exact previous context is restored after success or failure. Local allowlists remain mandatory even for `@PermitAll`, and Spring denials cannot become successful tool text. See [authorization](agent-skills/loomspan-docs/references/skill-authoring/authorization.md) for precise inheritance rules and scope boundaries.

Console displays the declared source as YAML or Java. YAML details contain resource path and YAML text; Java details contain bean and declared method. Links and pagination use the registered skill name for both sources.

## Operations and limits

`loomspan.session` provides execution safeguards. Defaults are a 60-second mission timeout, maximum depth 32, 64 skill invocations, 128 tool invocations, 32 linter retries, 64 model calls, 192 physical provider attempts, and 200,000 usage units. Attachments default to a 20 MB maximum size.

```yaml
loomspan:
  session:
    mission-timeout: 60s
    max-depth: 32
    attachments:
      max-size: 20MB
    quotas:
      max-skill-invocations: 64
      max-tool-invocations: 128
      max-linter-retries: 32
      max-model-calls: 64
      max-provider-attempts: 192
      max-usage-units: 200000

execution-trace:
  persistence: ONERROR # NEVER, ONERROR, or ALWAYS
```

When Micrometer is on the application classpath, Loomspan records usage metrics automatically. Execution traces and the `SkillTemplate` observer callback can be used to inspect a completed skill execution.

### Opt-in Console observability REST API

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
at startup; rotate it by restarting the application. Every request must present
exactly one `X-loomspan-Api-Key` header. Authenticated responses use
`Cache-Control: no-store` and identify the current process with
`X-loomspan-Instance-Id`.

The current routes are `instance`, `skills`, `skills/{registeredName}`,
`active-executions`, `active-executions/{sessionId}`, `activity`, `traces`,
`traces/{traceId}`, and `traces/{traceId}/artifact` beneath the API root. The
artifact route accepts only GET with no query, range, or conditional headers
and an absent, wildcard, or NDJSON-compatible `Accept` header:

```bash
curl -H "X-loomspan-Api-Key: $LOOMSPAN_OBSERVABILITY_API_KEY" \
  -H "Accept: application/x-ndjson" \
  -OJ "http://localhost:8081/_loomspan/observability/v1/traces/$TRACE_ID/artifact"
```

Active execution list/detail responses expose `activeBranches`, never a single
truncated path. Each branch is one current open leaf with a complete ordered
root-to-leaf `path` and nullable `planId`, `taskId`, `stepNumber`,
`parallelGroup`, and `effectiveConcurrency` assignment facts inherited from the
nearest assigned frame. Shared prefixes are identical, leaves are ordered by
their frame-open sequence, and an empty array means no frame is currently open.
These live and finalized-trace shapes are same-version diagnostic contracts,
not application APIs.

A successful download is
`application/x-ndjson; charset=utf-8`, has the exact cataloged
`Content-Length`, and uses a standards-encoded attachment disposition whose
`filename`/`filename*` values represent
`loomspan-trace-<traceId>.ndjson`. Clients should use the decoded attachment
filename rather than comparing the serialized header text.
The process admits eight downloads independently from the 16 SSE subscriptions;
a ninth receives `429/LIMIT_EXCEEDED` without queuing. Transfers time out after
five minutes. A transfer admitted before expiration may finish, but new
requests for unknown, expired, deleted, or raced resources receive
`404/NOT_FOUND`.

The body is the exact finalized diagnostic file: Loomspan does not parse,
rewrite, normalize, redact, compress, or buffer it in full. Authenticated traces
may contain application business data and paths already recorded by canonical
diagnostics. Ordinary DTOs, lookup identifiers, response headers, and safe
download filenames never expose or derive from the internal artifact path.

Applications using Spring Security must let the reserved namespace reach
loomspan's filter while retaining their normal rules elsewhere. Loomspan does not
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
sanitized diagnostic and leaves the entire optional adapter, observation, and
completion grace behavior disabled.

TLS and any host/proxy rejection remain application infrastructure
responsibilities. A server-to-server Console client on the same listener needs
no CORS configuration. Rotating the key prevents later requests but does not
retroactively revoke a transfer that was already authenticated and admitted.
