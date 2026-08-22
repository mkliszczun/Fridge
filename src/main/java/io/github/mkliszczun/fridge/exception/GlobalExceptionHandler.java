package io.github.mkliszczun.fridge.exception;

import io.github.mkliszczun.fridge.dto.ErrorResponse;
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
public class GlobalExceptionHandler {

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

        if (ex instanceof EntityNotFoundException) {
            status = HttpStatus.NOT_FOUND;
        } else if (ex instanceof ResponseStatusException rse) {
            status = HttpStatus.valueOf(rse.getStatusCode().value());
        } else {
            ResponseStatus rs = AnnotationUtils.findAnnotation(ex.getClass(), ResponseStatus.class);
            if (rs != null) {
                status = rs.value();
            }
        }

        return ResponseEntity
                .status(status)
                .body(ErrorResponse.of(ex.getMessage()));
    }
}
