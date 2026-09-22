package io.github.mkliszczun.fridge.dto;

import io.github.mkliszczun.fridge.enums.ProductType;
import io.github.mkliszczun.fridge.enums.Unit;

public record AiProductProposalResponse(String name, String ean, String brand,
                                        ProductType productType, Unit defaultUnit,
                                        Integer shelfLifeAfterOpeningDays,
                                        Integer defaultExpirationDays) {}
