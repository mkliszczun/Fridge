package com.example.demo.off;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@Disabled("Smoke test - run manually")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OffClientSmokeTest {

    @Autowired
    OffClient offClient;

    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Test
    void shouldFetchProductAndPrint() throws Exception {
        // znany EAN (Nutella); możesz podmienić na dowolny
        String ean = "3017620422003";

        OffClient.OffResponse resp = offClient.getByEan(ean)
                .block(Duration.ofSeconds(10));

        assertNotNull(resp, "Brak odpowiedzi z OFF");
        System.out.println("=== OpenFoodFacts response ===");
        System.out.println(mapper.writeValueAsString(resp));

        assertEquals(1, resp.status(), "OFF zwrócił status != 1 (nie znaleziono?)");
        assertEquals(ean, resp.code(), "Kod EAN w odpowiedzi nie zgadza się");
        assertNotNull(resp.product(), "Pole 'product' jest puste");
    }
}
