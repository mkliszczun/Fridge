package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.dto.AiMealPlanFromRecipesGenerateRequest;
import io.github.mkliszczun.fridge.dto.AiMealPlanFromRecipesProposalResponse;
import io.github.mkliszczun.fridge.dto.AiPlannedMealProposalResponse;
import io.github.mkliszczun.fridge.exception.ConflictException;
import io.github.mkliszczun.fridge.exception.InvalidAiResponseException;
import io.github.mkliszczun.fridge.recipe.Recipe;
import io.github.mkliszczun.fridge.repository.RecipeRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

@Service
public class AiMealPlanFromRecipesServiceImpl implements AiMealPlanFromRecipesService {

    private static final int MAX_ATTEMPTS = 2;

    private final OpenAiMealPlanClient openAiClient;
    private final RecipeRepository recipeRepository;
    private final FridgeService fridgeService;

    public AiMealPlanFromRecipesServiceImpl(OpenAiMealPlanClient openAiClient,
                                            RecipeRepository recipeRepository,
                                            FridgeService fridgeService) {
        this.openAiClient = openAiClient;
        this.recipeRepository = recipeRepository;
        this.fridgeService = fridgeService;
    }

    @Override
    public AiMealPlanFromRecipesProposalResponse generate(
            UUID fridgeId,
            UUID userId,
            AiMealPlanFromRecipesGenerateRequest request) {
        fridgeService.requireMembership(fridgeId, userId);

        List<Recipe> recipes = recipeRepository.findAllByOwnerUserIdOrderByNameAsc(userId);
        if (recipes.isEmpty()) {
            throw new ConflictException("No recipes available for meal planning");
        }

        Map<UUID, Recipe> recipesById = new LinkedHashMap<>();
        recipes.forEach(recipe -> recipesById.put(recipe.getId(), recipe));
        List<MealPlanRecipeCandidate> candidates = recipes.stream()
                .map(this::toCandidate)
                .toList();

        InvalidAiResponseException lastError = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                List<UUID> recipeIds = openAiClient.generate(request, candidates);
                validateSelection(recipeIds, request.days(), recipesById.keySet());
                return toResponse(fridgeId, request, recipeIds, recipesById);
            } catch (InvalidAiResponseException ex) {
                lastError = ex;
            }
        }
        throw lastError;
    }

    private MealPlanRecipeCandidate toCandidate(Recipe recipe) {
        return new MealPlanRecipeCandidate(
                recipe.getId(),
                recipe.getName(),
                recipe.getDescription(),
                recipe.getIngredients().stream()
                        .map(ingredient -> ingredient.getName())
                        .toList()
        );
    }

    private void validateSelection(List<UUID> recipeIds, int days, Set<UUID> availableRecipeIds) {
        boolean invalidCount = recipeIds == null || recipeIds.size() != days;
        if (invalidCount) {
            throw new InvalidAiResponseException("AI returned an invalid meal plan");
        }
        boolean unknownRecipe = recipeIds.stream().anyMatch(id -> !availableRecipeIds.contains(id));
        boolean unnecessaryDuplicates = availableRecipeIds.size() >= days
                && recipeIds.stream().distinct().count() != recipeIds.size();
        if (unknownRecipe || unnecessaryDuplicates) {
            throw new InvalidAiResponseException("AI returned an invalid meal plan");
        }
    }

    private AiMealPlanFromRecipesProposalResponse toResponse(
            UUID fridgeId,
            AiMealPlanFromRecipesGenerateRequest request,
            List<UUID> recipeIds,
            Map<UUID, Recipe> recipesById) {
        List<AiPlannedMealProposalResponse> meals = IntStream.range(0, recipeIds.size())
                .mapToObj(index -> {
                    Recipe recipe = recipesById.get(recipeIds.get(index));
                    return new AiPlannedMealProposalResponse(
                            request.startDate().plusDays(index),
                            request.servings(),
                            recipe.getId(),
                            recipe.getName()
                    );
                })
                .toList();
        return new AiMealPlanFromRecipesProposalResponse(
                fridgeId,
                request.startDate(),
                request.days(),
                request.servings(),
                meals
        );
    }
}
