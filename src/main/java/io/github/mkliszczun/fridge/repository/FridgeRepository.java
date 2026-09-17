package io.github.mkliszczun.fridge.repository;

import io.github.mkliszczun.fridge.fridge.Fridge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface FridgeRepository extends JpaRepository<Fridge, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Fridge f where f.id = :id")
    Optional<Fridge> findByIdForUpdate(UUID id);
}
