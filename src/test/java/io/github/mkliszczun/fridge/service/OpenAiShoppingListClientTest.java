package io.github.mkliszczun.fridge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.mkliszczun.fridge.config.OpenAiProperties;
import io.github.mkliszczun.fridge.enums.Unit;
import io.github.mkliszczun.fridge.exception.AiServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiShoppingListClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void match_sendsCandidatesAndParsesMatchedIds() throws Exception {
        OpenAiProperties properties = properties("test-key");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiShoppingListClient client = new OpenAiShoppingListClient(
                builder, properties, objectMapper);
        UUID ingredientId = UUID.randomUUID();
        UUID fridgeItemId = UUID.randomUUID();
        LocalDate expirationDate = LocalDate.now().plusDays(2);
        String response = objectMapper.writeValueAsString(Map.of(
                "output_text", objectMapper.writeValueAsString(Map.of(
                        "matches", List.of(Map.of(
                                "plannedMealIngredientId", ingredientId,
                                "fridgeItemIds", List.of(fridgeItemId)
                        ))
                ))
        ));

        server.expect(once(), requestTo("https://api.openai.com/v1/responses"))
                .andExpect(method(POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
                .andExpect(content().string(containsString("\"type\":\"json_schema\"")))
                .andExpect(content().string(containsString(ingredientId.toString())))
                .andExpect(content().string(containsString(fridgeItemId.toString())))
                .andExpect(content().string(containsString("Makaron pełnoziarnisty")))
                .andExpect(content().string(containsString(expirationDate.toString())))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        List<ShoppingListIngredientMatch> result = client.match(
                List.of(new ShoppingListIngredientCandidate(
                        ingredientId, "Makaron", new BigDecimal("200"), "GRAM")),
                List.of(new ShoppingListFridgeItemCandidate(
                        fridgeItemId,
                        "Makaron pełnoziarnisty",
                        new BigDecimal("150"),
                        Unit.GRAM,
                        expirationDate))
        );

        assertThat(result).containsExactly(
                new ShoppingListIngredientMatch(ingredientId, List.of(fridgeItemId)));
        server.verify();
    }

    @Test
    void match_withoutApiKeyFailsBeforeCallingProvider() {
        OpenAiShoppingListClient client = new OpenAiShoppingListClient(
                RestClient.builder(), properties(""), objectMapper);

        assertThatThrownBy(() -> client.match(List.of(), List.of()))
                .isInstanceOf(AiServiceUnavailableException.class)
                .hasMessage("AI service is not configured");
    }

    private OpenAiProperties properties(String apiKey) {
        OpenAiProperties properties = new OpenAiProperties();
        properties.setApiKey(apiKey);
        return properties;
    }
}
