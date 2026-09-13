package ai.loomspan.api;

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
    void restInvocationDeeplyCopiesContainersAndPreservesLeavesAndNulls()
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

        RestSkillInvocation invocation = new RestSkillInvocation("lookup", input);
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
        assertThatThrownBy(() -> new RestSkillInvocation(null, Map.of())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RestSkillInvocation("lookup", null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void restSpiExposesOnlyTheTicketedJdkSignature()
    {
        assertThat(RestSkillInvocation.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("skillName", "input");
        assertThat(RestSkillHandler.class.getDeclaredMethods())
                .singleElement()
                .satisfies(method -> {
                    assertThat(method.getName()).isEqualTo("handle");
                    assertThat(method.getReturnType()).isEqualTo(String.class);
                    assertThat(method.getParameterTypes()).containsExactly(RestSkillInvocation.class);
                });
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
    void skillExceptionHasOnlyMessageAndMessageCauseConstructors()
    {
        assertThat(SkillException.class.getDeclaredConstructors())
                .extracting(constructor -> List.of(constructor.getParameterTypes()))
                .containsExactlyInAnyOrder(
                        List.of(String.class),
                        List.of(String.class, Throwable.class));
    }

    private SkillExecutionEvent event(Map<String, Object> details)
    {
        return new SkillExecutionEvent(Instant.parse("2026-07-15T12:00:00Z"), "INFO", "THOUGHT", details, null, null);
    }
}
