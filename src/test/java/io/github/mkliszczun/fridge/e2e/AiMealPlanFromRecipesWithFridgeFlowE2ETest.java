package io.github.mkliszczun.fridge.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mkliszczun.fridge.repository.PlannedMealRepository;
import io.github.mkliszczun.fridge.service.OpenAiMealPlanWithFridgeClient;
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
class AiMealPlanFromRecipesWithFridgeFlowE2ETest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlannedMealRepository plannedMealRepository;

    @MockitoBean
    private OpenAiMealPlanWithFridgeClient openAiClient;

    @Test
    void memberCanGeneratePlanUsingFridgeWithoutSavingPlannedMeals() throws Exception {
        String ownerToken = register();
        String outsiderToken = register();
        UUID fridgeId = createFridge(ownerToken);
        UUID recipeId = createRecipe(ownerToken);
        createFridgeItem(ownerToken, fridgeId);
        LocalDate startDate = LocalDate.now().plusDays(1);
        when(openAiClient.generate(any(), any(), any())).thenReturn(List.of(recipeId));

        mvc.perform(post(
                        "/api/fridges/{fridgeId}/ai/meal-plans/generate-from-recipes-with-fridge",
                        fridgeId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "startDate", startDate.toString(),
                                "days", 1,
                                "servings", 3,
                                "guidelines", "Lekkie obiady"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fridgeId").value(fridgeId.toString()))
                .andExpect(jsonPath("$.startDate").value(startDate.toString()))
                .andExpect(jsonPath("$.days").value(1))
                .andExpect(jsonPath("$.meals[0].plannedDate").value(startDate.toString()))
                .andExpect(jsonPath("$.meals[0].servings").value(3))
                .andExpect(jsonPath("$.meals[0].recipeId").value(recipeId.toString()))
                .andExpect(jsonPath("$.meals[0].recipeName").value("Makaron z pesto"));

        assertThat(plannedMealRepository.count()).isZero();

        mvc.perform(post(
                        "/api/fridges/{fridgeId}/ai/meal-plans/generate-from-recipes-with-fridge",
                        fridgeId)
                        .header("Authorization", "Bearer " + outsiderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "startDate", startDate.toString(),
                                "days", 1,
                                "servings", 3
                        ))))
                .andExpect(status().isForbidden());

        mvc.perform(post(
                        "/api/fridges/{fridgeId}/ai/meal-plans/generate-from-recipes-with-fridge",
                        fridgeId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "startDate", startDate.toString(),
                                "days", 11,
                                "servings", 3
                        ))))
                .andExpect(status().isBadRequest());

        verify(openAiClient, times(1)).generate(any(), any(), any());
    }

    private String register() throws Exception {
        String login = "ai-fridge+" + UUID.randomUUID() + "@test.local";
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

    private UUID createRecipe(String token) throws Exception {
        var result = mvc.perform(post("/api/recipes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "Makaron z pesto",
                                "description", "Szybki obiad",
                                "instructions", "Ugotuj makaron.",
                                "servings", 2,
                                "ingredients", List.of(Map.of(
                                        "name", "Makaron",
                                        "amount", 200,
                                        "unit", "g",
                                        "optional", false
                                ))
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(read(result.getResponse().getContentAsString()).get("id").asText());
    }

    private void createFridgeItem(String token, UUID fridgeId) throws Exception {
        mvc.perform(post("/api/fridge-items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "fridgeId", fridgeId,
                                "customName", "Makaron pełnoziarnisty",
                                "amount", 150,
                                "unit", "GRAM",
                                "bestBeforeDate", LocalDate.now().plusDays(2).toString()
                        ))))
                .andExpect(status().isCreated());
    }

    private JsonNode read(String json) throws Exception {
        return objectMapper.readTree(json);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
