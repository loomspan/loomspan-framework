package ai.loomspan.sample.travel;

import ai.loomspan.api.SkillExecutionEvent;
import ai.loomspan.api.SkillExecutionView;
import ai.loomspan.api.SkillTemplate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TravelControllerTest {

    @Test
    void planDelegatesWithRequestTextAndScenario() {
        SkillTemplate skillTemplate = mock(SkillTemplate.class);
        TravelController controller = new TravelController(skillTemplate, new DefaultResourceLoader());
        List<SkillExecutionEvent> events = List.of();
        doAnswer(invocation -> {
            Consumer<SkillExecutionView> observer = invocation.getArgument(2);
            observer.accept(new SkillExecutionView("travel-session", events));
            return "{\"summary\":\"weekend trip\"}";
        }).when(skillTemplate).invoke(eq("planTrip"), any(Map.class), any());

        Map<String, Object> response = controller.plan(
                new TravelController.PlanTripRequest("Plan a cheap NYC weekend", "budget-nyc-weekend"));

        ArgumentCaptor<Map<String, Object>> inputCaptor = ArgumentCaptor.forClass(Map.class);
        verify(skillTemplate).invoke(eq("planTrip"), inputCaptor.capture(), any());
        assertThat(inputCaptor.getValue())
                .containsEntry("requestText", "Plan a cheap NYC weekend")
                .containsEntry("scenario", "budget-nyc-weekend")
                .doesNotContainValue(null);
        assertThat(response.get("result")).isEqualTo("{\"summary\":\"weekend trip\"}");
        assertThat(response.get("sessionId")).isEqualTo("travel-session");
        assertThat(response.get("executionEvents")).isEqualTo(events);
        assertThat(response).doesNotContainKey("filePath");
    }

    @Test
    void planOmitsNullOptionalScenario() {
        SkillTemplate skillTemplate = mock(SkillTemplate.class);
        TravelController controller = new TravelController(skillTemplate, new DefaultResourceLoader());
        doAnswer(invocation -> "\"ok\"")
                .when(skillTemplate).invoke(eq("planTrip"), any(Map.class), any());

        controller.plan(new TravelController.PlanTripRequest("request only", null));

        ArgumentCaptor<Map<String, Object>> inputCaptor = ArgumentCaptor.forClass(Map.class);
        verify(skillTemplate).invoke(eq("planTrip"), inputCaptor.capture(), any());
        assertThat(inputCaptor.getValue())
                .containsEntry("requestText", "request only")
                .doesNotContainKey("scenario")
                .doesNotContainValue(null);
    }

    @Test
    void planScenarioLoadsFixtureAndSetsScenario() {
        SkillTemplate skillTemplate = mock(SkillTemplate.class);
        TravelController controller = new TravelController(skillTemplate, new DefaultResourceLoader());
        List<SkillExecutionEvent> events = List.of();
        doAnswer(invocation -> {
            Consumer<SkillExecutionView> observer = invocation.getArgument(2);
            observer.accept(new SkillExecutionView("scenario-session", events));
            return "{\"summary\":\"budget plan\"}";
        }).when(skillTemplate).invoke(eq("planTrip"), any(Map.class), any());

        Map<String, Object> response = controller.planScenario("budget-nyc-weekend");

        ArgumentCaptor<Map<String, Object>> inputCaptor = ArgumentCaptor.forClass(Map.class);
        verify(skillTemplate).invoke(eq("planTrip"), inputCaptor.capture(), any());
        Map<String, Object> inputs = inputCaptor.getValue();
        assertThat(inputs.get("scenario")).isEqualTo("budget-nyc-weekend");
        assertThat(inputs.get("requestText").toString())
                .containsIgnoringCase("New York")
                .containsIgnoringCase("400");
        assertThat(response.get("sessionId")).isEqualTo("scenario-session");
        assertThat(response.get("executionEvents")).isEqualTo(events);
        assertThat(response.get("result")).isEqualTo("{\"summary\":\"budget plan\"}");
    }

    @Test
    void scenariosListsFourKnownKeys() {
        TravelController controller = new TravelController(mock(SkillTemplate.class), new DefaultResourceLoader());

        List<Map<String, String>> scenarios = controller.scenarios();

        assertThat(scenarios).extracting(s -> s.get("name"))
                .containsExactly(
                        "budget-nyc-weekend",
                        "loyalty-points-max",
                        "fastest-sfo-sea",
                        "underspecified");
        assertThat(scenarios).allSatisfy(s -> assertThat(s.get("description")).isNotBlank());
    }

    @Test
    void everyListedScenarioHasClasspathFixture() {
        ResourceLoader resourceLoader = new DefaultResourceLoader();
        TravelController controller = new TravelController(mock(SkillTemplate.class), resourceLoader);

        assertThat(controller.scenarios()).isNotEmpty().allSatisfy(scenario -> {
            String name = scenario.get("name");
            Resource fixture = resourceLoader.getResource("classpath:/fixtures/travel/" + name + ".txt");
            assertThat(fixture.exists())
                    .as("fixture for listed scenario '%s'", name)
                    .isTrue();
        });
    }

    @Test
    void planScenarioRejectsUnknownName() {
        TravelController controller = new TravelController(mock(SkillTemplate.class), new DefaultResourceLoader());

        assertThatThrownBy(() -> controller.planScenario("not-a-real-scenario"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unknown scenario");
    }

    @Test
    void planRejectsMissingRequestText() {
        TravelController controller = new TravelController(mock(SkillTemplate.class), new DefaultResourceLoader());

        assertThatThrownBy(() -> controller.plan(
                new TravelController.PlanTripRequest(null, "budget-nyc-weekend")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("requestText is required");

        assertThatThrownBy(() -> controller.plan(
                new TravelController.PlanTripRequest("  ", null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("requestText is required");
    }
}
