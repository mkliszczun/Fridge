package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.dto.AiMealPlanFromRecipesGenerateRequest;
import io.github.mkliszczun.fridge.dto.AiMealPlanFromRecipesProposalResponse;
import io.github.mkliszczun.fridge.enums.Unit;
import io.github.mkliszczun.fridge.exception.ConflictException;
import io.github.mkliszczun.fridge.fridge.FridgeItem;
import io.github.mkliszczun.fridge.recipe.Recipe;
import io.github.mkliszczun.fridge.recipe.RecipeIngredient;
import io.github.mkliszczun.fridge.repository.FridgeItemRepository;
import io.github.mkliszczun.fridge.repository.PlannedMealReservationRepository;
import io.github.mkliszczun.fridge.repository.RecipeRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiMealPlanFromRecipesWithFridgeServiceImplTest {

    @Mock
    private OpenAiMealPlanWithFridgeClient openAiClient;

    @Mock
    private RecipeRepository recipeRepository;

    @Mock
    private FridgeItemRepository fridgeItemRepository;

    @Mock
    private PlannedMealReservationRepository reservationRepository;

    @Mock
    private FridgeService fridgeService;

    @InjectMocks
    private AiMealPlanFromRecipesWithFridgeServiceImpl service;

    @Test
    void generate_sendsRecipesAndAvailableInventoryOrderedByExpiration() {
        UUID fridgeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Recipe firstRecipe = recipe("Makaron z serem", "Makaron", "200", "g");
        Recipe secondRecipe = recipe("Omlet", "Jajka", "3", "szt.");
        FridgeItem laterItem = fridgeItem(
                "Makaron pełnoziarnisty", "300", Unit.GRAM, LocalDate.now().plusDays(5));
        FridgeItem urgentItem = fridgeItem(
                "Jajka", "4", Unit.PIECE, LocalDate.now().plusDays(1));
        FridgeItem fullyReservedItem = fridgeItem(
                "Ser", "100", Unit.GRAM, LocalDate.now().plusDays(2));
        AiMealPlanFromRecipesGenerateRequest request = request(2);

        when(recipeRepository.findAllByOwnerUserIdOrderByNameAsc(userId))
                .thenReturn(List.of(firstRecipe, secondRecipe));
        when(fridgeItemRepository.findActiveByFridge(fridgeId))
                .thenReturn(List.of(laterItem, fullyReservedItem, urgentItem));
        when(reservationRepository.sumReservedAmount(laterItem.getId()))
                .thenReturn(new BigDecimal("50"));
        when(reservationRepository.sumReservedAmount(urgentItem.getId()))
                .thenReturn(BigDecimal.ZERO);
        when(reservationRepository.sumReservedAmount(fullyReservedItem.getId()))
                .thenReturn(new BigDecimal("100"));
        when(openAiClient.generate(eq(request), anyList(), anyList()))
                .thenReturn(List.of(secondRecipe.getId(), firstRecipe.getId()));

        AiMealPlanFromRecipesProposalResponse result = service.generate(
                fridgeId, userId, request);

        assertThat(result.meals()).extracting(meal -> meal.recipeId())
                .containsExactly(secondRecipe.getId(), firstRecipe.getId());
        assertThat(result.meals().get(0).plannedDate()).isEqualTo(request.startDate());
        assertThat(result.meals()).allMatch(meal -> meal.servings().equals(3));
        verify(fridgeService).requireMembership(fridgeId, userId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MealPlanWithFridgeRecipeCandidate>> recipeCaptor =
                ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MealPlanFridgeItemCandidate>> itemCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(openAiClient).generate(eq(request), recipeCaptor.capture(), itemCaptor.capture());

        assertThat(recipeCaptor.getValue().get(0).ingredients()).singleElement().satisfies(
                ingredient -> {
                    assertThat(ingredient.name()).isEqualTo("Makaron");
                    assertThat(ingredient.amount()).isEqualByComparingTo("200");
                    assertThat(ingredient.unit()).isEqualTo("g");
                });
        assertThat(itemCaptor.getValue())
                .extracting(MealPlanFridgeItemCandidate::id)
                .containsExactly(urgentItem.getId(), laterItem.getId());
        assertThat(itemCaptor.getValue().get(1).availableAmount()).isEqualByComparingTo("250");
    }

    @Test
    void generate_retriesWhenAiReturnsUnknownRecipe() {
        UUID fridgeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Recipe recipe = recipe("Omlet", "Jajka", "3", "szt.");
        AiMealPlanFromRecipesGenerateRequest request = request(1);
        when(recipeRepository.findAllByOwnerUserIdOrderByNameAsc(userId))
                .thenReturn(List.of(recipe));
        when(fridgeItemRepository.findActiveByFridge(fridgeId)).thenReturn(List.of());
        when(openAiClient.generate(eq(request), anyList(), anyList()))
                .thenReturn(List.of(UUID.randomUUID()), List.of(recipe.getId()));

        AiMealPlanFromRecipesProposalResponse result = service.generate(
                fridgeId, userId, request);

        assertThat(result.meals()).singleElement().satisfies(meal ->
                assertThat(meal.recipeId()).isEqualTo(recipe.getId()));
        verify(openAiClient, times(2)).generate(eq(request), anyList(), anyList());
    }

    @Test
    void generate_failsBeforeLoadingInventoryWhenUserHasNoRecipes() {
        UUID userId = UUID.randomUUID();
        when(recipeRepository.findAllByOwnerUserIdOrderByNameAsc(userId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.generate(UUID.randomUUID(), userId, request(2)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("No recipes available for meal planning");

        verifyNoInteractions(fridgeItemRepository, reservationRepository);
        verify(openAiClient, never()).generate(any(), anyList(), anyList());
    }

    private AiMealPlanFromRecipesGenerateRequest request(int days) {
        return new AiMealPlanFromRecipesGenerateRequest(
                LocalDate.now().plusDays(1),
                days,
                3,
                "Lekkie obiady"
        );
    }

    private Recipe recipe(String name, String ingredientName, String amount, String unit) {
        Recipe recipe = new Recipe();
        ReflectionTestUtils.setField(recipe, "id", UUID.randomUUID());
        recipe.setName(name);
        recipe.setDescription("Opis " + name);
        recipe.setInstructions("Instrukcja");
        recipe.setServings(2);

        RecipeIngredient ingredient = new RecipeIngredient();
        ingredient.setName(ingredientName);
        ingredient.setAmount(new BigDecimal(amount));
        ingredient.setUnit(unit);
        ingredient.setOptional(false);
        recipe.replaceIngredients(List.of(ingredient));
        return recipe;
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
}
