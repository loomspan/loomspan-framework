package ai.loomspan.api;

import ai.loomspan.api.extensions.v1.SkillExecutorInvocation;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationApiValueTest
{
    @Test
    void rootInvocationReturnsTextAndSessionIdentity() throws Exception
    {
        Class<?> result = SkillTemplate.class.getMethod("invoke", String.class, String.class, Map.class).getReturnType();
        assertThat(result.getSimpleName()).isEqualTo("SkillInvocationResult");
        assertThat(result.getRecordComponents())
                .extracting(component -> List.of(component.getName(), component.getType()))
                .containsExactly(List.of("text", String.class), List.of("sessionId", String.class));
    }

    @Test
    void invocationValuesPreserveTextAndImmutableIdentity()
    {
        String text = "  {\"answer\":42}\r\n";
        assertThat(new SkillInvocationResult(text, "root").text()).isEqualTo(text);
        assertThat(new SkillInvocationResult(text, "root").sessionId()).isEqualTo("root");
        assertThatThrownBy(() -> new SkillInvocationResult(null, "root")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SkillInvocationResult("", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SkillInvocationResult("", " \n")).isInstanceOf(IllegalArgumentException.class);
        var cause = new IllegalStateException("original");
        var failure = new SkillException("failure", cause, null, "root");
        assertThat(failure.getSessionId()).isEqualTo("root");
        assertThat(failure).hasCause(cause);
        assertThat(new SkillException("before").getSessionId()).isNull();
        assertThat(new SkillException("before", cause).getSessionId()).isNull();
        assertThat(new SkillException("before", cause, null).getSessionId()).isNull();
        var denial = new SkillAccessDeniedException("denied", cause, "root");
        assertThat(denial).isInstanceOf(org.springframework.security.access.AccessDeniedException.class).hasCause(cause);
        assertThat(denial.getSessionId()).isEqualTo("root");
        assertThatThrownBy(() -> new SkillAccessDeniedException("denied", cause, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SkillAccessDeniedException("denied", cause, " ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(new PlannerInabilityException("reason", cause, null, "root").getSessionId()).isEqualTo("root");
        assertThat(new RecoverableSkillException("reason", cause, null, "root").getSessionId()).isEqualTo("root");
        var input = new SkillInputValidationException("invalid", cause, List.of(), null, "root");
        assertThat(input.getSessionId()).isEqualTo("root");
        assertThat(input).hasCause(cause);
    }

    @Test
    void rootOperationsRequireExplicitIdentityComponents() throws Exception
    {
        for (Class<?> input : List.of(Object.class, Map.class))
        {
            assertThat(SkillTemplate.class.getMethod("validate", String.class, String.class, input).getReturnType())
                    .isEqualTo(void.class);
            assertThat(SkillTemplate.class.getMethod("invoke", String.class, String.class, input).getReturnType())
                    .isEqualTo(SkillInvocationResult.class);
            assertThat(SkillTemplate.class.getMethod("invoke", String.class, String.class, input,
                    java.util.function.Consumer.class).getReturnType()).isEqualTo(SkillInvocationResult.class);
            assertThat(SkillInvocationHandoff.class.getMethod("handoff", String.class, String.class, input).getReturnType())
                    .isEqualTo(AdmittedSkillInvocation.class);
        }
    }

    @Test
    void plannerInabilityUsesTheSupportedRecoverableContract() throws Exception
    {
        String reason = "  no evidence\r\n\u96ea\t";
        RecoverableSkillException failure = new PlannerInabilityException(reason);
        assertThat(failure).hasMessage(reason);
        assertThat(PlannerInabilityException.class.getConstructors())
                .containsExactlyInAnyOrder(PlannerInabilityException.class.getConstructor(String.class), PlannerInabilityException.class.getConstructor(String.class, Throwable.class, SkillFailureDiagnosis.class, String.class));
        assertThat(PlannerInabilityException.class.getDeclaredMethods()).isEmpty();
        assertThatThrownBy(() -> new PlannerInabilityException(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PlannerInabilityException("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlannerInabilityException(" \r\n\t")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publicCatalogAndValidationExposeOnlyTheTicketedShape() throws Exception
    {
        assertThat(SkillKind.values()).containsExactly(SkillKind.YAML, SkillKind.JAVA);
        assertThat(SkillDescriptor.class.getRecordComponents())
                .extracting(component -> List.of(component.getName(), component.getType()))
                .containsExactly(
                        List.of("namespace", String.class),
                        List.of("localName", String.class),
                        List.of("description", String.class),
                        List.of("kind", SkillKind.class),
                        List.of("inputSchema", String.class),
                        List.of("outputSchema", String.class));
        assertThat(SkillDescriptor.class.getConstructors()).hasSize(1);
        assertThat(SkillDescriptor.class.getConstructors()[0].getParameterCount()).isEqualTo(6);
        assertThat(SkillCatalog.class.getDeclaredMethods())
                .extracting(java.lang.reflect.Method::getName)
                .containsExactlyInAnyOrder("namespace", "generationId", "publicationVersion", "skills", "skill");
        var publicationMethod = SkillCatalog.class.getMethod("publicationVersion");
        assertThat(publicationMethod.getParameterTypes()).isEmpty();
        assertThat(publicationMethod.getGenericReturnType().getTypeName())
                .isEqualTo("java.util.Optional<java.lang.String>");
        var skillsMethod = SkillCatalog.class.getMethod("skills");
        assertThat(skillsMethod.getParameterTypes()).isEmpty();
        assertThat(skillsMethod.getReturnType()).isEqualTo(List.class);
        assertThat(skillsMethod.getGenericReturnType().getTypeName())
                .isEqualTo("java.util.List<ai.loomspan.api.SkillDescriptor>");
        var skillMethod = SkillCatalog.class.getMethod("skill", String.class);
        assertThat(skillMethod.getParameterTypes()).containsExactly(String.class);
        assertThat(skillMethod.getReturnType()).isEqualTo(java.util.Optional.class);
        assertThat(skillMethod.getGenericReturnType().getTypeName())
                .isEqualTo("java.util.Optional<ai.loomspan.api.SkillDescriptor>");
        assertThat(SkillTemplate.class.getMethod("validate", String.class, String.class, Object.class).getReturnType())
                .isEqualTo(void.class);
        assertThat(SkillTemplate.class.getMethod("validate", String.class, String.class, Map.class).getReturnType())
                .isEqualTo(void.class);
        assertThatThrownBy(() -> new SkillDescriptor("default", " ", "description", SkillKind.YAML, "{}", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void handoffAndAdmissionHandleExposeOnlyTheSupportedSingleUseShape() throws Exception
    {
        assertThat(SkillInvocationHandoff.class.getDeclaredMethods())
                .extracting(method -> List.of(method.getName(), method.getReturnType(), List.of(method.getParameterTypes())))
                .containsExactlyInAnyOrder(
                        List.of("handoff", AdmittedSkillInvocation.class, List.of(String.class, String.class, Object.class)),
                        List.of("handoff", AdmittedSkillInvocation.class, List.of(String.class, String.class, Map.class)));
        assertThat(AdmittedSkillInvocation.class.getDeclaredMethods())
                .extracting(method -> List.of(method.getName(), method.getReturnType(), List.of(method.getParameterTypes())))
                .containsExactlyInAnyOrder(
                        List.of("generationId", String.class, List.of()),
                        List.of("invoke", SkillInvocationResult.class, List.of()),
                        List.of("invoke", SkillInvocationResult.class, List.of(java.util.function.Consumer.class)),
                        List.of("release", void.class, List.of()));
        assertThat(SkillTemplate.class.getDeclaredMethods()).hasSize(6);
    }

    @Test
    void metadataExposesValidatedReusableIdentityComponents() throws Exception
    {
        assertThat(ValidatedSkill.class.getRecordComponents())
                .extracting(component -> List.of(component.getName(), component.getType()))
                .containsExactly(List.of("namespace", String.class), List.of("localName", String.class),
                        List.of("kind", SkillKind.class));
        assertThat(ValidatedSkill.class.getConstructors()).hasSize(1);
        assertThat(ValidatedSkill.class.getConstructors()[0].getParameterTypes())
                .containsExactly(String.class, String.class, SkillKind.class);
        assertThatThrownBy(() -> ValidatedSkill.class.getMethod("name")).isInstanceOf(NoSuchMethodException.class);
        assertThatThrownBy(() -> SkillDescriptor.class.getMethod("name")).isInstanceOf(NoSuchMethodException.class);
        for (var pair : List.of(List.of("orders", "lookup"), List.of("order-support", "find-order")))
        {
            var validated = new ValidatedSkill(pair.get(0), pair.get(1), SkillKind.JAVA);
            var descriptor = new SkillDescriptor(pair.get(0), pair.get(1), "  exact description  ", SkillKind.JAVA, " { } ", null);
            assertThat(validated.namespace()).isEqualTo(pair.get(0));
            assertThat(validated.localName()).isEqualTo(pair.get(1));
            assertThat(validated.qualifiedName()).isEqualTo(pair.get(0) + ":" + pair.get(1));
            assertThat(descriptor.namespace()).isEqualTo(validated.namespace());
            assertThat(descriptor.localName()).isEqualTo(validated.localName());
            assertThat(descriptor.qualifiedName()).isEqualTo(validated.qualifiedName());
            assertThat(descriptor.description()).isEqualTo("  exact description  ");
            assertThat(descriptor.inputSchema()).isEqualTo(" { } ");
            assertThat(descriptor.outputSchema()).isNull();
            assertThat(validated).isEqualTo(new ValidatedSkill(pair.get(0), pair.get(1), SkillKind.JAVA));
        }
        for (String invalid : List.of("", " ", "Orders", "lookup_name", "a--b", "1lookup", "\u00e9", "claude", "x-anthropic", "orders:lookup", "a".repeat(65)))
        {
            assertThatThrownBy(() -> new ValidatedSkill(invalid, "lookup", SkillKind.YAML)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new SkillDescriptor("orders", invalid, "description", SkillKind.YAML, "{}", null)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(new ValidatedSkill("order-support", "a".repeat(49), SkillKind.YAML).localName()).hasSize(49);
        assertThatThrownBy(() -> new ValidatedSkill("order-support", "a".repeat(50), SkillKind.YAML)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SkillDescriptor("order-support", "a".repeat(50), "description", SkillKind.YAML, "{}", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ValidatedSkill(null, "lookup", SkillKind.JAVA)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ValidatedSkill("orders", null, SkillKind.JAVA)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ValidatedSkill("orders", "lookup", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SkillDescriptor(null, "lookup", "description", SkillKind.JAVA, "{}", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SkillDescriptor("orders", null, "description", SkillKind.JAVA, "{}", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SkillDescriptor("orders", "lookup", null, SkillKind.JAVA, "{}", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SkillDescriptor("orders", "lookup", "description", null, "{}", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SkillDescriptor("orders", "lookup", "description", SkillKind.JAVA, null, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void executorInvocationDeeplyCopiesContainersAndPreservesLeavesAndNulls()
    {
        Object leaf = new ByteArrayResource(new byte[] {1, 2, 3});
        List<Object> values = new ArrayList<>();
        values.add(leaf);
        values.add(null);
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("values", values);
        List<Object> nonStringKeyedValues = new ArrayList<>(List.of("nested"));
        Map<Object, Object> nonStringKeyed = new LinkedHashMap<>();
        nonStringKeyed.put(7, nonStringKeyedValues);
        nested.put("nonStringKeyed", nonStringKeyed);
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("nested", nested);

        SkillExecutorInvocation invocation = ai.loomspan.internal.core.ExecutorInvocations.create(input, Map.of(), Map.of(), java.util.Map.of(), "tax-lookup", "generation-a", () -> false, java.util.Optional::empty, org.mockito.Mockito.mock(ai.loomspan.api.extensions.v1.ExecutorDiagnostics.class));
        assertThat(invocation.skillName()).isEqualTo("tax-lookup");
        assertThat(invocation.executorConfigGenerationId()).isEqualTo("generation-a");
        values.add("late");
        nonStringKeyedValues.add("late");
        nonStringKeyed.put(8, "late");
        nested.put("late", true);
        input.clear();

        Map<?, ?> copiedNested = (Map<?, ?>) invocation.input().get("nested");
        assertThat((List<Object>) copiedNested.get("values")).containsExactly(leaf, null);
        assertThat(((List<?>) copiedNested.get("values")).getFirst()).isSameAs(leaf);
        assertThat((Map<Object, Object>) copiedNested.get("nonStringKeyed"))
                .containsEntry(7, List.of("nested"));
        assertThatThrownBy(() -> invocation.input().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> ((List<Object>) copiedNested.get("values")).add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> ((Map<Object, Object>) copiedNested.get("nonStringKeyed")).put(8, "x"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> ai.loomspan.internal.core.ExecutorInvocations.create(Map.of(), Map.of(), Map.of(), java.util.Map.of(), null, "generation-a", () -> false, java.util.Optional::empty, org.mockito.Mockito.mock(ai.loomspan.api.extensions.v1.ExecutorDiagnostics.class))).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ai.loomspan.internal.core.ExecutorInvocations.create(null, Map.of(), Map.of(), java.util.Map.of(), "tax-lookup", "generation-a", () -> false, java.util.Optional::empty, org.mockito.Mockito.mock(ai.loomspan.api.extensions.v1.ExecutorDiagnostics.class))).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ai.loomspan.internal.core.ExecutorInvocations.create(Map.of(), Map.of(), Map.of(), java.util.Map.of(), "tax-lookup", null, () -> false, java.util.Optional::empty, org.mockito.Mockito.mock(ai.loomspan.api.extensions.v1.ExecutorDiagnostics.class))).isInstanceOf(NullPointerException.class);
    }

    @Test
    void executorInvocationExposesOnlyTheAgreedInvocationData()
    {
        assertThat(SkillExecutorInvocation.class.getDeclaredMethods())
                .extracting(java.lang.reflect.Method::getName)
                .containsExactlyInAnyOrder("input", "skillConfig", "executorConfig", "consumerExecutorConfig", "skillName",
                        "executorConfigGenerationId", "diagnostics", "isCancelled", "remainingTime");
    }

    @Test
    void skillExecutionViewDefensivelyCopiesEvents()
    {
        List<SkillExecutionEvent> source = new ArrayList<>();
        SkillExecutionView view = new SkillExecutionView("session-1", source);
        source.add(event(Map.of()));

        assertThat(view.events()).isEmpty();
        assertThatThrownBy(() -> view.events().add(event(Map.of())))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void skillExecutionEventDeeplyCopiesDetails()
    {
        List<Object> nestedList = new ArrayList<>(List.of("one"));
        Map<String, Object> nestedMap = new LinkedHashMap<>();
        nestedMap.put("items", nestedList);
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("nested", nestedMap);

        SkillExecutionEvent event = event(source);
        nestedList.add("two");
        nestedMap.put("later", true);
        source.put("new", "value");

        assertThat(event.details()).containsOnlyKeys("nested");
        Map<?, ?> copiedNested = (Map<?, ?>) event.details().get("nested");
        assertThat(copiedNested.get("items")).isEqualTo(List.of("one"));
        assertThatThrownBy(() -> event.details().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> ((List<Object>) copiedNested.get("items")).add("three"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void skillExecutionEventSupportsNullFrameAndRoute()
    {
        SkillExecutionEvent event = event(Map.of("ok", true));

        assertThat(event.frameId()).isNull();
        assertThat(event.route()).isNull();
    }

    @Test
    void skillInputValidationExceptionDefensivelyCopiesIssues()
    {
        List<SkillInputValidationIssue> source = new ArrayList<>();
        source.add(new SkillInputValidationIssue("$.name", "required", "Name is required"));
        SkillInputValidationException exception = new SkillInputValidationException("invalid", source);
        source.clear();

        assertThat(exception.getIssues()).hasSize(1);
        assertThatThrownBy(() -> exception.getIssues().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void skillInputValidationIssueExposesOnlyPathCodeAndMessage()
    {
        assertThat(SkillInputValidationIssue.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("path", "code", "message");
    }

    @Test
    void skillExceptionPreservesLegacyConstructorsAndAddsDiagnosis()
    {
        assertThat(SkillException.class.getDeclaredConstructors())
                .extracting(constructor -> List.of(constructor.getParameterTypes()))
                .containsExactlyInAnyOrder(
                        List.of(String.class),
                        List.of(String.class, Throwable.class),
                        List.of(String.class, Throwable.class, SkillFailureDiagnosis.class), List.of(String.class, Throwable.class, SkillFailureDiagnosis.class, String.class));
    }

    @Test
    void diagnosisIsAnImmutableExactValueAndLegacyExceptionsHaveNoDiagnosis()
    {
        var issue = new SkillContractIssue("/a~1b~0c", "type", "  exact\nexplanation  ");
        var issues = new ArrayList<SkillContractIssue>();
        issues.add(issue);
        var diagnosis = new SkillFailureDiagnosis("default-child", SkillContractStage.OUTPUT_SCHEMA, "Failed contract", issues);
        issues.clear();
        assertThat(diagnosis.issues()).containsExactly(issue);
        assertThatThrownBy(() -> diagnosis.issues().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(new SkillContractIssue("", "type", "root").path()).isEmpty();
        assertThat(new SkillException("legacy", null).getFailureDiagnosis()).isEmpty();
        assertThat(new SkillInputValidationException("legacy", List.of()).getFailureDiagnosis()).isEmpty();
        assertThat(new SkillException("failure", null, diagnosis).getFailureDiagnosis()).contains(diagnosis);
        assertThat(issue.message()).isEqualTo("  exact\nexplanation  ");
    }

    @Test
    void legacyMessageConstructorsRetainThrowableCauseInitialization()
    {
        var cause = new IllegalStateException("later cause");
        var failure = new SkillException("message");
        var input = new SkillInputValidationException("message", List.of());
        assertThat(failure.initCause(cause)).isSameAs(failure);
        assertThat(input.initCause(cause)).isSameAs(input);
        assertThat(failure.getCause()).isSameAs(cause);
        assertThat(input.getCause()).isSameAs(cause);
    }

    private SkillExecutionEvent event(Map<String, Object> details)
    {
        return new SkillExecutionEvent(Instant.parse("2026-07-15T12:00:00Z"), "INFO", "THOUGHT", details, null, null);
    }
}
