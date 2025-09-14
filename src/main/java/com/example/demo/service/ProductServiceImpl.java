package com.example.demo.service;

import com.example.demo.enums.ProductType;
import com.example.demo.enums.Unit;
import com.example.demo.fridge.Product;
import com.example.demo.repository.ProductRepository;
import org.springframework.stereotype.Service;

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

        return product;
    }

    @Override
    public boolean deleteProduct(UUID id) {
        return false;
    }

    @Override
    public Product findProductById(UUID id) {
        return null;
    }

    @Override
    public Product findProductByName(String name) {
        return null;
    }

    @Override
    public Product findProductByEan(String ean) {
        return null;
    }
}
