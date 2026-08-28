package io.github.mkliszczun.fridge.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.UUID;

public record PlannedMealRequest(
        @NotNull UUID recipeId,
        @NotNull LocalDate plannedDate,
        @NotNull @Positive Integer servings
) {
}
