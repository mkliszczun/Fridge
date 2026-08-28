package io.github.mkliszczun.fridge.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record RecipeIngredientResponse(
        UUID id,
        String name,
        BigDecimal amount,
        String unit,
        boolean optional,
        String note,
        int position
) {
}
