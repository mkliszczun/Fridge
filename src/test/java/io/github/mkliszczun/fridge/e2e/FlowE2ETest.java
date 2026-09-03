package io.github.mkliszczun.fridge.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = Replace.ANY)
@DirtiesContext
@ActiveProfiles("test")
class FlowE2ETest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @Test
    void full_flow_register_login_createFridge_addItem_use_discard() throws Exception {
        // 1) REGISTER
        var login = "user+" + UUID.randomUUID() + "@test.local";
        var password = "Secret123!";
        mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("login", login, "password", password))))
                .andExpect(status().isCreated());

        // 2) LOGIN → JWT
        var loginRes = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("login", login, "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andReturn();
        var token = Json.read(loginRes.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .str("token");
        assertThat(token).isNotBlank();

        // 3) CREATE FRIDGE
        var fridgeRes = mvc.perform(post("/api/fridges")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Dom"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Dom"))
                .andReturn();
        var fridgeId = UUID.fromString(Json.read(fridgeRes.getResponse().getContentAsString()).str("id"));

        // 4) CREATE PRODUCT (add to catalog)
        var productRes = mvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "Mleko",
                                "productType", "DAIRY",
                                "ean", "1234567891234",
                                "defaultUnit", "MILLILITER",
                                "shelfLifeAfterOpeningDays", 3
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.shelfLifeAfterOpeningDays").value(3))
                .andReturn();
        var productId = UUID.fromString(Json.read(productRes.getResponse().getContentAsString()).str("id"));

        // 5) ADD FRIDGE ITEM
        var itemRes = mvc.perform(post("/api/fridge-items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "fridgeId", fridgeId.toString(),
                                "productId", productId.toString(),
                                "amount", "1000",
                                "unit", "MILLILITER",
                                "bestBeforeDate", java.time.LocalDate.now().plusDays(5).toString()
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.state").value("SEALED"))
                .andReturn();
        var itemId = UUID.fromString(Json.read(itemRes.getResponse().getContentAsString()).str("id"));

        var today = java.time.LocalDate.now();

        // 6) CHANGE BEST-BEFORE DATE
        var changedBestBeforeDate = today.plusDays(8);
        mvc.perform(patch("/api/fridge-items/{id}/best-before-date", itemId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "bestBeforeDate", changedBestBeforeDate.toString()
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bestBeforeDate").value(changedBestBeforeDate.toString()))
                .andExpect(jsonPath("$.effectiveExpireAt").value(changedBestBeforeDate.toString()));

        // 7) USED PRODUCT CANNOT BE DELETED
        mvc.perform(delete("/api/products/{id}", productId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());

        // 8) UNUSED PRODUCT CAN BE DELETED
        var unusedProductRes = mvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "Produkt testowy",
                                "productType", "OTHER",
                                "ean", "9876543210987",
                                "defaultUnit", "GRAM"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        var unusedProductId = UUID.fromString(
                Json.read(unusedProductRes.getResponse().getContentAsString()).str("id"));

        mvc.perform(delete("/api/products/{id}", unusedProductId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // 9) USE PART OF SEALED ITEM
        mvc.perform(post("/api/fridge-items/{id}/use", itemId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("amountUsed", "100"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(900))
                .andExpect(jsonPath("$.state").value("OPEN"))
                .andExpect(jsonPath("$.openDate").value(today.toString()))
                .andExpect(jsonPath("$.effectiveExpireAt").value(today.plusDays(3).toString()));

        // 10) DISCARD
        mvc.perform(post("/api/fridge-items/{id}/discard", itemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // 11) GET ITEM
         mvc.perform(get("/api/fridge-items/item/{id}", itemId)
                 .header("Authorization", "Bearer " + token))
             .andExpect(status().isOk())
             .andExpect(jsonPath("$.state").value("DISCARDED"));
    }

    // --- helpers ---
    private String json(Object o) throws Exception { return om.writeValueAsString(o); }

    /**
     * Super simple JSON Parser
     */
    static class Json {
        private final com.fasterxml.jackson.databind.JsonNode n;
        private Json(com.fasterxml.jackson.databind.JsonNode n){ this.n = n; }
        static Json read(String s) throws Exception {
            return new Json(new com.fasterxml.jackson.databind.ObjectMapper().readTree(s));
        }
        String str(String name){ return n.get(name).asText(); }
    }
}
