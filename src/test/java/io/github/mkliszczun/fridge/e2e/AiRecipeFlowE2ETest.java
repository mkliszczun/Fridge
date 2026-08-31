package io.github.mkliszczun.fridge.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mkliszczun.fridge.dto.AiRecipeGenerateRequest;
import io.github.mkliszczun.fridge.dto.RecipeIngredientRequest;
import io.github.mkliszczun.fridge.dto.RecipeRequest;
import io.github.mkliszczun.fridge.repository.RecipeRepository;
import io.github.mkliszczun.fridge.service.OpenAiRecipeClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = Replace.ANY)
@DirtiesContext
@ActiveProfiles("test")
class AiRecipeFlowE2ETest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecipeRepository recipeRepository;

    @MockitoBean
    private OpenAiRecipeClient openAiClient;

    @Test
    void authenticatedUserCanGenerateRecipeWithGuidelinesWithoutSavingIt() throws Exception {
        String token = register();
        when(openAiClient.generate(
                any(),
                nullable(RecipeRequest.class),
                nullable(String.class)
        )).thenReturn(recipe());

        mvc.perform(post("/api/ai/recipes/generate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "servings", 2,
                                "guidelines", "Bez mięsa, szybkie, dużo białka"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Makaron z pesto"))
                .andExpect(jsonPath("$.servings").value(2))
                .andExpect(jsonPath("$.ingredients[0].name").value("Makaron"));

        assertThat(recipeRepository.count()).isZero();
        ArgumentCaptor<AiRecipeGenerateRequest> requestCaptor =
                ArgumentCaptor.forClass(AiRecipeGenerateRequest.class);
        verify(openAiClient).generate(
                requestCaptor.capture(),
                nullable(RecipeRequest.class),
                nullable(String.class)
        );
        assertThat(requestCaptor.getValue().guidelines())
                .isEqualTo("Bez mięsa, szybkie, dużo białka");

        mvc.perform(post("/api/ai/recipes/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("servings", 2))))
                .andExpect(status().isUnauthorized());

        verify(openAiClient, times(1)).generate(
                any(),
                nullable(RecipeRequest.class),
                nullable(String.class)
        );
    }

    private String register() throws Exception {
        String login = "ai-recipe+" + UUID.randomUUID() + "@test.local";
        var result = mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("login", login, "password", "Secret123!"))))
                .andExpect(status().isCreated())
                .andReturn();
        return read(result.getResponse().getContentAsString()).get("token").asText();
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

    private JsonNode read(String json) throws Exception {
        return objectMapper.readTree(json);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
