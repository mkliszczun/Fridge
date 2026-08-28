package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.dto.PlannedMealCreateRequest;
import io.github.mkliszczun.fridge.dto.PlannedMealReservationRequest;
import io.github.mkliszczun.fridge.dto.PlannedMealReservationUpdateRequest;
import io.github.mkliszczun.fridge.dto.PlannedMealUpdateRequest;
import io.github.mkliszczun.fridge.exception.ConflictException;
import io.github.mkliszczun.fridge.exception.NotFoundException;
import io.github.mkliszczun.fridge.fridge.Fridge;
import io.github.mkliszczun.fridge.fridge.FridgeItem;
import io.github.mkliszczun.fridge.mealplan.PlannedMeal;
import io.github.mkliszczun.fridge.mealplan.PlannedMealIngredient;
import io.github.mkliszczun.fridge.mealplan.PlannedMealReservation;
import io.github.mkliszczun.fridge.recipe.Recipe;
import io.github.mkliszczun.fridge.repository.FridgeItemRepository;
import io.github.mkliszczun.fridge.repository.PlannedMealRepository;
import io.github.mkliszczun.fridge.repository.PlannedMealReservationRepository;
import io.github.mkliszczun.fridge.repository.RecipeRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class PlannedMealServiceImpl implements PlannedMealService {

    private final PlannedMealRepository repository;
    private final PlannedMealReservationRepository reservationRepository;
    private final FridgeItemRepository fridgeItemRepository;
    private final RecipeRepository recipeRepository;
    private final FridgeService fridgeService;

    public PlannedMealServiceImpl(PlannedMealRepository repository,
                                  PlannedMealReservationRepository reservationRepository,
                                  FridgeItemRepository fridgeItemRepository,
                                  RecipeRepository recipeRepository,
                                  FridgeService fridgeService) {
        this.repository = repository;
        this.reservationRepository = reservationRepository;
        this.fridgeItemRepository = fridgeItemRepository;
        this.recipeRepository = recipeRepository;
        this.fridgeService = fridgeService;
    }

    @Override
    @Transactional
    public PlannedMeal create(UUID fridgeId, UUID userId, PlannedMealCreateRequest request) {
        Fridge fridge = fridgeService.requireMembership(fridgeId, userId);
        Recipe recipe = findOwnedRecipe(request.recipeId(), userId);

        PlannedMeal plannedMeal = new PlannedMeal();
        plannedMeal.setFridge(fridge);
        plannedMeal.snapshotRecipe(recipe);
        plannedMeal.setPlannedDate(request.plannedDate());
        plannedMeal.setServings(request.servings());
        plannedMeal.setCreatedByUserId(userId);
        return repository.save(plannedMeal);
    }

    @Override
    @Transactional
    public List<PlannedMeal> list(UUID fridgeId, UUID userId) {
        fridgeService.requireMembership(fridgeId, userId);
        return repository.findAllByFridgeIdOrderByPlannedDateAscCreatedAtAsc(fridgeId);
    }

    @Override
    @Transactional
    public PlannedMeal get(UUID fridgeId, UUID plannedMealId, UUID userId) {
        fridgeService.requireMembership(fridgeId, userId);
        return findPlannedMeal(plannedMealId, fridgeId);
    }

    @Override
    @Transactional
    public PlannedMeal update(UUID fridgeId, UUID plannedMealId, UUID userId, PlannedMealUpdateRequest request) {
        fridgeService.requireMembership(fridgeId, userId);
        PlannedMeal plannedMeal = findPlannedMeal(plannedMealId, fridgeId);

        UUID sourceRecipeId = plannedMeal.getSourceRecipe() == null
                ? null
                : plannedMeal.getSourceRecipe().getId();
        if (request.recipeId() != null && !request.recipeId().equals(sourceRecipeId)) {
            plannedMeal.snapshotRecipe(findOwnedRecipe(request.recipeId(), userId));
        }
        plannedMeal.setPlannedDate(request.plannedDate());
        plannedMeal.setServings(request.servings());
        return repository.save(plannedMeal);
    }

    @Override
    @Transactional
    public void delete(UUID fridgeId, UUID plannedMealId, UUID userId) {
        fridgeService.requireMembership(fridgeId, userId);
        repository.delete(findPlannedMeal(plannedMealId, fridgeId));
    }

    @Override
    @Transactional
    public PlannedMealReservation createReservation(UUID fridgeId, UUID plannedMealId, UUID userId,
                                                    PlannedMealReservationRequest request) {
        fridgeService.requireMembership(fridgeId, userId);
        PlannedMeal plannedMeal = findPlannedMeal(plannedMealId, fridgeId);
        PlannedMealIngredient ingredient = findIngredient(plannedMeal, request.plannedMealIngredientId());
        FridgeItem fridgeItem = findActiveFridgeItemForUpdate(request.fridgeItemId(), fridgeId);

        if (reservationRepository.existsByPlannedMealIngredientIdAndFridgeItemId(
                ingredient.getId(), fridgeItem.getId())) {
            throw new ConflictException("This fridge item is already reserved for the ingredient");
        }
        assertAmountAvailable(fridgeItem, request.amount(), BigDecimal.ZERO);

        PlannedMealReservation reservation = new PlannedMealReservation();
        ingredient.addReservation(reservation);
        reservation.setFridgeItem(fridgeItem);
        reservation.setAmount(request.amount());
        return reservationRepository.save(reservation);
    }

    @Override
    @Transactional
    public PlannedMealReservation updateReservation(UUID fridgeId, UUID plannedMealId, UUID reservationId,
                                                    UUID userId, PlannedMealReservationUpdateRequest request) {
        fridgeService.requireMembership(fridgeId, userId);
        findPlannedMeal(plannedMealId, fridgeId);
        PlannedMealReservation reservation = findReservation(reservationId, plannedMealId);
        FridgeItem fridgeItem = findActiveFridgeItemForUpdate(reservation.getFridgeItem().getId(), fridgeId);

        assertAmountAvailable(fridgeItem, request.amount(), reservation.getAmount());
        reservation.setAmount(request.amount());
        return reservationRepository.save(reservation);
    }

    @Override
    @Transactional
    public void deleteReservation(UUID fridgeId, UUID plannedMealId, UUID reservationId, UUID userId) {
        fridgeService.requireMembership(fridgeId, userId);
        findPlannedMeal(plannedMealId, fridgeId);
        PlannedMealReservation reservation = findReservation(reservationId, plannedMealId);
        reservation.getPlannedMealIngredient().removeReservation(reservation);
        reservationRepository.delete(reservation);
    }

    private PlannedMeal findPlannedMeal(UUID plannedMealId, UUID fridgeId) {
        return repository.findByIdAndFridgeId(plannedMealId, fridgeId)
                .orElseThrow(() -> new NotFoundException("Planned meal not found"));
    }

    private Recipe findOwnedRecipe(UUID recipeId, UUID userId) {
        return recipeRepository.findByIdAndOwnerUserId(recipeId, userId)
                .orElseThrow(() -> new NotFoundException("Recipe not found"));
    }

    private PlannedMealIngredient findIngredient(PlannedMeal plannedMeal, UUID ingredientId) {
        return plannedMeal.getIngredients().stream()
                .filter(ingredient -> ingredient.getId().equals(ingredientId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Planned meal ingredient not found"));
    }

    private PlannedMealReservation findReservation(UUID reservationId, UUID plannedMealId) {
        return reservationRepository.findByIdAndPlannedMealIngredientPlannedMealId(
                        reservationId, plannedMealId)
                .orElseThrow(() -> new NotFoundException("Planned meal reservation not found"));
    }

    private FridgeItem findActiveFridgeItemForUpdate(UUID fridgeItemId, UUID fridgeId) {
        return fridgeItemRepository.findActiveByIdAndFridgeForUpdate(fridgeItemId, fridgeId)
                .orElseThrow(() -> new NotFoundException("Fridge item not found"));
    }

    private void assertAmountAvailable(FridgeItem fridgeItem, BigDecimal requestedAmount,
                                       BigDecimal currentReservationAmount) {
        BigDecimal reservedByOthers = reservationRepository.sumReservedAmount(fridgeItem.getId())
                .subtract(currentReservationAmount);
        BigDecimal availableAmount = fridgeItem.getAmount().subtract(reservedByOthers);
        if (requestedAmount.compareTo(availableAmount) > 0) {
            throw new ConflictException("Reservation amount exceeds available amount");
        }
    }
}
