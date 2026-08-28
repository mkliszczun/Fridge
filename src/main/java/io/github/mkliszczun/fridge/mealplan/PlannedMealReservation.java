package io.github.mkliszczun.fridge.mealplan;

import io.github.mkliszczun.fridge.fridge.FridgeItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "planned_meal_reservation", indexes = {
        @Index(name = "idx_planned_meal_reservation_ingredient", columnList = "planned_meal_ingredient_id"),
        @Index(name = "idx_planned_meal_reservation_item", columnList = "fridge_item_id")
}, uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_planned_meal_reservation_ingredient_item",
                columnNames = {"planned_meal_ingredient_id", "fridge_item_id"}
        )
})
public class PlannedMealReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "planned_meal_ingredient_id", nullable = false)
    private PlannedMealIngredient plannedMealIngredient;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "fridge_item_id", nullable = false)
    private FridgeItem fridgeItem;

    @Positive
    @Column(nullable = false, precision = 38, scale = 2)
    private BigDecimal amount;

    public UUID getId() { return id; }
    public PlannedMealIngredient getPlannedMealIngredient() { return plannedMealIngredient; }
    public void setPlannedMealIngredient(PlannedMealIngredient plannedMealIngredient) {
        this.plannedMealIngredient = plannedMealIngredient;
    }
    public FridgeItem getFridgeItem() { return fridgeItem; }
    public void setFridgeItem(FridgeItem fridgeItem) { this.fridgeItem = fridgeItem; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
