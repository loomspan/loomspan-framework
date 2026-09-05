package ai.loomspan.sample.incident;

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
@RequestMapping("/incidents")
public class IncidentController {

    private static final Logger log = LoggerFactory.getLogger(IncidentController.class);

    private static final List<Map<String, String>> SCENARIOS = List.of(
            Map.of(
                    "name", "network-dns",
                    "description", "EU users cannot resolve api.example.com (network → DNS)"),
            Map.of(
                    "name", "app-deploy-regression",
                    "description", "Checkout 500s after 14:02 deploy (app → deploys + errors)"),
            Map.of(
                    "name", "ambiguous-slow",
                    "description", "Intermittent slowness, no deploy today (mixed / model judgment)"),
            Map.of(
                    "name", "firewall-block",
                    "description", "Internal wiki blank after firewall change (network → firewall)"));

    private final SkillTemplate skillTemplate;
    private final ResourceLoader resourceLoader;

    public IncidentController(SkillTemplate skillTemplate, ResourceLoader resourceLoader) {
        this.skillTemplate = skillTemplate;
        this.resourceLoader = resourceLoader;
    }

    @PostMapping(value = "/handle", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> handle(@RequestBody HandleIncidentRequest request) {
        if (request == null || !StringUtils.hasText(request.ticketText())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ticketText is required");
        }
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("ticketText", request.ticketText());
        if (StringUtils.hasText(request.scenario())) {
            inputs.put("scenario", request.scenario().trim());
        }
        return invokeHandleIncident(inputs);
    }

    @GetMapping(value = "/scenarios", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Map<String, String>> scenarios() {
        return SCENARIOS;
    }

    @GetMapping(value = "/handle-scenario", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> handleScenario(@RequestParam("name") String name) {
        String scenario = requireKnownScenario(name);
        String ticketText = loadFixture(scenario);
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("ticketText", ticketText);
        inputs.put("scenario", scenario);
        return invokeHandleIncident(inputs);
    }

    private Map<String, Object> invokeHandleIncident(Map<String, Object> inputs) {
        long startedAtNanos = System.nanoTime();
        ViewHolder holder = new ViewHolder();
        String result = skillTemplate.invoke("handleIncident", inputs, holder::set);
        log.info("Completed handleIncident sessionId={} scenario={} elapsedMs={}",
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
                    "Unknown scenario '" + trimmed + "'. Use GET /incidents/scenarios for valid keys.");
        }
        return trimmed;
    }

    private String loadFixture(String scenario) {
        String location = "classpath:/fixtures/incidents/" + scenario + ".txt";
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

    public record HandleIncidentRequest(String ticketText, String scenario) {
    }

    private static final class ViewHolder {
        private SkillExecutionView view;

        private void set(SkillExecutionView view) {
            this.view = view;
        }
    }
}
