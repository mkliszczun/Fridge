package io.github.mkliszczun.fridge.controller;

import io.github.mkliszczun.fridge.dto.RecipeIngredientResponse;
import io.github.mkliszczun.fridge.dto.RecipeRequest;
import io.github.mkliszczun.fridge.dto.RecipeResponse;
import io.github.mkliszczun.fridge.recipe.Recipe;
import io.github.mkliszczun.fridge.recipe.RecipeIngredient;
import io.github.mkliszczun.fridge.security.AppUserDetails;
import io.github.mkliszczun.fridge.service.RecipeService;
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
@RequestMapping("/api/recipes")
public class RecipesController {

    private final RecipeService service;

    public RecipesController(RecipeService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RecipeResponse create(@Valid @RequestBody RecipeRequest request,
                                 @AuthenticationPrincipal AppUserDetails user) {
        return toResponse(service.create(user.getId(), request));
    }

    @GetMapping
    public List<RecipeResponse> list(@AuthenticationPrincipal AppUserDetails user) {
        return service.list(user.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{recipeId}")
    public RecipeResponse get(@PathVariable UUID recipeId,
                              @AuthenticationPrincipal AppUserDetails user) {
        return toResponse(service.get(recipeId, user.getId()));
    }

    @PutMapping("/{recipeId}")
    public RecipeResponse update(@PathVariable UUID recipeId,
                                 @Valid @RequestBody RecipeRequest request,
                                 @AuthenticationPrincipal AppUserDetails user) {
        return toResponse(service.update(recipeId, user.getId(), request));
    }

    @DeleteMapping("/{recipeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID recipeId,
                       @AuthenticationPrincipal AppUserDetails user) {
        service.delete(recipeId, user.getId());
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
