package io.github.mkliszczun.fridge.dto;

import io.github.mkliszczun.fridge.enums.ProductType;
import io.github.mkliszczun.fridge.enums.Unit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AddProductRequest(@NotBlank String name,
                                String ean,
                                ProductType productType,
                                Unit defaultUnit,
                                @PositiveOrZero Integer shelfLifeAfterOpeningDays,
                                @Size(max = 255) String brand) {
}
