package com.example.demo.dto;

import com.example.demo.enums.ProductType;
import com.example.demo.enums.Unit;
import jakarta.validation.constraints.NotBlank;

public record AddProductRequest(@NotBlank String name, String ean, ProductType productType, Unit defaultUnit) {
}
