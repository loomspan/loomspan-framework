package ai.loomspan.sample;

import ai.loomspan.api.SkillExecutionEvent;
import ai.loomspan.api.SkillExecutionView;
import ai.loomspan.api.SkillTemplate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

import java.util.Map;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SampleControllerTest {

    @Test
    void sampleControllerDelegatesExpensesToPublicYamlSkill() {
        SkillTemplate skillTemplate = mock(SkillTemplate.class);
        SampleController controller = new SampleController(skillTemplate, new DefaultResourceLoader());
        org.mockito.Mockito.when(skillTemplate.invoke("expenseLookup", Map.of())).thenReturn("[\"expense\"]");

        assertThat(controller.getExpenses()).isEqualTo("[\"expense\"]");
        verify(skillTemplate).invoke("expenseLookup", Map.of());
    }

    @Test
    void sampleControllerDelegatesWeightTicketSampleToSkillTemplate() {
        SkillTemplate skillTemplate = mock(SkillTemplate.class);
        SampleController controller = new SampleController(skillTemplate, new DefaultResourceLoader());
        List<SkillExecutionEvent> events = List.of();
        doAnswer(invocation -> {
            Consumer<SkillExecutionView> observer = invocation.getArgument(2);
            observer.accept(new SkillExecutionView("weight-ticket-session", events));
            return "{\"ticket_number\":\"59212018\"}";
        }).when(skillTemplate).invoke(eq("weightTicketParser"), any(Map.class), any());

        Map<String, Object> response = (Map<String, Object>) controller.parseSampleWeightTicket();

        verify(skillTemplate).invoke(eq("weightTicketParser"), any(Map.class), any());
        assertThat(response.get("result")).isEqualTo("{\"ticket_number\":\"59212018\"}");
        assertThat(response.get("filePath")).isEqualTo("classpath:/forms/weight_ticket.jpg");
        assertThat(response.get("sessionId")).isEqualTo("weight-ticket-session");
        assertThat(response.get("executionEvents")).isEqualTo(events);
    }

    @Test
    void sampleControllerDelegatesToSkillTemplate() throws Exception {
        SkillTemplate skillTemplate = mock(SkillTemplate.class);
        SampleController controller = new SampleController(skillTemplate, new DefaultResourceLoader());
        List<SkillExecutionEvent> events = List.of();
        doAnswer(invocation -> {
            Consumer<SkillExecutionView> observer = invocation.getArgument(2);
            observer.accept(new SkillExecutionView("session-123", events));
            return "\"ok\"";
        }).when(skillTemplate).invoke(eq("duplicateInvoiceChecker"), any(Map.class), any());

        String fixturePath = new ClassPathResource("fixtures/duplicate-invoice.txt").getFile().getAbsolutePath();
        Map<String, Object> response = (Map<String, Object>) controller.checkDuplicateInvoice(
                fixturePath);

        verify(skillTemplate).invoke(eq("duplicateInvoiceChecker"), any(Map.class), any());
        assertThat(response.get("result")).isEqualTo("\"ok\"");
        assertThat(response.get("sessionId")).isEqualTo("session-123");
        assertThat(response.get("executionEvents")).isEqualTo(events);
    }

    @Test
    void sampleControllerDelegatesPureYamlWeightTicketResourceToSkillTemplate() {
        SkillTemplate skillTemplate = mock(SkillTemplate.class);
        SampleController controller = new SampleController(skillTemplate, new DefaultResourceLoader());
        doAnswer(invocation -> "{\"ticket_number\":\"59212018\"}")
                .when(skillTemplate).invoke(eq("weightTicketParserBySkill"), any(Map.class), any());

        Map<String, Object> response = (Map<String, Object>) controller.parseSampleWeightTicketBySkill();

        ArgumentCaptor<Map<String, Object>> inputCaptor = ArgumentCaptor.forClass(Map.class);
        verify(skillTemplate).invoke(eq("weightTicketParserBySkill"), inputCaptor.capture(), any());
        assertThat(inputCaptor.getValue().get("image")).isInstanceOf(Resource.class);
        assertThat(response.get("result")).isEqualTo("{\"ticket_number\":\"59212018\"}");
        assertThat(response.get("filePath")).isEqualTo("classpath:/forms/weight_ticket.jpg");
    }
}
