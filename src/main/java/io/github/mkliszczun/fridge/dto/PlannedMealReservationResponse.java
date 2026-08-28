package io.github.mkliszczun.fridge.dto;

import io.github.mkliszczun.fridge.enums.Unit;

import java.math.BigDecimal;
import java.util.UUID;

public record PlannedMealReservationResponse(
        UUID id,
        UUID plannedMealIngredientId,
        UUID fridgeItemId,
        String itemName,
        BigDecimal amount,
        Unit unit
) {
}
