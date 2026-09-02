package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.dto.AiShoppingListGenerateRequest;
import io.github.mkliszczun.fridge.dto.AiShoppingListProposalResponse;

import java.util.UUID;

public interface AiShoppingListService {

    AiShoppingListProposalResponse generate(UUID fridgeId, UUID userId,
                                            AiShoppingListGenerateRequest request);
}
