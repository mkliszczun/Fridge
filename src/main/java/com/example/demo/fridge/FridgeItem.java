package com.example.demo.fridge;

import com.example.demo.common.Audit;
import com.example.demo.enums.ItemState;
import com.example.demo.enums.Unit;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "fridge_item",
        indexes = {
                @Index(name = "idx_item_fridge", columnList = "fridge_id"),
                @Index(name = "idx_item_fridge_state", columnList = "fridge_id,state"),
                @Index(name = "idx_item_expire", columnList = "fridge_id,effective_expire_at")
        })
public class FridgeItem extends Audit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "fridge_id", nullable = false)
    private Fridge fridge;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product; // opcjonalnie — można dodać „customName”

    private String customName;

    @NotNull
    @Positive
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Unit unit;

    private LocalDate bestBeforeDate;
    private LocalDate openDate;

    @Column(name = "effective_expire_at")
    private LocalDate effectiveExpireAt; // do it on the service side

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemState state = ItemState.SEALED;

    @Column(name = "owner_user_id", columnDefinition = "uuid")
    private UUID ownerUserId;

    private OffsetDateTime archivedAt; // soft delete / historia

    // --- getters/setters ---

    public UUID getId() { return id; }
    public Fridge getFridge() { return fridge; }
    public void setFridge(Fridge fridge) { this.fridge = fridge; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public String getCustomName() { return customName; }
    public void setCustomName(String customName) { this.customName = customName; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public Unit getUnit() { return unit; }
    public void setUnit(Unit unit) { this.unit = unit; }
    public LocalDate getBestBeforeDate() { return bestBeforeDate; }
    public void setBestBeforeDate(LocalDate bestBeforeDate) { this.bestBeforeDate = bestBeforeDate; }
    public LocalDate getOpenDate() { return openDate; }
    public void setOpenDate(LocalDate openDate) { this.openDate = openDate; }
    public LocalDate getEffectiveExpireAt() { return effectiveExpireAt; }
    public void setEffectiveExpireAt(LocalDate effectiveExpireAt) { this.effectiveExpireAt = effectiveExpireAt; }
    public ItemState getState() { return state; }
    public void setState(ItemState state) { this.state = state; }
    public UUID getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(UUID ownerUserId) { this.ownerUserId = ownerUserId; }
    public OffsetDateTime getArchivedAt() { return archivedAt; }
    public void setArchivedAt(OffsetDateTime archivedAt) { this.archivedAt = archivedAt; }
}
