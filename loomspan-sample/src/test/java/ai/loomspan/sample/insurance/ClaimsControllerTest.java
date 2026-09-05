package ai.loomspan.sample.insurance;

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

class ClaimsControllerTest {

    @Test
    void processDelegatesToProcessClaimWithClaimTextAndOptionalFields() {
        SkillTemplate skillTemplate = mock(SkillTemplate.class);
        ClaimsController controller = new ClaimsController(skillTemplate, new DefaultResourceLoader());
        List<SkillExecutionEvent> events = List.of();
        doAnswer(invocation -> {
            Consumer<SkillExecutionView> observer = invocation.getArgument(2);
            observer.accept(new SkillExecutionView("claim-session", events));
            return "{\"disposition\":\"pay\"}";
        }).when(skillTemplate).invoke(eq("processClaim"), any(Map.class), any());

        Map<String, Object> response = controller.process(
                new ClaimsController.ProcessClaimRequest(
                        "Minor bumper damage", "POL-AUTO-1001", 2200.0, "clear-auto-pay"));

        ArgumentCaptor<Map<String, Object>> inputCaptor = ArgumentCaptor.forClass(Map.class);
        verify(skillTemplate).invoke(eq("processClaim"), inputCaptor.capture(), any());
        assertThat(inputCaptor.getValue())
                .containsEntry("claimText", "Minor bumper damage")
                .containsEntry("policyId", "POL-AUTO-1001")
                .containsEntry("claimedAmount", 2200.0)
                .containsEntry("scenario", "clear-auto-pay")
                .doesNotContainValue(null);
        assertThat(response.get("result")).isEqualTo("{\"disposition\":\"pay\"}");
        assertThat(response.get("sessionId")).isEqualTo("claim-session");
        assertThat(response.get("executionEvents")).isEqualTo(events);
    }

    @Test
    void processOmitsOptionalFieldsWhenNotProvided() {
        SkillTemplate skillTemplate = mock(SkillTemplate.class);
        ClaimsController controller = new ClaimsController(skillTemplate, new DefaultResourceLoader());
        doAnswer(invocation -> "\"ok\"")
                .when(skillTemplate).invoke(eq("processClaim"), any(Map.class), any());

        controller.process(new ClaimsController.ProcessClaimRequest("claim only", null, null, null));

        ArgumentCaptor<Map<String, Object>> inputCaptor = ArgumentCaptor.forClass(Map.class);
        verify(skillTemplate).invoke(eq("processClaim"), inputCaptor.capture(), any());
        assertThat(inputCaptor.getValue())
                .containsEntry("claimText", "claim only")
                .doesNotContainKey("policyId")
                .doesNotContainKey("claimedAmount")
                .doesNotContainKey("scenario")
                .doesNotContainValue(null);
    }

    @Test
    void processScenarioLoadsFixtureSetsScenarioAndEnrichesClearAutoPay() {
        SkillTemplate skillTemplate = mock(SkillTemplate.class);
        ClaimsController controller = new ClaimsController(skillTemplate, new DefaultResourceLoader());
        List<SkillExecutionEvent> events = List.of();
        doAnswer(invocation -> {
            Consumer<SkillExecutionView> observer = invocation.getArgument(2);
            observer.accept(new SkillExecutionView("scenario-session", events));
            return "{\"disposition\":\"partial_pay\"}";
        }).when(skillTemplate).invoke(eq("processClaim"), any(Map.class), any());

        Map<String, Object> response = controller.processScenario("clear-auto-pay");

        ArgumentCaptor<Map<String, Object>> inputCaptor = ArgumentCaptor.forClass(Map.class);
        verify(skillTemplate).invoke(eq("processClaim"), inputCaptor.capture(), any());
        Map<String, Object> inputs = inputCaptor.getValue();
        assertThat(inputs.get("scenario")).isEqualTo("clear-auto-pay");
        assertThat(inputs.get("policyId")).isEqualTo("POL-AUTO-1001");
        assertThat(inputs.get("claimedAmount")).isEqualTo(2200.0);
        assertThat(inputs.get("claimText").toString())
                .containsIgnoringCase("bumper")
                .contains("POL-AUTO-1001");
        assertThat(response.get("sessionId")).isEqualTo("scenario-session");
        assertThat(response.get("executionEvents")).isEqualTo(events);
        assertThat(response.get("result")).isEqualTo("{\"disposition\":\"partial_pay\"}");
    }

