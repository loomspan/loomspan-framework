package ai.loomspan.api;

import org.junit.jupiter.api.Test;

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
