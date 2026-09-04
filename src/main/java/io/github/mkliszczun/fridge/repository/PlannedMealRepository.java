package io.github.mkliszczun.fridge.repository;

import io.github.mkliszczun.fridge.mealplan.PlannedMeal;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlannedMealRepository extends JpaRepository<PlannedMeal, UUID> {

    @EntityGraph(attributePaths = {
            "ingredients",
            "ingredients.reservations",
            "ingredients.reservations.fridgeItem",
            "ingredients.reservations.fridgeItem.product"
    })
    List<PlannedMeal> findAllByFridgeIdAndCompletedAtIsNullOrderByPlannedDateAscCreatedAtAsc(
            UUID fridgeId);

    @EntityGraph(attributePaths = {
            "ingredients",
            "ingredients.reservations",
            "ingredients.reservations.fridgeItem",
            "ingredients.reservations.fridgeItem.product"
    })
    @Query("""
            select meal from PlannedMeal meal
            where meal.id = :id
              and meal.fridge.id = :fridgeId
              and meal.completedAt is null
            """)
    Optional<PlannedMeal> findActiveByIdAndFridgeId(UUID id, UUID fridgeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select meal from PlannedMeal meal
            where meal.id = :id
              and meal.fridge.id = :fridgeId
            """)
    Optional<PlannedMeal> findByIdAndFridgeIdForUpdate(UUID id, UUID fridgeId);

    @Modifying
    @Query("update PlannedMeal p set p.sourceRecipe = null where p.sourceRecipe.id = :recipeId")
    int clearSourceRecipe(UUID recipeId);
}
