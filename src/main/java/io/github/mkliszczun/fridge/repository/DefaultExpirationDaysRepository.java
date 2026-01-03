package io.github.mkliszczun.fridge.repository;

import io.github.mkliszczun.fridge.entity.DefaultExpirationDays;
import io.github.mkliszczun.fridge.enums.ProductType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DefaultExpirationDaysRepository extends JpaRepository<DefaultExpirationDays, UUID> {
    Optional<DefaultExpirationDays> findByProductType(ProductType productType);


}
