package io.github.mkliszczun.fridge.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record PlannedMealReservationUpdateRequest(
        @NotNull @Positive BigDecimal amount
) {
}
