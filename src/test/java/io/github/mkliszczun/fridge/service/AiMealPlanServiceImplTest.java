package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.dto.AiMealPlanGenerateRequest;
import io.github.mkliszczun.fridge.dto.AiMealPlanProposalResponse;
import io.github.mkliszczun.fridge.dto.AiRecipeGenerateRequest;
import io.github.mkliszczun.fridge.dto.RecipeIngredientRequest;
import io.github.mkliszczun.fridge.dto.RecipeRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiMealPlanServiceImplTest {

    @Mock
    private AiRecipeService aiRecipeService;

    @Mock
    private FridgeService fridgeService;

    @InjectMocks
    private AiMealPlanServiceImpl service;

    @Test
    void generate_requiresMembershipAndDelegatesRecipeGeneration() {
        UUID fridgeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        RecipeRequest previousProposal = recipe(2);
        AiMealPlanGenerateRequest request = new AiMealPlanGenerateRequest(
                LocalDate.now().plusDays(1),
                2,
                "Szybki obiad",
                previousProposal,
                "Bez pomidorów"
        );
        RecipeRequest recipe = recipe(2);
        when(aiRecipeService.generate(any(), eq(previousProposal), eq("Bez pomidorów")))
                .thenReturn(recipe);

        AiMealPlanProposalResponse result = service.generate(fridgeId, userId, request);

        assertThat(result.fridgeId()).isEqualTo(fridgeId);
        assertThat(result.plannedDate()).isEqualTo(request.plannedDate());
        assertThat(result.recipe()).isSameAs(recipe);
        verify(fridgeService).requireMembership(fridgeId, userId);
        ArgumentCaptor<AiRecipeGenerateRequest> requestCaptor =
                ArgumentCaptor.forClass(AiRecipeGenerateRequest.class);
        verify(aiRecipeService).generate(
                requestCaptor.capture(),
                eq(previousProposal),
                eq("Bez pomidorów")
        );
        assertThat(requestCaptor.getValue().servings()).isEqualTo(2);
        assertThat(requestCaptor.getValue().guidelines()).isEqualTo("Szybki obiad");
    }

    private RecipeRequest recipe(int servings) {
        return new RecipeRequest(
                "Makaron z pesto",
                "Szybki obiad",
                "Ugotuj makaron i wymieszaj z pesto.",
                servings,
                List.of(new RecipeIngredientRequest(
                        "Makaron",
                        new BigDecimal("200"),
                        "g",
                        false,
                        null
                ))
        );
    }
}
