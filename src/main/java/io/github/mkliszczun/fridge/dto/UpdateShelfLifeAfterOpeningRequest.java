package io.github.mkliszczun.fridge.dto;

import jakarta.validation.constraints.PositiveOrZero;

public record UpdateShelfLifeAfterOpeningRequest(
        @PositiveOrZero Integer shelfLifeAfterOpeningDays) {
}
