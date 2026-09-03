package io.github.mkliszczun.fridge.service;

import java.math.BigDecimal;

public record MealPlanWithFridgeIngredientCandidate(
        String name,
        BigDecimal amount,
        String unit,
        boolean optional
) {
}
