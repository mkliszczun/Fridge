package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.dto.AiShoppingListGenerateRequest;
import io.github.mkliszczun.fridge.dto.AiShoppingListProposalResponse;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiShoppingListServiceImplTest {

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
    private AiShoppingListServiceImpl service;

    @Test
    void generate_scalesIngredientsAndSubtractsFreeAndReservedAmounts() {
        UUID fridgeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID plannedMealId = UUID.randomUUID();
        PlannedMeal meal = meal(plannedMealId, 2, 4);
        PlannedMealIngredient pasta = ingredient(meal, "Makaron", "200", "g", false);
        PlannedMealIngredient cheese = ingredient(meal, "Ser", "50", "g", false);
        ingredient(meal, "Bazylia", null, null, true);
        FridgeItem pastaItem = fridgeItem("Makaron pełnoziarnisty", "300", Unit.GRAM);
        reserve(pasta, pastaItem, "100");

        when(plannedMealRepository.findByIdAndFridgeId(plannedMealId, fridgeId))
                .thenReturn(Optional.of(meal));
        when(fridgeItemRepository.findActiveByFridge(fridgeId)).thenReturn(List.of(pastaItem));
        when(reservationRepository.sumReservedAmount(pastaItem.getId()))
                .thenReturn(new BigDecimal("100"));
        when(openAiClient.match(anyList(), anyList())).thenReturn(List.of(
                new ShoppingListIngredientMatch(pasta.getId(), List.of(pastaItem.getId()))
        ));

        AiShoppingListProposalResponse result = service.generate(
                fridgeId, userId, new AiShoppingListGenerateRequest(List.of(plannedMealId)));

        assertThat(result.fridgeId()).isEqualTo(fridgeId);
        assertThat(result.plannedMealIds()).containsExactly(plannedMealId);
        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).name()).isEqualTo("Makaron");
        assertThat(result.items().get(0).amount()).isEqualByComparingTo("100");
        assertThat(result.items().get(0).unit()).isEqualTo("GRAM");
        assertThat(result.items().get(0).plannedMealIngredientIds()).containsExactly(pasta.getId());
        assertThat(result.items().get(0).sources()).singleElement().satisfies(source -> {
            assertThat(source.plannedMealIngredientId()).isEqualTo(pasta.getId());
            assertThat(source.amount()).isEqualByComparingTo("100");
        });
        assertThat(result.items().get(1).name()).isEqualTo("Ser");
        assertThat(result.items().get(1).amount()).isEqualByComparingTo("100");
        assertThat(result.items().get(1).plannedMealIngredientIds()).containsExactly(cheese.getId());
        verify(fridgeService).requireMembership(fridgeId, userId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ShoppingListIngredientCandidate>> ingredientCaptor =
                ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ShoppingListFridgeItemCandidate>> itemCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(openAiClient).match(ingredientCaptor.capture(), itemCaptor.capture());
        assertThat(ingredientCaptor.getValue())
                .extracting(ShoppingListIngredientCandidate::amount)
                .containsExactly(new BigDecimal("400"), new BigDecimal("100"));
        assertThat(itemCaptor.getValue()).singleElement().satisfies(candidate ->
                assertThat(candidate.availableAmount()).isEqualByComparingTo("200"));
    }

    @Test
    void generate_retriesWhenAiReturnsUnknownFridgeItem() {
        UUID fridgeId = UUID.randomUUID();
        UUID plannedMealId = UUID.randomUUID();
        PlannedMeal meal = meal(plannedMealId, 2, 2);
        PlannedMealIngredient ingredient = ingredient(meal, "Makaron", "200", "g", false);
        FridgeItem item = fridgeItem("Makaron", "200", Unit.GRAM);

        when(plannedMealRepository.findByIdAndFridgeId(plannedMealId, fridgeId))
                .thenReturn(Optional.of(meal));
        when(fridgeItemRepository.findActiveByFridge(fridgeId)).thenReturn(List.of(item));
        when(reservationRepository.sumReservedAmount(item.getId())).thenReturn(BigDecimal.ZERO);
        when(openAiClient.match(anyList(), anyList())).thenReturn(
                List.of(new ShoppingListIngredientMatch(
                        ingredient.getId(), List.of(UUID.randomUUID()))),
                List.of(new ShoppingListIngredientMatch(
                        ingredient.getId(), List.of(item.getId())))
        );

        AiShoppingListProposalResponse result = service.generate(
                fridgeId, UUID.randomUUID(),
                new AiShoppingListGenerateRequest(List.of(plannedMealId)));

        assertThat(result.items()).isEmpty();
        verify(openAiClient, times(2)).match(anyList(), anyList());
    }

    @Test
    void generate_withoutInventoryReturnsFullListWithoutCallingAi() {
        UUID fridgeId = UUID.randomUUID();
        UUID plannedMealId = UUID.randomUUID();
        PlannedMeal meal = meal(plannedMealId, 2, 2);
        ingredient(meal, "Ryż", "200", "g", false);
        ingredient(meal, "Sól", null, null, false);

        when(plannedMealRepository.findByIdAndFridgeId(plannedMealId, fridgeId))
                .thenReturn(Optional.of(meal));
        when(fridgeItemRepository.findActiveByFridge(fridgeId)).thenReturn(List.of());

        AiShoppingListProposalResponse result = service.generate(
                fridgeId, UUID.randomUUID(),
                new AiShoppingListGenerateRequest(List.of(plannedMealId)));

        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).amount()).isEqualByComparingTo("200");
        assertThat(result.items().get(0).unit()).isEqualTo("GRAM");
        assertThat(result.items().get(1).amount()).isNull();
        assertThat(result.items().get(1).unit()).isNull();
        verify(openAiClient, never()).match(anyList(), anyList());
    }

    @Test
    void generate_aggregatesPieceShortagesBeforeRoundingUp() {
        UUID fridgeId = UUID.randomUUID();
        UUID firstMealId = UUID.randomUUID();
        UUID secondMealId = UUID.randomUUID();
        PlannedMeal firstMeal = meal(firstMealId, 2, 3);
        PlannedMeal secondMeal = meal(secondMealId, 2, 3);
        ingredient(firstMeal, "Jajka", "1", "szt.", false);
        ingredient(secondMeal, "Jajka", "1", "szt.", false);

        when(plannedMealRepository.findByIdAndFridgeId(firstMealId, fridgeId))
                .thenReturn(Optional.of(firstMeal));
        when(plannedMealRepository.findByIdAndFridgeId(secondMealId, fridgeId))
                .thenReturn(Optional.of(secondMeal));
        when(fridgeItemRepository.findActiveByFridge(fridgeId)).thenReturn(List.of());

        AiShoppingListProposalResponse result = service.generate(
                fridgeId,
                UUID.randomUUID(),
                new AiShoppingListGenerateRequest(List.of(firstMealId, secondMealId)));

        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.name()).isEqualTo("Jajka");
            assertThat(item.amount()).isEqualByComparingTo("3");
            assertThat(item.unit()).isEqualTo("PIECE");
            assertThat(item.plannedMealIngredientIds()).hasSize(2);
            assertThat(item.sources())
                    .extracting(source -> source.amount())
                    .containsExactly(new BigDecimal("1.5"), new BigDecimal("1.5"));
        });
        verify(openAiClient, never()).match(anyList(), anyList());
    }

    @Test
    void generate_rejectsDuplicatePlannedMealIds() {
        UUID plannedMealId = UUID.randomUUID();

        assertThatThrownBy(() -> service.generate(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new AiShoppingListGenerateRequest(List.of(plannedMealId, plannedMealId))))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Planned meal IDs must be unique");

        verifyNoInteractions(plannedMealRepository);
    }

    private PlannedMeal meal(UUID id, int recipeServings, int plannedServings) {
        PlannedMeal meal = new PlannedMeal();
        ReflectionTestUtils.setField(meal, "id", id);
        ReflectionTestUtils.setField(meal, "recipeServings", recipeServings);
        meal.setServings(plannedServings);
        return meal;
    }

    private PlannedMealIngredient ingredient(PlannedMeal meal, String name, String amount,
                                             String unit, boolean optional) {
        PlannedMealIngredient ingredient = new PlannedMealIngredient();
        ReflectionTestUtils.setField(ingredient, "id", UUID.randomUUID());
        ingredient.setPlannedMeal(meal);
        ingredient.setName(name);
        ingredient.setAmount(amount == null ? null : new BigDecimal(amount));
        ingredient.setUnit(unit);
        ingredient.setOptional(optional);
        ingredient.setPosition(meal.getIngredients().size());
        meal.getIngredients().add(ingredient);
        return ingredient;
    }

    private FridgeItem fridgeItem(String name, String amount, Unit unit) {
        FridgeItem item = new FridgeItem();
        ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
        item.setCustomName(name);
        item.setAmount(new BigDecimal(amount));
        item.setUnit(unit);
        return item;
    }

    private void reserve(PlannedMealIngredient ingredient, FridgeItem item, String amount) {
        PlannedMealReservation reservation = new PlannedMealReservation();
        ReflectionTestUtils.setField(reservation, "id", UUID.randomUUID());
        reservation.setFridgeItem(item);
        reservation.setAmount(new BigDecimal(amount));
        ingredient.addReservation(reservation);
    }
}
