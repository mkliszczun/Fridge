package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.dto.RecipeIngredientRequest;
import io.github.mkliszczun.fridge.dto.RecipeRequest;
import io.github.mkliszczun.fridge.exception.NotFoundException;
import io.github.mkliszczun.fridge.recipe.Recipe;
import io.github.mkliszczun.fridge.recipe.RecipeIngredient;
import io.github.mkliszczun.fridge.repository.PlannedMealRepository;
import io.github.mkliszczun.fridge.repository.RecipeRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class RecipeServiceImpl implements RecipeService {

    private final RecipeRepository repository;
    private final PlannedMealRepository plannedMealRepository;

    public RecipeServiceImpl(RecipeRepository repository, PlannedMealRepository plannedMealRepository) {
        this.repository = repository;
        this.plannedMealRepository = plannedMealRepository;
    }

    @Override
    @Transactional
    public Recipe create(UUID ownerUserId, RecipeRequest request) {
        Recipe recipe = new Recipe();
        recipe.setOwnerUserId(ownerUserId);
        applyRequest(recipe, request);
        return repository.save(recipe);
    }

    @Override
    public List<Recipe> list(UUID ownerUserId) {
        return repository.findAllByOwnerUserIdOrderByNameAsc(ownerUserId);
    }

    @Override
    public Recipe get(UUID recipeId, UUID ownerUserId) {
        return findOwnedRecipe(recipeId, ownerUserId);
    }

    @Override
    @Transactional
    public Recipe update(UUID recipeId, UUID ownerUserId, RecipeRequest request) {
        Recipe recipe = findOwnedRecipe(recipeId, ownerUserId);
        applyRequest(recipe, request);
        return repository.save(recipe);
    }

    @Override
    @Transactional
    public void delete(UUID recipeId, UUID ownerUserId) {
        Recipe recipe = findOwnedRecipe(recipeId, ownerUserId);
        plannedMealRepository.clearSourceRecipe(recipeId);
        repository.delete(recipe);
    }

    private Recipe findOwnedRecipe(UUID recipeId, UUID ownerUserId) {
        return repository.findByIdAndOwnerUserId(recipeId, ownerUserId)
                .orElseThrow(() -> new NotFoundException("Recipe not found"));
    }

    private void applyRequest(Recipe recipe, RecipeRequest request) {
        recipe.setName(request.name());
        recipe.setDescription(request.description());
        recipe.setInstructions(request.instructions());
        recipe.setServings(request.servings());

        List<RecipeIngredient> ingredients = new ArrayList<>();
        for (int position = 0; position < request.ingredients().size(); position++) {
            ingredients.add(toEntity(request.ingredients().get(position), position));
        }
        recipe.replaceIngredients(ingredients);
    }

    private RecipeIngredient toEntity(RecipeIngredientRequest request, int position) {
        RecipeIngredient ingredient = new RecipeIngredient();
        ingredient.setName(request.name());
        ingredient.setAmount(request.amount());
        ingredient.setUnit(request.unit());
        ingredient.setOptional(request.optional());
        ingredient.setNote(request.note());
        ingredient.setPosition(position);
        return ingredient;
    }
}
