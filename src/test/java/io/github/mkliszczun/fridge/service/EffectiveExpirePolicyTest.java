package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.entity.DefaultExpirationDays;
import io.github.mkliszczun.fridge.enums.ProductType;
import io.github.mkliszczun.fridge.fridge.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EffectiveExpirePolicyTest {

    @Mock
    DefaultExpirationDaysService defaultExpirationDaysService;

    EffectiveExpirePolicy policy;

    @BeforeEach
    void setUp() {
        policy = new EffectiveExpirePolicy(defaultExpirationDaysService);
    }

    @Test
    void usesBestBeforeDateForSealedItem() {
        Product product = product(ProductType.DAIRY, null);
        LocalDate bestBeforeDate = LocalDate.of(2026, 9, 10);

        LocalDate result = policy.computeEffectiveExpireAt(
                bestBeforeDate, null, product, null, null);

        assertThat(result).isEqualTo(bestBeforeDate);
    }

    @Test
    void usesProductTypeDefaultWhenBestBeforeDateIsMissing() {
        Product product = product(ProductType.DAIRY, null);
        when(defaultExpirationDaysService.getByProductType(ProductType.DAIRY))
                .thenReturn(Optional.of(defaults(ProductType.DAIRY, 14, 3)));
        LocalDate earliestExpected = LocalDate.now().plusDays(14);

        LocalDate result = policy.computeEffectiveExpireAt(
                null, null, product, null, null);

        LocalDate latestExpected = LocalDate.now().plusDays(14);
        assertThat(result).isBetween(earliestExpected, latestExpected);
    }

    @Test
    void productAfterOpeningDaysShortenExpiration() {
        Product product = product(ProductType.DAIRY, 3);
        LocalDate openDate = LocalDate.of(2026, 9, 1);

        LocalDate result = policy.computeEffectiveExpireAt(
                LocalDate.of(2026, 9, 10), openDate, product, null, null);

        assertThat(result).isEqualTo(LocalDate.of(2026, 9, 4));
    }

    @Test
    void openingNeverExtendsExistingExpiration() {
        Product product = product(ProductType.DAIRY, 7);
        LocalDate bestBeforeDate = LocalDate.of(2026, 9, 2);

        LocalDate result = policy.computeEffectiveExpireAt(
                bestBeforeDate, LocalDate.of(2026, 9, 1), product, null, null);

        assertThat(result).isEqualTo(bestBeforeDate);
    }

    @Test
    void usesProductTypeDefaultAfterOpeningWhenProductOverrideIsMissing() {
        Product product = product(ProductType.DAIRY, null);
        when(defaultExpirationDaysService.getByProductType(ProductType.DAIRY))
                .thenReturn(Optional.of(defaults(ProductType.DAIRY, 14, 3)));

        LocalDate result = policy.computeEffectiveExpireAt(
                LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 1),
                product, null, null);

        assertThat(result).isEqualTo(LocalDate.of(2026, 9, 4));
    }

    @Test
    void customItemWithoutDateReturnsNullInsteadOfThrowing() {
        LocalDate result = policy.computeEffectiveExpireAt(
                null, null, null, null, null);

        assertThat(result).isNull();
    }

    private Product product(ProductType productType, Integer afterOpeningDays) {
        Product product = new Product();
        product.setProductType(productType);
        product.setShelfLifeAfterOpeningDays(afterOpeningDays);
        return product;
    }

    private DefaultExpirationDays defaults(ProductType productType,
                                           Integer defaultDays,
                                           Integer afterOpeningDays) {
        return DefaultExpirationDays.builder()
                .productType(productType)
                .defaultExpirationDays(defaultDays)
                .expirationDaysAfterOpening(afterOpeningDays)
                .build();
    }
}
