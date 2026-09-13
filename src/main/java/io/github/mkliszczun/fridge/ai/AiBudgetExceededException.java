package io.github.mkliszczun.fridge.ai;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class AiBudgetExceededException extends ResponseStatusException {
    private final HttpHeaders headers = new HttpHeaders();

    public AiBudgetExceededException(long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "Daily AI budget exhausted");
        headers.set(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
    }

    @Override public HttpHeaders getHeaders() { return headers; }
}
