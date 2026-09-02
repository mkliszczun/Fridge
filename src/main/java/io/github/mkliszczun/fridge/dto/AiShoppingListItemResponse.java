package io.github.mkliszczun.fridge.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record AiShoppingListItemResponse(
        String name,
        BigDecimal amount,
        String unit,
        List<UUID> plannedMealIngredientIds
) {
}
