package io.github.mkliszczun.fridge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mkliszczun.fridge.config.OpenAiProperties;
import io.github.mkliszczun.fridge.dto.AiMealPlanFromRecipesGenerateRequest;
import io.github.mkliszczun.fridge.exception.AiServiceUnavailableException;
import io.github.mkliszczun.fridge.exception.InvalidAiResponseException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OpenAiMealPlanClient {

    private static final String INSTRUCTIONS = """
            Jesteś modułem aplikacji Fridge wybierającym przepisy do planu posiłków.
            Zwracaj wyłącznie dane zgodne z przekazanym JSON Schema.
            Wybierz dokładnie tyle przepisów, ile dni wskazał użytkownik.
            Używaj wyłącznie identyfikatorów z przekazanej listy przepisów.
            Kolejność identyfikatorów odpowiada kolejnym dniom planu.
            Nie powtarzaj przepisów, jeżeli dostępna lista pozwala tego uniknąć.
            Uwzględnij wytyczne użytkownika, jeśli zostały podane.
            Nie twórz nowych przepisów i nie modyfikuj przekazanych danych.
            """;

    private static final String MEAL_PLAN_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "recipeIds": {
                  "type": "array",
                  "minItems": 1,
                  "maxItems": 10,
                  "items": {"type": "string", "format": "uuid"}
                }
              },
              "required": ["recipeIds"],
              "additionalProperties": false
            }
            """;

    private final RestClient restClient;
    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;
    private final JsonNode mealPlanSchema;

    public OpenAiMealPlanClient(RestClient.Builder builder,
                                OpenAiProperties properties,
                                ObjectMapper objectMapper) {
        this.restClient = builder.baseUrl(properties.getBaseUrl()).build();
        this.properties = properties;
        this.objectMapper = objectMapper;
        try {
            this.mealPlanSchema = objectMapper.readTree(MEAL_PLAN_SCHEMA);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Invalid built-in meal plan schema", ex);
        }
    }

    public List<UUID> generate(AiMealPlanFromRecipesGenerateRequest request,
                               List<MealPlanRecipeCandidate> candidates) {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new AiServiceUnavailableException("AI service is not configured");
        }

        try {
            JsonNode response = restClient.post()
                    .uri("/responses")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(createPayload(request, candidates))
                    .retrieve()
                    .body(JsonNode.class);
            return parseRecipeIds(response);
        } catch (RestClientResponseException | ResourceAccessException ex) {
            throw new AiServiceUnavailableException("AI service request failed");
        }
    }

    private Map<String, Object> createPayload(AiMealPlanFromRecipesGenerateRequest request,
                                              List<MealPlanRecipeCandidate> candidates) {
        Map<String, Object> format = new LinkedHashMap<>();
        format.put("type", "json_schema");
        format.put("name", "meal_plan_from_recipes");
        format.put("strict", true);
        format.put("schema", mealPlanSchema);

        Map<String, Object> text = new LinkedHashMap<>();
        text.put("format", format);
        text.put("verbosity", "low");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", properties.getModel());
        payload.put("instructions", INSTRUCTIONS);
        payload.put("input", createInput(request, candidates));
        payload.put("reasoning", Map.of("effort", "low"));
        payload.put("text", text);
        payload.put("max_output_tokens", 2000);
        payload.put("store", false);
        return payload;
    }

    private String createInput(AiMealPlanFromRecipesGenerateRequest request,
                               List<MealPlanRecipeCandidate> candidates) {
        StringBuilder input = new StringBuilder()
                .append("Zaplanuj po jednym posiłku na ")
                .append(request.days())
                .append(" dni, dla ")
                .append(request.servings())
                .append(" porcji dziennie, od ")
                .append(request.startDate())
                .append(".\n");

        if (StringUtils.hasText(request.guidelines())) {
            input.append("Wytyczne użytkownika: ")
                    .append(request.guidelines().trim())
                    .append("\n");
        } else {
            input.append("Brak dodatkowych wytycznych.\n");
        }
        input.append("Dostępne przepisy: ").append(toJson(candidates));
        return input.toString();
    }

    private String toJson(List<MealPlanRecipeCandidate> candidates) {
        try {
            return objectMapper.writeValueAsString(candidates);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Recipe candidates cannot be serialized", ex);
        }
    }

    private List<UUID> parseRecipeIds(JsonNode response) {
        String outputText = extractOutputText(response);
        if (!StringUtils.hasText(outputText)) {
            throw new InvalidAiResponseException("AI did not return a meal plan");
        }
        try {
            JsonNode recipeIds = objectMapper.readTree(outputText).path("recipeIds");
            if (!recipeIds.isArray()) {
                throw new InvalidAiResponseException("AI returned an invalid meal plan");
            }
            return recipeIds.valueStream()
                    .map(JsonNode::asText)
                    .map(UUID::fromString)
                    .toList();
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new InvalidAiResponseException("AI returned an invalid meal plan", ex);
        }
    }

    private String extractOutputText(JsonNode response) {
        if (response == null) {
            return null;
        }
        JsonNode directOutput = response.get("output_text");
        if (directOutput != null && directOutput.isTextual()) {
            return directOutput.asText();
        }
        for (JsonNode output : response.path("output")) {
            for (JsonNode content : output.path("content")) {
                if ("output_text".equals(content.path("type").asText())
                        && content.path("text").isTextual()) {
                    return content.path("text").asText();
                }
            }
        }
        return null;
    }
}
