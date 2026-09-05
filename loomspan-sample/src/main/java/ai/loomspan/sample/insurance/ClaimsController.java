package ai.loomspan.sample.insurance;

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
@RequestMapping("/claims")
public class ClaimsController {

    private static final Logger log = LoggerFactory.getLogger(ClaimsController.class);

    private static final List<Map<String, String>> SCENARIOS = List.of(
            Map.of(
                    "name", "clear-auto-pay",
                    "description", "Minor covered collision, clean history (pay / partial after deductible)"),
            Map.of(
                    "name", "exclusion-flood",
                    "description", "Water damage where flood is excluded (deny)"),
            Map.of(
                    "name", "fraud-velocity",
                    "description", "Third similar claim in 60 days, high anomaly (refer_siu)"),
            Map.of(
                    "name", "ambiguous-liability",
                    "description", "Unclear fault, missing date (refer_human)"),
            Map.of(
                    "name", "over-limit",
                    "description", "Claimed amount above policy limit (partial_pay at formula)"));

    /** Static enrichment for process-scenario demos (must match Java leaf story data). */
    private static final Map<String, ScenarioEnrichment> ENRICHMENT = Map.of(
            "clear-auto-pay", new ScenarioEnrichment("POL-AUTO-1001", 2200.0),
            "exclusion-flood", new ScenarioEnrichment("POL-HOME-2002", 15000.0),
            "fraud-velocity", new ScenarioEnrichment("POL-AUTO-1001", 4800.0),
            "ambiguous-liability", new ScenarioEnrichment("POL-AUTO-1001", null),
            "over-limit", new ScenarioEnrichment("POL-AUTO-1001", 25000.0));

    private final SkillTemplate skillTemplate;
    private final ResourceLoader resourceLoader;

    public ClaimsController(SkillTemplate skillTemplate, ResourceLoader resourceLoader) {
        this.skillTemplate = skillTemplate;
        this.resourceLoader = resourceLoader;
    }

    @PostMapping(value = "/process", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> process(@RequestBody ProcessClaimRequest request) {
        if (request == null || !StringUtils.hasText(request.claimText())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "claimText is required");
        }
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("claimText", request.claimText());
        if (StringUtils.hasText(request.policyId())) {
            inputs.put("policyId", request.policyId().trim());
        }
        if (request.claimedAmount() != null) {
            inputs.put("claimedAmount", request.claimedAmount());
        }
        if (StringUtils.hasText(request.scenario())) {
            inputs.put("scenario", request.scenario().trim());
        }
        return invokeProcessClaim(inputs);
    }

    @GetMapping(value = "/scenarios", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Map<String, String>> scenarios() {
        return SCENARIOS;
    }

    @GetMapping(value = "/process-scenario", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> processScenario(@RequestParam("name") String name) {
        String scenario = requireKnownScenario(name);
        String claimText = loadFixture(scenario);
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("claimText", claimText);
        inputs.put("scenario", scenario);
        ScenarioEnrichment enrichment = ENRICHMENT.get(scenario);
        if (enrichment != null) {
            if (StringUtils.hasText(enrichment.policyId())) {
                inputs.put("policyId", enrichment.policyId());
            }
            if (enrichment.claimedAmount() != null) {
                inputs.put("claimedAmount", enrichment.claimedAmount());
            }
        }
        return invokeProcessClaim(inputs);
    }

    private Map<String, Object> invokeProcessClaim(Map<String, Object> inputs) {
        long startedAtNanos = System.nanoTime();
        ViewHolder holder = new ViewHolder();
        String result = skillTemplate.invoke("processClaim", inputs, holder::set);
        log.info("Completed processClaim sessionId={} scenario={} elapsedMs={}",
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
                    "Unknown scenario '" + trimmed + "'. Use GET /claims/scenarios for valid keys.");
        }
        return trimmed;
    }

    private String loadFixture(String scenario) {
        String location = "classpath:/fixtures/insurance/claims/" + scenario + ".txt";
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

    public record ProcessClaimRequest(
            String claimText,
            String policyId,
            Double claimedAmount,
            String scenario) {
    }

    private record ScenarioEnrichment(String policyId, Double claimedAmount) {
    }

    private static final class ViewHolder {
        private SkillExecutionView view;

        private void set(SkillExecutionView view) {
            this.view = view;
        }
    }
}
