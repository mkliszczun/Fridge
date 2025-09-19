package io.github.mkliszczun.fridge.dto;

import io.github.mkliszczun.fridge.enums.ProductType;
import io.github.mkliszczun.fridge.enums.Unit;

import java.util.UUID;

public record AddProductResponse(UUID id, ProductType productType, String name, String brand, Unit defaultUnit) {
}
