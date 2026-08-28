package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.dto.RecipeIngredientRequest;
import io.github.mkliszczun.fridge.dto.RecipeRequest;
import io.github.mkliszczun.fridge.exception.ConflictException;
import io.github.mkliszczun.fridge.exception.NotFoundException;
import io.github.mkliszczun.fridge.recipe.Recipe;
import io.github.mkliszczun.fridge.recipe.RecipeIngredient;
import io.github.mkliszczun.fridge.repository.PlannedMealRepository;
import io.github.mkliszczun.fridge.repository.RecipeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecipeServiceImplTest {

    @Mock
    private RecipeRepository repository;

    @Mock
    private PlannedMealRepository plannedMealRepository;

    @InjectMocks
    private RecipeServiceImpl service;

    @Test
    void create_assignsOwnerAndPreservesIngredientOrder() {
        UUID ownerId = UUID.randomUUID();
        RecipeRequest request = request("Carbonara");
        when(repository.save(any(Recipe.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Recipe result = service.create(ownerId, request);

        ArgumentCaptor<Recipe> captor = ArgumentCaptor.forClass(Recipe.class);
        verify(repository).save(captor.capture());
        Recipe saved = captor.getValue();
        assertThat(result).isSameAs(saved);
        assertThat(saved.getOwnerUserId()).isEqualTo(ownerId);
        assertThat(saved.getName()).isEqualTo("Carbonara");
        assertThat(saved.getServings()).isEqualTo(2);
        assertThat(saved.getIngredients()).hasSize(2);
        assertThat(saved.getIngredients().get(0).getName()).isEqualTo("Makaron");
        assertThat(saved.getIngredients().get(0).getPosition()).isZero();
        assertThat(saved.getIngredients().get(0).getRecipe()).isSameAs(saved);
        assertThat(saved.getIngredients().get(1).getPosition()).isEqualTo(1);
    }

    @Test
    void update_replacesRecipeDataAndIngredients() {
        UUID recipeId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Recipe existing = new Recipe();
        existing.setOwnerUserId(ownerId);
        existing.setName("Old name");
        existing.setInstructions("Old instructions");
        existing.setServings(1);
        RecipeIngredient oldIngredient = new RecipeIngredient();
        oldIngredient.setName("Old ingredient");
        existing.replaceIngredients(List.of(oldIngredient));

        when(repository.findByIdAndOwnerUserId(recipeId, ownerId)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        Recipe result = service.update(recipeId, ownerId, request("Carbonara"));

        assertThat(result.getName()).isEqualTo("Carbonara");
        assertThat(result.getIngredients()).extracting(RecipeIngredient::getName)
                .containsExactly("Makaron", "Jajko");
        verify(repository).save(existing);
    }

    @Test
    void get_returnsOnlyRecipeOwnedByUser() {
        UUID recipeId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Recipe recipe = new Recipe();
        when(repository.findByIdAndOwnerUserId(recipeId, ownerId)).thenReturn(Optional.of(recipe));

        assertThat(service.get(recipeId, ownerId)).isSameAs(recipe);
    }

    @Test
    void get_missingOrForeignRecipeThrowsNotFound() {
        UUID recipeId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(repository.findByIdAndOwnerUserId(recipeId, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(recipeId, ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Recipe not found");
    }

    @Test
    void delete_removesOwnedRecipe() {
        UUID recipeId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Recipe recipe = new Recipe();
        when(repository.findByIdAndOwnerUserId(recipeId, ownerId)).thenReturn(Optional.of(recipe));

        service.delete(recipeId, ownerId);

        verify(repository).delete(recipe);
    }

    @Test
    void delete_doesNotRemoveForeignRecipe() {
        UUID recipeId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(repository.findByIdAndOwnerUserId(recipeId, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(recipeId, ownerId))
                .isInstanceOf(NotFoundException.class);
        verify(repository, never()).delete(any());
    }

    @Test
    void delete_plannedRecipeThrowsConflict() {
        UUID recipeId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Recipe recipe = new Recipe();
        when(repository.findByIdAndOwnerUserId(recipeId, ownerId)).thenReturn(Optional.of(recipe));
        when(plannedMealRepository.existsByRecipeId(recipeId)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(recipeId, ownerId))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Recipe is used in a planned meal");
        verify(repository, never()).delete(any());
    }

    private RecipeRequest request(String name) {
        return new RecipeRequest(
                name,
                "Klasyczny makaron",
                "Ugotuj makaron i połącz składniki.",
                2,
                List.of(
                        new RecipeIngredientRequest("Makaron", new BigDecimal("200"), "g", false, null),
                        new RecipeIngredientRequest("Jajko", new BigDecimal("2"), "szt.", false, null)
                )
        );
    }
}
