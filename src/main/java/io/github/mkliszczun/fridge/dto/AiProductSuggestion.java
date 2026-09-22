package io.github.mkliszczun.fridge.dto;

import io.github.mkliszczun.fridge.enums.ProductType;
import io.github.mkliszczun.fridge.enums.Unit;
import jakarta.validation.constraints.*;

/** Model output; identity and existing form values are supplied by the server, not the model. */
public record AiProductSuggestion(@Size(max = 255) String brand,
                                 @NotNull ProductType productType,
                                 @NotNull Unit defaultUnit,
                                 @PositiveOrZero @Max(3650) Integer shelfLifeAfterOpeningDays) {}
