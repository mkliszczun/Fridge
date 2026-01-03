package io.github.mkliszczun.fridge.controller;

import io.github.mkliszczun.fridge.dto.DefaultExpirationResponse;
import io.github.mkliszczun.fridge.dto.DefaultExpirationUpdateRequest;
import io.github.mkliszczun.fridge.entity.DefaultExpirationDays;
import io.github.mkliszczun.fridge.enums.ProductType;
import io.github.mkliszczun.fridge.service.DefaultExpirationDaysService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/admin/expiration")
@RequiredArgsConstructor
public class DefultExpirationDaysController {

    private final DefaultExpirationDaysService service;
    @GetMapping
    List<DefaultExpirationResponse> getExpirationDays(){
        List<DefaultExpirationResponse> result = new java.util.ArrayList<>();

        for (ProductType type : ProductType.values()) {
            service.getByProductType(type).ifPresent(entity ->
                    result.add(new DefaultExpirationResponse(
                            entity.getProductType(),
                            entity.getDefaultExpirationDays(),
                            entity.getExpirationDaysAfterOpening()
                    ))
            );
        }
        return result;
    }


    @PostMapping("/default")
    void updateDefaultExpiration(@RequestParam("productType") ProductType productType,
                                 @RequestBody DefaultExpirationUpdateRequest request) {
        Integer days = request.defaultExpirationDays();
        if (days == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "defaultExpirationDays must be provided for /admin/expiration/default"
            );
        }
        validateNonNegative(days, "defaultExpirationDays");
        service.updateDefaultExpiration(productType, days);
    }

    @PostMapping("/after-opening")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateAfterOpeningExpiration(@RequestParam("productType") ProductType productType,
                                             @RequestBody DefaultExpirationUpdateRequest request) {
        Integer days = request.expirationDaysAfterOpening();
        if (days == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "expirationDaysAfterOpening must be provided for /admin/expiration/after-opening"
            );
        }
        validateNonNegative(days, "expirationDaysAfterOpening");
        service.updateDaysAfterOpeningForType(productType, days);
    }


    private DefaultExpirationResponse toResponse(DefaultExpirationDays entity) {
        return new DefaultExpirationResponse(
                entity.getProductType(),
                entity.getDefaultExpirationDays(),
                entity.getExpirationDaysAfterOpening()
        );
    }

    private void validateNonNegative(Integer value, String fieldName) {
        if (value < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    fieldName + " must be >= 0"
            );
        }
    }
}
