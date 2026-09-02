package io.github.mkliszczun.fridge.controller;

import io.github.mkliszczun.fridge.dto.AiShoppingListGenerateRequest;
import io.github.mkliszczun.fridge.dto.AiShoppingListProposalResponse;
import io.github.mkliszczun.fridge.security.AppUserDetails;
import io.github.mkliszczun.fridge.service.AiShoppingListService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/fridges/{fridgeId}/ai/shopping-lists")
public class AiShoppingListsController {

    private final AiShoppingListService service;

    public AiShoppingListsController(AiShoppingListService service) {
        this.service = service;
    }

    @PostMapping("/generate")
    public AiShoppingListProposalResponse generate(
            @PathVariable UUID fridgeId,
            @Valid @RequestBody AiShoppingListGenerateRequest request,
            @AuthenticationPrincipal AppUserDetails user) {
        return service.generate(fridgeId, user.getId(), request);
    }
}
