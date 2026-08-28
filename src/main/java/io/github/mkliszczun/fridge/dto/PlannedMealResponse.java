package io.github.mkliszczun.fridge.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PlannedMealResponse(
        UUID id,
        UUID fridgeId,
        RecipeResponse recipe,
        LocalDate plannedDate,
        Integer servings,
        UUID createdByUserId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
