package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.dto.AiMealPlanFromRecipesGenerateRequest;
import io.github.mkliszczun.fridge.dto.AiMealPlanFromRecipesProposalResponse;
import io.github.mkliszczun.fridge.exception.ConflictException;
import io.github.mkliszczun.fridge.recipe.Recipe;
import io.github.mkliszczun.fridge.repository.RecipeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiMealPlanFromRecipesServiceImplTest {

    @Mock
    private OpenAiMealPlanClient openAiClient;

    @Mock
    private RecipeRepository recipeRepository;

    @Mock
    private FridgeService fridgeService;

    @InjectMocks
    private AiMealPlanFromRecipesServiceImpl service;

    @Test
    void generate_mapsSelectedRecipesToConsecutiveDaysWithoutSavingAnything() {
        UUID fridgeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Recipe firstRecipe = recipe("Zupa pomidorowa");
        Recipe secondRecipe = recipe("Makaron z pesto");
        AiMealPlanFromRecipesGenerateRequest request = request(2);
        when(recipeRepository.findAllByOwnerUserIdOrderByNameAsc(userId))
                .thenReturn(List.of(firstRecipe, secondRecipe));
        when(openAiClient.generate(eq(request), anyList()))
                .thenReturn(List.of(firstRecipe.getId(), secondRecipe.getId()));

        AiMealPlanFromRecipesProposalResponse result = service.generate(fridgeId, userId, request);

        assertThat(result.fridgeId()).isEqualTo(fridgeId);
        assertThat(result.meals()).hasSize(2);
        assertThat(result.meals().get(0).plannedDate()).isEqualTo(request.startDate());
        assertThat(result.meals().get(0).recipeId()).isEqualTo(firstRecipe.getId());
        assertThat(result.meals().get(1).plannedDate()).isEqualTo(request.startDate().plusDays(1));
        assertThat(result.meals().get(1).recipeId()).isEqualTo(secondRecipe.getId());
        assertThat(result.meals()).allMatch(meal -> meal.servings().equals(3));
        verify(fridgeService).requireMembership(fridgeId, userId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MealPlanRecipeCandidate>> candidatesCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(openAiClient).generate(eq(request), candidatesCaptor.capture());
        assertThat(candidatesCaptor.getValue())
                .extracting(MealPlanRecipeCandidate::id)
                .containsExactly(firstRecipe.getId(), secondRecipe.getId());
    }

    @Test
    void generate_retriesWhenAiUnnecessarilyRepeatsRecipe() {
        UUID userId = UUID.randomUUID();
        Recipe firstRecipe = recipe("Zupa pomidorowa");
        Recipe secondRecipe = recipe("Makaron z pesto");
        AiMealPlanFromRecipesGenerateRequest request = request(2);
        when(recipeRepository.findAllByOwnerUserIdOrderByNameAsc(userId))
                .thenReturn(List.of(firstRecipe, secondRecipe));
        when(openAiClient.generate(eq(request), anyList()))
                .thenReturn(
                        List.of(firstRecipe.getId(), firstRecipe.getId()),
                        List.of(firstRecipe.getId(), secondRecipe.getId())
                );

        AiMealPlanFromRecipesProposalResponse result =
                service.generate(UUID.randomUUID(), userId, request);

        assertThat(result.meals()).extracting(meal -> meal.recipeId())
                .containsExactly(firstRecipe.getId(), secondRecipe.getId());
        verify(openAiClient, times(2)).generate(eq(request), anyList());
    }

    @Test
    void generate_allowsRepeatingRecipesWhenThereAreFewerRecipesThanDays() {
        UUID userId = UUID.randomUUID();
        Recipe recipe = recipe("Zupa pomidorowa");
        AiMealPlanFromRecipesGenerateRequest request = request(2);
        when(recipeRepository.findAllByOwnerUserIdOrderByNameAsc(userId))
                .thenReturn(List.of(recipe));
        when(openAiClient.generate(eq(request), anyList()))
                .thenReturn(List.of(recipe.getId(), recipe.getId()));

        AiMealPlanFromRecipesProposalResponse result =
                service.generate(UUID.randomUUID(), userId, request);

        assertThat(result.meals()).extracting(meal -> meal.recipeId())
                .containsExactly(recipe.getId(), recipe.getId());
    }

    @Test
    void generate_failsBeforeCallingAiWhenUserHasNoRecipes() {
        UUID userId = UUID.randomUUID();
        when(recipeRepository.findAllByOwnerUserIdOrderByNameAsc(userId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.generate(UUID.randomUUID(), userId, request(2)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("No recipes available for meal planning");
        verify(openAiClient, never()).generate(eq(request(2)), anyList());
    }

    private AiMealPlanFromRecipesGenerateRequest request(int days) {
        return new AiMealPlanFromRecipesGenerateRequest(
                LocalDate.now().plusDays(1),
                days,
                3,
                "Lekkie obiady"
        );
    }

    private Recipe recipe(String name) {
        Recipe recipe = new Recipe();
        ReflectionTestUtils.setField(recipe, "id", UUID.randomUUID());
        recipe.setName(name);
        recipe.setDescription("Opis " + name);
        recipe.setInstructions("Instrukcja");
        recipe.setServings(2);
        return recipe;
    }
}
