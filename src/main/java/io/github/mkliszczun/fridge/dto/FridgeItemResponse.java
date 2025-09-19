package io.github.mkliszczun.fridge.dto;

import io.github.mkliszczun.fridge.enums.ItemState;
import io.github.mkliszczun.fridge.enums.Unit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record FridgeItemResponse(
        UUID id,
        UUID fridgeId,
        UUID productId,
        String name,
        BigDecimal amount,
        Unit unit,
        LocalDate bestBeforeDate,
        LocalDate openDate,
        LocalDate effectiveExpireAt,
        ItemState state
) {}
