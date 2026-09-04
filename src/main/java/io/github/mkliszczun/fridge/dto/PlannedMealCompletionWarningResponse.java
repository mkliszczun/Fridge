package io.github.mkliszczun.fridge.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PlannedMealCompletionWarningResponse(
        String code,
        UUID plannedMealIngredientId,
        String ingredientName,
        BigDecimal requiredAmount,
        BigDecimal consumedAmount,
        BigDecimal missingAmount,
        String unit
) {
}
