package io.github.mkliszczun.fridge.controller;

import io.github.mkliszczun.fridge.dto.FridgeCreateRequest;
import io.github.mkliszczun.fridge.dto.FridgeResponse;
import io.github.mkliszczun.fridge.fridge.Fridge;
import io.github.mkliszczun.fridge.security.AppUserDetails;
import io.github.mkliszczun.fridge.service.FridgeService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/fridges")
public class FridgesController {
    private final FridgeService fridgeService;

    public FridgesController(FridgeService fridgeService) {
        this.fridgeService = fridgeService;
    }


    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FridgeResponse create(@Valid @RequestBody FridgeCreateRequest req, @AuthenticationPrincipal AppUserDetails user) {
        var userId = user.getId();
        Fridge fridge = fridgeService.createFridge(req.name(), userId);
        return toResponse(fridge);
    }

    @GetMapping
    List<FridgeResponse> getAllFridges(@AuthenticationPrincipal AppUserDetails user){
        List<Fridge> fridges = fridgeService.listMyFridges(user.getId());
        List<FridgeResponse> res = new ArrayList<>();

        for (Fridge fridge : fridges){
            res.add(toResponse(fridge));
        }
        return res;
    }


    private FridgeResponse toResponse(Fridge f) {
        return new FridgeResponse(f.getId(), f.getName());
    }
}
