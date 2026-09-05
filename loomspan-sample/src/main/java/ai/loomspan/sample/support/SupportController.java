package ai.loomspan.sample.support;

import ai.loomspan.api.SkillExecutionView;
import ai.loomspan.api.SkillTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/support")
public class SupportController {

    private static final Logger log = LoggerFactory.getLogger(SupportController.class);

    private static final List<Map<String, String>> SCENARIOS = List.of(
            Map.of(
                    "name", "billing-duplicate-charge",
                    "description", "Charged twice for March Pro plan (billing → invoices / refund)"),
            Map.of(
                    "name", "tech-crash-on-checkout",
                    "description", "App crashes on pay step (technical → known issues / bug ticket)"),
            Map.of(
                    "name", "mixed-billing-and-crash",
                    "description", "Duplicate charge and checkout crash (billing + technical)"),
            Map.of(
                    "name", "how-to-export",
                    "description", "How do I export CSV? (how-to → help center; no refund)"),
            Map.of(
                    "name", "angry-goodwill",
                    "description", "Small overcharge, first complaint, furious tone (policy judgment)"));

    /** Static enrichment for resolve-scenario demos. */
    private static final Map<String, String> CUSTOMER_IDS = Map.of(
            "billing-duplicate-charge", "CUST-1001",
            "tech-crash-on-checkout", "CUST-1002",
            "mixed-billing-and-crash", "CUST-1003",
            "how-to-export", "CUST-1004",
            "angry-goodwill", "CUST-1005");

    private final SkillTemplate skillTemplate;
    private final ResourceLoader resourceLoader;

    public SupportController(SkillTemplate skillTemplate, ResourceLoader resourceLoader) {
        this.skillTemplate = skillTemplate;
        this.resourceLoader = resourceLoader;
    }

    @PostMapping(value = "/resolve", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> resolve(@RequestBody ResolveSupportRequest request) {
        if (request == null || !StringUtils.hasText(request.emailText())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "emailText is required");
        }
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("emailText", request.emailText());
        if (StringUtils.hasText(request.customerId())) {
            inputs.put("customerId", request.customerId().trim());
        }
        if (StringUtils.hasText(request.scenario())) {
            inputs.put("scenario", request.scenario().trim());
        }
        return invokeResolveSupportCase(inputs);
    }

    @GetMapping(value = "/scenarios", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Map<String, String>> scenarios() {
        return SCENARIOS;
    }

    @GetMapping(value = "/resolve-scenario", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> resolveScenario(@RequestParam("name") String name) {
        String scenario = requireKnownScenario(name);
        String emailText = loadFixture(scenario);
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("emailText", emailText);
        inputs.put("scenario", scenario);
        String customerId = CUSTOMER_IDS.get(scenario);
        if (StringUtils.hasText(customerId)) {
            inputs.put("customerId", customerId);
        }
        return invokeResolveSupportCase(inputs);
    }

    private Map<String, Object> invokeResolveSupportCase(Map<String, Object> inputs) {
        long startedAtNanos = System.nanoTime();
        ViewHolder holder = new ViewHolder();
        String result = skillTemplate.invoke("resolveSupportCase", inputs, holder::set);
        log.info("Completed resolveSupportCase sessionId={} scenario={} elapsedMs={}",
                holder.view == null ? "unknown" : holder.view.sessionId(),
                inputs.getOrDefault("scenario", ""),
                elapsedMillis(startedAtNanos));
        return buildExecutionResponse(result, holder.view);
    }

    private String requireKnownScenario(String name) {
        if (!StringUtils.hasText(name)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scenario name is required");
        }
        String trimmed = name.trim();
        boolean known = SCENARIOS.stream().anyMatch(s -> s.get("name").equals(trimmed));
        if (!known) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown scenario '" + trimmed + "'. Use GET /support/scenarios for valid keys.");
        }
        return trimmed;
    }

    private String loadFixture(String scenario) {
        String location = "classpath:/fixtures/support/" + scenario + ".txt";
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Fixture not found for scenario '" + scenario + "'");
        }
        try (InputStreamReader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            return FileCopyUtils.copyToString(reader);
        }
        catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to load fixture for scenario '" + scenario + "'", ex);
        }
    }

    private long elapsedMillis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }

    private Map<String, Object> buildExecutionResponse(String result, SkillExecutionView executionView) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("result", result);
        if (executionView != null) {
            response.put("sessionId", executionView.sessionId());
            response.put("executionEvents", executionView.events());
        }
        return response;
    }

    public record ResolveSupportRequest(String emailText, String customerId, String scenario) {
    }

    private static final class ViewHolder {
        private SkillExecutionView view;

        private void set(SkillExecutionView view) {
            this.view = view;
        }
    }
}
