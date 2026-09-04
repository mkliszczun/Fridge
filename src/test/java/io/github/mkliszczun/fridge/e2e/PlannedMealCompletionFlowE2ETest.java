package io.github.mkliszczun.fridge.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mkliszczun.fridge.enums.ItemState;
import io.github.mkliszczun.fridge.repository.FridgeItemRepository;
import io.github.mkliszczun.fridge.repository.PlannedMealRepository;
import io.github.mkliszczun.fridge.repository.PlannedMealReservationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
class PlannedMealCompletionFlowE2ETest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FridgeItemRepository fridgeItemRepository;

    @Autowired
    private PlannedMealRepository plannedMealRepository;

    @Autowired
    private PlannedMealReservationRepository reservationRepository;

    @Test
    void memberCanCompleteMealDespiteMissingProductsWithoutConsumingTwice() throws Exception {
        String ownerToken = register();
        String outsiderToken = register();
        UUID fridgeId = createFridge(ownerToken);
        UUID recipeId = createRecipe(ownerToken);
        JsonNode meal = createPlannedMeal(ownerToken, fridgeId, recipeId);
        UUID mealId = UUID.fromString(meal.get("id").asText());
        UUID milkIngredientId = ingredientId(meal, 0);
        UUID cheeseIngredientId = ingredientId(meal, 1);
        UUID milkItemId = createFridgeItem(
                ownerToken, fridgeId, "Mleko 3,2%", 1000, "MILLILITER");
        UUID cheeseItemId = createFridgeItem(
                ownerToken, fridgeId, "Ser", 100, "GRAM");

        createReservation(ownerToken, fridgeId, mealId, milkIngredientId, milkItemId, 600);
        createReservation(ownerToken, fridgeId, mealId, cheeseIngredientId, cheeseItemId, 100);

        mvc.perform(post("/api/fridge-items/{itemId}/use", cheeseItemId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("amountUsed", 60))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(40));

        mvc.perform(post(
                        "/api/fridges/{fridgeId}/planned-meals/{plannedMealId}/complete",
                        fridgeId, mealId)
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isForbidden());

        mvc.perform(post(
                        "/api/fridges/{fridgeId}/planned-meals/{plannedMealId}/complete",
                        fridgeId, mealId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plannedMealId").value(mealId.toString()))
                .andExpect(jsonPath("$.completedAt").isNotEmpty())
                .andExpect(jsonPath("$.warnings.length()").value(2))
                .andExpect(jsonPath("$.warnings[0].code").value("MISSING_AMOUNT"))
                .andExpect(jsonPath("$.warnings[0].ingredientName").value("Ser"))
                .andExpect(jsonPath("$.warnings[0].requiredAmount").value(100))
                .andExpect(jsonPath("$.warnings[0].consumedAmount").value(40))
                .andExpect(jsonPath("$.warnings[0].missingAmount").value(60))
                .andExpect(jsonPath("$.warnings[0].unit").value("GRAM"))
                .andExpect(jsonPath("$.warnings[1].code").value("UNRESERVED_INGREDIENT"))
                .andExpect(jsonPath("$.warnings[1].ingredientName").value("Sól"));

        mvc.perform(get("/api/fridge-items/{fridgeId}", fridgeId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(milkItemId.toString()))
                .andExpect(jsonPath("$[0].amount").value(400))
                .andExpect(jsonPath("$[0].reservedAmount").value(0))
                .andExpect(jsonPath("$[0].availableAmount").value(400))
                .andExpect(jsonPath("$[0].state").value("OPEN"))
                .andExpect(jsonPath("$[0].openDate").value(LocalDate.now().toString()));

        mvc.perform(get("/api/fridges/{fridgeId}/planned-meals", fridgeId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mvc.perform(post(
                        "/api/fridges/{fridgeId}/planned-meals/{plannedMealId}/complete",
                        fridgeId, mealId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isConflict());

        var consumedCheese = fridgeItemRepository.findById(cheeseItemId).orElseThrow();
        assertThat(consumedCheese.getAmount()).isEqualByComparingTo("0");
        assertThat(consumedCheese.getState()).isEqualTo(ItemState.CONSUMED);
        assertThat(consumedCheese.getArchivedAt()).isNotNull();
        assertThat(plannedMealRepository.findById(mealId).orElseThrow().getCompletedAt()).isNotNull();
        assertThat(reservationRepository.count()).isZero();
    }

    private String register() throws Exception {
        String login = "meal-completion+" + UUID.randomUUID() + "@test.local";
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
                                "name", "Zapiekanka",
                                "description", "Obiad",
                                "instructions", "Połącz składniki.",
                                "servings", 2,
                                "ingredients", List.of(
                                        Map.of(
                                                "name", "Mleko",
                                                "amount", 600,
                                                "unit", "ml",
                                                "optional", false
                                        ),
                                        Map.of(
                                                "name", "Ser",
                                                "amount", 100,
                                                "unit", "g",
                                                "optional", false
                                        ),
                                        Map.of(
                                                "name", "Sól",
                                                "amount", 1,
                                                "unit", "szczypta",
                                                "optional", false
                                        )
                                )
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(read(result.getResponse().getContentAsString()).get("id").asText());
    }

    private JsonNode createPlannedMeal(
            String token, UUID fridgeId, UUID recipeId) throws Exception {
        var result = mvc.perform(post("/api/fridges/{fridgeId}/planned-meals", fridgeId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "recipeId", recipeId,
                                "plannedDate", LocalDate.now(),
                                "servings", 2
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return read(result.getResponse().getContentAsString());
    }

    private UUID ingredientId(JsonNode meal, int index) {
        return UUID.fromString(
                meal.get("recipe").get("ingredients").get(index).get("id").asText());
    }

    private UUID createFridgeItem(
            String token, UUID fridgeId, String name, int amount, String unit) throws Exception {
        var result = mvc.perform(post("/api/fridge-items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "fridgeId", fridgeId,
                                "customName", name,
                                "amount", amount,
                                "unit", unit
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(read(result.getResponse().getContentAsString()).get("id").asText());
    }

    private void createReservation(
            String token,
            UUID fridgeId,
            UUID mealId,
            UUID ingredientId,
            UUID fridgeItemId,
            int amount) throws Exception {
        mvc.perform(post(
                        "/api/fridges/{fridgeId}/planned-meals/{plannedMealId}/reservations",
                        fridgeId, mealId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "plannedMealIngredientId", ingredientId,
                                "fridgeItemId", fridgeItemId,
                                "amount", amount
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
