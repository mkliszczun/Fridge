package io.github.mkliszczun.fridge.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ShoppingListImportRequest(
        @NotEmpty @Size(max = 100) List<@Valid Item> items
) {
    public record Item(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 64) String unit,
            @NotEmpty List<@Valid Source> sources
    ) {
    }

    public record Source(
            @NotNull UUID plannedMealIngredientId,
            @Positive BigDecimal amount
    ) {
    }
}
