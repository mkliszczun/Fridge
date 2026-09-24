package io.github.mkliszczun.fridge.fridge;

import io.github.mkliszczun.fridge.common.Audit;
import io.github.mkliszczun.fridge.entity.UserEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fridge_invitation", uniqueConstraints = @UniqueConstraint(
        name = "uk_fridge_invitation_recipient", columnNames = {"fridge_id", "invited_user_id"}))
@Getter
@Setter
public class FridgeInvitation extends Audit {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "fridge_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Fridge fridge;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private UserEntity invitedUser;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by_user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private UserEntity invitedBy;

    @Column(nullable = false)
    private Instant expiresAt;
}
