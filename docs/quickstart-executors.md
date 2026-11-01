# Application-owned Java executors

This complete command-line application calls its own local HTTP service through
an executor, then invokes a SQL-shaped example without a database. It needs Java
21+ and Maven 3.9+, with no model provider or credentials. The application owns the
server, reusable HTTP client, timeouts and cleanup. Loomspan supplies declaration
binding, validated input and captured configuration; it supplies no HTTP or SQL
implementation.

This example matches `1.0.0-beta.9-SNAPSHOT`. First install the matching framework
checkout with `./mvnw -DskipTests install` (`./mvnw.cmd` on Windows). This snapshot
coordinate does not imply Maven Central availability. For a published version,
use its exact dependency and matching documentation tag.

Create a separate application directory and these files.

`pom.xml`:

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
  <artifactId>executor-demo</artifactId>
  <version>0.0.1-SNAPSHOT</version>
  <properties><java.version>21</java.version></properties>
  <dependencies>
    <dependency>
      <groupId>ai.loomspan</groupId>
      <artifactId>loomspan-spring-boot-starter</artifactId>
      <version>1.0.0-beta.9-SNAPSHOT</version>
    </dependency>
  </dependencies>
  <build><plugins><plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
  </plugin></plugins></build>
</project>
```

`src/main/resources/application.yml`:

```yaml
spring:
  main:
    web-application-type: none
loomspan:
  executor-config:
    default:
      http: {path: /echo}
      sql: {statement: 'select :id', database: infrastructure}
```

`src/main/resources/skills/send.yaml`:

```yaml
name: send
description: Send a message to the application's local service
executor: http
config: {prefix: 'service:'}
input_schema:
  type: object
  properties:
    message: {type: string}
  required: [message]
  additionalProperties: false
```

`src/main/resources/skills/query.yaml`:

```yaml
name: query
description: Demonstrate SQL-shaped application configuration without a database
executor: sql
config: {database: archive, statement: operation-data}
input_schema:
  type: object
  properties:
    id: {type: integer}
  required: [id]
  additionalProperties: false
```

`src/main/java/example/DemoApplication.java`:

```java
package example;

import ai.loomspan.api.SkillExecutor;
import ai.loomspan.api.SkillExecutorInvocation;
import ai.loomspan.api.SkillTemplate;
import com.sun.net.httpserver.HttpServer;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        try (var context = SpringApplication.run(DemoApplication.class, args)) {
            // ApplicationRunner has completed; context close releases our beans.
        }
    }

    @Bean(destroyMethod = "close")
    ApplicationExecutors executors() throws Exception { return new ApplicationExecutors(); }

    @Bean
    ApplicationRunner demo(SkillTemplate skills) {
        return args -> {
            String http = skills.invoke("default-send", Map.of("message", "hello"));
            String sql = skills.invoke("default-query", Map.of("id", 7));
            if (!"service:hello".equals(http) || !"archive|select :id|7".equals(sql)) {
                throw new IllegalStateException("Unexpected executor output: " + http + " / " + sql);
            }
            System.out.println(http);
            System.out.println(sql);
        };
    }

    public static class ApplicationExecutors implements AutoCloseable {
        private final HttpServer server;
        private final HttpClient client;

        ApplicationExecutors() throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            server.createContext("/echo", exchange -> {
                try (exchange) {
                    byte[] bytes = exchange.getRequestBody().readAllBytes();
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                }
            });
            server.start();
        }

        @SkillExecutor(name = "http", config = "http")
        public String send(SkillExecutorInvocation call) throws Exception {
            URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort())
                    .resolve(call.executorConfig().get("path").toString());
            var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(2))
                    .POST(HttpRequest.BodyPublishers.ofString(call.input().get("message").toString(), StandardCharsets.UTF_8))
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IllegalStateException("HTTP " + response.statusCode());
            return call.skillConfig().get("prefix") + response.body();
        }

        @SkillExecutor(name = "sql", config = "sql")
        public String query(SkillExecutorInvocation call) {
            // Application interpretation only: this example opens no database.
            return call.skillConfig().get("database") + "|" + call.executorConfig().get("statement")
                    + "|" + call.input().get("id");
        }

        @Override public void close() {
            server.stop(0);
            client.close();
        }
    }
}
```

Run from the separate application's directory:

```bash
mvn spring-boot:run
```

After startup logs, the application prints and verifies:

```text
service:hello
archive|select :id|7
```

It then exits and closes its resources. No fixed listening port is required.
The two configuration maps are separate general application data: both may
contain SQL, database selectors, HTTP settings or other domain values. Their
interpretation belongs to the executor; Loomspan performs no merge or domain
validation. Invalid business input fails before application invocation.

Both YAML leaves appear as `SkillKind.JAVA`. The `http` and `sql` executor
registrations themselves are reference-only and cannot be invoked as skills.
All declarations here share the Private startup `default` namespace. For foreign
executor namespaces and complete replacement, read
[executor configuration](../agent-skills/loomspan-docs/references/skill-authoring/executor-skills.md)
and [publication and resource ownership](../agent-skills/loomspan-docs/references/java-api/skill-reload.md).
For return values, failures, Spring advice, caller security and Resource leaves,
read [Java declarations](../agent-skills/loomspan-docs/references/java-api/java-skills.md).
