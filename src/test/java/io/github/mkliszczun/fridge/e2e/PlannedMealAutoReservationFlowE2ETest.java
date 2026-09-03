package io.github.mkliszczun.fridge.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mkliszczun.fridge.repository.PlannedMealReservationRepository;
import io.github.mkliszczun.fridge.service.OpenAiShoppingListClient;
import io.github.mkliszczun.fridge.service.ShoppingListIngredientMatch;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = Replace.ANY)
@DirtiesContext
@ActiveProfiles("test")
class PlannedMealAutoReservationFlowE2ETest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlannedMealReservationRepository reservationRepository;

    @MockitoBean
    private OpenAiShoppingListClient openAiClient;

    @Test
    void memberCanReservePartOfItemAndRetryWithoutCreatingDuplicates() throws Exception {
        String ownerToken = register();
        String outsiderToken = register();
        UUID fridgeId = createFridge(ownerToken);
        UUID recipeId = createRecipe(ownerToken);
        JsonNode meal = createPlannedMeal(ownerToken, fridgeId, recipeId);
        UUID mealId = UUID.fromString(meal.get("id").asText());
        UUID ingredientId = UUID.fromString(
                meal.get("recipe").get("ingredients").get(0).get("id").asText());
        UUID fridgeItemId = createFridgeItem(ownerToken, fridgeId);
        when(openAiClient.match(anyList(), anyList())).thenReturn(List.of(
                new ShoppingListIngredientMatch(ingredientId, List.of(fridgeItemId))));
        String request = json(Map.of("plannedMealIds", List.of(mealId)));

        mvc.perform(post("/api/fridges/{fridgeId}/planned-meals/reserve", fridgeId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(mealId.toString()))
                .andExpect(jsonPath("$[0].recipe.ingredients[0].reservations[0].fridgeItemId")
                        .value(fridgeItemId.toString()))
                .andExpect(jsonPath("$[0].recipe.ingredients[0].reservations[0].amount")
                        .value(600));

        mvc.perform(get("/api/fridge-items/{fridgeId}", fridgeId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].amount").value(1000))
                .andExpect(jsonPath("$[0].reservedAmount").value(600))
                .andExpect(jsonPath("$[0].availableAmount").value(400));

        mvc.perform(post("/api/fridges/{fridgeId}/planned-meals/reserve", fridgeId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].recipe.ingredients[0].reservations.length()")
                        .value(1));

        mvc.perform(post("/api/fridges/{fridgeId}/planned-meals/reserve", fridgeId)
                        .header("Authorization", "Bearer " + outsiderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isForbidden());

        assertThat(reservationRepository.count()).isEqualTo(1);
        verify(openAiClient, times(1)).match(anyList(), anyList());
    }

    private String register() throws Exception {
        String login = "auto-reserve+" + UUID.randomUUID() + "@test.local";
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
                                "name", "Owsianka",
                                "description", "Śniadanie",
                                "instructions", "Wymieszaj składniki.",
                                "servings", 2,
                                "ingredients", List.of(Map.of(
                                        "name", "Mleko",
                                        "amount", 300,
                                        "unit", "ml",
                                        "optional", false
                                ))
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(read(result.getResponse().getContentAsString()).get("id").asText());
    }

    private JsonNode createPlannedMeal(String token, UUID fridgeId, UUID recipeId) throws Exception {
        var result = mvc.perform(post("/api/fridges/{fridgeId}/planned-meals", fridgeId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "recipeId", recipeId,
                                "plannedDate", LocalDate.now().plusDays(1),
                                "servings", 4
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return read(result.getResponse().getContentAsString());
    }

    private UUID createFridgeItem(String token, UUID fridgeId) throws Exception {
        var result = mvc.perform(post("/api/fridge-items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "fridgeId", fridgeId,
                                "customName", "Mleko 3,2%",
                                "amount", 1000,
                                "unit", "MILLILITER"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservedAmount").value(0))
                .andExpect(jsonPath("$.availableAmount").value(1000))
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
