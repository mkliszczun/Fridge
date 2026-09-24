package io.github.mkliszczun.fridge.service;

import io.github.mkliszczun.fridge.dto.FridgeInvitationResponse;
import io.github.mkliszczun.fridge.dto.FridgeResponse;
import io.github.mkliszczun.fridge.entity.UserEntity;
import io.github.mkliszczun.fridge.enums.FridgeRole;
import io.github.mkliszczun.fridge.exception.ConflictException;
import io.github.mkliszczun.fridge.exception.ForbiddenException;
import io.github.mkliszczun.fridge.exception.NotFoundException;
import io.github.mkliszczun.fridge.fridge.FridgeInvitation;
import io.github.mkliszczun.fridge.fridge.FridgeMember;
import io.github.mkliszczun.fridge.repository.*;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;

@Service
public class FridgeInvitationService {
    private static final Duration VALIDITY = Duration.ofDays(7);
    private final FridgeInvitationRepository invitations;
    private final FridgeRepository fridges;
    private final FridgeMemberRepository members;
    private final UserRepository users;
    private final EntityManager entityManager;
    private final Clock clock;

    public FridgeInvitationService(FridgeInvitationRepository invitations, FridgeRepository fridges,
                                   FridgeMemberRepository members, UserRepository users,
                                   EntityManager entityManager, Clock clock) {
        this.invitations = invitations;
        this.fridges = fridges;
        this.members = members;
        this.users = users;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional
    public void invite(UUID fridgeId, UUID senderId, String email) {
        String address = email.trim().toLowerCase(Locale.ROOT);
        var candidates = users.findVerifiedIdsByEmail(address);
        UUID recipientId = candidates.size() == 1 ? candidates.get(0) : null;
        Map<UUID, UserEntity> lockedUsers = new HashMap<>();
        // Lock users in a stable order before the fridge, matching account deletion's lock order.
        Stream.of(senderId, recipientId).filter(Objects::nonNull).distinct().sorted().forEach(id ->
                users.findLockedById(id).ifPresent(user -> {
                    entityManager.refresh(user);
                    lockedUsers.put(id, user);
                }));
        UserEntity sender = lockedUsers.get(senderId);
        requireActiveAccount(sender);
        var fridge = fridges.findByIdForUpdate(fridgeId)
                .orElseThrow(() -> new NotFoundException("Fridge not found"));
        if (!isOwner(fridgeId, senderId)) throw new ForbiddenException("Only OWNER can invite members");

        UserEntity recipient = lockedUsers.get(recipientId);
        // Identical response for unknown/unverified addresses, self-invites and current members.
        if (!isActiveAccount(recipient) || !address.equals(recipient.getEmail().trim().toLowerCase(Locale.ROOT))
                || senderId.equals(recipientId) || members.existsByFridgeIdAndUserId(fridgeId, recipientId)) return;

        var now = clock.instant();
        var existing = invitations.findByFridgeIdAndInvitedUserId(fridgeId, recipientId);
        if (existing.isPresent()) {
            if (existing.get().getExpiresAt().isAfter(now)) return;
            invitations.delete(existing.get());
            invitations.flush();
        }
        var invitation = new FridgeInvitation();
        invitation.setFridge(fridge);
        invitation.setInvitedUser(recipient);
        invitation.setInvitedBy(sender);
        invitation.setExpiresAt(now.plus(VALIDITY));
        invitations.save(invitation);
    }

    @Transactional(readOnly = true)
    public List<FridgeInvitationResponse> list(UUID userId) {
        return invitations.findActiveForUser(userId, clock.instant()).stream().map(invitation ->
                new FridgeInvitationResponse(invitation.getId(), invitation.getFridge().getId(),
                        invitation.getFridge().getName(), invitation.getInvitedBy().getId(),
                        invitation.getInvitedBy().getEmail(), invitation.getCreatedAt(), invitation.getExpiresAt()))
                .toList();
    }

    @Transactional
    public FridgeResponse accept(UUID invitationId, UUID userId) {
        var invitation = lockForRecipient(invitationId, userId);
        var fridge = invitation.getFridge();
        if (!isOwner(fridge.getId(), invitation.getInvitedBy().getId())) {
            throw new ConflictException("Invitation sender is no longer an OWNER");
        }
        if (!members.existsByFridgeIdAndUserId(fridge.getId(), userId)) {
            var membership = new FridgeMember();
            membership.setFridge(fridge);
            membership.setUserId(userId);
            membership.setRoleInFridge(FridgeRole.MEMBER);
            membership.setIsDefault(!members.existsDefaultForUser(userId));
            members.save(membership);
        }
        invitations.delete(invitation);
        return new FridgeResponse(fridge.getId(), fridge.getName());
    }

    @Transactional
    public void decline(UUID invitationId, UUID userId) {
        invitations.delete(lockForRecipient(invitationId, userId));
    }

    private FridgeInvitation lockForRecipient(UUID invitationId, UUID userId) {
        var user = users.findLockedById(userId).orElseThrow(() -> new ForbiddenException("Account unavailable"));
        entityManager.refresh(user);
        requireActiveAccount(user);
        UUID fridgeId = invitations.findFridgeIdForRecipient(invitationId, userId)
                .orElseThrow(FridgeInvitationService::unavailable);
        fridges.findByIdForUpdate(fridgeId).orElseThrow(FridgeInvitationService::unavailable);
        var invitation = invitations.findForRecipientForUpdate(invitationId, userId)
                .orElseThrow(FridgeInvitationService::unavailable);
        if (!invitation.getExpiresAt().isAfter(clock.instant())) throw unavailable();
        return invitation;
    }

    private boolean isOwner(UUID fridgeId, UUID userId) {
        return members.findByFridgeIdAndUserId(fridgeId, userId)
                .filter(member -> member.getRoleInFridge() == FridgeRole.OWNER).isPresent();
    }

    private static boolean isActiveAccount(UserEntity user) {
        return user != null && user.isEmailVerified() && user.isEnabled() && user.isAccountNonExpired()
                && user.isAccountNonLocked() && user.isCredentialsNonExpired();
    }

    private static void requireActiveAccount(UserEntity user) {
        if (!isActiveAccount(user)) throw new ForbiddenException("Verified active account required");
    }

    private static NotFoundException unavailable() {
        return new NotFoundException("Invitation not found or expired");
    }
}
