package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.enums.Unit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ShoppingListFridgeItemCandidate(
        UUID id,
        String name,
        BigDecimal availableAmount,
        Unit unit,
        LocalDate effectiveExpireAt
) {
}
