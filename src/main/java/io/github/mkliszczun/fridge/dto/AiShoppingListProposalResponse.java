package io.github.mkliszczun.fridge.dto;

import java.util.List;
import java.util.UUID;

public record AiShoppingListProposalResponse(
        UUID fridgeId,
        List<UUID> plannedMealIds,
        List<AiShoppingListItemResponse> items
) {
}
