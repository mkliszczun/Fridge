package io.github.mkliszczun.fridge.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AiRecipeGenerateRequest(
        @NotNull @Positive Integer servings,
        @Size(max = 1000) String guidelines
) {
}
