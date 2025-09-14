package com.example.demo.service;

import com.example.demo.enums.ProductType;
import com.example.demo.enums.Unit;
import com.example.demo.fridge.Product;
import com.example.demo.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ProductServiceImpl implements ProductService {

    final ProductRepository productRepository;

    public ProductServiceImpl(ProductRepository productRepository){
        this.productRepository = productRepository;
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
}
