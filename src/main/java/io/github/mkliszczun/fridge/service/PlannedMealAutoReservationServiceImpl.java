package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.dto.PlannedMealsReserveRequest;
import io.github.mkliszczun.fridge.enums.Unit;
import io.github.mkliszczun.fridge.exception.ConflictException;
import io.github.mkliszczun.fridge.exception.InvalidAiResponseException;
import io.github.mkliszczun.fridge.exception.NotFoundException;
import io.github.mkliszczun.fridge.fridge.FridgeItem;
import io.github.mkliszczun.fridge.mealplan.PlannedMeal;
import io.github.mkliszczun.fridge.mealplan.PlannedMealIngredient;
import io.github.mkliszczun.fridge.mealplan.PlannedMealReservation;
import io.github.mkliszczun.fridge.repository.FridgeItemRepository;
import io.github.mkliszczun.fridge.repository.PlannedMealRepository;
import io.github.mkliszczun.fridge.repository.PlannedMealReservationRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class PlannedMealAutoReservationServiceImpl
        implements PlannedMealAutoReservationService {

    private static final int MAX_ATTEMPTS = 2;

    private final OpenAiShoppingListClient openAiClient;
    private final PlannedMealRepository plannedMealRepository;
    private final FridgeItemRepository fridgeItemRepository;
    private final PlannedMealReservationRepository reservationRepository;
    private final FridgeService fridgeService;

    public PlannedMealAutoReservationServiceImpl(
            OpenAiShoppingListClient openAiClient,
            PlannedMealRepository plannedMealRepository,
            FridgeItemRepository fridgeItemRepository,
            PlannedMealReservationRepository reservationRepository,
            FridgeService fridgeService) {
        this.openAiClient = openAiClient;
        this.plannedMealRepository = plannedMealRepository;
        this.fridgeItemRepository = fridgeItemRepository;
        this.reservationRepository = reservationRepository;
        this.fridgeService = fridgeService;
    }

    @Override
    @Transactional
    public List<PlannedMeal> reserve(
            UUID fridgeId,
            UUID userId,
            PlannedMealsReserveRequest request) {
        fridgeService.requireMembership(fridgeId, userId);
        assertUniqueMealIds(request.plannedMealIds());

        List<PlannedMeal> meals = request.plannedMealIds().stream()
                .map(id -> plannedMealRepository.findActiveByIdAndFridgeId(id, fridgeId)
                        .orElseThrow(() -> new NotFoundException("Planned meal not found")))
                .sorted(Comparator.comparing(PlannedMeal::getPlannedDate)
                        .thenComparing(meal -> meal.getId().toString()))
                .toList();
        List<IngredientNeed> needs = ingredientNeeds(meals);
        List<FridgeItem> fridgeItems = fridgeItemRepository.findActiveByFridge(fridgeId);
        Map<UUID, BigDecimal> availableAmounts = availableAmounts(fridgeItems);

        List<ShoppingListIngredientCandidate> ingredientCandidates = needs.stream()
                .filter(need -> need.remainingAmount().signum() > 0)
                .map(need -> new ShoppingListIngredientCandidate(
                        need.ingredient().getId(),
                        need.ingredient().getName(),
                        cleanAmount(need.remainingAmount()),
                        need.unit().name()))
                .toList();
        List<ShoppingListFridgeItemCandidate> fridgeItemCandidates = fridgeItems.stream()
                .filter(item -> availableAmounts.get(item.getId()).signum() > 0)
                .map(item -> new ShoppingListFridgeItemCandidate(
                        item.getId(),
                        itemName(item),
                        availableAmounts.get(item.getId()),
                        item.getUnit(),
                        item.getEffectiveExpireAt()))
                .sorted(Comparator
                        .comparing(ShoppingListFridgeItemCandidate::effectiveExpireAt,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(candidate -> candidate.id().toString()))
                .toList();

        if (ingredientCandidates.isEmpty() || fridgeItemCandidates.isEmpty()) {
            return meals;
        }

        List<ShoppingListIngredientMatch> matches = generateValidMatches(
                ingredientCandidates, fridgeItemCandidates, needs);
        createReservations(fridgeId, needs, matches);
        return meals;
    }

    private void assertUniqueMealIds(List<UUID> plannedMealIds) {
        if (new HashSet<>(plannedMealIds).size() != plannedMealIds.size()) {
            throw new ConflictException("Planned meal IDs must be unique");
        }
    }

    private List<IngredientNeed> ingredientNeeds(List<PlannedMeal> meals) {
        List<IngredientNeed> needs = new ArrayList<>();
        for (PlannedMeal meal : meals) {
            for (PlannedMealIngredient ingredient : meal.getIngredients()) {
                if (ingredient.isOptional()) {
                    continue;
                }
                NormalizedQuantity quantity = normalizeQuantity(
                        scaleAmount(ingredient.getAmount(), meal), ingredient.getUnit());
                if (quantity == null) {
                    continue;
                }
                BigDecimal requiredAmount = quantity.unit() == Unit.PIECE
                        ? quantity.amount().setScale(0, RoundingMode.CEILING)
                        : quantity.amount();
                BigDecimal reservedAmount = ingredient.getReservations().stream()
                        .filter(reservation -> reservation.getFridgeItem().getUnit() == quantity.unit())
                        .map(PlannedMealReservation::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                needs.add(new IngredientNeed(
                        ingredient,
                        quantity.unit(),
                        requiredAmount.subtract(reservedAmount).max(BigDecimal.ZERO)));
            }
        }
        return needs;
    }

    private BigDecimal scaleAmount(BigDecimal amount, PlannedMeal meal) {
        if (amount == null) {
            return null;
        }
        return amount
                .multiply(BigDecimal.valueOf(meal.getServings()))
                .divide(BigDecimal.valueOf(meal.getRecipeServings()), MathContext.DECIMAL128);
    }

    private NormalizedQuantity normalizeQuantity(BigDecimal amount, String rawUnit) {
        if (amount == null || rawUnit == null || rawUnit.isBlank()) {
            return null;
        }
        String unit = rawUnit.trim().toLowerCase(Locale.ROOT).replace(".", "");
        return switch (unit) {
            case "g", "gram", "grams", "gramy", "gramów", "gramow" ->
                    new NormalizedQuantity(amount, Unit.GRAM);
            case "kg", "kilogram", "kilogramy", "kilogramów", "kilogramow" ->
                    new NormalizedQuantity(amount.multiply(BigDecimal.valueOf(1000)), Unit.GRAM);
            case "ml", "mililitr", "mililitry", "mililitrów", "mililitrow",
                    "milliliter", "milliliters", "millilitre", "millilitres" ->
                    new NormalizedQuantity(amount, Unit.MILLILITER);
            case "l", "litr", "litry", "litrów", "litrow" ->
                    new NormalizedQuantity(amount.multiply(BigDecimal.valueOf(1000)), Unit.MILLILITER);
            case "szt", "sztuka", "sztuki", "sztuk", "piece" ->
                    new NormalizedQuantity(amount, Unit.PIECE);
            default -> null;
        };
    }

    private Map<UUID, BigDecimal> availableAmounts(List<FridgeItem> fridgeItems) {
        Map<UUID, BigDecimal> available = new LinkedHashMap<>();
        for (FridgeItem item : fridgeItems) {
            BigDecimal reserved = reservationRepository.sumReservedAmount(item.getId());
            available.put(item.getId(), cleanAmount(
                    item.getAmount().subtract(reserved).max(BigDecimal.ZERO)));
        }
        return available;
    }

    private List<ShoppingListIngredientMatch> generateValidMatches(
            List<ShoppingListIngredientCandidate> ingredientCandidates,
            List<ShoppingListFridgeItemCandidate> fridgeItemCandidates,
            List<IngredientNeed> needs) {
        InvalidAiResponseException lastError = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                List<ShoppingListIngredientMatch> matches = openAiClient.match(
                        ingredientCandidates, fridgeItemCandidates);
                validateMatches(matches, needs, fridgeItemCandidates);
                return matches;
            } catch (InvalidAiResponseException ex) {
                lastError = ex;
            }
        }
        throw lastError;
    }

    private void validateMatches(
            List<ShoppingListIngredientMatch> matches,
            List<IngredientNeed> needs,
            List<ShoppingListFridgeItemCandidate> fridgeItems) {
        if (matches == null) {
            throw new InvalidAiResponseException("AI returned invalid reservation matches");
        }
        Map<UUID, IngredientNeed> needsById = new HashMap<>();
        needs.stream()
                .filter(need -> need.remainingAmount().signum() > 0)
                .forEach(need -> needsById.put(need.ingredient().getId(), need));
        Map<UUID, ShoppingListFridgeItemCandidate> fridgeItemsById = new HashMap<>();
        fridgeItems.forEach(item -> fridgeItemsById.put(item.id(), item));

        Set<UUID> matchedIngredients = new HashSet<>();
        for (ShoppingListIngredientMatch match : matches) {
            if (match == null || match.plannedMealIngredientId() == null
                    || match.fridgeItemIds() == null || match.fridgeItemIds().isEmpty()
                    || !matchedIngredients.add(match.plannedMealIngredientId())) {
                throw new InvalidAiResponseException("AI returned invalid reservation matches");
            }
            IngredientNeed need = needsById.get(match.plannedMealIngredientId());
            if (need == null
                    || new HashSet<>(match.fridgeItemIds()).size() != match.fridgeItemIds().size()) {
                throw new InvalidAiResponseException("AI returned invalid reservation matches");
            }
            for (UUID fridgeItemId : match.fridgeItemIds()) {
                ShoppingListFridgeItemCandidate item = fridgeItemsById.get(fridgeItemId);
                if (item == null || item.unit() != need.unit()) {
                    throw new InvalidAiResponseException("AI returned invalid reservation matches");
                }
            }
        }
    }

    private void createReservations(
            UUID fridgeId,
            List<IngredientNeed> needs,
            List<ShoppingListIngredientMatch> matches) {
        Map<UUID, List<UUID>> matchesByIngredient = new HashMap<>();
        matches.forEach(match -> matchesByIngredient.put(
                match.plannedMealIngredientId(), match.fridgeItemIds()));

        Set<UUID> matchedItemIds = matches.stream()
                .flatMap(match -> match.fridgeItemIds().stream())
                .collect(java.util.stream.Collectors.toSet());
        Map<UUID, FridgeItem> lockedItems = new LinkedHashMap<>();
        Map<UUID, BigDecimal> remainingAmounts = new HashMap<>();
        matchedItemIds.stream()
                .sorted(Comparator.comparing(UUID::toString))
                .forEach(itemId -> {
                    FridgeItem item = fridgeItemRepository
                            .findActiveByIdAndFridgeForUpdate(itemId, fridgeId)
                            .orElseThrow(() -> new ConflictException(
                                    "Fridge inventory changed while creating reservations"));
                    lockedItems.put(itemId, item);
                    BigDecimal reserved = reservationRepository.sumReservedAmount(itemId);
                    remainingAmounts.put(itemId,
                            item.getAmount().subtract(reserved).max(BigDecimal.ZERO));
                });

        for (IngredientNeed need : needs) {
            BigDecimal missingAmount = need.remainingAmount();
            List<UUID> matchedIds = matchesByIngredient.getOrDefault(
                            need.ingredient().getId(), List.of()).stream()
                    .sorted(Comparator
                            .comparing((UUID id) -> lockedItems.get(id).getEffectiveExpireAt(),
                                    Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(UUID::toString))
                    .toList();
            for (UUID itemId : matchedIds) {
                BigDecimal available = remainingAmounts.getOrDefault(itemId, BigDecimal.ZERO);
                BigDecimal allocated = available.min(missingAmount);
                if (allocated.signum() <= 0) {
                    continue;
                }
                saveReservation(need.ingredient(), lockedItems.get(itemId), allocated);
                remainingAmounts.put(itemId, available.subtract(allocated));
                missingAmount = missingAmount.subtract(allocated);
                if (missingAmount.signum() == 0) {
                    break;
                }
            }
        }
    }

    private void saveReservation(
            PlannedMealIngredient ingredient,
            FridgeItem fridgeItem,
            BigDecimal amount) {
        PlannedMealReservation reservation = reservationRepository
                .findByPlannedMealIngredientIdAndFridgeItemId(
                        ingredient.getId(), fridgeItem.getId())
                .orElseGet(() -> {
                    PlannedMealReservation created = new PlannedMealReservation();
                    ingredient.addReservation(created);
                    created.setFridgeItem(fridgeItem);
                    return created;
                });
        reservation.setAmount(cleanAmount(reservation.getAmount() == null
                ? amount
                : reservation.getAmount().add(amount)));
        reservationRepository.save(reservation);
    }

    private String itemName(FridgeItem item) {
        return item.getProduct() == null ? item.getCustomName() : item.getProduct().getName();
    }

    private BigDecimal cleanAmount(BigDecimal amount) {
        BigDecimal stripped = amount.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    private record IngredientNeed(
            PlannedMealIngredient ingredient,
            Unit unit,
            BigDecimal remainingAmount
    ) {
    }

    private record NormalizedQuantity(BigDecimal amount, Unit unit) {
    }
}
