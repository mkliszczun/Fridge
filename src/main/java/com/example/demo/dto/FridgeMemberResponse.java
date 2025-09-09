package com.example.demo.dto;

import com.example.demo.enums.FridgeRole;
import java.util.UUID;


public record FridgeMemberResponse(
        UUID userId,
        FridgeRole role,
        boolean isDefault
) {}
