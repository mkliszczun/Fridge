package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.dto.PlannedMealCompletionResponse;

import java.util.UUID;

public interface PlannedMealCompletionService {

    PlannedMealCompletionResponse complete(UUID fridgeId, UUID plannedMealId, UUID userId);
}
