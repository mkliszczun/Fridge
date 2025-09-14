package com.example.demo.dto;

import com.example.demo.enums.ProductType;
import com.example.demo.enums.Unit;

import java.util.UUID;

public record AddProductResponse(UUID id, ProductType productType, String name, String brand, Unit defaultUnit) {
}
