package io.github.mkliszczun.fridge.dto;

import jakarta.validation.constraints.NotNull;

public record ShoppingListCheckedUpdateRequest(
        @NotNull Boolean checked
) {
}
