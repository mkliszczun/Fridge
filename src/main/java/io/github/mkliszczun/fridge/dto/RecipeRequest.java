package io.github.mkliszczun.fridge.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RecipeRequest(
        @NotBlank @Size(max = 255) String name,
        String description,
        @NotBlank String instructions,
        @NotNull @Positive Integer servings,
        @NotNull @Size(min = 1) List<@Valid RecipeIngredientRequest> ingredients
) {
}
