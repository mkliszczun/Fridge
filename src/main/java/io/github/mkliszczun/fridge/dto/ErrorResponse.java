package io.github.mkliszczun.fridge.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
        Instant timestamp,
        String error,
        List<String> details
) {
    public static ErrorResponse of(String error) {
        return new ErrorResponse(Instant.now(), error, List.of());
    }
    public static ErrorResponse of(String error, List<String> details) {
        return new ErrorResponse(Instant.now(), error, details);
    }
}