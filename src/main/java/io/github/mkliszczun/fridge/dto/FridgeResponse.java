package io.github.mkliszczun.fridge.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FridgeResponse(
        UUID id,
        String name
) {}
