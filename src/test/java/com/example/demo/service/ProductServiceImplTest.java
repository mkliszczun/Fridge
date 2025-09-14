package com.example.demo.service;

import com.example.demo.enums.ProductType;
import com.example.demo.fridge.Product;
import com.example.demo.repository.ProductRepository;
import com.example.demo.enums.Unit;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

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
        assertThat(toSave.getProductType()).isEqualTo(ProductType.DAIRY);
        assertThat(toSave.getEan()).isEqualTo("5901234567890");
        assertThat(toSave.getDefaultUnit()).isEqualTo(Unit.MILLILITER);
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
}
