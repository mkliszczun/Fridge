package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.dto.ShoppingListImportRequest;
import io.github.mkliszczun.fridge.exception.ConflictException;
import io.github.mkliszczun.fridge.exception.NotFoundException;
import io.github.mkliszczun.fridge.fridge.Fridge;
import io.github.mkliszczun.fridge.mealplan.PlannedMealIngredient;
import io.github.mkliszczun.fridge.repository.PlannedMealIngredientRepository;
import io.github.mkliszczun.fridge.repository.ShoppingListItemRepository;
import io.github.mkliszczun.fridge.shoppinglist.ShoppingListItem;
import io.github.mkliszczun.fridge.shoppinglist.ShoppingListItemSource;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ShoppingListServiceImpl implements ShoppingListService {

    private final ShoppingListItemRepository itemRepository;
    private final PlannedMealIngredientRepository ingredientRepository;
    private final FridgeService fridgeService;

    public ShoppingListServiceImpl(ShoppingListItemRepository itemRepository,
                                   PlannedMealIngredientRepository ingredientRepository,
                                   FridgeService fridgeService) {
        this.itemRepository = itemRepository;
        this.ingredientRepository = ingredientRepository;
        this.fridgeService = fridgeService;
    }

    @Override
    @Transactional
    public List<ShoppingListItem> list(UUID fridgeId, UUID userId) {
        fridgeService.requireMembership(fridgeId, userId);
        return itemRepository.findAllByFridgeIdOrderByCheckedAscCreatedAtAsc(fridgeId);
    }

    @Override
    @Transactional
    public ShoppingListItem addItem(UUID fridgeId, UUID userId, String name,
                                    BigDecimal amount, String unit) {
        Fridge fridge = fridgeService.requireMembership(fridgeId, userId);
        List<ShoppingListItem> items = new ArrayList<>(
                itemRepository.findAllByFridgeIdOrderByCheckedAscCreatedAtAsc(fridgeId));
        boolean quantified = amount != null;
        ShoppingListItem item = findMergeTarget(items, name, unit, quantified);
        if (item == null) {
            item = newItem(fridge, name, unit, quantified);
        }
        if (quantified) {
            BigDecimal current = item.getManualAmount() == null
                    ? BigDecimal.ZERO
                    : item.getManualAmount();
            item.setManualAmount(cleanAmount(current.add(amount)));
        }
        return itemRepository.save(item);
    }

    @Override
    @Transactional
    public List<ShoppingListItem> importProposal(
            UUID fridgeId, UUID userId, ShoppingListImportRequest request) {
        Fridge fridge = fridgeService.requireMembership(fridgeId, userId);
        Map<UUID, PlannedMealIngredient> ingredients = validateSources(fridgeId, request);
        List<ShoppingListItem> items = new ArrayList<>(
                itemRepository.findAllByFridgeIdOrderByCheckedAscCreatedAtAsc(fridgeId));
        Set<UUID> importedIds = importedIngredientIds(items);
        Set<ShoppingListItem> changedItems = new LinkedHashSet<>();

        for (ShoppingListImportRequest.Item requestedItem : request.items()) {
            boolean quantified = quantified(requestedItem);
            List<ShoppingListImportRequest.Source> newSources = requestedItem.sources().stream()
                    .filter(source -> !importedIds.contains(source.plannedMealIngredientId()))
                    .toList();
            if (newSources.isEmpty()) {
                continue;
            }

            ShoppingListItem item = findMergeTarget(
                    items, requestedItem.name(), requestedItem.unit(), quantified);
            if (item == null) {
                item = newItem(fridge, requestedItem.name(), requestedItem.unit(), quantified);
                items.add(item);
            }

            for (ShoppingListImportRequest.Source requestedSource : newSources) {
                UUID ingredientId = requestedSource.plannedMealIngredientId();
                ShoppingListItemSource source = new ShoppingListItemSource();
                source.setPlannedMealIngredientId(ingredients.get(ingredientId).getId());
                source.setContributionAmount(requestedSource.amount() == null
                        ? null
                        : cleanAmount(requestedSource.amount()));
                item.addSource(source);
                importedIds.add(ingredientId);
            }
            changedItems.add(item);
        }

        if (!changedItems.isEmpty()) {
            itemRepository.saveAll(changedItems);
        }
        return itemRepository.findAllByFridgeIdOrderByCheckedAscCreatedAtAsc(fridgeId);
    }

    @Override
    @Transactional
    public ShoppingListItem setChecked(
            UUID fridgeId, UUID itemId, UUID userId, boolean checked) {
        fridgeService.requireMembership(fridgeId, userId);
        ShoppingListItem item = findItem(fridgeId, itemId);
        item.setChecked(checked);
        return itemRepository.save(item);
    }

    @Override
    @Transactional
    public void deleteItem(UUID fridgeId, UUID itemId, UUID userId) {
        fridgeService.requireMembership(fridgeId, userId);
        itemRepository.delete(findItem(fridgeId, itemId));
    }

    @Override
    @Transactional
    public void deleteCheckedItems(UUID fridgeId, UUID userId) {
        fridgeService.requireMembership(fridgeId, userId);
        List<ShoppingListItem> checkedItems = itemRepository
                .findAllByFridgeIdOrderByCheckedAscCreatedAtAsc(fridgeId)
                .stream()
                .filter(ShoppingListItem::isChecked)
                .toList();
        itemRepository.deleteAll(checkedItems);
    }

    private Map<UUID, PlannedMealIngredient> validateSources(
            UUID fridgeId, ShoppingListImportRequest request) {
        Set<UUID> requestedIds = new LinkedHashSet<>();
        int sourceCount = 0;
        for (ShoppingListImportRequest.Item item : request.items()) {
            for (ShoppingListImportRequest.Source source : item.sources()) {
                sourceCount++;
                requestedIds.add(source.plannedMealIngredientId());
            }
        }
        if (requestedIds.size() != sourceCount) {
            throw new ConflictException("Shopping list sources must be unique");
        }

        Map<UUID, PlannedMealIngredient> ingredients = new HashMap<>();
        ingredientRepository.findAllById(requestedIds)
                .forEach(ingredient -> ingredients.put(ingredient.getId(), ingredient));
        boolean allBelongToFridge = ingredients.size() == requestedIds.size()
                && ingredients.values().stream().allMatch(ingredient ->
                ingredient.getPlannedMeal().getFridge().getId().equals(fridgeId));
        if (!allBelongToFridge) {
            throw new NotFoundException("Planned meal ingredient not found");
        }
        return ingredients;
    }

    private boolean quantified(ShoppingListImportRequest.Item item) {
        long quantifiedSources = item.sources().stream()
                .filter(source -> source.amount() != null)
                .count();
        if (quantifiedSources != 0 && quantifiedSources != item.sources().size()) {
            throw new ConflictException(
                    "Shopping list item sources must either all have amounts or none");
        }
        return quantifiedSources > 0;
    }

    private Set<UUID> importedIngredientIds(List<ShoppingListItem> items) {
        Set<UUID> ids = new HashSet<>();
        items.forEach(item -> item.getSources().forEach(
                source -> ids.add(source.getPlannedMealIngredientId())));
        return ids;
    }

    private ShoppingListItem findMergeTarget(
            List<ShoppingListItem> items, String name, String unit, boolean quantified) {
        String normalizedName = normalize(name);
        String normalizedUnit = normalize(unit);
        return items.stream()
                .filter(item -> !item.isChecked())
                .filter(item -> item.isQuantified() == quantified)
                .filter(item -> normalize(item.getName()).equals(normalizedName))
                .filter(item -> normalize(item.getUnit()).equals(normalizedUnit))
                .findFirst()
                .orElse(null);
    }

    private ShoppingListItem newItem(
            Fridge fridge, String name, String unit, boolean quantified) {
        ShoppingListItem item = new ShoppingListItem();
        item.setFridge(fridge);
        item.setName(name.trim());
        item.setUnit(unit == null || unit.isBlank() ? null : unit.trim());
        item.setQuantified(quantified);
        item.setChecked(false);
        return item;
    }

    private ShoppingListItem findItem(UUID fridgeId, UUID itemId) {
        return itemRepository.findByIdAndFridgeId(itemId, fridgeId)
                .orElseThrow(() -> new NotFoundException("Shopping list item not found"));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private BigDecimal cleanAmount(BigDecimal amount) {
        BigDecimal stripped = amount.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}
