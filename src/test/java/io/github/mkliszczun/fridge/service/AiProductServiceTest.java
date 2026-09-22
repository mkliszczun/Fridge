package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.dto.*;
import io.github.mkliszczun.fridge.entity.DefaultExpirationDays;
import io.github.mkliszczun.fridge.enums.ProductType;
import io.github.mkliszczun.fridge.enums.Unit;
import io.github.mkliszczun.fridge.exception.AiServiceUnavailableException;
import io.github.mkliszczun.fridge.exception.InvalidAiResponseException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class AiProductServiceTest {
    private final OpenAiProductClient client = mock(OpenAiProductClient.class);
    private final DefaultExpirationDaysService defaults = mock(DefaultExpirationDaysService.class);
    private ValidatorFactory factory;
    private AiProductService service;

    @BeforeEach void setup() {
        factory = Validation.buildDefaultValidatorFactory();
        service = new AiProductService(client, factory.getValidator(), defaults);
        when(defaults.getByProductType(ProductType.DAIRY)).thenReturn(Optional.of(
                DefaultExpirationDays.builder().productType(ProductType.DAIRY).defaultExpirationDays(7).expirationDaysAfterOpening(3).build()));
    }
    @AfterEach void close() { factory.close(); }

    @Test void nameOnlyGeneratesDraftAndUsesCategoryDefault() {
        when(client.generate(any(), any())).thenReturn(new AiProductSuggestion(null, ProductType.DAIRY, Unit.MILLILITER, 3));
        var result = service.generate(request());
        assertThat(result.name()).isEqualTo("Mleko");
        assertThat(result.productType()).isEqualTo(ProductType.DAIRY);
        assertThat(result.defaultUnit()).isEqualTo(Unit.MILLILITER);
        assertThat(result.shelfLifeAfterOpeningDays()).isEqualTo(3);
        assertThat(result.defaultExpirationDays()).isEqualTo(7);
        assertThat(result.brand()).isNull();
        verify(defaults, never()).updateDefaultExpiration(any(), any());
        verify(defaults, never()).updateDaysAfterOpeningForType(any(), any());
    }

    @Test void preservesManualValuesIncludingZeroAndPassesOffContext() {
        var off = new AiProductGenerateRequest.OffData("Mleko z OFF", "OFF marka", List.of("en:dairies"));
        var request = new AiProductGenerateRequest(" Moja nazwa ", "123", " Moja marka ", ProductType.MEAT, Unit.GRAM, 0, off);
        when(client.generate(eq(request), any())).thenReturn(new AiProductSuggestion("AI marka", ProductType.DAIRY, Unit.MILLILITER, 5));
        var result = service.generate(request);
        assertThat(result.name()).isEqualTo("Moja nazwa");
        assertThat(result.ean()).isEqualTo("123");
        assertThat(result.brand()).isEqualTo("Moja marka");
        assertThat(result.productType()).isEqualTo(ProductType.MEAT);
        assertThat(result.defaultUnit()).isEqualTo(Unit.GRAM);
        assertThat(result.shelfLifeAfterOpeningDays()).isZero();
        verify(client).generate(eq(request), argThat(list -> list.get(0).defaultExpirationDays().equals(7)));
    }

    @Test void offBrandWinsOverInventedBrandAndUnknownOpeningDaysStayNull() {
        var request = new AiProductGenerateRequest("Mleko", "123", null, null, null, null,
                new AiProductGenerateRequest.OffData("Mleko", " Pilos ", List.of()));
        when(client.generate(any(), any())).thenReturn(new AiProductSuggestion("Wrong", ProductType.DAIRY, Unit.MILLILITER, null));
        var result = service.generate(request);
        assertThat(result.brand()).isEqualTo("Pilos");
        assertThat(result.shelfLifeAfterOpeningDays()).isNull();
    }

    @Test void invalidProposalRetriesOnceThenReturnsValidProposal() {
        when(client.generate(any(), any()))
                .thenReturn(new AiProductSuggestion(null, ProductType.DAIRY, Unit.GRAM, -1))
                .thenReturn(new AiProductSuggestion(null, ProductType.DAIRY, Unit.GRAM, 0));
        assertThat(service.generate(request()).shelfLifeAfterOpeningDays()).isZero();
        verify(client, times(2)).generate(any(), any());
    }

    @Test void invalidProposalCannotEscapeValidationAfterRetry() {
        when(client.generate(any(), any())).thenReturn(new AiProductSuggestion(null, ProductType.DAIRY, null, 5000));
        assertThatThrownBy(() -> service.generate(request())).isInstanceOf(InvalidAiResponseException.class);
        verify(client, times(2)).generate(any(), any());
    }

    @Test void providerFailureDoesNotRetryOrReturnPartialData() {
        when(client.generate(any(), any())).thenThrow(new AiServiceUnavailableException("unavailable"));
        assertThatThrownBy(() -> service.generate(request())).isInstanceOf(AiServiceUnavailableException.class);
        verify(client).generate(any(), any());
    }
    private AiProductGenerateRequest request() { return new AiProductGenerateRequest(" Mleko ", null, null, null, null, null, null); }
}
