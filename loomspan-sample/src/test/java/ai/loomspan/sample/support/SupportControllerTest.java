package ai.loomspan.sample.support;

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

class SupportControllerTest {

    @Test
    void resolveDelegatesWithEmailScenarioAndCustomerId() {
        SkillTemplate skillTemplate = mock(SkillTemplate.class);
        SupportController controller = new SupportController(skillTemplate, new DefaultResourceLoader());
        List<SkillExecutionEvent> events = List.of();
        doAnswer(invocation -> {
            Consumer<SkillExecutionView> observer = invocation.getArgument(2);
            observer.accept(new SkillExecutionView("support-session", events));
            return "{\"disposition\":\"refund_offered\"}";
        }).when(skillTemplate).invoke(eq("resolveSupportCase"), any(Map.class), any());

        Map<String, Object> response = controller.resolve(
                new SupportController.ResolveSupportRequest(
                        "Charged twice", "CUST-1001", "billing-duplicate-charge"));

        ArgumentCaptor<Map<String, Object>> inputCaptor = ArgumentCaptor.forClass(Map.class);
        verify(skillTemplate).invoke(eq("resolveSupportCase"), inputCaptor.capture(), any());
        assertThat(inputCaptor.getValue())
                .containsEntry("emailText", "Charged twice")
                .containsEntry("customerId", "CUST-1001")
                .containsEntry("scenario", "billing-duplicate-charge")
                .doesNotContainValue(null);
        assertThat(response.get("result")).isEqualTo("{\"disposition\":\"refund_offered\"}");
        assertThat(response.get("sessionId")).isEqualTo("support-session");
        assertThat(response.get("executionEvents")).isEqualTo(events);
        assertThat(response).doesNotContainKey("filePath");
    }

    @Test
    void resolveOmitsNullOptionalKeys() {
        SkillTemplate skillTemplate = mock(SkillTemplate.class);
        SupportController controller = new SupportController(skillTemplate, new DefaultResourceLoader());
        doAnswer(invocation -> "\"ok\"")
                .when(skillTemplate).invoke(eq("resolveSupportCase"), any(Map.class), any());

        controller.resolve(new SupportController.ResolveSupportRequest("email only", null, null));

        ArgumentCaptor<Map<String, Object>> inputCaptor = ArgumentCaptor.forClass(Map.class);
        verify(skillTemplate).invoke(eq("resolveSupportCase"), inputCaptor.capture(), any());
        assertThat(inputCaptor.getValue())
                .containsEntry("emailText", "email only")
                .doesNotContainKey("customerId")
                .doesNotContainKey("scenario")
                .doesNotContainValue(null);
    }

    @Test
    void resolveScenarioLoadsFixtureSetsScenarioAndEnrichesCustomerId() {
        SkillTemplate skillTemplate = mock(SkillTemplate.class);
        SupportController controller = new SupportController(skillTemplate, new DefaultResourceLoader());
        List<SkillExecutionEvent> events = List.of();
        doAnswer(invocation -> {
            Consumer<SkillExecutionView> observer = invocation.getArgument(2);
            observer.accept(new SkillExecutionView("scenario-session", events));
            return "{\"disposition\":\"how_to_answered\"}";
        }).when(skillTemplate).invoke(eq("resolveSupportCase"), any(Map.class), any());

        Map<String, Object> response = controller.resolveScenario("how-to-export");

        ArgumentCaptor<Map<String, Object>> inputCaptor = ArgumentCaptor.forClass(Map.class);
        verify(skillTemplate).invoke(eq("resolveSupportCase"), inputCaptor.capture(), any());
        Map<String, Object> inputs = inputCaptor.getValue();
        assertThat(inputs.get("scenario")).isEqualTo("how-to-export");
        assertThat(inputs.get("customerId")).isEqualTo("CUST-1004");
        assertThat(inputs.get("emailText").toString())
                .containsIgnoringCase("CSV")
                .containsIgnoringCase("export");
        assertThat(response.get("sessionId")).isEqualTo("scenario-session");
        assertThat(response.get("executionEvents")).isEqualTo(events);
        assertThat(response.get("result")).isEqualTo("{\"disposition\":\"how_to_answered\"}");
    }

    @Test
    void resolveScenarioEnrichesMixedBillingCustomerId() {
        SkillTemplate skillTemplate = mock(SkillTemplate.class);
        SupportController controller = new SupportController(skillTemplate, new DefaultResourceLoader());
        doAnswer(invocation -> "\"ok\"")
                .when(skillTemplate).invoke(eq("resolveSupportCase"), any(Map.class), any());

        controller.resolveScenario("mixed-billing-and-crash");

        ArgumentCaptor<Map<String, Object>> inputCaptor = ArgumentCaptor.forClass(Map.class);
        verify(skillTemplate).invoke(eq("resolveSupportCase"), inputCaptor.capture(), any());
        Map<String, Object> inputs = inputCaptor.getValue();
        assertThat(inputs.get("scenario")).isEqualTo("mixed-billing-and-crash");
        assertThat(inputs.get("customerId")).isEqualTo("CUST-1003");
        assertThat(inputs.get("emailText").toString())
                .containsIgnoringCase("charged twice")
                .containsIgnoringCase("crash");
    }

    @Test
    void scenariosListsFiveKnownKeys() {
        SupportController controller = new SupportController(mock(SkillTemplate.class), new DefaultResourceLoader());

        List<Map<String, String>> scenarios = controller.scenarios();

        assertThat(scenarios).extracting(s -> s.get("name"))
                .containsExactly(
                        "billing-duplicate-charge",
                        "tech-crash-on-checkout",
                        "mixed-billing-and-crash",
                        "how-to-export",
                        "angry-goodwill");
        assertThat(scenarios).allSatisfy(s -> assertThat(s.get("description")).isNotBlank());
    }

    @Test
    void everyListedScenarioHasClasspathFixture() {
        ResourceLoader resourceLoader = new DefaultResourceLoader();
        SupportController controller = new SupportController(mock(SkillTemplate.class), resourceLoader);

        assertThat(controller.scenarios()).isNotEmpty().allSatisfy(scenario -> {
            String name = scenario.get("name");
            Resource fixture = resourceLoader.getResource("classpath:/fixtures/support/" + name + ".txt");
            assertThat(fixture.exists())
                    .as("fixture for listed scenario '%s'", name)
                    .isTrue();
        });
    }

    @Test
    void resolveScenarioRejectsUnknownName() {
        SupportController controller = new SupportController(mock(SkillTemplate.class), new DefaultResourceLoader());

        assertThatThrownBy(() -> controller.resolveScenario("not-a-real-scenario"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unknown scenario");
    }

    @Test
    void resolveRejectsMissingEmailText() {
        SupportController controller = new SupportController(mock(SkillTemplate.class), new DefaultResourceLoader());

        assertThatThrownBy(() -> controller.resolve(
                new SupportController.ResolveSupportRequest(null, "CUST-1", "billing-duplicate-charge")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("emailText is required");

        assertThatThrownBy(() -> controller.resolve(
                new SupportController.ResolveSupportRequest("  ", null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("emailText is required");
    }
}
