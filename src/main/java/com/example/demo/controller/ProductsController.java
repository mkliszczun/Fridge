package com.example.demo.controller;

import com.example.demo.dto.AddProductRequest;
import com.example.demo.dto.AddProductResponse;
import com.example.demo.fridge.Product;
import com.example.demo.repository.ProductRepository;
import com.example.demo.security.AppUserDetails;
import com.example.demo.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

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


    private AddProductResponse toResponse(Product product) {
        return new AddProductResponse(product.getId(),
                product.getProductType(),
                product.getName(),
                product.getBrand(),
                product.getDefaultUnit()
                );
    }
}
