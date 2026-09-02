package io.github.mkliszczun.fridge.service;

import java.math.BigDecimal;
import java.util.UUID;

public record ShoppingListIngredientCandidate(
        UUID id,
        String name,
        BigDecimal amount,
        String unit
) {
}
