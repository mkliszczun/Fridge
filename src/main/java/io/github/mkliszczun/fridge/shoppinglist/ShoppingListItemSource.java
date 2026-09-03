package io.github.mkliszczun.fridge.shoppinglist;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "shopping_list_item_source", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_shopping_list_source_ingredient",
                columnNames = "planned_meal_ingredient_id")
})
public class ShoppingListItemSource {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "shopping_list_item_id", nullable = false)
    private ShoppingListItem shoppingListItem;

    @Column(name = "planned_meal_ingredient_id", nullable = false)
    private UUID plannedMealIngredientId;

    @Positive
    @Column(name = "contribution_amount", precision = 19, scale = 3)
    private BigDecimal contributionAmount;

    public UUID getId() { return id; }
    public ShoppingListItem getShoppingListItem() { return shoppingListItem; }
    public void setShoppingListItem(ShoppingListItem shoppingListItem) {
        this.shoppingListItem = shoppingListItem;
    }
    public UUID getPlannedMealIngredientId() { return plannedMealIngredientId; }
    public void setPlannedMealIngredientId(UUID plannedMealIngredientId) {
        this.plannedMealIngredientId = plannedMealIngredientId;
    }
    public BigDecimal getContributionAmount() { return contributionAmount; }
    public void setContributionAmount(BigDecimal contributionAmount) {
        this.contributionAmount = contributionAmount;
    }
}
