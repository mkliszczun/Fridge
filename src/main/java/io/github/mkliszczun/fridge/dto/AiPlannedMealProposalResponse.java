package io.github.mkliszczun.fridge.dto;

import java.time.LocalDate;
import java.util.UUID;

public record AiPlannedMealProposalResponse(
        LocalDate plannedDate,
        Integer servings,
        UUID recipeId,
        String recipeName
) {
}
