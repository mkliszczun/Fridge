package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.dto.AiMealPlanFromRecipesGenerateRequest;
import io.github.mkliszczun.fridge.dto.AiMealPlanFromRecipesProposalResponse;

import java.util.UUID;

public interface AiMealPlanFromRecipesWithFridgeService {

    AiMealPlanFromRecipesProposalResponse generate(
            UUID fridgeId,
            UUID userId,
            AiMealPlanFromRecipesGenerateRequest request);
}
