package io.github.mkliszczun.fridge.security;

import java.util.UUID;

public record UserPrincipal(UUID id, String username) {
}
