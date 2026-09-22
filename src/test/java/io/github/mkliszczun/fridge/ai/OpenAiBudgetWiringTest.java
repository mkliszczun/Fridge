package io.github.mkliszczun.fridge.ai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.web.client.RestClient;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class OpenAiBudgetWiringTest {
    @Autowired ApplicationContext context;

    @Test void allProductionAiClientsRejectUnmeteredCallsBeforeHttp() {
        for (String bean : new String[]{"openAiRecipeClient", "openAiMealPlanClient", "openAiMealPlanWithFridgeClient", "openAiShoppingListClient", "openAiProductClient"}) {
            RestClient client = (RestClient) ReflectionTestUtils.getField(context.getBean(bean), "restClient");
            assertThatThrownBy(() -> client.post().uri("/responses").body(Map.of()).retrieve().toBodilessEntity())
                    .as(bean).isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
        }
    }
}
