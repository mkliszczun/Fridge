package io.github.mkliszczun.fridge.exception;

import io.github.mkliszczun.fridge.dto.ErrorResponse;
import io.github.mkliszczun.fridge.logging.SafeDiagnostics;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@ControllerAdvice
@lombok.extern.slf4j.Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(io.github.mkliszczun.fridge.account.EmailDeliveryException.class)
    public ResponseEntity<ErrorResponse> handleEmailDelivery(io.github.mkliszczun.fridge.account.EmailDeliveryException ex) {
        log.error("event=email_delivery_unavailable diagnostics={}", SafeDiagnostics.describe(ex));
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ErrorResponse.of(ex.getReason()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {

        List<String> details = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .toList();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("Validation failed", details));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex) {

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;

        if (ex instanceof org.springframework.security.core.AuthenticationException) {
            status = HttpStatus.UNAUTHORIZED;
        } else if (ex instanceof org.springframework.security.access.AccessDeniedException) {
            status = HttpStatus.FORBIDDEN;
        } else if (ex instanceof EntityNotFoundException) {
            status = HttpStatus.NOT_FOUND;
        } else if (ex instanceof ResponseStatusException rse) {
            status = HttpStatus.valueOf(rse.getStatusCode().value());
        } else {
            ResponseStatus rs = AnnotationUtils.findAnnotation(ex.getClass(), ResponseStatus.class);
            if (rs != null) {
                status = rs.value();
            }
        }

        if (status.is5xxServerError()) {
            log.error("event=api_failure status={} diagnostics={}", status.value(), SafeDiagnostics.describe(ex));
        }
        var response = ResponseEntity.status(status);
        if (ex instanceof ResponseStatusException rse) {
            response.headers(rse.getHeaders());
        }
        return response.body(ErrorResponse.of(status.is5xxServerError() ? "Service unavailable"
                : status == HttpStatus.UNAUTHORIZED ? "Authentication failed" : ex.getMessage()));
    }
}
