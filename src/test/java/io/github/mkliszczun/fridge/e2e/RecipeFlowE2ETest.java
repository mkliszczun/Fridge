package io.github.mkliszczun.fridge.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

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
class RecipeFlowE2ETest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void authenticatedUserCanCreateReadUpdateAndDeleteOwnRecipe() throws Exception {
        String token = registerAndLogin();

        mvc.perform(post("/api/recipes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "Niepoprawny przepis",
                                "instructions", "Instrukcje",
                                "servings", 2,
                                "ingredients", List.of()
                        ))))
                .andExpect(status().isBadRequest());

        var createResult = mvc.perform(post("/api/recipes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(recipeRequest("Carbonara", 2))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Carbonara"))
                .andExpect(jsonPath("$.servings").value(2))
                .andExpect(jsonPath("$.ingredients[0].name").value("Makaron"))
                .andExpect(jsonPath("$.ingredients[0].amount").value(200))
                .andExpect(jsonPath("$.ingredients[0].unit").value("g"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andReturn();

        UUID recipeId = UUID.fromString(read(createResult.getResponse().getContentAsString()).get("id").asText());

        mvc.perform(get("/api/recipes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(recipeId.toString()))
                .andExpect(jsonPath("$[0].name").value("Carbonara"));

        mvc.perform(put("/api/recipes/{recipeId}", recipeId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(recipeRequest("Carbonara po zmianie", 4))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Carbonara po zmianie"))
                .andExpect(jsonPath("$.servings").value(4));

        mvc.perform(get("/api/recipes/{recipeId}", recipeId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Carbonara po zmianie"));

        mvc.perform(delete("/api/recipes/{recipeId}", recipeId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/recipes/{recipeId}", recipeId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void otherUserCannotReadUpdateOrDeleteRecipe() throws Exception {
        String ownerToken = registerAndLogin();
        String otherUserToken = registerAndLogin();

        var createResult = mvc.perform(post("/api/recipes")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(recipeRequest("Prywatny przepis", 2))))
                .andExpect(status().isCreated())
                .andReturn();

        UUID recipeId = UUID.fromString(read(createResult.getResponse().getContentAsString()).get("id").asText());

        mvc.perform(get("/api/recipes/{recipeId}", recipeId)
                        .header("Authorization", "Bearer " + otherUserToken))
                .andExpect(status().isNotFound());

        mvc.perform(put("/api/recipes/{recipeId}", recipeId)
                        .header("Authorization", "Bearer " + otherUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(recipeRequest("Próba zmiany", 4))))
                .andExpect(status().isNotFound());

        mvc.perform(delete("/api/recipes/{recipeId}", recipeId)
                        .header("Authorization", "Bearer " + otherUserToken))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/recipes/{recipeId}", recipeId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Prywatny przepis"));
    }

    private String registerAndLogin() throws Exception {
        String login = "recipe+" + UUID.randomUUID() + "@test.local";
        String password = "Secret123!";

        mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("login", login, "password", password))))
                .andExpect(status().isCreated());

        var loginResult = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("login", login, "password", password))))
                .andExpect(status().isOk())
                .andReturn();

        return read(loginResult.getResponse().getContentAsString()).get("token").asText();
    }

    private Map<String, Object> recipeRequest(String name, int servings) {
        return Map.of(
                "name", name,
                "description", "Klasyczny makaron",
                "instructions", "Ugotuj makaron i połącz składniki.",
                "servings", servings,
                "ingredients", List.of(
                        Map.of(
                                "name", "Makaron",
                                "amount", 200,
                                "unit", "g",
                                "optional", false
                        ),
                        Map.of(
                                "name", "Sól",
                                "optional", true,
                                "note", "do smaku"
                        )
                )
        );
    }

    private JsonNode read(String json) throws Exception {
        return objectMapper.readTree(json);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
