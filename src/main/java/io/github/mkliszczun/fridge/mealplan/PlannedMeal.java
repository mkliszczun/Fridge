package io.github.mkliszczun.fridge.mealplan;

import io.github.mkliszczun.fridge.common.Audit;
import io.github.mkliszczun.fridge.fridge.Fridge;
import io.github.mkliszczun.fridge.recipe.Recipe;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "planned_meal", indexes = {
        @Index(name = "idx_planned_meal_fridge_date", columnList = "fridge_id,planned_date"),
        @Index(name = "idx_planned_meal_source_recipe", columnList = "source_recipe_id")
})
public class PlannedMeal extends Audit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "fridge_id", nullable = false)
    private Fridge fridge;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_recipe_id")
    private Recipe sourceRecipe;

    @NotBlank
    @Column(name = "recipe_name", nullable = false)
    private String recipeName;

    @Column(name = "recipe_description", columnDefinition = "text")
    private String recipeDescription;

    @NotBlank
    @Column(name = "recipe_instructions", nullable = false, columnDefinition = "text")
    private String recipeInstructions;

    @Positive
    @Column(name = "recipe_servings", nullable = false)
    private Integer recipeServings;

    @OneToMany(mappedBy = "plannedMeal", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<PlannedMealIngredient> ingredients = new ArrayList<>();

    @NotNull
    @Column(name = "planned_date", nullable = false)
    private LocalDate plannedDate;

    @NotNull
    @Positive
    @Column(nullable = false)
    private Integer servings;

    @Column(name = "created_by_user_id", nullable = false, columnDefinition = "uuid")
    private UUID createdByUserId;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    public UUID getId() {
        return id;
    }

    public Fridge getFridge() {
        return fridge;
    }

    public void setFridge(Fridge fridge) {
        this.fridge = fridge;
    }

    public Recipe getSourceRecipe() {
        return sourceRecipe;
    }

    public String getRecipeName() { return recipeName; }
    public String getRecipeDescription() { return recipeDescription; }
    public String getRecipeInstructions() { return recipeInstructions; }
    public Integer getRecipeServings() { return recipeServings; }
    public List<PlannedMealIngredient> getIngredients() { return ingredients; }

    public void snapshotRecipe(Recipe recipe) {
        sourceRecipe = recipe;
        recipeName = recipe.getName();
        recipeDescription = recipe.getDescription();
        recipeInstructions = recipe.getInstructions();
        recipeServings = recipe.getServings();

        ingredients.clear();
        recipe.getIngredients().forEach(source -> {
            PlannedMealIngredient snapshot = new PlannedMealIngredient();
            snapshot.setPlannedMeal(this);
            snapshot.setName(source.getName());
            snapshot.setAmount(source.getAmount());
            snapshot.setUnit(source.getUnit());
            snapshot.setOptional(source.isOptional());
            snapshot.setNote(source.getNote());
            snapshot.setPosition(source.getPosition());
            ingredients.add(snapshot);
        });
    }

    public LocalDate getPlannedDate() {
        return plannedDate;
    }

    public void setPlannedDate(LocalDate plannedDate) {
        this.plannedDate = plannedDate;
    }

    public Integer getServings() {
        return servings;
    }

    public void setServings(Integer servings) {
        this.servings = servings;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(UUID createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
