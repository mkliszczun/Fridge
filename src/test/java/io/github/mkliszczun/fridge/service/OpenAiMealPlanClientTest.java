package io.github.mkliszczun.fridge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mkliszczun.fridge.config.OpenAiProperties;
import io.github.mkliszczun.fridge.dto.AiMealPlanGenerateRequest;
import io.github.mkliszczun.fridge.dto.RecipeIngredientRequest;
import io.github.mkliszczun.fridge.dto.RecipeRequest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

class OpenAiMealPlanClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void generate_sendsStructuredOutputRequestAndParsesRecipe() throws Exception {
        OpenAiProperties properties = properties("test-key");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiMealPlanClient client = new OpenAiMealPlanClient(builder, properties, objectMapper);
        RecipeRequest expectedRecipe = recipe();
        String response = objectMapper.writeValueAsString(Map.of(
                "output_text", objectMapper.writeValueAsString(expectedRecipe)
        ));

        server.expect(once(), requestTo("https://api.openai.com/v1/responses"))
                .andExpect(method(POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
                .andExpect(content().string(containsString("\"model\":\"gpt-5.6-luna\"")))
                .andExpect(content().string(containsString("\"type\":\"json_schema\"")))
                .andExpect(content().string(containsString("Brak dodatkowych preferencji")))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        RecipeRequest result = client.generate(request());

        assertThat(result).isEqualTo(expectedRecipe);
        server.verify();
    }

    @Test
    void generate_withoutApiKeyFailsBeforeCallingProvider() {
        OpenAiProperties properties = properties("");
        OpenAiMealPlanClient client = new OpenAiMealPlanClient(
                RestClient.builder(), properties, objectMapper);

        assertThatThrownBy(() -> client.generate(request()))
                .isInstanceOf(AiServiceUnavailableException.class)
                .hasMessage("AI service is not configured");
    }

    @Test
    void generate_includesPreviousProposalAndAdditionalFeedback() throws Exception {
        OpenAiProperties properties = properties("test-key");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiMealPlanClient client = new OpenAiMealPlanClient(builder, properties, objectMapper);
        RecipeRequest previousProposal = recipe();
        AiMealPlanGenerateRequest request = new AiMealPlanGenerateRequest(
                LocalDate.now().plusDays(1),
                2,
                "Szybki obiad",
                previousProposal,
                "Nie lubię pomidorów, zrób coś lżejszego"
        );
        String response = objectMapper.writeValueAsString(Map.of(
                "output_text", objectMapper.writeValueAsString(previousProposal)
        ));

        server.expect(once(), requestTo("https://api.openai.com/v1/responses"))
                .andExpect(content().string(containsString("Zaproponuj inne danie")))
                .andExpect(content().string(containsString("Makaron z pesto")))
                .andExpect(content().string(containsString("Nie lubię pomidorów")))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        client.generate(request);

        server.verify();
    }

    private OpenAiProperties properties(String apiKey) {
        OpenAiProperties properties = new OpenAiProperties();
        properties.setApiKey(apiKey);
        return properties;
    }

    private AiMealPlanGenerateRequest request() {
        return new AiMealPlanGenerateRequest(
                LocalDate.now().plusDays(1),
                2,
                null,
                null,
                null
        );
    }

    private RecipeRequest recipe() {
        return new RecipeRequest(
                "Makaron z pesto",
                "Szybki obiad",
                "Ugotuj makaron i wymieszaj z pesto.",
                2,
                List.of(new RecipeIngredientRequest(
                        "Makaron",
                        new BigDecimal("200"),
                        "g",
                        false,
                        null
                ))
        );
    }
}
