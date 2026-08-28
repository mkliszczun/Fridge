package io.github.mkliszczun.fridge.repository;

import io.github.mkliszczun.fridge.mealplan.PlannedMeal;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

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
    List<PlannedMeal> findAllByFridgeIdOrderByPlannedDateAscCreatedAtAsc(UUID fridgeId);

    @EntityGraph(attributePaths = {
            "ingredients",
            "ingredients.reservations",
            "ingredients.reservations.fridgeItem",
            "ingredients.reservations.fridgeItem.product"
    })
    Optional<PlannedMeal> findByIdAndFridgeId(UUID id, UUID fridgeId);

    @Modifying
    @Query("update PlannedMeal p set p.sourceRecipe = null where p.sourceRecipe.id = :recipeId")
    int clearSourceRecipe(UUID recipeId);
}
