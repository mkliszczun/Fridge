package io.github.mkliszczun.fridge.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PlannedMealCompletionResponse(
        UUID plannedMealId,
        OffsetDateTime completedAt,
        List<PlannedMealCompletionWarningResponse> warnings
) {
}
