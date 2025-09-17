package com.example.demo.service;

import com.example.demo.enums.ProductType;
import com.example.demo.enums.Unit;
import com.example.demo.exception.ParsingProductFromApiException;
import com.example.demo.fridge.Product;

import java.util.UUID;

public interface ProductService {
    Product createProduct(String name, ProductType productType, String ean, Unit defaultUnit);
    boolean deleteProduct(UUID id);

    Product findProductById(UUID id);

    Product findProductByName(String name);

    Product findProductByEan(String ean);

    Product parseProductFromApi(String ean) throws ParsingProductFromApiException;
}
