package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.dto.PlannedMealRequest;
import io.github.mkliszczun.fridge.mealplan.PlannedMeal;

import java.util.List;
import java.util.UUID;

public interface PlannedMealService {
    PlannedMeal create(UUID fridgeId, UUID userId, PlannedMealRequest request);
    List<PlannedMeal> list(UUID fridgeId, UUID userId);
    PlannedMeal get(UUID fridgeId, UUID plannedMealId, UUID userId);
    PlannedMeal update(UUID fridgeId, UUID plannedMealId, UUID userId, PlannedMealRequest request);
    void delete(UUID fridgeId, UUID plannedMealId, UUID userId);
}
