package io.github.mkliszczun.fridge.dto;

import java.util.List;
import java.util.UUID;

public record PlannedRecipeResponse(
        UUID sourceRecipeId,
        String name,
        String description,
        String instructions,
        Integer servings,
        List<PlannedMealIngredientResponse> ingredients
) {
}
