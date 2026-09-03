package io.github.mkliszczun.fridge.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AiShoppingListItemSourceResponse(
        UUID plannedMealIngredientId,
        BigDecimal amount
) {
}
