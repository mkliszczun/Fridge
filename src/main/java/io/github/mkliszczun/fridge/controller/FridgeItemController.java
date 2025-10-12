package io.github.mkliszczun.fridge.controller;

import io.github.mkliszczun.fridge.dto.FridgeItemCreateRequest;
import io.github.mkliszczun.fridge.dto.FridgeItemResponse;
import io.github.mkliszczun.fridge.dto.UpdateAmountRequest;
import io.github.mkliszczun.fridge.fridge.FridgeItem;
import io.github.mkliszczun.fridge.repository.FridgeItemRepository;
import io.github.mkliszczun.fridge.security.AppUserDetails;
import io.github.mkliszczun.fridge.service.FridgeItemService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/fridge-items")
public class FridgeItemController {

    private final FridgeItemService itemService;
    private final FridgeItemRepository itemRepository;

    public FridgeItemController(FridgeItemService itemService,
                                FridgeItemRepository itemRepository) {
        this.itemService = itemService;
        this.itemRepository = itemRepository;

    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FridgeItemResponse create(@Valid @RequestBody FridgeItemCreateRequest req,
                                     @AuthenticationPrincipal AppUserDetails currentUser) {
        var userId = currentUser.getId();
        var item = itemService.createItem(
                req.fridgeId(),
                userId,
                req.productId(),
                req.customName(),
                req.amount(),
                req.unit(),
                req.bestBeforeDate(),
                req.openDate()
        );
        return toResponse(item);
    }

    @GetMapping("/{fridgeId}")
    public List<FridgeItemResponse> list(@PathVariable UUID fridgeId,
                                         @RequestParam(required = false, defaultValue = "false") boolean expiringSoon,
                                         @AuthenticationPrincipal AppUserDetails currentUser) {
        var userId = currentUser.getId();
        return itemService.list(fridgeId, userId, expiringSoon).stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/item/{itemId}")
    public FridgeItemResponse get(@PathVariable UUID itemId,
                                  @AuthenticationPrincipal AppUserDetails currentUser) {
        var userId = currentUser.getId();
        var item = itemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Item not found"));
        itemService.list(item.getFridge().getId(), userId, false);
        return toResponse(item);
    }

    @PatchMapping("/{itemId}/amount")
    public FridgeItemResponse updateAmount(@PathVariable UUID itemId,
                                           @Valid @RequestBody UpdateAmountRequest req,
                                           @AuthenticationPrincipal AppUserDetails currentUser) {
        var userId = currentUser.getId();
        var updated = itemService.updateAmount(itemId, userId, req.amount());
        return toResponse(updated);
    }

    @PostMapping("/{itemId}/open")
    public FridgeItemResponse open(@PathVariable UUID itemId,
                                   @RequestParam(required = false) LocalDate openDate,
                                   @AuthenticationPrincipal AppUserDetails currentUser) {
        var userId = currentUser.getId();
        var updated = itemService.openItem(itemId, userId, openDate);
        return toResponse(updated);
    }

    @PostMapping("/{itemId}/consume")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void consume(@PathVariable UUID itemId,
                        @AuthenticationPrincipal AppUserDetails currentUser) {
        var userId = currentUser.getId();
        itemService.consume(itemId, userId);
    }

    @PostMapping("/{itemId}/discard")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void discard(@PathVariable UUID itemId,
                        @AuthenticationPrincipal AppUserDetails currentUser) {
        var userId = currentUser.getId();
        itemService.discard(itemId, userId);
    }

    // --- mapper ---
    private FridgeItemResponse toResponse(FridgeItem i) {
        var name = (i.getProduct() != null) ? i.getProduct().getName() : i.getCustomName();
        return new FridgeItemResponse(
                i.getId(),
                i.getFridge().getId(),
                i.getProduct() != null ? i.getProduct().getId() : null,
                name,
                i.getAmount(),
                i.getUnit(),
                i.getBestBeforeDate(),
                i.getOpenDate(),
                i.getEffectiveExpireAt(),
                i.getState()
        );
    }
}