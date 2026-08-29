package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.dto.AiMealPlanGenerateRequest;
import io.github.mkliszczun.fridge.dto.AiMealPlanProposalResponse;
import io.github.mkliszczun.fridge.dto.RecipeIngredientRequest;
import io.github.mkliszczun.fridge.dto.RecipeRequest;
import io.github.mkliszczun.fridge.exception.InvalidAiResponseException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

@Service
public class AiMealPlanServiceImpl implements AiMealPlanService {

    private static final int MAX_ATTEMPTS = 2;

    private final OpenAiMealPlanClient openAiClient;
    private final FridgeService fridgeService;
    private final Validator validator;

    public AiMealPlanServiceImpl(OpenAiMealPlanClient openAiClient,
                                 FridgeService fridgeService,
                                 Validator validator) {
        this.openAiClient = openAiClient;
        this.fridgeService = fridgeService;
        this.validator = validator;
    }

    @Override
    public AiMealPlanProposalResponse generate(UUID fridgeId, UUID userId,
                                               AiMealPlanGenerateRequest request) {
        fridgeService.requireMembership(fridgeId, userId);

        InvalidAiResponseException lastError = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                RecipeRequest recipe = openAiClient.generate(request);
                validateRecipe(recipe, request.servings());
                return new AiMealPlanProposalResponse(fridgeId, request.plannedDate(), recipe);
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
