package io.github.mkliszczun.fridge.controller;

import io.github.mkliszczun.fridge.dto.AiMealPlanGenerateRequest;
import io.github.mkliszczun.fridge.dto.AiMealPlanProposalResponse;
import io.github.mkliszczun.fridge.security.AppUserDetails;
import io.github.mkliszczun.fridge.service.AiMealPlanService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/fridges/{fridgeId}/ai/meal-plans")
public class AiMealPlansController {

    private final AiMealPlanService service;

    public AiMealPlansController(AiMealPlanService service) {
        this.service = service;
    }

    @PostMapping("/generate")
    public AiMealPlanProposalResponse generate(@PathVariable UUID fridgeId,
                                               @Valid @RequestBody AiMealPlanGenerateRequest request,
                                               @AuthenticationPrincipal AppUserDetails user) {
        return service.generate(fridgeId, user.getId(), request);
    }
}
