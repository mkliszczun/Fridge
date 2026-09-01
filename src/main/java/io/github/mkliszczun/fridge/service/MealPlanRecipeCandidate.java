package io.github.mkliszczun.fridge.service;

import java.util.List;
import java.util.UUID;

public record MealPlanRecipeCandidate(
        UUID id,
        String name,
        String description,
        List<String> ingredients
) {
}
