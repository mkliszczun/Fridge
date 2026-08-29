package io.github.mkliszczun.fridge.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record AiMealPlanGenerateRequest(
        @NotNull @FutureOrPresent LocalDate plannedDate,
        @NotNull @Positive Integer servings,
        @Size(max = 1000) String prompt,
        @Valid RecipeRequest previousProposal,
        @Size(max = 1000) String feedback
) {
}
