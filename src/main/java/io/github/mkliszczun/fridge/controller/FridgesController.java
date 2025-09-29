package io.github.mkliszczun.fridge.controller;

import io.github.mkliszczun.fridge.dto.FridgeCreateRequest;
import io.github.mkliszczun.fridge.enums.FridgeRole;
import io.github.mkliszczun.fridge.fridge.Fridge;
import io.github.mkliszczun.fridge.security.AppUserDetails;
import io.github.mkliszczun.fridge.service.FridgeService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
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
    public record FridgeResponse(UUID id, String name) {} //Todo - remove these records

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FridgeResponse create(@Valid @RequestBody FridgeCreateRequest req, @AuthenticationPrincipal AppUserDetails user) {
        var userId = user.getId();
        Fridge fridge = fridgeService.createFridge(req.name(), userId);
        return toResponse(fridge, FridgeRole.OWNER, 1);
    }

    @GetMapping
    List<FridgeResponse> getAllFridges(@AuthenticationPrincipal AppUserDetails user){
        List<Fridge> fridges = fridgeService.listMyFridges(user.getId());
        List<FridgeResponse> res = new ArrayList<>();

        for (Fridge fridge : fridges){
            res.add(toResponse(fridge, FridgeRole.OWNER, 1));
        }
        return res;
    }


    private FridgeResponse toResponse(Fridge f, FridgeRole role, Integer members) {
        return new FridgeResponse(f.getId(), f.getName());
    }
}