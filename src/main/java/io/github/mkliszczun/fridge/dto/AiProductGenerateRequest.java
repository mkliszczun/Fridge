package io.github.mkliszczun.fridge.dto;

import io.github.mkliszczun.fridge.enums.ProductType;
import io.github.mkliszczun.fridge.enums.Unit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record AiProductGenerateRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 32) String ean,
        @Size(max = 255) String brand,
        ProductType productType,
        Unit defaultUnit,
        @PositiveOrZero @Max(3650) Integer defaultExpirationDays,
        @PositiveOrZero @Max(3650) Integer shelfLifeAfterOpeningDays,
        @Valid OffData offData) {

    public record OffData(@Size(max = 255) String productName,
                          @Size(max = 255) String brands,
                          @Size(max = 40) List<@NotBlank @Size(max = 120) String> categoriesTags) {}
}
