package com.example.demo.controller;

import com.example.demo.dto.FridgeCreateRequest;
import com.example.demo.dto.FridgeResponse;
import com.example.demo.fridge.Fridge;
import com.example.demo.service.FridgeService;
import io.jsonwebtoken.Jwt;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/fridges")
public class FridgesController {
    private final FridgeService fridgeService;

    public FridgesController(FridgeService fridgeService) {
        this.fridgeService = fridgeService;
    }

    // --- DTOs ---
    public record CreateFridgeRequest(@NotBlank String name) {}
    public record FridgeResponse(UUID id, String name) {}

    @PostMapping
    public ResponseEntity<FridgeResponse> createFridge(
            @Valid @RequestBody CreateFridgeRequest req,
            Authentication authentication
    ) {
        UUID currentUserId = extractUserId(authentication);

        Fridge fridge = fridgeService.createFridge(req.name(), currentUserId);

        // 201 + body {id, name}; Location nie jest asercją w teście, ale jest mile widziany.
        URI location = URI.create("/api/fridges/" + fridge.getId());
        return ResponseEntity
                .created(location)
                .body(new FridgeResponse(fridge.getId(), fridge.getName()));
    }

    // --- helpers ---
    private UUID extractUserId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Object sub = jwtAuth.getToken().getClaims().getOrDefault("sub",
                    jwtAuth.getToken().getClaims().get("user_id"));
            if (sub instanceof String s) {
                try {
                    return UUID.fromString(s);
                } catch (IllegalArgumentException ignored) {
                    // wpadniemy niżej do błędu 401
                }
            }
        }
        // (opcjonalnie) możesz dodać inne ścieżki np. principal z polem id
        throw new ResponseStatusException(UNAUTHORIZED, "Cannot resolve current user id from token");
    }
}// todo - zasadniczo to to do wypierniczenia, trzeba napisać kontrolery, chat zgubił kontekst, bredzi, trzeba pisać samemu (pod test e2e)
