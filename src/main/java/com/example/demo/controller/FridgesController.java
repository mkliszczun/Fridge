package com.example.demo.controller;

import com.example.demo.dto.FridgeCreateRequest;
import com.example.demo.dto.FridgeResponse;
import com.example.demo.enums.FridgeRole;
import com.example.demo.fridge.Fridge;
import com.example.demo.security.AppUserDetails;
import com.example.demo.service.FridgeService;
import io.jsonwebtoken.Jwt;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
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
    @ResponseStatus(HttpStatus.CREATED)
    public FridgeResponse create(@Valid @RequestBody FridgeCreateRequest req, @AuthenticationPrincipal AppUserDetails user) {
        var userId = user.getId();
        Fridge fridge = fridgeService.createFridge(req.name(), userId);
        return toResponse(fridge, FridgeRole.OWNER, 1);
    }


    private FridgeResponse toResponse(Fridge f, FridgeRole role, Integer members) {
        return new FridgeResponse(f.getId(), f.getName());
    }
}// todo - zasadniczo to to do wypierniczenia, trzeba napisać kontrolery, chat zgubił kontekst, bredzi, trzeba pisać samemu (pod test e2e)
