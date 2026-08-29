package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.dto.AiMealPlanGenerateRequest;
import io.github.mkliszczun.fridge.dto.AiMealPlanProposalResponse;

import java.util.UUID;

public interface AiMealPlanService {
    AiMealPlanProposalResponse generate(UUID fridgeId, UUID userId, AiMealPlanGenerateRequest request);
}
