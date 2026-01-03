package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.entity.DefaultExpirationDays;
import io.github.mkliszczun.fridge.enums.ProductType;
import io.github.mkliszczun.fridge.repository.DefaultExpirationDaysRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@RequiredArgsConstructor
@Service
public class DefaultExpirationDaysServiceImpl implements DefaultExpirationDaysService{

    private final DefaultExpirationDaysRepository repository;

    @Override
    public Optional<DefaultExpirationDays> getByProductType(ProductType type) {
        return repository.findByProductType(type);
    }

    @Transactional
    @Override
    public void updateDefaultExpiration(ProductType type, Integer daysToSet) {
        DefaultExpirationDays entity = repository.findByProductType(type)
                .orElseThrow(() -> new EntityNotFoundException(
                        "DefaultExpirationDays not found for productType=" + type));

        entity.setDefaultExpirationDays(daysToSet);
    }

    @Transactional
    @Override
    public void updateDaysAfterOpeningForType(ProductType type, Integer daysToSet) {
        DefaultExpirationDays entity = repository.findByProductType(type)
                .orElseThrow(() -> new EntityNotFoundException(
                        "DefaultExpirationDays not found for productType=" + type));

        entity.setExpirationDaysAfterOpening(daysToSet);
    }
}



