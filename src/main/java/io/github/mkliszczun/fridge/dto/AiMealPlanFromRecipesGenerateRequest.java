package io.github.mkliszczun.fridge.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record AiMealPlanFromRecipesGenerateRequest(
        @NotNull @FutureOrPresent LocalDate startDate,
        @NotNull @Positive @Max(10) Integer days,
        @NotNull @Positive Integer servings,
        @Size(max = 1000) String guidelines
) {
}
