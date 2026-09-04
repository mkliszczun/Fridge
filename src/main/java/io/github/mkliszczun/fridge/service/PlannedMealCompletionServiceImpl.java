package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.dto.PlannedMealCompletionResponse;
import io.github.mkliszczun.fridge.dto.PlannedMealCompletionWarningResponse;
import io.github.mkliszczun.fridge.enums.ItemState;
import io.github.mkliszczun.fridge.enums.Unit;
import io.github.mkliszczun.fridge.exception.ConflictException;
import io.github.mkliszczun.fridge.exception.NotFoundException;
import io.github.mkliszczun.fridge.fridge.FridgeItem;
import io.github.mkliszczun.fridge.mealplan.PlannedMeal;
import io.github.mkliszczun.fridge.mealplan.PlannedMealIngredient;
import io.github.mkliszczun.fridge.mealplan.PlannedMealReservation;
import io.github.mkliszczun.fridge.repository.FridgeItemRepository;
import io.github.mkliszczun.fridge.repository.PlannedMealRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class PlannedMealCompletionServiceImpl implements PlannedMealCompletionService {

    private final PlannedMealRepository plannedMealRepository;
    private final FridgeItemRepository fridgeItemRepository;
    private final FridgeService fridgeService;
    private final EffectiveExpirePolicy expirePolicy;

    public PlannedMealCompletionServiceImpl(
            PlannedMealRepository plannedMealRepository,
            FridgeItemRepository fridgeItemRepository,
            FridgeService fridgeService,
            EffectiveExpirePolicy expirePolicy) {
        this.plannedMealRepository = plannedMealRepository;
        this.fridgeItemRepository = fridgeItemRepository;
        this.fridgeService = fridgeService;
        this.expirePolicy = expirePolicy;
    }

    @Override
    @Transactional
    public PlannedMealCompletionResponse complete(
            UUID fridgeId, UUID plannedMealId, UUID userId) {
        fridgeService.requireMembership(fridgeId, userId);
        PlannedMeal plannedMeal = plannedMealRepository
                .findByIdAndFridgeIdForUpdate(plannedMealId, fridgeId)
                .orElseThrow(() -> new NotFoundException("Planned meal not found"));
        if (plannedMeal.getCompletedAt() != null) {
            throw new ConflictException("Planned meal is already completed");
        }

        List<PlannedMealIngredient> ingredients = List.copyOf(plannedMeal.getIngredients());
        Map<UUID, FridgeItem> lockedItems = lockReservedItems(fridgeId, ingredients);
        Map<UUID, BigDecimal> remainingAmounts = new LinkedHashMap<>();
        lockedItems.forEach((id, item) -> remainingAmounts.put(
                id, isActive(item) ? item.getAmount() : BigDecimal.ZERO));

        List<PlannedMealCompletionWarningResponse> warnings = new ArrayList<>();
        for (PlannedMealIngredient ingredient : ingredients) {
            IngredientConsumption consumption = consumeReservations(
                    ingredient, lockedItems, remainingAmounts);
            addWarningIfNeeded(plannedMeal, ingredient, consumption, warnings);
        }

        OffsetDateTime completedAt = OffsetDateTime.now();
        applyInventoryChanges(lockedItems, remainingAmounts, completedAt);
        ingredients.forEach(ingredient -> ingredient.getReservations().clear());
        plannedMeal.setCompletedAt(completedAt);
        plannedMealRepository.save(plannedMeal);

        return new PlannedMealCompletionResponse(
                plannedMeal.getId(), completedAt, List.copyOf(warnings));
    }

    private Map<UUID, FridgeItem> lockReservedItems(
            UUID fridgeId, List<PlannedMealIngredient> ingredients) {
        Map<UUID, FridgeItem> lockedItems = new LinkedHashMap<>();
        ingredients.stream()
                .flatMap(ingredient -> ingredient.getReservations().stream())
                .map(reservation -> reservation.getFridgeItem().getId())
                .distinct()
                .sorted(Comparator.comparing(UUID::toString))
                .forEach(itemId -> fridgeItemRepository
                        .findByIdAndFridgeForUpdate(itemId, fridgeId)
                        .ifPresent(item -> lockedItems.put(itemId, item)));
        return lockedItems;
    }

    private IngredientConsumption consumeReservations(
            PlannedMealIngredient ingredient,
            Map<UUID, FridgeItem> lockedItems,
            Map<UUID, BigDecimal> remainingAmounts) {
        BigDecimal consumedAmount = BigDecimal.ZERO;
        Map<Unit, BigDecimal> consumedByUnit = new LinkedHashMap<>();
        List<PlannedMealReservation> reservations = ingredient.getReservations().stream()
                .sorted(Comparator.comparing(reservation -> reservation.getId().toString()))
                .toList();
        for (PlannedMealReservation reservation : reservations) {
            UUID itemId = reservation.getFridgeItem().getId();
            FridgeItem item = lockedItems.get(itemId);
            if (item == null || !isActive(item)) {
                continue;
            }
            BigDecimal remainingAmount = remainingAmounts.getOrDefault(itemId, BigDecimal.ZERO);
            BigDecimal consumedFromItem = reservation.getAmount().min(remainingAmount);
            if (consumedFromItem.signum() <= 0) {
                continue;
            }
            remainingAmounts.put(itemId, remainingAmount.subtract(consumedFromItem));
            consumedAmount = consumedAmount.add(consumedFromItem);
            consumedByUnit.merge(item.getUnit(), consumedFromItem, BigDecimal::add);
        }
        return new IngredientConsumption(cleanAmount(consumedAmount), consumedByUnit);
    }

    private void addWarningIfNeeded(
            PlannedMeal plannedMeal,
            PlannedMealIngredient ingredient,
            IngredientConsumption consumption,
            List<PlannedMealCompletionWarningResponse> warnings) {
        if (ingredient.isOptional()) {
            return;
        }

        NormalizedQuantity required = normalizedRequiredQuantity(plannedMeal, ingredient);
        if (required != null) {
            BigDecimal consumedAmount = cleanAmount(
                    consumption.byUnit().getOrDefault(required.unit(), BigDecimal.ZERO));
            BigDecimal missingAmount = required.amount().subtract(consumedAmount).max(BigDecimal.ZERO);
            if (missingAmount.signum() > 0) {
                warnings.add(new PlannedMealCompletionWarningResponse(
                        "MISSING_AMOUNT",
                        ingredient.getId(),
                        ingredient.getName(),
                        cleanAmount(required.amount()),
                        consumedAmount,
                        cleanAmount(missingAmount),
                        required.unit().name()));
            }
            return;
        }

        BigDecimal reservedAmount = ingredient.getReservations().stream()
                .map(PlannedMealReservation::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (reservedAmount.signum() == 0) {
            warnings.add(new PlannedMealCompletionWarningResponse(
                    "UNRESERVED_INGREDIENT",
                    ingredient.getId(),
                    ingredient.getName(),
                    null,
                    BigDecimal.ZERO,
                    null,
                    ingredient.getUnit()));
        } else if (consumption.totalAmount().compareTo(reservedAmount) < 0) {
            warnings.add(new PlannedMealCompletionWarningResponse(
                    "RESERVED_ITEM_UNAVAILABLE",
                    ingredient.getId(),
                    ingredient.getName(),
                    null,
                    consumption.totalAmount(),
                    cleanAmount(reservedAmount.subtract(consumption.totalAmount())),
                    ingredient.getUnit()));
        }
    }

    private NormalizedQuantity normalizedRequiredQuantity(
            PlannedMeal plannedMeal, PlannedMealIngredient ingredient) {
        if (ingredient.getAmount() == null
                || ingredient.getUnit() == null
                || ingredient.getUnit().isBlank()) {
            return null;
        }
        BigDecimal scaledAmount = ingredient.getAmount()
                .multiply(BigDecimal.valueOf(plannedMeal.getServings()))
                .divide(BigDecimal.valueOf(plannedMeal.getRecipeServings()), MathContext.DECIMAL128);
        String unit = ingredient.getUnit().trim().toLowerCase(Locale.ROOT).replace(".", "");
        return switch (unit) {
            case "g", "gram", "grams", "gramy", "gramów", "gramow" ->
                    new NormalizedQuantity(scaledAmount, Unit.GRAM);
            case "kg", "kilogram", "kilogramy", "kilogramów", "kilogramow" ->
                    new NormalizedQuantity(scaledAmount.multiply(BigDecimal.valueOf(1000)), Unit.GRAM);
            case "ml", "mililitr", "mililitry", "mililitrów", "mililitrow",
                    "milliliter", "milliliters", "millilitre", "millilitres" ->
                    new NormalizedQuantity(scaledAmount, Unit.MILLILITER);
            case "l", "litr", "litry", "litrów", "litrow" ->
                    new NormalizedQuantity(
                            scaledAmount.multiply(BigDecimal.valueOf(1000)), Unit.MILLILITER);
            case "szt", "sztuka", "sztuki", "sztuk", "piece" ->
                    new NormalizedQuantity(
                            scaledAmount.setScale(0, RoundingMode.CEILING), Unit.PIECE);
            default -> null;
        };
    }

    private void applyInventoryChanges(
            Map<UUID, FridgeItem> lockedItems,
            Map<UUID, BigDecimal> remainingAmounts,
            OffsetDateTime completedAt) {
        LocalDate completionDate = completedAt.toLocalDate();
        lockedItems.forEach((itemId, item) -> {
            if (!isActive(item)) {
                return;
            }
            BigDecimal remainingAmount = remainingAmounts.get(itemId);
            if (remainingAmount.compareTo(item.getAmount()) == 0) {
                return;
            }
            if (item.getState() == ItemState.SEALED) {
                openItem(item, completionDate);
            }
            item.setAmount(remainingAmount);
            if (remainingAmount.signum() == 0) {
                item.setState(ItemState.CONSUMED);
                item.setArchivedAt(completedAt);
            }
        });
    }

    private void openItem(FridgeItem item, LocalDate openDate) {
        LocalDate previousEffectiveExpireAt = item.getEffectiveExpireAt();
        item.setOpenDate(openDate);
        item.setState(ItemState.OPEN);
        LocalDate recalculatedExpireAt = expirePolicy.computeEffectiveExpireAt(
                item.getBestBeforeDate(), openDate, item.getProduct(), null, null);
        if (previousEffectiveExpireAt != null
                && (recalculatedExpireAt == null
                || previousEffectiveExpireAt.isBefore(recalculatedExpireAt))) {
            recalculatedExpireAt = previousEffectiveExpireAt;
        }
        item.setEffectiveExpireAt(recalculatedExpireAt);
    }

    private boolean isActive(FridgeItem item) {
        return item.getArchivedAt() == null
                && item.getState() != ItemState.CONSUMED
                && item.getState() != ItemState.DISCARDED;
    }

    private BigDecimal cleanAmount(BigDecimal amount) {
        BigDecimal stripped = amount.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    private record NormalizedQuantity(BigDecimal amount, Unit unit) {
    }

    private record IngredientConsumption(
            BigDecimal totalAmount,
            Map<Unit, BigDecimal> byUnit
    ) {
    }
}
