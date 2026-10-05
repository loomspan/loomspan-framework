package ai.loomspan.integration;
import ai.loomspan.api.SkillTemplate;
import okhttp3.mockwebserver.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
class DeclaredOutputBindingsOrdinaryIntegrationTest {
    @TempDir Path directory;
    @Configuration(proxyBeanMethods=false) @EnableAutoConfiguration static class App { }
    @Test void fullInputAssemblyMakesNoProviderRequest() throws Exception {
        run(false,(template,calls)-> {
            var events=new ArrayList<ai.loomspan.api.SkillExecutionEvent>();
            var output=template.invoke("comparison",Map.of("assessment",Map.of("exact","original")),view->events.addAll(view.events()));
            assertThat(output).contains("\"assessment\":{\"exact\":\"original\"}");assertThat(calls.get()).isZero();
            assertThat(events).filteredOn(event->event.type().equalsIgnoreCase("RESULT_ASSEMBLED")).hasSize(1);
        });
    }
    @Test void mixedOverrideCorrectionRequestsOnlyUnboundFieldsAndRetainsInputEvidence() throws Exception {
        run(true,(template,calls)-> {
            var output=template.invoke("comparison",Map.of("assessment",Map.of("exact","original")));
            assertThat(output).contains("\"assessment\":{\"exact\":\"original\"}","\"summary\":\"reasoned\"");assertThat(calls.get()).isEqualTo(2);
        });
    }
    @Test void concurrentInputOnlyRootsKeepDistinctSourcesWithoutProviderWork() throws Exception {
        run(false,(template,calls)-> {
            var first=java.util.concurrent.CompletableFuture.supplyAsync(()->template.invoke("comparison",Map.of("assessment",Map.of("id","first"))));
            var second=java.util.concurrent.CompletableFuture.supplyAsync(()->template.invoke("comparison",Map.of("assessment",Map.of("id","second"))));
            assertThat(first.join()).contains("first").doesNotContain("second");
            assertThat(second.join()).contains("second").doesNotContain("first");
            assertThat(calls.get()).isZero();
        });
    }
    @Test void boundOnlyLinterFailureIsTerminalWithoutProviderFallbackOrAssemblyEvent() throws Exception {
        run(false,"linter:\n  type: regex\n  max_retries: 2\n  regex:\n    pattern: '^NEVER$'\n",null,(template,calls)-> {
            var events=new ArrayList<ai.loomspan.api.SkillExecutionEvent>();
            assertThatThrownBy(()->template.invoke("comparison",Map.of("assessment",Map.of("exact","original")),view->events.addAll(view.events())))
                    .hasStackTraceContaining("binding_linter_validation");
            assertThat(calls.get()).isZero();
            assertThat(events).isNotEmpty().noneMatch(event->event.type().equalsIgnoreCase("RESULT_ASSEMBLED"));
            assertThat(events).filteredOn(event -> event.type().equalsIgnoreCase("LINTER")).singleElement()
                    .satisfies(event -> assertThat(event.details()).containsEntry("attempt", 1).containsEntry("retryCount", 0)
                            .containsEntry("maxRetries", 2).containsEntry("status", "EXHAUSTED")
                            .containsEntry("detail", "Assembled output did not match configured regex linter."));
        });
    }
    @Test void heldCorrectionRetainsCapturedBindingGenerationAcrossReload() throws Exception {
        var reload=new java.util.concurrent.atomic.AtomicReference<Runnable>();
        run(true,"",reload,(template,calls)-> {
            var original=template.invoke("comparison",Map.of("assessment",Map.of("exact","original")));
            assertThat(original).contains("assessment","original").doesNotContain("replacement");
            var replacement=template.invoke("comparison",Map.of("replacement",Map.of("exact","original")));
            assertThat(replacement).contains("replacement","original").doesNotContain("assessment");
            assertThat(calls.get()).isEqualTo(3);
        });
    }
    @Test void openRootKeepsProviderForAdditionalOutputSpace() throws Exception {
        custom("""
                output_schema:
                  type: object
                  properties:
                    assessment: {type: object, additionalProperties: true}
                  required: [assessment]
                  additionalProperties: true
                output_bindings:
                  /assessment: {from: input, path: /assessment}
                """,List.of("{\"extra\":\"reasoned\"}"),body->assertThat(body).doesNotContain("$.assessment \u2014 object"),(template,calls)-> {
            assertThat(template.invoke("comparison",Map.of("assessment",Map.of("exact","original")))).contains("original","extra","reasoned");
            assertThat(calls.get()).isEqualTo(1);
        });
    }
    @Test void optionalUnboundPropertyKeepsProviderEvenWhenOmitted() throws Exception {
        custom("""
                output_schema:
                  type: object
                  properties:
                    assessment: {type: object, additionalProperties: true}
                    optionalExplanation: {type: string, nullable: true}
                  required: [assessment]
                  additionalProperties: false
                output_bindings:
                  /assessment: {from: input, path: /assessment}
                """,List.of("{}"),body->assertThat(body).contains("optionalExplanation", "optional", "nullable"),(template,calls)-> {
            assertThat(template.invoke("comparison",Map.of("assessment",Map.of("exact","original")))).contains("original").doesNotContain("optionalExplanation");
            assertThat(calls.get()).isEqualTo(1);
        });
    }
    @Test void optionalAncestorRequiredUnboundSiblingAppearsInInitialAndCorrectiveContracts() throws Exception {
        custom(nestedContract(),List.of("{}","{\"context\":{\"summary\":\"reasoned\"}}"),body-> {
            assertThat(body).contains("$.context \u2014 object, required", "$.context.summary \u2014 string, required");
            assertThat(body).doesNotContain("$.context.assessment \u2014 object");
        },(template,calls)-> {
            assertThat(template.invoke("comparison",Map.of("assessment",Map.of("exact","original")))).contains("context","reasoned","original");
            assertThat(calls.get()).isEqualTo(2);
        });
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"{\"CONTEXT\":{\"ASSESSMENT\":{\"exact\":\"original\"},\"summary\":\"bad\"}}","{\"context\":null}","{\"context\":[]}"})
    void aliasesAndBlockingAncestorsUseBoundedCorrection(String override) throws Exception {
        custom(nestedContract(),List.of(override,"{\"context\":{\"summary\":\"reasoned\"}}"),body->assertThat(body).contains("Framework supplies these output destinations"),(template,calls)-> {
            assertThat(template.invoke("comparison",Map.of("assessment",Map.of("exact","original")))).contains("original","reasoned").doesNotContain("bad");
            assertThat(calls.get()).isEqualTo(2);
        });
    }
    @Test void dynamicBoundTypeMismatchFailsBeforeProviderAndWithoutAssembly() throws Exception {
        custom("""
                output_schema:
                  type: object
                  properties:
                    count: {type: integer}
                    summary: {type: string}
                  required: [count, summary]
                  additionalProperties: false
                output_bindings:
                  /count: {from: input, path: /assessment/count}
                """,List.of(),body->{throw new AssertionError("Unexpected model fallback");},(template,calls)-> {
            var events=new ArrayList<ai.loomspan.api.SkillExecutionEvent>();
            assertThatThrownBy(()->template.invoke("comparison",Map.of("assessment",Map.of("count","uncoerced")),view->events.addAll(view.events())))
                    .hasStackTraceContaining("binding_output_validation");
            assertThat(calls.get()).isZero();
            assertThat(events).isNotEmpty().noneMatch(event->event.type().equalsIgnoreCase("RESULT_ASSEMBLED"));
        });
    }
    @Test void immutableBoundEvidenceFailureHasNoProviderFallbackOrAssembly() throws Exception {
        custom("""
                allowed_skills: [{name: producer}]
                output_schema:
                  type: object
                  properties:
                    assessment: {type: object, additionalProperties: true, evidence: producer}
                    summary: {type: string}
                  required: [assessment, summary]
                  additionalProperties: false
                output_bindings:
                  /assessment: {from: input, path: /assessment}
                """,List.of(),body->{throw new AssertionError("Unexpected model fallback");},(template,calls)-> {
            var events=new ArrayList<ai.loomspan.api.SkillExecutionEvent>();
            assertThatThrownBy(()->template.invoke("comparison",Map.of("assessment",Map.of("exact","original")),view->events.addAll(view.events())))
                    .hasStackTraceContaining("binding_evidence_validation");
            assertThat(calls.get()).isZero();
            assertThat(events).isNotEmpty().noneMatch(event->event.type().equalsIgnoreCase("RESULT_ASSEMBLED"));
        });
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void mixedLinterChecksCompleteAssemblyAndRejectsExhaustion(boolean corrected) throws Exception {
        custom("""
                output_schema:
                  type: object
                  properties:
                    assessment: {type: object, additionalProperties: true}
                    summary: {type: string}
                  required: [assessment, summary]
                  additionalProperties: false
                output_bindings:
                  /assessment: {from: input, path: /assessment}
                linter:
                  type: regex
                  max_retries: 1
                  regex:
                    pattern: '(?s)(?=.*original)(?=.*GOOD).*'
                    message: Use GOOD
                """,List.of("{\"summary\":\"BAD\"}", corrected ? "{\"summary\":\"GOOD\"}" : "{\"summary\":\"BAD\"}"),
                body -> assertThat(body).contains("Framework supplies these output destinations"), (template,calls) -> {
                    var events = new ArrayList<ai.loomspan.api.SkillExecutionEvent>();
                    if (corrected) assertThat(template.invoke("comparison", Map.of("assessment",Map.of("exact","original")), view -> events.addAll(view.events())))
                            .contains("original", "GOOD");
                    else assertThatThrownBy(() -> template.invoke("comparison", Map.of("assessment",Map.of("exact","original")), view -> events.addAll(view.events())))
                            .hasStackTraceContaining("binding_linter_validation");
                    assertThat(calls).hasValue(2);
                    assertThat(events.stream().filter(event -> event.type().equalsIgnoreCase("RESULT_ASSEMBLED"))).hasSize(corrected ? 1 : 0);
                });
    }

    private String nestedContract() { return """
            output_schema:
              type: object
              properties:
                context:
                  type: object
                  nullable: true
                  properties:
                    assessment: {type: object, additionalProperties: true}
                    summary: {type: string}
                  required: [assessment, summary]
                  additionalProperties: false
              additionalProperties: false
            output_bindings:
              /context/assessment: {from: input, path: /assessment}
            """; }
    private void custom(String contract,List<String> candidates,java.util.function.Consumer<String> requestCheck,
            java.util.function.BiConsumer<SkillTemplate,AtomicInteger> check) throws Exception {
        Files.writeString(directory.resolve("comparison.yaml"),"""
                name: comparison
                description: Compare accepted evidence
                model: local
                prompt: Reason from supplied evidence.
                output_schema_max_retries: 1
                input_schema:
                  type: object
                  properties:
                    assessment: {type: object, additionalProperties: true}
                  required: [assessment]
                  additionalProperties: false
                """+contract);
        Files.writeString(directory.resolve("producer.yaml"),"name: producer\ndescription: Evidence producer\nmodel: local\nprompt: Produce accepted evidence.\n");
        AtomicInteger calls=new AtomicInteger();
        try(MockWebServer server=new MockWebServer()) {
            server.setDispatcher(new Dispatcher() { public MockResponse dispatch(RecordedRequest request) {
                String body=request.getBody().readUtf8();requestCheck.accept(body);
                int attempt=calls.getAndIncrement();
                assertThat(attempt).isLessThan(candidates.size());
                String content=candidates.get(attempt);
                return new MockResponse().setHeader("Content-Type","application/json").setBody(JsonMapper.builder().build().writeValueAsString(Map.of("id","local","object","chat.completion","created",1,"model","deterministic","choices",List.of(Map.of("index",0,"message",Map.of("role","assistant","content",content),"finish_reason","stop")),"usage",Map.of("prompt_tokens",1,"completion_tokens",1,"total_tokens",2))));
            }});server.start();
            new ApplicationContextRunner().withUserConfiguration(App.class).withPropertyValues("spring.main.web-application-type=none","loomspan.skills.locations="+directory.toUri()+"*.yaml","loomspan.connections.local.driver=openai","loomspan.connections.local.base-url="+server.url("/"),"loomspan.connections.local.api-key=local-test","loomspan.models.local.connection=local","loomspan.models.local.provider-model=deterministic").run(context->{ assertThat(context).hasNotFailed();check.accept(context.getBean(SkillTemplate.class),calls); });
        }
    }
    private void run(boolean mixed,java.util.function.BiConsumer<SkillTemplate,AtomicInteger> check) throws Exception {
        run(mixed,"",null,check);
    }
    private void run(boolean mixed,String extra,java.util.concurrent.atomic.AtomicReference<Runnable> reload,java.util.function.BiConsumer<SkillTemplate,AtomicInteger> check) throws Exception {
        Files.writeString(directory.resolve("comparison.yaml"),"""
                name: comparison
                description: Compare accepted evidence
                model: local
                prompt: Reason from the supplied assessment.
                output_schema_max_retries: 1
                input_schema:
                  type: object
                  properties:
                    assessment: {type: object, additionalProperties: true}
                  required: [assessment]
                  additionalProperties: false
                output_schema:
                  type: object
                  properties:
                    assessment: {type: object, additionalProperties: true}
                """+(mixed?"    summary: {type: string}\n  required: [assessment, summary]\n":"  required: [assessment]\n")+"""
                  additionalProperties: false
                output_bindings:
                  /assessment: {from: input, path: /assessment}
                """+extra);
        AtomicInteger calls=new AtomicInteger();
        try(MockWebServer server=new MockWebServer()) {
            server.setDispatcher(new Dispatcher() { public MockResponse dispatch(RecordedRequest request) {
                String body=request.getBody().readUtf8();
                assertThat(body).contains("original","Framework supplies these output destinations");
                assertThat(body).doesNotContain("$.assessment \u2014 object");
                int attempt=calls.incrementAndGet();
                if (attempt==2 && reload!=null) reload.get().run();
                String content=attempt==1?"{\"assessment\":{\"exact\":\"original\"},\"summary\":\"reasoned\"}":"{\"summary\":\"reasoned\"}";
                return new MockResponse().setHeader("Content-Type","application/json").setBody(JsonMapper.builder().build().writeValueAsString(Map.of("id","local","object","chat.completion","created",1,"model","deterministic","choices",List.of(Map.of("index",0,"message",Map.of("role","assistant","content",content),"finish_reason","stop")),"usage",Map.of("prompt_tokens",1,"completion_tokens",1,"total_tokens",2))));
            }});server.start();
            new ApplicationContextRunner().withUserConfiguration(App.class).withPropertyValues("spring.main.web-application-type=none","loomspan.skills.locations="+directory.toUri()+"*.yaml","loomspan.connections.local.driver=openai","loomspan.connections.local.base-url="+server.url("/"),"loomspan.connections.local.api-key=local-test","loomspan.models.local.connection=local","loomspan.models.local.provider-model=deterministic").run(context->{ assertThat(context).hasNotFailed();
                if(reload!=null) reload.set(()-> {
                    try { var path=directory.resolve("comparison.yaml");Files.writeString(path,Files.readString(path).replace("assessment","replacement")); }
                    catch(java.io.IOException ex) { throw new RuntimeException(ex); }
                    var manager=context.getBean(ai.loomspan.internal.skill.SkillGenerationManager.class);
                    manager.activate(manager.prepare());
                });
                check.accept(context.getBean(SkillTemplate.class),calls);
            });
        }
    }
}
