package io.github.mkliszczun.fridge.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mkliszczun.fridge.config.OpenAiProperties;
import io.github.mkliszczun.fridge.dto.AiProductGenerateRequest;
import io.github.mkliszczun.fridge.security.AppUserDetails;
import io.github.mkliszczun.fridge.service.*;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.*;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AiProductBudgetTest {
    private final UUID userId = UUID.randomUUID();
    private final AiBudgetService budget = mock(AiBudgetService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final AiBudgetService.Reservation reservation = new AiBudgetService.Reservation(userId, LocalDate.now(), 30400, 2000);
    private final AiProductGenerateRequest request = new AiProductGenerateRequest("Mleko", null, null, null, null, null, null);
    private MockRestServiceServer server;
    private AiProductService service;
    private ValidatorFactory factory;

    @BeforeEach void setup() {
        var properties = new OpenAiProperties();
        properties.setApiKey("fake-key");
        var builder = RestClient.builder();
        new OpenAiBudgetInterceptor(budget, new AiBudgetProperties(), properties, mapper).customize(builder);
        server = MockRestServiceServer.bindTo(builder).build();
        factory = Validation.buildDefaultValidatorFactory();
        service = new AiProductService(new OpenAiProductClient(builder, properties, mapper), factory.getValidator(), mock(DefaultExpirationDaysService.class));
        var principal = new AppUserDetails(userId, "test", "unused", Set.of(), true, true, true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        when(budget.reserve(eq(userId), eq(0L), eq(2000), anyBoolean())).thenReturn(reservation);
    }
    @AfterEach void close() {
        SecurityContextHolder.clearContext(); RequestContextHolder.resetRequestAttributes(); factory.close();
    }

    @Test void invalidOutputRetryChargesBothResponsesButCountsOneUse() throws Exception {
        for (String output : List.of("{}", "{\"brand\":null,\"productType\":\"DAIRY\",\"defaultUnit\":\"MILLILITER\",\"shelfLifeAfterOpeningDays\":3}")) {
            server.expect(requestTo("https://api.openai.com/v1/responses")).andRespond(withSuccess(mapper.writeValueAsString(
                    Map.of("output_text", output, "usage", Map.of("input_tokens", 100, "output_tokens", 40))), MediaType.APPLICATION_JSON));
        }
        assertThat(service.generate(request).shelfLifeAfterOpeningDays()).isEqualTo(3);
        verify(budget).reserve(userId, 0, 2000, true);
        verify(budget).reserve(userId, 0, 2000, false);
        verify(budget, times(2)).settle(reservation, 100, 0, 100, 40);
        server.verify();
    }

    @Test void exhaustedBudgetRejectsProductGenerationBeforeCallingProvider() {
        when(budget.reserve(userId, 0, 2000, true)).thenThrow(new AiBudgetExceededException(60));
        assertThatThrownBy(() -> service.generate(request)).isInstanceOf(AiBudgetExceededException.class);
        verify(budget).reserve(userId, 0, 2000, true);
        verify(budget, never()).settle(any(), anyLong(), anyLong(), anyLong(), anyLong());
        server.verify();
    }
}