    @Test
    void processScenarioEnrichesOverLimitWithHighClaimedAmount() {
        SkillTemplate skillTemplate = mock(SkillTemplate.class);
        ClaimsController controller = new ClaimsController(skillTemplate, new DefaultResourceLoader());
        doAnswer(invocation -> "\"ok\"")
                .when(skillTemplate).invoke(eq("processClaim"), any(Map.class), any());

        controller.processScenario("over-limit");

        ArgumentCaptor<Map<String, Object>> inputCaptor = ArgumentCaptor.forClass(Map.class);
        verify(skillTemplate).invoke(eq("processClaim"), inputCaptor.capture(), any());
        Map<String, Object> inputs = inputCaptor.getValue();
        assertThat(inputs.get("scenario")).isEqualTo("over-limit");
        assertThat(inputs.get("policyId")).isEqualTo("POL-AUTO-1001");
        assertThat(inputs.get("claimedAmount")).isEqualTo(25000.0);
        assertThat(inputs.get("claimText").toString()).containsIgnoringCase("total");
    }

    @Test
    void processScenarioOmitsClaimedAmountForAmbiguousWhenNotInTable() {
        SkillTemplate skillTemplate = mock(SkillTemplate.class);
        ClaimsController controller = new ClaimsController(skillTemplate, new DefaultResourceLoader());
        doAnswer(invocation -> "\"ok\"")
                .when(skillTemplate).invoke(eq("processClaim"), any(Map.class), any());

        controller.processScenario("ambiguous-liability");

        ArgumentCaptor<Map<String, Object>> inputCaptor = ArgumentCaptor.forClass(Map.class);
        verify(skillTemplate).invoke(eq("processClaim"), inputCaptor.capture(), any());
        Map<String, Object> inputs = inputCaptor.getValue();
        assertThat(inputs.get("scenario")).isEqualTo("ambiguous-liability");
        assertThat(inputs.get("policyId")).isEqualTo("POL-AUTO-1001");
        assertThat(inputs).doesNotContainKey("claimedAmount");
    }

    @Test
    void scenariosListsFiveKnownKeys() {
        ClaimsController controller = new ClaimsController(mock(SkillTemplate.class), new DefaultResourceLoader());

        List<Map<String, String>> scenarios = controller.scenarios();

        assertThat(scenarios).extracting(s -> s.get("name"))
                .containsExactly(
                        "clear-auto-pay",
                        "exclusion-flood",
                        "fraud-velocity",
                        "ambiguous-liability",
                        "over-limit");
        assertThat(scenarios).allSatisfy(s -> assertThat(s.get("description")).isNotBlank());
    }

    @Test
    void everyListedScenarioHasClasspathFixture() {
        ResourceLoader resourceLoader = new DefaultResourceLoader();
        ClaimsController controller = new ClaimsController(mock(SkillTemplate.class), resourceLoader);

        assertThat(controller.scenarios()).isNotEmpty().allSatisfy(scenario -> {
            String name = scenario.get("name");
            Resource fixture = resourceLoader.getResource(
                    "classpath:/fixtures/insurance/claims/" + name + ".txt");
            assertThat(fixture.exists())
                    .as("fixture for listed scenario '%s'", name)
                    .isTrue();
        });
    }

    @Test
    void processScenarioRejectsUnknownName() {
        ClaimsController controller = new ClaimsController(mock(SkillTemplate.class), new DefaultResourceLoader());

        assertThatThrownBy(() -> controller.processScenario("not-a-real-scenario"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unknown scenario");
    }

    @Test
    void processRejectsMissingClaimText() {
        ClaimsController controller = new ClaimsController(mock(SkillTemplate.class), new DefaultResourceLoader());

        assertThatThrownBy(() -> controller.process(
                new ClaimsController.ProcessClaimRequest("  ", null, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("claimText is required");
    }
}
