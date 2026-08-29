package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.dto.AiMealPlanGenerateRequest;
import io.github.mkliszczun.fridge.dto.AiMealPlanProposalResponse;
import io.github.mkliszczun.fridge.dto.RecipeIngredientRequest;
import io.github.mkliszczun.fridge.dto.RecipeRequest;
import io.github.mkliszczun.fridge.exception.InvalidAiResponseException;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiMealPlanServiceImplTest {

    @Mock
    private OpenAiMealPlanClient openAiClient;

    @Mock
    private FridgeService fridgeService;

    @Mock
    private Validator validator;

    @InjectMocks
    private AiMealPlanServiceImpl service;

    @Test
    void generate_returnsValidatedProposalWithoutPersistingAnything() {
        UUID fridgeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AiMealPlanGenerateRequest request = request(2);
        RecipeRequest recipe = recipe(2);
        when(openAiClient.generate(request)).thenReturn(recipe);
        when(validator.validate(recipe)).thenReturn(Collections.emptySet());

        AiMealPlanProposalResponse result = service.generate(fridgeId, userId, request);

        assertThat(result.fridgeId()).isEqualTo(fridgeId);
        assertThat(result.plannedDate()).isEqualTo(request.plannedDate());
        assertThat(result.recipe()).isSameAs(recipe);
        verify(fridgeService).requireMembership(fridgeId, userId);
    }

    @Test
    void generate_retriesOnceAfterInvalidAiResponse() {
        UUID fridgeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AiMealPlanGenerateRequest request = request(2);
        RecipeRequest recipe = recipe(2);
        when(openAiClient.generate(request))
                .thenThrow(new InvalidAiResponseException("Invalid"))
                .thenReturn(recipe);
        when(validator.validate(recipe)).thenReturn(Collections.emptySet());

        AiMealPlanProposalResponse result = service.generate(fridgeId, userId, request);

        assertThat(result.recipe()).isSameAs(recipe);
        verify(openAiClient, times(2)).generate(request);
    }

    @Test
    void generate_failsAfterTwoRecipesWithWrongServings() {
        AiMealPlanGenerateRequest request = request(2);
        RecipeRequest recipe = recipe(3);
        when(openAiClient.generate(request)).thenReturn(recipe);
        when(validator.validate(recipe)).thenReturn(Collections.emptySet());

        assertThatThrownBy(() -> service.generate(UUID.randomUUID(), UUID.randomUUID(), request))
                .isInstanceOf(InvalidAiResponseException.class)
                .hasMessage("AI returned an invalid recipe");
        verify(openAiClient, times(2)).generate(request);
    }

    private AiMealPlanGenerateRequest request(int servings) {
        return new AiMealPlanGenerateRequest(
                LocalDate.now().plusDays(1),
                servings,
                null,
                null,
                null
        );
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
