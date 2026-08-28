package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.dto.PlannedMealRequest;
import io.github.mkliszczun.fridge.exception.NotFoundException;
import io.github.mkliszczun.fridge.fridge.Fridge;
import io.github.mkliszczun.fridge.mealplan.PlannedMeal;
import io.github.mkliszczun.fridge.recipe.Recipe;
import io.github.mkliszczun.fridge.repository.PlannedMealRepository;
import io.github.mkliszczun.fridge.repository.RecipeRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class PlannedMealServiceImpl implements PlannedMealService {

    private final PlannedMealRepository repository;
    private final RecipeRepository recipeRepository;
    private final FridgeService fridgeService;

    public PlannedMealServiceImpl(PlannedMealRepository repository,
                                  RecipeRepository recipeRepository,
                                  FridgeService fridgeService) {
        this.repository = repository;
        this.recipeRepository = recipeRepository;
        this.fridgeService = fridgeService;
    }

    @Override
    @Transactional
    public PlannedMeal create(UUID fridgeId, UUID userId, PlannedMealRequest request) {
        Fridge fridge = fridgeService.requireMembership(fridgeId, userId);
        Recipe recipe = findOwnedRecipe(request.recipeId(), userId);

        PlannedMeal plannedMeal = new PlannedMeal();
        plannedMeal.setFridge(fridge);
        plannedMeal.setRecipe(recipe);
        plannedMeal.setPlannedDate(request.plannedDate());
        plannedMeal.setServings(request.servings());
        plannedMeal.setCreatedByUserId(userId);
        return repository.save(plannedMeal);
    }

    @Override
    @Transactional
    public List<PlannedMeal> list(UUID fridgeId, UUID userId) {
        fridgeService.requireMembership(fridgeId, userId);
        return repository.findAllByFridgeIdOrderByPlannedDateAscCreatedAtAsc(fridgeId);
    }

    @Override
    @Transactional
    public PlannedMeal get(UUID fridgeId, UUID plannedMealId, UUID userId) {
        fridgeService.requireMembership(fridgeId, userId);
        return findPlannedMeal(plannedMealId, fridgeId);
    }

    @Override
    @Transactional
    public PlannedMeal update(UUID fridgeId, UUID plannedMealId, UUID userId, PlannedMealRequest request) {
        fridgeService.requireMembership(fridgeId, userId);
        PlannedMeal plannedMeal = findPlannedMeal(plannedMealId, fridgeId);

        if (!plannedMeal.getRecipe().getId().equals(request.recipeId())) {
            plannedMeal.setRecipe(findOwnedRecipe(request.recipeId(), userId));
        }
        plannedMeal.setPlannedDate(request.plannedDate());
        plannedMeal.setServings(request.servings());
        return repository.save(plannedMeal);
    }

    @Override
    @Transactional
    public void delete(UUID fridgeId, UUID plannedMealId, UUID userId) {
        fridgeService.requireMembership(fridgeId, userId);
        repository.delete(findPlannedMeal(plannedMealId, fridgeId));
    }

    private PlannedMeal findPlannedMeal(UUID plannedMealId, UUID fridgeId) {
        return repository.findByIdAndFridgeId(plannedMealId, fridgeId)
                .orElseThrow(() -> new NotFoundException("Planned meal not found"));
    }

    private Recipe findOwnedRecipe(UUID recipeId, UUID userId) {
        return recipeRepository.findByIdAndOwnerUserId(recipeId, userId)
                .orElseThrow(() -> new NotFoundException("Recipe not found"));
    }
}
