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
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
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
class AiShoppingListFlowE2ETest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlannedMealReservationRepository reservationRepository;

    @MockitoBean
    private OpenAiShoppingListClient openAiClient;

    @Test
    void memberCanGenerateShoppingListWithoutSavingReservations() throws Exception {
        String ownerToken = register();
        String outsiderToken = register();
        UUID fridgeId = createFridge(ownerToken);
        UUID recipeId = createRecipe(ownerToken);
        JsonNode plannedMeal = createPlannedMeal(ownerToken, fridgeId, recipeId);
        UUID plannedMealId = UUID.fromString(plannedMeal.get("id").asText());
        UUID pastaIngredientId = UUID.fromString(
                plannedMeal.get("recipe").get("ingredients").get(0).get("id").asText());
        UUID cheeseIngredientId = UUID.fromString(
                plannedMeal.get("recipe").get("ingredients").get(1).get("id").asText());
        UUID fridgeItemId = createFridgeItem(ownerToken, fridgeId);
        when(openAiClient.match(anyList(), anyList())).thenReturn(List.of(
                new ShoppingListIngredientMatch(
                        pastaIngredientId, List.of(fridgeItemId))
        ));

        mvc.perform(post("/api/fridges/{fridgeId}/ai/shopping-lists/generate", fridgeId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("plannedMealIds", List.of(plannedMealId)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fridgeId").value(fridgeId.toString()))
                .andExpect(jsonPath("$.plannedMealIds[0]").value(plannedMealId.toString()))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].name").value("Makaron"))
                .andExpect(jsonPath("$.items[0].amount").value(100))
                .andExpect(jsonPath("$.items[0].unit").value("GRAM"))
                .andExpect(jsonPath("$.items[0].plannedMealIngredientIds[0]")
                        .value(pastaIngredientId.toString()))
                .andExpect(jsonPath("$.items[1].name").value("Ser"))
                .andExpect(jsonPath("$.items[1].amount").value(100))
                .andExpect(jsonPath("$.items[1].unit").value("GRAM"))
                .andExpect(jsonPath("$.items[1].plannedMealIngredientIds[0]")
                        .value(cheeseIngredientId.toString()));

        assertThat(reservationRepository.count()).isZero();

        mvc.perform(post("/api/fridges/{fridgeId}/ai/shopping-lists/generate", fridgeId)
                        .header("Authorization", "Bearer " + outsiderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("plannedMealIds", List.of(plannedMealId)))))
                .andExpect(status().isForbidden());

        List<UUID> tooManyMealIds = IntStream.range(0, 11)
                .mapToObj(ignored -> UUID.randomUUID())
                .toList();
        mvc.perform(post("/api/fridges/{fridgeId}/ai/shopping-lists/generate", fridgeId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("plannedMealIds", tooManyMealIds))))
                .andExpect(status().isBadRequest());

        verify(openAiClient, times(1)).match(anyList(), anyList());
    }

    private String register() throws Exception {
        String login = "shopping+" + UUID.randomUUID() + "@test.local";
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
                                "name", "Makaron z serem",
                                "description", "Obiad testowy",
                                "instructions", "Ugotuj makaron i dodaj ser.",
                                "servings", 2,
                                "ingredients", List.of(
                                        ingredient("Makaron", 200, "g", false),
                                        ingredient("Ser", 50, "g", false),
                                        ingredient("Bazylia", 5, "g", true)
                                )
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
                                "customName", "Makaron pełnoziarnisty",
                                "amount", 300,
                                "unit", "GRAM"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(read(result.getResponse().getContentAsString()).get("id").asText());
    }

    private Map<String, Object> ingredient(String name, int amount, String unit, boolean optional) {
        return Map.of(
                "name", name,
                "amount", amount,
                "unit", unit,
                "optional", optional
        );
    }

    private JsonNode read(String json) throws Exception {
        return objectMapper.readTree(json);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
