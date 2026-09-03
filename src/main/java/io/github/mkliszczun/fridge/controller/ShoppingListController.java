package io.github.mkliszczun.fridge.controller;

import io.github.mkliszczun.fridge.dto.ShoppingListCheckedUpdateRequest;
import io.github.mkliszczun.fridge.dto.ShoppingListImportRequest;
import io.github.mkliszczun.fridge.dto.ShoppingListItemCreateRequest;
import io.github.mkliszczun.fridge.dto.ShoppingListItemResponse;
import io.github.mkliszczun.fridge.dto.ShoppingListResponse;
import io.github.mkliszczun.fridge.security.AppUserDetails;
import io.github.mkliszczun.fridge.service.ShoppingListService;
import io.github.mkliszczun.fridge.shoppinglist.ShoppingListItem;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/fridges/{fridgeId}/shopping-list")
public class ShoppingListController {

    private final ShoppingListService service;

    public ShoppingListController(ShoppingListService service) {
        this.service = service;
    }

    @GetMapping
    public ShoppingListResponse list(
            @PathVariable UUID fridgeId,
            @AuthenticationPrincipal AppUserDetails user) {
        return toResponse(fridgeId, service.list(fridgeId, user.getId()));
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ShoppingListItemResponse addItem(
            @PathVariable UUID fridgeId,
            @Valid @RequestBody ShoppingListItemCreateRequest request,
            @AuthenticationPrincipal AppUserDetails user) {
        return toItemResponse(service.addItem(
                fridgeId, user.getId(), request.name(), request.amount(), request.unit()));
    }

    @PostMapping("/import")
    public ShoppingListResponse importProposal(
            @PathVariable UUID fridgeId,
            @Valid @RequestBody ShoppingListImportRequest request,
            @AuthenticationPrincipal AppUserDetails user) {
        return toResponse(fridgeId, service.importProposal(fridgeId, user.getId(), request));
    }

    @PatchMapping("/items/{itemId}/checked")
    public ShoppingListItemResponse setChecked(
            @PathVariable UUID fridgeId,
            @PathVariable UUID itemId,
            @Valid @RequestBody ShoppingListCheckedUpdateRequest request,
            @AuthenticationPrincipal AppUserDetails user) {
        return toItemResponse(service.setChecked(
                fridgeId, itemId, user.getId(), request.checked()));
    }

    @DeleteMapping("/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteItem(
            @PathVariable UUID fridgeId,
            @PathVariable UUID itemId,
            @AuthenticationPrincipal AppUserDetails user) {
        service.deleteItem(fridgeId, itemId, user.getId());
    }

    @DeleteMapping("/checked-items")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCheckedItems(
            @PathVariable UUID fridgeId,
            @AuthenticationPrincipal AppUserDetails user) {
        service.deleteCheckedItems(fridgeId, user.getId());
    }

    private ShoppingListResponse toResponse(UUID fridgeId, List<ShoppingListItem> items) {
        return new ShoppingListResponse(
                fridgeId,
                items.stream().map(this::toItemResponse).toList()
        );
    }

    private ShoppingListItemResponse toItemResponse(ShoppingListItem item) {
        return new ShoppingListItemResponse(
                item.getId(),
                item.getName(),
                totalAmount(item),
                item.getUnit(),
                item.isChecked(),
                item.getSources().stream()
                        .map(source -> source.getPlannedMealIngredientId())
                        .toList()
        );
    }

    private BigDecimal totalAmount(ShoppingListItem item) {
        if (!item.isQuantified()) {
            return null;
        }
        BigDecimal total = item.getManualAmount() == null
                ? BigDecimal.ZERO
                : item.getManualAmount();
        for (var source : item.getSources()) {
            if (source.getContributionAmount() != null) {
                total = total.add(source.getContributionAmount());
            }
        }
        BigDecimal cleaned = cleanAmount(total);
        if ("PIECE".equalsIgnoreCase(item.getUnit())) {
            return cleaned.setScale(0, RoundingMode.CEILING);
        }
        return cleaned;
    }

    private BigDecimal cleanAmount(BigDecimal amount) {
        BigDecimal stripped = amount.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}
