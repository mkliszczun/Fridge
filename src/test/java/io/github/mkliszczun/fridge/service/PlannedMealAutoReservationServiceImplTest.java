package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.dto.PlannedMealsReserveRequest;
import io.github.mkliszczun.fridge.enums.Unit;
import io.github.mkliszczun.fridge.exception.ConflictException;
import io.github.mkliszczun.fridge.fridge.FridgeItem;
import io.github.mkliszczun.fridge.mealplan.PlannedMeal;
import io.github.mkliszczun.fridge.mealplan.PlannedMealIngredient;
import io.github.mkliszczun.fridge.mealplan.PlannedMealReservation;
import io.github.mkliszczun.fridge.repository.FridgeItemRepository;
import io.github.mkliszczun.fridge.repository.PlannedMealRepository;
import io.github.mkliszczun.fridge.repository.PlannedMealReservationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlannedMealAutoReservationServiceImplTest {

    @Mock
    private OpenAiShoppingListClient openAiClient;

    @Mock
    private PlannedMealRepository plannedMealRepository;

    @Mock
    private FridgeItemRepository fridgeItemRepository;

    @Mock
    private PlannedMealReservationRepository reservationRepository;

    @Mock
    private FridgeService fridgeService;

    @InjectMocks
    private PlannedMealAutoReservationServiceImpl service;

    @Test
    void reserve_scalesIngredientAndUsesOnlyCurrentlyAvailableAmount() {
        UUID fridgeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        PlannedMeal meal = meal(2, 4);
        PlannedMealIngredient ingredient = ingredient(meal, "Makaron", "200", "g");
        FridgeItem item = fridgeItem(
                "Makaron pełnoziarnisty", "500", Unit.GRAM, LocalDate.now().plusDays(2));

        when(plannedMealRepository.findByIdAndFridgeId(meal.getId(), fridgeId))
                .thenReturn(Optional.of(meal));
        when(fridgeItemRepository.findActiveByFridge(fridgeId)).thenReturn(List.of(item));
        when(fridgeItemRepository.findActiveByIdAndFridgeForUpdate(item.getId(), fridgeId))
                .thenReturn(Optional.of(item));
        when(reservationRepository.sumReservedAmount(item.getId()))
                .thenReturn(new BigDecimal("100"));
        when(reservationRepository.findByPlannedMealIngredientIdAndFridgeItemId(
                ingredient.getId(), item.getId())).thenReturn(Optional.empty());
        when(openAiClient.match(anyList(), anyList())).thenReturn(List.of(
                new ShoppingListIngredientMatch(ingredient.getId(), List.of(item.getId()))));
        when(reservationRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<PlannedMeal> result = service.reserve(
                fridgeId,
                userId,
                new PlannedMealsReserveRequest(List.of(meal.getId())));

        assertThat(result).containsExactly(meal);
        assertThat(ingredient.getReservations()).singleElement().satisfies(reservation -> {
            assertThat(reservation.getFridgeItem()).isEqualTo(item);
            assertThat(reservation.getAmount()).isEqualByComparingTo("400");
        });
        verify(fridgeService).requireMembership(fridgeId, userId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ShoppingListIngredientCandidate>> ingredientCaptor =
                ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ShoppingListFridgeItemCandidate>> itemCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(openAiClient).match(ingredientCaptor.capture(), itemCaptor.capture());
        assertThat(ingredientCaptor.getValue()).singleElement().satisfies(candidate ->
                assertThat(candidate.amount()).isEqualByComparingTo("400"));
        assertThat(itemCaptor.getValue()).singleElement().satisfies(candidate -> {
            assertThat(candidate.availableAmount()).isEqualByComparingTo("400");
            assertThat(candidate.effectiveExpireAt()).isEqualTo(item.getEffectiveExpireAt());
        });
    }

    @Test
    void reserve_updatesExistingReservationAndDoesNotDuplicateItOnRetry() {
        UUID fridgeId = UUID.randomUUID();
        PlannedMeal meal = meal(2, 4);
        PlannedMealIngredient ingredient = ingredient(meal, "Mleko", "200", "ml");
        FridgeItem item = fridgeItem("Mleko", "500", Unit.MILLILITER, null);
        PlannedMealReservation existing = reservation(ingredient, item, "150");

        when(plannedMealRepository.findByIdAndFridgeId(meal.getId(), fridgeId))
                .thenReturn(Optional.of(meal));
        when(fridgeItemRepository.findActiveByFridge(fridgeId)).thenReturn(List.of(item));
        when(fridgeItemRepository.findActiveByIdAndFridgeForUpdate(item.getId(), fridgeId))
                .thenReturn(Optional.of(item));
        when(reservationRepository.sumReservedAmount(item.getId())).thenAnswer(ignored ->
                ingredient.getReservations().stream()
                        .map(PlannedMealReservation::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
        when(reservationRepository.findByPlannedMealIngredientIdAndFridgeItemId(
                ingredient.getId(), item.getId())).thenReturn(Optional.of(existing));
        when(openAiClient.match(anyList(), anyList())).thenReturn(List.of(
                new ShoppingListIngredientMatch(ingredient.getId(), List.of(item.getId()))));
        when(reservationRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        PlannedMealsReserveRequest request = new PlannedMealsReserveRequest(List.of(meal.getId()));

        service.reserve(fridgeId, UUID.randomUUID(), request);
        service.reserve(fridgeId, UUID.randomUUID(), request);

        assertThat(ingredient.getReservations()).containsExactly(existing);
        assertThat(existing.getAmount()).isEqualByComparingTo("400");
        verify(openAiClient, times(1)).match(anyList(), anyList());
        verify(reservationRepository, times(1)).save(existing);
    }

    @Test
    void reserve_rejectsDuplicateMealIdsBeforeLoadingMeals() {
        UUID mealId = UUID.randomUUID();

        assertThatThrownBy(() -> service.reserve(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new PlannedMealsReserveRequest(List.of(mealId, mealId))))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Planned meal IDs must be unique");

        verify(plannedMealRepository, never()).findByIdAndFridgeId(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(openAiClient, never()).match(anyList(), anyList());
    }

    private PlannedMeal meal(int recipeServings, int plannedServings) {
        PlannedMeal meal = new PlannedMeal();
        ReflectionTestUtils.setField(meal, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(meal, "recipeServings", recipeServings);
        meal.setPlannedDate(LocalDate.now().plusDays(1));
        meal.setServings(plannedServings);
        return meal;
    }

    private PlannedMealIngredient ingredient(
            PlannedMeal meal, String name, String amount, String unit) {
        PlannedMealIngredient ingredient = new PlannedMealIngredient();
        ReflectionTestUtils.setField(ingredient, "id", UUID.randomUUID());
        ingredient.setPlannedMeal(meal);
        ingredient.setName(name);
        ingredient.setAmount(new BigDecimal(amount));
        ingredient.setUnit(unit);
        ingredient.setPosition(meal.getIngredients().size());
        meal.getIngredients().add(ingredient);
        return ingredient;
    }

    private FridgeItem fridgeItem(
            String name, String amount, Unit unit, LocalDate effectiveExpireAt) {
        FridgeItem item = new FridgeItem();
        ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
        item.setCustomName(name);
        item.setAmount(new BigDecimal(amount));
        item.setUnit(unit);
        item.setEffectiveExpireAt(effectiveExpireAt);
        return item;
    }

    private PlannedMealReservation reservation(
            PlannedMealIngredient ingredient, FridgeItem item, String amount) {
        PlannedMealReservation reservation = new PlannedMealReservation();
        ReflectionTestUtils.setField(reservation, "id", UUID.randomUUID());
        reservation.setFridgeItem(item);
        reservation.setAmount(new BigDecimal(amount));
        ingredient.addReservation(reservation);
        return reservation;
    }
}
