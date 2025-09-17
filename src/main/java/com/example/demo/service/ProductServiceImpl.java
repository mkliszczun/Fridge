package com.example.demo.service;

import com.example.demo.enums.ProductType;
import com.example.demo.enums.Unit;
import com.example.demo.exception.ParsingProductFromApiException;
import com.example.demo.fridge.Product;
import com.example.demo.off.OffClient;
import com.example.demo.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;

@Service
public class ProductServiceImpl implements ProductService {

    final ProductRepository productRepository;

    final OffClient offClient;

    public ProductServiceImpl(ProductRepository productRepository, OffClient offClient){
        this.productRepository = productRepository;
        this.offClient = offClient;
    }

    @Override
    public Product createProduct(String name, ProductType productType, String ean, Unit defaultUnit) {
        Product product = new Product();
        product.setName(name);
        product.setProductType(productType);
        product.setEan(ean);
        product.setDefaultUnit(defaultUnit);
        Product savedProduct = productRepository.save(product);

        return savedProduct;
    }

    @Override
    public boolean deleteProduct(UUID id) {
        if (!productRepository.existsById(id)) {
            return false;
        }
        productRepository.deleteById(id);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public Product findProductById(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + id));
    }

    @Override
    public Product findProductByName(String name) {
        return productRepository.findFirstByNameIgnoreCase(name)
                .orElseThrow(() -> new EntityNotFoundException("Product not found by name: " + name));
    }

    @Override
    public Product findProductByEan(String ean) {
        return productRepository.findByEan(ean)
                .orElseThrow(() -> new EntityNotFoundException("Product not found by EAN: " + ean));
    }

    @Override
    public Product parseProductFromApi(String ean) throws ParsingProductFromApiException {
        try {
            var resp = offClient.getByEan(ean).block(Duration.ofSeconds(10));
            if (resp == null) {
                throw new ParsingProductFromApiException("Brak odpowiedzi z Open Food Facts");
            }
            if (resp.status() != 1 || resp.product() == null) {
                throw new ParsingProductFromApiException("Produkt nie znaleziony dla EAN: " + ean);
            }

            var p = resp.product();
            var product = new Product();

            // EAN
            product.setEan(resp.code() != null ? resp.code() : ean);

            // Nazwa (wymagana) – jeśli brak, przerywamy
            if (hasText(p.productName())) {
                product.setName(p.productName().trim());
            } else {
                throw new ParsingProductFromApiException("Brak nazwy produktu w odpowiedzi dla EAN: " + ean);
            }

            // Marka (opcjonalnie – bierzemy pierwszy wpis z listy rozdzielanej przecinkami)
            if (hasText(p.brands())) {
                var firstBrand = Arrays.stream(p.brands().split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .findFirst()
                        .orElse(null);
                if (firstBrand != null) product.setBrand(firstBrand);
            }

            // Macro - if present in response
            var n = p.nutriments();
            if (n != null) {
                if (n.energyKcal100g() != null)  product.setKcal100(toBig(n.energyKcal100g()));
                if (n.proteins100g() != null)    product.setProtein100(toBig(n.proteins100g()));
                if (n.carbohydrates100g() != null) product.setCarbs100(toBig(n.carbohydrates100g()));
                if (n.fat100g() != null)         product.setFat100(toBig(n.fat100g()));
            }

            return product;

        } catch (ParsingProductFromApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ParsingProductFromApiException("Błąd podczas pobierania/przetwarzania OFF: " + e.getMessage());
        }
    }

    private static boolean hasText(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static BigDecimal toBig(Double d) {
        // delikatne zaokrąglenie do 2 miejsc – możesz zmienić na scale=1/3 wedle potrzeb
        return BigDecimal.valueOf(d).setScale(2, RoundingMode.HALF_UP);
    }
}
