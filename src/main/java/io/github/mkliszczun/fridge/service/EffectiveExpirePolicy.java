package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.fridge.Product;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;
@Component
public class EffectiveExpirePolicy {
    public LocalDate computeEffectiveExpireAt(LocalDate bestBeforeDate,
                                              LocalDate openDate,
                                              Product product,
                                              String productTypeFallback,
                                              Integer defaultShelfAfterOpenDays) {
        // 1) If open and 'after open' available start from openDate
        Integer afterOpen = Optional.ofNullable(product)
                .map(Product::getShelfLifeAfterOpeningDays)
                .orElse(defaultShelfAfterOpenDays);

        if (openDate != null && afterOpen != null) {
            return openDate.plusDays(afterOpen);
        }
        // 2) In the other case start from bestBeforeDate - or null
        return bestBeforeDate;
    }
}
