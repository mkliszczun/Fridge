package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.dto.AiRecipeGenerateRequest;
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
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiRecipeServiceImplTest {

    @Mock
    private OpenAiRecipeClient openAiClient;

    @Mock
    private Validator validator;

    @InjectMocks
    private AiRecipeServiceImpl service;

    @Test
    void generate_returnsValidatedRecipe() {
        AiRecipeGenerateRequest request = request(2);
        RecipeRequest recipe = recipe(2);
        when(openAiClient.generate(request, null, null)).thenReturn(recipe);
        when(validator.validate(recipe)).thenReturn(Collections.emptySet());

        RecipeRequest result = service.generate(request);

        assertThat(result).isSameAs(recipe);
    }

    @Test
    void generate_retriesOnceAfterInvalidAiResponse() {
        AiRecipeGenerateRequest request = request(2);
        RecipeRequest recipe = recipe(2);
        when(openAiClient.generate(request, null, null))
                .thenThrow(new InvalidAiResponseException("Invalid"))
                .thenReturn(recipe);
        when(validator.validate(recipe)).thenReturn(Collections.emptySet());

        RecipeRequest result = service.generate(request);

        assertThat(result).isSameAs(recipe);
        verify(openAiClient, times(2)).generate(request, null, null);
    }

    @Test
    void generate_failsAfterTwoRecipesWithWrongServings() {
        AiRecipeGenerateRequest request = request(2);
        RecipeRequest recipe = recipe(3);
        when(openAiClient.generate(request, null, null)).thenReturn(recipe);
        when(validator.validate(recipe)).thenReturn(Collections.emptySet());

        assertThatThrownBy(() -> service.generate(request))
                .isInstanceOf(InvalidAiResponseException.class)
                .hasMessage("AI returned an invalid recipe");
        verify(openAiClient, times(2)).generate(request, null, null);
    }

    private AiRecipeGenerateRequest request(int servings) {
        return new AiRecipeGenerateRequest(servings, null);
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
