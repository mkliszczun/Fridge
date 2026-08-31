package io.github.mkliszczun.fridge.controller;

import io.github.mkliszczun.fridge.dto.AiRecipeGenerateRequest;
import io.github.mkliszczun.fridge.dto.RecipeRequest;
import io.github.mkliszczun.fridge.service.AiRecipeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/recipes")
public class AiRecipesController {

    private final AiRecipeService service;

    public AiRecipesController(AiRecipeService service) {
        this.service = service;
    }

    @PostMapping("/generate")
    public RecipeRequest generate(@Valid @RequestBody AiRecipeGenerateRequest request) {
        return service.generate(request);
    }
}
