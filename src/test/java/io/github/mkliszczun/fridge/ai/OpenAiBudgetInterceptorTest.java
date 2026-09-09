package io.github.mkliszczun.fridge.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mkliszczun.fridge.config.OpenAiProperties;
import io.github.mkliszczun.fridge.security.AppUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OpenAiBudgetInterceptorTest {
    final AiBudgetService budget = mock(AiBudgetService.class);
    final ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
    final ObjectMapper mapper = new ObjectMapper();
    final UUID userId = UUID.randomUUID();
    final AiBudgetService.Reservation reservation = new AiBudgetService.Reservation(userId, LocalDate.now(), 30400, 4000);
    OpenAiBudgetInterceptor interceptor;
    MockClientHttpRequest request;
    byte[] body;

    @BeforeEach void setup() throws Exception {
        OpenAiProperties openAi = new OpenAiProperties();
        openAi.setBaseUrl("https://api.openai.com/v1");
        interceptor = new OpenAiBudgetInterceptor(budget, new AiBudgetProperties(), openAi, mapper);
        request = new MockClientHttpRequest(HttpMethod.POST, URI.create("https://api.openai.com/v1/responses"));
        body = mapper.writeValueAsBytes(Map.of("model", "gpt-5.6-luna", "input", "Milk", "instructions", "Recipe", "max_output_tokens", 4000));
        var principal = new AppUserDetails(userId, "test", "unused", Set.of(), true, true, true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        when(budget.reserve(userId, 0, 4000)).thenReturn(reservation);
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void metersBeforeSchemaParsingAndPreservesResponseBody() throws Exception {
        byte[] response = "{\"output\":[],\"usage\":{\"input_tokens\":1000,\"output_tokens\":100,\"input_tokens_details\":{\"cached_tokens\":500}}}".getBytes(StandardCharsets.UTF_8);
        when(execution.execute(any(), any())).thenReturn(new MockClientHttpResponse(response, HttpStatus.OK));
        try (var result = interceptor.intercept(request, body, execution)) {
            assertThat(result.getBody().readAllBytes()).isEqualTo(response);
        }
        verify(budget).settle(reservation, 1000, 500, 100);
        var order = inOrder(budget, execution);
        order.verify(budget).reserve(userId, 0, 4000);
        order.verify(execution).execute(any(), any());
    }

    @Test void timeoutAndMissingUsageRetainReservation() throws Exception {
        when(execution.execute(any(), any())).thenThrow(new IOException("timeout"));
        assertThatThrownBy(() -> interceptor.intercept(request, body, execution)).isInstanceOf(IOException.class);
        verify(budget, never()).settle(any(), anyLong(), anyLong(), anyLong());
        when(execution.execute(any(), any())).thenReturn(new MockClientHttpResponse("{}".getBytes(StandardCharsets.UTF_8), HttpStatus.OK));
        interceptor.intercept(request, body, execution).close();
        verify(budget, times(2)).reserve(userId, 0, 4000);
        verify(budget, never()).settle(any(), anyLong(), anyLong(), anyLong());
    }

    @Test void budgetExhaustionAndUnknownPricingNeverCallProvider() throws Exception {
        when(budget.reserve(userId, 0, 4000)).thenThrow(new AiBudgetExceededException(60));
        assertThatThrownBy(() -> interceptor.intercept(request, body, execution)).isInstanceOf(AiBudgetExceededException.class);
        byte[] unknownModel = mapper.writeValueAsBytes(Map.of("model", "unknown-model"));
        assertThatThrownBy(() -> interceptor.intercept(request, unknownModel, execution))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verifyNoInteractions(execution);
    }

    @Test void unrelatedHttpRequestsAreNotMeteredAndAiWithoutUserIsDenied() throws Exception {
        when(execution.execute(any(), any())).thenReturn(new MockClientHttpResponse(new byte[0], HttpStatus.OK));
        var unrelated = new MockClientHttpRequest(HttpMethod.GET, URI.create("https://example.com/product"));
        interceptor.intercept(unrelated, new byte[0], execution).close();
        verifyNoInteractions(budget);
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> interceptor.intercept(request, body, execution))
                .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
        verify(execution, times(1)).execute(any(), any());
    }
}
