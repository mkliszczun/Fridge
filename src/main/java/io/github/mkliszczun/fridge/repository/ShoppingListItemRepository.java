package io.github.mkliszczun.fridge.repository;

import io.github.mkliszczun.fridge.shoppinglist.ShoppingListItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShoppingListItemRepository extends JpaRepository<ShoppingListItem, UUID> {

    @EntityGraph(attributePaths = "sources")
    List<ShoppingListItem> findAllByFridgeIdOrderByCheckedAscCreatedAtAsc(UUID fridgeId);

    @EntityGraph(attributePaths = "sources")
    Optional<ShoppingListItem> findByIdAndFridgeId(UUID id, UUID fridgeId);
}
