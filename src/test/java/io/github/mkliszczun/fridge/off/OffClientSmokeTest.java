package io.github.mkliszczun.fridge.off;

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
//        String ean = "3017620422003";
        String ean = "5900783009472";

        OffClient.OffResponse resp = offClient.getByEan(ean)
                .block(Duration.ofSeconds(10));

        assertNotNull(resp, "No response from OFF");
        System.out.println("=== OpenFoodFacts response ===");
        System.out.println(mapper.writeValueAsString(resp));

        assertEquals(1, resp.status(), "OFF returned != 1 status");
        assertEquals(ean, resp.code(), "EAN code is incorrect");
        assertNotNull(resp.product(), "'Product' field is empty");
    }
}
