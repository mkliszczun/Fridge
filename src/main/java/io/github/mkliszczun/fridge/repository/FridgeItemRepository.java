package io.github.mkliszczun.fridge.repository;

import io.github.mkliszczun.fridge.fridge.FridgeItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FridgeItemRepository extends JpaRepository<FridgeItem, UUID> {

    @Query("""
      select i from FridgeItem i
      where i.fridge.id = :fridgeId
        and i.archivedAt is null
      """)
    List<FridgeItem> findActiveByFridge(UUID fridgeId);

    @Query("""
      select i from FridgeItem i
      where i.fridge.id = :fridgeId
        and i.archivedAt is null
        and i.effectiveExpireAt between :from and :to
      order by i.effectiveExpireAt asc
      """)
    List<FridgeItem> findExpiringBetween(UUID fridgeId, LocalDate from, LocalDate to);

    boolean existsByFridgeIdAndArchivedAtIsNull(UUID fridgeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
      select i from FridgeItem i
      where i.id = :itemId
        and i.fridge.id = :fridgeId
        and i.archivedAt is null
      """)
    Optional<FridgeItem> findActiveByIdAndFridgeForUpdate(UUID itemId, UUID fridgeId);
}
