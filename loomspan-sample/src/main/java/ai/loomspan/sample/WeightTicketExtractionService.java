package ai.loomspan.sample;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import ai.loomspan.api.SkillMethod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;

@Service
public class WeightTicketExtractionService
{

    private static final String SAMPLE_IMAGE = "classpath:/forms/weight_ticket.jpg";
    private static final Duration OPENAI_REQUEST_TIMEOUT = Duration.ofMinutes(3);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    private static final String PROMPT = """
            Act as a high-precision document parser for certified truck scale tickets.

            CRITICAL GUIDELINES:

            This CAT Scale ticket contains printed labels, dot-matrix values, and a handwritten signature.
            Read each value from its labeled region and preserve identifiers as strings.

            ### EXTRACTION GOALS:
            1. TICKET DETAILS: Extract the ticket number, local weigh date and time, scale number, and complete scale location.
            2. AXLE WEIGHTS: Extract steer, drive, trailer, and certified gross weights in pounds from the right side.

            ### LOGIC & VALIDATION:
            - WEIGHT CHECK: Verify that steer axle + drive axle + trailer axle = gross weight. Use the printed gross value as the source of truth; never alter a legible value merely to make the arithmetic work.
            - DO NOT GUESS: If a field is missing, illegible, or uncertain, return null. Never invent values to satisfy the schema.
            - NORMALIZATION: Return weighed_at_local as yyyy-MM-dd'T'HH:mm (infer 20xx for a two-digit year), weights as integer pounds, and scale_location as the printed facility and address lines joined with commas.
            """;

    private static final String SCHEMA = """
            {
              "name": "weight_ticket",
              "strict": true,
              "schema": {
                "type": "object",
                "properties": {
                  "ticket_number": { "type": ["string", "null"], "description": "Ticket number printed at the upper left. Null if not legible." },
                  "weighed_at_local": { "type": ["string", "null"], "description": "Combined ticket date and time as yyyy-MM-dd'T'HH:mm. Infer 20xx for a two-digit year. Null if not legible." },
                  "scale_number": { "type": ["string", "null"], "description": "Scale identifier beside the SCALE label. Null if not legible." },
                  "scale_location": { "type": ["string", "null"], "description": "Complete printed scale facility and address, with lines joined by commas. Null if not legible." },
                  "steer_axle_weight_lb": { "type": ["integer", "null"], "description": "Steer axle weight in pounds. Null if not legible." },
                  "drive_axle_weight_lb": { "type": ["integer", "null"], "description": "Drive axle weight in pounds. Null if not legible." },
                  "trailer_axle_weight_lb": { "type": ["integer", "null"], "description": "Trailer axle weight in pounds. Null if not legible." },
                  "gross_weight_lb": { "type": ["integer", "null"], "description": "Certified gross weight in pounds. Null if not legible." }
                },
                "required": [
                  "ticket_number", "weighed_at_local", "scale_number", "scale_location",
                  "steer_axle_weight_lb", "drive_axle_weight_lb", "trailer_axle_weight_lb", "gross_weight_lb"
                ],
                "additionalProperties": false
              }
            }
            """;

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public WeightTicketExtractionService(ResourceLoader resourceLoader, ObjectMapper objectMapper,
            @Value("${openai.api.key:${OPENAI_API_KEY:}}") String apiKey,
            @Value("${sample.weight-ticket.extraction.model:gpt-5-mini}") String model)
    {
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
    }

    @SkillMethod(name = "weightTicketParser", description = "Extracts structured CAT Scale ticket fields from the bundled weight_ticket.jpg sample using the OpenAI Responses vision API.")
    public JsonNode extractSampleWeightTicket()
    {
        if (apiKey == null || apiKey.isBlank())
        {
            throw new IllegalStateException("Set openai.api.key or OPENAI_API_KEY before running weight-ticket extraction.");
        }

        try
        {
            Resource image = resourceLoader.getResource(SAMPLE_IMAGE);
            byte[] imageBytes;
            try (InputStream inputStream = image.getInputStream())
            {
                imageBytes = inputStream.readAllBytes();
            }
            String dataUrl = "data:" + imageMimeType(image.getFilename()) + ";base64,"
                    + Base64.getEncoder().encodeToString(imageBytes);

            ObjectNode format = (ObjectNode) objectMapper.readTree(SCHEMA);
            format.put("type", "json_schema");

            ArrayNode content = objectMapper.createArrayNode()
                    .add(objectMapper.createObjectNode()
                            .put("type", "input_text")
                            .put("text", PROMPT))
                    .add(objectMapper.createObjectNode()
                            .put("type", "input_image")
                            .put("image_url", dataUrl));

            ObjectNode userMessage = objectMapper.createObjectNode()
                    .put("role", "user")
                    .set("content", content);

            ObjectNode payload = objectMapper.createObjectNode()
                    .put("model", model)
                    .set("input", objectMapper.createArrayNode().add(userMessage));

            payload.set("text", objectMapper.createObjectNode().set("format", format));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/responses"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(OPENAI_REQUEST_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200)
            {
                throw new IllegalStateException("OpenAI Responses API HTTP " + response.statusCode() + ": " + response.body());
            }

            return objectMapper.readTree(extractOutputText(response.body()));
        }
        catch (InterruptedException ex)
        {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while extracting sample weight ticket.", ex);
        }
        catch (IOException ex)
        {
            throw new IllegalStateException("Failed to extract sample weight ticket.", ex);
        }
    }

    private String extractOutputText(String responseBody) throws IOException
    {
        JsonNode output = objectMapper.readTree(responseBody).path("output");
        if (output.isArray())
        {
            for (JsonNode item : output)
            {
                JsonNode content = item.path("content");
                if (!content.isArray())
                {
                    continue;
                }
                for (JsonNode contentItem : content)
                {
                    if ("output_text".equals(contentItem.path("type").asText()))
                    {
                        return contentItem.path("text").asText();
                    }
                }
            }
        }
        throw new IllegalStateException("No output_text found in Responses API result.");
    }

    private static String imageMimeType(String filename)
    {
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg"))
        {
            return "image/jpeg";
        }
        if (lower.endsWith(".png"))
        {
            return "image/png";
        }
        if (lower.endsWith(".gif"))
        {
            return "image/gif";
        }
        if (lower.endsWith(".webp"))
        {
            return "image/webp";
        }
        throw new IllegalArgumentException("Unsupported image type for OpenAI vision input: " + filename);
    }
}
