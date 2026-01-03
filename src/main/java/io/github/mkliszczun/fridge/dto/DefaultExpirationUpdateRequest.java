package io.github.mkliszczun.fridge.dto;

public record DefaultExpirationUpdateRequest(    Integer defaultExpirationDays,
                                                 Integer expirationDaysAfterOpening) {
}
