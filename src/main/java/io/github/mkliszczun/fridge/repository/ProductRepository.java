package io.github.mkliszczun.fridge.repository;

import io.github.mkliszczun.fridge.fridge.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    Optional<Product> findByEan(String ean);
    Optional<Product> findFirstByNameIgnoreCase(String name);
    boolean existsByEan(String ean);
}
