package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.dto.PlannedMealCreateRequest;
import io.github.mkliszczun.fridge.dto.PlannedMealReservationRequest;
import io.github.mkliszczun.fridge.dto.PlannedMealReservationUpdateRequest;
import io.github.mkliszczun.fridge.dto.PlannedMealUpdateRequest;
import io.github.mkliszczun.fridge.mealplan.PlannedMeal;
import io.github.mkliszczun.fridge.mealplan.PlannedMealReservation;

import java.util.List;
import java.util.UUID;

public interface PlannedMealService {
    PlannedMeal create(UUID fridgeId, UUID userId, PlannedMealCreateRequest request);
    List<PlannedMeal> list(UUID fridgeId, UUID userId);
    PlannedMeal get(UUID fridgeId, UUID plannedMealId, UUID userId);
    PlannedMeal update(UUID fridgeId, UUID plannedMealId, UUID userId, PlannedMealUpdateRequest request);
    void delete(UUID fridgeId, UUID plannedMealId, UUID userId);
    PlannedMealReservation createReservation(UUID fridgeId, UUID plannedMealId, UUID userId,
                                             PlannedMealReservationRequest request);
    PlannedMealReservation updateReservation(UUID fridgeId, UUID plannedMealId, UUID reservationId,
                                             UUID userId, PlannedMealReservationUpdateRequest request);
    void deleteReservation(UUID fridgeId, UUID plannedMealId, UUID reservationId, UUID userId);
}
