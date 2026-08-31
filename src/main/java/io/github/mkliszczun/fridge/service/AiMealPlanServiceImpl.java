package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.dto.AiMealPlanGenerateRequest;
import io.github.mkliszczun.fridge.dto.AiMealPlanProposalResponse;
import io.github.mkliszczun.fridge.dto.AiRecipeGenerateRequest;
import io.github.mkliszczun.fridge.dto.RecipeRequest;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AiMealPlanServiceImpl implements AiMealPlanService {

    private final AiRecipeService aiRecipeService;
    private final FridgeService fridgeService;

    public AiMealPlanServiceImpl(AiRecipeService aiRecipeService,
                                 FridgeService fridgeService) {
        this.aiRecipeService = aiRecipeService;
        this.fridgeService = fridgeService;
    }

    @Override
    public AiMealPlanProposalResponse generate(UUID fridgeId, UUID userId,
                                               AiMealPlanGenerateRequest request) {
        fridgeService.requireMembership(fridgeId, userId);

        AiRecipeGenerateRequest recipeRequest = new AiRecipeGenerateRequest(
                request.servings(),
                request.prompt()
        );
        RecipeRequest recipe = aiRecipeService.generate(
                recipeRequest,
                request.previousProposal(),
                request.feedback()
        );
        return new AiMealPlanProposalResponse(fridgeId, request.plannedDate(), recipe);
    }
}
