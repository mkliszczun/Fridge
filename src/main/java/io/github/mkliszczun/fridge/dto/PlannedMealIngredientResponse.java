package io.github.mkliszczun.fridge.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PlannedMealIngredientResponse(
        UUID id,
        String name,
        BigDecimal amount,
        String unit,
        boolean optional,
        String note,
        int position,
        List<PlannedMealReservationResponse> reservations
) {
}
