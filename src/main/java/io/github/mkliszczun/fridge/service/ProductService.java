package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.enums.ProductType;
import io.github.mkliszczun.fridge.enums.Unit;
import io.github.mkliszczun.fridge.exception.ParsingProductFromApiException;
import io.github.mkliszczun.fridge.fridge.Product;

import java.util.List;
import java.util.UUID;

public interface ProductService {
    Product createProduct(String name, ProductType productType, String ean, Unit defaultUnit);
    boolean deleteProduct(UUID id);

    Product findProductById(UUID id);

    Product findProductByName(String name);

    Product findProductByEan(String ean);

    Product parseProductFromApi(String ean) throws ParsingProductFromApiException;

    List<Product> findAllProducts();
}
