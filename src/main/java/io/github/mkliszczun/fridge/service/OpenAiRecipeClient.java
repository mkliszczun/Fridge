package io.github.mkliszczun.fridge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mkliszczun.fridge.config.OpenAiProperties;
import io.github.mkliszczun.fridge.dto.AiRecipeGenerateRequest;
import io.github.mkliszczun.fridge.dto.RecipeRequest;
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
import java.util.Map;

@Service
public class OpenAiRecipeClient {

    private static final String INSTRUCTIONS = """
            Jesteś modułem aplikacji Fridge generującym dokładnie jeden przepis.
            Zwracaj wyłącznie dane zgodne z przekazanym JSON Schema i zawsze pisz po polsku.
            Przepis ma być realistyczny, wykonalny w domu i przeznaczony dokładnie dla wskazanej liczby porcji.
            Nie zakładaj znajomości zawartości lodówki ani produktów użytkownika.
            Ilości składników dotyczą całego przepisu. Używaj krótkich, zrozumiałych jednostek, np. g, ml, szt., łyżka.
            Ilość i jednostka mogą być null tylko wtedy, gdy dokładna ilość nie ma sensu, np. przyprawa do smaku.
            Instrukcje mają być kompletne i zapisane jako jeden czytelny tekst.
            """;

    private static final String RECIPE_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "name": {"type": "string"},
                "description": {"type": ["string", "null"]},
                "instructions": {"type": "string"},
                "servings": {"type": "integer", "minimum": 1},
                "ingredients": {
                  "type": "array",
                  "minItems": 1,
                  "maxItems": 30,
                  "items": {
                    "type": "object",
                    "properties": {
                      "name": {"type": "string"},
                      "amount": {"type": ["number", "null"], "exclusiveMinimum": 0},
                      "unit": {"type": ["string", "null"]},
                      "optional": {"type": "boolean"},
                      "note": {"type": ["string", "null"]}
                    },
                    "required": ["name", "amount", "unit", "optional", "note"],
                    "additionalProperties": false
                  }
                }
              },
              "required": ["name", "description", "instructions", "servings", "ingredients"],
              "additionalProperties": false
            }
            """;

    private final RestClient restClient;
    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;
    private final JsonNode recipeSchema;

    public OpenAiRecipeClient(RestClient.Builder builder,
                              OpenAiProperties properties,
                              ObjectMapper objectMapper) {
        this.restClient = builder.baseUrl(properties.getBaseUrl()).build();
        this.properties = properties;
        this.objectMapper = objectMapper;
        try {
            this.recipeSchema = objectMapper.readTree(RECIPE_SCHEMA);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Invalid built-in recipe schema", ex);
        }
    }

    public RecipeRequest generate(AiRecipeGenerateRequest request,
                                  RecipeRequest previousProposal,
                                  String feedback) {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new AiServiceUnavailableException("AI service is not configured");
        }

        try {
            JsonNode response = restClient.post()
                    .uri("/responses")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(createPayload(request, previousProposal, feedback))
                    .retrieve()
                    .body(JsonNode.class);
            return parseRecipe(response);
        } catch (RestClientResponseException | ResourceAccessException ex) {
            throw new AiServiceUnavailableException("AI service request failed");
        }
    }

    private Map<String, Object> createPayload(AiRecipeGenerateRequest request,
                                              RecipeRequest previousProposal,
                                              String feedback) {
        Map<String, Object> format = new LinkedHashMap<>();
        format.put("type", "json_schema");
        format.put("name", "recipe");
        format.put("strict", true);
        format.put("schema", recipeSchema);

        Map<String, Object> text = new LinkedHashMap<>();
        text.put("format", format);
        text.put("verbosity", "low");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", properties.getModel());
        payload.put("instructions", INSTRUCTIONS);
        payload.put("input", createInput(request, previousProposal, feedback));
        payload.put("reasoning", Map.of("effort", "low"));
        payload.put("text", text);
        payload.put("max_output_tokens", 4000);
        payload.put("store", false);
        return payload;
    }

    private String createInput(AiRecipeGenerateRequest request,
                               RecipeRequest previousProposal,
                               String feedback) {
        StringBuilder input = new StringBuilder()
                .append("Wygeneruj jeden przepis dla ")
                .append(request.servings())
                .append(" porcji.\n");

        if (StringUtils.hasText(request.guidelines())) {
            input.append("Wytyczne użytkownika: ").append(request.guidelines().trim()).append("\n");
        } else {
            input.append("Brak dodatkowych wytycznych. Wybierz popularny i wykonalny przepis.\n");
        }

        if (previousProposal != null) {
            input.append("Zaproponuj inny przepis niż poprzednio. Poprzednia propozycja: ")
                    .append(toJson(previousProposal))
                    .append("\n");
        }
        if (StringUtils.hasText(feedback)) {
            input.append("Dodatkowa uwaga do nowej propozycji: ")
                    .append(feedback.trim())
                    .append("\n");
        }
        return input.toString();
    }

    private String toJson(RecipeRequest recipe) {
        try {
            return objectMapper.writeValueAsString(recipe);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Previous proposal cannot be serialized", ex);
        }
    }

    private RecipeRequest parseRecipe(JsonNode response) {
        String outputText = extractOutputText(response);
        if (!StringUtils.hasText(outputText)) {
            throw new InvalidAiResponseException("AI did not return a recipe");
        }
        try {
            return objectMapper.readValue(outputText, RecipeRequest.class);
        } catch (JsonProcessingException ex) {
            throw new InvalidAiResponseException("AI returned an invalid recipe", ex);
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
