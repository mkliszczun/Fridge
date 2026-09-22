package io.github.mkliszczun.fridge.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mkliszczun.fridge.config.OpenAiProperties;
import io.github.mkliszczun.fridge.security.AppUserDetails;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.server.ResponseStatusException;
import java.io.*;
import java.net.URI;

/** Installed on every Spring-managed RestClient.Builder, covering all AI clients and each retry. */
@Component
public class OpenAiBudgetInterceptor implements ClientHttpRequestInterceptor, RestClientCustomizer {
    private static final String USE_COUNTED_ATTRIBUTE = OpenAiBudgetInterceptor.class.getName() + ".useCounted";
    private final AiBudgetService budget;
    private final AiBudgetProperties prices;
    private final OpenAiProperties openAi;
    private final ObjectMapper mapper;

    public OpenAiBudgetInterceptor(AiBudgetService budget, AiBudgetProperties prices, OpenAiProperties openAi, ObjectMapper mapper) {
        this.budget = budget;
        this.prices = prices;
        this.openAi = openAi;
        this.mapper = mapper;
    }

    @Override public void customize(RestClient.Builder builder) { builder.requestInterceptor(this); }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        URI endpoint = URI.create(openAi.getBaseUrl().replaceAll("/+$", "") + "/responses");
        if (!endpoint.equals(request.getURI())) return execution.execute(request, body);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AppUserDetails user)) {
            throw new BadCredentialsException("AI requires an authenticated account");
        }
        if (body.length > 65_536) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "AI request is too large");
        JsonNode payload = mapper.readTree(body);
        if (!prices.getPricedModel().equals(payload.path("model").asText())
                || prices.getCachedInputPrice().compareTo(prices.getInputPrice()) > 0) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI pricing is not configured for this model");
        }
        int maxOutput = payload.path("max_output_tokens").asInt(0);
        // Only bounded text requests are priced here; tools/files/history require separate accounting.
        if (maxOutput < 1 || maxOutput > 8192 || !payload.path("input").isTextual()
                || !payload.path("instructions").isTextual() || payload.has("tools")
                || payload.has("previous_response_id") || payload.has("conversation")
                || (payload.has("service_tier") && !"default".equals(payload.path("service_tier").asText()))) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Unsupported AI billing configuration");
        }
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        boolean newUse = requestAttributes == null
                || requestAttributes.getAttribute(USE_COUNTED_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST) == null;
        var reservation = budget.reserve(user.getId(), user.getTokenVersion(), maxOutput, newUse);
        if (newUse && requestAttributes != null) {
            requestAttributes.setAttribute(USE_COUNTED_ATTRIBUTE, Boolean.TRUE, RequestAttributes.SCOPE_REQUEST);
        }
        ((com.fasterxml.jackson.databind.node.ObjectNode) payload).put("service_tier", "default");
        body = mapper.writeValueAsBytes(payload);
        // A timeout, crash, HTTP error or missing usage retains the conservative reservation.
        ClientHttpResponse response = execution.execute(request, body);
        try {
            byte[] bytes = response.getBody().readAllBytes();
            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode result = mapper.readTree(bytes);
                JsonNode usage = result == null ? null : result.get("usage");
                if (usage != null && usage.path("input_tokens").isIntegralNumber() && usage.path("input_tokens").canConvertToLong()
                        && usage.path("output_tokens").isIntegralNumber() && usage.path("output_tokens").canConvertToLong()) {
                    long cached = usage.path("input_tokens_details").path("cached_tokens").asLong(0);
                    long input = usage.path("input_tokens").asLong();
                    // Missing cache-write details are charged conservatively at the write rate.
                    long writes = usage.path("input_tokens_details").path("cache_write_tokens").asLong(input - cached);
                    budget.settle(reservation, input, cached, writes, usage.path("output_tokens").asLong());
                }
            }
            return new BufferedResponse(response, bytes);
        } catch (IOException | RuntimeException ex) {
            response.close();
            throw ex;
        }
    }

    private record BufferedResponse(ClientHttpResponse delegate, byte[] bytes) implements ClientHttpResponse {
        @Override public HttpStatusCode getStatusCode() throws IOException { return delegate.getStatusCode(); }
        @Override public String getStatusText() throws IOException { return delegate.getStatusText(); }
        @Override public HttpHeaders getHeaders() { return delegate.getHeaders(); }
        @Override public InputStream getBody() { return new ByteArrayInputStream(bytes); }
        @Override public void close() { delegate.close(); }
    }
}
