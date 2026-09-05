package ai.loomspan.sample.travel;

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
@RequestMapping("/travel")
public class TravelController {

    private static final Logger log = LoggerFactory.getLogger(TravelController.class);

    private static final List<Map<String, String>> SCENARIOS = List.of(
            Map.of(
                    "name", "budget-nyc-weekend",
                    "description", "NYC weekend ~$400 all-in; OK with trains (cost-first)"),
            Map.of(
                    "name", "loyalty-points-max",
                    "description", "Prefer Marriott gold-tier perks even if pricier"),
            Map.of(
                    "name", "fastest-sfo-sea",
                    "description", "SFO→SEA morning meeting; minimize travel time"),
            Map.of(
                    "name", "underspecified",
                    "description", "Somewhere warm in March — missing origin/dates/budget"));

    private final SkillTemplate skillTemplate;
    private final ResourceLoader resourceLoader;

    public TravelController(SkillTemplate skillTemplate, ResourceLoader resourceLoader) {
        this.skillTemplate = skillTemplate;
        this.resourceLoader = resourceLoader;
    }

    @PostMapping(value = "/plan", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> plan(@RequestBody PlanTripRequest request) {
        if (request == null || !StringUtils.hasText(request.requestText())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "requestText is required");
        }
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("requestText", request.requestText());
        if (StringUtils.hasText(request.scenario())) {
            inputs.put("scenario", request.scenario().trim());
        }
        return invokePlanTrip(inputs);
    }

    @GetMapping(value = "/scenarios", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Map<String, String>> scenarios() {
        return SCENARIOS;
    }

    @GetMapping(value = "/plan-scenario", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> planScenario(@RequestParam("name") String name) {
        String scenario = requireKnownScenario(name);
        String requestText = loadFixture(scenario);
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("requestText", requestText);
        inputs.put("scenario", scenario);
        return invokePlanTrip(inputs);
    }

    private Map<String, Object> invokePlanTrip(Map<String, Object> inputs) {
        long startedAtNanos = System.nanoTime();
        ViewHolder holder = new ViewHolder();
        String result = skillTemplate.invoke("planTrip", inputs, holder::set);
        log.info("Completed planTrip sessionId={} scenario={} elapsedMs={}",
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
                    "Unknown scenario '" + trimmed + "'. Use GET /travel/scenarios for valid keys.");
        }
        return trimmed;
    }

    private String loadFixture(String scenario) {
        String location = "classpath:/fixtures/travel/" + scenario + ".txt";
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

    public record PlanTripRequest(String requestText, String scenario) {
    }

    private static final class ViewHolder {
        private SkillExecutionView view;

        private void set(SkillExecutionView view) {
            this.view = view;
        }
    }
}
