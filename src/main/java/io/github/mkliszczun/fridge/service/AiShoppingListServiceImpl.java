package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.dto.AiShoppingListGenerateRequest;
import io.github.mkliszczun.fridge.dto.AiShoppingListItemResponse;
import io.github.mkliszczun.fridge.dto.AiShoppingListProposalResponse;
import io.github.mkliszczun.fridge.enums.Unit;
import io.github.mkliszczun.fridge.exception.ConflictException;
import io.github.mkliszczun.fridge.exception.InvalidAiResponseException;
import io.github.mkliszczun.fridge.exception.NotFoundException;
import io.github.mkliszczun.fridge.fridge.FridgeItem;
import io.github.mkliszczun.fridge.mealplan.PlannedMeal;
import io.github.mkliszczun.fridge.mealplan.PlannedMealIngredient;
import io.github.mkliszczun.fridge.repository.FridgeItemRepository;
import io.github.mkliszczun.fridge.repository.PlannedMealRepository;
import io.github.mkliszczun.fridge.repository.PlannedMealReservationRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AiShoppingListServiceImpl implements AiShoppingListService {

    private static final int MAX_ATTEMPTS = 2;

    private final OpenAiShoppingListClient openAiClient;
    private final PlannedMealRepository plannedMealRepository;
    private final FridgeItemRepository fridgeItemRepository;
    private final PlannedMealReservationRepository reservationRepository;
    private final FridgeService fridgeService;

    public AiShoppingListServiceImpl(OpenAiShoppingListClient openAiClient,
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
    public AiShoppingListProposalResponse generate(
            UUID fridgeId,
            UUID userId,
            AiShoppingListGenerateRequest request) {
        fridgeService.requireMembership(fridgeId, userId);
        assertUniqueMealIds(request.plannedMealIds());

        List<PlannedMeal> meals = request.plannedMealIds().stream()
                .map(id -> plannedMealRepository.findByIdAndFridgeId(id, fridgeId)
                        .orElseThrow(() -> new NotFoundException("Planned meal not found")))
                .toList();
        List<IngredientNeed> needs = toIngredientNeeds(meals);
        List<FridgeItem> fridgeItems = fridgeItemRepository.findActiveByFridge(fridgeId);
        Map<UUID, BigDecimal> availableAmounts = availableAmounts(fridgeItems);

        List<ShoppingListIngredientCandidate> ingredientCandidates = needs.stream()
                .filter(need -> need.normalizedQuantity() != null)
                .map(this::toCandidate)
                .toList();
        List<ShoppingListFridgeItemCandidate> fridgeItemCandidates = fridgeItems.stream()
                .filter(item -> availableAmounts.get(item.getId()).signum() > 0)
                .map(item -> new ShoppingListFridgeItemCandidate(
                        item.getId(), itemName(item), availableAmounts.get(item.getId()),
                        item.getUnit(), item.getEffectiveExpireAt()))
                .toList();

        List<ShoppingListIngredientMatch> matches = ingredientCandidates.isEmpty()
                || fridgeItemCandidates.isEmpty()
                ? List.of()
                : generateValidMatches(ingredientCandidates, fridgeItemCandidates, needs);

        return new AiShoppingListProposalResponse(
                fridgeId,
                List.copyOf(request.plannedMealIds()),
                buildShoppingItems(needs, fridgeItemCandidates, matches)
        );
    }

    private void assertUniqueMealIds(List<UUID> plannedMealIds) {
        if (new HashSet<>(plannedMealIds).size() != plannedMealIds.size()) {
            throw new ConflictException("Planned meal IDs must be unique");
        }
    }

    private List<IngredientNeed> toIngredientNeeds(List<PlannedMeal> meals) {
        List<IngredientNeed> needs = new ArrayList<>();
        for (PlannedMeal meal : meals) {
            for (PlannedMealIngredient ingredient : meal.getIngredients()) {
                if (!ingredient.isOptional()) {
                    BigDecimal scaledAmount = scaleAmount(ingredient.getAmount(), meal);
                    needs.add(new IngredientNeed(
                            ingredient,
                            ingredient.getName(),
                            scaledAmount,
                            ingredient.getUnit(),
                            normalizeQuantity(scaledAmount, ingredient.getUnit())
                    ));
                }
            }
        }
        return needs;
    }

    private BigDecimal scaleAmount(BigDecimal amount, PlannedMeal meal) {
        if (amount == null) {
            return null;
        }
        return cleanAmount(amount
                .multiply(BigDecimal.valueOf(meal.getServings()))
                .divide(BigDecimal.valueOf(meal.getRecipeServings()), MathContext.DECIMAL128));
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

    private ShoppingListIngredientCandidate toCandidate(IngredientNeed need) {
        NormalizedQuantity quantity = need.normalizedQuantity();
        return new ShoppingListIngredientCandidate(
                need.ingredient().getId(),
                need.name(),
                cleanAmount(quantity.amount()),
                quantity.unit().name()
        );
    }

    private Map<UUID, BigDecimal> availableAmounts(List<FridgeItem> fridgeItems) {
        Map<UUID, BigDecimal> available = new LinkedHashMap<>();
        for (FridgeItem item : fridgeItems) {
            BigDecimal reserved = reservationRepository.sumReservedAmount(item.getId());
            BigDecimal amount = item.getAmount().subtract(reserved).max(BigDecimal.ZERO);
            available.put(item.getId(), cleanAmount(amount));
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

    private void validateMatches(List<ShoppingListIngredientMatch> matches,
                                 List<IngredientNeed> needs,
                                 List<ShoppingListFridgeItemCandidate> fridgeItems) {
        if (matches == null) {
            throw new InvalidAiResponseException("AI returned invalid shopping list matches");
        }
        Map<UUID, IngredientNeed> needsById = new HashMap<>();
        needs.stream()
                .filter(need -> need.normalizedQuantity() != null)
                .forEach(need -> needsById.put(need.ingredient().getId(), need));
        Map<UUID, ShoppingListFridgeItemCandidate> fridgeItemsById = new HashMap<>();
        fridgeItems.forEach(item -> fridgeItemsById.put(item.id(), item));

        Set<UUID> matchedIngredients = new HashSet<>();
        for (ShoppingListIngredientMatch match : matches) {
            if (match == null || match.plannedMealIngredientId() == null
                    || match.fridgeItemIds() == null || match.fridgeItemIds().isEmpty()
                    || !matchedIngredients.add(match.plannedMealIngredientId())) {
                throw new InvalidAiResponseException("AI returned invalid shopping list matches");
            }
            IngredientNeed need = needsById.get(match.plannedMealIngredientId());
            if (need == null || new HashSet<>(match.fridgeItemIds()).size() != match.fridgeItemIds().size()) {
                throw new InvalidAiResponseException("AI returned invalid shopping list matches");
            }
            for (UUID fridgeItemId : match.fridgeItemIds()) {
                ShoppingListFridgeItemCandidate item = fridgeItemsById.get(fridgeItemId);
                if (item == null || item.unit() != need.normalizedQuantity().unit()) {
                    throw new InvalidAiResponseException("AI returned invalid shopping list matches");
                }
            }
        }
    }

    private List<AiShoppingListItemResponse> buildShoppingItems(
            List<IngredientNeed> needs,
            List<ShoppingListFridgeItemCandidate> fridgeItems,
            List<ShoppingListIngredientMatch> matches) {
        Map<UUID, BigDecimal> remainingAmounts = new HashMap<>();
        fridgeItems.forEach(item -> remainingAmounts.put(item.id(), item.availableAmount()));
        Map<UUID, List<UUID>> matchesByIngredient = new HashMap<>();
        matches.forEach(match -> matchesByIngredient.put(
                match.plannedMealIngredientId(), match.fridgeItemIds()));

        Map<ShoppingItemKey, ShoppingItemAccumulator> shoppingItems = new LinkedHashMap<>();
        for (IngredientNeed need : needs) {
            if (need.normalizedQuantity() == null) {
                if (need.amount() == null && !need.ingredient().getReservations().isEmpty()) {
                    continue;
                }
                addShoppingItem(shoppingItems, need, need.amount(), need.unit());
                continue;
            }

            NormalizedQuantity quantity = need.normalizedQuantity();
            BigDecimal missingAmount = quantity.amount()
                    .subtract(existingReservedAmount(need, quantity.unit()))
                    .max(BigDecimal.ZERO);
            for (UUID fridgeItemId : matchesByIngredient.getOrDefault(
                    need.ingredient().getId(), List.of())) {
                BigDecimal available = remainingAmounts.getOrDefault(fridgeItemId, BigDecimal.ZERO);
                BigDecimal used = available.min(missingAmount);
                remainingAmounts.put(fridgeItemId, available.subtract(used));
                missingAmount = missingAmount.subtract(used);
                if (missingAmount.signum() == 0) {
                    break;
                }
            }
            if (missingAmount.signum() > 0) {
                addShoppingItem(shoppingItems, need, cleanAmount(missingAmount), quantity.unit().name());
            }
        }
        return shoppingItems.values().stream()
                .map(ShoppingItemAccumulator::toResponse)
                .toList();
    }

    private BigDecimal existingReservedAmount(IngredientNeed need, Unit unit) {
        return need.ingredient().getReservations().stream()
                .filter(reservation -> reservation.getFridgeItem().getUnit() == unit)
                .map(reservation -> reservation.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void addShoppingItem(Map<ShoppingItemKey, ShoppingItemAccumulator> shoppingItems,
                                 IngredientNeed need, BigDecimal amount, String unit) {
        ShoppingItemKey key = new ShoppingItemKey(
                need.name().trim().toLowerCase(Locale.ROOT),
                unit == null ? "" : unit.trim().toLowerCase(Locale.ROOT),
                amount != null
        );
        shoppingItems.computeIfAbsent(
                        key,
                        ignored -> new ShoppingItemAccumulator(need.name(), unit, amount != null))
                .add(amount, need.ingredient().getId());
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
            String name,
            BigDecimal amount,
            String unit,
            NormalizedQuantity normalizedQuantity
    ) {
    }

    private record NormalizedQuantity(BigDecimal amount, Unit unit) {
    }

    private record ShoppingItemKey(String name, String unit, boolean quantified) {
    }

    private static final class ShoppingItemAccumulator {
        private final String name;
        private final String unit;
        private final boolean quantified;
        private final List<UUID> ingredientIds = new ArrayList<>();
        private BigDecimal amount = BigDecimal.ZERO;

        private ShoppingItemAccumulator(String name, String unit, boolean quantified) {
            this.name = name;
            this.unit = unit;
            this.quantified = quantified;
        }

        private void add(BigDecimal addedAmount, UUID ingredientId) {
            if (quantified) {
                amount = amount.add(addedAmount);
            }
            ingredientIds.add(ingredientId);
        }

        private AiShoppingListItemResponse toResponse() {
            BigDecimal responseAmount = null;
            if (quantified) {
                BigDecimal stripped = amount.stripTrailingZeros();
                responseAmount = stripped.scale() < 0 ? stripped.setScale(0) : stripped;
                if (Unit.PIECE.name().equals(unit)) {
                    responseAmount = responseAmount.setScale(0, RoundingMode.CEILING);
                }
            }
            return new AiShoppingListItemResponse(
                    name,
                    responseAmount,
                    unit,
                    List.copyOf(ingredientIds)
            );
        }
    }
}
