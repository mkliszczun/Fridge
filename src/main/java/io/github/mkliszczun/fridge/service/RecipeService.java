package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.dto.RecipeRequest;
import io.github.mkliszczun.fridge.recipe.Recipe;

import java.util.List;
import java.util.UUID;

public interface RecipeService {
    Recipe create(UUID ownerUserId, RecipeRequest request);
    List<Recipe> list(UUID ownerUserId);
    Recipe get(UUID recipeId, UUID ownerUserId);
    Recipe update(UUID recipeId, UUID ownerUserId, RecipeRequest request);
    void delete(UUID recipeId, UUID ownerUserId);
}
