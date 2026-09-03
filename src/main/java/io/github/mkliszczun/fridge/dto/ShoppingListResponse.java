package io.github.mkliszczun.fridge.dto;

import java.util.List;
import java.util.UUID;

public record ShoppingListResponse(
        UUID fridgeId,
        List<ShoppingListItemResponse> items
) {
}
