package io.github.mkliszczun.fridge.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record UpdateBestBeforeDateRequest(
        @NotNull LocalDate bestBeforeDate
) {
}
