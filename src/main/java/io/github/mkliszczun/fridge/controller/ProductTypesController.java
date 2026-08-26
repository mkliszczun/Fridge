package io.github.mkliszczun.fridge.controller;

import io.github.mkliszczun.fridge.enums.ProductType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.EnumSet;
import java.util.List;

@RestController
@RequestMapping("/api/product-types")
public class ProductTypesController {
    @GetMapping
    public List<ProductType> getProductTypes(){
        return List.copyOf(EnumSet.allOf(ProductType.class));
    }
}
