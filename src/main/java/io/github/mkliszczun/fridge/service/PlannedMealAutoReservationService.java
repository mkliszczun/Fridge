package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.dto.PlannedMealsReserveRequest;
import io.github.mkliszczun.fridge.mealplan.PlannedMeal;

import java.util.List;
import java.util.UUID;

public interface PlannedMealAutoReservationService {

    List<PlannedMeal> reserve(
            UUID fridgeId,
            UUID userId,
            PlannedMealsReserveRequest request);
}
