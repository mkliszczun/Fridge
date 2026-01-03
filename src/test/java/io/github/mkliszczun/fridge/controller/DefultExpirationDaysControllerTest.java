package io.github.mkliszczun.fridge.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mkliszczun.fridge.entity.DefaultExpirationDays;
import io.github.mkliszczun.fridge.enums.ProductType;
import io.github.mkliszczun.fridge.security.JpaUserDetailsService;
import io.github.mkliszczun.fridge.util.JwtUtil;
import io.github.mkliszczun.fridge.service.DefaultExpirationDaysService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DefultExpirationDaysController.class)
@AutoConfigureMockMvc(addFilters = false)
class DefultExpirationDaysControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @MockitoBean DefaultExpirationDaysService service;
    @MockitoBean JwtUtil jwtUtil;
    @MockitoBean JpaUserDetailsService userDetailsService;

    @Test
    void getExpirationDays_returnsConfiguredDefaults() throws Exception {
        for (ProductType type : ProductType.values()) {
            when(service.getByProductType(type)).thenReturn(Optional.empty());
        }

        when(service.getByProductType(ProductType.DAIRY)).thenReturn(Optional.of(
                DefaultExpirationDays.builder()
                        .productType(ProductType.DAIRY)
                        .defaultExpirationDays(7)
                        .expirationDaysAfterOpening(3)
                        .build()
        ));
        when(service.getByProductType(ProductType.MEAT)).thenReturn(Optional.of(
                DefaultExpirationDays.builder()
                        .productType(ProductType.MEAT)
                        .defaultExpirationDays(5)
                        .expirationDaysAfterOpening(2)
                        .build()
        ));

        mvc.perform(get("/admin/expiration"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].productType").value("DAIRY"))
                .andExpect(jsonPath("$[0].defaultExpirationDays").value(7))
                .andExpect(jsonPath("$[0].expirationDaysAfterOpening").value(3))
                .andExpect(jsonPath("$[1].productType").value("MEAT"))
                .andExpect(jsonPath("$[1].defaultExpirationDays").value(5))
                .andExpect(jsonPath("$[1].expirationDaysAfterOpening").value(2));
    }

    @Test
    void updateDefaultExpiration_requiresDefaultExpirationDays() throws Exception {
        mvc.perform(post("/admin/expiration/default")
                        .param("productType", "DAIRY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void updateDefaultExpiration_rejectsNegativeDays() throws Exception {
        mvc.perform(post("/admin/expiration/default")
                        .param("productType", "DAIRY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("defaultExpirationDays", -1))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void updateDefaultExpiration_updatesWithValidDays() throws Exception {
        mvc.perform(post("/admin/expiration/default")
                        .param("productType", "DAIRY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("defaultExpirationDays", 10))))
                .andExpect(status().isOk());

        verify(service).updateDefaultExpiration(ProductType.DAIRY, 10);
    }

    @Test
    void updateAfterOpening_requiresExpirationDaysAfterOpening() throws Exception {
        mvc.perform(post("/admin/expiration/after-opening")
                        .param("productType", "DAIRY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void updateAfterOpening_updatesWithValidDays() throws Exception {
        mvc.perform(post("/admin/expiration/after-opening")
                        .param("productType", "DAIRY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("expirationDaysAfterOpening", 4))))
                .andExpect(status().isNoContent());

        verify(service).updateDaysAfterOpeningForType(ProductType.DAIRY, 4);
    }

    private String json(Object o) throws Exception {
        return om.writeValueAsString(o);
    }
}
