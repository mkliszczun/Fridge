package io.github.mkliszczun.fridge.controller;

import io.github.mkliszczun.fridge.enums.ProductType;
import org.springframework.security.core.parameters.P;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/product-types")
public class ProductTypesController {
    @GetMapping
    public List<ProductType> getProductTypes(){
        List<ProductType> types = new ArrayList<>();
        types.add(ProductType.DAIRY);
        types.add(ProductType.OTHER);
        types.add(ProductType.BAKERY);
        types.add(ProductType.DRY);
        types.add(ProductType.BEVERAGE);
        types.add(ProductType.VEGETABLE);
        types.add(ProductType.MEAT);
        types.add(ProductType.FRUIT);

        return types;
    }
}
