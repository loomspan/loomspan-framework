package ai.loomspan.internal.skill;

import ai.loomspan.autoconfigure.AiDriver;
import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.api.RestSkillHandler;
import ai.loomspan.api.SkillDocument;
import ai.loomspan.api.ExecutionConfiguration;
import ai.loomspan.api.SkillValidationIssue;
import ai.loomspan.api.SkillKind;
import ai.loomspan.api.ValidatedSkill;
import ai.loomspan.internal.core.CapabilityKind;
import ai.loomspan.internal.core.CapabilityMetadata;
import ai.loomspan.internal.core.CapabilityToolDescriptor;
import ai.loomspan.internal.core.SkillExecutionDescriptor;
import ai.loomspan.internal.core.SkillMethodBeanPostProcessor;
import ai.loomspan.internal.core.SkillSource;
import ai.loomspan.internal.runtime.input.SkillInputContractResolver;
import ai.loomspan.internal.security.SkillAccessPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillGenerationManagerTest
{
    @Test
    void nativeSchemasEnterExistingBindingCompatibilityAndCapturedCatalogs(@TempDir Path directory) {
        var properties = loadingProperties(directory);
        var connection = new LoomspanProperties.ConnectionProperties(); connection.setDriver(AiDriver.TYPESAFE); connection.setApiKey("fixture");
        properties.getConnections().put("native", connection);
        var model = new LoomspanProperties.ModelCatalogEntry(); model.setConnection("native"); model.setProviderModel("jev"); properties.getModels().put("native", model);
        var javaSkills = mock(SkillMethodBeanPostProcessor.class); when(javaSkills.capabilities()).thenReturn(List.of());
        var manager = new SkillGenerationManager(javaSkills, () -> new YamlSkillCatalog(properties), new SkillInputContractResolver(), new StaticListableBeanFactory());
        manager.afterSingletonsInstantiated();
        String leaf = """
                name: producer
                description: Evaluate
                model: native
                input_schema: {type: object}
                typesafe:
                  state: ${/input}
                  questions:
                    category: {type: choice, criteria: {A: null, B: null}}
                    severity: {type: score, criteria: [low, high]}
                """;
        String parent = """
                name: parent
                description: Bind named answers
                model: model
                planning_mode: true
                allowed_skills: [{name: default-producer, max_tasks: 1}]
                output_schema:
                  type: object
                  properties:
                    result: {type: string, enum: [A, Z]}
                  required: [result]
                  additionalProperties: false
                output_bindings:
                  /result: {from: child_result, skill: default-producer, path: /category/choice}
                """;
        var producer = new SkillDocument("opaque native label", leaf);
        assertThat(manager.validate(manager.capturePreparation("default"), List.of(new SkillDocument("parent", parent), producer), ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE).valid()).isTrue();
        var generation = manager.prepare(manager.capturePreparation("default"), List.of(new SkillDocument("parent", parent), producer), ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE, java.util.Map.of("fixture.key", "fixture"));
        var schema = generation.capability("default-producer").tool().outputSchema();
        assertThat(generation.skillCatalog().skill("default-producer").orElseThrow().outputSchema()).isEqualTo(schema);
        assertThat(generation.registeredSkillCatalog().find("default-producer").orElseThrow().outputSchema()).isEqualTo(schema);
        assertThat(generation.registeredSkillCatalog().find("default-producer").orElseThrow().sourcePath()).isEqualTo("opaque native label");
        assertThat(generation.registeredSkillCatalog().find("default-producer").orElseThrow().yaml()).isEqualTo(leaf);
        for (String invalid : List.of(parent.replace("enum: [A, Z]", "enum: [Z]"),
                parent.replace("enum: [A, Z]", "enum: [score]").replace("path: /category/choice", "path: /category/type"),
                parent.replace("type: string, enum: [A, Z]", "type: integer").replace("path: /category/choice", "path: /severity/score"))) {
            assertThat(manager.validate(manager.capturePreparation("default"), List.of(new SkillDocument("parent", invalid), producer), ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE).issues())
                    .anyMatch(issue -> issue.fieldPath().equals("output_bindings"));
        }
        var dynamic = new SkillDocument("dynamic", leaf.replace("criteria: {A: null, B: null}", "criteria: '${/input/options}'"));
        assertThat(manager.validate(manager.capturePreparation("default"), List.of(new SkillDocument("parent", parent.replace("enum: [A, Z]", "enum: [Z]")), dynamic), ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE).valid()).isTrue();
    }

    @Test
    void validatesOutputBindingPathsTypesAndProducerCountsAgainstCompleteGeneration(@TempDir Path directory) {
        SkillMethodBeanPostProcessor javaSkills = mock(SkillMethodBeanPostProcessor.class);
        when(javaSkills.capabilities()).thenReturn(List.of());
        SkillGenerationManager manager = new SkillGenerationManager(javaSkills,
                () -> new YamlSkillCatalog(loadingProperties(directory)), new SkillInputContractResolver(), new StaticListableBeanFactory());
        manager.afterSingletonsInstantiated();
        String parent = """
                name: parent
                description: parent
                model: model
                planning_mode: true
                allowed_skills: [{name: default-producer, max_tasks: 2}]
                input_schema:
                  type: object
                  properties:
                    id: {type: integer}
                  additionalProperties: false
                output_schema:
                  type: object
                  properties:
                    CaseId: {type: number}
                    answer: {type: string}
                  required: [CaseId, answer]
                  additionalProperties: false
                output_bindings:
                  /caseid: {from: input, path: /id}
                  /answer: {from: child_result, skill: default-producer, path: /value}
                """;
        var producer = new SkillDocument("producer", "name: producer\ndescription: producer\nmodel: model\noutput_schema: {type: object, properties: {value: {type: string}}, additionalProperties: false}\n");
        assertThat(manager.validate(manager.capturePreparation("default"), List.of(new SkillDocument("parent", parent), producer), ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE).valid()).isTrue();
        var generation = manager.prepare(manager.capturePreparation("default"), List.of(new SkillDocument("parent", parent), producer), ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE, java.util.Map.of("fixture.key", "fixture"));
        assertThat(generation.definition("default-parent").outputBindings().getFirst().destination().pointer()).isEqualTo("/CaseId");
        Map<String, String> invalid = Map.ofEntries(
                Map.entry("count zero", parent.replace("max_tasks: 2", "max_tasks: 0")),
                Map.entry("count minimum", parent.replace("max_tasks: 2", "min_tasks: 2")),
                Map.entry("unknown source", parent.replace("path: /id", "path: /absent")),
                Map.entry("closed destination", parent.replace("/caseid:", "/absent:")),
                Map.entry("scalar ancestor", parent.replace("/caseid:", "/caseid/child:")),
                Map.entry("array traversal", parent.replace("id: {type: integer}", "id: {type: array, items: {type: integer}}").replace("path: /id", "path: /id/0")),
                Map.entry("types", parent.replace("CaseId: {type: number}", "CaseId: {type: boolean}")),
                Map.entry("producer source", parent.replace("path: /value", "path: /absent")));
        for (var entry : invalid.entrySet()) {
            var validation = manager.validate(manager.capturePreparation("default"), List.of(new SkillDocument("parent", entry.getValue()), producer), ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE);
            assertThat(validation.valid()).as(entry.getKey()).isFalse();
            assertThat(validation.issues()).anyMatch(issue -> issue.fieldPath().equals("output_bindings"));
        }
        var unknown = new SkillDocument("producer", "name: producer\ndescription: producer\nmodel: model\n");
        assertThat(manager.validate(manager.capturePreparation("default"), List.of(new SkillDocument("parent", parent), unknown), ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE).valid()).isTrue();
        var open = new SkillDocument("producer", "name: producer\ndescription: producer\nmodel: model\noutput_schema: {type: object, additionalProperties: true}\n");
        assertThat(manager.validate(manager.capturePreparation("default"), List.of(new SkillDocument("parent", parent), open), ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE).valid()).isTrue();
        var nullable = new SkillDocument("producer", producer.yaml().replace("type: string", "type: string, nullable: true"));
        assertThat(manager.validate(manager.capturePreparation("default"), List.of(new SkillDocument("parent", parent), nullable), ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE).issues())
                .anyMatch(issue -> issue.message().contains("nullable"));
    }

    @Test
    void validatesBindingGraphsAndKnownSchemasWithoutGuessingProducerOutput(@TempDir Path directory) {
        SkillMethodBeanPostProcessor javaSkills = mock(SkillMethodBeanPostProcessor.class);
        when(javaSkills.capabilities()).thenReturn(List.of());
        SkillGenerationManager manager = new SkillGenerationManager(javaSkills,
                () -> new YamlSkillCatalog(loadingProperties(directory)), new SkillInputContractResolver(), new StaticListableBeanFactory());
        manager.afterSingletonsInstantiated();
        String parent = """
                name: parent
                description: parent
                model: model
                planning_mode: true
                input_schema:
                  type: object
                  properties:
                    requestId: {type: string}
                  required: [requestId]
                  additionalProperties: false
                allowed_skills:
                  - name: default-producer
                    max_tasks: 1
                  - name: default-consumer
                    input_bindings:
                      /caseId: {from: input, path: /requestId}
                      /data: {from: child_result, skill: default-producer, path: /undeclaredOutput}
                """;
        var producer = new SkillDocument("producer", "name: producer\ndescription: producer\nmodel: model\n");
        var consumer = new SkillDocument("consumer", """
                name: consumer
                description: consumer
                model: model
                input_schema:
                  type: object
                  properties:
                    caseId: {type: string}
                    data: {type: object}
                  required: [caseId, data]
                  additionalProperties: false
                """);
        assertThat(manager.validate(manager.capturePreparation("default"), List.of(new SkillDocument("parent", parent), producer, consumer), ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE).valid()).isTrue();
        Map<String, String> bad = Map.of(
                "unknown producer", parent.replace("skill: default-producer", "skill: default-other"),
                "self", parent.replace("skill: default-producer", "skill: default-consumer"),
                "impossible count", parent.replace("max_tasks: 1", "max_tasks: 0"),
                "missing input field", parent.replace("path: /requestId", "path: /absent"),
                "closed target", parent.replace("/caseId:", "/absent:"),
                "scalar target ancestor", parent.replace("/caseId:", "/caseId/child:"));
        for (var entry : bad.entrySet()) {
            var validation = manager.validate(manager.capturePreparation("default"), List.of(new SkillDocument("parent", entry.getValue()), producer, consumer), ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE);
            assertThat(validation.valid()).as(entry.getKey()).isFalse();
            assertThat(validation.issues()).anyMatch(issue -> issue.fieldPath().equals("allowed_skills.input_bindings"));
        }
        String cycle = parent.replace("    max_tasks: 1", "    max_tasks: 1\n    input_bindings:\n      /data: {from: child_result, skill: default-consumer, path: ''}");
        assertThat(manager.validate(manager.capturePreparation("default"), List.of(new SkillDocument("parent", cycle), producer, consumer), ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE).issues())
                .anyMatch(issue -> issue.message().contains("cyclic"));
    }

    @Test
    void resolvesForwardingMetadataThroughChainsWithoutInheritingEvidence(@TempDir Path directory) {
        SkillMethodBeanPostProcessor javaSkills = mock(SkillMethodBeanPostProcessor.class);
        when(javaSkills.capabilities()).thenReturn(List.of(javaSkill("fixedJava")));
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("handler", (RestSkillHandler) invocation -> "raw");
        SkillGenerationManager manager = new SkillGenerationManager(javaSkills,
                () -> new YamlSkillCatalog(loadingProperties(directory)), new SkillInputContractResolver(), beans);
        manager.afterSingletonsInstantiated();
        var documents = List.of(forwardingDocument("outer", "inner"), forwardingDocument("inner", "producer"),
                new SkillDocument("producer", """
                        name: producer
                        description: Produce an answer
                        model: model
                        allowed_skills: [{name: default-fixedJava}]
                        output_schema:
                          type: object
                          properties:
                            answer: {type: string, evidence: default-fixedJava, nullable: true}
                          required: [answer]
                          additionalProperties: false
                        """),
                forwardingDocument("javaParent", "fixedJava"), forwardingDocument("restParent", "restLeaf"),
                new SkillDocument("restLeaf", "name: restLeaf\ndescription: REST leaf\nrest: true\n"),
                forwardingDocument("plainParent", "plain"),
                new SkillDocument("plain", "name: plain\ndescription: plain\nmodel: model\n"));
        assertThat(manager.validate(manager.capturePreparation("default"), documents, ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE).valid()).isTrue();
        SkillGeneration generation = manager.prepare(manager.capturePreparation("default"), documents, ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE, java.util.Map.of("fixture.key", "fixture"));
        String schema = generation.skillCatalog().skill("default-producer").orElseThrow().outputSchema();
        assertThat(schema).contains("answer", "nullable", "additionalProperties").doesNotContain("evidence");
        assertThat(generation.definition("default-producer").evidenceContract().isEmpty()).isFalse();
        assertThat(generation.definition("default-inner").outputSchema()).isNull();
        assertThat(generation.definition("default-inner").evidenceContract().isEmpty()).isTrue();
        for (String name : List.of("outer", "inner", "producer")) {
            assertThat(generation.skillCatalog().skill("default-" + name).orElseThrow().outputSchema()).isEqualTo(schema);
            assertThat(generation.registeredSkillCatalog().find("default-" + name).orElseThrow().outputSchema()).isEqualTo(schema);
        }
        for (String name : List.of("javaParent", "fixedJava", "restParent", "restLeaf", "plainParent", "plain"))
            assertThat(generation.skillCatalog().skill("default-" + name).orElseThrow().outputSchema()).isNull();
        ai.loomspan.testkit.TestSkillGenerations.activate(manager, generation);
        var captured = manager.capture(ai.loomspan.internal.core.SkillIdentity.parse("default-outer"));
        var replacement = manager.prepare(manager.capturePreparation("default"), List.of(forwardingDocument("outer", "fixedJava")), ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE, java.util.Map.of("fixture.key", "fixture"));
        ai.loomspan.testkit.TestSkillGenerations.activate(manager, replacement);
        assertThat(captured.snapshot().entryGeneration().skillCatalog().skill("default-outer").orElseThrow().outputSchema()).isEqualTo(schema);
        assertThat(manager.active("default").skillCatalog().skill("default-outer").orElseThrow().outputSchema()).isNull();
        captured.lease().close();
    }

    @Test
    void rejectsForwardingCyclesAndMissingTargetsWithoutActivating(@TempDir Path directory) {
        SkillMethodBeanPostProcessor javaSkills = mock(SkillMethodBeanPostProcessor.class);
        when(javaSkills.capabilities()).thenReturn(List.of());
        SkillGenerationManager manager = new SkillGenerationManager(javaSkills,
                () -> new YamlSkillCatalog(loadingProperties(directory)), new SkillInputContractResolver(), new StaticListableBeanFactory());
        manager.afterSingletonsInstantiated();
        String original = manager.active("default").id();
        for (var candidate : List.of(List.of(forwardingDocument("self", "self")),
                List.of(forwardingDocument("one", "two"), forwardingDocument("two", "one")),
                List.of(forwardingDocument("one", "missing")))) {
            var validation = manager.validate(manager.capturePreparation("default"), candidate, ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE);
            assertThat(validation.valid()).isFalse();
            assertThat(validation.issues()).anyMatch(issue -> "output_from.skill".equals(issue.fieldPath()));
            assertThatThrownBy(() -> manager.prepare(manager.capturePreparation("default"), candidate, ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE, java.util.Map.of("fixture.key", "fixture"))).isInstanceOf(IllegalStateException.class);
            assertThat(manager.active("default").id()).isEqualTo(original);
        }
        assertThat(manager.validate(manager.capturePreparation("default"), List.of(
                new SkillDocument("a", "name: a\ndescription: a\nmodel: model\nallowed_skills: [{name: default-b}]\n"),
                new SkillDocument("b", "name: b\ndescription: b\nmodel: model\nallowed_skills: [{name: default-a}]\n")), ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE).valid()).isTrue();
    }

    private static SkillDocument forwardingDocument(String name, String child) {
        return new SkillDocument(name, """
                name: %s
                description: Prepare and delegate
                model: model
                planning_mode: true
                allowed_skills: [{name: default-%s, required: true, max_tasks: 1}]
                output_from: {skill: default-%s}
                """.formatted(name, child, child));
    }

    @Test
    void runtimeSnapshotDetachesRequestTimeoutFromStartupMutation()
    {
        var original = new LoomspanProperties.ConnectionProperties();
        original.setRequestTimeout(java.time.Duration.ofSeconds(4));
        var properties = new LoomspanProperties();
        properties.setConnections(Map.of("primary", original));
        var registry = mock(ai.loomspan.internal.autoconfigure.NamedAiConnectionRegistry.class);
        try (var runtime = new ExecutionRuntime(properties, ai.loomspan.internal.core.TracePersistencePolicy.NEVER, registry))
        {
            original.setRequestTimeout(java.time.Duration.ofMillis(1));
            assertThat(runtime.properties().getConnections().get("primary").getRequestTimeout())
                    .isEqualTo(java.time.Duration.ofSeconds(4));
        }
    }
    @Test
    void hostCredentialsAndExecutionSettingsRemainWithCapturedGeneration(@TempDir Path directory) throws Exception
    {
        SkillMethodBeanPostProcessor javaSkills = mock(SkillMethodBeanPostProcessor.class);
        when(javaSkills.capabilities()).thenReturn(List.of());
        LoomspanProperties startup = loadingProperties(directory);
        var environment = new org.springframework.mock.env.MockEnvironment()
                .withProperty("host.key", "deployment-key");
        var registries = new java.util.ArrayList<ai.loomspan.internal.autoconfigure.NamedAiConnectionRegistry>();
        SkillGenerationManager manager = new SkillGenerationManager(javaSkills,
                () -> new YamlSkillCatalog(startup), new SkillInputContractResolver(),
                new StaticListableBeanFactory(), startup, environment, (effective, policy) -> {
                    var registry = mock(ai.loomspan.internal.autoconfigure.NamedAiConnectionRegistry.class);
                    registries.add(registry);
                    return new ExecutionRuntime(effective, policy, registry);
                });
        manager.afterSingletonsInstantiated();
        String template = """
                loomspan:
                  connections:
                    primary:
                      driver: openai
                      api-key-ref: host.key
                  models:
                    chosen:
                      connection: primary
                      provider-model: selected
                  session:
                    max-depth: %d
                    quotas:
                      max-provider-attempts: %d
                """;
        SkillGeneration first = manager.prepare(manager.capturePreparation("default"), List.of(new SkillDocument("leaf", "name: leaf\ndescription: fixture\nmodel: chosen\n")), new ExecutionConfiguration(template.formatted(3, 4)), ai.loomspan.api.NamespaceVisibility.PRIVATE, Map.of("host.key", "key-A"));
        ai.loomspan.testkit.TestSkillGenerations.activate(manager, first);
        var owner = manager.capture(ai.loomspan.internal.core.SkillIdentity.parse("default-leaf"));
        SkillGeneration second = manager.prepare(manager.capturePreparation("default"), List.of(), new ExecutionConfiguration(template.formatted(7, 9)), ai.loomspan.api.NamespaceVisibility.PRIVATE, Map.of("host.key", "key-B"));
        ai.loomspan.testkit.TestSkillGenerations.activate(manager, second);
        assertThat(owner.snapshot().entryGeneration().runtime().properties().getConnections().get("primary").getApiKey())
                .isEqualTo("key-A");
        assertThat(owner.snapshot().entryGeneration().runtime().properties().getSession().getMaxDepth()).isEqualTo(3);
        assertThat(owner.snapshot().entryGeneration().runtime().properties().getSession().getQuotas().getMaxProviderAttempts())
                .isEqualTo(4);
        assertThat(manager.active("default").runtime().properties().getConnections().get("primary").getApiKey())
                .isEqualTo("key-B");
        assertThat(manager.active("default").runtime().properties().getSession().getMaxDepth()).isEqualTo(7);
        assertThat(manager.active("default").runtime().properties().getSession().getQuotas().getMaxProviderAttempts())
                .isEqualTo(9);
        org.mockito.Mockito.verifyNoInteractions(registries.get(1));
        owner.lease().close();
        verify(registries.get(1)).destroy();
    }
    @Test
    void providerResourcesRetireAfterTheLastCapturedOwnerAndCloseOnce() throws Exception
    {
        SkillMethodBeanPostProcessor javaSkills = mock(SkillMethodBeanPostProcessor.class);
        when(javaSkills.capabilities()).thenReturn(List.of());
        SkillGenerationManager manager = new SkillGenerationManager(javaSkills,
                () -> catalog(), new SkillInputContractResolver(), new StaticListableBeanFactory());
        manager.afterSingletonsInstantiated();
        var firstRegistry = mock(ai.loomspan.internal.autoconfigure.NamedAiConnectionRegistry.class);
        var secondRegistry = mock(ai.loomspan.internal.autoconfigure.NamedAiConnectionRegistry.class);
        SkillGeneration first = new SkillGeneration("default", "first", Map.of("default-leaf", ai.loomspan.testkit.TestSkillGenerations.leaf()), Map.of(), null, null,
                new ExecutionRuntime(new LoomspanProperties(), ai.loomspan.internal.core.TracePersistencePolicy.ONERROR,
                        firstRegistry));
        SkillGeneration second = new SkillGeneration("default", "second", Map.of(), Map.of(), null, null,
                new ExecutionRuntime(new LoomspanProperties(), ai.loomspan.internal.core.TracePersistencePolicy.ONERROR,
                        secondRegistry));
        ai.loomspan.testkit.TestSkillGenerations.activate(manager, first);
        var captured = manager.capture(ai.loomspan.internal.core.SkillIdentity.parse("default-leaf"));
        ai.loomspan.testkit.TestSkillGenerations.activate(manager, second);
        org.mockito.Mockito.verifyNoInteractions(firstRegistry);
        captured.lease().close();
        captured.lease().close();
        verify(firstRegistry).destroy();
        manager.destroy();
        manager.destroy();
        verify(secondRegistry).destroy();
    }

    @Test
    void validationReturnsCompleteSortedKindsForProposedCandidate(@TempDir Path directory) throws Exception
    {
        SkillMethodBeanPostProcessor javaSkills = mock(SkillMethodBeanPostProcessor.class);
        when(javaSkills.capabilities()).thenReturn(List.of(javaSkill("Zjava")));
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        AtomicInteger constructions = new AtomicInteger();
        RootBeanDefinition handler = new RootBeanDefinition(RestSkillHandler.class);
        handler.setScope(BeanDefinition.SCOPE_PROTOTYPE);
        handler.setInstanceSupplier(() -> {
            constructions.incrementAndGet();
            return (RestSkillHandler) invocation -> "ok";
        });
        beans.registerBeanDefinition("handler", handler);
        SkillGenerationManager manager = new SkillGenerationManager(javaSkills,
                () -> new YamlSkillCatalog(loadingProperties(directory)), new SkillInputContractResolver(), beans);
        manager.afterSingletonsInstantiated();
        String activeId = manager.active("default").id();
        AtomicInteger retirements = new AtomicInteger();
        manager.onGenerationRetired(ignored -> retirements.incrementAndGet());
        var first = manager.validate(manager.capturePreparation("default"), List.of(
                new SkillDocument("opaque model source", "name: ayaml\ndescription: model\nmodel: model\n"),
                new SkillDocument("opaque REST source", "name: Brest\ndescription: route\nrest: true\n")), ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE);
        assertThat(first.valid()).isTrue();
        assertThat(first.skills()).containsExactly(
                new ValidatedSkill("default-Brest", SkillKind.REST),
                new ValidatedSkill("default-Zjava", SkillKind.JAVA),
                new ValidatedSkill("default-ayaml", SkillKind.YAML));
        assertThat(first.skills()).extracting(ValidatedSkill::name).doesNotHaveDuplicates();
        assertThat(manager.active("default").id()).isEqualTo(activeId);
        assertThat(manager.active("default").skillCatalog().skill("default-Brest")).isEmpty();
        assertThat(constructions).hasValue(0);
        assertThat(retirements).hasValue(0);

        var replacement = manager.validate(manager.capturePreparation("default"), List.of(
                new SkillDocument("kind changed", "name: ayaml\ndescription: route\nrest: true\n")), ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE);
        assertThat(replacement.skills()).containsExactly(
                new ValidatedSkill("default-Zjava", SkillKind.JAVA),
                new ValidatedSkill("default-ayaml", SkillKind.REST));
        assertThat(first.skills()).containsExactly(
                new ValidatedSkill("default-Brest", SkillKind.REST),
                new ValidatedSkill("default-Zjava", SkillKind.JAVA),
                new ValidatedSkill("default-ayaml", SkillKind.YAML));
        assertThat(manager.validate(manager.capturePreparation("default"), List.of(), ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE).skills()).containsExactly(new ValidatedSkill("default-Zjava", SkillKind.JAVA));
        assertThat(retirements).hasValue(0);
        assertThat(manager.prepare(manager.capturePreparation("default"), List.of(), ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE, java.util.Map.of("fixture.key", "fixture")).id()).endsWith("-2");
        assertThat(constructions).hasValue(0);
    }

    @Test
    void validationHasNoPartialMetadataOnErrorsAndConfiguredMatchesSupplied(@TempDir Path directory)
            throws Exception
    {
        SkillMethodBeanPostProcessor javaSkills = mock(SkillMethodBeanPostProcessor.class);
        when(javaSkills.capabilities()).thenReturn(List.of());
        SkillGenerationManager manager = manager(javaSkills,
                () -> new YamlSkillCatalog(loadingProperties(directory)));
        manager.afterSingletonsInstantiated();
        assertThat(manager.validate(manager.capturePreparation("default"), List.of(), ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE).valid()).isTrue();
        assertThat(manager.validate(manager.capturePreparation("default"), List.of(), ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE).skills()).isEmpty();
        String yaml = "name: only\ndescription: model\nmodel: model\n";
        Files.writeString(directory.resolve("configured.yaml"), yaml);
        var configured = manager.validate(manager.capturePreparation("default"), ai.loomspan.testkit.PublicationFixtures.documents(loadingProperties(directory)), ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE);
        var supplied = manager.validate(manager.capturePreparation("default"), List.of(new SkillDocument("diagnostic label", yaml)), ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE);
        assertThat(configured.skills()).containsExactly(new ValidatedSkill("default-only", SkillKind.YAML));
        assertThat(supplied.skills()).isEqualTo(configured.skills());

        var malformed = manager.validate(manager.capturePreparation("default"), List.of(new SkillDocument("good", yaml),
                new SkillDocument("bad", "invalid: [")), ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE);
        assertThat(malformed.valid()).isFalse();
        assertThat(malformed.skills()).isEmpty();
        assertThat(malformed.issues()).anySatisfy(issue -> assertThat(issue.sourceName()).isEqualTo("bad"));
        var duplicate = manager.validate(manager.capturePreparation("default"), List.of(new SkillDocument("first", yaml),
                new SkillDocument("second", yaml)), ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE);
        assertThat(duplicate.valid()).isFalse();
        assertThat(duplicate.skills()).isEmpty();
        assertThat(duplicate.issues()).anySatisfy(issue -> assertThat(issue.message()).contains("only"));
        Files.writeString(directory.resolve("configured.yaml"), yaml.replace("only", "later"));
        assertThat(manager.validate(manager.capturePreparation("default"), ai.loomspan.testkit.PublicationFixtures.documents(loadingProperties(directory)), ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE).skills()).containsExactly(new ValidatedSkill("default-later", SkillKind.YAML));
        assertThat(configured.skills()).containsExactly(new ValidatedSkill("default-only", SkillKind.YAML));
    }

    @Test
    void warningOnlyValidationRetainsCompleteMetadata(@TempDir Path directory) throws Exception
    {
        SkillMethodBeanPostProcessor javaSkills = mock(SkillMethodBeanPostProcessor.class);
        when(javaSkills.capabilities()).thenReturn(List.of(javaSkill("fixedJava")));
        LoomspanProperties properties = loadingProperties(directory);
        properties.setModels(Map.of("gpt-5", properties.getModels().get("model")));
        SkillGenerationManager manager = manager(javaSkills, () -> new YamlSkillCatalog(properties));
        manager.afterSingletonsInstantiated();
        String yaml = Files.readString(Path.of("src/test/resources/skills/valid/output-schema-complex-skill.yaml"));
        var feedback = manager.validate(manager.capturePreparation("default"), List.of(new SkillDocument("complex draft", yaml)), ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE);
        assertThat(feedback.valid()).isTrue();
        assertThat(feedback.issues()).isNotEmpty().allSatisfy(issue ->
                assertThat(issue.severity()).isEqualTo(SkillValidationIssue.Severity.WARNING));
        assertThat(feedback.skills()).containsExactly(
                new ValidatedSkill("default-fixedJava", SkillKind.JAVA),
                new ValidatedSkill("default-outputSchemaComplexSkill", SkillKind.YAML));
    }

    @Test
    void validationIsRepeatableAndLeavesIdsHandlersAndFixedSnapshotUntouched(@TempDir Path directory)
    {
        SkillMethodBeanPostProcessor javaSkills = mock(SkillMethodBeanPostProcessor.class);
        when(javaSkills.capabilities()).thenReturn(List.of(javaSkill("fixedJava")));
        AtomicInteger handlers = new AtomicInteger();
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        RootBeanDefinition handler = new RootBeanDefinition(RestSkillHandler.class);
        handler.setScope(BeanDefinition.SCOPE_PROTOTYPE);
        handler.setInstanceSupplier(() -> {
            handlers.incrementAndGet();
            return (RestSkillHandler) invocation -> "ok";
        });
        beans.registerBeanDefinition("handler", handler);
        SkillGenerationManager manager = new SkillGenerationManager(javaSkills,
                () -> new YamlSkillCatalog(loadingProperties(directory)), new SkillInputContractResolver(), beans);
        assertThatThrownBy(() -> manager.validate(manager.capturePreparation("default"), List.of(), ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("startup");
        manager.afterSingletonsInstantiated();
        String activeId = manager.active("default").id();
        List<SkillDocument> proposed = List.of(new SkillDocument("draft", "name: restSkill\ndescription: rest\nrest: true\n"));
        var first = manager.validate(manager.capturePreparation("default"), proposed, ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE);
        var second = manager.validate(manager.capturePreparation("default"), proposed, ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE);
        assertThat(first.valid()).isTrue();
        assertThat(second).isEqualTo(first);
        assertThat(manager.validate(manager.capturePreparation("default"), List.of(new SkillDocument("bad", "invalid: [")), ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE).valid()).isFalse();
        assertThat(handlers).hasValue(0);
        assertThat(manager.active("default").id()).isEqualTo(activeId);
        assertThat(manager.prepare(manager.capturePreparation("default"), proposed, ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE, java.util.Map.of("fixture.key", "fixture")).id()).endsWith("-2");
        assertThat(handlers).hasValue(1);
        verify(javaSkills).capabilities();
    }

    @Test
    void validationMatchesPreparationForConflictsAndReferences(@TempDir Path directory)
    {
        SkillMethodBeanPostProcessor javaSkills = mock(SkillMethodBeanPostProcessor.class);
        when(javaSkills.capabilities()).thenReturn(List.of(javaSkill("fixedJava")));
        SkillGenerationManager manager = manager(javaSkills, () -> new YamlSkillCatalog(loadingProperties(directory)));
        manager.afterSingletonsInstantiated();
        List<SkillDocument> conflict = List.of(new SkillDocument("conflict", "name: fixedJava\ndescription: conflict\nmodel: model\n"));
        var conflictFeedback = manager.validate(manager.capturePreparation("default"), conflict, ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE);
        assertThat(conflictFeedback.skills()).isEmpty();
        assertThat(conflictFeedback.issues()).anySatisfy(issue -> {
            assertThat(issue.severity()).isEqualTo(SkillValidationIssue.Severity.ERROR);
            assertThat(issue.fieldPath()).isEqualTo("name");
        });
        assertThatThrownBy(() -> manager.prepare(manager.capturePreparation("default"), conflict, ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE, java.util.Map.of("fixture.key", "fixture"))).hasMessageContaining("already registered");
        List<SkillDocument> missing = List.of(new SkillDocument("parent", "name: parent\ndescription: parent\nmodel: model\nallowed_skills:\n  - name: default-missing\n"));
        var missingFeedback = manager.validate(manager.capturePreparation("default"), missing, ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE);
        assertThat(missingFeedback.skills()).isEmpty();
        assertThat(missingFeedback.issues()).extracting(issue -> issue.fieldPath())
                .contains("allowed_skills");
        assertThatThrownBy(() -> manager.prepare(manager.capturePreparation("default"), missing, ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE, java.util.Map.of("fixture.key", "fixture"))).hasMessageContaining("Unknown child skill");
        assertThat(manager.validate(manager.capturePreparation("default"), List.of(), ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE).valid()).isTrue();
        assertThat(manager.prepare(manager.capturePreparation("default"), List.of(), ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE, java.util.Map.of("fixture.key", "fixture")).capabilities()).extracting(CapabilityMetadata::name)
                .containsExactly("default-fixedJava");
    }

    @Test
    void restHandlerDeclarationChecksAreAdvisoryAndDoNotConstructBeans(@TempDir Path directory)
    {
        SkillMethodBeanPostProcessor javaSkills = mock(SkillMethodBeanPostProcessor.class);
        when(javaSkills.capabilities()).thenReturn(List.of());
        List<SkillDocument> rest = List.of(new SkillDocument("REST draft",
                "name: leaf\ndescription: leaf\nrest: true\n"));
        SkillGenerationManager none = manager(javaSkills, () -> new YamlSkillCatalog(loadingProperties(directory)));
        none.afterSingletonsInstantiated();
        var noHandlerFeedback = none.validate(none.capturePreparation("default"), rest, ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE);
        assertThat(noHandlerFeedback.skills()).isEmpty();
        assertThat(noHandlerFeedback.issues()).anySatisfy(issue ->
                assertThat(issue.message()).contains("found none"));
        assertThatThrownBy(() -> none.prepare(none.capturePreparation("default"), rest, ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE, java.util.Map.of("fixture.key", "fixture"))).hasMessageContaining("found none");
        assertThat(none.validate(none.capturePreparation("default"), List.of(), ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE).valid()).isTrue();

        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        AtomicInteger constructions = new AtomicInteger();
        for (String name : List.of("first", "second"))
        {
            RootBeanDefinition handler = new RootBeanDefinition(RestSkillHandler.class);
            handler.setScope(BeanDefinition.SCOPE_PROTOTYPE);
            handler.setInstanceSupplier(() -> {
                constructions.incrementAndGet();
                return (RestSkillHandler) invocation -> "ok";
            });
            beans.registerBeanDefinition(name, handler);
        }
        SkillGenerationManager multiple = new SkillGenerationManager(javaSkills,
                () -> new YamlSkillCatalog(loadingProperties(directory)), new SkillInputContractResolver(), beans);
        multiple.afterSingletonsInstantiated();
        var multipleHandlerFeedback = multiple.validate(multiple.capturePreparation("default"), rest, ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE);
        assertThat(multipleHandlerFeedback.skills()).isEmpty();
        assertThat(multipleHandlerFeedback.issues()).anySatisfy(issue ->
                assertThat(issue.message()).contains("first, second"));
        assertThatThrownBy(() -> multiple.prepare(multiple.capturePreparation("default"), rest, ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE, java.util.Map.of("fixture.key", "fixture"))).hasMessageContaining("first, second");
        assertThat(constructions).hasValue(0);
        assertThat(multiple.validate(multiple.capturePreparation("default"), List.of(), ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE).valid()).isTrue();
    }

    @Test
    void failedNamedDocumentSuppressesOnlyDependentChildNoise(@TempDir Path directory)
    {
        SkillMethodBeanPostProcessor javaSkills = mock(SkillMethodBeanPostProcessor.class);
        when(javaSkills.capabilities()).thenReturn(List.of());
        SkillGenerationManager manager = manager(javaSkills, () -> new YamlSkillCatalog(loadingProperties(directory)));
        manager.afterSingletonsInstantiated();
        var result = manager.validate(manager.capturePreparation("default"), List.of(
                new SkillDocument("bad", "name: couldExist\ndescription: bad\nrest: true\nmodel: forbidden\n"),
                new SkillDocument("parent", "name: parent\ndescription: parent\nmodel: model\nallowed_skills:\n  - name: default-couldExist\n  - name: default-certainlyMissing\n")), ai.loomspan.testkit.PublicationFixtures.configuration(), ai.loomspan.api.NamespaceVisibility.PRIVATE);
        assertThat(result.issues()).extracting(issue -> issue.message())
                .anySatisfy(message -> assertThat(message).contains("certainlyMissing"))
                .noneSatisfy(message -> assertThat(message).contains("Unknown child skill 'couldExist'"));
    }
    private static SkillGenerationManager manager(SkillMethodBeanPostProcessor javaSkills,
            java.util.function.Supplier<YamlSkillCatalog> catalogs)
    {
        return new SkillGenerationManager(javaSkills, catalogs, new SkillInputContractResolver(),
                new StaticListableBeanFactory());
    }

    private static LoomspanProperties loadingProperties(Path directory)
    {
        LoomspanProperties properties = new LoomspanProperties();
        LoomspanProperties.Skills skills = new LoomspanProperties.Skills();
        skills.setLocations(List.of(directory.toUri() + "*.yaml"));
        properties.setSkills(skills);
        LoomspanProperties.ConnectionProperties connection = new LoomspanProperties.ConnectionProperties();
        connection.setDriver(AiDriver.OPENAI);
        properties.setConnections(Map.of("connection", connection));
        LoomspanProperties.ModelCatalogEntry model = new LoomspanProperties.ModelCatalogEntry();
        model.setConnection("connection");
        model.setProviderModel("provider-model");
        model.setThinkingLevels(java.util.Set.of("medium"));
        properties.setModels(Map.of("model", model));
        return properties;
    }

    private static YamlSkillCatalog catalog(YamlSkillDefinition... definitions)
    {
        YamlSkillCatalog catalog = mock(YamlSkillCatalog.class);
        when(catalog.checkedConfigured(true)).thenReturn(
                new YamlSkillCatalog.CheckedDocuments(List.of(definitions), List.of()));
        return catalog;
    }

    private static CapabilityMetadata javaSkill(String name)
    {
        String canonical = "default-" + name;
        return new CapabilityMetadata("java:" + name, canonical, name, SkillExecutionDescriptor.none(),
                SkillAccessPolicy.unrestricted(), arguments -> name, CapabilityKind.JAVA_SKILL,
                CapabilityToolDescriptor.generic(canonical, name),
                new SkillSource(null, "testBean", name + "()"));
    }

    private static YamlSkillDefinition yamlSkill(String name, List<String> allowed)
    {
        YamlSkillManifest manifest = new YamlSkillManifest();
        manifest.setName(name);
        manifest.setDescription(name);
        manifest.setModel("model");
        manifest.setAllowedSkills(allowed.stream()
                .map(child -> new YamlSkillManifest.AllowedSkillManifest("default-" + child, null, null, null)).toList());
        byte[] yaml = ("name: " + name + "\ndescription: " + name + "\nmodel: model\n")
                .getBytes(StandardCharsets.UTF_8);
        ByteArrayResource resource = new ByteArrayResource(yaml, name + ".yaml")
        {
            @Override public String getFilename() { return name + ".yaml"; }
        };
        return new YamlSkillDefinition("default", resource, manifest,
                new EffectiveSkillExecutionConfiguration("model", "connection", AiDriver.OPENAI,
                        "provider-model", "medium"));
    }

    private static YamlSkillDefinition restSkill(String name)
    {
        YamlSkillManifest manifest = new YamlSkillManifest();
        manifest.setName(name);
        manifest.setDescription(name);
        manifest.setRest(true);
        byte[] yaml = ("name: " + name + "\ndescription: " + name + "\nrest: true\n")
                .getBytes(StandardCharsets.UTF_8);
        ByteArrayResource resource = new ByteArrayResource(yaml, name + ".yaml")
        {
            @Override public String getFilename() { return name + ".yaml"; }
        };
        return new YamlSkillDefinition("default", resource, manifest, null);
    }
}
