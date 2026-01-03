package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.entity.DefaultExpirationDays;
import io.github.mkliszczun.fridge.enums.ProductType;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public interface DefaultExpirationDaysService {
    Optional<DefaultExpirationDays> getByProductType(ProductType type);
    public void updateDefaultExpiration(ProductType type, Integer daysToSet);
    public void updateDaysAfterOpeningForType(ProductType type, Integer daysToSet);
}
