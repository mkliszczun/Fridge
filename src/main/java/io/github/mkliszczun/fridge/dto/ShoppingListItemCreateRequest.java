package io.github.mkliszczun.fridge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ShoppingListItemCreateRequest(
        @NotBlank @Size(max = 255) String name,
        @Positive BigDecimal amount,
        @Size(max = 64) String unit
) {
}
