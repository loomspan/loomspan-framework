# First embedded Java skill

This example turns a customer support report into an engineering triage brief
using one model-backed skill in a command-line Spring Boot application.
It requires Java 21+, Maven 3.9+, and an OpenAI connection with a model your account
can use. It makes a real provider request when run. Use
[connection guidance](../agent-skills/loomspan-docs/references/skill-authoring/model-selection-and-connections.md)
for another driver.

## Obtain the starter

The example matches this checkout's `1.0.0-beta.9-SNAPSHOT`. This is a development
coordinate, not a claim that the artifact is available from Maven Central.
For a source evaluation, first run in the matching framework checkout:

```bash
./mvnw -DskipTests install
```

On Windows, use `./mvnw.cmd`. For a published release, select its exact version
and use the quickstart from that release's tag instead.

## Create a separate application

Create `pom.xml` in a new directory outside the framework checkout:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.1.0</version>
    <relativePath/>
  </parent>
  <groupId>example</groupId>
  <artifactId>loomspan-demo</artifactId>
  <version>0.0.1-SNAPSHOT</version>
  <properties>
    <java.version>21</java.version>
  </properties>
  <dependencies>
    <dependency>
      <groupId>ai.loomspan</groupId>
      <artifactId>loomspan-spring-boot-starter</artifactId>
      <version>1.0.0-beta.9-SNAPSHOT</version>
    </dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

Create `src/main/resources/application.yml`:

```yaml
loomspan:
  connections:
    primary:
      driver: openai
      api-key: ${OPENAI_API_KEY}
  models:
    assistant:
      connection: primary
      provider-model: ${LOOMSPAN_PROVIDER_MODEL}
```

Set `OPENAI_API_KEY` and `LOOMSPAN_PROVIDER_MODEL` in the environment of the process
that will launch the application. The latter is a provider model ID available to
your account, not the Loomspan alias `assistant`.

Create `src/main/resources/skills/triage-support-request.yaml`:

```yaml
name: triageSupportRequest
description: Turn a customer issue into an actionable engineering triage brief.
model: assistant
prompt: >
  Assess customer impact and urgency, distinguish reported facts from hypotheses,
  and propose the next investigation steps. Identify missing information.
  Do not claim to have checked systems or taken action.
input_schema:
  type: object
  properties:
    customerMessage: { type: string }
  required: [customerMessage]
  additionalProperties: false
```

Loomspan discovers `classpath:/skills/**/*.yaml` by default. Files ending in
`.yml` require that extension in `loomspan.skills.locations`.

Create `src/main/java/example/DemoApplication.java`:

```java
package example;

import ai.loomspan.api.SkillTemplate;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.Map;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        try (var context = SpringApplication.run(DemoApplication.class, args)) {
            // The ApplicationRunner completes before the context is closed.
        }
    }

    @Bean
    ApplicationRunner triage(SkillTemplate skills) {
        return args -> System.out.println(skills.invoke("triageSupportRequest",
            Map.of("customerMessage", """
                Since this morning's deployment, checkout times out after payment.
                Three customers say they were charged but received no order confirmation.
                Retrying sometimes creates two orders. Browsing and cart updates still work.
                """)));
    }
}
```

## Run and inspect

From the new application's directory:

```bash
mvn spring-boot:run
```

The application prints a textual triage brief and closes. An illustrative brief
would identify reported charges without order confirmations and duplicate orders
as urgent customer impact, treat the deployment as a possible cause rather than a
proven one, and suggest correlating payment and order records and checking retry
behavior. It should ask for affected order identifiers and timestamps.

This first skill reasons over the supplied report; it does not query those systems.
Wording and accuracy are model-dependent. Add application capabilities when the
workflow needs to gather evidence, as described below.

If startup fails, check the exact dependency, environment variables, alias, and
`.yaml` file location. If the provider request fails, follow the named connection
and model in the diagnostic and consult the
[connection troubleshooting reference](../agent-skills/loomspan-docs/references/skill-authoring/model-selection-and-connections.md).
The example uses default [execution limits](../agent-skills/loomspan-docs/references/skill-authoring/execution-limits.md).

## Develop further

- Add [Java skills](../agent-skills/loomspan-docs/references/java-api/java-skills.md)
  or [REST leaves](../agent-skills/loomspan-docs/references/skill-authoring/rest-skills.md)
  for application operations.
- Design a [skill tree](../agent-skills/loomspan-docs/references/skill-authoring/mental-model.md)
  when the model needs controlled child capabilities or planning.
- Define [structured outputs](../agent-skills/loomspan-docs/references/skill-authoring/output-contracts.md)
  and [authorization](../agent-skills/loomspan-docs/references/skill-authoring/authorization.md)
  for your application's contract.
- For a long-running servlet application, use [Console setup](console-setup.md)
  to enable runtime investigation. This command-line example does not expose the
  servlet observability API.
