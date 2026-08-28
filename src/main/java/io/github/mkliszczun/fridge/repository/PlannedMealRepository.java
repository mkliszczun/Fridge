package io.github.mkliszczun.fridge.repository;

import io.github.mkliszczun.fridge.mealplan.PlannedMeal;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlannedMealRepository extends JpaRepository<PlannedMeal, UUID> {

    @EntityGraph(attributePaths = {"recipe", "recipe.ingredients"})
    List<PlannedMeal> findAllByFridgeIdOrderByPlannedDateAscCreatedAtAsc(UUID fridgeId);

    @EntityGraph(attributePaths = {"recipe", "recipe.ingredients"})
    Optional<PlannedMeal> findByIdAndFridgeId(UUID id, UUID fridgeId);

    boolean existsByRecipeId(UUID recipeId);
}
