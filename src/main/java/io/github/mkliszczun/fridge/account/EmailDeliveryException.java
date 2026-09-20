package io.github.mkliszczun.fridge.account;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class EmailDeliveryException extends ResponseStatusException {
    public EmailDeliveryException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "Email delivery unavailable; retry with the same verification token");
    }
}
