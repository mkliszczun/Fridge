package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.dto.*;
import io.github.mkliszczun.fridge.entity.DefaultExpirationDays;
import io.github.mkliszczun.fridge.enums.ProductType;
import io.github.mkliszczun.fridge.exception.InvalidAiResponseException;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.util.Arrays;

@Service
public class AiProductService {
    private final OpenAiProductClient client;
    private final Validator validator;
    private final DefaultExpirationDaysService defaults;

    public AiProductService(OpenAiProductClient client, Validator validator, DefaultExpirationDaysService defaults) {
        this.client = client; this.validator = validator; this.defaults = defaults;
    }

    public AiProductProposalResponse generate(AiProductGenerateRequest request) {
        var categoryDefaults = Arrays.stream(ProductType.values()).flatMap(type -> defaults.getByProductType(type).stream())
                .map(value -> new DefaultExpirationResponse(value.getProductType(), value.getDefaultExpirationDays(),
                        value.getExpirationDaysAfterOpening())).toList();
        InvalidAiResponseException failure = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                AiProductSuggestion suggestion = client.generate(request, categoryDefaults);
                if (suggestion == null || !validator.validate(suggestion).isEmpty()) {
                    throw new InvalidAiResponseException("AI returned invalid product data");
                }
                ProductType type = request.productType() != null ? request.productType() : suggestion.productType();
                String brand = text(request.brand());
                if (brand == null && request.offData() != null) brand = text(request.offData().brands());
                if (brand == null) brand = text(suggestion.brand());
                Integer categoryDays = defaults.getByProductType(type).map(DefaultExpirationDays::getDefaultExpirationDays).orElse(null);
                return new AiProductProposalResponse(request.name().trim(), text(request.ean()), brand, type,
                        request.defaultUnit() != null ? request.defaultUnit() : suggestion.defaultUnit(),
                        request.shelfLifeAfterOpeningDays() != null ? request.shelfLifeAfterOpeningDays() : suggestion.shelfLifeAfterOpeningDays(),
                        categoryDays);
            } catch (InvalidAiResponseException ex) {
                failure = ex;
            }
        }
        throw failure;
    }

    private static String text(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
}
