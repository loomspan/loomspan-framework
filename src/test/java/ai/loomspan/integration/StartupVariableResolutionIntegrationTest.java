package ai.loomspan.integration;

import ai.loomspan.api.SkillExecutor;
import ai.loomspan.api.SkillTemplate;
import ai.loomspan.api.extensions.v1.SkillExecutorInvocation;
import ai.loomspan.autoconfigure.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class StartupVariableResolutionIntegrationTest {
    @TempDir Path directory;

    @Test void effectiveProfileSettingsShareOneLookupWithConfiguredSkill() throws Exception {
        try (var server = new okhttp3.mockwebserver.MockWebServer()) {
        server.enqueue(new okhttp3.mockwebserver.MockResponse().setHeader("Content-Type", "application/json").setBody("""
                {"id":"response","object":"response","created_at":1,"model":"demo","status":"completed","output":[],"usage":{"input_tokens":1,"output_tokens":0,"total_tokens":1}}
                """));
        Path settings = directory.resolve("application.yaml");
        Files.writeString(settings, """
                loomspan:
                  connections:
                    primary:
                      driver: openai
                      base-url: %s
                      api-key: {$var: default.provider.key}
                      headers: {X-Region: {$var: app.region}, Accept: application/json}
                  models: {primary: {connection: primary, provider-model: demo}}
                  executor-config: {default: {settings: {region: {$var: app.region}}}}
                ---
                spring.config.activate.on-profile: inactive
                loomspan.connections.unused.api-key: {$var: inactive.missing}
                """.formatted(server.url("/v1")));
        Files.writeString(directory.resolve("application-local.yaml"), "loomspan.connections.primary.api-key: local-demo-key\n");
        Files.writeString(directory.resolve("leaf.yaml"), """
                name: leaf
                description: configured leaf
                executor: configured
                config: {region: {$var: app.region}}
                """);
        var counts = new LinkedHashMap<String, Integer>();
        context(settings).withPropertyValues("spring.profiles.active=local")
                .withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                        new MapPropertySource("variables", Map.of("app.region", "west")) {
                            @Override public Object getProperty(String name) {
                                if (name.equals("app.region") || name.equals("default.provider.key") || name.equals("inactive.missing"))
                                    counts.merge(name, 1, Integer::sum);
                                return super.getProperty(name);
                            }
                        })).run(context -> {
                    assertThat(context).hasNotFailed();
                    var connection = context.getBean(LoomspanProperties.class).getConnections().get("primary");
                    assertThat(connection.getDriver()).isEqualTo(AiDriver.OPENAI);
                    assertThat(connection.getApiKey()).isEqualTo("local-demo-key");
                    assertThat(connection.getHeaders()).containsEntry("X-Region", "west").containsEntry("Accept", "application/json");
                    assertThat(context.getBean(SkillTemplate.class).invoke("default", "leaf", Map.of()).text()).isEqualTo("captured");
                    var invocation = context.getBean(Executor.class).invocation;
                    assertThat(invocation.executorConfig()).containsEntry("region", "west");
                    assertThat(invocation.skillConfig()).containsEntry("region", "west");
                    context.getBean(ai.loomspan.internal.autoconfigure.NamedAiConnectionRegistry.class).asMap().get("primary")
                            .chatModel().call(new org.springframework.ai.chat.prompt.Prompt("hello",
                                    org.springframework.ai.openai.responses.OpenAiResponsesChatOptions.builder().model("demo").build()));
                    try {
                        var request = server.takeRequest(2, java.util.concurrent.TimeUnit.SECONDS);
                        assertThat(request).isNotNull();
                        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer local-demo-key");
                        assertThat(request.getHeader("X-Region")).isEqualTo("west");
                    } catch (InterruptedException failure) { throw new AssertionError(failure); }
                    assertThat(counts).containsExactly(Map.entry("app.region", 1));
                });
        }
    }

    @Test void baseMissingValueFailsWithAuthoredLocation() throws Exception {
        Path settings = settings("""
                loomspan:
                  connections:
                    primary: {driver: openai, api-key: {$var: default.provider.key}}
                """);
        binding(settings).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("missing-value")
                    .hasStackTraceContaining("application.yaml").hasStackTraceContaining("(document #0)")
                    .hasStackTraceContaining("loomspan.connections.primary.api-key");
        });
        binding(settings).withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("blank", Map.of("default.provider.key", " \n")))).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("blank-value")
                    .hasStackTraceContaining("application.yaml").hasStackTraceContaining("loomspan.connections.primary.api-key");
        });
    }

    @Test void authoredAndReturnedPlaceholderTextReachesConsumersLiterally() throws Exception {
        Path settings = settings("""
                region: west
                report.label: ${region}
                copied: ${region}
                loomspan:
                  connections:
                    primary:
                      driver: openai
                      api-key: ${region}
                      headers: {X-Literal: '${region}', X-Host: {$var: report.label}, X-Terminal: {$var: terminal}}
                  models: {primary: {connection: primary, provider-model: '${region}'}}
                  executor-config:
                    default:
                      settings: {literal: '${region}', host: {$var: report.label}, terminal: {$var: terminal}, multiline: {$var: multiline}}
                """);
        Files.writeString(directory.resolve("leaf.yaml"), """
                name: leaf
                description: configured leaf
                executor: configured
                config: {literal: '${region}', host: {$var: report.label}, terminal: {$var: terminal}, multiline: {$var: multiline}}
                """);
        String multiline = "\nkey: [a, b]\n  indentation: ${untouched}\n";
        var counts = new LinkedHashMap<String, Integer>();
        context(settings).withInitializer(context -> {
            var original = context.getEnvironment();
            var environment = new org.springframework.core.env.StandardEnvironment() {
                @Override public String getProperty(String name) {
                    if (name.equals("terminal")) { counts.merge(name, 1, Integer::sum); return "returned-${region}"; }
                    if (name.equals("multiline")) { counts.merge(name, 1, Integer::sum); return multiline; }
                    if (name.equals("multiline") || name.equals("report.label")) counts.merge(name, 1, Integer::sum);
                    return super.getProperty(name);
                }
            };
            environment.merge(original);
            environment.getPropertySources().addFirst(new MapPropertySource("multiline", Map.of("multiline", multiline)));
            context.setEnvironment(environment);
        }).run(context -> {
            assertThat(context).hasNotFailed();
            var properties = context.getBean(LoomspanProperties.class);
            assertThat(properties.getConnections().get("primary").getApiKey()).isEqualTo("${region}");
            assertThat(properties.getConnections().get("primary").getHeaders()).containsEntry("X-Literal", "${region}")
                    .containsEntry("X-Host", "west").containsEntry("X-Terminal", "returned-${region}");
            assertThat(properties.getModels().get("primary").getProviderModel()).isEqualTo("west");
            assertThat(context.getEnvironment().getProperty("copied")).isEqualTo("west");
            context.getBean(SkillTemplate.class).invoke("default", "leaf", Map.of());
            var invocation = context.getBean(Executor.class).invocation;
            for (var data : java.util.List.of(invocation.executorConfig(), invocation.skillConfig()))
                assertThat(data).containsEntry("literal", "${region}").containsEntry("host", "west")
                        .containsEntry("terminal", "returned-${region}").containsEntry("multiline", multiline);
            assertThat(counts).containsOnly(Map.entry("report.label", 1), Map.entry("terminal", 1), Map.entry("multiline", 1));
        });
    }

    @Test void importedExecutorSectionReplacesLowerMarkersBeforeLookup() throws Exception {
        Files.writeString(directory.resolve("imported.yaml"), "loomspan: {executor-config: {default: {selected: {$var: imported.value}}}}\n");
        Path settings = settings("""
                spring.config.import: imported.yaml
                loomspan: {executor-config: {default: {discarded: {$var: missing.base}}}}
                ---
                spring.config.activate.on-profile: local
                loomspan: {executor-config: {default: {profile: local}}}
                """);
        var counts = new LinkedHashMap<String, Integer>();
        var runner = binding(settings).withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                counting(Map.of("imported.value", "imported"), counts, "imported.value", "missing.base")));
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(LoomspanProperties.class).getExecutorConfig().namespaces().get("default"))
                    .containsOnly(Map.entry("selected", "imported"));
            assertThat(counts).containsExactly(Map.entry("imported.value", 1));
        });
        counts.clear();
        runner.withPropertyValues("spring.profiles.active=local").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(LoomspanProperties.class).getExecutorConfig().namespaces().get("default"))
                    .containsOnly(Map.entry("profile", "local"));
            assertThat(counts).isEmpty();
        });
    }

    @Test void higherSourcesInheritConnectionFieldsAndSystemEnvironmentSuppliesValues() throws Exception {
        Path settings = settings("""
                loomspan:
                  connections:
                    primary:
                      driver: openai
                      api-key: {$var: ' invalid lower '}
                      headers: {X-Region: {$var: app.region}, Accept: application/json}
                """);
        binding(settings).withInitializer(context -> {
            context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.SystemEnvironmentPropertySource(
                    "isolated-environment", Map.of("APP_REGION", "west")));
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("higher", Map.of(
                    "loomspan.connections.primary.api-key", "${literal.key}", "loomspan.connections.primary.request-timeout", "250ms")));
        }).run(context -> {
            assertThat(context).hasNotFailed();
            var connection = context.getBean(LoomspanProperties.class).getConnections().get("primary");
            assertThat(connection.getDriver()).isEqualTo(AiDriver.OPENAI);
            assertThat(connection.getApiKey()).isEqualTo("${literal.key}");
            assertThat(connection.getRequestTimeout()).isEqualTo(java.time.Duration.ofMillis(250));
            assertThat(connection.getHeaders()).containsEntry("X-Region", "west").containsEntry("Accept", "application/json");
        });
    }

    @Test void markerKeysAndTypedSettingsReachTheirOwningValidator() throws Exception {
        Path settings = settings("""
                loomspan:
                  connections:
                    primary:
                      driver: openai
                      api-key: key
                      request-timeout: {$var: timeout}
                      provider-retry: {max-attempts: {$var: attempts}, enabled: {$var: enabled}}
                      headers:
                        {$var: header}: {$var: region}
                        Accept: application/json
                """);
        var values = new LinkedHashMap<String, Object>(Map.of("timeout", "250ms", "attempts", "2", "enabled", "false", "header", "X-Region", "region", "west"));
        var runner = binding(settings).withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("values", values)));
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var connection = context.getBean(LoomspanProperties.class).getConnections().get("primary");
            assertThat(connection.getRequestTimeout()).isEqualTo(java.time.Duration.ofMillis(250));
            assertThat(connection.getProviderRetry().getMaxAttempts()).isEqualTo(2);
            assertThat(connection.getProviderRetry().isEnabled()).isFalse();
            assertThat(connection.getHeaders()).containsOnly(Map.entry("X-Region", "west"), Map.entry("Accept", "application/json"));
        });
        values.put("header", "Accept");
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("key-collision")
                    .hasStackTraceContaining("headers{key#0}").hasStackTraceContaining("headers.Accept");
            assertThat(stack(context.getStartupFailure())).doesNotContain("west");
        });
        values.put("header", "X-Region");
        for (String invalid : java.util.List.of("not-a-duration", "-1ms")) {
            values.put("timeout", invalid);
            runner.run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining("request-timeout");
            });
        }
    }

    @Test void strictUnknownPropertiesAndSelectedMalformedMarkersStillFail() throws Exception {
        for (String yaml : java.util.List.of(
                "loomspan: {unexpected: value}",
                "loomspan: {connections: {primary: {driver: openai, api-key: key, unexpected: value}}}",
                "loomspan: {connections: {primary: {driver: openai, api-key: {$var: ' padded '}}}}")) {
            binding(settings(yaml)).run(context -> assertThat(context).hasFailed());
        }
    }

    @Test void optionalConnectionNullsRetainNativeDefaults() throws Exception {
        Path settings = settings("""
                loomspan:
                  connections:
                    primary:
                      driver: openai
                      api-key: key
                      base-url: null
                      request-timeout: ~
                      headers: null
                      openai: null
                      provider-retry: null
                    vertex:
                      driver: gemini
                      api-key: null
                      gemini: {vertex-ai: true, project-id: project, location: global, credentials-json: null}
                """);
        binding(settings).run(context -> {
            assertThat(context).hasNotFailed();
            var connections = context.getBean(LoomspanProperties.class).getConnections();
            assertThat(connections.get("primary").getBaseUrl()).isNull();
            assertThat(connections.get("primary").getRequestTimeout()).isNull();
            assertThat(connections.get("primary").getHeaders()).isEmpty();
            assertThat(connections.get("primary").getOpenai()).isNull();
            assertThat(connections.get("primary").getProviderRetry().getMaxAttempts()).isEqualTo(3);
            assertThat(connections.get("vertex").getGemini().getCredentialsJson()).isNull();
        });
    }

    @Test void markerHeaderKeysReachActualProviderRequest() throws Exception {
        try (var server = new okhttp3.mockwebserver.MockWebServer()) {
            server.enqueue(new okhttp3.mockwebserver.MockResponse().setHeader("Content-Type", "application/json").setBody("""
                    {"id":"response","object":"response","created_at":1,"model":"demo","status":"completed","output":[],"usage":{"input_tokens":1,"output_tokens":0,"total_tokens":1}}
                    """));
            Path settings = settings("""
                    loomspan:
                      connections:
                        primary:
                          driver: openai
                          base-url: %s
                          api-key: key
                          headers:
                            {$var: header}: {$var: region}
                            Accept: application/json
                    """.formatted(server.url("/v1")));
            binding(settings).withPropertyValues("header=X-Region", "region=west").run(context -> {
                assertThat(context).hasNotFailed();
                var registry = new ai.loomspan.internal.autoconfigure.NamedAiConnectionRegistry(
                        context.getBean(LoomspanProperties.class).getConnections(), new ai.loomspan.internal.springai.SpringAiProviderIntegration());
                assertThatCode(() -> {
                    try {
                        registry.asMap().get("primary").chatModel().call(new org.springframework.ai.chat.prompt.Prompt("hello",
                                org.springframework.ai.openai.responses.OpenAiResponsesChatOptions.builder().model("demo").build()));
                        var request = server.takeRequest(2, java.util.concurrent.TimeUnit.SECONDS);
                        assertThat(request).isNotNull();
                        assertThat(request.getHeader("X-Region")).isEqualTo("west");
                        assertThat(request.getHeader("Accept")).isEqualTo("application/json");
                    } finally { registry.destroy(); }
                }).doesNotThrowAnyException();
            });
        }
    }

    @Test void flattenedYamlMarkersAndCommandLineOverridesUseEffectiveFields() throws Exception {
        Path settings = settings("""
                loomspan.connections.primary.driver: openai
                loomspan.connections.primary.api-key: {$var: missing.key}
                loomspan.connections.primary.requestTimeout: {$var: timeout}
                loomspan.connections.primary.headers.X-Region: {$var: region}
                """);
        var application = new org.springframework.boot.SpringApplication(Binding.class);
        application.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
        application.setRegisterShutdownHook(false);
        try (var context = application.run("--spring.config.location=" + settings.toUri(), "--spring.main.banner-mode=off",
                "--loomspan.connections.primary.apiKey=command-key", "--timeout=250ms", "--region=west")) {
            var connection = context.getBean(LoomspanProperties.class).getConnections().get("primary");
            assertThat(connection.getApiKey()).isEqualTo("command-key");
            assertThat(connection.getRequestTimeout()).isEqualTo(java.time.Duration.ofMillis(250));
            assertThat(connection.getHeaders()).containsEntry("X-Region", "west");
        }
    }

    @Test void mixedNestedAndDottedYamlFieldsKeepEffectiveValuesAndAuthoredMarkers() throws Exception {
        String nested = """
                loomspan:
                  connections:
                    primary:
                      driver: openai
                      api-key: nested-key
                      headers: {Accept: application/json}
                """;
        String dotted = """
                loomspan.connections.primary.api-key: dotted-key
                loomspan.connections.primary.headers.X-Region: {$var: app.region}
                """;
        for (boolean nestedFirst : java.util.List.of(true, false)) {
            var counts = new LinkedHashMap<String, Integer>();
            Path settings = settings(nestedFirst ? nested + dotted : dotted + nested);
            binding(settings).withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                    counting(Map.of("app.region", "west"), counts, "app.region")))
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        var connection = context.getBean(LoomspanProperties.class).getConnections().get("primary");
                        assertThat(connection.getDriver()).isEqualTo(AiDriver.OPENAI);
                        assertThat(connection.getApiKey()).isEqualTo(nestedFirst ? "dotted-key" : "nested-key");
                        assertThat(connection.getHeaders()).containsOnly(Map.entry("Accept", "application/json"), Map.entry("X-Region", "west"));
                        assertThat(counts).containsExactly(Map.entry("app.region", 1));
                    });
        }
    }

    @Test void relaxedYamlPrefixesRetainAuthoredMarkers() throws Exception {
        for (String prefix : java.util.List.of("loomspan", "Loomspan", "LOOMSPAN")) {
            Path settings = settings("""
                    %s:
                      Connections:
                        primary:
                          driver: openai
                          api-key: {$var: provider.key}
                      executorConfig:
                        default: {settings: {region: {$var: region}}}
                    """.formatted(prefix));
            binding(settings).withPropertyValues("provider.key=demo-key", "region=west").run(context -> {
                assertThat(context).hasNotFailed();
                var properties = context.getBean(LoomspanProperties.class);
                assertThat(properties.getConnections().get("primary").getApiKey()).isEqualTo("demo-key");
                assertThat(properties.getExecutorConfig().namespaces().get("default").get("settings"))
                        .isEqualTo(Map.of("region", "west"));
            });
        }
    }

    @Test void relaxedFieldsPreserveExactConnectionAndHeaderKeys() throws Exception {
        Path settings = settings("""
                loomspan:
                  connections:
                    'primary.dot':
                      driver: openai
                      api-key: {$var: ' invalid displaced '}
                      headers: {X-Region: {$var: region}, XRegion: distinct}
                  executorConfig: {default: {settings: {region: {$var: region}}}}
                ---
                spring.config.activate.on-profile: local
                loomspan:
                  connections:
                    'primary.dot':
                      apiKey: local-key
                      headers: {Accept: application/json}
                """);
        binding(settings).withPropertyValues("spring.profiles.active=local", "region=west").run(context -> {
            assertThat(context).hasNotFailed();
            var properties = context.getBean(LoomspanProperties.class);
            assertThat(properties.getConnections()).containsOnlyKeys("primary.dot");
            var connection = properties.getConnections().get("primary.dot");
            assertThat(connection.getApiKey()).isEqualTo("local-key");
            assertThat(connection.getHeaders()).containsOnly(Map.entry("X-Region", "west"), Map.entry("XRegion", "distinct"),
                    Map.entry("Accept", "application/json"));
            assertThat(properties.getExecutorConfig().namespaces().get("default").get("settings")).isEqualTo(Map.of("region", "west"));
        });
    }

    @Test void missingSkillValuePreventsProviderConstructionAndStartupActivation() throws Exception {
        Path settings = settings("""
                loomspan:
                  connections:
                    primary: {driver: gemini, gemini: {vertex-ai: true, project-id: project, location: global, credentials-json: invalid-json}}
                  executor-config: {default: {settings: {}}}
                """);
        Files.writeString(directory.resolve("leaf.yaml"), "name: leaf\ndescription: leaf\nexecutor: configured\nconfig: {value: {$var: skill.only}}\n");
        context(settings).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("missing-value").hasStackTraceContaining("leaf.yaml")
                    .hasStackTraceContaining("config.value");
            assertThat(stack(context.getStartupFailure())).doesNotContain("credentials-json could not be loaded");
        });
    }

    @Test void startupCachesStayLocalAndCapturedWhileLaterPreparationReadsFreshly() throws Exception {
        Path settings = settings("""
                loomspan:
                  executor-config: {default: {settings: {region: {$var: shared}}}}
                """);
        String skill = "name: leaf\ndescription: leaf\nexecutor: configured\nconfig: {region: {$var: shared}}\n";
        String execution = Files.readString(settings);
        Files.writeString(directory.resolve("leaf.yaml"), skill);
        for (String startupValue : java.util.List.of("first", "second")) {
            var values = new LinkedHashMap<String, Object>(Map.of("shared", startupValue));
            var counts = new LinkedHashMap<String, Integer>();
            context(settings).withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(counting(values, counts, "shared")))
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        var template = context.getBean(SkillTemplate.class);
                        template.invoke("default", "leaf", Map.of());
                        assertThat(context.getBean(Executor.class).invocation.skillConfig()).containsEntry("region", startupValue);
                        values.put("shared", "rotated");
                        template.invoke("default", "leaf", Map.of());
                        assertThat(context.getBean(Executor.class).invocation.executorConfig()).containsEntry("region", startupValue);
                        assertThat(counts).containsExactly(Map.entry("shared", 1));
                        var reloader = context.getBean(ai.loomspan.api.SkillReloader.class);
                        try (var candidate = reloader.prepare("default", java.util.List.of(new ai.loomspan.api.SkillDocument("leaf.yaml", skill)),
                                new ai.loomspan.api.ExecutionConfiguration(execution))) {
                            reloader.publish(candidate);
                        }
                        template.invoke("default", "leaf", Map.of());
                        assertThat(context.getBean(Executor.class).invocation.skillConfig()).containsEntry("region", "rotated");
                        assertThat(counts).containsExactly(Map.entry("shared", 2));
                    });
        }
    }

    @Test void configuredForeignExecutorRetainsExistingStartupDependencyBoundary() throws Exception {
        Path settings = settings("""
                loomspan:
                  executor-config:
                    default: {settings: {consumer: {$var: consumer.value}}}
                    infra: {settings: {owner: {$var: owner.value}}}
                """);
        Files.writeString(directory.resolve("leaf.yaml"), "name: leaf\ndescription: leaf\nexecutor: infra:foreign\nconfig: {leaf: {$var: leaf.value}}\n");
        context(settings).withUserConfiguration(ForeignBeans.class)
                .withPropertyValues("consumer.value=consumer", "owner.value=owner", "leaf.value=leaf").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("Unknown executor 'infra:foreign'");
        });
    }

    @Test void multilineVertexJsonBindsAndConstructsFromLiteralAndReferencedSettings() throws Exception {
        var generator = java.security.KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        String pem = "-----BEGIN PRIVATE KEY-----\n" + java.util.Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(generator.generateKeyPair().getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----\n";
        String json = """
                {"type":"service_account","project_id":"project","private_key_id":"test",
                 "private_key":"%s","client_email":"test@project.iam.gserviceaccount.com",
                 "client_id":"123456789","auth_uri":"https://accounts.google.com/o/oauth2/auth",
                 "token_uri":"https://oauth2.googleapis.com/token"}
                """.formatted(pem.replace("\n", "\\n"));
        for (boolean referenced : java.util.List.of(false, true)) {
            String credentials = referenced ? "{$var: vertex.json}\n" : "|\n" + json.indent(10);
            Path settings = settings("""
                    loomspan:
                      connections:
                        primary:
                          driver: gemini
                          gemini:
                            vertex-ai: {$var: vertex.enabled}
                            project-id: project
                            location: us-central1
                            credentials-json: %s
                      executor-config: {default: {settings: {}}}
                    """.formatted(credentials.stripTrailing()) + "\n");
            context(settings).withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("values", Map.of("vertex.enabled", "true", "vertex.json", json))))
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context.getBean(LoomspanProperties.class).getConnections().get("primary").getGemini().getCredentialsJson())
                                .isEqualTo(json);
                        assertThat(context.getBean(ai.loomspan.internal.autoconfigure.NamedAiConnectionRegistry.class).asMap().get("primary").chatModel())
                                .isNotNull();
                    });
        }
    }


    private Path settings(String yaml) throws Exception {
        Path settings = directory.resolve("application.yaml");
        Files.writeString(settings, yaml);
        return settings;
    }

    private ApplicationContextRunner binding(Path settings) {
        return new ApplicationContextRunner().withUserConfiguration(Binding.class)
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.config.location=" + settings.toUri());
    }

    private static MapPropertySource counting(Map<String, Object> values, Map<String, Integer> counts, String... names) {
        var selected = java.util.Set.of(names);
        return new MapPropertySource("values", values) {
            @Override public Object getProperty(String name) {
                if (selected.contains(name)) counts.merge(name, 1, Integer::sum);
                return super.getProperty(name);
            }
        };
    }

    private static String stack(Throwable failure) {
        var text = new java.io.StringWriter();
        failure.printStackTrace(new java.io.PrintWriter(text));
        return text.toString();
    }

    @org.springframework.context.annotation.Import(ai.loomspan.internal.autoconfigure.ExecutorConfigurationBinding.class)
    @org.springframework.boot.context.properties.EnableConfigurationProperties(LoomspanProperties.class)
    @Configuration(proxyBeanMethods = false) static class Binding {}

    private ApplicationContextRunner context(Path settings) {
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class,
                        LoomspanJacksonAutoConfiguration.class, LoomspanAutoConfiguration.class, LoomspanAiAutoConfiguration.class))
                .withUserConfiguration(Beans.class).withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.config.location=" + settings.toUri(),
                        "loomspan.skills.locations=" + directory.resolve("leaf.yaml").toUri());
    }

    @Configuration(proxyBeanMethods = false) static class Beans { @Bean Executor executor() { return new Executor(); } }
    static class Executor {
        SkillExecutorInvocation invocation;
        @SkillExecutor(name = "configured", config = "settings") public String execute(SkillExecutorInvocation invocation) {
            this.invocation = invocation;
            return "captured";
        }
    }
    @Configuration(proxyBeanMethods = false) static class ForeignBeans { @Bean ForeignExecutor foreignExecutor() { return new ForeignExecutor(); } }
    static class ForeignExecutor {
        @SkillExecutor(namespace = "infra", name = "foreign", config = "settings") public String foreign(SkillExecutorInvocation invocation) { return "captured"; }
    }
}
