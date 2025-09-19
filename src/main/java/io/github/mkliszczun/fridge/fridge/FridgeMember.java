package io.github.mkliszczun.fridge.fridge;

import io.github.mkliszczun.fridge.common.Audit;
import io.github.mkliszczun.fridge.enums.FridgeRole;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Entity
@Table(name = "fridge_member",
        uniqueConstraints = @UniqueConstraint(name = "uk_fridge_user", columnNames = {"fridge_id","user_id"}),
        indexes = {
                @Index(name = "idx_fridge_member_fridge", columnList = "fridge_id"),
                @Index(name = "idx_fridge_member_user", columnList = "user_id")
        })
public class FridgeMember extends Audit {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "fridge_id", nullable = false)
    private Fridge fridge;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FridgeRole roleInFridge = FridgeRole.MEMBER;

    @NotNull
    private Boolean isDefault = Boolean.FALSE;

    public UUID getId() { return id; }
    public Fridge getFridge() { return fridge; }
    public void setFridge(Fridge fridge) { this.fridge = fridge; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public FridgeRole getRoleInFridge() { return roleInFridge; }
    public void setRoleInFridge(FridgeRole role) { this.roleInFridge = role; }
    public Boolean getIsDefault() { return isDefault; }
    public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }

}
