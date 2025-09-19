package io.github.mkliszczun.fridge.dto;

import io.github.mkliszczun.fridge.enums.FridgeRole;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.hibernate.validator.constraints.UUID;

import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FridgeResponse(
        UUID id,
        String name,
        Integer membersCount,
        OffsetDateTime createdAt,
        FridgeRole roleOfCurrentUser
) {}
