package io.github.mkliszczun.fridge.service;

import java.util.List;
import java.util.UUID;

public record ShoppingListIngredientMatch(
        UUID plannedMealIngredientId,
        List<UUID> fridgeItemIds
) {
}
