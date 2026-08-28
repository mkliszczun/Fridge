package io.github.mkliszczun.fridge.mealplan;

import jakarta.persistence.Column;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "planned_meal_ingredient")
public class PlannedMealIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "planned_meal_id", nullable = false)
    private PlannedMeal plannedMeal;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Positive
    private BigDecimal amount;

    @Column(length = 64)
    private String unit;

    @Column(name = "is_optional", nullable = false)
    private boolean optional;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "display_order", nullable = false)
    private int position;

    @OneToMany(mappedBy = "plannedMealIngredient", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<PlannedMealReservation> reservations = new LinkedHashSet<>();

    public UUID getId() { return id; }
    public PlannedMeal getPlannedMeal() { return plannedMeal; }
    public void setPlannedMeal(PlannedMeal plannedMeal) { this.plannedMeal = plannedMeal; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public boolean isOptional() { return optional; }
    public void setOptional(boolean optional) { this.optional = optional; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
    public Set<PlannedMealReservation> getReservations() { return reservations; }
    public void addReservation(PlannedMealReservation reservation) {
        reservation.setPlannedMealIngredient(this);
        reservations.add(reservation);
    }
    public void removeReservation(PlannedMealReservation reservation) {
        reservations.remove(reservation);
    }
}
