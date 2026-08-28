package io.github.mkliszczun.fridge.controller;

import io.github.mkliszczun.fridge.dto.PlannedMealCreateRequest;
import io.github.mkliszczun.fridge.dto.PlannedMealIngredientResponse;
import io.github.mkliszczun.fridge.dto.PlannedMealReservationRequest;
import io.github.mkliszczun.fridge.dto.PlannedMealReservationResponse;
import io.github.mkliszczun.fridge.dto.PlannedMealReservationUpdateRequest;
import io.github.mkliszczun.fridge.dto.PlannedMealResponse;
import io.github.mkliszczun.fridge.dto.PlannedMealUpdateRequest;
import io.github.mkliszczun.fridge.dto.PlannedRecipeResponse;
import io.github.mkliszczun.fridge.mealplan.PlannedMeal;
import io.github.mkliszczun.fridge.mealplan.PlannedMealIngredient;
import io.github.mkliszczun.fridge.mealplan.PlannedMealReservation;
import io.github.mkliszczun.fridge.security.AppUserDetails;
import io.github.mkliszczun.fridge.service.PlannedMealService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/fridges/{fridgeId}/planned-meals")
public class PlannedMealsController {

    private final PlannedMealService service;

    public PlannedMealsController(PlannedMealService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlannedMealResponse create(@PathVariable UUID fridgeId,
                                      @Valid @RequestBody PlannedMealCreateRequest request,
                                      @AuthenticationPrincipal AppUserDetails user) {
        return toResponse(service.create(fridgeId, user.getId(), request));
    }

    @GetMapping
    public List<PlannedMealResponse> list(@PathVariable UUID fridgeId,
                                         @AuthenticationPrincipal AppUserDetails user) {
        return service.list(fridgeId, user.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{plannedMealId}")
    public PlannedMealResponse get(@PathVariable UUID fridgeId,
                                   @PathVariable UUID plannedMealId,
                                   @AuthenticationPrincipal AppUserDetails user) {
        return toResponse(service.get(fridgeId, plannedMealId, user.getId()));
    }

    @PutMapping("/{plannedMealId}")
    public PlannedMealResponse update(@PathVariable UUID fridgeId,
                                      @PathVariable UUID plannedMealId,
                                      @Valid @RequestBody PlannedMealUpdateRequest request,
                                      @AuthenticationPrincipal AppUserDetails user) {
        return toResponse(service.update(fridgeId, plannedMealId, user.getId(), request));
    }

    @DeleteMapping("/{plannedMealId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID fridgeId,
                       @PathVariable UUID plannedMealId,
                       @AuthenticationPrincipal AppUserDetails user) {
        service.delete(fridgeId, plannedMealId, user.getId());
    }

    @PostMapping("/{plannedMealId}/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public PlannedMealReservationResponse createReservation(
            @PathVariable UUID fridgeId,
            @PathVariable UUID plannedMealId,
            @Valid @RequestBody PlannedMealReservationRequest request,
            @AuthenticationPrincipal AppUserDetails user) {
        return toResponse(service.createReservation(fridgeId, plannedMealId, user.getId(), request));
    }

    @PutMapping("/{plannedMealId}/reservations/{reservationId}")
    public PlannedMealReservationResponse updateReservation(
            @PathVariable UUID fridgeId,
            @PathVariable UUID plannedMealId,
            @PathVariable UUID reservationId,
            @Valid @RequestBody PlannedMealReservationUpdateRequest request,
            @AuthenticationPrincipal AppUserDetails user) {
        return toResponse(service.updateReservation(
                fridgeId, plannedMealId, reservationId, user.getId(), request));
    }

    @DeleteMapping("/{plannedMealId}/reservations/{reservationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteReservation(@PathVariable UUID fridgeId,
                                  @PathVariable UUID plannedMealId,
                                  @PathVariable UUID reservationId,
                                  @AuthenticationPrincipal AppUserDetails user) {
        service.deleteReservation(fridgeId, plannedMealId, reservationId, user.getId());
    }

    private PlannedMealResponse toResponse(PlannedMeal plannedMeal) {
        return new PlannedMealResponse(
                plannedMeal.getId(),
                plannedMeal.getFridge().getId(),
                toRecipeResponse(plannedMeal),
                plannedMeal.getPlannedDate(),
                plannedMeal.getServings(),
                plannedMeal.getCreatedByUserId(),
                plannedMeal.getCreatedAt(),
                plannedMeal.getUpdatedAt()
        );
    }

    private PlannedRecipeResponse toRecipeResponse(PlannedMeal plannedMeal) {
        UUID sourceRecipeId = plannedMeal.getSourceRecipe() == null
                ? null
                : plannedMeal.getSourceRecipe().getId();
        return new PlannedRecipeResponse(
                sourceRecipeId,
                plannedMeal.getRecipeName(),
                plannedMeal.getRecipeDescription(),
                plannedMeal.getRecipeInstructions(),
                plannedMeal.getRecipeServings(),
                plannedMeal.getIngredients().stream().map(this::toResponse).toList()
        );
    }

    private PlannedMealIngredientResponse toResponse(PlannedMealIngredient ingredient) {
        return new PlannedMealIngredientResponse(
                ingredient.getId(),
                ingredient.getName(),
                ingredient.getAmount(),
                ingredient.getUnit(),
                ingredient.isOptional(),
                ingredient.getNote(),
                ingredient.getPosition(),
                ingredient.getReservations().stream().map(this::toResponse).toList()
        );
    }

    private PlannedMealReservationResponse toResponse(PlannedMealReservation reservation) {
        var fridgeItem = reservation.getFridgeItem();
        String itemName = fridgeItem.getProduct() == null
                ? fridgeItem.getCustomName()
                : fridgeItem.getProduct().getName();
        return new PlannedMealReservationResponse(
                reservation.getId(),
                reservation.getPlannedMealIngredient().getId(),
                fridgeItem.getId(),
                itemName,
                reservation.getAmount(),
                fridgeItem.getUnit()
        );
    }
}
