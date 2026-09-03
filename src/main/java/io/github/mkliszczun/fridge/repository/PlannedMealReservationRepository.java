package io.github.mkliszczun.fridge.repository;

import io.github.mkliszczun.fridge.mealplan.PlannedMealReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface PlannedMealReservationRepository extends JpaRepository<PlannedMealReservation, UUID> {

    Optional<PlannedMealReservation> findByIdAndPlannedMealIngredientPlannedMealId(
            UUID id,
            UUID plannedMealId
    );

    boolean existsByPlannedMealIngredientIdAndFridgeItemId(UUID ingredientId, UUID fridgeItemId);

    Optional<PlannedMealReservation> findByPlannedMealIngredientIdAndFridgeItemId(
            UUID ingredientId,
            UUID fridgeItemId
    );

    @Query("""
            select coalesce(sum(reservation.amount), 0)
            from PlannedMealReservation reservation
            where reservation.fridgeItem.id = :fridgeItemId
            """)
    BigDecimal sumReservedAmount(UUID fridgeItemId);
}
