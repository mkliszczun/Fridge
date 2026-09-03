package io.github.mkliszczun.fridge.service;

import java.util.List;
import java.util.UUID;

public record MealPlanWithFridgeRecipeCandidate(
        UUID id,
        String name,
        String description,
        Integer servings,
        List<MealPlanWithFridgeIngredientCandidate> ingredients
) {
}
