package io.github.mkliszczun.fridge.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mkliszczun.fridge.enums.FridgeRole;
import io.github.mkliszczun.fridge.fridge.FridgeMember;
import io.github.mkliszczun.fridge.repository.FridgeMemberRepository;
import io.github.mkliszczun.fridge.repository.FridgeRepository;
import io.github.mkliszczun.fridge.repository.UserRepository;
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

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = Replace.ANY)
@DirtiesContext
@ActiveProfiles("test")
class PlannedMealFlowE2ETest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FridgeRepository fridgeRepository;

    @Autowired
    private FridgeMemberRepository memberRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void fridgeMembersCanManageSharedPlanAndReadRecipe() throws Exception {
        UserSession owner = register();
        UserSession member = register();
        UserSession outsider = register();
        UUID fridgeId = createFridge(owner.token());
        UUID recipeId = createRecipe(owner.token());
        addMember(fridgeId, member.userId());

        LocalDate plannedDate = LocalDate.now().plusDays(2);
        var createResult = mvc.perform(post("/api/fridges/{fridgeId}/planned-meals", fridgeId)
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(plannedMealRequest(recipeId, plannedDate, 2))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.fridgeId").value(fridgeId.toString()))
                .andExpect(jsonPath("$.recipe.sourceRecipeId").value(recipeId.toString()))
                .andExpect(jsonPath("$.recipe.name").value("Carbonara"))
                .andExpect(jsonPath("$.recipe.ingredients[0].name").value("Makaron"))
                .andExpect(jsonPath("$.plannedDate").value(plannedDate.toString()))
                .andExpect(jsonPath("$.servings").value(2))
                .andExpect(jsonPath("$.createdByUserId").value(owner.userId().toString()))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andReturn();

        UUID plannedMealId = UUID.fromString(
                read(createResult.getResponse().getContentAsString()).get("id").asText());
        UUID ingredientId = UUID.fromString(
                read(createResult.getResponse().getContentAsString())
                        .get("recipe").get("ingredients").get(0).get("id").asText());
        UUID secondIngredientId = UUID.fromString(
                read(createResult.getResponse().getContentAsString())
                        .get("recipe").get("ingredients").get(1).get("id").asText());
        UUID fridgeItemId = createFridgeItem(owner.token(), fridgeId);

        var reservationResult = mvc.perform(post(
                        "/api/fridges/{fridgeId}/planned-meals/{plannedMealId}/reservations",
                        fridgeId, plannedMealId)
                        .header("Authorization", "Bearer " + member.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(reservationRequest(ingredientId, fridgeItemId, 200))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plannedMealIngredientId").value(ingredientId.toString()))
                .andExpect(jsonPath("$.fridgeItemId").value(fridgeItemId.toString()))
                .andExpect(jsonPath("$.itemName").value("Makaron w szafce"))
                .andExpect(jsonPath("$.amount").value(200))
                .andExpect(jsonPath("$.unit").value("GRAM"))
                .andReturn();
        UUID reservationId = UUID.fromString(
                read(reservationResult.getResponse().getContentAsString()).get("id").asText());

        mvc.perform(put(
                        "/api/fridges/{fridgeId}/planned-meals/{plannedMealId}/reservations/{reservationId}",
                        fridgeId, plannedMealId, reservationId)
                        .header("Authorization", "Bearer " + member.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("amount", 250))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(250));

        mvc.perform(post(
                        "/api/fridges/{fridgeId}/planned-meals/{plannedMealId}/reservations",
                        fridgeId, plannedMealId)
                        .header("Authorization", "Bearer " + member.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(reservationRequest(secondIngredientId, fridgeItemId, 251))))
                .andExpect(status().isConflict());

        mvc.perform(put(
                        "/api/fridges/{fridgeId}/planned-meals/{plannedMealId}/reservations/{reservationId}",
                        fridgeId, plannedMealId, reservationId)
                        .header("Authorization", "Bearer " + member.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("amount", 501))))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/fridges/{fridgeId}/planned-meals/{plannedMealId}", fridgeId, plannedMealId)
                        .header("Authorization", "Bearer " + member.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipe.ingredients[0].reservations[0].id")
                        .value(reservationId.toString()))
                .andExpect(jsonPath("$.recipe.ingredients[0].reservations[0].amount").value(250));

        mvc.perform(delete(
                        "/api/fridges/{fridgeId}/planned-meals/{plannedMealId}/reservations/{reservationId}",
                        fridgeId, plannedMealId, reservationId)
                        .header("Authorization", "Bearer " + outsider.token()))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/fridges/{fridgeId}/planned-meals", fridgeId)
                        .header("Authorization", "Bearer " + member.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(plannedMealRequest(recipeId, plannedDate, 2))))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/fridges/{fridgeId}/planned-meals", fridgeId)
                        .header("Authorization", "Bearer " + member.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(plannedMealId.toString()))
                .andExpect(jsonPath("$[0].recipe.instructions").value("Ugotuj makaron."));

        mvc.perform(get("/api/fridges/{fridgeId}/planned-meals/{plannedMealId}", fridgeId, plannedMealId)
                        .header("Authorization", "Bearer " + member.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipe.name").value("Carbonara"));

        LocalDate changedDate = plannedDate.plusDays(1);
        mvc.perform(put("/api/fridges/{fridgeId}/planned-meals/{plannedMealId}", fridgeId, plannedMealId)
                        .header("Authorization", "Bearer " + member.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(plannedMealUpdateRequest(changedDate, 4))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plannedDate").value(changedDate.toString()))
                .andExpect(jsonPath("$.servings").value(4));

        mvc.perform(get("/api/fridges/{fridgeId}/planned-meals", fridgeId)
                        .header("Authorization", "Bearer " + outsider.token()))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/recipes/{recipeId}", recipeId)
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updatedRecipeRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Carbonara po zmianie"));

        mvc.perform(get("/api/fridges/{fridgeId}/planned-meals/{plannedMealId}", fridgeId, plannedMealId)
                        .header("Authorization", "Bearer " + member.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipe.name").value("Carbonara"))
                .andExpect(jsonPath("$.recipe.ingredients[0].name").value("Makaron"));

        mvc.perform(delete("/api/recipes/{recipeId}", recipeId)
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/fridges/{fridgeId}/planned-meals/{plannedMealId}", fridgeId, plannedMealId)
                        .header("Authorization", "Bearer " + member.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipe.sourceRecipeId").value(nullValue()))
                .andExpect(jsonPath("$.recipe.name").value("Carbonara"))
                .andExpect(jsonPath("$.recipe.instructions").value("Ugotuj makaron."))
                .andExpect(jsonPath("$.recipe.ingredients[0].name").value("Makaron"))
                .andExpect(jsonPath("$.recipe.ingredients[0].reservations[0].id")
                        .value(reservationId.toString()));

        mvc.perform(delete(
                        "/api/fridges/{fridgeId}/planned-meals/{plannedMealId}/reservations/{reservationId}",
                        fridgeId, plannedMealId, reservationId)
                        .header("Authorization", "Bearer " + member.token()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/fridges/{fridgeId}/planned-meals/{plannedMealId}", fridgeId, plannedMealId)
                        .header("Authorization", "Bearer " + member.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipe.ingredients[0].reservations").isEmpty());

        mvc.perform(delete("/api/fridges/{fridgeId}/planned-meals/{plannedMealId}", fridgeId, plannedMealId)
                        .header("Authorization", "Bearer " + member.token()))
                .andExpect(status().isNoContent());

    }

    private UserSession register() throws Exception {
        String login = "planned-meal+" + UUID.randomUUID() + "@test.local";
        var result = mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("login", login, "password", "Secret123!"))))
                .andExpect(status().isCreated())
                .andReturn();

        String token = read(result.getResponse().getContentAsString()).get("token").asText();
        UUID userId = userRepository.findByUsername(login).orElseThrow().getId();
        return new UserSession(token, userId);
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
        Map<String, Object> request = Map.of(
                "name", "Carbonara",
                "description", "Klasyczny makaron",
                "instructions", "Ugotuj makaron.",
                "servings", 2,
                "ingredients", List.of(
                        Map.of(
                                "name", "Makaron",
                                "amount", 200,
                                "unit", "g",
                                "optional", false
                        ),
                        Map.of(
                                "name", "Ser",
                                "amount", 50,
                                "unit", "g",
                                "optional", false
                        )
                )
        );
        var result = mvc.perform(post("/api/recipes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(read(result.getResponse().getContentAsString()).get("id").asText());
    }

    private UUID createFridgeItem(String token, UUID fridgeId) throws Exception {
        var result = mvc.perform(post("/api/fridge-items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "fridgeId", fridgeId.toString(),
                                "customName", "Makaron w szafce",
                                "amount", 500,
                                "unit", "GRAM"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(read(result.getResponse().getContentAsString()).get("id").asText());
    }

    private void addMember(UUID fridgeId, UUID userId) {
        FridgeMember membership = new FridgeMember();
        membership.setFridge(fridgeRepository.findById(fridgeId).orElseThrow());
        membership.setUserId(userId);
        membership.setRoleInFridge(FridgeRole.MEMBER);
        membership.setIsDefault(false);
        memberRepository.save(membership);
    }

    private Map<String, Object> plannedMealRequest(UUID recipeId, LocalDate date, int servings) {
        return Map.of(
                "recipeId", recipeId.toString(),
                "plannedDate", date.toString(),
                "servings", servings
        );
    }

    private Map<String, Object> plannedMealUpdateRequest(LocalDate date, int servings) {
        return Map.of(
                "plannedDate", date.toString(),
                "servings", servings
        );
    }

    private Map<String, Object> reservationRequest(UUID ingredientId, UUID fridgeItemId, int amount) {
        return Map.of(
                "plannedMealIngredientId", ingredientId.toString(),
                "fridgeItemId", fridgeItemId.toString(),
                "amount", amount
        );
    }

    private Map<String, Object> updatedRecipeRequest() {
        return Map.of(
                "name", "Carbonara po zmianie",
                "description", "Zmieniony przepis",
                "instructions", "Nowe instrukcje.",
                "servings", 3,
                "ingredients", List.of(Map.of(
                        "name", "Spaghetti",
                        "amount", 300,
                        "unit", "g",
                        "optional", false
                ))
        );
    }

    private JsonNode read(String json) throws Exception {
        return objectMapper.readTree(json);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private record UserSession(String token, UUID userId) {
    }
}
