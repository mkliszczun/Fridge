package io.github.mkliszczun.fridge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record RegisterRequest(@NotBlank @Email @Size(max = 64) String login,
                              @NotBlank @Size(min = 8, max = 72) String password) {
}
