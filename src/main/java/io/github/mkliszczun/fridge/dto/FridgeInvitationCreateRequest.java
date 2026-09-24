package io.github.mkliszczun.fridge.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record FridgeInvitationCreateRequest(@NotBlank @Email @Size(max = 254) String email) {
    public FridgeInvitationCreateRequest {
        if (email != null) email = email.trim().toLowerCase(Locale.ROOT);
    }
}
