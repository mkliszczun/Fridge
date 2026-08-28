package io.github.mkliszczun.fridge.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record PlannedMealReservationRequest(
        @NotNull UUID plannedMealIngredientId,
        @NotNull UUID fridgeItemId,
        @NotNull @Positive BigDecimal amount
) {
}
