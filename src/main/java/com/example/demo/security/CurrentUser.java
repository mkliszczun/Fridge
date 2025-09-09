package com.example.demo.security;

import java.security.Principal;
import java.util.UUID;

public record CurrentUser(UUID id, String username) implements Principal {
    @Override public String getName() { return username; }
}
