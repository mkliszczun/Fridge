package io.github.mkliszczun.fridge.dto;

import io.github.mkliszczun.fridge.enums.Unit;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record FridgeItemCreateRequest(
        @NotNull UUID fridgeId,
        UUID productId,
        String customName,
        @NotNull @Positive BigDecimal amount,
        @NotNull Unit unit,
        LocalDate bestBeforeDate,
        LocalDate openDate
) {}
