package com.example.demo.fridge;

import com.example.demo.common.Audit;
import com.example.demo.enums.ProductType;
import com.example.demo.enums.Unit;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "product",
        indexes = @Index(name = "idx_product_ean", columnList = "ean", unique = true))
public class Product extends Audit {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    private String name;

    private String brand;

    // EAN pod etap 2
    @Column(length = 32, unique = true)
    private String ean;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductType productType = ProductType.OTHER;

    // makro na 100 g/ml
    @PositiveOrZero private BigDecimal kcal100;
    @PositiveOrZero private BigDecimal protein100;
    @PositiveOrZero private BigDecimal carbs100;
    @PositiveOrZero private BigDecimal fat100;

    private Integer shelfLifeAfterOpeningDays; // null => użyj domyślnych per productType

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Unit defaultUnit = Unit.GRAM;

    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getEan() { return ean; }
    public void setEan(String ean) { this.ean = ean; }
    public ProductType getProductType() { return productType; }
    public void setProductType(ProductType productType) { this.productType = productType; }
    public BigDecimal getKcal100() { return kcal100; }
    public void setKcal100(BigDecimal kcal100) { this.kcal100 = kcal100; }
    public BigDecimal getProtein100() { return protein100; }
    public void setProtein100(BigDecimal protein100) { this.protein100 = protein100; }
    public BigDecimal getCarbs100() { return carbs100; }
    public void setCarbs100(BigDecimal carbs100) { this.carbs100 = carbs100; }
    public BigDecimal getFat100() { return fat100; }
    public void setFat100(BigDecimal fat100) { this.fat100 = fat100; }
    public Integer getShelfLifeAfterOpeningDays() { return shelfLifeAfterOpeningDays; }
    public void setShelfLifeAfterOpeningDays(Integer days) { this.shelfLifeAfterOpeningDays = days; }
    public Unit getDefaultUnit() { return defaultUnit; }
    public void setDefaultUnit(Unit defaultUnit) { this.defaultUnit = defaultUnit; }
}