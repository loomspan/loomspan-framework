package ai.loomspan.sample.incident;

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

class IncidentControllerTest {

    @Test
    void handleDelegatesToHandleIncidentWithTicketAndScenario() {
        SkillTemplate skillTemplate = mock(SkillTemplate.class);
        IncidentController controller = new IncidentController(skillTemplate, new DefaultResourceLoader());
        List<SkillExecutionEvent> events = List.of();
        doAnswer(invocation -> {
            Consumer<SkillExecutionView> observer = invocation.getArgument(2);
            observer.accept(new SkillExecutionView("incident-session", events));
            return "{\"severity\":\"SEV2\"}";
        }).when(skillTemplate).invoke(eq("handleIncident"), any(Map.class), any());

        Map<String, Object> response = controller.handle(
                new IncidentController.HandleIncidentRequest("EU DNS down", "network-dns"));

        ArgumentCaptor<Map<String, Object>> inputCaptor = ArgumentCaptor.forClass(Map.class);
        verify(skillTemplate).invoke(eq("handleIncident"), inputCaptor.capture(), any());
        assertThat(inputCaptor.getValue())
                .containsEntry("ticketText", "EU DNS down")
                .containsEntry("scenario", "network-dns")
                .doesNotContainValue(null);
        assertThat(response.get("result")).isEqualTo("{\"severity\":\"SEV2\"}");
        assertThat(response.get("sessionId")).isEqualTo("incident-session");
        assertThat(response.get("executionEvents")).isEqualTo(events);
        assertThat(response).doesNotContainKey("filePath");
    }

    @Test
    void handleOmitsScenarioWhenNotProvided() {
        SkillTemplate skillTemplate = mock(SkillTemplate.class);
        IncidentController controller = new IncidentController(skillTemplate, new DefaultResourceLoader());
        doAnswer(invocation -> "\"ok\"")
                .when(skillTemplate).invoke(eq("handleIncident"), any(Map.class), any());

        controller.handle(new IncidentController.HandleIncidentRequest("ticket only", null));

        ArgumentCaptor<Map<String, Object>> inputCaptor = ArgumentCaptor.forClass(Map.class);
        verify(skillTemplate).invoke(eq("handleIncident"), inputCaptor.capture(), any());
        assertThat(inputCaptor.getValue())
                .containsEntry("ticketText", "ticket only")
                .doesNotContainKey("scenario")
                .doesNotContainValue(null);
    }

    @Test
    void handleScenarioLoadsFixtureAndSetsScenarioKey() {
        SkillTemplate skillTemplate = mock(SkillTemplate.class);
        IncidentController controller = new IncidentController(skillTemplate, new DefaultResourceLoader());
        List<SkillExecutionEvent> events = List.of();
        doAnswer(invocation -> {
            Consumer<SkillExecutionView> observer = invocation.getArgument(2);
            observer.accept(new SkillExecutionView("scenario-session", events));
            return "{\"category\":\"network\"}";
        }).when(skillTemplate).invoke(eq("handleIncident"), any(Map.class), any());

        Map<String, Object> response = controller.handleScenario("network-dns");

        ArgumentCaptor<Map<String, Object>> inputCaptor = ArgumentCaptor.forClass(Map.class);
        verify(skillTemplate).invoke(eq("handleIncident"), inputCaptor.capture(), any());
        Map<String, Object> inputs = inputCaptor.getValue();
        assertThat(inputs.get("scenario")).isEqualTo("network-dns");
        assertThat(inputs.get("ticketText").toString())
                .contains("api.example.com")
                .containsIgnoringCase("EU");
        assertThat(response.get("sessionId")).isEqualTo("scenario-session");
        assertThat(response.get("executionEvents")).isEqualTo(events);
        assertThat(response.get("result")).isEqualTo("{\"category\":\"network\"}");
    }

    @Test
    void scenariosListsFourKnownKeys() {
        IncidentController controller = new IncidentController(mock(SkillTemplate.class), new DefaultResourceLoader());

        List<Map<String, String>> scenarios = controller.scenarios();

        assertThat(scenarios).extracting(s -> s.get("name"))
                .containsExactly("network-dns", "app-deploy-regression", "ambiguous-slow", "firewall-block");
        assertThat(scenarios).allSatisfy(s -> assertThat(s.get("description")).isNotBlank());
    }

    @Test
    void everyListedScenarioHasClasspathFixture() {
        ResourceLoader resourceLoader = new DefaultResourceLoader();
        IncidentController controller = new IncidentController(mock(SkillTemplate.class), resourceLoader);

        assertThat(controller.scenarios()).isNotEmpty().allSatisfy(scenario -> {
            String name = scenario.get("name");
            Resource fixture = resourceLoader.getResource("classpath:/fixtures/incidents/" + name + ".txt");
            assertThat(fixture.exists())
                    .as("fixture for listed scenario '%s'", name)
                    .isTrue();
        });
    }

    @Test
    void handleScenarioRejectsUnknownName() {
        IncidentController controller = new IncidentController(mock(SkillTemplate.class), new DefaultResourceLoader());

        assertThatThrownBy(() -> controller.handleScenario("not-a-real-scenario"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unknown scenario");
    }
}
