package io.github.mkliszczun.fridge.controller;

import io.github.mkliszczun.fridge.dto.PlannedMealRequest;
import io.github.mkliszczun.fridge.dto.PlannedMealResponse;
import io.github.mkliszczun.fridge.dto.RecipeIngredientResponse;
import io.github.mkliszczun.fridge.dto.RecipeResponse;
import io.github.mkliszczun.fridge.mealplan.PlannedMeal;
import io.github.mkliszczun.fridge.recipe.Recipe;
import io.github.mkliszczun.fridge.recipe.RecipeIngredient;
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
                                      @Valid @RequestBody PlannedMealRequest request,
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
                                      @Valid @RequestBody PlannedMealRequest request,
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

    private PlannedMealResponse toResponse(PlannedMeal plannedMeal) {
        return new PlannedMealResponse(
                plannedMeal.getId(),
                plannedMeal.getFridge().getId(),
                toResponse(plannedMeal.getRecipe()),
                plannedMeal.getPlannedDate(),
                plannedMeal.getServings(),
                plannedMeal.getCreatedByUserId(),
                plannedMeal.getCreatedAt(),
                plannedMeal.getUpdatedAt()
        );
    }

    private RecipeResponse toResponse(Recipe recipe) {
        return new RecipeResponse(
                recipe.getId(),
                recipe.getName(),
                recipe.getDescription(),
                recipe.getInstructions(),
                recipe.getServings(),
                recipe.getIngredients().stream().map(this::toResponse).toList(),
                recipe.getCreatedAt(),
                recipe.getUpdatedAt()
        );
    }

    private RecipeIngredientResponse toResponse(RecipeIngredient ingredient) {
        return new RecipeIngredientResponse(
                ingredient.getId(),
                ingredient.getName(),
                ingredient.getAmount(),
                ingredient.getUnit(),
                ingredient.isOptional(),
                ingredient.getNote(),
                ingredient.getPosition()
        );
    }
}
