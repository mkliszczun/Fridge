package io.github.mkliszczun.fridge.dto;

import io.github.mkliszczun.fridge.enums.ProductType;

public record DefaultExpirationResponse(ProductType productType,
                                        Integer defaultExpirationDays,
                                        Integer expirationDaysAfterOpening) {
}
