package io.github.mkliszczun.fridge.controller;

import io.github.mkliszczun.fridge.dto.FridgeInvitationCreateRequest;
import io.github.mkliszczun.fridge.dto.FridgeInvitationResponse;
import io.github.mkliszczun.fridge.dto.FridgeResponse;
import io.github.mkliszczun.fridge.security.AppUserDetails;
import io.github.mkliszczun.fridge.service.FridgeInvitationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class FridgeInvitationsController {
    private final FridgeInvitationService service;

    public FridgeInvitationsController(FridgeInvitationService service) {
        this.service = service;
    }

    @PostMapping("/api/fridges/{fridgeId}/invitations")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> invite(@PathVariable UUID fridgeId,
                                      @AuthenticationPrincipal AppUserDetails user,
                                      @Valid @RequestBody FridgeInvitationCreateRequest request) {
        service.invite(fridgeId, user.getId(), request.email());
        return Map.of("message", "If this user can be invited, an invitation is available in their account.");
    }

    @GetMapping("/api/fridge-invitations")
    public List<FridgeInvitationResponse> list(@AuthenticationPrincipal AppUserDetails user) {
        return service.list(user.getId());
    }

    @PostMapping("/api/fridge-invitations/{invitationId}/accept")
    public FridgeResponse accept(@PathVariable UUID invitationId, @AuthenticationPrincipal AppUserDetails user) {
        return service.accept(invitationId, user.getId());
    }

    @PostMapping("/api/fridge-invitations/{invitationId}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decline(@PathVariable UUID invitationId, @AuthenticationPrincipal AppUserDetails user) {
        service.decline(invitationId, user.getId());
    }
}
