package io.github.mkliszczun.fridge.shoppinglist;

import io.github.mkliszczun.fridge.common.Audit;
import io.github.mkliszczun.fridge.fridge.Fridge;
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
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "shopping_list_item", indexes = {
        @Index(name = "idx_shopping_list_item_fridge", columnList = "fridge_id")
})
public class ShoppingListItem extends Audit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "fridge_id", nullable = false)
    private Fridge fridge;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Positive
    @Column(name = "manual_amount", precision = 19, scale = 3)
    private BigDecimal manualAmount;

    @Column(length = 64)
    private String unit;

    @Column(name = "is_quantified", nullable = false)
    private boolean quantified;

    @Column(name = "is_checked", nullable = false)
    private boolean checked;

    @OneToMany(mappedBy = "shoppingListItem", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<ShoppingListItemSource> sources = new ArrayList<>();

    public UUID getId() { return id; }
    public Fridge getFridge() { return fridge; }
    public void setFridge(Fridge fridge) { this.fridge = fridge; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getManualAmount() { return manualAmount; }
    public void setManualAmount(BigDecimal manualAmount) { this.manualAmount = manualAmount; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public boolean isQuantified() { return quantified; }
    public void setQuantified(boolean quantified) { this.quantified = quantified; }
    public boolean isChecked() { return checked; }
    public void setChecked(boolean checked) { this.checked = checked; }
    public List<ShoppingListItemSource> getSources() { return sources; }

    public void addSource(ShoppingListItemSource source) {
        source.setShoppingListItem(this);
        sources.add(source);
    }
}
