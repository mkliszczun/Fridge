package io.github.mkliszczun.fridge.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mkliszczun.fridge.dto.AiProductSuggestion;
import io.github.mkliszczun.fridge.enums.*;
import io.github.mkliszczun.fridge.exception.AiServiceUnavailableException;
import io.github.mkliszczun.fridge.repository.ProductRepository;
import io.github.mkliszczun.fridge.service.OpenAiProductClient;
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
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@DirtiesContext
@ActiveProfiles("test")
class AiProductFlowE2ETest extends VerifiedAccountTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired ProductRepository products;
    @MockitoBean OpenAiProductClient client;

    @Test void proposalDoesNotSaveAndExplicitSavePersistsEditableBrandAndDays() throws Exception {
        String token = token();
        long count = products.count();
        when(client.generate(any(), any())).thenReturn(new AiProductSuggestion("Pilos", ProductType.DAIRY, Unit.MILLILITER, 3));
        mvc.perform(post("/api/ai/products/generate").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Mleko\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Mleko"))
                .andExpect(jsonPath("$.productType").value("DAIRY"))
                .andExpect(jsonPath("$.shelfLifeAfterOpeningDays").value(3));
        assertThat(products.count()).isEqualTo(count);
        var response = mvc.perform(post("/api/products").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Mleko poprawione","brand":"Moja marka","productType":"DAIRY",
                                 "defaultUnit":"MILLILITER","shelfLifeAfterOpeningDays":2}
                                """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.brand").value("Moja marka"))
                .andExpect(jsonPath("$.shelfLifeAfterOpeningDays").value(2)).andReturn();
        var id = UUID.fromString(mapper.readTree(response.getResponse().getContentAsString()).get("id").asText());
        assertThat(products.findById(id).orElseThrow().getShelfLifeAfterOpeningDays()).isEqualTo(2);
        assertThat(products.count()).isEqualTo(count + 1);
    }

    @Test void authenticationAndInputValidationHappenBeforeAi() throws Exception {
        mvc.perform(post("/api/ai/products/generate").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Mleko\"}"))
                .andExpect(status().isUnauthorized());
        String token = token();
        for (String input : List.of("{\"name\":\" \"}", "{\"name\":\"Mleko\",\"productType\":\"BAD\"}",
                "{\"name\":\"Mleko\",\"shelfLifeAfterOpeningDays\":-1}",
                mapper.writeValueAsString(Map.of("name", "Mleko", "offData", Map.of("categoriesTags", List.of("x".repeat(121))))))) {
            mvc.perform(post("/api/ai/products/generate").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(input)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(client);
    }

    @Test void invalidAiAndUnavailableProviderDoNotSaveAnything() throws Exception {
        String token = token();
        long count = products.count();
        when(client.generate(any(), any())).thenReturn(new AiProductSuggestion(null, ProductType.DAIRY, Unit.GRAM, -1));
        mvc.perform(post("/api/ai/products/generate").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Mleko\"}"))
                .andExpect(status().isBadGateway());
        verify(client, times(2)).generate(any(), any());
        when(client.generate(any(), any())).thenThrow(new AiServiceUnavailableException("failed"));
        mvc.perform(post("/api/ai/products/generate").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Mleko\"}"))
                .andExpect(status().isServiceUnavailable());
        assertThat(products.count()).isEqualTo(count);
    }

    private String token() throws Exception {
        return mapper.readTree(registerVerified("product-ai-" + UUID.randomUUID() + "@test.local", "Secret123!")
                .getResponse().getContentAsString()).get("token").asText();
    }
}
