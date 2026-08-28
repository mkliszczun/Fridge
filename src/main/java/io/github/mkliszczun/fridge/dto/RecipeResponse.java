package io.github.mkliszczun.fridge.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record RecipeResponse(
        UUID id,
        String name,
        String description,
        String instructions,
        Integer servings,
        List<RecipeIngredientResponse> ingredients,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
