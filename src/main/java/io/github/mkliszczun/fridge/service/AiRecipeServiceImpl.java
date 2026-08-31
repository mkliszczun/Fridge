package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.dto.AiRecipeGenerateRequest;
import io.github.mkliszczun.fridge.dto.RecipeIngredientRequest;
import io.github.mkliszczun.fridge.dto.RecipeRequest;
import io.github.mkliszczun.fridge.exception.InvalidAiResponseException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class AiRecipeServiceImpl implements AiRecipeService {

    private static final int MAX_ATTEMPTS = 2;

    private final OpenAiRecipeClient openAiClient;
    private final Validator validator;

    public AiRecipeServiceImpl(OpenAiRecipeClient openAiClient, Validator validator) {
        this.openAiClient = openAiClient;
        this.validator = validator;
    }

    @Override
    public RecipeRequest generate(AiRecipeGenerateRequest request) {
        return generate(request, null, null);
    }

    @Override
    public RecipeRequest generate(AiRecipeGenerateRequest request,
                                  RecipeRequest previousProposal,
                                  String feedback) {
        InvalidAiResponseException lastError = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                RecipeRequest recipe = openAiClient.generate(request, previousProposal, feedback);
                validateRecipe(recipe, request.servings());
                return recipe;
            } catch (InvalidAiResponseException ex) {
                lastError = ex;
            }
        }
        throw lastError;
    }

    private void validateRecipe(RecipeRequest recipe, Integer expectedServings) {
        if (recipe == null) {
            throw new InvalidAiResponseException("AI returned an invalid recipe");
        }
        Set<ConstraintViolation<RecipeRequest>> violations = validator.validate(recipe);
        boolean mismatchedServings = !expectedServings.equals(recipe.servings());
        boolean mismatchedIngredientAmount = recipe.ingredients().stream()
                .anyMatch(this::hasMismatchedAmountAndUnit);
        if (!violations.isEmpty() || mismatchedServings || mismatchedIngredientAmount) {
            throw new InvalidAiResponseException("AI returned an invalid recipe");
        }
    }

    private boolean hasMismatchedAmountAndUnit(RecipeIngredientRequest ingredient) {
        return (ingredient.amount() == null) != (ingredient.unit() == null);
    }
}
