package io.github.mkliszczun.fridge.repository;

import io.github.mkliszczun.fridge.fridge.FridgeInvitation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FridgeInvitationRepository extends JpaRepository<FridgeInvitation, UUID> {
    Optional<FridgeInvitation> findByFridgeIdAndInvitedUserId(UUID fridgeId, UUID userId);

    @Query("select i from FridgeInvitation i join fetch i.fridge join fetch i.invitedBy "
            + "where i.invitedUser.id = :userId and i.expiresAt > :now order by i.createdAt, i.id")
    List<FridgeInvitation> findActiveForUser(UUID userId, Instant now);

    // Scalar lookup avoids caching stale entities before taking the user -> fridge -> invitation locks.
    @Query("select i.fridge.id from FridgeInvitation i where i.id = :id and i.invitedUser.id = :userId")
    Optional<UUID> findFridgeIdForRecipient(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from FridgeInvitation i where i.id = :id and i.invitedUser.id = :userId")
    Optional<FridgeInvitation> findForRecipientForUpdate(UUID id, UUID userId);
}
