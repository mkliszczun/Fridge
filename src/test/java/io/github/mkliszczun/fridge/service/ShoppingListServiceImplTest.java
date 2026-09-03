package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.dto.ShoppingListImportRequest;
import io.github.mkliszczun.fridge.exception.NotFoundException;
import io.github.mkliszczun.fridge.fridge.Fridge;
import io.github.mkliszczun.fridge.mealplan.PlannedMeal;
import io.github.mkliszczun.fridge.mealplan.PlannedMealIngredient;
import io.github.mkliszczun.fridge.repository.PlannedMealIngredientRepository;
import io.github.mkliszczun.fridge.repository.ShoppingListItemRepository;
import io.github.mkliszczun.fridge.shoppinglist.ShoppingListItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShoppingListServiceImplTest {

    @Mock
    private ShoppingListItemRepository itemRepository;

    @Mock
    private PlannedMealIngredientRepository ingredientRepository;

    @Mock
    private FridgeService fridgeService;

    @InjectMocks
    private ShoppingListServiceImpl service;

    @Test
    void addItem_mergesAmountIntoMatchingUncheckedItem() {
        UUID fridgeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Fridge fridge = fridge(fridgeId);
        ShoppingListItem existing = item(fridge, "Mleko", "ml", "100");
        when(fridgeService.requireMembership(fridgeId, userId)).thenReturn(fridge);
        when(itemRepository.findAllByFridgeIdOrderByCheckedAscCreatedAtAsc(fridgeId))
                .thenReturn(List.of(existing));
        when(itemRepository.save(existing)).thenReturn(existing);

        ShoppingListItem result = service.addItem(
                fridgeId, userId, " mleko ", new BigDecimal("50"), "ML");

        assertThat(result).isSameAs(existing);
        assertThat(result.getManualAmount()).isEqualByComparingTo("150");
        verify(itemRepository).save(existing);
    }

    @Test
    void importProposal_isIdempotentAndMergesNewIngredientSources() {
        UUID fridgeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Fridge fridge = fridge(fridgeId);
        PlannedMealIngredient first = ingredient(fridge);
        PlannedMealIngredient second = ingredient(fridge);
        List<ShoppingListItem> storedItems = new ArrayList<>();
        when(fridgeService.requireMembership(fridgeId, userId)).thenReturn(fridge);
        when(ingredientRepository.findAllById(any())).thenAnswer(invocation -> {
            Iterable<UUID> requested = invocation.getArgument(0);
            List<PlannedMealIngredient> found = new ArrayList<>();
            requested.forEach(id -> {
                if (id.equals(first.getId())) found.add(first);
                if (id.equals(second.getId())) found.add(second);
            });
            return found;
        });
        when(itemRepository.findAllByFridgeIdOrderByCheckedAscCreatedAtAsc(fridgeId))
                .thenAnswer(ignored -> new ArrayList<>(storedItems));
        when(itemRepository.saveAll(any())).thenAnswer(invocation -> {
            Iterable<ShoppingListItem> savedItems = invocation.getArgument(0);
            List<ShoppingListItem> saved = new ArrayList<>();
            savedItems.forEach(item -> {
                saved.add(item);
                if (!storedItems.contains(item)) storedItems.add(item);
            });
            return saved;
        });

        ShoppingListImportRequest firstRequest = importRequest(
                "Jajka", "PIECE", first.getId(), "1.5");
        service.importProposal(fridgeId, userId, firstRequest);
        service.importProposal(fridgeId, userId, firstRequest);
        service.importProposal(fridgeId, userId, importRequest(
                "jajka", "piece", second.getId(), "1.5"));

        assertThat(storedItems).singleElement().satisfies(item -> {
            assertThat(item.getSources()).hasSize(2);
            assertThat(item.getSources())
                    .extracting(source -> source.getContributionAmount())
                    .containsExactly(new BigDecimal("1.5"), new BigDecimal("1.5"));
        });
        verify(itemRepository, times(2)).saveAll(any());
    }

    @Test
    void importProposal_rejectsIngredientFromAnotherFridge() {
        UUID fridgeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Fridge fridge = fridge(fridgeId);
        PlannedMealIngredient foreignIngredient = ingredient(fridge(UUID.randomUUID()));
        when(fridgeService.requireMembership(fridgeId, userId)).thenReturn(fridge);
        when(ingredientRepository.findAllById(any()))
                .thenReturn(List.of(foreignIngredient));

        assertThatThrownBy(() -> service.importProposal(
                fridgeId,
                userId,
                importRequest("Mleko", "MILLILITER", foreignIngredient.getId(), "500")))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("ingredient");

        verify(itemRepository, never())
                .findAllByFridgeIdOrderByCheckedAscCreatedAtAsc(fridgeId);
    }

    private ShoppingListImportRequest importRequest(
            String name, String unit, UUID ingredientId, String amount) {
        return new ShoppingListImportRequest(List.of(
                new ShoppingListImportRequest.Item(
                        name,
                        unit,
                        List.of(new ShoppingListImportRequest.Source(
                                ingredientId, new BigDecimal(amount)))
                )
        ));
    }

    private ShoppingListItem item(
            Fridge fridge, String name, String unit, String manualAmount) {
        ShoppingListItem item = new ShoppingListItem();
        item.setFridge(fridge);
        item.setName(name);
        item.setUnit(unit);
        item.setQuantified(true);
        item.setManualAmount(new BigDecimal(manualAmount));
        return item;
    }

    private PlannedMealIngredient ingredient(Fridge fridge) {
        PlannedMeal meal = new PlannedMeal();
        meal.setFridge(fridge);
        PlannedMealIngredient ingredient = new PlannedMealIngredient();
        ReflectionTestUtils.setField(ingredient, "id", UUID.randomUUID());
        ingredient.setPlannedMeal(meal);
        ingredient.setName("Ingredient");
        return ingredient;
    }

    private Fridge fridge(UUID id) {
        Fridge fridge = new Fridge();
        ReflectionTestUtils.setField(fridge, "id", id);
        fridge.setName("Dom");
        return fridge;
    }
}
