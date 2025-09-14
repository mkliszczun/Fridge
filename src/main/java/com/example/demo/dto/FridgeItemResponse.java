package com.example.demo.dto;

import com.example.demo.enums.ItemState;
import com.example.demo.enums.Unit;

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
