package io.github.mkliszczun.fridge.controller;

import io.github.mkliszczun.fridge.dto.AddProductRequest;
import io.github.mkliszczun.fridge.dto.AddProductResponse;
import io.github.mkliszczun.fridge.fridge.Product;
import io.github.mkliszczun.fridge.security.AppUserDetails;
import io.github.mkliszczun.fridge.service.ProductService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.parameters.P;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
public class ProductsController {

    final ProductService productService;

    public ProductsController(ProductService productService){
        this.productService = productService;
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AddProductResponse addProduct(@Valid @RequestBody AddProductRequest req, @AuthenticationPrincipal AppUserDetails userDetails){
        UUID userId = userDetails.getId();
        Product savedProduct = productService.createProduct(req.name(), req.productType(), req.ean(), req.defaultUnit());
        return toResponse(savedProduct);
    }

    @GetMapping
    public List<Product> listAllProducts(){
        return productService.findAllProducts();
    }

    @GetMapping("/{ean}")
    public AddProductResponse findByEan(@PathVariable String ean,
                             @AuthenticationPrincipal AppUserDetails userDetails){
        UUID userId = userDetails.getId();
        Product product = productService.findProductByEan(ean);
        return toResponse(product);
    }

    private AddProductResponse toResponse(Product product) {
        return new AddProductResponse(product.getId(),
                product.getProductType(),
                product.getName(),
                product.getBrand(),
                product.getDefaultUnit()
                );
    }
}
