package com.example.demo.service;

import com.example.demo.fridge.Product;
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
        // 1) Jeśli otwarte i mamy „po otwarciu” — licz od openDate
        Integer afterOpen = Optional.ofNullable(product)
                .map(Product::getShelfLifeAfterOpeningDays)
                .orElse(defaultShelfAfterOpenDays);

        if (openDate != null && afterOpen != null) {
            return openDate.plusDays(afterOpen);
        }
        // 2) W przeciwnym razie bierz bestBeforeDate (może być null)
        return bestBeforeDate;
    }
}
