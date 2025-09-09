package com.example.demo.service;

import com.example.demo.enums.ItemState;
import com.example.demo.enums.Unit;
import com.example.demo.fridge.FridgeItem;
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
                                 UUID productId,       // może być null
                                 String customName,    // jeśli brak productId
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
