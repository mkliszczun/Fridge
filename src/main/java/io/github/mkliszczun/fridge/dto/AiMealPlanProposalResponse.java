package io.github.mkliszczun.fridge.dto;

import java.time.LocalDate;
import java.util.UUID;

public record AiMealPlanProposalResponse(
        UUID fridgeId,
        LocalDate plannedDate,
        RecipeRequest recipe
) {
}
