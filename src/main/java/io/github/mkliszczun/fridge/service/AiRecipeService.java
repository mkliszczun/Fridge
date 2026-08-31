package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.dto.AiRecipeGenerateRequest;
import io.github.mkliszczun.fridge.dto.RecipeRequest;

public interface AiRecipeService {
    RecipeRequest generate(AiRecipeGenerateRequest request);

    RecipeRequest generate(AiRecipeGenerateRequest request,
                           RecipeRequest previousProposal,
                           String feedback);
}
