package io.github.mkliszczun.fridge.exception;

import io.github.mkliszczun.fridge.dto.ErrorResponse;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void returnsStatusDeclaredOnApplicationException() {
        assertRuntimeResponse(new NotFoundException("missing"), HttpStatus.NOT_FOUND);
        assertRuntimeResponse(new EntityNotFoundException("missing entity"), HttpStatus.NOT_FOUND);
        assertRuntimeResponse(new ForbiddenException("forbidden"), HttpStatus.FORBIDDEN);
        assertRuntimeResponse(new ConflictException("conflict"), HttpStatus.CONFLICT);
        assertRuntimeResponse(new ParsingProductFromApiException("off unavailable"), HttpStatus.BAD_GATEWAY);
    }

    @Test
    void returnsStatusFromResponseStatusException() {
        assertRuntimeResponse(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "bad request"),
                HttpStatus.BAD_REQUEST
        );
    }

    @Test
    void returnsInternalServerErrorForUnhandledRuntimeException() {
        assertRuntimeResponse(new RuntimeException("failure"), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void returnsValidationDetails() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "name", "must not be blank"));

        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("validatedMethod", String.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidationException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().timestamp()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("Validation failed");
        assertThat(response.getBody().details()).containsExactly("name: must not be blank");
    }

    private void assertRuntimeResponse(RuntimeException exception, HttpStatus expectedStatus) {
        ResponseEntity<ErrorResponse> response = handler.handleRuntimeException(exception);

        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().timestamp()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo(exception.getMessage());
        assertThat(response.getBody().details()).isEmpty();
    }

    @SuppressWarnings("unused")
    private void validatedMethod(String value) {
    }
}
