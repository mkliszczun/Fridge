package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.entity.DefaultExpirationDays;
import io.github.mkliszczun.fridge.fridge.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;
@Component
@RequiredArgsConstructor
public class EffectiveExpirePolicy {

    private final DefaultExpirationDaysService defaultExpirationDaysService;
    public LocalDate computeEffectiveExpireAt(LocalDate bestBeforeDate,
                                              LocalDate openDate,
                                              Product product,
                                              String productTypeFallback,
                                              Integer defaultShelfAfterOpenDays) {
        Optional<DefaultExpirationDays> typeDefaults = Optional.ofNullable(product)
                .map(Product::getProductType)
                .flatMap(defaultExpirationDaysService::getByProductType);

        LocalDate sealedExpireAt = bestBeforeDate;
        if (sealedExpireAt == null) {
            sealedExpireAt = typeDefaults
                    .map(DefaultExpirationDays::getDefaultExpirationDays)
                    .map(days -> LocalDate.now().plusDays(days))
                    .orElse(null);
        }

        if (openDate == null) {
            return sealedExpireAt;
        }

        Integer afterOpenDays = Optional.ofNullable(product)
                .map(Product::getShelfLifeAfterOpeningDays)
                .orElseGet(() -> typeDefaults
                        .map(DefaultExpirationDays::getExpirationDaysAfterOpening)
                        .orElse(defaultShelfAfterOpenDays));

        if (afterOpenDays == null) {
            return sealedExpireAt;
        }

        LocalDate openedExpireAt = openDate.plusDays(afterOpenDays);
        if (sealedExpireAt == null || openedExpireAt.isBefore(sealedExpireAt)) {
            return openedExpireAt;
        }
        return sealedExpireAt;
    }
}
