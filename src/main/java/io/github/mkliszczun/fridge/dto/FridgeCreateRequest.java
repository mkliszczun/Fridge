package io.github.mkliszczun.fridge.dto;

import jakarta.validation.constraints.NotBlank;

public record FridgeCreateRequest(
        @NotBlank(message = "name must not be blank")
        String name
) {}