package io.github.mkliszczun.fridge.dto;

import io.github.mkliszczun.fridge.enums.ProductType;
import io.github.mkliszczun.fridge.enums.Unit;
import jakarta.validation.constraints.NotBlank;

public record AddProductRequest(@NotBlank String name, String ean, ProductType productType, Unit defaultUnit) {
}
