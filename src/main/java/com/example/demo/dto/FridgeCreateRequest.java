package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;

public record FridgeCreateRequest(
        @NotBlank(message = "name must not be blank")
        String name
) {}