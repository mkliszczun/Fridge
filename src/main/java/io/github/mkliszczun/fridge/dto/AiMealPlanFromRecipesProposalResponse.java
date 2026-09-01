package io.github.mkliszczun.fridge.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AiMealPlanFromRecipesProposalResponse(
        UUID fridgeId,
        LocalDate startDate,
        Integer days,
        Integer servings,
        List<AiPlannedMealProposalResponse> meals
) {
}
