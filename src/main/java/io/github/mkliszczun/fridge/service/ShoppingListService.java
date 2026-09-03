package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.dto.ShoppingListImportRequest;
import io.github.mkliszczun.fridge.shoppinglist.ShoppingListItem;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface ShoppingListService {

    List<ShoppingListItem> list(UUID fridgeId, UUID userId);

    ShoppingListItem addItem(UUID fridgeId, UUID userId, String name,
                             BigDecimal amount, String unit);

    List<ShoppingListItem> importProposal(UUID fridgeId, UUID userId,
                                          ShoppingListImportRequest request);

    ShoppingListItem setChecked(UUID fridgeId, UUID itemId, UUID userId, boolean checked);

    void deleteItem(UUID fridgeId, UUID itemId, UUID userId);

    void deleteCheckedItems(UUID fridgeId, UUID userId);
}
