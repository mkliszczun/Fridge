package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.enums.ItemState;
import io.github.mkliszczun.fridge.enums.Unit;
import io.github.mkliszczun.fridge.fridge.FridgeItem;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public interface FridgeItemService {
    void assertMembership(UUID fridgeId, UUID userId);
    FridgeItem createItem(UUID fridgeId,
                                 UUID currentUserId,
                                 UUID productId,       // may be null
                                 String customName,    // if there's no productId
                                 BigDecimal amount,
                                 Unit unit,
                                 LocalDate bestBeforeDate,
                                 LocalDate openDate);

    FridgeItem openItem(UUID itemId, UUID currentUserId, LocalDate openDate);

    FridgeItem updateAmount(UUID itemId, UUID currentUserId, BigDecimal newAmount);

    void consume(UUID itemId, UUID currentUserId);

    void discard(UUID itemId, UUID currentUserId);

    void archiveWithState(UUID itemId, UUID currentUserId, ItemState state);

    List<FridgeItem> list(UUID fridgeId, UUID currentUserId, Boolean expiringSoon);

}
