package io.github.mkliszczun.fridge.repository;

import io.github.mkliszczun.fridge.mealplan.PlannedMealIngredient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PlannedMealIngredientRepository
        extends JpaRepository<PlannedMealIngredient, UUID> {
}
