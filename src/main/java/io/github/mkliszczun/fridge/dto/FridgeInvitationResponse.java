package io.github.mkliszczun.fridge.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

public record FridgeInvitationResponse(UUID id, UUID fridgeId, String fridgeName,
                                      UUID invitedByUserId, String invitedByEmail,
                                      OffsetDateTime createdAt, Instant expiresAt) {}
