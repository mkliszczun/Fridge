package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.enums.ProductType;
import io.github.mkliszczun.fridge.exception.ParsingProductFromApiException;
import io.github.mkliszczun.fridge.fridge.Product;
import io.github.mkliszczun.fridge.off.OffClient;
import io.github.mkliszczun.fridge.repository.ProductRepository;
import io.github.mkliszczun.fridge.enums.Unit;
import jakarta.persistence.EntityNotFoundException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductServiceImpl productService;

    @Mock
    private OffClient offClient;


    private Product sampleProduct;
    private final UUID productId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        sampleProduct = new Product();
        sampleProduct.setName("Milk");
        sampleProduct.setProductType(ProductType.DAIRY);
        sampleProduct.setEan("5901234567890");
        sampleProduct.setDefaultUnit(Unit.MILLILITER);
    }

    @Test
    void createProduct_shouldPersistAndReturnSavedEntity() {
        // given
        Product saved = new Product();
        saved.setName(sampleProduct.getName());
        saved.setProductType(sampleProduct.getProductType());
        saved.setEan(sampleProduct.getEan());
        saved.setDefaultUnit(sampleProduct.getDefaultUnit());
        given(productRepository.save(Mockito.any(Product.class))).willReturn(saved);

        // when
        Product result = productService.createProduct(
                sampleProduct.getName(),
                sampleProduct.getProductType(),
                sampleProduct.getEan(),
                sampleProduct.getDefaultUnit()
        );

        // then
        assertThat(result).isSameAs(saved);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        Product toSave = captor.getValue();
        assertThat(toSave.getName()).isEqualTo("Milk");
        Assertions.assertThat(toSave.getProductType()).isEqualTo(ProductType.DAIRY);
        assertThat(toSave.getEan()).isEqualTo("5901234567890");
        Assertions.assertThat(toSave.getDefaultUnit()).isEqualTo(Unit.MILLILITER);
    }

    @Test
    void deleteProduct_whenExists_shouldDeleteAndReturnTrue() {
        given(productRepository.existsById(productId)).willReturn(true);

        boolean deleted = productService.deleteProduct(productId);

        assertThat(deleted).isTrue();
        verify(productRepository).deleteById(productId);
    }

    @Test
    void deleteProduct_whenNotExists_shouldReturnFalseAndNotDelete() {
        given(productRepository.existsById(productId)).willReturn(false);

        boolean deleted = productService.deleteProduct(productId);

        assertThat(deleted).isFalse();
        verify(productRepository, never()).deleteById(any());
    }

    @Test
    void findProductById_whenFound_shouldReturn() {
        given(productRepository.findById(productId)).willReturn(Optional.of(sampleProduct));

        Product result = productService.findProductById(productId);

        assertThat(result).isSameAs(sampleProduct);
    }

    @Test
    void findProductById_whenMissing_shouldThrow() {
        given(productRepository.findById(productId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.findProductById(productId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Product not found");
    }

    @Test
    void findProductByName_whenFound_shouldReturn() {
        given(productRepository.findFirstByNameIgnoreCase("Milk"))
                .willReturn(Optional.of(sampleProduct));

        Product result = productService.findProductByName("Milk");

        assertThat(result).isSameAs(sampleProduct);
    }

    @Test
    void findProductByName_whenMissing_shouldThrow() {
        given(productRepository.findFirstByNameIgnoreCase("Milk"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.findProductByName("Milk"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("name");
    }

    @Test
    void findProductByEan_whenFound_shouldReturn() {
        given(productRepository.findByEan("5901234567890"))
                .willReturn(Optional.of(sampleProduct));

        Product result = productService.findProductByEan("5901234567890");

        assertThat(result).isSameAs(sampleProduct);
    }

    @Test
    void findProductByEan_whenMissing_shouldThrow() {
        given(productRepository.findByEan("5901234567890"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.findProductByEan("5901234567890"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("EAN");
    }

    @Test
    void parseProductFromApi_success_full() throws Exception {
        String ean = "3017620422003";
        var nutr = new OffClient.OffNutriments(539.0, 6.3, 57.5, 30.9);
        var prod = new OffClient.OffProduct("Nutella", "Ferrero", nutr,
                List.of("en:breakfasts", "en:spreads"));
        var resp = new OffClient.OffResponse(1, ean, prod);

        given(offClient.getByEan(ean)).willReturn(reactor.core.publisher.Mono.just(resp));

        Product p = productService.parseProductFromApi(ean);

        assertThat(p.getEan()).isEqualTo(ean);
        assertThat(p.getName()).isEqualTo("Nutella");
        assertThat(p.getBrand()).isEqualTo("Ferrero");
        Assertions.assertThat(p.getProductType()).isEqualTo(ProductType.OTHER);
        assertThat(p.getKcal100()).isEqualByComparingTo("539.00");
        assertThat(p.getProtein100()).isEqualByComparingTo("6.30");
        assertThat(p.getCarbs100()).isEqualByComparingTo("57.50");
        assertThat(p.getFat100()).isEqualByComparingTo("30.90");

        verify(offClient).getByEan(ean);
    }

    @Test
    void parseProductFromApi_success_partial_missingFields() throws Exception {
        String ean = "0001112223334";
        var prod = new OffClient.OffProduct("Plain Name", "", null, List.of());
        var resp = new OffClient.OffResponse(1, ean, prod);

        given(offClient.getByEan(ean)).willReturn(reactor.core.publisher.Mono.just(resp));

        Product p = productService.parseProductFromApi(ean);

        assertThat(p.getName()).isEqualTo("Plain Name");
        assertThat(p.getBrand()).isNull();
        assertThat(p.getKcal100()).isNull();
        assertThat(p.getProtein100()).isNull();
        assertThat(p.getCarbs100()).isNull();
        assertThat(p.getFat100()).isNull();

        verify(offClient).getByEan(ean);
    }

    @Test
    void parseProductFromApi_error_notFound_status0() {
        String ean = "9999999999999";
        var resp = new OffClient.OffResponse(0, ean, null);

        given(offClient.getByEan(ean)).willReturn(reactor.core.publisher.Mono.just(resp));

        assertThatThrownBy(() -> productService.parseProductFromApi(ean))
                .isInstanceOf(ParsingProductFromApiException.class);

        verify(offClient).getByEan(ean);
    }

    @Test
    void parseProductFromApi_error_missingName() {
        String ean = "1234567890123";
        var nutr = new OffClient.OffNutriments(null, 1.0, null, 2.0);
        var prod = new OffClient.OffProduct(null, "Brand", nutr, List.of());
        var resp = new OffClient.OffResponse(1, ean, prod);

        given(offClient.getByEan(ean)).willReturn(reactor.core.publisher.Mono.just(resp));

        assertThatThrownBy(() -> productService.parseProductFromApi(ean))
                .isInstanceOf(ParsingProductFromApiException.class);

        verify(offClient).getByEan(ean);
    }

    @Test
    void parseProductFromApi_error_nullResponse() {
        String ean = "1231231231231";
        given(offClient.getByEan(ean)).willReturn(reactor.core.publisher.Mono.empty());

        assertThatThrownBy(() -> productService.parseProductFromApi(ean))
                .isInstanceOf(ParsingProductFromApiException.class);

        verify(offClient).getByEan(ean);
    }

    @Test
    void parseProductFromApi_success_brandFirstOnly() throws Exception {
        String ean = "5555555555555";
        var prod = new OffClient.OffProduct("Multi Brand", "FirstBrand, SecondBrand", null, List.of());
        var resp = new OffClient.OffResponse(1, ean, prod);

        given(offClient.getByEan(ean)).willReturn(reactor.core.publisher.Mono.just(resp));

        Product p = productService.parseProductFromApi(ean);

        assertThat(p.getBrand()).isEqualTo("FirstBrand");
        verify(offClient).getByEan(ean);
    }
}
