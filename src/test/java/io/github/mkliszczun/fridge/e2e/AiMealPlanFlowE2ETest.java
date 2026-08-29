package io.github.mkliszczun.fridge.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mkliszczun.fridge.dto.RecipeIngredientRequest;
import io.github.mkliszczun.fridge.dto.RecipeRequest;
import io.github.mkliszczun.fridge.repository.PlannedMealRepository;
import io.github.mkliszczun.fridge.repository.RecipeRepository;
import io.github.mkliszczun.fridge.service.OpenAiMealPlanClient;
import org.junit.jupiter.api.Test;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
class AiMealPlanFlowE2ETest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecipeRepository recipeRepository;

    @Autowired
    private PlannedMealRepository plannedMealRepository;

    @MockitoBean
    private OpenAiMealPlanClient openAiClient;

    @Test
    void memberCanGenerateProposalWithoutSavingRecipeOrPlannedMeal() throws Exception {
        String ownerToken = register();
        String outsiderToken = register();
        UUID fridgeId = createFridge(ownerToken);
        LocalDate plannedDate = LocalDate.now().plusDays(1);
        when(openAiClient.generate(any())).thenReturn(recipe());

        mvc.perform(post("/api/fridges/{fridgeId}/ai/meal-plans/generate", fridgeId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "plannedDate", plannedDate.toString(),
                                "servings", 2
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fridgeId").value(fridgeId.toString()))
                .andExpect(jsonPath("$.plannedDate").value(plannedDate.toString()))
                .andExpect(jsonPath("$.recipe.name").value("Makaron z pesto"))
                .andExpect(jsonPath("$.recipe.servings").value(2))
                .andExpect(jsonPath("$.recipe.ingredients[0].name").value("Makaron"));

        assertThat(recipeRepository.count()).isZero();
        assertThat(plannedMealRepository.count()).isZero();

        mvc.perform(post("/api/fridges/{fridgeId}/ai/meal-plans/generate", fridgeId)
                        .header("Authorization", "Bearer " + outsiderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "plannedDate", plannedDate.toString(),
                                "servings", 2
                        ))))
                .andExpect(status().isForbidden());

        verify(openAiClient, times(1)).generate(any());
    }

    private String register() throws Exception {
        String login = "ai-meal-plan+" + UUID.randomUUID() + "@test.local";
        var result = mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("login", login, "password", "Secret123!"))))
                .andExpect(status().isCreated())
                .andReturn();
        return read(result.getResponse().getContentAsString()).get("token").asText();
    }

    private UUID createFridge(String token) throws Exception {
        var result = mvc.perform(post("/api/fridges")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Dom"))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(read(result.getResponse().getContentAsString()).get("id").asText());
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
