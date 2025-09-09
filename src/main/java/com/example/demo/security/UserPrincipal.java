package com.example.demo.security;

import java.util.UUID;

public record UserPrincipal(UUID id, String username) {
}
