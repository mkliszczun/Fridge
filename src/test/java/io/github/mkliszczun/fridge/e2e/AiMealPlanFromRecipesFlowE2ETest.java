package io.github.mkliszczun.fridge.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mkliszczun.fridge.repository.PlannedMealRepository;
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
class AiMealPlanFromRecipesFlowE2ETest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlannedMealRepository plannedMealRepository;

    @MockitoBean
    private OpenAiMealPlanClient openAiClient;

    @Test
    void memberCanGeneratePlanFromOwnedRecipesWithoutSavingPlannedMeals() throws Exception {
        String ownerToken = register();
        String outsiderToken = register();
        UUID fridgeId = createFridge(ownerToken);
        UUID firstRecipeId = createRecipe(ownerToken, "Zupa pomidorowa");
        UUID secondRecipeId = createRecipe(ownerToken, "Makaron z pesto");
        LocalDate startDate = LocalDate.now().plusDays(1);
        when(openAiClient.generate(any(), any()))
                .thenReturn(List.of(firstRecipeId, secondRecipeId));

        mvc.perform(post(
                        "/api/fridges/{fridgeId}/ai/meal-plans/generate-from-recipes",
                        fridgeId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "startDate", startDate.toString(),
                                "days", 2,
                                "servings", 3,
                                "guidelines", "Lekkie obiady"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fridgeId").value(fridgeId.toString()))
                .andExpect(jsonPath("$.startDate").value(startDate.toString()))
                .andExpect(jsonPath("$.days").value(2))
                .andExpect(jsonPath("$.meals[0].plannedDate").value(startDate.toString()))
                .andExpect(jsonPath("$.meals[0].servings").value(3))
                .andExpect(jsonPath("$.meals[0].recipeId").value(firstRecipeId.toString()))
                .andExpect(jsonPath("$.meals[0].recipeName").value("Zupa pomidorowa"))
                .andExpect(jsonPath("$.meals[1].plannedDate")
                        .value(startDate.plusDays(1).toString()))
                .andExpect(jsonPath("$.meals[1].recipeId").value(secondRecipeId.toString()));

        assertThat(plannedMealRepository.count()).isZero();

        mvc.perform(post(
                        "/api/fridges/{fridgeId}/ai/meal-plans/generate-from-recipes",
                        fridgeId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "startDate", startDate.toString(),
                                "days", 11,
                                "servings", 3
                        ))))
                .andExpect(status().isBadRequest());

        mvc.perform(post(
                        "/api/fridges/{fridgeId}/ai/meal-plans/generate-from-recipes",
                        fridgeId)
                        .header("Authorization", "Bearer " + outsiderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "startDate", startDate.toString(),
                                "days", 2,
                                "servings", 3
                        ))))
                .andExpect(status().isForbidden());

        verify(openAiClient, times(1)).generate(any(), any());
    }

    private String register() throws Exception {
        String login = "ai-plan+" + UUID.randomUUID() + "@test.local";
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

    private UUID createRecipe(String token, String name) throws Exception {
        Map<String, Object> ingredient = Map.of(
                "name", "Składnik",
                "amount", 1,
                "unit", "szt.",
                "optional", false
        );
        var result = mvc.perform(post("/api/recipes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", name,
                                "description", "Opis " + name,
                                "instructions", "Przygotuj posiłek.",
                                "servings", 2,
                                "ingredients", List.of(ingredient)
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(read(result.getResponse().getContentAsString()).get("id").asText());
    }

    private JsonNode read(String json) throws Exception {
        return objectMapper.readTree(json);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
