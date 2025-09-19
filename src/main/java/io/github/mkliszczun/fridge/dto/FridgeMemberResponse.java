package io.github.mkliszczun.fridge.dto;

import io.github.mkliszczun.fridge.enums.FridgeRole;

import java.util.UUID;


public record FridgeMemberResponse(
        UUID userId,
        FridgeRole role,
        boolean isDefault
) {}
